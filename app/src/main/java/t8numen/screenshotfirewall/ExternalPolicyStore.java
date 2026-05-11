package t8numen.screenshotfirewall;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

final class ExternalPolicyStore {
    private static final String TAG = "ScreenshotFirewall";
    private static final String SETTINGS_POLICIES = "screenshot_firewall_policies";
    private static final String SETTINGS_EVENTS = "screenshot_firewall_events";
    private static final String SETTINGS_CONFIG = "screenshot_firewall_config";
    private static final String ENTRY_SEPARATOR = "\n";
    private static final String VALUE_SEPARATOR = "=";
    private static final String POLICY_KEY_SEPARATOR = "|";
    private static final String EVENT_FIELD_SEPARATOR = "\t";
    private static final String CONFIG_DEFAULT_POLICY = "default_policy";
    private static final String CONFIG_OVERRIDE_RULES = "override_rules";
    private static final String CONFIG_COOLDOWN_SECONDS = "cooldown_seconds";
    private static final int EVENT_LIMIT = 80;

    private ExternalPolicyStore() {
    }

    static Map<String, String> readPolicies(Context context) {
        if (context == null) {
            return new TreeMap<>();
        }
        Map<String, String> decoded = decode(Settings.Global.getString(
                context.getContentResolver(),
                SETTINGS_POLICIES));
        Map<String, String> policies = new TreeMap<>();
        for (Map.Entry<String, String> entry : decoded.entrySet()) {
            if (isPolicyKey(entry.getKey())) {
                policies.put(entry.getKey(), entry.getValue());
            }
        }
        return policies;
    }

    static boolean hasPolicyKey(Context context) {
        if (context == null) {
            return false;
        }
        return Settings.Global.getString(context.getContentResolver(), SETTINGS_POLICIES) != null;
    }

    static void setPolicy(
            Context context,
            String feature,
            String packageName,
            String activityName,
            String policy) {
        if (context == null
                || TextUtils.isEmpty(feature)
                || TextUtils.isEmpty(packageName)
                || TextUtils.isEmpty(policy)) {
            return;
        }
        Map<String, String> policies = readPolicies(context);
        policies.put(policyKey(feature, packageName, activityName), policy);
        writePolicies(context, policies);
    }

    static void clearPolicy(
            Context context,
            String feature,
            String packageName,
            String activityName) {
        if (context == null || TextUtils.isEmpty(packageName)) {
            return;
        }
        Map<String, String> policies = readPolicies(context);
        if (TextUtils.isEmpty(feature)) {
            policies.remove(policyKey(Features.FLAG_SECURE, packageName, activityName));
            policies.remove(policyKey(Features.SCREEN_CAPTURE_OBSERVER, packageName, activityName));
        } else {
            policies.remove(policyKey(feature, packageName, activityName));
        }
        writePolicies(context, policies);
    }

    static void clearAll(Context context) {
        if (context == null) {
            return;
        }
        writePolicies(context, new TreeMap<String, String>());
    }

    static void recordEvent(
            Context context,
            String feature,
            String packageName,
            String appLabel,
            String activityName,
            long timeMillis) {
        if (context == null
                || TextUtils.isEmpty(feature)
                || TextUtils.isEmpty(packageName)) {
            return;
        }
        try {
            ArrayList<String> events = readEvents(context);
            events.add(0, encodeEvent(
                    timeMillis,
                    feature,
                    packageName,
                    TextUtils.isEmpty(appLabel) ? packageName : appLabel,
                    TextUtils.isEmpty(activityName) ? "" : activityName));
            while (events.size() > EVENT_LIMIT) {
                events.remove(events.size() - 1);
            }
            writeEvents(context, events);
        } catch (Throwable throwable) {
            Log.e(TAG, "record screenshot firewall event failed", throwable);
        }
    }

    static ArrayList<String> readEvents(Context context) {
        ArrayList<String> events = new ArrayList<>();
        if (context == null) {
            return events;
        }
        String raw = Settings.Global.getString(context.getContentResolver(), SETTINGS_EVENTS);
        if (TextUtils.isEmpty(raw)) {
            return events;
        }
        String[] lines = raw.split(ENTRY_SEPARATOR);
        for (String line : lines) {
            if (!TextUtils.isEmpty(line)) {
                events.add(line);
            }
        }
        return events;
    }

    static void clearEvents(Context context) {
        if (context == null) {
            return;
        }
        Settings.Global.putString(context.getContentResolver(), SETTINGS_EVENTS, "");
    }

    static Config readConfig(Context context) {
        if (context == null) {
            return Config.defaults();
        }
        try {
            Map<String, String> values = decode(Settings.Global.getString(
                    context.getContentResolver(),
                    SETTINGS_CONFIG));
            String defaultPolicy = normalizePolicy(values.get(CONFIG_DEFAULT_POLICY));
            boolean overrideRules = Boolean.parseBoolean(values.get(CONFIG_OVERRIDE_RULES));
            double cooldownSeconds = Config.DEFAULT_COOLDOWN_SECONDS;
            String rawCooldown = values.get(CONFIG_COOLDOWN_SECONDS);
            if (!TextUtils.isEmpty(rawCooldown)) {
                try {
                    cooldownSeconds = Double.parseDouble(rawCooldown);
                } catch (Throwable ignored) {
                    cooldownSeconds = Config.DEFAULT_COOLDOWN_SECONDS;
                }
            }
            return new Config(defaultPolicy, overrideRules, cooldownSeconds);
        } catch (Throwable throwable) {
            Log.e(TAG, "read screenshot firewall config failed", throwable);
            return Config.defaults();
        }
    }

    static void writeConfig(Context context, Config config) {
        if (context == null) {
            return;
        }
        Config safeConfig = config == null ? Config.defaults() : config;
        try {
            Map<String, String> values = new TreeMap<>();
            values.put(CONFIG_DEFAULT_POLICY, safeConfig.defaultPolicy);
            values.put(CONFIG_OVERRIDE_RULES, String.valueOf(safeConfig.overrideRules));
            values.put(CONFIG_COOLDOWN_SECONDS, safeConfig.cooldownSecondsString());
            Settings.Global.putString(
                    context.getContentResolver(),
                    SETTINGS_CONFIG,
                    encode(values));
        } catch (Throwable throwable) {
            Log.e(TAG, "write screenshot firewall config failed", throwable);
        }
    }

    static void sendPolicyResult(Context context) {
        if (context == null) {
            return;
        }
        try {
            ArrayList<String> entries = new ArrayList<>();
            for (Map.Entry<String, String> entry : readPolicies(context).entrySet()) {
                if (!TextUtils.isEmpty(entry.getKey()) && !TextUtils.isEmpty(entry.getValue())) {
                    entries.add(entry.getKey() + VALUE_SEPARATOR + entry.getValue());
                }
            }
            Intent result = new Intent(PromptContract.ACTION_POLICY_RESULT);
            result.setPackage(PolicyController.MODULE_PACKAGE);
            result.putStringArrayListExtra(PromptContract.EXTRA_POLICY_ENTRIES, entries);
            result.putStringArrayListExtra(PromptContract.EXTRA_EVENT_ENTRIES, readEvents(context));
            Config config = readConfig(context);
            result.putExtra(PromptContract.EXTRA_DEFAULT_POLICY, config.defaultPolicy);
            result.putExtra(PromptContract.EXTRA_OVERRIDE_RULES, config.overrideRules);
            result.putExtra(PromptContract.EXTRA_COOLDOWN_SECONDS, config.cooldownSeconds);
            context.sendBroadcast(result);
            Log.i(TAG, "external policy result sent entries=" + entries.size());
        } catch (Throwable throwable) {
            Log.e(TAG, "send external policy result failed", throwable);
        }
    }

    static String policyKey(String feature, String packageName) {
        return policyKey(feature, packageName, "");
    }

    static String policyKey(String feature, String packageName, String activityName) {
        if (TextUtils.isEmpty(activityName)) {
            return feature + POLICY_KEY_SEPARATOR + packageName;
        }
        return feature + POLICY_KEY_SEPARATOR + packageName + POLICY_KEY_SEPARATOR + activityName;
    }

    static String policyActivity(String policyKey) {
        int first = policyKey == null ? -1 : policyKey.indexOf(POLICY_KEY_SEPARATOR);
        int second = first < 0 ? -1 : policyKey.indexOf(POLICY_KEY_SEPARATOR, first + 1);
        if (second <= 0 || second >= policyKey.length() - 1) {
            return "";
        }
        return policyKey.substring(second + 1);
    }

    static String policyFeature(String policyKey) {
        int index = policyKey == null ? -1 : policyKey.indexOf(POLICY_KEY_SEPARATOR);
        if (index <= 0) {
            return null;
        }
        return policyKey.substring(0, index);
    }

    static String policyPackage(String policyKey) {
        int first = policyKey == null ? -1 : policyKey.indexOf(POLICY_KEY_SEPARATOR);
        if (first <= 0 || first >= policyKey.length() - 1) {
            return null;
        }
        int second = policyKey.indexOf(POLICY_KEY_SEPARATOR, first + 1);
        if (second < 0) {
            return policyKey.substring(first + 1);
        }
        if (second == first + 1) {
            return null;
        }
        return policyKey.substring(first + 1, second);
    }

    static String eventFieldSeparator() {
        return EVENT_FIELD_SEPARATOR;
    }

    static String decodeValue(String value) {
        return value == null ? "" : Uri.decode(value);
    }

    private static String normalizePolicy(String policy) {
        return PolicyController.POLICY_BLOCK.equals(policy)
                ? PolicyController.POLICY_BLOCK
                : PolicyController.POLICY_ALLOW;
    }

    private static void writePolicies(Context context, Map<String, String> policies) {
        Settings.Global.putString(
                context.getContentResolver(),
                SETTINGS_POLICIES,
                encode(policies));
    }

    private static void writeEvents(Context context, ArrayList<String> events) {
        StringBuilder builder = new StringBuilder();
        for (String event : events) {
            if (TextUtils.isEmpty(event)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(ENTRY_SEPARATOR);
            }
            builder.append(event);
        }
        Settings.Global.putString(context.getContentResolver(), SETTINGS_EVENTS, builder.toString());
    }

    private static String encodeEvent(
            long timeMillis,
            String feature,
            String packageName,
            String appLabel,
            String activityName) {
        return timeMillis
                + EVENT_FIELD_SEPARATOR + Uri.encode(feature)
                + EVENT_FIELD_SEPARATOR + Uri.encode(packageName)
                + EVENT_FIELD_SEPARATOR + Uri.encode(appLabel)
                + EVENT_FIELD_SEPARATOR + Uri.encode(activityName);
    }

    private static Map<String, String> decode(String raw) {
        Map<String, String> values = new TreeMap<>();
        if (TextUtils.isEmpty(raw)) {
            return values;
        }
        String[] lines = raw.split(ENTRY_SEPARATOR);
        for (String line : lines) {
            int index = line.indexOf(VALUE_SEPARATOR);
            if (index <= 0 || index >= line.length() - 1) {
                continue;
            }
            values.put(line.substring(0, index), line.substring(index + 1));
        }
        return values;
    }

    private static String encode(Map<String, String> values) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (TextUtils.isEmpty(entry.getKey()) || TextUtils.isEmpty(entry.getValue())) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(ENTRY_SEPARATOR);
            }
            builder.append(entry.getKey()).append(VALUE_SEPARATOR).append(entry.getValue());
        }
        return builder.toString();
    }

    private static boolean isPolicyKey(String key) {
        return !TextUtils.isEmpty(policyFeature(key)) && !TextUtils.isEmpty(policyPackage(key));
    }

    static final class Config {
        static final double MIN_COOLDOWN_SECONDS = 5.0d;
        static final double MAX_COOLDOWN_SECONDS = 60.0d;
        static final double DEFAULT_COOLDOWN_SECONDS = 60.0d;

        final String defaultPolicy;
        final boolean overrideRules;
        final double cooldownSeconds;

        Config(String defaultPolicy, boolean overrideRules, double cooldownSeconds) {
            this.defaultPolicy = normalizePolicy(defaultPolicy);
            this.overrideRules = overrideRules;
            this.cooldownSeconds = normalizeCooldown(cooldownSeconds);
        }

        static Config defaults() {
            return new Config(PolicyController.POLICY_ALLOW, false, DEFAULT_COOLDOWN_SECONDS);
        }

        Decision defaultDecision() {
            return PolicyController.POLICY_BLOCK.equals(defaultPolicy)
                    ? Decision.BLOCK
                    : Decision.ALLOW;
        }

        long cooldownMillis() {
            return Math.max(1L, Math.round(cooldownSeconds * 1000.0d));
        }

        String cooldownSecondsString() {
            return String.format(java.util.Locale.US, "%.1f", cooldownSeconds);
        }

        private static double normalizeCooldown(double seconds) {
            if (Double.isNaN(seconds) || Double.isInfinite(seconds)) {
                return DEFAULT_COOLDOWN_SECONDS;
            }
            double clamped = Math.max(MIN_COOLDOWN_SECONDS, Math.min(MAX_COOLDOWN_SECONDS, seconds));
            return Math.round(clamped * 10.0d) / 10.0d;
        }
    }
}

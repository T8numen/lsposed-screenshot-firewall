package t8numen.screenshotfirewall;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.text.TextUtils;
import android.util.AtomicFile;

import java.io.File;
import java.io.FileInputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

final class PolicyController {
    static final String MODULE_PACKAGE = "t8numen.screenshotfirewall";
    static final String POLICY_ALLOW = "allow";
    static final String POLICY_BLOCK = "block";

    private static final String PREFS_NAME = "screenshot_firewall_system";
    private static final String POLICY_PREFIX = "policy.";
    private static final String FALLBACK_POLICY_FILE =
            "/data/system/screenshot_firewall_policies.properties";
    private static final Set<String> SAFE_SYSTEM_PACKAGES;
    private static final ConcurrentHashMap<String, String> MEMORY_POLICIES =
            new ConcurrentHashMap<>();
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    private static volatile Context appContext;
    private static volatile SharedPreferences preferences;

    static {
        HashSet<String> packages = new HashSet<>();
        packages.add("android");
        packages.add("com.android.systemui");
        packages.add(MODULE_PACKAGE);
        SAFE_SYSTEM_PACKAGES = Collections.unmodifiableSet(packages);
    }

    private PolicyController() {
    }

    static void ensureInitialized(Context context) {
        if (context == null || INITIALIZED.get()) {
            return;
        }
        synchronized (PolicyController.class) {
            if (INITIALIZED.get()) {
                return;
            }
            Context baseContext = context;
            try {
                Context deviceContext = context.createDeviceProtectedStorageContext();
                if (deviceContext != null) {
                    baseContext = deviceContext;
                }
            } catch (Throwable throwable) {
                Logx.e("create device storage context failed; using service context", throwable);
            }
            appContext = baseContext;

            try {
                preferences = baseContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                Logx.i("persistent policy storage initialized");
            } catch (Throwable throwable) {
                preferences = null;
                loadFallbackPolicies();
                Logx.e("shared preferences unavailable; using system policy file", throwable);
            }

            INITIALIZED.set(true);
            Logx.i("policy controller initialized");
        }
    }

    static Decision evaluate(String packageName, String feature) {
        return evaluate(packageName, feature, "");
    }

    static Decision evaluate(String packageName, String feature, String activityName) {
        if (TextUtils.isEmpty(packageName)) {
            return Decision.ALLOW;
        }
        if (SAFE_SYSTEM_PACKAGES.contains(packageName)) {
            return Decision.ALLOW;
        }

        syncExternalPolicies();
        ExternalPolicyStore.Config config = readExternalConfig();
        if (config.overrideRules) {
            return config.defaultDecision();
        }

        String stored = storedPolicy(ExternalPolicyStore.policyKey(
                feature,
                packageName,
                activityName));
        if (stored == null && !TextUtils.isEmpty(activityName)) {
            stored = storedPolicy(ExternalPolicyStore.policyKey(feature, packageName));
        }

        if (POLICY_ALLOW.equals(stored)) {
            return Decision.ALLOW;
        }
        if (POLICY_BLOCK.equals(stored)) {
            return Decision.BLOCK;
        }
        return config.defaultDecision();
    }

    private static String storedPolicy(String policyKey) {
        String stored = MEMORY_POLICIES.get(policyKey);
        SharedPreferences prefs = preferences;
        if (stored == null && prefs != null) {
            try {
                stored = prefs.getString(POLICY_PREFIX + policyKey, null);
            } catch (Throwable throwable) {
                Logx.e("read policy failed for " + policyKey, throwable);
            }
        }
        return stored;
    }

    private static void syncExternalPolicies() {
        Context context = appContext;
        if (context == null) {
            return;
        }
        long identity = Binder.clearCallingIdentity();
        try {
            if (!ExternalPolicyStore.hasPolicyKey(context)) {
                return;
            }
            MEMORY_POLICIES.clear();
            MEMORY_POLICIES.putAll(ExternalPolicyStore.readPolicies(context));
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    private static ExternalPolicyStore.Config readExternalConfig() {
        Context context = appContext;
        if (context == null) {
            return ExternalPolicyStore.Config.defaults();
        }
        long identity = Binder.clearCallingIdentity();
        try {
            return ExternalPolicyStore.readConfig(context);
        } catch (Throwable throwable) {
            Logx.e("read external config failed", throwable);
            return ExternalPolicyStore.Config.defaults();
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    static String getAppLabel(String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return "未知应用";
        }
        Context context = appContext;
        if (context == null) {
            return packageName;
        }
        try {
            PackageManager packageManager = context.getPackageManager();
            ApplicationInfo info = packageManager.getApplicationInfo(packageName, 0);
            CharSequence label = packageManager.getApplicationLabel(info);
            return TextUtils.isEmpty(label) ? packageName : label.toString();
        } catch (Throwable ignored) {
            return packageName;
        }
    }

    private static void loadFallbackPolicies() {
        AtomicFile atomicFile = new AtomicFile(new File(FALLBACK_POLICY_FILE));
        try (FileInputStream inputStream = atomicFile.openRead()) {
            Properties properties = new Properties();
            properties.load(inputStream);
            for (String key : properties.stringPropertyNames()) {
                if (!key.startsWith(POLICY_PREFIX)) {
                    continue;
                }
                String packageName = key.substring(POLICY_PREFIX.length());
                String policy = properties.getProperty(key);
                if (!TextUtils.isEmpty(packageName) && !TextUtils.isEmpty(policy)) {
                    MEMORY_POLICIES.put(packageName, policy);
                }
            }
            Logx.i("fallback policy file loaded entries=" + MEMORY_POLICIES.size());
        } catch (Throwable throwable) {
            Logx.e("fallback policy file unavailable; using volatile policy storage", throwable);
        }
    }
}

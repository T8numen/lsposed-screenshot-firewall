package t8numen.screenshotfirewall;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.BroadcastReceiver;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class OverlayPermissionActivity extends Activity {
    private static final int TAB_RULES = 0;
    private static final int TAB_EVENTS = 1;
    private static final int TAB_SETTINGS = 2;
    private static final int EVENT_MODE_TIMELINE = 0;
    private static final int EVENT_MODE_PACKAGE = 1;

    private static final int COLOR_BG = 0xFFF6F7FB;
    private static final int COLOR_SURFACE = 0xFFFFFFFF;
    private static final int COLOR_TEXT = 0xFF111827;
    private static final int COLOR_TEXT_SECONDARY = 0xFF64748B;
    private static final int COLOR_LINE = 0xFFE5E7EB;
    private static final int COLOR_ACCENT = 0xFF2563EB;
    private static final int COLOR_GREEN = 0xFF15803D;
    private static final int COLOR_GREEN_BG = 0xFFEAF7EE;
    private static final int COLOR_RED = 0xFFB91C1C;
    private static final int COLOR_RED_BG = 0xFFFCE8E8;

    private TextView subtitleView;
    private TextView summaryView;
    private TextView rulesTab;
    private TextView eventsTab;
    private TextView settingsTab;
    private TextView eventTimelineTab;
    private TextView eventPackageTab;
    private SeekBar cooldownSeekBarView;
    private EditText cooldownInputView;
    private LinearLayout contentRoot;
    private BroadcastReceiver policyReceiver;

    private int selectedTab = TAB_RULES;
    private int selectedEventMode = EVENT_MODE_TIMELINE;
    private String defaultPolicy = PolicyController.POLICY_ALLOW;
    private boolean overrideRules;
    private double cooldownSeconds = ExternalPolicyStore.Config.DEFAULT_COOLDOWN_SECONDS;
    private boolean suppressCooldownInputEvents;
    private final ArrayList<PolicyEntry> policies = new ArrayList<>();
    private final ArrayList<FeatureEvent> events = new ArrayList<>();
    private final Map<String, PolicyEntry> currentPolicyMap = new HashMap<>();
    private final Set<String> expandedTimelineGroups = new HashSet<>();
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    private final SimpleDateFormat rangeDateFormat =
            new SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault());
    private final SimpleDateFormat rangeTimeFormat =
            new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        hideActionBar();
        styleSystemBars();
        registerPolicyReceiver();
        buildContentView();
        queryPolicies();
    }

    @Override
    protected void onResume() {
        super.onResume();
        queryPolicies();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (policyReceiver != null) {
            try {
                unregisterReceiver(policyReceiver);
            } catch (Throwable ignored) {
                // Receiver may already be gone if the Activity is finishing after process cleanup.
            }
            policyReceiver = null;
        }
    }

    private void hideActionBar() {
        try {
            if (getActionBar() != null) {
                getActionBar().hide();
            }
        } catch (Throwable ignored) {
            // Some OEM themes do not expose an ActionBar.
        }
    }

    @SuppressWarnings("deprecation")
    private void styleSystemBars() {
        Window window = getWindow();
        window.setStatusBarColor(COLOR_BG);
        window.setNavigationBarColor(COLOR_SURFACE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            window.getDecorView().setSystemUiVisibility(flags);
        }
    }

    private void buildContentView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BG);
        root.setPadding(dp(18), statusBarHeight() + dp(14), dp(18), 0);

        TextView title = new TextView(this);
        title.setText("系统侧截图防火墙");
        title.setTextColor(COLOR_TEXT);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title, matchWrap());

        subtitleView = new TextView(this);
        subtitleView.setTextColor(COLOR_TEXT_SECONDARY);
        subtitleView.setTextSize(14);
        subtitleView.setPadding(0, dp(6), 0, 0);
        root.addView(subtitleView, matchWrap());

        summaryView = new TextView(this);
        summaryView.setTextColor(COLOR_TEXT_SECONDARY);
        summaryView.setTextSize(13);
        summaryView.setPadding(0, dp(12), 0, dp(12));
        root.addView(summaryView, matchWrap());

        root.addView(tabBar(), matchWrap());

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(false);
        scrollView.setClipToPadding(false);
        scrollView.setPadding(0, dp(12), 0, dp(24));

        contentRoot = new LinearLayout(this);
        contentRoot.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(contentRoot, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        root.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f));
        setContentView(root);
        render();
    }

    private LinearLayout tabBar() {
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(dp(3), dp(3), dp(3), dp(3));
        tabs.setBackground(rounded(COLOR_LINE, 8));

        rulesTab = tab("规则", TAB_RULES);
        eventsTab = tab("最近事件", TAB_EVENTS);
        settingsTab = tab("全局设置", TAB_SETTINGS);
        tabs.addView(rulesTab, new LinearLayout.LayoutParams(0, dp(42), 1f));
        tabs.addView(eventsTab, new LinearLayout.LayoutParams(0, dp(42), 1f));
        tabs.addView(settingsTab, new LinearLayout.LayoutParams(0, dp(42), 1f));
        updateTabs();
        return tabs;
    }

    private TextView tab(String text, final int tab) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setGravity(Gravity.CENTER);
        view.setTextSize(14);
        view.setSingleLine(true);
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (selectedTab != tab) {
                    selectedTab = tab;
                    render();
                }
            }
        });
        return view;
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerPolicyReceiver() {
        policyReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null
                        || !PromptContract.ACTION_POLICY_RESULT.equals(intent.getAction())) {
                    return;
                }
                parsePolicies(intent.getStringArrayListExtra(PromptContract.EXTRA_POLICY_ENTRIES));
                parseEvents(intent.getStringArrayListExtra(PromptContract.EXTRA_EVENT_ENTRIES));
                parseConfig(intent);
                render();
            }
        };
        IntentFilter filter = new IntentFilter(PromptContract.ACTION_POLICY_RESULT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(policyReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(policyReceiver, filter);
        }
    }

    private void parsePolicies(ArrayList<String> entries) {
        policies.clear();
        currentPolicyMap.clear();
        if (entries == null) {
            return;
        }
        for (String entry : entries) {
            int index = entry == null ? -1 : entry.indexOf('=');
            if (index <= 0 || index >= entry.length() - 1) {
                continue;
            }
            String key = entry.substring(0, index);
            String feature = ExternalPolicyStore.policyFeature(key);
            String packageName = ExternalPolicyStore.policyPackage(key);
            String activityName = ExternalPolicyStore.policyActivity(key);
            String policy = entry.substring(index + 1);
            if (TextUtils.isEmpty(feature)
                    || TextUtils.isEmpty(packageName)
                    || TextUtils.isEmpty(policy)) {
                continue;
            }
            PolicyEntry policyEntry = new PolicyEntry(
                    feature,
                    packageName,
                    activityName,
                    appLabel(packageName),
                    policy);
            policies.add(policyEntry);
            currentPolicyMap.put(policyEntry.key(), policyEntry);
        }
    }

    private void parseEvents(ArrayList<String> entries) {
        events.clear();
        if (entries == null) {
            return;
        }
        for (String entry : entries) {
            String[] parts = entry == null
                    ? new String[0]
                    : entry.split(ExternalPolicyStore.eventFieldSeparator(), -1);
            if (parts.length < 5) {
                continue;
            }
            try {
                long timeMillis = Long.parseLong(parts[0]);
                String feature = ExternalPolicyStore.decodeValue(parts[1]);
                String packageName = ExternalPolicyStore.decodeValue(parts[2]);
                String label = ExternalPolicyStore.decodeValue(parts[3]);
                String activityName = ExternalPolicyStore.decodeValue(parts[4]);
                if (TextUtils.isEmpty(label)) {
                    label = appLabel(packageName);
                }
                if (!TextUtils.isEmpty(feature) && !TextUtils.isEmpty(packageName)) {
                    events.add(new FeatureEvent(
                            timeMillis,
                            feature,
                            packageName,
                            label,
                            activityName));
                }
            } catch (Throwable ignored) {
                // Ignore malformed event rows from older builds.
            }
        }
    }

    private void parseConfig(Intent intent) {
        String policy = intent.getStringExtra(PromptContract.EXTRA_DEFAULT_POLICY);
        boolean block = PolicyController.POLICY_BLOCK.equals(policy);
        defaultPolicy = block ? PolicyController.POLICY_BLOCK : PolicyController.POLICY_ALLOW;
        overrideRules = intent.getBooleanExtra(PromptContract.EXTRA_OVERRIDE_RULES, false);
        ExternalPolicyStore.Config config = new ExternalPolicyStore.Config(
                defaultPolicy,
                overrideRules,
                intent.getDoubleExtra(
                        PromptContract.EXTRA_COOLDOWN_SECONDS,
                        ExternalPolicyStore.Config.DEFAULT_COOLDOWN_SECONDS));
        cooldownSeconds = config.cooldownSeconds;
    }

    private void render() {
        if (contentRoot == null) {
            return;
        }
        updateTabs();
        updateSummary();
        contentRoot.removeAllViews();
        if (selectedTab == TAB_RULES) {
            renderRules();
        } else if (selectedTab == TAB_EVENTS) {
            renderEvents();
        } else {
            renderGlobalSettings();
        }
    }

    private void updateSummary() {
        if (subtitleView != null) {
            subtitleView.setText(PolicyDisplayText.defaultSummary(defaultPolicy)
                    + "，"
                    + (overrideRules ? "全局策略覆盖单独规则" : "单独规则优先"));
        }
        if (summaryView == null) {
            return;
        }
        summaryView.setText(PolicyDisplayText.defaultSummary(defaultPolicy)
                + " · "
                + (overrideRules ? "全局覆盖" : "规则优先")
                + " · 冷却 "
                + cooldownText()
                + " · 规则 "
                + policies.size()
                + " · 事件 "
                + events.size());
    }

    private void updateTabs() {
        styleTab(rulesTab, selectedTab == TAB_RULES);
        styleTab(eventsTab, selectedTab == TAB_EVENTS);
        styleTab(settingsTab, selectedTab == TAB_SETTINGS);
    }

    private void styleTab(TextView tab, boolean selected) {
        if (tab == null) {
            return;
        }
        tab.setTextColor(selected ? COLOR_TEXT : COLOR_TEXT_SECONDARY);
        tab.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        tab.setBackground(selected ? rounded(COLOR_SURFACE, 7) : null);
    }

    private void renderRules() {
        contentRoot.addView(newRuleButton(), matchWrap());

        sectionTitle("应用禁止截图 (FLAG_SECURE)");
        int flagCount = renderPolicySection(Features.FLAG_SECURE);
        if (flagCount == 0) {
            emptyRow(PolicyController.POLICY_BLOCK.equals(defaultPolicy)
                    ? "暂无 FLAG_SECURE 规则。当前全局默认会解除应用的禁止截图限制。"
                    : "暂无 FLAG_SECURE 规则。当前全局默认会保留应用的禁止截图限制。");
        }

        sectionTitle("截图回执检测");
        int observerCount = renderPolicySection(Features.SCREEN_CAPTURE_OBSERVER);
        if (observerCount == 0) {
            emptyRow(PolicyController.POLICY_BLOCK.equals(defaultPolicy)
                    ? "暂无截图回执规则。当前全局默认会阻止应用收到截图通知。"
                    : "暂无截图回执规则。当前全局默认会允许应用收到截图通知。");
        }

    }

    private TextView newRuleButton() {
        TextView button = actionText("新建规则", COLOR_ACCENT, COLOR_SURFACE, COLOR_ACCENT);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showPolicyEditor(null);
            }
        });
        return button;
    }

    private void renderGlobalSettings() {
        sectionTitle("全局设置");

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(14));
        card.setBackground(rounded(COLOR_SURFACE, 8, COLOR_LINE));
        LinearLayout.LayoutParams cardParams = matchWrap();
        cardParams.topMargin = dp(10);

        card.addView(settingLabel("默认策略"), matchWrap());
        card.addView(settingNote(PolicyDisplayText.defaultHelp(defaultPolicy)), settingNoteParams());
        LinearLayout defaultSegments = segmentedControl();
        defaultSegments.addView(configSegment(
                "保留/允许",
                PolicyController.POLICY_ALLOW.equals(defaultPolicy),
                COLOR_GREEN,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        setDefaultPolicy(PolicyController.POLICY_ALLOW);
                    }
                }), new LinearLayout.LayoutParams(0, dp(38), 1f));
        defaultSegments.addView(configSegment(
                "解除/阻止",
                PolicyController.POLICY_BLOCK.equals(defaultPolicy),
                COLOR_RED,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        setDefaultPolicy(PolicyController.POLICY_BLOCK);
                    }
                }), new LinearLayout.LayoutParams(0, dp(38), 1f));
        card.addView(defaultSegments, settingControlParams());

        card.addView(settingDivider());
        card.addView(settingLabel("覆盖单独规则"), matchWrap());
        card.addView(settingNote("关闭时包名规则优先；开启时全局默认覆盖所有规则。"), settingNoteParams());
        LinearLayout overrideSegments = segmentedControl();
        overrideSegments.addView(configSegment(
                "关闭",
                !overrideRules,
                COLOR_TEXT_SECONDARY,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        setOverrideRules(false);
                    }
                }), new LinearLayout.LayoutParams(0, dp(38), 1f));
        overrideSegments.addView(configSegment(
                "开启",
                overrideRules,
                COLOR_ACCENT,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        setOverrideRules(true);
                    }
                }), new LinearLayout.LayoutParams(0, dp(38), 1f));
        card.addView(overrideSegments, settingControlParams());

        card.addView(settingDivider());
        LinearLayout cooldownHeader = new LinearLayout(this);
        cooldownHeader.setOrientation(LinearLayout.HORIZONTAL);
        cooldownHeader.setGravity(Gravity.CENTER_VERTICAL);
        cooldownHeader.addView(settingLabel("Toast / 事件冷却"), new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f));
        cooldownInputView = cooldownInput();
        cooldownHeader.addView(cooldownInputView, new LinearLayout.LayoutParams(
                dp(92),
                dp(42)));
        card.addView(cooldownHeader, matchWrap());
        card.addView(settingNote("同一应用同一功能重复提示的最短间隔。范围 5.0-60.0 秒。"), settingNoteParams());
        card.addView(cooldownSeekBar(), settingControlParams());

        contentRoot.addView(card, cardParams);
    }

    private TextView settingLabel(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(COLOR_TEXT);
        view.setTextSize(14);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private TextView settingNote(String text) {
        TextView view = secondary(text, 2);
        view.setTextSize(12);
        return view;
    }

    private LinearLayout segmentedControl() {
        LinearLayout control = new LinearLayout(this);
        control.setOrientation(LinearLayout.HORIZONTAL);
        control.setPadding(dp(3), dp(3), dp(3), dp(3));
        control.setBackground(rounded(COLOR_LINE, 8));
        return control;
    }

    private TextView configSegment(
            String text,
            boolean selected,
            int selectedColor,
            View.OnClickListener listener) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setGravity(Gravity.CENTER);
        view.setTextSize(13);
        view.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        view.setTextColor(selected ? selectedColor : COLOR_TEXT_SECONDARY);
        view.setSingleLine(true);
        view.setBackground(selected ? rounded(COLOR_SURFACE, 7) : null);
        view.setOnClickListener(listener);
        return view;
    }

    private SeekBar cooldownSeekBar() {
        SeekBar seekBar = new SeekBar(this);
        cooldownSeekBarView = seekBar;
        seekBar.setMax(cooldownMaxProgress());
        seekBar.setProgress(cooldownProgress(cooldownSeconds));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                cooldownSeconds = cooldownFromProgress(progress);
                updateCooldownDisplay();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Nothing to do.
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                cooldownSeconds = cooldownFromProgress(seekBar.getProgress());
                updateCooldownDisplay();
                sendConfigSet();
            }
        });
        return seekBar;
    }

    private EditText cooldownInput() {
        final EditText input = new EditText(this);
        input.setText(cooldownNumberText());
        input.setTextColor(COLOR_TEXT);
        input.setTextSize(13);
        input.setTypeface(Typeface.DEFAULT_BOLD);
        input.setGravity(Gravity.CENTER);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setPadding(dp(8), 0, dp(8), 0);
        input.setBackground(rounded(COLOR_SURFACE, 8, COLOR_LINE));
        input.setFilters(new InputFilter[] {new OneDecimalInputFilter()});
        input.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
                // Nothing to do.
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                // Nothing to do.
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (suppressCooldownInputEvents) {
                    return;
                }
                Double value = parseCooldown(editable == null ? null : editable.toString());
                if (value == null) {
                    return;
                }
                cooldownSeconds = normalizedCooldown(value);
                syncCooldownSeekBar();
                updateSummary();
            }
        });
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView view, int actionId, android.view.KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    input.clearFocus();
                    return true;
                }
                return false;
            }
        });
        input.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                if (!hasFocus) {
                    commitCooldownInput();
                }
            }
        });
        return input;
    }

    private void setDefaultPolicy(String policy) {
        if (TextUtils.equals(defaultPolicy, policy)) {
            return;
        }
        defaultPolicy = PolicyController.POLICY_BLOCK.equals(policy)
                ? PolicyController.POLICY_BLOCK
                : PolicyController.POLICY_ALLOW;
        sendConfigSet();
        render();
    }

    private void setOverrideRules(boolean enabled) {
        if (overrideRules == enabled) {
            return;
        }
        overrideRules = enabled;
        sendConfigSet();
        render();
    }

    private void updateCooldownDisplay() {
        syncCooldownSeekBar();
        syncCooldownInput();
        updateSummary();
    }

    private View settingDivider() {
        View view = new View(this);
        view.setBackgroundColor(COLOR_LINE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1));
        params.topMargin = dp(14);
        params.bottomMargin = dp(12);
        view.setLayoutParams(params);
        return view;
    }

    private LinearLayout.LayoutParams settingNoteParams() {
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(3);
        return params;
    }

    private LinearLayout.LayoutParams settingControlParams() {
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(9);
        return params;
    }

    private int renderPolicySection(String feature) {
        int count = 0;
        for (PolicyEntry policy : policies) {
            if (feature.equals(policy.feature)) {
                contentRoot.addView(policyRow(policy));
                count++;
            }
        }
        return count;
    }

    private void renderEvents() {
        contentRoot.addView(clearEventsButton(), matchWrap());
        contentRoot.addView(eventModeBar(), matchWrap());
        if (events.isEmpty()) {
            emptyRow("暂未记录到应用禁止截图或注册截图回执。");
        } else if (selectedEventMode == EVENT_MODE_PACKAGE) {
            renderEventsByPackage();
        } else {
            renderEventsTimeline();
        }
    }

    private TextView clearEventsButton() {
        TextView clear = actionText("清除事件记录", COLOR_TEXT_SECONDARY, COLOR_SURFACE, COLOR_LINE);
        clear.setEnabled(!events.isEmpty());
        clear.setAlpha(events.isEmpty() ? 0.45f : 1f);
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendSimpleCommand(PromptContract.ACTION_EVENTS_CLEAR);
            }
        });
        return clear;
    }

    private void renderEventsTimeline() {
        for (TimelineEventGroup group : timelineEventGroups()) {
            contentRoot.addView(eventGroupCard(group));
        }
    }

    private ArrayList<TimelineEventGroup> timelineEventGroups() {
        ArrayList<TimelineEventGroup> groups = new ArrayList<>();
        TimelineEventGroup current = null;
        for (FeatureEvent event : events) {
            if (current == null || !current.accepts(event)) {
                current = new TimelineEventGroup(event);
                groups.add(current);
            }
            current.add(event);
        }
        return groups;
    }

    private void renderEventsByPackage() {
        LinkedHashMap<String, ArrayList<FeatureEvent>> grouped = new LinkedHashMap<>();
        for (FeatureEvent event : events) {
            ArrayList<FeatureEvent> group = grouped.get(event.packageName);
            if (group == null) {
                group = new ArrayList<>();
                grouped.put(event.packageName, group);
            }
            group.add(event);
        }

        for (Map.Entry<String, ArrayList<FeatureEvent>> entry : grouped.entrySet()) {
            ArrayList<FeatureEvent> group = entry.getValue();
            if (group.isEmpty()) {
                continue;
            }
            contentRoot.addView(packageGroupHeader(group.get(0), group.size()));
            for (FeatureEvent event : group) {
                contentRoot.addView(eventRow(event, false));
            }
        }
    }

    private View policyRow(final PolicyEntry policy) {
        LinearLayout row = cardRow();
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showPolicyEditor(policy);
            }
        });

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(primary(policy.appLabel), matchWrap());
        texts.addView(copyableSecondary(policy.packageName, 1, "包名", policy.packageName), matchWrap());
        TextView activity = copyableSecondary(
                "Activity：" + policyScopeText(policy.packageName, policy.activityName),
                1,
                "Activity",
                policy.activityName);
        LinearLayout.LayoutParams activityParams = matchWrap();
        activityParams.topMargin = dp(2);
        texts.addView(activity, activityParams);

        LinearLayout meta = new LinearLayout(this);
        meta.setOrientation(LinearLayout.HORIZONTAL);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        meta.addView(chip(
                PolicyDisplayText.feature(policy.feature),
                0xFFF1F5F9,
                COLOR_TEXT_SECONDARY));
        meta.addView(chip(
                PolicyDisplayText.policy(policy.feature, policy.policy),
                policyBg(policy.feature, policy.policy),
                policyColor(policy.feature, policy.policy)));
        LinearLayout.LayoutParams metaParams = matchWrap();
        metaParams.topMargin = dp(7);
        texts.addView(meta, metaParams);

        row.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView clear = actionText("撤销", COLOR_TEXT_SECONDARY, COLOR_SURFACE, COLOR_LINE);
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendPolicyClear(policy.packageName, policy.feature, policy.activityName);
            }
        });
        row.addView(clear, trailingActionParams());
        return row;
    }

    private void showPolicyEditor(final PolicyEntry existing) {
        final LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int horizontalPadding = dp(2);
        form.setPadding(horizontalPadding, dp(8), horizontalPadding, 0);

        final EditText packageInput = dialogInput("包名");
        final EditText activityInput = dialogInput("Activity（可空，空表示整个包）");
        if (existing != null) {
            packageInput.setText(existing.packageName);
            activityInput.setText(existing.activityName);
        }

        form.addView(dialogLabel("功能"), matchWrap());
        final RadioGroup featureGroup = new RadioGroup(this);
        featureGroup.setOrientation(RadioGroup.VERTICAL);
        final int flagId = View.generateViewId();
        final int observerId = View.generateViewId();
        featureGroup.addView(radioButton(
                flagId,
                PolicyDisplayText.feature(Features.FLAG_SECURE)));
        featureGroup.addView(radioButton(
                observerId,
                PolicyDisplayText.feature(Features.SCREEN_CAPTURE_OBSERVER)));
        featureGroup.check(existing == null || Features.FLAG_SECURE.equals(existing.feature)
                ? flagId
                : observerId);
        form.addView(featureGroup, dialogGroupParams());

        form.addView(dialogLabel("包名"), dialogTopParams());
        form.addView(packageInput, matchWrap());
        form.addView(dialogLabel("Activity"), dialogTopParams());
        form.addView(activityInput, matchWrap());

        form.addView(dialogLabel("结果"), dialogTopParams());
        final RadioGroup policyGroup = new RadioGroup(this);
        policyGroup.setOrientation(RadioGroup.VERTICAL);
        final int allowId = View.generateViewId();
        final int blockId = View.generateViewId();
        policyGroup.addView(radioButton(allowId, "允许原始行为"));
        policyGroup.addView(radioButton(blockId, "拒绝对应能力"));
        policyGroup.check(existing == null || PolicyController.POLICY_ALLOW.equals(existing.policy)
                ? allowId
                : blockId);
        form.addView(policyGroup, dialogGroupParams());

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "新建规则" : "编辑规则")
                .setView(form)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                String packageName = cleanInput(packageInput);
                                if (TextUtils.isEmpty(packageName)) {
                                    Toast.makeText(
                                            OverlayPermissionActivity.this,
                                            "包名不能为空",
                                            Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                String activityName = cleanInput(activityInput);
                                String feature = featureGroup.getCheckedRadioButtonId() == observerId
                                        ? Features.SCREEN_CAPTURE_OBSERVER
                                        : Features.FLAG_SECURE;
                                String policy = policyGroup.getCheckedRadioButtonId() == blockId
                                        ? PolicyController.POLICY_BLOCK
                                        : PolicyController.POLICY_ALLOW;
                                sendPolicySet(packageName, feature, activityName, policy, existing);
                                dialog.dismiss();
                            }
                        });
            }
        });
        dialog.show();
    }

    private TextView dialogLabel(String text) {
        TextView label = settingLabel(text);
        label.setTextSize(13);
        return label;
    }

    private EditText dialogInput(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setTextColor(COLOR_TEXT);
        input.setTextSize(14);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        input.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        return input;
    }

    private RadioButton radioButton(int id, String text) {
        RadioButton button = new RadioButton(this);
        button.setId(id);
        button.setText(text);
        button.setTextColor(COLOR_TEXT);
        button.setTextSize(14);
        button.setMinHeight(dp(38));
        return button;
    }

    private LinearLayout.LayoutParams dialogTopParams() {
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(12);
        return params;
    }

    private LinearLayout.LayoutParams dialogGroupParams() {
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(4);
        return params;
    }

    private String cleanInput(EditText input) {
        if (input == null || input.getText() == null) {
            return "";
        }
        return input.getText().toString().trim();
    }

    private LinearLayout eventModeBar() {
        LinearLayout modes = new LinearLayout(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        modes.setPadding(dp(3), dp(3), dp(3), dp(3));
        modes.setBackground(rounded(COLOR_LINE, 8));
        LinearLayout.LayoutParams params = matchWrap();
        params.bottomMargin = dp(4);
        modes.setLayoutParams(params);

        eventTimelineTab = eventModeTab("时间线", EVENT_MODE_TIMELINE);
        eventPackageTab = eventModeTab("按包名", EVENT_MODE_PACKAGE);
        modes.addView(eventTimelineTab, new LinearLayout.LayoutParams(0, dp(38), 1f));
        modes.addView(eventPackageTab, new LinearLayout.LayoutParams(0, dp(38), 1f));
        updateEventModeTabs();
        return modes;
    }

    private TextView eventModeTab(String text, final int mode) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setGravity(Gravity.CENTER);
        view.setTextSize(13);
        view.setSingleLine(true);
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (selectedEventMode != mode) {
                    selectedEventMode = mode;
                    render();
                }
            }
        });
        return view;
    }

    private void updateEventModeTabs() {
        styleEventModeTab(eventTimelineTab, selectedEventMode == EVENT_MODE_TIMELINE);
        styleEventModeTab(eventPackageTab, selectedEventMode == EVENT_MODE_PACKAGE);
    }

    private void styleEventModeTab(TextView tab, boolean selected) {
        if (tab == null) {
            return;
        }
        tab.setTextColor(selected ? COLOR_TEXT : COLOR_TEXT_SECONDARY);
        tab.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        tab.setBackground(selected ? rounded(COLOR_SURFACE, 7) : null);
    }

    private View packageGroupHeader(FeatureEvent event, int count) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(0, dp(16), 0, dp(2));

        TextView title = new TextView(this);
        title.setText(event.appLabel + " · " + count + " 条");
        title.setTextColor(COLOR_TEXT);
        title.setTextSize(16);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        header.addView(title, matchWrap());

        TextView packageName = copyableSecondary(event.packageName, 1, "包名", event.packageName);
        LinearLayout.LayoutParams packageParams = matchWrap();
        packageParams.topMargin = dp(2);
        header.addView(packageName, packageParams);
        return header;
    }

    private View eventGroupCard(final TimelineEventGroup group) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(rounded(COLOR_SURFACE, 8, COLOR_LINE));
        LinearLayout.LayoutParams cardParams = matchWrap();
        cardParams.topMargin = dp(10);
        card.setLayoutParams(cardParams);

        card.addView(primary(group.appLabel), matchWrap());
        TextView range = secondary(eventRangeText(group), 2);
        LinearLayout.LayoutParams rangeParams = matchWrap();
        rangeParams.topMargin = dp(4);
        card.addView(range, rangeParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        PolicyEntry current = currentPolicy(group.packageName, group.feature);
        addPolicyAction(
                actions,
                PolicyDisplayText.policy(group.feature, PolicyController.POLICY_ALLOW),
                group.packageName,
                group.feature,
                PolicyController.POLICY_ALLOW,
                current);
        addPolicyAction(
                actions,
                PolicyDisplayText.policy(group.feature, PolicyController.POLICY_BLOCK),
                group.packageName,
                group.feature,
                PolicyController.POLICY_BLOCK,
                current);
        TextView revoke = actionText("撤销", COLOR_TEXT_SECONDARY, COLOR_SURFACE, COLOR_LINE);
        boolean hasPolicy = current != null;
        revoke.setEnabled(hasPolicy);
        revoke.setAlpha(hasPolicy ? 1f : 0.45f);
        revoke.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendPolicyClear(group.packageName, group.feature, "");
            }
        });
        actions.addView(revoke, smallActionParams());
        LinearLayout.LayoutParams actionParams = matchWrap();
        actionParams.topMargin = dp(8);
        card.addView(actions, actionParams);

        card.addView(copyableSecondary("包名：" + group.packageName, 2, "包名", group.packageName),
                detailParams());
        boolean expanded = expandedTimelineGroups.contains(group.key);
        card.addView(expandToggleButton(group, expanded ? "折叠" : "展开 " + group.eventCount + " 条"));
        if (!expanded) {
            return card;
        }
        for (TimelineActivityGroup activityGroup : group.activityGroups) {
            card.addView(activityTitle(group.packageName, activityGroup.activityName), detailParams());
            for (FeatureEvent event : activityGroup.events) {
                TextView time = secondary(dateFormat.format(new Date(event.timeMillis)), 1);
                time.setPadding(dp(8), 0, 0, 0);
                LinearLayout.LayoutParams timeParams = matchWrap();
                timeParams.topMargin = dp(2);
                card.addView(time, timeParams);
            }
        }
        card.addView(expandToggleButton(group, "折叠"));
        return card;
    }

    private TextView expandToggleButton(final TimelineEventGroup group, String text) {
        TextView button = actionText(text, COLOR_TEXT_SECONDARY, COLOR_SURFACE, COLOR_LINE);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (expandedTimelineGroups.contains(group.key)) {
                    expandedTimelineGroups.remove(group.key);
                } else {
                    expandedTimelineGroups.add(group.key);
                }
                render();
            }
        });
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(8);
        button.setLayoutParams(params);
        return button;
    }

    private String eventRangeText(TimelineEventGroup group) {
        return rangeText(group.startTimeMillis, group.endTimeMillis);
    }

    private TextView activityTitle(String packageName, String activityName) {
        TextView view = secondary(
                "Activity：" + displayActivityName(packageName, activityName),
                2);
        view.setTextColor(COLOR_TEXT);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        attachCopy(view, "Activity", activityName);
        return view;
    }

    private String rangeText(long startTimeMillis, long endTimeMillis) {
        Date start = new Date(startTimeMillis);
        Date end = new Date(endTimeMillis);
        String endText = sameDay(startTimeMillis, endTimeMillis)
                ? rangeTimeFormat.format(end)
                : rangeDateFormat.format(end);
        return rangeDateFormat.format(start) + " -- " + endText;
    }

    private boolean sameDay(long leftMillis, long rightMillis) {
        Calendar left = Calendar.getInstance();
        Calendar right = Calendar.getInstance();
        left.setTimeInMillis(leftMillis);
        right.setTimeInMillis(rightMillis);
        return left.get(Calendar.YEAR) == right.get(Calendar.YEAR)
                && left.get(Calendar.DAY_OF_YEAR) == right.get(Calendar.DAY_OF_YEAR);
    }

    private String displayActivityName(String packageName, String activityName) {
        if (TextUtils.isEmpty(activityName)) {
            return "未知";
        }
        String display = activityName;
        int slashIndex = display.lastIndexOf('/');
        if (slashIndex >= 0 && slashIndex < display.length() - 1) {
            display = display.substring(slashIndex + 1);
        }
        if (!TextUtils.isEmpty(packageName) && display.startsWith(packageName + ".")) {
            display = display.substring(packageName.length() + 1);
        }
        if (display.startsWith(".")) {
            display = display.substring(1);
        }
        int dotIndex = display.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < display.length() - 1) {
            display = display.substring(dotIndex + 1);
        }
        return TextUtils.isEmpty(display) ? "未知" : display;
    }

    private String policyScopeText(String packageName, String activityName) {
        return TextUtils.isEmpty(activityName)
                ? "全部 Activity"
                : displayActivityName(packageName, activityName);
    }

    private View eventRow(final FeatureEvent event, boolean showAppLabel) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(rounded(COLOR_SURFACE, 8, COLOR_LINE));
        LinearLayout.LayoutParams cardParams = matchWrap();
        cardParams.topMargin = dp(10);
        card.setLayoutParams(cardParams);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleTexts = new LinearLayout(this);
        titleTexts.setOrientation(LinearLayout.VERTICAL);
        if (showAppLabel) {
            titleTexts.addView(primary(event.appLabel), matchWrap());
        }
        titleTexts.addView(secondary(PolicyDisplayText.feature(event.feature)
                + " · " + dateFormat.format(new Date(event.timeMillis)), 1), matchWrap());
        top.addView(titleTexts, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f));

        card.addView(top, matchWrap());
        if (showAppLabel) {
            card.addView(copyableSecondary(
                    "包名：" + event.packageName,
                    2,
                    "包名",
                    event.packageName), detailParams());
        }
        if (!TextUtils.isEmpty(event.activityName)) {
            card.addView(copyableSecondary(
                    "Activity：" + displayActivityName(event.packageName, event.activityName),
                    2,
                    "Activity",
                    event.activityName), detailParams());
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        PolicyEntry current = currentPolicy(event);
        addEventAction(actions, PolicyDisplayText.policy(event.feature, PolicyController.POLICY_ALLOW),
                event, PolicyController.POLICY_ALLOW, current);
        addEventAction(actions, PolicyDisplayText.policy(event.feature, PolicyController.POLICY_BLOCK),
                event, PolicyController.POLICY_BLOCK, current);
        TextView revoke = actionText("撤销", COLOR_TEXT_SECONDARY, COLOR_SURFACE, COLOR_LINE);
        boolean hasPolicy = current != null;
        revoke.setEnabled(hasPolicy);
        revoke.setAlpha(hasPolicy ? 1f : 0.45f);
        revoke.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendPolicyClear(event.packageName, event.feature, "");
            }
        });
        actions.addView(revoke, smallActionParams());

        LinearLayout.LayoutParams actionParams = matchWrap();
        actionParams.topMargin = dp(10);
        card.addView(actions, actionParams);
        return card;
    }

    private void addEventAction(
            LinearLayout actions,
            String text,
            final FeatureEvent event,
            final String policy,
            PolicyEntry current) {
        addPolicyAction(actions, text, event.packageName, event.feature, policy, current);
    }

    private void addPolicyAction(
            LinearLayout actions,
            String text,
            final String packageName,
            final String feature,
            final String policy,
            PolicyEntry current) {
        boolean selected = current != null && policy.equals(current.policy);
        int color = PolicyController.POLICY_BLOCK.equals(policy) ? COLOR_RED : COLOR_GREEN;
        int bg = selected
                ? (PolicyController.POLICY_BLOCK.equals(policy) ? COLOR_RED_BG : COLOR_GREEN_BG)
                : COLOR_SURFACE;
        TextView action = actionText(text, color, bg, selected ? color : COLOR_LINE);
        action.setEnabled(!selected);
        action.setAlpha(selected ? 0.82f : 1f);
        action.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendPolicySet(packageName, feature, "", policy, null);
            }
        });
        actions.addView(action, smallActionParams());
    }

    private PolicyEntry currentPolicy(FeatureEvent event) {
        return currentPolicy(event.packageName, event.feature);
    }

    private PolicyEntry currentPolicy(String packageName, String feature) {
        return currentPolicyMap.get(ExternalPolicyStore.policyKey(feature, packageName));
    }

    private void sectionTitle(String text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextColor(COLOR_TEXT);
        title.setTextSize(17);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = contentRoot.getChildCount() == 0 ? dp(2) : dp(22);
        contentRoot.addView(title, params);
    }

    private void emptyRow(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(COLOR_TEXT_SECONDARY);
        view.setTextSize(14);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(dp(14), dp(12), dp(14), dp(12));
        view.setBackground(rounded(COLOR_SURFACE, 8, COLOR_LINE));
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(10);
        contentRoot.addView(view, params);
    }

    private LinearLayout cardRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(12), dp(12));
        row.setBackground(rounded(COLOR_SURFACE, 8, COLOR_LINE));
        LinearLayout.LayoutParams rowParams = matchWrap();
        rowParams.topMargin = dp(10);
        row.setLayoutParams(rowParams);
        return row;
    }

    private TextView primary(String text) {
        TextView view = new TextView(this);
        view.setText(TextUtils.isEmpty(text) ? "未知应用" : text);
        view.setTextColor(COLOR_TEXT);
        view.setTextSize(15);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        return view;
    }

    private TextView secondary(String text, int maxLines) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(COLOR_TEXT_SECONDARY);
        view.setTextSize(13);
        view.setMaxLines(maxLines);
        view.setEllipsize(TextUtils.TruncateAt.END);
        return view;
    }

    private TextView copyableSecondary(String text, int maxLines, String label, String value) {
        TextView view = secondary(text, maxLines);
        attachCopy(view, label, value);
        return view;
    }

    private void attachCopy(View view, final String label, final String value) {
        if (view == null || TextUtils.isEmpty(value)) {
            return;
        }
        view.setLongClickable(true);
        view.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                copyText(label, value);
                return true;
            }
        });
    }

    private void copyText(String label, String value) {
        if (TextUtils.isEmpty(value)) {
            return;
        }
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(label, value));
        }
        Toast.makeText(this, "已复制" + label, Toast.LENGTH_SHORT).show();
    }

    private TextView chip(String text, int bgColor, int textColor) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(textColor);
        view.setTextSize(12);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(8), dp(3), dp(8), dp(3));
        view.setBackground(rounded(bgColor, 999));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.rightMargin = dp(6);
        view.setLayoutParams(params);
        return view;
    }

    private TextView actionText(String text, int textColor, int bgColor, int strokeColor) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(textColor);
        view.setTextSize(13);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setMinHeight(dp(44));
        view.setPadding(dp(12), 0, dp(12), 0);
        view.setBackground(rounded(bgColor, 8, strokeColor));
        view.setClickable(true);
        return view;
    }

    private LinearLayout.LayoutParams trailingActionParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(82), dp(44));
        params.leftMargin = dp(12);
        return params;
    }

    private LinearLayout.LayoutParams smallActionParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(76), dp(40));
        params.leftMargin = dp(8);
        return params;
    }

    private LinearLayout.LayoutParams detailParams() {
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(6);
        return params;
    }

    private String appLabel(String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return "未知应用";
        }
        try {
            PackageManager packageManager = getPackageManager();
            ApplicationInfo info = packageManager.getApplicationInfo(packageName, 0);
            CharSequence label = packageManager.getApplicationLabel(info);
            return TextUtils.isEmpty(label) ? packageName : label.toString();
        } catch (Throwable ignored) {
            return packageName;
        }
    }

    private int policyColor(String feature, String policy) {
        if (PolicyController.POLICY_BLOCK.equals(policy)) {
            return COLOR_GREEN;
        }
        if (PolicyController.POLICY_ALLOW.equals(policy)) {
            return COLOR_RED;
        }
        return COLOR_TEXT_SECONDARY;
    }

    private int policyBg(String feature, String policy) {
        if (PolicyController.POLICY_BLOCK.equals(policy)) {
            return COLOR_GREEN_BG;
        }
        if (PolicyController.POLICY_ALLOW.equals(policy)) {
            return COLOR_RED_BG;
        }
        return 0xFFF1F5F9;
    }

    private String cooldownText() {
        return String.format(Locale.getDefault(), "%.1f 秒", cooldownSeconds);
    }

    private String cooldownNumberText() {
        return String.format(Locale.getDefault(), "%.1f", cooldownSeconds);
    }

    private int cooldownMaxProgress() {
        return Math.round((float) ((ExternalPolicyStore.Config.MAX_COOLDOWN_SECONDS
                - ExternalPolicyStore.Config.MIN_COOLDOWN_SECONDS) * 10.0d));
    }

    private int cooldownProgress(double seconds) {
        ExternalPolicyStore.Config config = new ExternalPolicyStore.Config(
                defaultPolicy,
                overrideRules,
                seconds);
        return Math.round((float) ((config.cooldownSeconds
                - ExternalPolicyStore.Config.MIN_COOLDOWN_SECONDS) * 10.0d));
    }

    private double cooldownFromProgress(int progress) {
        double value = ExternalPolicyStore.Config.MIN_COOLDOWN_SECONDS + (progress / 10.0d);
        return normalizedCooldown(value);
    }

    private double normalizedCooldown(double seconds) {
        return new ExternalPolicyStore.Config(defaultPolicy, overrideRules, seconds).cooldownSeconds;
    }

    private Double parseCooldown(String raw) {
        if (TextUtils.isEmpty(raw)) {
            return null;
        }
        try {
            return Double.parseDouble(raw);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void commitCooldownInput() {
        if (cooldownInputView == null) {
            return;
        }
        Double value = parseCooldown(cooldownInputView.getText() == null
                ? null
                : cooldownInputView.getText().toString());
        if (value != null) {
            cooldownSeconds = normalizedCooldown(value);
        }
        updateCooldownDisplay();
        sendConfigSet();
    }

    private void syncCooldownSeekBar() {
        if (cooldownSeekBarView != null) {
            int progress = cooldownProgress(cooldownSeconds);
            if (cooldownSeekBarView.getProgress() != progress) {
                cooldownSeekBarView.setProgress(progress);
            }
        }
    }

    private void syncCooldownInput() {
        if (cooldownInputView == null) {
            return;
        }
        String text = cooldownNumberText();
        if (TextUtils.equals(cooldownInputView.getText(), text)) {
            return;
        }
        suppressCooldownInputEvents = true;
        try {
            cooldownInputView.setText(text);
            cooldownInputView.setSelection(cooldownInputView.getText().length());
        } finally {
            suppressCooldownInputEvents = false;
        }
    }

    private void queryPolicies() {
        sendSimpleCommand(PromptContract.ACTION_POLICY_QUERY);
    }

    private void sendSimpleCommand(String action) {
        Intent intent = new Intent(action);
        intent.setPackage(PromptContract.SYSTEM_UI_PACKAGE);
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        sendBroadcast(intent);
    }

    private void sendPolicySet(
            String packageName,
            String feature,
            String activityName,
            String policy,
            PolicyEntry oldPolicy) {
        Intent intent = new Intent(PromptContract.ACTION_POLICY_SET);
        intent.setPackage(PromptContract.SYSTEM_UI_PACKAGE);
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        intent.putExtra(PromptContract.EXTRA_PACKAGE, packageName);
        intent.putExtra(
                PromptContract.EXTRA_ACTIVITY,
                TextUtils.isEmpty(activityName) ? "" : activityName);
        intent.putExtra(PromptContract.EXTRA_FEATURE, feature);
        intent.putExtra(PromptContract.EXTRA_POLICY, policy);
        if (oldPolicy != null
                && !TextUtils.equals(
                        oldPolicy.key(),
                        ExternalPolicyStore.policyKey(feature, packageName, activityName))) {
            intent.putExtra(PromptContract.EXTRA_OLD_PACKAGE, oldPolicy.packageName);
            intent.putExtra(PromptContract.EXTRA_OLD_ACTIVITY, oldPolicy.activityName);
            intent.putExtra(PromptContract.EXTRA_OLD_FEATURE, oldPolicy.feature);
        }
        sendBroadcast(intent);
    }

    private void sendConfigSet() {
        Intent intent = new Intent(PromptContract.ACTION_CONFIG_SET);
        intent.setPackage(PromptContract.SYSTEM_UI_PACKAGE);
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        intent.putExtra(PromptContract.EXTRA_DEFAULT_POLICY, defaultPolicy);
        intent.putExtra(PromptContract.EXTRA_OVERRIDE_RULES, overrideRules);
        intent.putExtra(PromptContract.EXTRA_COOLDOWN_SECONDS, cooldownSeconds);
        sendBroadcast(intent);
    }

    private void sendPolicyClear(String packageName, String feature, String activityName) {
        Intent intent = new Intent(PromptContract.ACTION_POLICY_CLEAR_PACKAGE);
        intent.setPackage(PromptContract.SYSTEM_UI_PACKAGE);
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        intent.putExtra(PromptContract.EXTRA_PACKAGE, packageName);
        intent.putExtra(
                PromptContract.EXTRA_ACTIVITY,
                TextUtils.isEmpty(activityName) ? "" : activityName);
        intent.putExtra(PromptContract.EXTRA_FEATURE, feature);
        sendBroadcast(intent);
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private GradientDrawable rounded(int color, int radiusDp, int strokeColor) {
        GradientDrawable drawable = rounded(color, radiusDp);
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int statusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : 0;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class OneDecimalInputFilter implements InputFilter {
        @Override
        public CharSequence filter(
                CharSequence source,
                int start,
                int end,
                Spanned dest,
                int dstart,
                int dend) {
            String left = dest == null ? "" : dest.subSequence(0, dstart).toString();
            String right = dest == null ? "" : dest.subSequence(dend, dest.length()).toString();
            String inserted = source == null ? "" : source.subSequence(start, end).toString();
            String candidate = left + inserted + right;
            if (candidate.length() == 0 || candidate.matches("\\d{0,2}(\\.\\d?)?")) {
                return null;
            }
            return "";
        }
    }

    private static final class TimelineEventGroup {
        final String key;
        final String packageName;
        final String appLabel;
        final String feature;
        final ArrayList<TimelineActivityGroup> activityGroups = new ArrayList<>();
        long startTimeMillis;
        long endTimeMillis;
        int eventCount;

        TimelineEventGroup(FeatureEvent event) {
            key = event.packageName
                    + "#"
                    + event.feature
                    + "#"
                    + event.timeMillis;
            packageName = event.packageName;
            appLabel = event.appLabel;
            feature = event.feature;
            startTimeMillis = event.timeMillis;
            endTimeMillis = event.timeMillis;
        }

        boolean accepts(FeatureEvent event) {
            return TextUtils.equals(packageName, event.packageName)
                    && TextUtils.equals(feature, event.feature);
        }

        void add(FeatureEvent event) {
            TimelineActivityGroup current = activityGroups.isEmpty()
                    ? null
                    : activityGroups.get(activityGroups.size() - 1);
            if (current == null || !current.accepts(event)) {
                current = new TimelineActivityGroup(event.activityName);
                activityGroups.add(current);
            }
            current.events.add(event);
            startTimeMillis = Math.min(startTimeMillis, event.timeMillis);
            endTimeMillis = Math.max(endTimeMillis, event.timeMillis);
            eventCount++;
        }
    }

    private static final class TimelineActivityGroup {
        final String activityName;
        final ArrayList<FeatureEvent> events = new ArrayList<>();

        TimelineActivityGroup(String activityName) {
            this.activityName = activityName;
        }

        boolean accepts(FeatureEvent event) {
            return TextUtils.equals(activityName, event.activityName);
        }
    }
}

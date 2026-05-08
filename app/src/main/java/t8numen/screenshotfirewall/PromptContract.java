package t8numen.screenshotfirewall;

final class PromptContract {
    static final String ANDROID_PACKAGE = "android";
    static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    static final String ACTION_POLICY_QUERY =
            "t8numen.screenshotfirewall.action.POLICY_QUERY";
    static final String ACTION_POLICY_RESULT =
            "t8numen.screenshotfirewall.action.POLICY_RESULT";
    static final String ACTION_POLICY_SET =
            "t8numen.screenshotfirewall.action.POLICY_SET";
    static final String ACTION_POLICY_CLEAR_PACKAGE =
            "t8numen.screenshotfirewall.action.POLICY_CLEAR_PACKAGE";
    static final String ACTION_POLICY_CLEAR_ALL =
            "t8numen.screenshotfirewall.action.POLICY_CLEAR_ALL";
    static final String ACTION_EVENTS_CLEAR =
            "t8numen.screenshotfirewall.action.EVENTS_CLEAR";
    static final String ACTION_CONFIG_SET =
            "t8numen.screenshotfirewall.action.CONFIG_SET";
    static final String EXTRA_PACKAGE = "package";
    static final String EXTRA_FEATURE = "feature";
    static final String EXTRA_POLICY = "policy";
    static final String EXTRA_POLICY_ENTRIES = "policy_entries";
    static final String EXTRA_EVENT_ENTRIES = "event_entries";
    static final String EXTRA_DEFAULT_POLICY = "default_policy";
    static final String EXTRA_OVERRIDE_RULES = "override_rules";
    static final String EXTRA_COOLDOWN_SECONDS = "cooldown_seconds";

    private PromptContract() {
    }
}

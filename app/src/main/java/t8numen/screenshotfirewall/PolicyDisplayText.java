package t8numen.screenshotfirewall;

final class PolicyDisplayText {
    private PolicyDisplayText() {
    }

    static String feature(String feature) {
        if (Features.FLAG_SECURE.equals(feature)) {
            return "应用禁止截图";
        }
        if (Features.SCREEN_CAPTURE_OBSERVER.equals(feature)) {
            return "截图回执检测";
        }
        return "未知功能";
    }

    static String policy(String feature, String policy) {
        if (Features.FLAG_SECURE.equals(feature)) {
            return PolicyController.POLICY_BLOCK.equals(policy) ? "解除禁止" : "保留禁止";
        }
        if (Features.SCREEN_CAPTURE_OBSERVER.equals(feature)) {
            return PolicyController.POLICY_BLOCK.equals(policy) ? "阻止回执" : "允许回执";
        }
        return "未知";
    }

    static String defaultSummary(String defaultPolicy) {
        return PolicyController.POLICY_BLOCK.equals(defaultPolicy)
                ? "默认解除禁止/阻止回执"
                : "默认保留禁止/允许回执";
    }

    static String defaultHelp(String defaultPolicy) {
        return PolicyController.POLICY_BLOCK.equals(defaultPolicy)
                ? "无单独规则时：应用禁止截图会被解除；截图回执会被阻止。"
                : "无单独规则时：应用禁止截图会被保留；截图回执会被允许。";
    }
}

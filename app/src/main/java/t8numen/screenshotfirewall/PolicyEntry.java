package t8numen.screenshotfirewall;

final class PolicyEntry {
    final String feature;
    final String packageName;
    final String appLabel;
    final String policy;

    PolicyEntry(String feature, String packageName, String appLabel, String policy) {
        this.feature = feature;
        this.packageName = packageName;
        this.appLabel = appLabel;
        this.policy = policy;
    }

    String key() {
        return ExternalPolicyStore.policyKey(feature, packageName);
    }
}

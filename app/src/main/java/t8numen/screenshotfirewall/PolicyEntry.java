package t8numen.screenshotfirewall;

final class PolicyEntry {
    final String feature;
    final String packageName;
    final String activityName;
    final String appLabel;
    final String policy;

    PolicyEntry(
            String feature,
            String packageName,
            String activityName,
            String appLabel,
            String policy) {
        this.feature = feature;
        this.packageName = packageName;
        this.activityName = activityName == null ? "" : activityName;
        this.appLabel = appLabel;
        this.policy = policy;
    }

    String key() {
        return ExternalPolicyStore.policyKey(feature, packageName, activityName);
    }
}

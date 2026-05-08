package t8numen.screenshotfirewall;

final class FeatureEvent {
    final long timeMillis;
    final String feature;
    final String packageName;
    final String appLabel;
    final String activityName;

    FeatureEvent(
            long timeMillis,
            String feature,
            String packageName,
            String appLabel,
            String activityName) {
        this.timeMillis = timeMillis;
        this.feature = feature;
        this.packageName = packageName;
        this.appLabel = appLabel;
        this.activityName = activityName;
    }
}

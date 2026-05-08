package t8numen.screenshotfirewall;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Toast;

import java.util.concurrent.ConcurrentHashMap;

final class SystemToastNotifier {
    private static final ConcurrentHashMap<String, Long> LAST_TOAST =
            new ConcurrentHashMap<>();

    private SystemToastNotifier() {
    }

    static void showFeatureToast(
            Context context,
            String packageName,
            String appLabel,
            String feature,
            Decision decision,
            long cooldownMillis) {
        if (context == null || TextUtils.isEmpty(packageName) || TextUtils.isEmpty(feature)) {
            return;
        }
        try {
            long now = System.currentTimeMillis();
            String key = feature + "#" + packageName;
            Long last = LAST_TOAST.get(key);
            long safeCooldownMillis = Math.max(1L, cooldownMillis);
            if (last != null && now - last < safeCooldownMillis) {
                return;
            }
            LAST_TOAST.put(key, now);

            String label = TextUtils.isEmpty(appLabel) ? packageName : appLabel;
            showToast(context, label + " " + resultText(feature, decision));
        } catch (Throwable throwable) {
            Logx.e("show feature toast failed", throwable);
        }
    }

    private static String resultText(String feature, Decision decision) {
        if (Features.SCREEN_CAPTURE_OBSERVER.equals(feature)) {
            return decision == Decision.BLOCK
                    ? "\u6ce8\u518c\u4e86\u622a\u56fe\u56de\u6267\uff0c\u5df2\u963b\u6b62\u56de\u6267\uff0c\u5e94\u7528\u4e0d\u4f1a\u6536\u5230\u622a\u56fe\u901a\u77e5"
                    : "\u6ce8\u518c\u4e86\u622a\u56fe\u56de\u6267\uff0c\u5df2\u5141\u8bb8\u56de\u6267\uff0c\u5e94\u7528\u53ef\u80fd\u6536\u5230\u622a\u56fe\u901a\u77e5";
        }
        return decision == Decision.BLOCK
                ? "\u5f00\u542f\u4e86\u7981\u6b62\u622a\u56fe\uff0c\u5df2\u89e3\u9664\u9650\u5236\uff0c\u53ef\u4ee5\u622a\u56fe"
                : "\u5f00\u542f\u4e86\u7981\u6b62\u622a\u56fe\uff0c\u5df2\u4fdd\u7559\u9650\u5236\uff0c\u622a\u56fe\u4f1a\u88ab\u963b\u6b62";
    }

    private static void showToast(final Context context, final String message) {
        try {
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                    } catch (Throwable throwable) {
                        Logx.e("show system toast failed", throwable);
                    }
                }
            });
        } catch (Throwable throwable) {
            Logx.e("schedule system toast failed", throwable);
        }
    }
}

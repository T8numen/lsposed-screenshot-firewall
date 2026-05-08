package t8numen.screenshotfirewall;

import android.util.Log;

import de.robv.android.xposed.XposedBridge;

final class Logx {
    private static final String LOGCAT_TAG = "ScreenshotFirewall";
    private static final String TAG = "[" + LOGCAT_TAG + "] ";

    private Logx() {
    }

    static void i(String message) {
        Log.i(LOGCAT_TAG, message);
        XposedBridge.log(TAG + message);
    }

    static void e(String message, Throwable throwable) {
        Log.e(LOGCAT_TAG, message, throwable);
        XposedBridge.log(TAG + message);
        XposedBridge.log(throwable);
    }
}

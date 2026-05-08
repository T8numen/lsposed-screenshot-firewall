package t8numen.screenshotfirewall;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class HookEntry implements IXposedHookLoadPackage {
    private static final String ANDROID_PACKAGE = "android";
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    private static final String MODULE_PACKAGE = "t8numen.screenshotfirewall";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            if (ANDROID_PACKAGE.equals(lpparam.packageName)) {
                Logx.i("loading in system framework process=" + lpparam.processName);
                SystemServerHooks.install(lpparam.classLoader);
                return;
            }

            if (SYSTEM_UI_PACKAGE.equals(lpparam.packageName)
                    || MODULE_PACKAGE.equals(lpparam.packageName)) {
                Logx.i("scope checked for " + lpparam.packageName + ", no target-app hooks installed");
                if (SYSTEM_UI_PACKAGE.equals(lpparam.packageName)) {
                    SystemUiPromptBridge.install();
                }
            }
        } catch (Throwable throwable) {
            Logx.e("handleLoadPackage failed for " + lpparam.packageName, throwable);
        }
    }
}

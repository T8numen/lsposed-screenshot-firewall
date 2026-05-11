package t8numen.screenshotfirewall;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.text.TextUtils;
import android.view.WindowManager;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

@SuppressLint({"PrivateApi", "StaticFieldLeak"})
final class SystemServerHooks {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
    private static final ConcurrentHashMap<String, Long> LAST_EVENTS = new ConcurrentHashMap<>();
    private static volatile Context cachedSystemContext;

    private SystemServerHooks() {
    }

    static void install(ClassLoader classLoader) {
        if (!INSTALLED.compareAndSet(false, true)) {
            return;
        }
        hookWindowManagerService(classLoader);
        hookActivityTaskManagerService(classLoader);
    }

    private static void hookWindowManagerService(ClassLoader classLoader) {
        try {
            Class<?> wmsClass = Reflect.findClass(
                    "com.android.server.wm.WindowManagerService",
                    classLoader);
            if (wmsClass == null) {
                return;
            }
            hookLayoutParamsMethods(wmsClass, "addWindow");
            hookLayoutParamsMethods(wmsClass, "relayoutWindow");
        } catch (Throwable throwable) {
            Logx.e("hook WindowManagerService failed", throwable);
        }
    }

    private static void hookLayoutParamsMethods(Class<?> targetClass, String methodName) {
        int hookCount = 0;
        for (Method method : targetClass.getDeclaredMethods()) {
            try {
                if (!methodName.equals(method.getName()) || layoutParamsIndex(method) < 0) {
                    continue;
                }
                method.setAccessible(true);
                XposedBridge.hookMethod(method, new FlagSecureHook(methodName));
                hookCount++;
            } catch (Throwable throwable) {
                Logx.e("hook " + methodName + " overload failed", throwable);
            }
        }
        if (hookCount == 0) {
            Logx.i("no WindowManagerService." + methodName + " overload matched");
        } else {
            Logx.i("hooked WindowManagerService." + methodName + " overloads=" + hookCount);
        }
    }

    private static int layoutParamsIndex(Method method) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            if (WindowManager.LayoutParams.class.isAssignableFrom(parameterTypes[i])) {
                return i;
            }
        }
        return -1;
    }

    private static void hookActivityTaskManagerService(ClassLoader classLoader) {
        try {
            Class<?> atmsClass = Reflect.findClass(
                    "com.android.server.wm.ActivityTaskManagerService",
                    classLoader);
            Class<?> activityRecordClass = Reflect.findClass(
                    "com.android.server.wm.ActivityRecord",
                    classLoader);
            if (atmsClass == null || activityRecordClass == null) {
                return;
            }
            hookScreenCaptureRegisterMethods(atmsClass, activityRecordClass);
            hookScreenCaptureUnregisterMethods(atmsClass);
        } catch (Throwable throwable) {
            Logx.e("hook ActivityTaskManagerService failed", throwable);
        }
    }

    private static void hookScreenCaptureRegisterMethods(
            Class<?> atmsClass,
            Class<?> activityRecordClass) {
        int hookCount = 0;
        for (Method method : atmsClass.getDeclaredMethods()) {
            try {
                if (!"registerScreenCaptureObserver".equals(method.getName())
                        || method.getParameterTypes().length != 2
                        || !IBinder.class.isAssignableFrom(method.getParameterTypes()[0])) {
                    continue;
                }
                method.setAccessible(true);
                XposedBridge.hookMethod(method, new ScreenCaptureRegisterHook(activityRecordClass));
                hookCount++;
            } catch (Throwable throwable) {
                Logx.e("hook registerScreenCaptureObserver overload failed", throwable);
            }
        }
        if (hookCount == 0) {
            Logx.i("registerScreenCaptureObserver not found; Android version may not support it");
        } else {
            Logx.i("hooked registerScreenCaptureObserver overloads=" + hookCount);
        }
    }

    private static void hookScreenCaptureUnregisterMethods(Class<?> atmsClass) {
        int hookCount = 0;
        for (Method method : atmsClass.getDeclaredMethods()) {
            try {
                if (!"unregisterScreenCaptureObserver".equals(method.getName())
                        || method.getParameterTypes().length != 2
                        || !IBinder.class.isAssignableFrom(method.getParameterTypes()[0])) {
                    continue;
                }
                method.setAccessible(true);
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            Context context = serviceContext(param.thisObject);
                            PolicyController.ensureInitialized(context);
                        } catch (Throwable throwable) {
                            Logx.e("unregisterScreenCaptureObserver hook failed", throwable);
                        }
                    }
                });
                hookCount++;
            } catch (Throwable throwable) {
                Logx.e("hook unregisterScreenCaptureObserver overload failed", throwable);
            }
        }
        if (hookCount == 0) {
            Logx.i("unregisterScreenCaptureObserver not found");
        } else {
            Logx.i("hooked unregisterScreenCaptureObserver overloads=" + hookCount);
        }
    }

    private static Context serviceContext(Object service) {
        Object context = Reflect.getObjectField(service, "mContext");
        if (context instanceof Context) {
            cachedSystemContext = (Context) context;
            return (Context) context;
        }

        Context cached = cachedSystemContext;
        if (cached != null) {
            return cached;
        }

        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Object activityThread = XposedHelpers.callStaticMethod(
                    activityThreadClass,
                    "currentActivityThread");
            if (activityThread != null) {
                Object systemContext = XposedHelpers.callMethod(activityThread, "getSystemContext");
                if (systemContext instanceof Context) {
                    cachedSystemContext = (Context) systemContext;
                    Logx.i("system context resolved from ActivityThread");
                    return (Context) systemContext;
                }
            }
        } catch (Throwable throwable) {
            Logx.e("resolve system context failed", throwable);
        }
        return null;
    }

    private static final class FlagSecureHook extends XC_MethodHook {
        private final String methodName;

        private FlagSecureHook(String methodName) {
            this.methodName = methodName;
        }

        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            try {
                WindowManager.LayoutParams attrs = findLayoutParams(param.args);
                if (attrs == null
                        || (attrs.flags & WindowManager.LayoutParams.FLAG_SECURE) == 0) {
                    return;
                }

                Context context = serviceContext(param.thisObject);
                PolicyController.ensureInitialized(context);
                String packageName = PackageResolver.fromWindowAttrs(context, attrs, param.args);
                String activityName = PackageResolver.activityFromWindowAttrs(attrs);
                Decision decision = PolicyController.evaluate(
                        packageName,
                        Features.FLAG_SECURE,
                        activityName);
                recordFeatureEvent(context, Features.FLAG_SECURE, packageName, activityName, decision);
                if (decision == Decision.BLOCK) {
                    attrs.flags &= ~WindowManager.LayoutParams.FLAG_SECURE;
                    Logx.i(methodName + " removed FLAG_SECURE package=" + packageName
                            + " activity=" + activityName
                            + " decision=" + decision);
                }
            } catch (Throwable throwable) {
                Logx.e(methodName + " FLAG_SECURE hook failed", throwable);
            }
        }

        private WindowManager.LayoutParams findLayoutParams(Object[] args) {
            if (args == null) {
                return null;
            }
            for (Object arg : args) {
                if (arg instanceof WindowManager.LayoutParams) {
                    return (WindowManager.LayoutParams) arg;
                }
            }
            return null;
        }
    }

    private static final class ScreenCaptureRegisterHook extends XC_MethodHook {
        private final Class<?> activityRecordClass;

        private ScreenCaptureRegisterHook(Class<?> activityRecordClass) {
            this.activityRecordClass = activityRecordClass;
        }

        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            try {
                if (param.args == null || param.args.length < 2 || !(param.args[0] instanceof IBinder)) {
                    return;
                }

                Context context = serviceContext(param.thisObject);
                PolicyController.ensureInitialized(context);

                IBinder activityToken = (IBinder) param.args[0];
                Object activityRecord = null;
                String packageName = null;
                Object globalLock = Reflect.getObjectField(param.thisObject, "mGlobalLock");
                if (globalLock != null) {
                    synchronized (globalLock) {
                        activityRecord = activityRecordForToken(activityToken);
                        packageName = PackageResolver.fromActivityRecord(activityRecord);
                    }
                } else {
                    activityRecord = activityRecordForToken(activityToken);
                    packageName = PackageResolver.fromActivityRecord(activityRecord);
                }
                String activityName = PackageResolver.activityFromActivityRecord(activityRecord);
                Decision decision = PolicyController.evaluate(
                        packageName,
                        Features.SCREEN_CAPTURE_OBSERVER,
                        activityName);
                recordFeatureEvent(
                        context,
                        Features.SCREEN_CAPTURE_OBSERVER,
                        packageName,
                        activityName,
                        decision);
                if (decision == Decision.BLOCK) {
                    param.setResult(null);
                    Logx.i("screen capture observer registration skipped package=" + packageName
                            + " activity=" + activityName
                            + " decision=" + decision);
                }
            } catch (Throwable throwable) {
                Logx.e("registerScreenCaptureObserver hook failed", throwable);
            }
        }

        private Object activityRecordForToken(IBinder activityToken) {
            try {
                return XposedHelpers.callStaticMethod(
                        activityRecordClass,
                        "forTokenLocked",
                        activityToken);
            } catch (Throwable throwable) {
                Logx.e("ActivityRecord.forTokenLocked failed", throwable);
                return null;
            }
        }
    }

    private static void recordFeatureEvent(
            Context context,
            String feature,
            String packageName,
            String activityName,
            Decision decision) {
        if (context == null || TextUtils.isEmpty(packageName) || TextUtils.isEmpty(feature)) {
            return;
        }
        if (PromptContract.ANDROID_PACKAGE.equals(packageName)
                || PromptContract.SYSTEM_UI_PACKAGE.equals(packageName)
                || PolicyController.MODULE_PACKAGE.equals(packageName)) {
            return;
        }
        try {
            long identity = Binder.clearCallingIdentity();
            try {
                ExternalPolicyStore.Config config = ExternalPolicyStore.readConfig(context);
                long cooldownMillis = config.cooldownMillis();
                long now = System.currentTimeMillis();
                String key = feature + "#" + packageName + "#" + activityName;
                Long last = LAST_EVENTS.get(key);
                if (last != null && now - last < cooldownMillis) {
                    return;
                }
                LAST_EVENTS.put(key, now);
                String appLabel = PolicyController.getAppLabel(packageName);
                ExternalPolicyStore.recordEvent(
                        context,
                        feature,
                        packageName,
                        appLabel,
                        activityName,
                        now);
                SystemToastNotifier.showFeatureToast(
                        context,
                        packageName,
                        appLabel,
                        feature,
                        decision,
                        cooldownMillis);
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
            Logx.i("event recorded package=" + packageName
                    + " feature=" + feature
                    + " activity=" + activityName);
        } catch (Throwable throwable) {
            Logx.e("record feature event failed", throwable);
        }
    }
}

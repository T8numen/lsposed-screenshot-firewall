package t8numen.screenshotfirewall;

import de.robv.android.xposed.XposedHelpers;

final class Reflect {
    private Reflect() {
    }

    static Class<?> findClass(String className, ClassLoader classLoader) {
        try {
            return XposedHelpers.findClass(className, classLoader);
        } catch (Throwable throwable) {
            Logx.e("class not found: " + className, throwable);
            return null;
        }
    }

    static Object getObjectField(Object target, String fieldName) {
        if (target == null) {
            return null;
        }
        try {
            return XposedHelpers.getObjectField(target, fieldName);
        } catch (Throwable ignored) {
            return null;
        }
    }

    static String getStringField(Object target, String fieldName) {
        Object value = getObjectField(target, fieldName);
        return value instanceof String ? (String) value : null;
    }
}

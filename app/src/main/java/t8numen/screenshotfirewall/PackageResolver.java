package t8numen.screenshotfirewall;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.text.TextUtils;
import android.view.WindowManager;

final class PackageResolver {
    private PackageResolver() {
    }

    static String fromWindowAttrs(
            Context context,
            WindowManager.LayoutParams attrs,
            Object[] args) {
        if (attrs != null && !TextUtils.isEmpty(attrs.packageName)) {
            return attrs.packageName;
        }

        if (args != null) {
            for (Object arg : args) {
                String packageName = Reflect.getStringField(arg, "mPackageName");
                if (!TextUtils.isEmpty(packageName)) {
                    return packageName;
                }
            }
        }

        if (context != null) {
            try {
                PackageManager packageManager = context.getPackageManager();
                String[] packages = packageManager.getPackagesForUid(Binder.getCallingUid());
                if (packages != null && packages.length > 0) {
                    return packages[0];
                }
            } catch (Throwable throwable) {
                Logx.e("resolve package from uid failed", throwable);
            }
        }

        return null;
    }

    static String fromActivityRecord(Object activityRecord) {
        String packageName = Reflect.getStringField(activityRecord, "packageName");
        if (!TextUtils.isEmpty(packageName)) {
            return packageName;
        }
        Object info = Reflect.getObjectField(activityRecord, "info");
        Object applicationInfo = Reflect.getObjectField(info, "applicationInfo");
        return Reflect.getStringField(applicationInfo, "packageName");
    }

    static String activityFromWindowAttrs(WindowManager.LayoutParams attrs) {
        if (attrs == null) {
            return null;
        }
        try {
            CharSequence title = attrs.getTitle();
            if (!TextUtils.isEmpty(title)) {
                return title.toString();
            }
        } catch (Throwable throwable) {
            Logx.e("resolve activity from window title failed", throwable);
        }
        return null;
    }

    static String activityFromActivityRecord(Object activityRecord) {
        String shortComponent = Reflect.getStringField(activityRecord, "shortComponentName");
        if (!TextUtils.isEmpty(shortComponent)) {
            return shortComponent;
        }

        Object component = Reflect.getObjectField(activityRecord, "mActivityComponent");
        if (component instanceof ComponentName) {
            return ((ComponentName) component).flattenToShortString();
        }
        if (component != null) {
            return String.valueOf(component);
        }

        Object info = Reflect.getObjectField(activityRecord, "info");
        String activityName = Reflect.getStringField(info, "name");
        if (TextUtils.isEmpty(activityName)) {
            return null;
        }
        String packageName = fromActivityRecord(activityRecord);
        return TextUtils.isEmpty(packageName)
                ? activityName
                : packageName + "/" + activityName;
    }
}

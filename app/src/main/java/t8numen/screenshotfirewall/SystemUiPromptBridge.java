package t8numen.screenshotfirewall;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Build;

import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

final class SystemUiPromptBridge {
    private static final AtomicBoolean HOOKED = new AtomicBoolean(false);
    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

    private SystemUiPromptBridge() {
    }

    static void install() {
        if (!HOOKED.compareAndSet(false, true)) {
            return;
        }
        try {
            XposedHelpers.findAndHookMethod(
                    Application.class,
                    "attach",
                    Context.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                if (param.args == null
                                        || param.args.length == 0
                                        || !(param.args[0] instanceof Context)) {
                                    return;
                                }
                                register((Context) param.args[0]);
                            } catch (Throwable throwable) {
                                Logx.e("systemui prompt bridge attach hook failed", throwable);
                            }
                        }
                    });
            Logx.i("systemui prompt bridge hook installed");
        } catch (Throwable throwable) {
            Logx.e("install systemui prompt bridge failed", throwable);
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private static void register(Context context) {
        if (context == null || !REGISTERED.compareAndSet(false, true)) {
            return;
        }
        try {
            Context appContext = context.getApplicationContext();
            if (appContext == null) {
                appContext = context;
            }
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    long identity = Binder.clearCallingIdentity();
                    try {
                        if (intent == null || intent.getAction() == null) {
                            return;
                        }
                        String action = intent.getAction();
                        Context appContext = context.getApplicationContext();
                        if (PromptContract.ACTION_POLICY_QUERY.equals(action)) {
                            ExternalPolicyStore.sendPolicyResult(appContext);
                        } else if (PromptContract.ACTION_POLICY_SET.equals(action)) {
                            String packageName = intent.getStringExtra(PromptContract.EXTRA_PACKAGE);
                            String activityName = intent.getStringExtra(PromptContract.EXTRA_ACTIVITY);
                            String feature = intent.getStringExtra(PromptContract.EXTRA_FEATURE);
                            String policy = intent.getStringExtra(PromptContract.EXTRA_POLICY);
                            String oldPackageName = intent.getStringExtra(
                                    PromptContract.EXTRA_OLD_PACKAGE);
                            String oldActivityName = intent.getStringExtra(
                                    PromptContract.EXTRA_OLD_ACTIVITY);
                            String oldFeature = intent.getStringExtra(
                                    PromptContract.EXTRA_OLD_FEATURE);
                            if (oldPackageName != null && oldFeature != null) {
                                ExternalPolicyStore.clearPolicy(
                                        appContext,
                                        oldFeature,
                                        oldPackageName,
                                        oldActivityName);
                            }
                            ExternalPolicyStore.setPolicy(
                                    appContext,
                                    feature,
                                    packageName,
                                    activityName,
                                    policy);
                            ExternalPolicyStore.sendPolicyResult(appContext);
                        } else if (PromptContract.ACTION_POLICY_CLEAR_PACKAGE.equals(action)) {
                            String packageName = intent.getStringExtra(PromptContract.EXTRA_PACKAGE);
                            String activityName = intent.getStringExtra(PromptContract.EXTRA_ACTIVITY);
                            String feature = intent.getStringExtra(PromptContract.EXTRA_FEATURE);
                            ExternalPolicyStore.clearPolicy(
                                    appContext,
                                    feature,
                                    packageName,
                                    activityName);
                            ExternalPolicyStore.sendPolicyResult(appContext);
                        } else if (PromptContract.ACTION_POLICY_CLEAR_ALL.equals(action)) {
                            ExternalPolicyStore.clearAll(appContext);
                            ExternalPolicyStore.sendPolicyResult(appContext);
                        } else if (PromptContract.ACTION_EVENTS_CLEAR.equals(action)) {
                            ExternalPolicyStore.clearEvents(appContext);
                            ExternalPolicyStore.sendPolicyResult(appContext);
                        } else if (PromptContract.ACTION_CONFIG_SET.equals(action)) {
                            String defaultPolicy = intent.getStringExtra(
                                    PromptContract.EXTRA_DEFAULT_POLICY);
                            boolean overrideRules = intent.getBooleanExtra(
                                    PromptContract.EXTRA_OVERRIDE_RULES,
                                    false);
                            double cooldownSeconds = intent.getDoubleExtra(
                                    PromptContract.EXTRA_COOLDOWN_SECONDS,
                                    ExternalPolicyStore.Config.DEFAULT_COOLDOWN_SECONDS);
                            ExternalPolicyStore.writeConfig(
                                    appContext,
                                    new ExternalPolicyStore.Config(
                                            defaultPolicy,
                                            overrideRules,
                                            cooldownSeconds));
                            ExternalPolicyStore.sendPolicyResult(appContext);
                        }
                    } catch (Throwable throwable) {
                        Logx.e("systemui overlay prompt receiver failed", throwable);
                    } finally {
                        Binder.restoreCallingIdentity(identity);
                    }
                }
            };

            IntentFilter filter = new IntentFilter(PromptContract.ACTION_POLICY_QUERY);
            filter.addAction(PromptContract.ACTION_POLICY_SET);
            filter.addAction(PromptContract.ACTION_POLICY_CLEAR_PACKAGE);
            filter.addAction(PromptContract.ACTION_POLICY_CLEAR_ALL);
            filter.addAction(PromptContract.ACTION_EVENTS_CLEAR);
            filter.addAction(PromptContract.ACTION_CONFIG_SET);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                appContext.registerReceiver(receiver, filter);
            }
            Logx.i("systemui overlay prompt receiver registered");
        } catch (Throwable throwable) {
            REGISTERED.set(false);
            Logx.e("register systemui overlay prompt receiver failed", throwable);
        }
    }
}

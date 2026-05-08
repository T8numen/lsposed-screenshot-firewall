# Project Instructions

## 项目概况

- 本项目是 Android Java LSPosed 模块 `SystemScreenshotFirewall`。
- 将当前目录视为项目根目录。除非用户明确要求，不要修改父级工作区里的其他项目。
- 模块面向 LSPosed 1.9.2 兼容性，默认使用 legacy XposedBridge API。
- 模块只处理系统侧截图相关能力，不 Hook 任意目标应用进程。

## Android 与 LSPosed 约束

- 沿用 `app/` 下现有 Java/Gradle 结构；除非用户明确要求，不要引入 AndroidX、Kotlin、Compose 或新的架构层。
- 保留 legacy Xposed 入口链路：`app/src/main/assets/xposed_init` -> `t8numen.screenshotfirewall.HookEntry` -> `IXposedHookLoadPackage`。
- Xposed API 继续使用 `compileOnly "de.robv.android.xposed:api:82"`。
- 除非用户明确选择放弃 LSPosed 1.9.2 兼容性，不要切换到 modern `libxposed`、`XposedModule`、`META-INF/xposed/java_init.list` 或 `module.prop`。
- 预期 LSPosed scope 为 System Framework / `android`、`com.android.systemui` 和 `t8numen.screenshotfirewall`。
- `android` / `system_server` 负责安装 `SystemServerHooks`；`com.android.systemui` 只承载规则、事件和配置 bridge。

## 必须保留的行为

- `FLAG_SECURE` 与 Android 14+ screenshot callback 是两套独立功能。保留 `flag_secure` 和 `screen_capture_observer` 这两个存储/action key。
- 默认行为是 allow/pass-through。只有保存为 `block` 的策略才会移除 `FLAG_SECURE` 或阻止 screenshot callback 注册。
- 保留通过 `Settings.Global` / `ExternalPolicyStore` bridge 管理的全局默认策略、全局覆盖单独规则、事件记录和冷却配置。
- 除非用户明确要求，不要重新引入目标应用 Hook、`MediaStore` 监听、`FileObserver` 监听、截图目录监听或旧的悬浮窗询问流程。
- Hook 和反射路径必须优先保证稳定性：捕获 `Throwable`，使用 `Logx` 记录，不让 `system_server` 崩溃。
- Hook 回调里不要等待 UI、广播、重磁盘操作或用户输入。

## 构建与验证

- 优先使用当前目录下的 Gradle wrapper。
- 修改 Android 代码后运行 `.\gradlew.bat :app:lintDebug`。
- 交付 build 或 release candidate 前运行 `.\gradlew.bat :app:assembleDebug`。
- 如果改动涉及系统侧 Hook，安装 APK 后还需要重载或重启被 Hook 的进程再验证；普通 APK 覆盖安装不足以验证 `system_server` 行为。
- 验证 screenshot callback 行为时，使用 `ScreenshotCallbackTest` 或其他明确会注册截图回执的测试应用。

## 发布与文档流程

- 做版本化变更时，同步更新 `app/build.gradle` 中的 `versionCode` 和 `versionName`。
- 将用户可见或行为变化记录到 `history.md`。
- 保持 `README.md` 与当前策略语义、UI、LSPosed scope 和验证要求一致。
- 归档 debug APK 到 `apk-history/debug/`，文件名包含 `versionName` 和 `versionCode`。
- 不要把本机绝对路径、设备序列号、token 或临时交接状态写进共享项目文档。

## 文案规则

- 项目文档和用户可见文案以简体中文为主。
- `FLAG_SECURE`、`Settings.Global`、`system_server`、类名、包名、action 和 Gradle task 等技术标识保留英文。
- 策略文案必须精确：`FLAG_SECURE` 表达应区分保留/解除截图禁止；screenshot callback 表达应区分允许/阻止回执注册。

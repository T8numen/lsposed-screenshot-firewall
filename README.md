# 系统侧截图防火墙

`SystemScreenshotFirewall` 是一个面向 LSPosed 1.9.2 的 Android legacy XposedBridge 模块。它在系统侧记录并按规则处理 `FLAG_SECURE` 与 Android 14+ 官方截图回执能力，用于查看哪些应用正在禁止截图或监听截图，并为指定应用设置允许/阻止规则。

模块默认放行。只有明确保存为 `block` 的规则才会改变系统行为。

## 功能

- 记录应用设置 `FLAG_SECURE` 的事件，包含包名、应用名、Activity/window title 和时间。
- 可按包名解除指定应用的 `FLAG_SECURE` 截图禁止。
- 记录 Android 14+ `registerScreenCaptureObserver(...)` 截图回执注册事件。
- 可按包名阻止指定应用注册截图回执。
- `FLAG_SECURE` 与截图回执是两套独立规则，互不混用。
- 模块主页提供 `规则`、`最近事件`、`全局设置` 三个页面。
- 最近事件默认按连续包名、功能和 Activity 分组，显示一段连续事件的开始/结束时间，详情默认折叠。
- 规则支持整包规则和 Activity 精确规则；Activity 精确规则优先于整包规则。
- 规则页可新建规则，也可点击已有规则编辑包名、Activity、功能和结果。
- 全局设置支持默认策略、是否覆盖单独规则，以及 Toast / 事件冷却时间。

## 不做什么

- 不 Hook 普通目标应用进程。
- 不监听 `MediaStore`、`FileObserver` 或截图目录。
- 不主动截图、保存截图或上传任何内容。
- 不使用旧版悬浮窗询问流程。
- 不切换到 LSPosed modern `libxposed` API。

## 兼容性

- LSPosed：官方 LSPosed 1.9.2 兼容路线。
- Xposed API：legacy XposedBridge API。
- 入口文件：`app/src/main/assets/xposed_init`。
- 入口类：`t8numen.screenshotfirewall.HookEntry`。
- 编译依赖：`compileOnly "de.robv.android.xposed:api:82"`。
- 当前主要面向 Android 15；Android 14+ 的截图回执功能依赖系统提供 `registerScreenCaptureObserver(...)`。

系统服务和厂商 ROM 可能修改内部类或方法签名。如果 Hook 点找不到，模块会记录日志并跳过，不应导致 `system_server` 崩溃。

## LSPosed 作用域

在 LSPosed Manager 中只需要勾选：

- System Framework / 系统框架
- `com.android.systemui`
- `t8numen.screenshotfirewall`

实际 Hook 安装在 `android` / `system_server` 中；`com.android.systemui` 用于承载规则、事件和配置 bridge；模块自身包名用于打开配置主页。

## 安装与启用

1. 安装 APK。
2. 在 LSPosed Manager 中启用模块。
3. 按上面的作用域勾选 System Framework、`com.android.systemui` 和模块自身。
4. 重启设备，或至少重载 `system_server` / `com.android.systemui`。
5. 打开 `系统侧截图防火墙`，在 `最近事件` 中观察事件，再按需要设置规则。

普通覆盖安装 APK 后，系统侧 Hook 不一定立即更新；涉及 Hook 逻辑变更时需要重启或重载被 Hook 的进程。

## 使用说明

### 规则

规则按功能分组：

- `FLAG_SECURE`：`允许` 表示保留应用的截图禁止；`阻止` 表示移除应用设置的 `FLAG_SECURE`。
- 截图回执：`允许` 表示允许应用注册截图回执；`阻止` 表示跳过截图回执注册。

单独包名规则优先于默认策略，除非在 `全局设置` 中开启了 `覆盖单独规则`。Activity 精确规则优先于整包规则；Activity 为空表示对整个包生效。规则列表会显示应用名、包名、Activity 范围、功能和当前策略。

### 最近事件

最近事件用于从真实系统行为中发现规则候选：

- 默认按时间线展示。
- 同一包名、同一功能、连续 Activity 的事件会合并显示。
- 同一包名同一功能但切换 Activity 时，会在同一组内开启新的 Activity 分段。
- 切换到其他应用或其他功能时，会开始新的事件组。
- 分组详情默认折叠，展开后可查看每次事件时间。
- 每组可直接设置允许、阻止或撤销规则。
- 包名和 Activity 支持长按复制，复制后会显示 Toast。
- 连续分组时间范围使用 `MM-dd HH:mm:ss -- HH:mm:ss`；跨天时结束时间也显示月日。

### 全局设置

- 默认策略：没有单独规则时使用。
- 覆盖单独规则：开启后全局默认策略会覆盖已保存的包名规则。
- Toast / 事件冷却：限制重复 Toast 和重复事件记录，范围为 5.0 到 60.0 秒。

## 策略语义

模块内保存的策略值以功能、包名和可选 Activity 为维度：

- `flag_secure|package`
- `screen_capture_observer|package`
- `flag_secure|package|activity`
- `screen_capture_observer|package|activity`

策略值：

- `allow`：允许系统原始行为。
- `block`：阻止对应功能。
- 未设置：使用默认策略；默认策略初始为 `allow`。

这意味着安装后默认不会改变任何应用行为，需要你在事件或规则页中主动保存 `block` 规则。

## 构建

要求：

- JDK 17
- Android SDK
- Gradle wrapper 使用项目自带 `gradlew.bat`

Windows:

```powershell
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebug
```

Debug APK 输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

项目归档过的 debug APK 会放在：

```text
apk-history/debug/
```

## 调试

模块日志使用 `ScreenshotFirewall` tag，并通过 Xposed 日志记录关键 Hook 状态。

常用检查：

```powershell
adb logcat | findstr ScreenshotFirewall
```

验证系统侧 Hook 时，安装 APK 后需要重启或重载相关进程。验证 Android 14+ 截图回执时，应使用会调用 `registerScreenCaptureObserver(...)` 的测试应用或目标应用流程。

## 项目结构

```text
app/src/main/assets/xposed_init
app/src/main/java/t8numen/screenshotfirewall/HookEntry.java
app/src/main/java/t8numen/screenshotfirewall/SystemServerHooks.java
app/src/main/java/t8numen/screenshotfirewall/SystemUiPromptBridge.java
app/src/main/java/t8numen/screenshotfirewall/OverlayPermissionActivity.java
history.md
apk-history/debug/
```

## 版本

当前版本：`1.0.23` / `versionCode 24`

变更记录见 [history.md](history.md)。

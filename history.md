# history

## 1.0.22 - 2026-05-09

- 最近事件连续分组新增起止时间摘要，显示在应用名和规则按钮之间。
- 连续事件详情默认折叠；展开后在详情顶部和底部都提供 `折叠` 按钮。
- Activity 分段标题只显示 Activity 名称，不再重复显示已经在上方展示过的包名。

## 1.0.21 - 2026-05-09

- 最近事件的默认时间线改为连续分组显示：同一包名和同一功能的连续事件合并到同一组，组内再按连续 Activity 分段展示多条时间。
- 分组事件卡片只在顶部显示一次应用名、规则按钮和包名，连续相同 Activity 只追加时间，切换 Activity 时在同一卡片内开启新的 Activity 分段。
- `清除事件记录` 按钮移动到最近事件页顶部，空列表时保持禁用状态。

## 1.0.20 - 2026-05-08

- 清理已停用的 `ask` / 悬浮窗询问遗留代码，删除旧 `SystemNotifier`、`OverlayPrompt*` 和 `allow_once` 决策通道，避免重新引入旧策略语义。
- 将模块主页的策略/事件数据模型和策略文案映射抽出到独立 helper，降低后续维护 `OverlayPermissionActivity` 的冲突范围。
- 更新 README 的全局设置、覆盖单独规则、Toast / 事件冷却和构建验证说明；移除共享 Gradle 配置中的本机 JDK 路径，并忽略本地 Android/Gradle 工作目录。

## 1.0.19 - 2026-05-04

- 澄清 Toast 和主页文案：`FLAG_SECURE` 显示为“保留禁止 / 解除禁止”，截图回执显示为“允许回执 / 阻止回执”。
- Toast 现在明确说明应用是否还能禁止截图、是否会收到截图通知，避免把“拦截截图”和“拦截禁止截图”混在一起。
- 最近事件按钮和规则状态不再直接显示裸 `允许` / `拦截`，全局默认策略摘要也改为面向用户结果的描述。

## 1.0.18 - 2026-05-04

- 将 `全局设置` 从规则页移到顶部第三个标签，与 `规则`、`最近事件` 并列，减少规则页内容挤压。
- `Toast / 事件冷却` 新增数值输入框，支持最多一位小数；输入后会同步更新滑条位置。
- 冷却输入提交时继续限制在 5.0 到 60.0 秒之间，滑条与输入框共享同一套配置写入逻辑。

## 1.0.17 - 2026-05-04

- 新增全局默认策略配置，可在 `默认允许` / `默认拦截` 之间切换；默认仍保持允许，避免升级后突然改变现有行为。
- 新增 `覆盖单独规则` 开关：关闭时包名规则优先，开启时全局默认策略覆盖已添加的包名规则。
- 事件记录和系统侧 Toast 的节流时间改为可配置，主页用 5.0 到 60.0 秒滑条调整，精度 0.1 秒。
- 规则页顶部新增 `全局设置` 卡片，保留原有 `FLAG_SECURE` 与截图回执分组规则管理。

## 1.0.16 - 2026-05-04

- 在 system_server 事件记录路径新增系统侧 Toast 提示，不 Hook 目标应用进程。
- 当应用设置 `FLAG_SECURE` 时提示“设置了禁止截图”，当应用注册截图回执时提示“注册了截图回执”，并显示当前结果 `已允许` / `已拦截`。
- Toast 按包名和功能做 60 秒节流，避免 `relayoutWindow` 或重复注册导致刷屏。

## 1.0.15 - 2026-05-04

- 最近事件页新增 `时间线` / `按包名` 二级切换，默认仍按时间倒序显示。
- `按包名` 模式会把同一包名的事件归到同一组，包组按最新事件出现顺序排列，组内继续按时间倒序。
- 规则页移除 `清除全部规则` 按钮，只保留每条规则的单项撤销，降低误操作风险。

## 1.0.14 - 2026-05-04

- 模块主页改为自绘顶部栏和 `规则` / `最近事件` 双标签结构，避免系统默认 ActionBar 挤压内容。
- 规则页按 `FLAG_SECURE` 与截图回执分组展示，优先显示应用名，长包名自动省略，支持单项撤销。
- 最近事件页改为紧凑事件卡片，限制长包名和 Activity 行数，并支持从事件直接设置 `允许`、`拦截` 或撤销规则。
- 按钮改为轻量描边/状态色样式，顶部增加默认策略、规则数和事件数摘要。

## 1.0.13 - 2026-05-04

- 策略语义改为默认放行：没有显式 `block` 规则时不移除 `FLAG_SECURE`，也不阻止 Android 14+ 官方截图回执注册。
- 将规则拆分为 `flag_secure|package` 与 `screen_capture_observer|package` 两套独立策略，模块主页按功能分组展示并支持单项撤销。
- 移除悬浮窗询问流程和悬浮窗权限入口；模块主页通过已驻留的 `com.android.systemui` bridge 写入/查询规则。
- `system_server` 在应用设置 `FLAG_SECURE` 或注册截图回执时记录事件，包含功能、包名、应用名、Activity/window title 与发生时间；模块主页可从事件直接设为拦截。
- 去掉 manifest 中的 `SYSTEM_ALERT_WINDOW`、悬浮窗 `Service` 与静态 receiver 声明，保留 legacy XposedBridge / `assets/xposed_init` 兼容 LSPosed 1.9.2。

## 1.0.12 - 2026-05-04

- 将悬浮窗按钮决策和模块主页策略管理改为经 `SystemUI` bridge 写入/查询 `Settings.Global`，避免依赖投递到 `system_server` 的动态广播。
- `system_server` 在评估策略时同步读取 `Settings.Global` 中的外部策略，并支持从该存储消费“允许一次”计数。
- 模块主页的策略查询、单包撤销、清除全部改为发送给已常驻的 `com.android.systemui` receiver，由 SystemUI 回传当前策略列表。

## 1.0.11 - 2026-05-04

- 模块主页新增策略管理列表，支持查询 system_server 中已保存的“始终允许 / 始终拦截”策略，并支持按包撤销或清除全部策略。
- system_server 新增策略查询与清除广播接收器，策略变更后立即回传当前列表给模块主页。
- 重做悬浮窗视觉样式，改为 SystemUI 进程创建的全屏半透明遮罩卡片；点击卡片外空白区域会关闭悬浮窗，卡片内按钮仍执行“允许一次 / 始终允许 / 始终拦截”。

## 1.0.10 - 2026-05-04

- 新增 `SystemUiPromptBridge`：在 `com.android.systemui` 进程中通过 `Application.attach` 注册动态悬浮窗 receiver，由已常驻的 SystemUI 进程创建提示悬浮窗，绕开 ColorOS/OPlus 对 `system_server` 拉起模块进程广播的后台启动拦截。
- `system_server` 的 `ask` 提示广播改为投递到 `com.android.systemui`，保留通知作为兜底，不 hook 目标应用进程。

## 1.0.9 - 2026-05-04

- 修复 `system_server` 发送悬浮窗广播时未指定用户的问题，改为 `sendBroadcastAsUser(..., android.os.Process.myUserHandle())`，并加入 `FLAG_INCLUDE_STOPPED_PACKAGES` 与 `FLAG_RECEIVER_FOREGROUND`，确保请求能投递到模块进程的 `OverlayPromptReceiver`。

## 1.0.8 - 2026-05-04

- 修复 v1.0.7 中悬浮窗显式广播仍误指向 `OverlayPromptService` 的问题，改为发送到 `OverlayPromptReceiver`，使模块进程能真正接收请求并创建 `TYPE_APPLICATION_OVERLAY` 悬浮窗。

## 1.0.7 - 2026-05-04

- 浮窗触发从启动模块 `Service` 改为显式广播到 `OverlayPromptReceiver`，避免 Android 12+ 后台 `Service` 启动限制导致 `BackgroundServiceStartNotAllowedException`。
- 抽出 `OverlayPromptController` 统一处理浮窗创建、按钮决策和权限兜底页，保留 `OverlayPromptService` 作为兼容入口但不再由 `system_server` 直接启动。

## 1.0.6 - 2026-05-04

- `ask` 通知、浮窗服务启动和按钮决策处理统一包裹 `Binder.clearCallingIdentity()`，避免在 WMS/ATMS 的 Binder 调用栈里沿用目标 App uid，导致系统误判为目标 App 在以 `android` 包名创建 `PendingIntent` 或启动模块服务。

## 1.0.5 - 2026-05-04

- 新增模块自身 `OverlayPromptService`，`ask` 时由 `system_server` 启动模块进程显示 `TYPE_APPLICATION_OVERLAY` 浮窗。
- 新增模块入口 `OverlayPermissionActivity` 与 `SYSTEM_ALERT_WINDOW` 权限声明，用于授予“显示在其他应用上层”权限。
- 浮窗按钮通过一次性 token 广播回 `system_server`，继续复用“允许一次 / 始终允许 / 始终拦截”策略。

## 1.0.4 - 2026-05-04

- `WindowManagerService.mContext` 为空时从 `ActivityThread.getSystemContext()` 兜底解析 system context，避免只移除 `FLAG_SECURE` 但无法初始化通知器。
- `ask` 提醒在通知之外增加同节流 `Toast`，便于确认厂商系统是否折叠了 Android System 通知。
- 增加通知跳过和节流日志，便于区分 scope 未加载、context 未初始化、通知被节流这三类情况。

## 1.0.3 - 2026-05-04

- `SharedPreferences` 在 `system_server` 不可用时改用 `/data/system/screenshot_firewall_policies.properties` 持久化包策略，保留“始终允许 / 始终拦截”的跨重启语义。
- fallback 策略文件只在初始化和通知按钮决策时读写，Hook 热路径继续只读内存缓存。

## 1.0.2 - 2026-05-04

- 从 1.0.0 日志确认 `system_server` 已命中 `FLAG_SECURE` 和截图回执 Hook，但 `android` context 无数据目录导致策略初始化失败，通知链路未启动。
- 策略存储在 system_server 中降级为内存策略，不再因为 `SharedPreferences` 不可用而中断通知初始化。
- 官方截图回执注册改为 Hook 前直接跳过原方法，`ask/block` 不再先注册再注销 observer。

## 1.0.1 - 2026-05-04

- 将模块日志同时写入普通 `logcat` 的 `ScreenshotFirewall` tag，便于无 root 读取加载和 Hook 命中状态。
- 修复 `lintDebug` 报告中的通知权限、动态 receiver flag、备份规则、图标和过时 SDK 检查提示。

## 1.0.0 - 2026-05-04

- 新建 LSPosed 1.9.2 legacy XposedBridge 模块。
- 在 `system_server` 中按方法名和 `WindowManager.LayoutParams` 参数匹配 Hook `WindowManagerService.addWindow(...)`、`WindowManagerService.relayoutWindow(...)`，按包策略移除 `FLAG_SECURE`。
- 在 `system_server` 中 Hook `ActivityTaskManagerService.registerScreenCaptureObserver(...)` 和 `unregisterScreenCaptureObserver(...)`，对 Android 14+ 官方截图检测注册执行 allow/block/ask 策略。
- `ask` 策略不阻塞 Hook 线程，先临时拦截并发送通知，通知按钮支持“允许一次 / 始终允许 / 始终拦截”。

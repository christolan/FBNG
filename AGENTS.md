# AGENTS.md — FBNG UI 规则

自用 Android 街机前端（LibretroDroid + 固定版本的 FBNeo）。界面是 Jetpack Compose，Material 3 深色方案。主设备是 OPPO Find N6，主输入是手柄，触屏是次要输入。这一阶段的界面只有游戏库（含导入结果）和游戏中的存档 / 读档 / 退出。仓库里没有 ROM 或 BIOS，也不要提交。

下面的字号、缩放、圆角、边距是建议起点，不是官方硬性标准。改它们之前先看现有界面，不要为了凑齐这些数字去重画一版。

## 视觉风格（待定）

风格方向还没定，由使用者之后重新决定。定下来之前，不要把某一种「AI 默认风」写成禁止项，也不要主动换成另一套配色或字体。

当前主题（以代码为准，不是目标稿）：

- 只有深色。`MainActivity` 用深色系统栏，`FbngTheme` 只套 `darkColorScheme`，没有动态色。
- 墨色背景 `FbngPalette.background` = `#12140F`。
- 正文是 `onBackground` / `onSurface` = `#F3F0E6`。按钮文字 `onPrimary` 和 `inverseSurface` 是奶油色 `#F6F3EA`。
- 按钮是朱砂 `primary` = `#C24223`，次要强调是黄铜 `secondary` = `#E0B15A`。
- 色值只写在 `ui/theme/FbngPalette.kt`，经 `Theme.kt` 进 `ColorScheme`。按钮用 `ui/theme/Controls.kt` 的 `FbngButton`。
- 还没有单独的字号、圆角、间距、动效文件。字号用 `MaterialTheme.typography`。

这一节更新之前，新界面沿用上面这套 token，不要另起一套。

## Token

颜色、字号、间距、圆角从 theme 取，不在组件里新写死。

- 颜色用 `MaterialTheme.colorScheme` 的角色，成对使用 onX / X。正文对比度至少 4.5:1，按钮和描边这类非文字标记至少 3:1。`ui/theme/ContrastTest.kt` 锁的是现在这些配对。
- 字号用 `MaterialTheme.typography`。手柄距离下，新写的正文不要小于 16sp，作为焦点标题的字不要小于 20sp。最多一到两个字体家族。等宽只用于数字或计时，不用于普通标签。
- 间距以 4dp 为基准，新间距只用 4 / 8 / 12 / 16 / 24 / 32 / 48。屏幕外边距的起点是 compact 16dp、medium 24dp、expanded 32dp。
- 圆角按层级区分：小控件 8dp，卡片 12dp，面板和对话框 24dp。不要整屏同一个圆角。
- 层级用色调表面（surface container），不要给每张卡片铺同一块灰阴影。同屏最多两级。
- 现在库页外边距是单列 20dp、双列 24dp，按钮高 52dp，HUD 黑边门槛是 `MinHudBar`（56dp，够放 48dp 触控再加 4dp 内边距）。这些先留着。改那个界面的布局时再收进 `ui/theme` 的命名 token，不要顺手重排。
- 游戏画面后面的纯黑是黑边底，不是表面色。继续用黑色，不要换成 `surface`。
- 动效不要在页面加载时逐块淡入上滑。这个工程的 Compose BOM `2026.09.00` 解析到的是 Material 3 **1.4.0**，没有稳定的 `MaterialExpressiveTheme`。不要为了动效去开实验 API。系统开了「移除动画」时，动画时长应为 0。

## 不能做的事

这些是交互和可访问性约束，不是视觉风格禁令。

- 在组件里新写 `Color(0x…)`，或在 token 之外新写一截魔法 `dp` / `sp`。
- 只有长按、滑动删除、悬停才能完成，没有手柄等价操作。
- 文案写「点击这里」「轻触这里」。同一动作全流程用同一个动词（「导入」「开始游戏」「存档」「读档」「退出」）。错误写原因和怎么修，不道歉。
- 关键功能只能靠 Start、Select 或 Menu。这三个键可以做快捷入口。
- 用 `isTablet`、屏幕英寸或机型名决定布局，或把手机布局等比放大到内屏。

## 手柄焦点

壳层（游戏库、导入结果、缺核心）要能只用方向键走完。游戏运行中不要拦截：`GamepadBridge` 在有 `GLRetroView` 时把方向键、A/B/X/Y 和肩键交给核心，回中之后壳层才重新接收。

- 进入每个界面时要有确定的初始焦点（主操作，或上次的那一项）。`FocusRequester` 放在可聚焦修饰符之前，`requestFocus()` 放在 `LaunchedEffect` 里，key 是目标出现的条件。
- 返回、关掉对话框、折叠、展开、旋转之后，按稳定 id 恢复焦点。找不到就落到确定的后备项。
- A / Enter / 方向键中心键 = 确认，B / Back = 返回。文本框要能用 B 退出编辑，而不是退出页面。X、Y、L1、R1 可以做收藏、搜索、翻页，但不能是唯一入口。
- 摇杆和 HAT 不自动变成焦点移动。若要支持，在 Activity 里对 `AXIS_HAT_X/Y` 和左摇杆做边沿检测，一次推动只走一格，回中才能再走。游戏运行中不做这件事。
- 焦点态要隔一米能看见：focused 约为 1.05 倍（大卡约 1.025）并加 2–3dp 的 primary 描边或发光；pressed 回到 1.0；disabled 降低不透明度。不要只靠默认 ripple。同一时刻只有一个焦点。
- 方向优先用默认空间搜索。只对走错的边用 `focusProperties`。焦点项要滚进可视区，不要贴死边缘。
- 每个新界面或改过焦点的界面，加一个 Compose 测试：`performKeyInput { pressKey(Key.DirectionDown) }` 然后 `assertIsFocused()`。现在的库页和 HUD 还没有自绘焦点，不要在无关改动里补上。

## 窗口与折叠

按窗口宽度分，不按设备分。断点是 WindowSizeClass 的 600dp 和 840dp：

- compact，宽度 < 600dp（外屏竖持）：单列。详情是整页。
- medium，600–840dp（手机横持这一档）：内容可以加列。导航若出现，用 rail 而不是把手机底栏拉宽。
- expanded，宽度 ≥ 840dp（内屏展开）：列表和详情分两栏。不要只把字和图放大。

列数用可用宽度算（例如 `GridCells.Adaptive`），不写死列数。

现在的代码还没接 `WindowSizeClass`。`libraryUsesTwoPanes`（`LayoutPolicy.kt`）在宽度 ≥ 600dp，或竖直且 `isSeparating` 的铰链（书本姿态）时用双栏。游戏中的 HUD 不看窗口宽度，只看 `layoutGameHud`：画面按核心宽高比在安全区内居中，黑边够 56dp 就把存档 / 读档 / 退出放进黑边，不够就叠在外侧，不缩小画面。改布局时按上面的 600 / 840 做；不要为了这条规则单独把现有双栏从 600 改到 840。

桌面模式（tabletop）以真实铰链边界为准。半折且铰链水平时，上半屏是画面，下半屏是控制。用 `FoldingFeature.bounds` 换算到本地坐标（`rememberFoldSnapshot` 已经记下 `hingeStartPx` / `hingeEndPx`），禁止假设上下各一半，控件不要压在铰链上。折叠和展开时保留当前焦点、滚动位置和正在运行的游戏。`MainActivity` 已处理 `configChanges`，游戏中的 `GLRetroView` 不要为了旋转或折叠新建一份。

Compose UI 1.12.1 里的 `mediaQuery { windowPosture == Posture.Tabletop }` 仍是 `@ExperimentalMediaQueryApi`。这个仓库现在读的是 `androidx.window` 1.5.1 的 `FoldingFeature`。新的 tabletop 布局继续用这份铰链边界，不要为了换 API 去开实验开关。

Find N6 内外屏在默认「显示大小」下的实际 dp 还没在真机上量过。不要把像素和 ppi 换算的结果写成已测的 WindowSizeClass。量到之后把两个屏的宽度 dp 写进这一节。

## 改 UI 的完成定义

1. 先写 3 到 6 行：这个界面做什么、唯一的记忆点、用哪些 token、哪些交互约束被检查过。有参考图时写明学哪一点。
2. `@Preview` 覆盖 loading、empty（无 ROM）、error（DAT 或核心缺失）、正常这四态，并覆盖这次改动实际会走到的宽度（compact，以及 medium 或 expanded）。深色即可，这个应用没有浅色主题。动到文字时加一档 `fontScale = 1.5`。
3. 渲染自查：`./gradlew :app:recordRoborazziDebug`。打开 `app/src/test/screenshots/` 里新生成的 PNG 看一遍，按「元素 / 期望 / 实际 / 修复」列差异，最多改三轮。要证明间距或对齐变了，加 `-Proborazzi.dumpUiTree=true`，用生成的 `.uitree.json` 里的 bounds，不要只靠肉眼。
4. 有真机或折叠模拟器时，外屏、内屏、tabletop 各留一张，并用手柄从进入页面走完主流程，留下焦点态。没有设备就在结果里写明没拍。
5. `./gradlew :app:verifyRoborazziDebug` 必须通过。有意的视觉变化要重新 record，并在 PR 里说明改了哪几张。
6. 汇报命令、输出和截图路径。没有截图不算改完。

基线在 `app/src/test/screenshots/`。对比失败时的差异图在 `app/build/outputs/roborazzi/`，报告在 `app/build/reports/roborazzi/index.html`。普通的 `testDebugUnitTest` 会跑这些测试里的布局断言，但不会比对图片；比对只在 `verifyRoborazziDebug`。

不要把加载中的圆形或条形进度画进基线，它们每一帧都不一样。现有基线是静帧：库页导入结果（手机竖屏）、库页双栏（手机横屏和展开宽屏）、DAT 错误、HUD 的上下黑边 / 左右黑边 / 黑边太薄时的叠加、以及缺少核心。不是 9 种尺寸乘全部状态。

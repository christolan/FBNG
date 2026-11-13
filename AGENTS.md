# AGENTS.md — FBNG UI 规则

自用 Android 街机前端（LibretroDroid + 固定版本的 FBNeo）。界面是 Jetpack Compose，Material 3 深色方案。主设备是 OPPO Find N6，主输入是手柄，触屏是次要输入。界面包含收藏、最近、游戏库（含导入结果）和设置；游戏中支持自动存读档、手动存读档、暂停 / 恢复、重置和退出。仓库里没有 ROM 或 BIOS，也不要提交。

下面的字号、缩放、圆角、边距是建议起点，不是官方硬性标准。改它们之前先看现有界面，不要为了凑齐这些数字去重画一版。

## 视觉风格（B：极简主机启动器）

界面是主机启动器：炭灰底、圆角卡片、无衬线字。唯一强调色是电光蓝，用来做实心按钮、焦点描边、选中标记和进度。没有第二套强调色（原来的朱砂 `#C24223` 和黄铜已去掉）。错误仍用独立的浅红色，避免和「可以开始」混成同一个蓝。只有深色。`MainActivity` 用深色系统栏，`FbngTheme` 只套 `darkColorScheme`，没有动态色。

色值只写在 `ui/theme/FbngPalette.kt`，经 `Theme.kt` 进 `ColorScheme`。字号在 `FbngType.kt`，间距、圆角和焦点缩放在 `FbngDimens.kt`。按钮用 `ui/theme/Controls.kt` 的 `FbngButton`。

- 背景 `background` = `#101318`。表面 `surface` = `#171C24`。更高一层是 `surfaceContainerHigh` = `#1E2530`，再往上是 `surfaceContainerHighest` = `#293140`。最亮的表面 `surfaceBright` = `#313A4A`。不要在这些深色上用深色字。
- 正文 `onBackground` / `onSurface` = `#F4F7FB`。次要正文 `onSurfaceVariant` = `#D5DCE6`。这两级在 `surfaceBright` 上仍超过 4.5:1。
- 电光蓝按钮 `primary` = `#206AFB`，按钮文字 `onPrimary` = `#FFFFFF`（约 4.65:1）。
- 电光蓝文字（选中的导航、已通过校验的状态）用 `secondary` = `#A9C4FF`。`primary` 贴在炭灰上大约只有 4:1，不够当正文，所以蓝色正文用 `secondary`，蓝色色块和描边用 `primary`。
- 焦点描边是 2dp 的 `primary`。实心 `primary` 按钮上改用 `onPrimary` 亮边，否则蓝边贴着蓝底看不见。焦点缩放：普通控件 1.05，大卡片 1.025；按下回到 1.0。缩放立即到位，不在页面加载时逐块淡入。
- 字体是无衬线（`FontFamily.SansSerif`）。正文和按钮 16sp，作为焦点标题的字不小于 20sp。
- 圆角：小控件 8dp，卡片 12dp，Banner 和空状态面板 24dp。手柄按键提示的 A/B 是圆形键帽。
- 导航：窗口宽度 ≥ 600dp，或竖直且分开的书本铰链，用左侧导航栏。宽度 < 600dp 且不是书本铰链，用底部导航栏。外边距 compact 16dp、medium 24dp、expanded 32dp，断点是 600dp 和 840dp（`shellWidth` / `shellUsesNavigationRail`）。列数按可用宽度算，不写死。medium 和 expanded 都是左侧栏，expanded 只把栏加宽、外边距加大，不把手机底栏拉宽。
- 导航依次是收藏、最近、游戏库、设置。「最近」按成功启动的时间倒序去重，保存在本地。收藏是启动首页。导入和同步从设置进入。同步页显示「即将推出」。设置也显示核心版本、许可和手柄映射说明。收藏、最近卡片和游戏详情使用真实封面，联网时只为收藏或玩过的游戏静默获取；设置提供手动获取缺失封面和更新入口。固定图片区保持原图比例，无图时显示游戏名。Banner 仍用文字。不要画假游戏，也不要放 ROM 画面。搜索只在窄屏游戏库里，按名称过滤这一款。
- 游戏中的黑边仍由 `layoutGameHud` 计算，不改那套公式。黑边上的暂停 / 恢复、存档、读档、重置、退出是炭灰底、电光蓝细边的圆角按钮。画面背后继续用纯黑。
- 游戏运行时方向键、摇杆和 A/B/X/Y 仍交给核心，壳层不抢。手动暂停时改由壳层接收，恢复后交还核心；Menu / Mode 是暂停快捷键，触屏按钮同样可用。壳层（游戏库、导入、同步、设置、缺核心）里，方向键和摇杆一次一格移动焦点，A 确认，B 返回。文本框里 B 只退出编辑。游戏库根页上的 B 不退出应用。

## Token

颜色、字号、间距、圆角从 theme 取，不在组件里新写死。

- 颜色用 `MaterialTheme.colorScheme` 的角色，成对使用 onX / X。正文对比度至少 4.5:1，按钮和描边这类非文字标记至少 3:1。`ui/theme/ContrastTest.kt` 锁的是现在这些配对。
- 字号用 `MaterialTheme.typography`。手柄距离下，新写的正文不要小于 16sp，作为焦点标题的字不要小于 20sp。最多一到两个字体家族。等宽只用于数字或计时，不用于普通标签。
- 间距以 4dp 为基准，新间距只用 4 / 8 / 12 / 16 / 24 / 32 / 48。屏幕外边距的起点是 compact 16dp、medium 24dp、expanded 32dp。
- 圆角按层级区分：小控件 8dp，卡片 12dp，面板和对话框 24dp。不要整屏同一个圆角。
- 层级用色调表面（surface container），不要给每张卡片铺同一块灰阴影。同屏最多两级。
- 壳层外边距是 compact 16dp、medium 24dp、expanded 32dp，按钮最小高度 48dp，都在 `FbngDimens`。HUD 黑边门槛仍是 `MinHudBar`（56dp，够放 48dp 触控再加 4dp 内边距），不因为换样式去改。
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
- 壳层和缺核心页面有自绘焦点。游戏运行中 HUD 按钮带同样的焦点样式，方向键由 `GamepadBridge` 交给核心。

## 窗口与折叠

按窗口宽度分，不按设备分。断点是 WindowSizeClass 的 600dp 和 840dp：

- compact，宽度 < 600dp（外屏竖持）：单列。详情是整页。
- medium，600–840dp（手机横持这一档）：内容可以加列。导航若出现，用 rail 而不是把手机底栏拉宽。
- expanded，宽度 ≥ 840dp（内屏展开）：列表和详情分两栏。不要只把字和图放大。

列数用可用宽度算（例如 `GridCells.Adaptive`），不写死列数。

壳层用 `shellWidth` / `shellUsesNavigationRail`（`LayoutPolicy.kt`）读取上面的 600dp 和 840dp，没有改用还在实验阶段的 adaptive API。`libraryUsesTwoPanes` 仍是「左侧栏」的判定：宽度 ≥ 600dp，或竖直且 `isSeparating` 的铰链（书本姿态）。水平的桌面铰链不会因此打开左侧栏。游戏中的 HUD 不看窗口宽度，只看 `layoutGameHud`：画面按核心宽高比在安全区内居中，黑边够 56dp 就把游戏操作按钮放进黑边，不够就叠在外侧，不缩小画面。

桌面模式（tabletop）以真实铰链边界为准。半折且铰链水平时，上半屏是画面，下半屏是控制。用 `FoldingFeature.bounds` 换算到本地坐标（`rememberFoldSnapshot` 已经记下 `hingeStartPx` / `hingeEndPx`），禁止假设上下各一半，控件不要压在铰链上。折叠和展开时保留当前焦点、滚动位置和正在运行的游戏。`MainActivity` 已处理 `configChanges`，游戏中的 `GLRetroView` 不要为了旋转或折叠新建一份。

Compose UI 1.12.1 里的 `mediaQuery { windowPosture == Posture.Tabletop }` 仍是 `@ExperimentalMediaQueryApi`。这个仓库现在读的是 `androidx.window` 1.5.1 的 `FoldingFeature`。新的 tabletop 布局继续用这份铰链边界，不要为了换 API 去开实验开关。

Find N6 内外屏在默认「显示大小」下的实际 dp 还没在真机上量过。不要把像素和 ppi 换算的结果写成已测的 WindowSizeClass。量到之后把两个屏的宽度 dp 写进这一节。

## 改 UI 的说明

先用 3 到 6 行说明界面用途、唯一的记忆点、使用的 token 和涉及的交互约束。有参考图时写明学哪一点。

## 本地构建签名 Release APK

以下签名配置仅用于本地构建。GitHub Actions 从仓库 Secrets 读取签名材料，并在 runner 上解码密钥库，无需本地配置文件或手动设置环境变量。流程见 [android-release.yml](.github/workflows/android-release.yml)。

1. 准备 Android SDK 37.2、Build-Tools 36.0.0、NDK 28.2.13676358 和 CMake 3.22.1。通过 `local.properties` 的 `sdk.dir` 或 `ANDROID_HOME` 指定 SDK。Gradle 的 Java 版本以 `gradle/gradle-daemon-jvm.properties` 为准，本机可用 Android Studio 自带的 Java 25。
2. 把 PKCS#12 发布密钥库和 `release-signing.env` 放在仓库外的 `~/.config/fbng/`，文件权限设为 `600`。环境文件导出 `JAVA_HOME`、`FBNG_RELEASE_STORE_FILE`、`FBNG_RELEASE_STORE_PASSWORD`、`FBNG_RELEASE_KEY_ALIAS` 和 `FBNG_RELEASE_KEY_PASSWORD`。`FBNG_RELEASE_STORE_FILE` 指向密钥库的绝对路径。
3. 在仓库根目录加载配置、构建并验签。若 SDK 装在其他位置，按实际路径设置 `ANDROID_HOME`。

   ```bash
   (
     set -e
     source "$HOME/.config/fbng/release-signing.env"
     ./gradlew :app:assembleRelease --no-daemon --no-configuration-cache

     tools="${ANDROID_HOME:-$HOME/Library/Android/sdk}/build-tools/36.0.0"
     apk="app/build/outputs/apk/release/app-release.apk"
     "$tools/apksigner" verify --min-sdk-version 21 --verbose "$apk"
     "$tools/apksigner" verify --min-sdk-version 24 --verbose "$apk"
     "$tools/apksigner" verify --min-sdk-version 33 --verbose --print-certs "$apk"
     "$tools/zipalign" -c -P 16 4 "$apk"
   )
   ```

   分别确认 API 21、24、33 的输出中 v1、v2、v3 签名为 `true`，证书 SHA-256 与发布证书一致，且 16 KB 对齐检查通过。

如果本机仅因 Release Lint 依赖未缓存且无法连接 Google Maven 而构建失败，可以临时在上述 Gradle 命令中加 `--offline -x :app:lintVitalRelease`，并执行同样的验签和对齐检查。

# AGENTS.md — FBNG UI 规则

自用 Android 街机前端（LibretroDroid + 固定版本的 FBNeo）。界面是 Jetpack Compose，Material 3 深色方案。主设备是 OPPO Find N6，主输入是手柄，触屏是次要输入。界面包含收藏、最近、游戏库（含导入结果）和设置；游戏中支持自动存读档、手动存读档、暂停 / 恢复、重置和退出。仓库里没有 ROM 或 BIOS，也不要提交。

下面的字号、圆角、边距是建议起点，不是官方硬性标准。改它们之前先看现有界面，不要为了凑齐这些数字去重画一版。

## 视觉风格（Islands：极简主机启动器）

界面是 Islands 主机启动器：炭灰底、相互留出间距的圆角卡片、无衬线字。导航、固定标题栏、游戏卡片、设置分区和暂停页使用独立色调表面，不加阴影。复用 `ui/theme/Islands.kt` 的容器。

存档弹窗的自动存档固定在左侧；「新建槽位」是右侧手动槽位横滑列表的最后一张等尺寸卡片，不再放在顶部。达到 10 个手动槽位后，末尾卡片保留并禁用，显示「已达上限」。连续新建时将末尾卡片滚入可视区再恢复焦点；读档列表不显示新建卡片。

界面的唯一强调色是电光蓝，用来做实心按钮、焦点描边、选中标记和进度。没有第二套强调色（原来的朱砂 `#C24223` 和黄铜已去掉）。错误仍用独立的浅红色，避免和「可以开始」混成同一个蓝。只有深色。`MainActivity` 用深色系统栏，`FbngTheme` 只套 `darkColorScheme`，没有动态色。

色值只写在 `ui/theme/FbngPalette.kt`，经 `Theme.kt` 进 `ColorScheme`。字号在 `FbngType.kt`，间距、圆角和焦点描边尺寸在 `FbngDimens.kt`。按钮用 `ui/theme/Controls.kt` 的 `FbngButton`。

- 背景 `background` = `#101318`。表面 `surface` = `#171C24`。更高一层是 `surfaceContainerHigh` = `#1E2530`，再往上是 `surfaceContainerHighest` = `#293140`。最亮的表面 `surfaceBright` = `#313A4A`。不要在这些深色上用深色字。
- 正文 `onBackground` / `onSurface` = `#F4F7FB`。次要正文 `onSurfaceVariant` = `#D5DCE6`。这两级在 `surfaceBright` 上仍超过 4.5:1。
- 电光蓝按钮 `primary` = `#206AFB`，按钮文字 `onPrimary` = `#FFFFFF`（约 4.65:1）。
- 电光蓝文字（选中的导航、已通过校验的状态）用 `secondary` = `#A9C4FF`。`primary` 贴在炭灰上大约只有 4:1，不够当正文，所以蓝色正文用 `secondary`，蓝色色块和描边用 `primary`。
- 焦点描边是 2dp 的 `primary`。实心 `primary` 按钮上改用 `onPrimary` 亮边，否则蓝边贴着蓝底看不见。获得焦点或按下时，按钮和卡片保持原尺寸。
- 字体是无衬线（`FontFamily.SansSerif`）。正文和按钮 16sp，作为焦点标题的字不小于 20sp。
- 圆角：小控件 8dp，卡片 12dp，Banner 和空状态面板 24dp。壳层不显示底部 A 确认 / B 返回提示条。
- 导航：窗口宽度 ≥ 600dp，或竖直且分开的书本铰链，用左侧导航栏。宽度 < 600dp 且不是书本铰链，用底部导航栏。外边距统一 16dp，导航断点仍是 600dp 和 840dp（`shellWidth` / `shellUsesNavigationRail`）。游戏库固定为 2 列 × 5 行，每页 10 个游戏，卡片自适应宽高。medium 和 expanded 都使用 96dp 的紧凑左侧栏，图标在标签上方；壳层统一使用 16dp 外边距，优先把空间留给游戏内容。
- 导航依次是收藏、最近、游戏库、设置。「最近」按成功启动的时间倒序去重，保存在本地。收藏是启动首页。导入和同步从设置进入，标题显示「设置/导入」和「设置/同步」。新增游戏通过配置游戏库目录完成。同步页显示「即将推出」。设置也显示核心版本、许可和手柄映射说明。收藏、最近使用等尺寸的横滑封面卡片，卡片撑满标题下方可用高度，封面区占据标题区之外的剩余高度，宽度随封面区高度调整。收藏、最近卡片和游戏详情使用真实封面，联网时只为收藏或玩过的游戏静默获取；设置提供手动获取缺失封面和更新入口。收藏和最近的封面裁切填满图片区，无图时在原位置显示游戏名。详情封面按原图比例显示。Banner 仍用文字。不要画假游戏，也不要放 ROM 画面。游戏库使用字母筛选和状态筛选。字母面板支持连续输入，按中文名拼音首字母、中文名、英文描述、套件 ZIP 名和实际来源文件名匹配，忽略大小写。面板按可用宽高排键，A–Z、数字、点、下划线、连字符、退格、清空和完成须同时可见；零结果时也能继续编辑。
- 游戏运行时只有游戏画面和纯黑边，不显示常驻 HUD 或悬浮按钮，也不为控件缩小画面。系统返回（侧滑和返回键）先打开独立的不透明暂停页，再次返回才自动存档并退出游戏；存读档和确认框打开时先关闭当前面板。暂停页沿用炭灰底和电光蓝控件，提供恢复、存档、读档、重置、退出。
- 游戏运行时方向键、摇杆和 A/B/X/Y 仍交给核心，壳层不抢。手动暂停时改由壳层接收，恢复后交还核心；Menu / Mode 是暂停快捷键，触屏按钮同样可用。壳层（游戏库、导入、同步、设置、缺核心）里，方向键和摇杆一次一格移动焦点，A 确认，B 返回。文本框里 B 只退出编辑。游戏库根页上的 B 不退出应用。

## Token

颜色、字号、间距、圆角从 theme 取，不在组件里新写死。

- 颜色用 `MaterialTheme.colorScheme` 的角色，成对使用 onX / X。正文对比度至少 4.5:1，按钮和描边这类非文字标记至少 3:1。`ui/theme/ContrastTest.kt` 锁的是现在这些配对。
- 字号用 `MaterialTheme.typography`。手柄距离下，新写的正文不要小于 16sp，作为焦点标题的字不要小于 20sp。最多一到两个字体家族。等宽只用于数字或计时，不用于普通标签。
- 间距以 4dp 为基准，新间距只用 4 / 8 / 12 / 16 / 24 / 32 / 48。屏幕外边距统一从 16dp 起。
- 圆角按层级区分：小控件 8dp，卡片 12dp，面板和对话框 24dp。不要整屏同一个圆角。
- 层级用色调表面（surface container），不要给每张卡片铺同一块灰阴影。同屏最多两级。
- 壳层外边距统一 16dp，按钮最小高度 48dp，都在 `FbngDimens`。暂停页使用现有间距和按钮 token，不依赖黑边是否能放下控件。
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
- 焦点态要隔一米能看见：用 2dp 的 primary 描边，实心 primary 按钮用 onPrimary 描边。焦点和按下时保持控件原尺寸，禁用态使用独立的颜色。不要只靠默认 ripple。同一时刻只有一个焦点。
- 方向优先用默认空间搜索。只对走错的边用 `focusProperties`。焦点项要滚进可视区，不要贴死边缘。
- 壳层、缺核心页面和暂停页有自绘焦点。暂停页默认选中「恢复」，关闭存读档或确认框后恢复到原操作；游戏运行时方向键由 `GamepadBridge` 交给核心。

## 窗口与折叠

按窗口宽度分，不按设备分。断点是 WindowSizeClass 的 600dp 和 840dp：

- compact，宽度 < 600dp：底部导航。游戏库每页固定 2 列 × 5 行，卡片平分可用宽高，详情是独立整页。
- medium，600–840dp：使用紧凑 rail。游戏库分页铺满内容区，不常驻详情栏。
- expanded，宽度 ≥ 840dp：分页目录仍固定 2 列 × 5 行，卡片平分可用宽高；选择后再进入详情。详情有真实封面时可左右排列封面和操作。

壳层各页统一使用 56dp 的固定标题栏。游戏库的字母筛选、状态筛选和分页与标题同行，窄窗口用紧凑按钮。游戏库卡片尺寸统一，每页固定 2 列 × 5 行，共 10 个游戏，末页保留相同尺寸。标题和文件名都固定单行，超长省略；卡片较矮时两者并排显示。卡片平分可用宽高。按列从上到下排列，页面不无限滚动；上一页 / 下一页按钮和手柄 L1 / R1 都能翻页。筛选变化回到第一页，窗口变化保留当前游戏所在页，关闭详情恢复该条目焦点。

壳层用 `shellWidth` / `shellUsesNavigationRail`（`LayoutPolicy.kt`）读取上面的 600dp 和 840dp，没有改用还在实验阶段的 adaptive API。`libraryUsesTwoPanes` 保留为旧布局辅助函数；当前目录详情不再常驻。左侧栏的判定仍是：宽度 ≥ 600dp，或竖直且 `isSeparating` 的铰链（书本姿态）。水平的桌面铰链不会因此打开左侧栏。游戏画面由 LibretroDroid 按核心宽高比在安全区内居中；运行时不显示黑边控件或叠加控件。暂停页覆盖显示时保留同一份 `GLRetroView`，恢复时不重新创建核心。原 `layoutGameHud` 公式保留，但不再决定运行时控件布局。

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

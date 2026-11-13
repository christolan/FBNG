# FBNG

FBNeo Next Generation 是一个自用 Android 街机前端。它用固定版本的 FinalBurn Neo 核心运行游戏，并用同一版本的街机 DAT 识别游戏、父套件和 BIOS。

界面是 Jetpack Compose。模拟由 [LibretroDroid](https://github.com/Swordfish90/LibretroDroid) 0.13.2 加载 FBNeo 核心完成。这一阶段没有 MAME，没有云同步，也不上架。

## ROM 和 BIOS 不在这里

仓库、源代码和构建出的 APK 都不包含任何游戏 ROM 或 BIOS，包括 `kovsh.zip` 和 `pgm.zip`。请使用你自己持有的 ZIP。FBNeo 核心是已经核对过的那一份，放在 `core/fbneo/libfbneo_libretro_android.so`。构建复制它之前会核对 SHA-256、二进制标记和 16 KB 对齐，对不上就停止。

FBNeo 的许可全文在 `third_party/FBNeo/license.txt`。LibretroDroid 是 GPLv3。这个应用只供个人使用，不收费、不接受捐赠、不发布到应用商店。

## 构建

需要 JDK 21 和 Android SDK，并且已经安装 `platforms/android-37.2`、Build-Tools 36、NDK `28.2.13676358` 和 CMake 3.22.1。NDK 用来编译读取核心画面比例的小库 `libfbng_aspect.so`，它和别的原生库一样要按 16 KB 对齐。在 `local.properties` 里写 `sdk.dir`，或者设置 `ANDROID_HOME`。

本机调试包用仓库里的测试证书：

```bash
./gradlew :app:assembleDebug
```

产物是 `app/build/outputs/apk/debug/app-debug.apk`。只含 `arm64-v8a`，对应 OPPO Find N6 和 RP6。`minSdk` 是 33，`targetSdk` 是 36，`compileSdk` 是当前稳定版 37.2。核心为了 16 KB 页对齐以未压缩形式放进 APK，包大约 100 MB。测试证书和口令只放在私有仓库的 `keystore/`，APK 本身仍然不会提交。

要打 Release 包，把 PKCS12 证书留在仓库外面，设置四个环境变量后再执行 `./gradlew :app:assembleRelease`：

- `FBNG_RELEASE_STORE_FILE`：keystore 文件路径
- `FBNG_RELEASE_STORE_PASSWORD`
- `FBNG_RELEASE_KEY_ALIAS`（这把钥匙的别名是 `fbng`）
- `FBNG_RELEASE_KEY_PASSWORD`

产物是 `app/build/outputs/apk/release/app-release.apk`，签名是 v1、v2 和 v3。没有这四个变量时，Release 构建不使用 debug 测试证书。

推送到 `main`、向 `main` 打开 pull request，或在 Actions 里手动运行 “Assemble release APK”，都会执行 `./gradlew :app:testDebugUnitTest :app:assembleRelease`。Actions 用上面同名的四个仓库 secrets 签名；keystore 的 secret 是 PKCS12 文件的 base64，解码后不进 git。如果 APK 里没有未压缩的 `lib/arm64-v8a/libfbneo_libretro_android.so`，构建会失败。`apksigner` 核对 v1、v2、v3，`zipalign -P 16` 核对 16 KB 对齐。Actions 里留下名为 `FBNG-<versionName>` 的 artifact（普通 zlib zip，保留 14 天）。推送到 `main` 或手动运行时，还会在本私有仓库的 Releases 里发一个版本号 Release：标题 `FBNG <versionName>`，tag `v<versionName>`，附件 `FBNG-<versionName>.apk`。0.x 仍是 prerelease，名字里不再带 debug。

构建会做两件和页面对齐有关的事：

- 仓库里的 FBNeo 核心和 LibretroDroid 0.13.2 的 arm64 库都必须是 ELF `PT_LOAD` 对齐至少 16 KB。0.13.2 已经用 `-Wl,-z,max-page-size=16384` 编译；0.14.0 预发布版没有额外的 16 KB 改动，所以这里用稳定版 0.13.2。
- 原生库以未压缩的形式打进 APK，并用 `zipalign -P 16` 检查 16 KB 对齐。`assembleDebug` 和 `assembleRelease` 结束时都会跑这项检查。

## 固定的 FBNeo 核心

| 项目 | 值 |
| --- | --- |
| 仓库里的文件 | `core/fbneo/libfbneo_libretro_android.so`（73,811,312 字节，从 FBNG 0.1.3 的 APK 原样抽出） |
| 文件 SHA-256 | `6d3994f4539e2d11131d54969eee55fee3c8ac97d97659077ded230c17355bc8` |
| 构建时间 | 2026-11-09 06:44 UTC |
| 提交 | [`77016b4dff6c76beba08d24ea1c61ed2750bc0a7`](https://github.com/libretro/FBNeo/commit/77016b4dff6c76beba08d24ea1c61ed2750bc0a7) |
| 二进制里的标记 | `GIT77016b4` |
| 当初的 zip SHA-256 | `7100778698fe9c4b9bd42f49de3683bd7999971b1ebb2df4ef43298e2b116bf1` |
| DAT | `libretro/FBNeo` 同一提交下的 `dats/FinalBurn Neo (ClrMame Pro XML, Arcade only).dat`，版本 1.0.0.03 |
| DAT SHA-256 | `9484cce16c1f37f5de5fd6d92a300a57d037c68b77744686dc0c3cff01c169d2` |

这一个 `.so` 进 git，是因为 buildbot 的 `latest` 地址后来换成了另一份文件，原来的 zip 已经下不到。0.1.3 的 APK 里这份库和那个 zip 是同一次构建打进去的，DAT 也还是仓库里同一份。不要把它换成更新的 nightly，否则已经核对过的 `kovsh` / `pgm` 可能对不上。摘要、提交和标记写在 `core/fbneo.pin.properties`。文件一对不上，构建就会失败。

`useLegacyPackaging` 是 false，安装清单里的 `extractNativeLibs` 也是 false，所以系统不会把核心复制到 `nativeLibraryDir`。应用如果在那个目录里找到已解压的文件，就直接用它；否则把 `base.apk!/lib/arm64-v8a/libfbneo_libretro_android.so` 交给 LibretroDroid。`dlopen` 从 API 23 起可以打开这种未压缩且按页对齐的 zip 条目。

## 添加文件夹和导入 ZIP

在「设置 → 导入」里选「添加文件夹」，可以添加多个本地目录。添加后和每次启动应用时，应用会递归扫描这些目录里的 ZIP。目录有变动时也可以选「重新扫描」。扫描结果会显示已识别的游戏、BIOS 和跳过的 ZIP 数量。移除目录会保留原文件、手动导入的 ZIP 和存档。

扫描时读取 ZIP 的文件索引来识别套件。再次扫描时，应用会复用大小和修改时间都未变的文件结果。已有游戏库在后台扫描期间可用，新识别的游戏会陆续加入列表。开始游戏时，应用会完整校验该游戏和它所需的父套件及 BIOS。

新手动导入会额外记录原始 ZIP 文件名，用于搜索；旧版已重命名且未记录的自定义原名无法还原，仍可按套件名搜索，重新导入后会记录原名。

也可以用「导入 zip」手动导入一个或多个归档。应用按 DAT 校验文件名、大小和 CRC-32，校验通过后以 FBNeo 的套件名存入私有目录。外层文件名用于区分内容相同的套件，例如多个版本共用同一份 ROM 时，请用对应的套件名命名 ZIP。

游戏库会显示已识别的游戏。克隆版缺少父套件或 BIOS 时，详情会列出缺少的 ZIP。游戏、父套件和 BIOS 可以来自不同目录，也可以与手动导入的 ZIP 搭配。如果游戏 ZIP 已经包含所需的父套件和 BIOS 文件，就可以直接启动。目录里的 ZIP 只在启动相关游戏时复制到临时目录。

## 游戏里能做的事

收藏、最近卡片和游戏详情支持真实封面。联网时，应用在后台为收藏或成功玩过的游戏获取缺失封面，不扫描下载整个游戏库。断网时任务等待联网，关闭界面后也由系统安排继续；系统省电限制可能延后执行。已有封面保存在应用私有目录，离线可用，下载失败时保留旧图，没有图片时显示游戏名。

「设置 → 游戏封面」提供「获取缺失封面」和「更新封面」，两者仍只处理收藏或玩过的游戏；后者会重新下载已有图片。手动任务显示等待、结果和失败数量。游玩范围复用「最近」中的成功启动记录，已有记录在升级后也可获取封面。取消收藏后，已玩过的游戏仍会参与获取；已有图片不会被删除。

图片来自 [Libretro FBNeo 图库](https://github.com/libretro-thumbnails/FBNeo_-_Arcade_Games) 的 `Named_Boxarts`，不使用标题画面或游戏截图。内置索引按固定 DAT 的英文描述和 `cloneof` 父游戏生成，部分游戏暂无对应封面。手动更新从同一图片路径获取最新内容；新增或更名的图片需更新内置索引，可运行 `python3 tools/update_cover_index.py` 重新生成。索引只包含路径，不把图片打进 APK。本版不包含本地自定义封面导入。

启动后进入「收藏」，收藏和最近使用小封面配游戏名、来源文件名的紧凑卡片；无封面时直接显示标题。可从收藏卡片开始游戏，也可在游戏库的详情中添加或取消收藏。宽窗口用左侧栏，窄窗口用底部栏，导航依次为「收藏」「最近」「游戏库」「设置」。「最近」按实际启动游戏的顺序排列，同一款游戏只显示一次，重开应用后仍会保留。游戏库使用紧凑的分页目录，不再常驻右侧详情栏；每页数量根据可用宽高和字号计算，按列从上到下排列。可用「上一页」「下一页」或手柄 L1 / R1 翻页，打开详情再返回时回到原游戏所在页。所有宽度都能搜索，支持中文名、中文名拼音首字母、英文描述、套件文件名和实际来源 ZIP 文件名，忽略大小写，例如 SGZJ 匹配三国战纪，HJDT 匹配合金弹头，KOF98 或 kof98.zip 匹配拳皇对应文件。字母筛选面板可连续输入字母和数字，支持退格、清空和完成；无结果时仍可继续编辑。修改筛选条件后回到第一页。「导入」和「同步」从设置进入，同步页显示「即将推出」。设置也显示核心版本和手柄映射说明。游戏运行时不显示常驻操作按钮。系统侧滑返回和返回键统一处理：第一次返回暂停画面和声音并打开独立暂停页，再次返回自动存档并退出游戏。暂停页以一行「恢复」和两行并排操作提供「存档 / 读档」「重置 / 退出」，默认焦点在「恢复」；存读档弹窗和确认框打开时，返回先关闭当前面板。手柄菜单功能键（Android MENU / BUTTON_MODE，实体键取决于设备映射）可打开或关闭暂停页，B 恢复游戏。切回前台仍保留暂停页和暂停状态。重置需要确认，会清除当前游戏的自动存档和全部手动槽位并重新开始，不影响其他游戏。

每款游戏都有一个由系统维护的自动存档，以及可新建的多个手动槽位。进入游戏会读取自动存档，退出按钮、暂停页再次系统返回和切到后台会更新自动存档。打开「存档」或「读档」会暂停画面和声音，并显示槽位弹窗：自动存档固定在左侧，只允许读取；手动槽位在右侧横向滚动，可用触屏滑动或手柄方向键选择。存档时可新建槽位，覆盖已有槽位需要确认；读取任何槽位前都会提示当前未存档进度将丢失。关闭弹窗会恢复打开前的暂停状态，确认提示取消后仍留在槽位弹窗中。

新存档写在 `files/states/<套件名>/slot<N>.save`，其中 `slot0.save` 是自动存档，`slot1.save` 起是手动槽位。每个文件包含进度、核心提交号、保存时间和游戏画面截图；截图直接取自游戏画面，不包含按钮和弹窗。先写临时文件、`fsync` 落盘，再原子替换槽位，防止进度和封面对不上。原有 `slot0.state` / `slot0.properties`（包括 `kovsh` 的旧存档）仍可作为自动存档读取，没有截图时显示占位提示。读档失败会保留原文件并停止自动覆盖，避免破坏旧进度；可重新读档或重置游戏。核心提交号不一致时会提示，因为 FBNeo 不保证跨版本读档。

有线和蓝牙手柄走 LibretroDroid 现成的按键和摇杆转发（包含 Android 和 RetroPad 的 A/B、X/Y 对调，以及 `controllerNumber` 分端口）。这一阶段没有映射界面。

应用从启动起固定为横屏，不提供竖屏切换；大屏同样申请保留横屏限制。游戏画面铺满安全区内的播放区域，由 LibretroDroid 按核心报告的宽高比居中：比屏幕窄就左右留黑边，比屏幕矮就上下留黑边。比例来自核心的 AV info，运行中如果核心用 `SET_GEOMETRY` 改了比例，黑边会跟着重算。读不到比例时才退回 4:3，画面仍然不缩小。黑边保持纯黑，无论宽窄都不放常驻按钮，也不叠加悬浮控件；操作集中在暂停页，因此不依赖加载前预测黑边尺寸，不为控件缩小游戏画面。暂停页使用不透明背景，只在暂停或出错时显示。`MainActivity` 自己处理 `configChanges`，游戏画面是同一个 `GLRetroView`，只改它的尺寸，不新建核心。旋转、折叠、窗口大小、系统栏和挖孔都会重算。系统如果仍然重建 Activity（比如没列进 `configChanges` 的变化），这次游戏会结束，进度靠自动存档恢复。`GLSurfaceView` 不能跨进程保留，进程死亡后会在下次启动游戏时自动读档。游戏库在窗口宽度达到 600dp，或处于书本姿态的竖直铰链时，用左侧导航栏；更窄的窗口用底部导航栏。壳层外边距统一 16dp，左侧栏缩为 96dp，为分页目录留出更多空间。这和游戏中的黑边无关。

## 测试

```bash
./gradlew :app:testDebugUnitTest
```

单测覆盖 DAT 解析、ZIP 校验、文件夹扫描、拆分套件与 BIOS 依赖，以及存档的持久化。Compose 测试覆盖多游戏选择和手柄焦点。

# FBNG

FBNeo Next Generation。这是一个只给自己用的 Android 街机前端，Phase 0 只做一件事：导入你自己的 zip，然后用固定版本的 FinalBurn Neo 核心运行《三国战纪：风云再起》（套件名 `kovsh`，中国版 104）。

界面是 Jetpack Compose。模拟由 [LibretroDroid](https://github.com/Swordfish90/LibretroDroid) 0.13.2 加载 FBNeo 核心完成。这一阶段没有 MAME，没有云同步，也不上架。

## ROM 和 BIOS 不在这里

仓库、源代码和构建出的 APK 都不包含任何游戏 ROM 或 BIOS，包括 `kovsh.zip` 和 `pgm.zip`。没有占位 dump。这两个文件只能由你自己导入。核心 `.so` 也不进 git：Gradle 在构建时按下面的摘要下载，校验失败就停止。

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

- 下载的 FBNeo 核心和 LibretroDroid 0.13.2 的 arm64 库都必须是 ELF `PT_LOAD` 对齐至少 16 KB。0.13.2 已经用 `-Wl,-z,max-page-size=16384` 编译；0.14.0 预发布版没有额外的 16 KB 改动，所以这里用稳定版 0.13.2。
- 原生库以未压缩的形式打进 APK，并用 `zipalign -P 16` 检查 16 KB 对齐。`assembleDebug` 和 `assembleRelease` 结束时都会跑这项检查。

## 固定的 FBNeo 核心

| 项目 | 值 |
| --- | --- |
| 下载地址 | `https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/fbneo_libretro_android.so.zip` |
| 构建时间 | 2026-11-09 06:44 UTC（zip 内文件的时间戳） |
| 提交 | [`77016b4dff6c76beba08d24ea1c61ed2750bc0a7`](https://github.com/libretro/FBNeo/commit/77016b4dff6c76beba08d24ea1c61ed2750bc0a7) |
| 二进制里的标记 | `GIT77016b4` |
| zip SHA-256 | `7100778698fe9c4b9bd42f49de3683bd7999971b1ebb2df4ef43298e2b116bf1` |
| DAT | `libretro/FBNeo` 同一提交下的 `dats/FinalBurn Neo (ClrMame Pro XML, Arcade only).dat`，版本 1.0.0.03 |
| DAT SHA-256 | `9484cce16c1f37f5de5fd6d92a300a57d037c68b77744686dc0c3cff01c169d2` |

buildbot 的 `latest` 目录会被替换。摘要、提交和标记写在 `core/fbneo.pin.properties`。文件一变，构建就会失败，需要同时更新这三项和对应的 DAT，不能只换其中一个。核心打进本地 APK 可以，不要把 `.so` 提交进 git。

`useLegacyPackaging` 是 false，安装清单里的 `extractNativeLibs` 也是 false，所以系统不会把核心复制到 `nativeLibraryDir`。应用如果在那个目录里找到已解压的文件，就直接用它；否则把 `base.apk!/lib/arm64-v8a/libfbneo_libretro_android.so` 交给 LibretroDroid。`dlopen` 从 API 23 起可以打开这种未压缩且按页对齐的 zip 条目。

## 导入两个 zip

1. 安装 Release APK，打开 FBNG。
2. 点「导入 zip」，用系统文件选择器选一个或多个 zip。可以同时选，也可以分两次。外层文件名无所谓。
3. 应用按 FBNeo DAT 核对 zip **里面**每个文件的文件名、大小和 CRC-32。
   - `kovsh` 是 12 个游戏文件，其中 `pgm_p0605_v104.u1` 的 CRC 是 `7c78e5f3`，`kovsh_v100_china.asic` 的 CRC 是 `0f09a5c1`。DAT 里这个套件还列出了 6 个 PGM BIOS 文件，那些文件属于 `pgm`，不必打进 `kovsh.zip`。
   - `pgm` 是 BIOS 套件：`pgm_t01s.rom`、`pgm_m01s.rom`、`pgm_p01s.u20`、`pgm_p02s.u20`、`ddp3_bios.u37`、`bios.u42`。
4. 三项都一致时，文件会复制到应用私有目录，并改成规范名字：
   - 游戏：`files/roms/kovsh.zip`。FBNeo 用这个文件名决定驱动是 `kovsh`。
   - BIOS：`files/system/fbneo/pgm.zip`，同时复制一份到 `files/roms/pgm.zip`。核心找 BIOS 的顺序是游戏目录、`SYSTEM_DIRECTORY/fbneo/`、`SYSTEM_DIRECTORY/`。传给核心的 system 目录就是 `files/system`。
5. 对不上时不会写入，界面会写明是缺文件、CRC 不对、大小不对，还是内容对了但内部文件名不对。识别成别的套件也不会安装。
6. 库里会显示《三国战纪：风云再起》和英文名 Knights of Valour Super Heroes。BIOS 没通过校验时，「开始游戏」是关掉的，避免进一个黑屏。

## 游戏里能做的事

一层界面：导入、游戏库、游戏中的「存档 / 读档 / 退出」。没有 RetroArch 那种套娃菜单。

存档只有槽 1，写在 `files/states/kovsh/slot0.state`，并带上核心提交号。`serializeState()` 在 GL 线程上取数据，然后 `fsync` 落盘。进程被杀掉之后，重新开始游戏再点「读档」就能恢复。核心提交号不一致时会提示，因为 FBNeo 不保证跨版本读档。

有线和蓝牙手柄走 LibretroDroid 现成的按键和摇杆转发（包含 Android 和 RetroPad 的 A/B、X/Y 对调，以及 `controllerNumber` 分端口）。这一阶段没有映射界面。

折叠和旋转不锁方向。游戏画面铺满安全区内的播放区域，由 LibretroDroid 按核心报告的宽高比居中：比屏幕窄就左右留黑边，比屏幕矮就上下留黑边。比例来自核心的 AV info，运行中如果核心用 `SET_GEOMETRY` 改了比例，黑边会跟着重算。读不到比例时才退回 4:3，画面仍然不缩小。存档、读档、退出画在这些黑边里，左右各一条或上下各一条，不再占一列固定宽度，所以画面保持在正中。黑边薄于 56dp（放不下一个 48dp 按钮加内边距）时，不从画面里挖位置，改在黑边外侧叠一组不改变画面大小的按钮。`MainActivity` 自己处理 `configChanges`，游戏画面是同一个 `GLRetroView`，只改它的尺寸，不新建核心。旋转、折叠、窗口大小、系统栏和挖孔都会重算。系统如果仍然重建 Activity（比如没列进 `configChanges` 的变化），这次游戏会结束，进度靠槽 1。`GLSurfaceView` 不能跨进程保留，进程死亡后也是先启动游戏再读档。游戏库的双栏布局仍按窗口宽度和书本姿态的铰链切换，和游戏中的黑边无关。

## 测试

```bash
./gradlew :app:testDebugUnitTest
```

单测覆盖 DAT 解析、`kovsh` / `pgm` 的文件名、大小和 CRC、导入时的错配报告，以及存档文件在新的存储对象里还能读回来。没有模拟器，所以没有在真机上跑过 Find N6 或 RP6。

# 构建、测试与发布

## 本地环境

- 使用仓库的 Gradle Wrapper。Gradle daemon 使用 Java 25，应用的 Java 源码和字节码兼容级别为 21。CI 的启动 JDK 配置为 Temurin 21。版本见 [daemon JVM 配置](../gradle/gradle-daemon-jvm.properties)和 [Gradle Wrapper 配置](../gradle/wrapper/gradle-wrapper.properties)。终端构建前让 `JAVA_HOME` 指向 Java 25，例如 `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`。
- 安装 Android SDK `platforms;android-37.2`、Build-Tools 36.0.0、NDK `28.2.13676358` 和 CMake 3.22.1。通过 `local.properties` 的 `sdk.dir` 或环境变量 `ANDROID_HOME` 指定 SDK。
- 当前 APK 仅含 `arm64-v8a`，`minSdk` 为 33，`targetSdk` 为 36，`compileSdk` 为 37.2。应用版本和 ABI 由 [app/build.gradle.kts](../app/build.gradle.kts) 定义。

## Debug 与测试

在仓库根目录执行，并且先按下一节准备 `.env` 或环境变量；两样都没有时下面所有命令在配置阶段就会报错。

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。Debug 和 Release 共用包名、版本配置和发布证书，日常可直接用 Android Studio 运行 Debug 包并保留游戏库及存档。两种包的应用功能一致，Debug 包可连接调试器。Debug 和 Release 打包后都会检查原生库的 ELF 和 APK 16 KB 对齐。核心来源和校验见[固定的 FBNeo 核心与资源](fbneo-core.md)。

包名和证书匹配时，相同 `versionCode` 的两种包可以双向覆盖安装，更高 `versionCode` 的版本可以覆盖升级。端内更新只提供 `versionCode` 更高的 GitHub Release。日常调试沿用当前发布版本的 `versionCode`，准备发布新版时再递增，方便用当前正式包覆盖调试包。 [Android 应用更新规则](https://developer.android.com/google/play/app-updates)

[测试源码](../app/src/test/java/com/xiaojiwei/fbng/)使用 JUnit、Robolectric 和 Compose 测试库，覆盖 DAT、归档识别、目录扫描、核心加载路径、游戏库检索、存档、更新元数据、焦点、布局和对比度。

## 准备签名材料

Debug 和 Release 都用同一张发布证书签名，才能相互覆盖安装并保留数据（包名一致、`versionCode` 不低于已安装的包）。构建过程既没有备用证书也不输出未签名包，所以每台要编译的机器都必须先准备签名材料。

1. 从已有开发机器或 Actions Secrets 取得 PKCS#12 发布密钥库，放到仓库外，例如 `~/.config/fbng/fbng-release.p12`，权限设为 `600`。不要把密钥库放进仓库。

2. 在仓库根目录创建 `.env`。它已被 `.gitignore` 忽略，Gradle 每次配置都会读取，Android Studio 同步也不需要额外设置。四项缺一不可，`FBNG_RELEASE_STORE_FILE` 用绝对路径：

   ```bash
   FBNG_RELEASE_STORE_FILE=/Users/<you>/.config/fbng/fbng-release.p12
   FBNG_RELEASE_STORE_PASSWORD=<store password>
   FBNG_RELEASE_KEY_ALIAS=<key alias>
   FBNG_RELEASE_KEY_PASSWORD=<key password>
   ```

   值可以加引号，`export ` 前缀和 `#` 注释行也接受。真实环境变量优先于 `.env`，CI 就是用 Actions Secrets 注入的。

   `JAVA_HOME` 不写进 `.env`：`gradlew` 在 Gradle 启动前就要找到 JDK，而 `.env` 是 Gradle 启动之后才读的。Android Studio 用自己的 Gradle JDK，不需要这一步。

3. 确认 Debug 和 Release 的证书相同。终端构建需要 `JAVA_HOME`，验签还要 `PATH` 里有 Java。

   ```bash
   ./gradlew :app:assembleDebug :app:assembleRelease
   tools="${ANDROID_HOME:-$HOME/Library/Android/sdk}/build-tools/36.0.0"
   "$tools/apksigner" verify --print-certs app/build/outputs/apk/debug/app-debug.apk | grep SHA-256
   "$tools/apksigner" verify --print-certs app/build/outputs/apk/release/app-release.apk | grep SHA-256
   ```

   两行输出的证书 SHA-256 必须相同。缺少 `.env` 也缺少环境变量时，Gradle 在配置阶段报错，不会产出任何 APK。

已经有设备装着测试证书的 Debug 包时，换成发布证书的包要先卸载一次，这次会丢数据；之后 Debug 和 Release 可以双向往来，数据保留。

## 本地构建签名 Release APK

本地 Release 使用仓库外的发布密钥库。缺少发布签名材料时 Gradle 直接报错，不会生成未签名的包。

1. 按上文准备 SDK。Gradle daemon 使用 Java 25，本机可使用 Android Studio 自带的 Java 25。
2. 签名材料来自上一节准备的 `.env`：PKCS#12 发布密钥库放在仓库外，`FBNG_RELEASE_*` 四个值写在仓库根目录的 `.env` 里。
3. 在仓库根目录构建并验签。若 SDK 装在其他位置，按实际路径设置 `ANDROID_HOME`。产物为 `app/build/outputs/apk/release/app-release.apk`。

   ```bash
   (
     set -e
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

## GitHub Actions 与应用更新

[Assemble release APK](../.github/workflows/android-release.yml) 在向 `main` 推送、针对 `main` 的 PR 和手动触发时执行 `:app:testDebugUnitTest :app:assembleRelease`。Actions 从以下仓库 Secrets 取得签名材料，临时解码密钥库供 Gradle 使用，任务结束后删除临时文件。

- `FBNG_RELEASE_KEYSTORE_BASE64` 存放 PKCS#12 二进制文件的 Base64。
- `FBNG_RELEASE_STORE_PASSWORD`、`FBNG_RELEASE_KEY_ALIAS`、`FBNG_RELEASE_KEY_PASSWORD` 对应本地签名设置。

Gradle 依赖缓存按工具链和依赖配置复用。依赖解析失败时刷新依赖元数据后重试一次。

工作流验证 v1、v2、v3 签名和 APK 的 16 KB 对齐。成功后上传 `FBNG-<versionName>` artifact，保留 14 天。PR 到此结束。向 `main` 推送时，仅在 `versionName` 或 `versionCode` 变化后发布，手动运行也可发布。Release 标题为 `FBNG <versionName>`，tag 为 `v<versionName>`，附件为 `FBNG-<versionName>.apk`。0.x 版本标记为 prerelease。

Debug 和 Release 应用启动时都会匿名检查 GitHub Releases，也可通过「设置 → 关于 → 应用更新」手动检查、下载和安装。发布新版时递增 `versionCode` 并沿用同一张 Release 证书。发布说明中的 `版本 <versionName>（versionCode <versionCode>）` 与 tag、附件名和附件摘要共同供应用识别更新。应用核对下载文件的大小和 SHA-256，安装前再核对包名、版本和签名。首次安装更新按系统提示授权 FBNG 安装未知应用，系统要求确认时会打开安装界面。

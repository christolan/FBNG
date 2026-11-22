# 固定的 FBNeo 核心与资源

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
| 加载库 | LibretroDroid 0.13.2。版本见 [核心锁定配置](../core/fbneo.pin.properties)及[依赖版本表](../gradle/libs.versions.toml) |

这一个 `.so` 进 git，是因为 buildbot 的 `latest` 地址后来换成了另一份文件，原来的 zip 已经下不到。0.1.3 的 APK 里这份库和那个 zip 是同一次构建打进去的，DAT 也还是仓库里同一份。不要把它换成更新的 nightly，否则已经核对过的 `kovsh` / `pgm` 可能对不上。固定版本、摘要和标记见 [核心锁定配置](../core/fbneo.pin.properties)。构建会校验核心文件，配套 DAT 的摘要由单测校验。

`useLegacyPackaging` 是 false，安装清单里的 `extractNativeLibs` 也是 false，所以系统不会把核心复制到 `nativeLibraryDir`。应用如果在那个目录里找到已解压的文件，就直接用它；否则把 `base.apk!/lib/arm64-v8a/libfbneo_libretro_android.so` 交给 LibretroDroid。`dlopen` 从 API 23 起可以打开这种未压缩且按页对齐的 zip 条目。

打包前检查 FBNeo 核心和 LibretroDroid 的 arm64 库，要求 ELF `PT_LOAD` 至少按 16 KB 对齐。打包时原生库以未压缩的形式写入 APK，组装完成后用 `zipalign -P 16` 检查。

LibretroDroid 0.13.2 的 Java 接口没有暴露拟合比例。截图探针通过 `dlsym` 查找 C++ 单例和 `getAspectRatio()`。链接时保留对 `liblibretrodroid.so` 的显式依赖，供 Android 动态链接器解析库。符号不可用时，截图裁剪回退到 4:3。

## 资源与许可

配套 DAT、简体中文游戏名和中文名数据的许可位于 [FBNeo assets](../app/src/main/assets/fbneo/)。ROM 和 BIOS 由用户自行提供，仓库和 APK 都不包含这些 ZIP。

FBNG 供个人使用，不收费、不接受捐赠、不发布到应用商店。仓库保留 [FBNeo 许可全文](../third_party/FBNeo/license.txt)。LibretroDroid 使用 GPLv3。[LibretroDroid 项目](https://github.com/Swordfish90/LibretroDroid)

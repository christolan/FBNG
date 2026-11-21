# 架构与数据

项目只有一个 Android 模块 `:app`，应用包名为 `com.xiaojiwei.fbng`。Compose 管理壳层和暂停页，LibretroDroid 的 `GLRetroView` 负责模拟与画面渲染。

## 代码入口

| 入口 | 职责 |
| --- | --- |
| [MainActivity.kt](../app/src/main/java/com/xiaojiwei/fbng/MainActivity.kt)、[GamepadInput.kt](../app/src/main/java/com/xiaojiwei/fbng/ui/GamepadInput.kt) 与 [FbngApp.kt](../app/src/main/java/com/xiaojiwei/fbng/ui/FbngApp.kt) | 系统栏、主窗口和弹窗的手柄输入、加载 DAT 与标题、壳层、目录扫描和游戏会话的切换 |
| [LibraryScreen.kt](../app/src/main/java/com/xiaojiwei/fbng/ui/LibraryScreen.kt) 与 [LayoutPolicy.kt](../app/src/main/java/com/xiaojiwei/fbng/ui/LayoutPolicy.kt) | 导航、焦点、窗口宽度分级和铰链姿态 |
| [library/](../app/src/main/java/com/xiaojiwei/fbng/library/) | 目录授权与扫描、归档索引、依赖解析、启动准备、检索和最近游玩 |
| [dat/](../app/src/main/java/com/xiaojiwei/fbng/dat/) 与 [importing/](../app/src/main/java/com/xiaojiwei/fbng/importing/) | 街机 DAT 解析、ZIP 成员摘要、套件识别和完整校验 |
| [GameScreen.kt](../app/src/main/java/com/xiaojiwei/fbng/ui/GameScreen.kt) 与 [game/](../app/src/main/java/com/xiaojiwei/fbng/game/) | 核心加载、游戏会话、按键转发、暂停和存读档 |
| [cpp/](../app/src/main/cpp/) | `libfbng_aspect.so` 读取核心报告的画面比例 |
| [artwork/](../app/src/main/java/com/xiaojiwei/fbng/artwork/) | 封面索引、联网任务和离线文件 |
| [update/](../app/src/main/java/com/xiaojiwei/fbng/update/) | Release 检查与下载、APK 校验和系统安装 |

## 从导入到开始游戏

1. `FolderLibrary` 持久化 Android 文件选择器授予的目录读取权限，递归枚举 ZIP。应用启动、添加目录或手动重新扫描时更新目录。
2. `LibraryIndex` 以文件名、大小和修改时间复用归档识别结果。首次加载可先显示可用缓存。后台扫描期间保留当前列表，扫描完成后一次性更新套件列表和目录统计。
3. `LibraryScanner` 与 `RomSetMatcher` 按固定 DAT 和 ZIP 成员信息识别套件。规范的套件文件名用于区分 ROM 内容相同的候选。依赖可来自多个目录或旧版导入，单个 ZIP 已含所需文件时也可启动。
4. 在游戏页选择固定槽位并按「开始游戏」时，`FolderLibrary.prepare` 将所需归档放入临时播放目录，完整读取 ZIP 并与扫描时的成员信息核对。失败会清理这次准备的目录。
5. `GameScreen` 创建会话并加载 FBNeo。`RunningGame` 管理生命周期与自动存档，`GamepadBridge` 在运行时把手柄交给核心。开局前和暂停时复用 `GameProgressPage`，暂停时覆盖在同一个 `GLRetroView` 上方。开局选定的槽位随启动传给 `RunningGame` 并保持固定，自动保存更新同一槽位。`GameProgressStore` 管理 3 个槽位、独立快照、旧档迁移与分页读取，`SaveStateStore` 负责底层原子存档包。暂停时应用快照先加载并验证，再原子写入槽位；失败时恢复原运行状态，后台/销毁回调等待操作完成，不与它并发覆盖。成功启动的套件加入最近游玩。

套件名是游戏、收藏、最近、存档和封面的稳定标识。中文名用于显示和检索。DAT 的 `romof` 描述 ROM 依赖，`cloneof` 用于封面索引沿父游戏查找图片。

壳层有基于 `FoldingFeature` 的铰链兼容分支。设备适配与真机验收以 RP6 为准。

## 本地数据

下表中的 `files/` 是应用私有文件目录，`cache/` 是 Android 的应用缓存目录。目录与偏好定义见 [AppStorage.kt](../app/src/main/java/com/xiaojiwei/fbng/library/AppStorage.kt) 和上面的代码入口。

| 路径或偏好 | 内容 |
| --- | --- |
| `files/roms/`、`files/system/fbneo/`、`files/source-names/` | 兼容旧版导入的游戏、BIOS 和原始文件名 |
| `files/library-index` | 本地归档扫描索引 |
| `files/cache/play/` | 每次启动游戏所需的临时归档 |
| `files/saves/` | 核心使用的保存目录 |
| `files/states/<套件名>/progress/` | 3 个固定槽位及对应截图 |
| `files/states/<套件名>/snapshots/` | 独立历史快照，分页读取元数据与截图 |
| `files/states/<套件名>/` | 旧档迁移备份、`progress-v1.migrated` 标记及 `selected-slot`；兼容性见[存档与读档](usage.md#存档与读档) |
| `files/artwork/downloaded/` | 已下载的游戏封面 |
| `library-folders`、`library-favorites`、`library-recent`、`cover-attempts` | 目录、收藏、最近游玩和重复缺图探测的本地偏好 |
| `appearance` | 全局圆角大小的本地偏好 |
| `cache/fbng-update.apk` | 应用更新下载缓存 |

## 联网流程

- 封面由 `CoverWorker` 交给 WorkManager，在联网时处理收藏或成功玩过的套件。相同图片路径共享下载和磁盘文件。已下载图片由 Coil 从本地显示。内置索引只保存路径，生成方法见[游戏封面](usage.md#游戏封面)。
- Release 应用匿名查询 GitHub Releases。它通过发布说明、tag 和附件元数据选出更高的 `versionCode`，下载后检查大小和 SHA-256，交给系统安装前还会核对包名、版本和签名。发版端的约定见[构建、测试与发布](build-release.md#github-actions-与应用更新)。

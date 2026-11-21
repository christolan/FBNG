# FBNG 游戏封面图源调研

调研日期：2026-11-13。范围：当前自用 Android / Jetpack Compose / 固定 FBNeo 街机 DAT；本次仅调研，没有改动应用代码。

当前封面的下载与匹配方式见 [使用说明](usage.md#游戏封面)。

## 数据验证

输入是仓库自带 DAT，SHA-256：`9484cce16c1f37f5de5fd6d92a300a57d037c68b77744686dc0c3cff01c169d2`。排除 14 个 BIOS 后共 **8,500 个游戏套件**，含地区版、克隆和修改版；这不是独立游戏数量，也不是用户实际安装的游戏库。

通过 [GitHub Tree API](https://api.github.com/repos/libretro-thumbnails/FBNeo_-_Arcade_Games/git/trees/master?recursive=1) 读取完整索引，返回 `truncated=false`，tree SHA 为 `701bce96b276cabee65970b57d8aa00926f692bc`。逐个以 DAT `description` 匹配 PNG 文件名，把 `&*/:`、尖括号、问号、反斜杠、竖线和双引号替换为 `_`，不做模糊搜索；回退仅沿 XML `cloneof`，排除 BIOS 并检测环。

| 类型 | 图库 PNG 数 | 直接匹配 DAT | 加入 cloneof 回退 | 图库该类文件总大小 |
| --- | ---: | ---: | ---: | ---: |
| 真实封面 Named_Boxarts | 6,482 | 5,081 / 59.78% | 6,253 / 73.56% | 3.255 GB |
| 标题画面 Named_Titles | 12,281 | 8,436 / 99.25% | 8,484 / 99.81% | 0.688 GB |
| 游戏截图 Named_Snaps | 12,268 | 8,434 / 99.22% | 8,484 / 99.81% | 0.968 GB |

大小是 API 中 blob size 的合计，十进制 GB，不是压缩包或 Android 内存占用。覆盖率是**文件名索引命中率**，不是逐张图像内容核验，也不保证所有文件都能下载或版本美术完全一致。父版本回退只表示共享游戏美术，不能改变游戏标题、版本和启动目标。

| 套件 | 当前标题对应真实封面 | 标题图 / 截图 |
| --- | --- | --- |
| kovsh（三国战纪：风云再起） | 未直接命中 | 均命中 |
| kov（三国战纪） | 未直接命中 | 均命中 |
| kof98（拳皇 98） | 命中 | 均命中 |
| mslug / mslug3（合金弹头） | 命中 | 均命中 |
| sf2（街霸 II） | 命中 | 均命中 |
| ddtod（龙与地下城：毁灭之塔） | 未直接命中 | 均命中 |

`kovsh` 的 `romof` 是 `pgm`，`kof98` 的 `romof` 是 `neogeo`；两者都是 BIOS，不能作为封面父游戏。当前解析器只读 `romof`，需要另读 DAT 已有的 `cloneof`，保留两种关系各自的用途。

额外做了四次图片 GET，均返回 HTTP 200、`image/png` 且 PNG 头包含有效尺寸：GitHub raw 上的 `kovsh` 标题图（512×384）、`kof98` 封面（512×640）、`ddonpach` 截图（240×320），以及 Libretro 缩略图服务器上的同一张 `kovsh` 标题图。仅用于核实格式和路径，没有加入应用或仓库。完整计数与探测记录见 [game-cover-evidence.json](game-cover-evidence.json)。这些是云环境连通性结果，未测手机或中国大陆网络表现。

## 方案比较

| 方案 | 适合当前项目的程度 | 主要成本与用途 |
| --- | --- | --- |
| Libretro FBNeo 图库 | 首选自动来源 | 有对应 FBNeo 分类；需处理英文标题、历史更名和缺图；静态下载无需账号 |
| 本地图片 | 离线来源 | 可手工修正匹配，需要逐款准备图片 |
| ScreenScraper | 后续扩展候选 | 官方要求申请开发者凭据，有并发与请求配额；适合以后同时需要简介、地区媒体等元数据时评估 |
| SteamGridDB | 后续人工精选候选 | 提供 API key，适合补充封面和 Banner；需要维护游戏 ID 映射，本次未测 FBNeo 覆盖率 |
| progetto-SNAPS / MAME 素材包 | 离线素材兼容候选 | 有截图和标题图资源；需要处理与 FBNeo 的套件命名差异，不作为当前真实封面来源 |

ScreenScraper 的 ROM 查询涉及文件校验值及大小。当前 DAT 里的 CRC 是 ZIP 内成员的 CRC，不能直接当成整个归档的标识传入；接入需要明确归档与拆分套件的匹配策略。其应用接入条件也须先向官方核实。该方案目前会增加本项目不需要的账号和元数据流程。[官方 API 文档](https://www.screenscraper.fr/webapi2.php)

## 索引与下载技术记录

### 匹配和索引

内部主键保持 `(system=fbneo-arcade, setName)`；中文标题只用于显示。构建期或维护脚本读取固定 DAT 和固定上游版本的文件树，产出小型 `artwork-index.json`。运行时读取内置索引，不逐款调用 GitHub API，也不靠反复 404 来探测缺图。

索引记录 `setName`、媒体类型、上游相对路径、来源版本和实际匹配的套件。优先级如下：

1. 用户为当前套件指定的本地封面。
2. 当前套件的精确封面，包括人工审核后的历史标题别名。
3. 沿 `cloneof` 找到的父游戏封面。

人工别名必须明确到套件和图片，不自动删括号或对中文名称模糊搜索。`kov` / `kovsh` 需要单独确认历史命名的封面内容后补映射，本次没有把名称相近的图片直接判定为正确封面。

远端路径按上游规范替换非法字符，再对 URL 路径段做 UTF-8 百分号编码；本地文件名使用套件名或资源 ID，避免中文显示名、斜线和地区字符串进入存储路径。

### 下载、持久化与本地覆盖

默认静态来源可用 `raw.githubusercontent.com/libretro-thumbnails/FBNeo_-_Arcade_Games/<固定 commit>/Named_Boxarts/...png`；Libretro 官方缩略图服务器作为可选来源，切换时不假设两者内容和更新时间一致。[Libretro 文档](https://docs.libretro.com/guides/roms-playlists-thumbnails/)描述了官方服务器下载方式。

明确下载的图片存入应用私有持久目录，例如 `files/artwork/downloaded/`；用户导入图片放 `files/artwork/custom/`。自动图片可单独清理，自定义图片不被下载覆盖。两者不只依赖可能被清除或淘汰的图片缓存，否则不能承诺离线可用。临时文件完成解码验证后原子替换，避免半张图片进入有效目录。

本地导入使用系统文件选择器并复制到私有目录，首期支持 PNG / JPEG / WebP 单图；批量图片目录和包导入可后做。下载设置连接/读取超时、有限并发、文件大小和像素数上限；404 记录缺图，429 按 Retry-After 等待，网络故障保留原图且不会把所有候选都标成永久缺失。以上是实现建议，具体预算需在设备上确定。

## 一手资料

- [FBNeo 专用图源及 README](https://github.com/libretro-thumbnails/FBNeo_-_Arcade_Games)：三个媒体目录、图片分辨率处理与上游同步说明。
- [Libretro 缩略图库说明](https://github.com/libretro-thumbnails/libretro-thumbnails)：媒体语义、文件名字符替换规则和服务器更新说明。
- [ScreenScraper Web API](https://www.screenscraper.fr/webapi2.php)：开发者接入、查询参数与配额。
- [SteamGridDB 官方客户端](https://github.com/SteamGridDB/node-steamgriddb)：API key 与接口用法。
- [progetto-SNAPS 官方站](https://www.progettosnaps.net/)：MAME 媒体资源。

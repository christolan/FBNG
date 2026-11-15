# FBNG 项目文档

FBNG（FBNeo Next Generation）是自用 Android 街机前端。项目优先支持横屏与手柄操作，主设备是 OPPO Find N6。应用通过 LibretroDroid 运行固定版本的 FBNeo，并用配套街机 DAT 识别游戏、父套件和 BIOS。应用目前提供本地游戏库、收藏、最近游玩、封面、暂停和存读档。同步页显示「即将推出」，现阶段仅支持 FBNeo 街机核心。

## 现行说明

| 文档 | 内容 |
| --- | --- |
| [使用说明](usage.md) | 导入目录、浏览与筛选、游戏封面、暂停、存读档和手柄操作 |
| [架构与数据](architecture.md) | 代码入口、扫描与启动流程、存储位置、封面下载和应用更新 |
| [Islands 界面与交互规则](ui-guidelines.md) | 设计 token、页面布局、手柄焦点、折叠适配和 UI 改动要求 |
| [掌机手柄优化方案](handheld-controls.md) | Switch 与 Steam Deck 调研、FBNG 交互契约、实施范围和验收矩阵 |
| [固定的 FBNeo 核心与资源](fbneo-core.md) | 核心与 DAT 的来源、版本、校验、加载方式和许可 |
| [构建、测试与发布](build-release.md) | SDK 与 JDK、Debug、签名 Release、验签、GitHub Actions 和应用更新约定 |

## 历史记录

| 文档 | 内容 |
| --- | --- |
| [FBNG 0.8.0 Islands 界面](islands-ui.md) | 0.8.0 的页面设计与存档槽位改版记录 |
| [封面支持方案调研](game-cover-research.md) | 2026-11-13 的图源比较、命中率与当时的设计提案，已上线行为见[使用说明](usage.md#游戏封面) |
| [封面调研数据](game-cover-evidence.json) | 当时的图库索引、覆盖率与图片请求探测数据 |

依赖、设备支持和应用版本以 [Gradle 配置](../app/build.gradle.kts)及[依赖版本表](../gradle/libs.versions.toml)为准。发布触发条件以 [GitHub Actions 工作流](../.github/workflows/android-release.yml)为准。功能或约束有变化时，更新对应的说明。

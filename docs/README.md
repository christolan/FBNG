# FBNG 项目文档

FBNG（FBNeo Next Generation）是自用 Android 街机前端。RP6 掌机是项目唯一的设备适配和真机验收目标。应用从启动起固定横屏，以内置手柄操作为主。应用通过 LibretroDroid 运行固定版本的 FBNeo，并用配套街机 DAT 识别游戏、父套件和 BIOS。应用目前提供本地游戏库、收藏、最近游玩、封面、暂停和存读档。同步页说明存档当前只保存在本机，现阶段仅支持 FBNeo 街机核心。

## 现行说明

| 文档 | 内容 |
| --- | --- |
| [使用说明](usage.md) | 导入目录、浏览与筛选、游戏封面、暂停、存读档和手柄操作 |
| [架构与数据](architecture.md) | 代码入口、扫描与启动流程、存储位置、封面下载和应用更新 |
| [固定的 FBNeo 核心与资源](fbneo-core.md) | 核心与 DAT 的来源、版本、校验、加载方式和许可 |
| [构建、测试与发布](build-release.md) | SDK 与 JDK、Debug、签名 Release、验签、GitHub Actions 和应用更新约定 |
| [设计原则](design-principle.md) | UI 与 UX 原则，必须严格遵守 |

## 封面图源调研

| 文档 | 内容 |
| --- | --- |
| [封面图源调研](game-cover-research.md) | 2026-11-13 的图源比较与 DAT 匹配命中率，现有功能见[使用说明](usage.md#游戏封面) |
| [封面调研数据](game-cover-evidence.json) | 当时的图库索引、覆盖率与图片请求探测数据 |

依赖、设备支持和应用版本以 [Gradle 配置](../app/build.gradle.kts)及[依赖版本表](../gradle/libs.versions.toml)为准。发布触发条件以 [GitHub Actions 工作流](../.github/workflows/android-release.yml)为准。功能或约束有变化时，更新对应的说明。

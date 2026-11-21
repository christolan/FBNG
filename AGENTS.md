# AGENTS.md — FBNG 协作说明

明确用户意图，区分问题与任务。用户提出问题时只回答问题，按用户明确提出的任务修改仓库。

RP6 掌机是项目唯一的设备适配和真机验收目标，以内置手柄操作为主。APP 从启动起固定横屏，游戏库、详情、暂停、设置和弹窗都遵守此规则。真机验收以 RP6 横屏的可用窗口和系统安全区为准。

## UI 设计

界面实现和交互行为以代码为准。项目中不保留 UI 设计截图、HTML 原型或其他本地设计稿。最新设计稿保存在 Notion，按需查看 [FBNG 设计稿](https://app.notion.com/p/FBNG-41fc2f73f77280e79a0fc0c873d0dc04?v=41fc2f73f77280cb9a6b000cfa143599)。

## 项目文档索引

[docs/README.md](docs/README.md) 是项目上下文的总索引，包含现行说明和调研数据。按任务读取相关文档。

| 任务 | 文档 |
| --- | --- |
| 了解产品范围与用户操作 | [项目概览](docs/README.md)、[使用说明](docs/usage.md) |
| 查找代码入口、数据流和存储兼容性 | [架构与数据](docs/architecture.md) |
| 修改核心、DAT 或应用打包中的原生库 | [固定的 FBNeo 核心与资源](docs/fbneo-core.md) |
| 本地构建、测试、签名、发布或应用更新 | [构建、测试与发布](docs/build-release.md) |

仓库和 APK 禁止包含 ROM 或 BIOS。Release 密钥与本地签名配置放在仓库外。

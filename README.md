# FBNG

FBNeo Next Generation 是自用 Android 街机前端。它只适配横屏，以手柄操作为主，界面采用 Jetpack Compose 和 Islands 卡片布局。APP 从启动起固定横屏，不随设备转动切换为竖屏；横屏内仍适配窗口大小、折叠铰链与安全区变化。应用通过 LibretroDroid 运行固定版本的 FinalBurn Neo，并用配套街机 DAT 识别游戏、父套件和 BIOS。

首次使用时，在「设置 → 导入 → 添加文件夹」选择存放 ZIP 的目录。仓库和 APK 都不包含游戏 ROM 或 BIOS，请使用自己持有的 ZIP。应用供个人使用。

## 文档

- [使用说明](docs/usage.md)
- [构建、测试与发布](docs/build-release.md)
- [固定的 FBNeo 核心与资源](docs/fbneo-core.md)
- [项目文档总索引](docs/README.md)
- [横屏游戏页：槽位与快照设计](docs/game-page-slots-snapshots.md)

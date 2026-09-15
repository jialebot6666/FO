# FO 版本归档

每个修复/功能版本交付前，把构建产物（`fo/build/libs/fo-<mod版本>.jar`，当前
`fo-V2.3.jar`）复制为 `fo-V<版本号>-<commit短哈希>.jar` 并提交到本目录，
保证版本留存。mod 版本号在 `fo/gradle/libs.versions.toml` 的 `mod-version`，升版时同步改。

## 版本清单

| 文件 | 版本 | 说明 |
|---|---|---|
| fo-5PrWPQNWZm.jar | 首版 | 初版 |
| fo-YoNbGZGn7b.jar | 汉化 | 前端汉化 |
| fo-csbnufGjfX.jar | 汉化+前缀 | 汉化 + FO 前缀 |
| fo-f295ca3.jar | V0.9 | ElytraCollector 三 bug 修复 |
| fo-9cfe2e0.jar | V1.0 | 默认物资金胡萝卜 |
| fo-V1.1.jar | V1.1 | 存鞘翅新流程 + 方向过滤 + StorageRecovery |
| fo-V1.2.jar | V1.2 | 默认物资加不死图腾 |
| fo-V1.3.jar | V1.3 | 自动拿空盒开关 |
| fo-V1.4.jar | V1.4 | 默认白名单 26 项 |
| fo-V1.4a.jar | V1.4a | 白名单补齐 61 项 |
| fo-V1.5.jar | V1.5 | 存鞘翅绝不用补给盒 + 无盒自动下线 |
| fo-V2.3-57178ab.jar | V2.3 | 低Y退出真正退出游戏 |

> 备注：8430096（AutoTrash 白名单修复）的云端交付链接已失效，
> 本地无 jar 副本；其源码仍完整保留在 Git 历史中（commit 8430096），
> 如需可随时重建。

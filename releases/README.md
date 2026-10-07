# FO 版本归档

每个修复/功能版本交付前，把构建产物（`fo/build/libs/FO-<mod版本>.jar`，当前
`FO-V2.3.jar`）复制为 `FO-V<版本号>-<commit短哈希>.jar` 并提交到本目录，
保证版本留存。mod 版本号在 `fo/gradle/libs.versions.toml` 的 `mod-version`，升版时同步改。

## 命名规范

- 产物文件名统一大写：`FO-V<版本>.jar` / `FO-V<版本>-<commit短哈希>.jar`。
- 产物名与 mod 版本号统一到版本体系：产物 = `FO-<mod版本>.jar`，升版同步改 `mod-version`。

## 版本清单

| 文件 | 版本 | 说明 |
|---|---|---|
| FO-5PrWPQNWZm.jar | 首版 | 初版 |
| FO-YoNbGZGn7b.jar | 汉化 | 前端汉化 |
| FO-csbnufGjfX.jar | 汉化+前缀 | 汉化 + FO 前缀 |
| FO-f295ca3.jar | V0.9 | ElytraCollector 三 bug 修复 |
| FO-9cfe2e0.jar | V1.0 | 默认物资金胡萝卜 |
| FO-V1.1.jar | V1.1 | 存鞘翅新流程 + 方向过滤 + StorageRecovery |
| FO-V1.2.jar | V1.2 | 默认物资加不死图腾 |
| FO-V1.3.jar | V1.3 | 自动拿空盒开关 |
| FO-V1.4.jar | V1.4 | 默认白名单 26 项 |
| FO-V1.4a.jar | V1.4a | 白名单补齐 61 项 |
| FO-V1.5.jar | V1.5 | 存鞘翅绝不用补给盒 + 无盒自动下线 |
| FO-V2.3-09a0ad2.jar | V2.3 | 低Y退出真正退出游戏 |
| FO-V4.15-c8142da.jar | V4.15 | 存沙路径重构：goToStore 选最近盒（只在 INIT_SCAN 同步过名字的盒中选，排除满盒/不可开盒）+ tickOpenStore 开盒超时原地重试不回 MINING |
| FO-V5.0-9e3384c.jar | V5.0 | 移植 IceHack 鞘翅套件：FO 自动鞘翅飞行 + FO 自动开宝库 + FO 实时平均速度（含 HUD FO 实时速度）；Baritone 桥接改纯反射；前端全汉化 |
| FO-V5.1-5d56b19.jar | V5.1 | 修复「挖掘异常？取消挖掘」误报：等待计数按用途拆开 + 方块还在时绝不打断 Baritone 挖潜影盒（末影箱同形问题一并修） |
| FO-V5.2-c3577ce.jar | V5.2 | 挖回潜影盒/末影箱改用 FO 自己的挖掘（默认关掉 Baritone 挖掘）+ 背包满自动丢垃圾腾位 + 槽位差捡盒判定 + 火球时不判挖掘超时 |

> 备注：8430096（AutoTrash 白名单修复）的云端交付链接已失效，
> 本地无 jar 副本；其源码仍完整保留在 Git 历史中（commit 8430096），
> 如需可随时重建。

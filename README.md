# FO — Meteor Client 生存辅助插件

Meteor Client（Fabric）的全自动生存辅助插件，MC **1.21.11**，模块统一带 `FO ` 前缀、界面全汉化。

## 模块

| 模块 | 说明 |
|---|---|
| FO 鞘翅采集 | 自动搜刮鞘翅相关资源 |
| FO 自动种树 | 自动补种树木 |
| FO 自动扔垃圾 | 背包清理，支持附魔书按等级过滤 |
| FO 自动LogPlus | 自动登出登录 |
| FO 自动挖沙 | 自动沙挖取 |
| FO 自动挖矿 | 自动采矿 |
| FO 杀戮光环 | 战斗辅助，含「切换回来」对齐官方 swap-back |
| FO 古城战利品搜索 | 附魔金苹果 / 迅捷潜行3 的古城箱子预测、战利品掷骰、Xaero 路径点联动、自动偷取（**半自动**，需手动开箱） |

模块注册入口：`src/main/java/com/fo/addon/AddonTemplate.java`。
开发规范与历史决策记录见 [`AGENTS.md`](AGENTS.md)。

## 构建

```bash
./gradlew build
```

产物在 `build/libs/`，命名规则 `FO-<mod版本>-<MC版本>.jar`（如 `FO-6.32-1.21.11.jar`）。

**需要 JDK 21**（MC 1.21.11 要求）。版本号、JDK 版本的唯一权威来源是
[`gradle/libs.versions.toml`](gradle/libs.versions.toml)，改那里即可。

单独跑测试：

```bash
./gradlew test
```

## 安装

1. 从 [Releases](https://github.com/wxh1024/FO/releases) 下载对应版本的 jar；
2. 放进 `.minecraft/mods/`；
3. 需同时安装 [Meteor Client](https://github.com/MeteorDevelopment/meteor-client) 与 [Baritone](https://github.com/cabaletta/baritone)。

## 仓库结构与 CI

仓库只有一个 CI 工作流（`.github/workflows/ci.yml`）：push / PR 时自动编译打包，
产物作为 Actions artifact 留存 30 天。单测本地跑，发版走手动命令。详见 [`docs/ci.md`](docs/ci.md)。

Windows 本地构建踩坑见 [`docs/windows-build.md`](docs/windows-build.md)。

版本归档清单见 [`releases/README.md`](releases/README.md)。

## 构建产物不入库

`*.jar` 不提交到 git（`gradle-wrapper.jar` 与 `libs/` 的源码级依赖除外），
产物一律发布到 GitHub Releases。历史遗留的 110 个 jar 已从 git 历史中剥离。
原因与白名单说明见 [`docs/ci.md`](docs/ci.md)。

## License

见 [LICENSE](LICENSE)。

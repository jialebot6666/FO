# AGENTS.md

> 本文件是**指针**，不是文档正文。FO 的开发规范已全部收敛到
> [`.github/skills/fo-development/SKILL.md`](.github/skills/fo-development/SKILL.md)
> 及其 `references/`（架构 / 构建发版 / Windows 构建）。
> 改任何规范前先读那个 skill，改完同步更新它，不要在本文件里另起一套。

## 一句话背景

FO — Minecraft **Meteor Client (Fabric) 生存辅助全自动插件**（MC 1.21.11，
模块统一带 `FO ` 前缀、界面全汉化）。当前版本 `6.32`，产物 `FO-6.32-1.21.11.jar`。

- 代码根：**仓库根**，包 `com.fo.addon`；8 个模块全部注册在 `AddonTemplate.onInitialize()`、
  统一放进 `Category("FO")`：鞘翅采集 / 自动种树 / 自动扔垃圾 / 自动LogPlus /
  自动挖沙 / 自动挖矿 / 杀戮光环 / 古城战利品搜索。
- 版本号与 JDK 版本的唯一权威来源：`gradle/libs.versions.toml`。
- `ref-src/` 是本机外部参考源码目录，被 `.gitignore` 排除、不入库。

## 硬性规范（摘要，全文见 skill）

1. 每次改动完成后创建对应的 Git commit。
2. 每次改动后编写/更新测试，交付前 `./gradlew build` 全绿。
3. 前端全中文（枚举必须覆写 `toString()` 返回中文）。
4. 务实说话：没从代码里看到的、没验证过的不说。
5. 先讨论后动手：方向性决策先给方案、由用户拍板再改代码。
6. 发版走 GitHub Release（jar 唯一分发渠道，`releases/*.jar` 不入库）。
   命名：版本号不带 `V`；产物 `FO-<mod版本>-<MC版本>.jar`；tag = `<mod版本>`；
   Release 标题 `FO <mod版本>-<MC版本>`。
7. 仓库拉取一律浅克隆（`git clone --depth=1`，走 `gh-proxy.com` 代理前缀）。

## 详细执行流程

涉及 FO 模块功能开发、Bug 修复、参数调整、测试与交付时，
**必须先加载 `.github/skills/fo-development/SKILL.md`**，严格按其工作流执行。
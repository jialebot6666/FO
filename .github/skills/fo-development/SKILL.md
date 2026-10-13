---
name: fo-development
description: FO 插件（Minecraft Meteor Client Fabric 生存辅助，MC 1.21.11）的开发、修复、测试与交付规范。涉及 FO 模块功能开发、Bug 修复、参数调整、默认值修改、单元测试、Windows 本地构建、CI 与 jar 交付时必须使用本 skill。
license: GPL-3.0
---

# FO 插件开发规范

> 本 skill 是 FO 的**唯一文档入口**。原 `AGENTS.md` / `docs/ci.md` / `docs/windows-build.md`
> 的有效内容已收敛到本文件与 `references/`，冲突时以本 skill 为准。

## 参考文档（按需读，不要一次全读）

| 文件 | 什么时候读 |
|---|---|
| [references/architecture.md](references/architecture.md) | 改古城战利品搜索模块、加新模块、找纯逻辑放哪、中间件/线程约定 |
| [references/build-and-release.md](references/build-and-release.md) | 构建、打tag、发 GitHub Release、CI、二进制入库、命名规范 |
| [references/windows-build.md](references/windows-build.md) | **在 Windows 上从零构建**（JDK / Gradle 镜像 / 依赖镜像 / 无 git 时走 REST API 发版） |

## 项目背景

- 定位：生存辅助全自动 Meteor Client 插件（Fabric，MC 1.21.11），模块名统一带 `FO ` 前缀
  （用户环境装有大量其他中文 addon，防重名冲突），前端全汉化。
- 代码根：**仓库根**（包 `com.fo.addon`）；远端仓库见 [build-and-release.md](references/build-and-release.md)
  （本地工作区路径按机器而定，不要写死）。
- Gradle 工程在**仓库根**，没有 `fo/` 子目录（2026-10-13 起）。
- `ref-src/` 是本机外部参考源码目录，被 `.gitignore` 排除、不入库。
- 现有模块共 **8 个**，全部注册在 `AddonTemplate.onInitialize()`、统一放进 `Category("FO")`：

| # | 模块 | 类 |
|---|---|---|
| 1 | FO 鞘翅采集 | `modules/ElytraCollector` |
| 2 | FO 自动种树 | `modules/AutoTree` |
| 3 | FO 自动扔垃圾 | `modules/AutoTrash` |
| 4 | FO 自动LogPlus | `modules/AutoLog` |
| 5 | FO 自动挖沙 | `modules/AutoMineSand` |
| 6 | FO 自动挖矿 | `modules/AutoMining` |
| 7 | FO 杀戮光环 | `modules/FOKillAura` |
| 8 | FO 古城战利品搜索 | `ancient/modules/FOAncientCitySearch` |

- 6.2 已删除 6.0 的 icehack-2 鞘翅套件（自动鞘翅飞行 / 自动不祥宝库 / 实时平均速度 三模块 + HUD），
  **当前没有 elytra 套件**，`ElytraUiTextTest` 等相关守护测试也已随它删除。
- 纯逻辑放 `src/main/java/com/fo/addon/utils/`（不依赖 MC 运行时，可单元测试）；
  古城模块的纯逻辑放 `ancient/core/`；测试放 `src/test/java/com/fo/addon/`。

## 铁律（不可协商）

1. **每次改动完成后，必须立即创建一个对应的 Git commit**（信息包含模块名 + 改动摘要），以便追踪和回滚。
2. **每次改动后，必须编写或更新相关单元测试**，并在交付前运行 `./gradlew build`，
   确保所有测试和验证全部通过（0 failures / 0 errors）。
3. **前端全中文**：所有用户可见的界面文本（模块名、设置名、设置描述、下拉框选项、开关、按钮、
   提示消息、通知、聊天输出）一律中文；不得出现英文 UI 文本（代码标识符、物品 ID、内部类名除外）。
   注意 Meteor 的 `EnumSetting`、模块列表、HUD、聊天输出**都走枚举的 `toString()`**，
   会上界面的枚举必须覆写 `toString()` 返回中文（`.name()` 是英文常量名，界面上不能用）。
4. **务实说话**：没从代码里实际看到的、没验证过的不说。不根据猜测推断功能，不编结论。
   不确定就写「不知道」或「需要看代码确认」。
5. **先讨论后动手**：涉及功能方案、模块设计、功能取舍、行为变化、默认值调整等方向性决策，
   必须先给出方案供用户讨论、由用户拍板后再改代码；未经确认不得直接实现。
6. **命名规范**：唯一权威来源是 [build-and-release.md](references/build-and-release.md) 的
   「命名规范」一节。要点：版本号不带 `V`；产物名 `FO-<mod版本>-<MC版本>.jar`；
   tag = `<mod版本>`；Release 标题 `FO <mod版本>-<MC版本>`；`fabric.mod.json` 的 version 用纯 mod 版本。
7. **发版走 GitHub Release**（jar 唯一分发渠道），`releases/*.jar` 不入库（`.gitignore` 已拦截）。
   纯文档改动**不要**发版、不要建 Release。

## 工作流

1. **定位**：找到对应模块文件（普通模块 `src/main/java/com/fo/addon/modules/`，
   古城模块 `src/main/java/com/fo/addon/ancient/modules/`）。
2. **改代码**：模块内做交互/时序逻辑；可提取的判定逻辑（决策表、列表过滤、方向过滤等）
   放 `utils/`（如 `FacingLogic`、`TrashLogic`、`CraftingSlotMath`、`SwapBackTracker`）
   或 `ancient/core/`（如 `AncientLootEvaluator`、`AncientChestStore`、`AncientStealStats`）便于单测。
3. **补测试**：为行为变更写/更新断言，判定类逻辑必须覆盖新行为。
4. **验证**：`./gradlew build` 全绿。Windows 上按 [references/windows-build.md](references/windows-build.md)
   用本地 `gradle.bat` + `-Dorg.gradle.java.home=<JDK>`，**不要用 `gradlew.bat`**。
5. **提交**：`git add` 相关文件 → commit。**提交前先按 blob SHA-1 与远端 tree 比对**，
   避免用旧副本覆盖别人的新提交（本机 `github.com` 的 git 传输常超时，
   推送与发版走 `api.github.com` REST API，见 [references/windows-build.md](references/windows-build.md) 第 6 节
   与 `scripts/gh-deliver.ps1`）。
6. **发版**：改 `gradle/libs.versions.toml` 的 `mod-version` → commit → tag → GitHub Release →
   在 `releases/README.md` 补一行（版本 | commit | 说明）。详见 [build-and-release.md](references/build-and-release.md)。
7. **交付**：`present_files` 把 `build/libs/FO-<mod版本>-<MC版本>.jar` 交给用户，并说明改动点与测试建议。

## 仓库拉取

新设备/新环境一律**浅克隆**（github.com 直连慢且常超时，历史含大量 jar 二进制）：

```bash
git clone --depth=1 https://gh-proxy.com/https://github.com/<owner>/FO.git
```

日常开发增量 `git pull` 即可；确需完整历史时才 `git fetch --unshallow`。**不要全量 clone。**

## 用户偏好（历史沉淀）

- 默认值/物资类需求：用户喜欢「直接从代码层改」，不要只给配置教程。
- 默认物资/名单：白名单模式，扔垃圾默认白名单 **62 项**
  （`utils/TrashDefaults.java` 的 `DEFAULT_WHITELIST_IDS`，4.22 加了 `minecraft:sand` 由 61 → 62，
  由 `TrashDefaultsTest` 断言锁定）；补给物资列表格式 `物品ID;最低值;目标库存`
  （如 `minecraft:golden_carrot;8;32`）。
- 模块开关/设置：新设置默认自动开启（除非用户明确要求默认关闭）。
- 交付时提醒：改默认值后需重置模块设置，旧配置会覆盖默认值。
- 测试环境：用户常用其他 mod，FO 的「异常」要先确认不是别的 mod 导致。
- UI 控件：**优先挑框架已经在用的那种**（分组 = `WSection`、行内块 = `WTable`、
  只读值 = `theme.textBox` + 拒绝输入的 `CharFilter`），不要自绘或塞大号字体的控件——
  看渲染效果的人只有用户，一次猜错就得多发一版。用框架的「删行」类API 前先读源码确认游标副作用
  （`WTable.removeRow(i)` 会把行游标 `rowI` 减一，删完必须补一次 `table.row()`）。
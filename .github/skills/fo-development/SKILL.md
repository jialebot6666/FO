---
name: fo-development
description: FO 插件（Minecraft Meteor Client Fabric 生存辅助，MC 1.21.11）的开发、修复、测试与交付规范。涉及 FO 模块功能开发、Bug 修复、参数调整、默认值修改、单元测试与 jar 交付时必须使用本 skill。
license: CC0-1.0
---

# FO 插件开发规范

## 项目背景

- 定位：生存辅助全自动 Meteor Client 插件（Fabric，MC 1.21.11），模块名统一带 `FO ` 前缀（用户环境装有大量其他中文 addon，防重名冲突），前端全汉化。
- 代码根：`仓库根`，包 `com.fo.addon`；远端仓库 `https://github.com/jialebot6666/FO`（本地工作区路径按机器而定，不要写死）。
- 现有模块：共 10 个模块 + 1 个 HUD 元素，完整清单见仓库根 `AGENTS.md`（本文件不重复维护清单，避免两处脱节）。
- 纯逻辑放 `src/main/java/com/fo/addon/utils/`（不依赖 MC 运行时，可单元测试）；测试放 `src/test/java/com/fo/addon/`。

## 铁律（不可协商）

1. **每次改动完成后，必须立即创建一个对应的 Git commit**（信息包含模块名 + 改动摘要），以便追踪和回滚。
2. **每次改动后，必须编写或更新相关单元测试**，并在交付前运行 `./ ./gradlew build`，确保所有测试和验证全部通过。
3. **前端全中文**：所有用户可见的界面文本（模块名、设置名、设置描述、下拉框选项、开关、按钮、提示消息、通知、聊天输出）一律中文；不得出现英文 UI 文本（代码标识符、物品 ID、内部类名除外）。新增/修改 UI 文本时必须自查是否中文。
4. **命名规范（FO 大写 + 版本号不带 V + 产物带 MC 版本）**：**唯一权威来源是仓库根 `AGENTS.md` 的「命名规范」一节**，本节与之保持一致，冲突时以 `AGENTS.md` 为准。要点：① 版本号一律不带 `V` 前缀（写 `6.25`，不写 `V6.25`）；② 构建产物名 = **`FO-<mod版本>-<MC版本>.jar`**，例 `FO-6.25-1.21.11.jar`，由 `build.gradle.kts` 从 `gradle/libs.versions.toml` 的 `mod-version` + `minecraft` 自动拼，**不得在别处写死**；③ Git tag = `<mod版本>`（如 `6.25`）；④ Release 标题 `FO <mod版本>-<MC版本>`，附件名 = 构建产物名；⑤ `fabric.mod.json` 的 `version` 用纯 mod 版本（不带 MC 后缀）；⑥ mod id 保持小写 `fo`，Java 包名 `com.fo.addon` 保持小写。
5. **先讨论后动手**：涉及功能方案、模块设计、功能取舍、行为变化、默认值调整等方向性决策时，必须先给出方案供用户讨论，由用户确定后再更改代码；未经用户拍板，不得直接动手实现。
6. **交付**：把 `build/libs/FO-<mod版本>-<MC版本>.jar`（两段都随 `gradle/libs.versions.toml` 的 `mod-version` 与 `minecraft` 走，当前为 `build/libs/FO-6.25-1.21.11.jar`）通过 `present_files` 交给用户。

## 工作流

1. **定位**：分析需求 → 找到对应模块文件（普通模块在 `src/main/java/com/fo/addon/modules/`，鞘翅套件在 `.../elytra/modules/`，其核心逻辑在 `.../elytra/core/`）。
2. **改代码**：模块内做交互/时序逻辑；可提取的判定逻辑（决策表、列表过滤、方向过滤等）放 `utils/`（模块级，如 `FacingLogic`、`TrashLogic`、`CraftingSlotMath`）或 `elytra/core/`（鞘翅套件级，如 `Needs`、`TaskStatus`、`TimelinessCounter`）便于单测。
3. **补测试**：为行为变更写/更新断言（尤其是判定类逻辑，必须覆盖新行为）。
4. **验证**：`./ ./gradlew build` 全绿（BUILD SUCCESSFUL + 全部测试 0 failures / 0 errors）。Windows 上按 `docs/windows-build.md` 用本地 `gradle.bat` + `-Dorg.gradle.java.home=<JDK>`，不要用 `gradlew.bat`。
5. **提交**：`git add` 相关文件 → commit（仓库根）。本机 `github.com` 的 git 传输会超时，推送改用 `api.github.com` 的 REST API（blob → tree → commit → 推进 `main`），见 `docs/scripts/gh-deliver.ps1`；**提交前先按 blob SHA-1 与远端 tree 比对，避免用旧副本覆盖别人的新提交**。
6. **发布 jar 走 GitHub Release（不再归档到 releases/）**：`./ ./gradlew build` 产出 `build/libs/FO-<mod版本>-<MC版本>.jar`（如 `FO-6.25-1.21.11.jar`）后：① commit（信息含模块名+摘要）→ ② 打 tag `<mod版本>`（**不带 V**，如 `6.25`，指向该版本 commit）→ ③ 建正式 Release：**标题 `FO <mod版本>-<MC版本>`**（如 `FO 6.25-1.21.11`）、**附件为构建产物**（附件名与产物同名），命令形如 `gh release create 6.25 build/libs/FO-6.25-1.21.11.jar --title "FO 6.25-1.21.11" --notes "<更新说明>"` → ④ 在 `releases/README.md` 版本清单加一行（版本 | commit | 说明）。**releases/*.jar 已被 `.gitignore` 拦截，禁止再往仓库提交 jar**；历史 jar 保留在 git 历史中可回溯。纯文档改动**不要**发版、不要建 Release。
7. **交付**：`present_files` 交付新 jar，并告知用户改动点与测试建议。

## 仓库拉取（与 AGENTS.md 硬性规范 7 一致）

- 新设备/新环境一律**浅克隆**（github.com 直连慢且常超时，历史含 100+ 个 jar 二进制，全量 clone 要很久）：
  `git clone --depth=1 https://gh-proxy.com/https://github.com/jialebot6666/FO.git`
- 日常开发增量 `git pull` 即可；确需完整历史时才 `git fetch --unshallow`；**不要全量 clone**。

## 用户偏好（历史沉淀）

- 默认值/物资类需求：用户喜欢"直接从代码层改"，不要只给配置教程。
- 默认物资/名单：白名单模式，扔垃圾默认白名单 **62 项**（`utils/TrashDefaults.java` 的 `DEFAULT_WHITELIST_IDS`，4.22 加了 `minecraft:sand` 由 61 → 62，由 `TrashDefaultsTest` 断言锁定）；补给物资列表格式 `物品ID;最低值;目标库存`（如 `minecraft:golden_carrot;8;32`）。
- 模块开关/设置：新设置默认自动开启（除非用户明确要求默认关闭）。
- 交付时提醒：改默认值后需重置模块设置，旧配置会覆盖默认值。
- 测试环境：用户常用其他 mod，FO 的"异常"要先确认不是别的 mod 导致。

---
name: fo-development
description: FO 插件（Minecraft Meteor Client Fabric 生存辅助，MC 1.21.11）的开发、修复、测试与交付规范。涉及 FO 模块功能开发、Bug 修复、参数调整、默认值修改、单元测试与 jar 交付时必须使用本 skill。
license: CC0-1.0
---

# FO 插件开发规范

## 项目背景

- 定位：生存辅助全自动 Meteor Client 插件（Fabric，MC 1.21.11），模块名统一带 `FO ` 前缀（用户环境装有大量其他中文 addon，防重名冲突），前端全汉化。
- 代码根：`fo/`，包 `com.fo.addon`；仓库根 `/home/user/Doubao/chats/38441043681646338`。
- 现有模块：FO 鞘翅采集（ElytraCollector）、FO 自动种树（AutoTree）、FO 自动扔垃圾（AutoTrash）。
- 纯逻辑放 `fo/src/main/java/com/fo/addon/utils/`（不依赖 MC 运行时，可单元测试）；测试放 `fo/src/test/java/com/fo/addon/`。

## 铁律（不可协商）

1. **每次改动完成后，必须立即创建一个对应的 Git commit**（信息包含模块名 + 改动摘要），以便追踪和回滚。
2. **每次改动后，必须编写或更新相关单元测试**，并在交付前运行 `cd fo && ./gradlew build`，确保所有测试和验证全部通过。
3. **前端全中文**：所有用户可见的界面文本（模块名、设置名、设置描述、下拉框选项、开关、按钮、提示消息、通知、聊天输出）一律中文；不得出现英文 UI 文本（代码标识符、物品 ID、内部类名除外）。新增/修改 UI 文本时必须自查是否中文。
4. **命名规范（FO 大写 + 版本体系统一）**：插件名称统一大写 **FO**——构建产物文件名（`FO-V<版本>.jar`）、mod 显示名一律大写 FO；**构建产物名与 mod 版本号必须统一到版本体系**：产物 = `FO-<mod版本>.jar`，升版时同步修改 `fo/gradle/libs.versions.toml` 的 `mod-version`，不得脱节；mod id 按 Fabric 规范保持小写 `fo`，Java 包名 `com.fo.addon` 保持小写。
5. **交付**：把 `fo/build/libs/FO-V2.3.jar`（文件名 = `FO-<mod版本>.jar`）通过 `present_files` 交给用户。

## 工作流

1. **定位**：分析需求 → 找到对应模块文件（`fo/src/main/java/com/fo/addon/modules/*.java`）。
2. **改代码**：模块内做交互/时序逻辑；可提取的判定逻辑（决策表、列表过滤、方向过滤等）放 `utils/` 便于单测。
3. **补测试**：为行为变更写/更新断言（尤其是判定类逻辑，必须覆盖新行为）。
4. **验证**：`cd fo && ./gradlew build` 全绿（BUILD SUCCESSFUL + 全部测试 0 failures）。
5. **提交**：`git add` 相关文件 → commit（仓库根）。
6. **归档 jar**：把 `fo/build/libs/FO-V2.3.jar` 复制到 `releases/FO-V<版本号>-<commit短哈希>.jar`（版本号按交付顺延 V2.x → V2.y），并 `git add releases/` 提交——**每个修复/功能版本必须留 jar 存档**。
7. **交付**：`present_files` 交付新 jar，并告知用户改动点与测试建议。

## 用户偏好（历史沉淀）

- 默认值/物资类需求：用户喜欢"直接从代码层改"，不要只给配置教程。
- 默认物资/名单：白名单模式，默认物品列表 61 项；物资列表格式 `物品ID;最低值;目标库存`（如 `minecraft:golden_carrot;8;32`）。
- 模块开关/设置：新设置默认自动开启（除非用户明确要求默认关闭）。
- 交付时提醒：改默认值后需重置模块设置，旧配置会覆盖默认值。
- 测试环境：用户常用其他 mod，FO 的"异常"要先确认不是别的 mod 导致。

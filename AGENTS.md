# AGENTS.md

## 项目

FO — Minecraft **Meteor Client (Fabric) 生存辅助全自动插件**（MC 1.21.11，模块统一带 `FO ` 前缀、全汉化）。
- 代码根：`fo/`（包 `com.fo.addon`），参考源码：`ref-src/`
- 现有模块：FO 鞘翅采集（ElytraCollector）、FO 自动种树（AutoTree）、FO 自动扔垃圾（AutoTrash）

## 硬性规范（每次改动必须执行）

1. **每次改动完成后，都必须创建一个对应的 Git commit**，以便后续追踪和回滚。
2. **每次改动后，都必须编写或更新相关测试**，并在交付给用户前，确保所有测试和验证全部通过。
3. **前端全中文**：所有用户可见的界面文本（模块名、设置名、设置描述、下拉框选项、开关、按钮、提示消息、通知、聊天输出）一律使用中文，不得出现英文 UI 文本（代码标识符、物品 ID、内部类名除外）。

## 构建与测试

- 构建 + 测试（一条命令）：`cd fo && ./gradlew build`
- JDK：21（`/home/user/jdks/jdk-21.0.12.1+1/bin/`）
- 产物：`fo/build/libs/fo-V2.3.jar`（版本号随 mod 版本走，见 `fo/gradle/libs.versions.toml` 的 `mod-version`）
- 交付：最终 jar 必须通过 `present_files` 交给用户

## 详细执行流程

涉及 FO 模块功能开发、Bug 修复、参数调整、测试与交付时，**必须加载 `.github/skills/fo-development/SKILL.md`**，严格按其工作流执行。

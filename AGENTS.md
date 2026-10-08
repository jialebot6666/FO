# AGENTS.md

## 项目

FO — Minecraft **Meteor Client (Fabric) 生存辅助全自动插件**（MC 1.21.11，模块统一带 `FO ` 前缀、全汉化）。
- 代码根：`fo/`（包 `com.fo.addon`）；`ref-src/` 是本机外部参考源码目录，被 `.gitignore` 排除、不入库
- 当前版本：`mod-version = V6.1`（见 `fo/gradle/libs.versions.toml`）
- 现有模块（全部注册在 `AddonTemplate.onInitialize()`，统一放进 `Category("FO")`，按注册顺序）：
  1. FO 鞘翅采集（`modules/ElytraCollector`）
  2. FO 自动种树（`modules/AutoTree`）
  3. FO 自动扔垃圾（`modules/AutoTrash`）
  4. FO 自动LogPlus（`modules/AutoLog`）
  5. FO 自动挖沙（`modules/AutoMineSand`）
  6. FO 自动挖矿（`modules/AutoMining`）
  7. FO杀戮光环（`modules/FOKillAura`）
  8. FO 自动鞘翅飞行（`elytra/modules/AutoElytraFlight`）
  9. FO 自动不祥宝库（`elytra/modules/AutoOminousVault`）
  10. FO 实时平均速度（`elytra/modules/SpeedMeter`）
- 另有 HUD 元素「FO 实时平均速度」（`elytra/hud/SpeedHud`，id `speed-meter`），与 SpeedMeter 共享统计数据
- 鞘翅套件（`elytra/`）自 V6.0 起整体来自 icehack-2 移植，V5.x 旧套件已删除

## 硬性规范（每次改动必须执行）

1. **每次改动完成后，都必须创建一个对应的 Git commit**，以便后续追踪和回滚。
2. **每次改动后，都必须编写或更新相关测试**，并在交付给用户前，确保所有测试和验证全部通过。
3. **前端全中文**：所有用户可见的界面文本（模块名、设置名、设置描述、下拉框选项、开关、按钮、提示消息、通知、聊天输出）一律使用中文，不得出现英文 UI 文本（代码标识符、物品 ID、内部类名除外）。
4. **务实说话**：没从代码里实际看到的、没验证过的，不说。不根据猜测推断功能，不编结论。不确定就说"不知道"或"需要看代码确认"。
5. **先讨论后动手**：涉及功能方案、模块设计、功能取舍、行为变化、默认值调整等方向性决策，必须先给出方案由用户拍板，未经确认不得直接改代码（与 `.github/skills/fo-development/SKILL.md` 铁律 5 一致）。
6. **每次发版在 GitHub 建正式 Release**：每个版本（Vx.y）交付时，除归档 `releases/FO-Vx.y-<短哈希>.jar` 外，必须在 GitHub（jialebot6666/FO）创建正式 Release——tag 指向该版本 commit、标题 `FO Vx.y`、说明写入该版本更新介绍、并附带对应 jar 为 Release 附件，方便用户直接下载与回溯。

## 构建与测试

- 构建 + 测试（一条命令）：`cd fo && ./gradlew build`（JUnit 5 随 build 一起跑，交付前必须 0 failures / 0 errors）
- JDK：21
  - Linux 侧：`/home/user/jdks/jdk-21.0.12.1+1/bin/`
  - Windows 侧：用 Minecraft 启动器自带的 runtime（**必须选带 `javac.exe` 的那个**），
    完整流程见 `docs/windows-build.md`
- ⚠️ `fo/gradle.properties` 里写死了 `org.gradle.java.home=/home/user/jdks/jdk-21.0.12.1+1`（Linux agent 的环境）。
  **不要改这个文件**，Windows 上用 `-Dorg.gradle.java.home=<本地 JDK 路径>` 覆盖。
- ⚠️ 国内网络：`services.gradle.org` 会 connection reset，别用 `gradlew.bat`；Gradle 9.2.0 走腾讯镜像下载后用 `gradle.bat` 直接跑。
  `github.com` 的 git 传输在本机超时，clone 走 `https://gh-proxy.com/https://github.com/...`，
  提交与发版走 `api.github.com` REST API（见 `docs/scripts/gh-deliver.ps1`）。
- Baritone 是仓库内的本地 jar（`fo/libs/baritone-api-fabric-1.21.11-SNAPSHOT.jar`，`modCompileOnly`，运行时由 baritone mod 提供），不需要额外下载。
- 产物：`fo/build/libs/FO-V6.1.jar`（版本号随 mod 版本走，见 `fo/gradle/libs.versions.toml` 的 `mod-version`）
- 交付：最终 jar 必须通过 `present_files` 交给用户

## 命名规范（硬性）

- 插件名称统一大写 **FO**：构建产物文件名（`FO-V<版本>.jar`）、mod 显示名（`fabric.mod.json` 的 `name`）一律大写 FO。
- **构建产物名与 mod 版本号统一到版本体系**：产物文件名 = `FO-<mod版本>.jar`；升版时同步修改 `fo/gradle/libs.versions.toml` 的 `mod-version`（如 V6.0 → V6.1），产物名自动跟随，不得出现产物名与版本体系脱节。
- mod id（`fabric.mod.json` 的 `id`）按 Fabric 硬性规范保持小写 `fo`；Java 包名 `com.fo.addon` 保持小写（Java 惯例）。

## 详细执行流程

涉及 FO 模块功能开发、Bug 修复、参数调整、测试与交付时，**必须加载 `.github/skills/fo-development/SKILL.md`**，严格按其工作流执行。

# AGENTS.md

## 项目

FO — Minecraft **Meteor Client (Fabric) 生存辅助全自动插件**（MC 1.21.11，模块统一带 `FO ` 前缀、全汉化）。
- 代码根：`fo/`（包 `com.fo.addon`）；`ref-src/` 是本机外部参考源码目录，被 `.gitignore` 排除、不入库
- 当前版本：`mod-version = V6.16`（见 `fo/gradle/libs.versions.toml`）
- 现有模块（全部注册在 `AddonTemplate.onInitialize()`，统一放进 `Category("FO")`，按注册顺序）：
  1. FO 鞘翅采集（`modules/ElytraCollector`）
  2. FO 自动种树（`modules/AutoTree`）
  3. FO 自动扔垃圾（`modules/AutoTrash`）
  4. FO 自动LogPlus（`modules/AutoLog`）
  5. FO 自动挖沙（`modules/AutoMineSand`）
  6. FO 自动挖矿（`modules/AutoMining`）
  7. FO杀戮光环（`modules/FOKillAura`）
  8. FO 古城战利品搜索（`ancient/modules/FOAncientCitySearch`，V6.3 新增：附魔金苹果/迅捷潜行3 搜索 + Xaero 路径点，半自动）
- **古城模块的依赖收尾路线**（目标「彻底零原生依赖」，全程自研：以 zychen027/AncientCityLootPredictor 的行为为规格，自己写成 Java）：
  - ✅ V6.7 已完成：**古城定位**改为纯 Java 自写（`utils/AncientCityLocatorLogic`），不再走 cubiomes 的 region 扫描；
    结构数据（58 个 NBT + 7 个 JSON）打进 mod 资源，由 `ancient/core/AncientResourceSource` 从 jar 内直读。
    半径语义：半径单位是区块，距离按**方块距离**、基准点为城市原点（旧实现是区块距离、基准点原点 + 8）。
  - ✅ V6.8 已完成：**结构重放 + 箱子提取 + 战利品掷骰**全部纯 Java。新增 `AncientLootTable` / `AncientEnchantData` /
    `AncientEnchantAlgorithm` / `AncientLootEvaluator`，战利品表与附魔数据（107 个文件）打包进资源。
    **已删除 `libs/fo-ancient-blackbox.jar`**（657 个混淆类）；两个 Windows DLL 抽到 `src/main/resources/natives/windows/`
    继续供群系验证用，jar 从 3.19 MiB 降到 1.98 MiB。
    ✅ V6.12 起：「>= 26.1」选项已真正生效（V6.13 实机验证：3C3U 服 ≥26.1 物品全对——
    即 26.x 的掷骰仍是每箱 LootTableSeed + JRand，只有表变了；misaka 反编译里那套
    「26.1 Xoroshiro 随机序列」与实机不符，仅留档不实现）。
  - ✅ V6.9 已完成：**双高亮 + 本地持久化 + 自动清理**。世界内高亮（附魔金=金、迅捷潜行3=蓝、两者=紫，
    靠近才画、半径可调）+ Xaero 路径点颜色改正；命中记录写到游戏目录 `fo-ancient-chests.json`
    （`AncientChestStore`，按「服务器+维度」分作用域），跨重启保留；箱子被开/被挖则两处高亮一并删除。
  - ✅ V6.10 已完成：修复 **Xaero 路径点联动全线失效**。旧桥要求 `xaero.common.minimap.waypoints.WaypointSet`
    （26.x 已删除）与 `xaero.map.mods.SupportMods`（属于世界地图模组），且「缺一个就整体判定未安装」，
    在现行 Xaero 上必然误报「Xaero地图模组未安装」。已重写 `XaeroWaypointBridge` 为
    **分层探测 + 双通道自动选**（官方 `ThirdPartyWaypoints` 优先，退化到玩家 `WaypointSet`），
    颜色改用 `WaypointColor` 枚举按名取。⚠️ 排查 Xaero 兼容性时切记：**对着真实 jar 反编译核对类名与签名**，
    不要凭记忆或旧资料（这次就是吃了这个亏）。
  - ✅ V6.11 已完成：**紧急修复 V6.10 崩溃**。⚠️ 铁律：**Xaero 路径点容器不是线程安全的**，
    其渲染线程每帧遍历；从后台线程写会让渲染线程抛 `ConcurrentModificationException` 整局崩掉。
    规定「后台线程只算，一切共享状态（本地记录 + Xaero）只在客户端线程改」，
    模块用 `pendingMarks` 队列 + `TickEvent.Post` 出队实现；`AncientChestStore` 另加 `synchronized` 兜底。
  - ✅ V6.12 已完成：**「&gt;= 26.1」的战利品表落地**。实测 4 份官方 jar（1.21.11/26.1.2/26.2/26.3）后确认：
    26.x 的古城表只比 1.21.11 少一个 `minecraft:name_tag`（权重 86→84），单这一项就会让每次加权抽取错位
    （实机表现：位置对、物品全错）；26.3 换了 JSON 键名但语义相同。
    26.x 官方表逐字节放进 `resources/fo/ancient/v26/loot_table/chests/`，由
    `AncientResourceSource.bundledLootTables(from26)` 按「游戏版本」设置选表。
    ⚠️ 注意 `AncientLootEvaluator.roll` 的**三参重载固定走 MC_NATIVE**，做兼容性对照时必须用四参版显式传 mode，
    否则会变成「生产模式 vs 兼容模式」的苹果比橘子（这次排查绕了很久就栽在这里）。
    ⚠️ 另注：Gradle 的 `-D` **不会**传给 fork 出来的测试 JVM，探针要读参数请用环境变量；
    且 `test` 任务默认走 build cache，改参数跑探针要加 `--no-build-cache`。
  - ✅ V6.13 已完成：**「游戏版本」四档化 + 高亮假命中修复**。四档 = ≤1.20.6 / 1.21~1.21.5 /
    1.21.6~1.21.11 / ≥26.1（差异清单见 GameVersionOption 类注释，全部来自官方 jar 逐条目实测）；
    掷骰器支持 ≤1.20.6 旧语法。高亮修复三件套：搜索结果【整体替换】作用域记录（不再合并）、
    记录 v2 格式带种子（兼容 v1）、渲染按当前种子过滤（拿不到种子则不过滤）。
    ⚠️ 命中记录的语义是「该作用域最近一次搜索的结果」——改这里之前先想清楚这个约定。
  - ✅ V6.14 已完成：**目标柱子 + 路径点生命周期管理**。柱子走 `Render3DEvent` 的
    `event.renderer`（`NO_DEPTH_TEST` 管线，穿墙可见；`depthRenderer` 才受遮挡），地表 Y 用
    `World.getTopY(Heightmap.Type.WORLD_SURFACE, x, z)` 并按 (x,z) 缓存。
    路径点生命周期：搜索整批替换时先撤旧点 → 开箱/被挖即删 → 关模块全收（按记录坐标删，不碰玩家手动的）→
    重开模块验证后重写。「已开」判定用自适应策略 `AncientOpenedChestPolicy`（先确认服务端会同步
    LootTable 字段才允许凭 null 判已开；限制：开箱后区块未重载时检测不到）。
    路径点写入方式默认改为「仅玩家路径点集」（官方接口代码保留可切回）。
  - ✅ V6.15 已完成：默认参数调整——搜索类型=两者都搜索、游戏版本=≥26.1（对齐 3C3U）、
    高亮半径=256。只改 `defaultValue`，逻辑零变动。
  - ✅ V6.16 已完成：**路径点只在世界地图显示**。写入时把可见性设为
    `WaypointVisibilityType.WORLD_MAP_LOCAL`（Xaero 26.x；`AbstractWaypointRenderProvider`
    对 WORLD_MAP_* 的点直接跳过小地图与世界内渲染）。「全局/本地」选本地——记录按服务器+维度存。
    旧版 Xaero 无该枚举，反射取不到则保持默认。Xaero 兼容性排查依旧铁律：对真实 jar 反编译核实。
  - ⏳ V7.0 待做：**群系验证**自写（替代 cubiomes 的 `isViableAncientCity`），然后删掉那两个 Windows DLL
  - 黄金回归测试：`AncientCityGoldenTest`（拼图块 + 箱子坐标 + LootTableSeed）与 `AncientLootEvaluatorTest`
    （113 箱掷骰结果逐箱锁死），两者都**不依赖黑盒**。
  - ⚠️ 已知与参考实现的偏差（我们按 MC 本体语义实现，是刻意修正）：参考实现的 `enchant_with_levels` 忽略 options
    用全量附魔、`enchant_randomly` 的标签不展开嵌套；修正后 113 箱里 21 箱的物品清单会不同。
    对照验证时可用 `AncientEnchantData.SourceMode.REFERENCE_COMPAT` 复刻它的偏差。
- V6.2 已删除 V6.0 的 icehack-2 鞘翅套件（自动鞘翅飞行/自动不祥宝库/实时平均速度 三模块 + HUD），当前无 elytra 套件

## 硬性规范（每次改动必须执行）

1. **每次改动完成后，都必须创建一个对应的 Git commit**，以便后续追踪和回滚。
2. **每次改动后，都必须编写或更新相关测试**，并在交付给用户前，确保所有测试和验证全部通过。
3. **前端全中文**：所有用户可见的界面文本（模块名、设置名、设置描述、下拉框选项、开关、按钮、提示消息、通知、聊天输出）一律使用中文，不得出现英文 UI 文本（代码标识符、物品 ID、内部类名除外）。
4. **务实说话**：没从代码里实际看到的、没验证过的，不说。不根据猜测推断功能，不编结论。不确定就说"不知道"或"需要看代码确认"。
5. **先讨论后动手**：涉及功能方案、模块设计、功能取舍、行为变化、默认值调整等方向性决策，必须先给出方案由用户拍板，未经确认不得直接改代码（与 `.github/skills/fo-development/SKILL.md` 铁律 5 一致）。
6. **每次发版在 GitHub 建正式 Release**：每个版本（Vx.y）交付时，除归档 `releases/FO-Vx.y.jar` 外，必须在 GitHub（jialebot6666/FO）创建正式 Release——tag 指向该版本 commit、标题 `FO Vx.y`、说明写入该版本更新介绍、并附带对应 jar 为 Release 附件，方便用户直接下载与回溯。

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
- ⚠️ **依赖下载走国内镜像**：`repo1.maven.org` 直连实测仅约 32 KB/s，Loom 冷启动会直接 `SocketTimeoutException: Read timed out`。
  解决办法（用户级配置，**不改仓库文件**）与实测数据见 `docs/windows-build.md` 第 10 节。
- Baritone 是仓库内的本地 jar（`fo/libs/baritone-api-fabric-1.21.11-SNAPSHOT.jar`，`modCompileOnly`，运行时由 baritone mod 提供），不需要额外下载。
- 产物：`fo/build/libs/FO-V6.16.jar`（版本号随 mod 版本走，见 `fo/gradle/libs.versions.toml` 的 `mod-version`）
- 交付：最终 jar 必须通过 `present_files` 交给用户

## 命名规范（硬性）

- 插件名称统一大写 **FO**：构建产物文件名（`FO-V<版本>.jar`）、mod 显示名（`fabric.mod.json` 的 `name`）一律大写 FO。
- **构建产物名与 mod 版本号统一到版本体系**：产物文件名 = `FO-<mod版本>.jar`；升版时同步修改 `fo/gradle/libs.versions.toml` 的 `mod-version`（如 V6.0 → V6.1），产物名自动跟随，不得出现产物名与版本体系脱节。
- mod id（`fabric.mod.json` 的 `id`）按 Fabric 硬性规范保持小写 `fo`；Java 包名 `com.fo.addon` 保持小写（Java 惯例）。

## 详细执行流程

涉及 FO 模块功能开发、Bug 修复、参数调整、测试与交付时，**必须加载 `.github/skills/fo-development/SKILL.md`**，严格按其工作流执行。

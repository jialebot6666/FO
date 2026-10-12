# AGENTS.md

## 项目

FO — Minecraft **Meteor Client (Fabric) 生存辅助全自动插件**（MC 1.21.11，模块统一带 `FO ` 前缀、全汉化）。
- 代码根：`fo/`（包 `com.fo.addon`）；`ref-src/` 是本机外部参考源码目录，被 `.gitignore` 排除、不入库
- 当前版本：`mod-version = 6.25`（见 `fo/gradle/libs.versions.toml`），产物名 = `FO-6.25-1.21.11.jar`（详见「命名规范」）
- 现有模块（全部注册在 `AddonTemplate.onInitialize()`，统一放进 `Category("FO")`，按注册顺序）：
  1. FO 鞘翅采集（`modules/ElytraCollector`）
  2. FO 自动种树（`modules/AutoTree`）
  3. FO 自动扔垃圾（`modules/AutoTrash`）
  4. FO 自动LogPlus（`modules/AutoLog`）
  5. FO 自动挖沙（`modules/AutoMineSand`）
  6. FO 自动挖矿（`modules/AutoMining`）
  7. FO 杀戮光环（`modules/FOKillAura`）
  8. FO 古城战利品搜索（`ancient/modules/FOAncientCitySearch`，6.3 新增：附魔金苹果/迅捷潜行3 搜索 + Xaero 路径点，半自动）
- **古城模块的依赖收尾路线**（目标「彻底零原生依赖」，全程自研：以 zychen027/AncientCityLootPredictor 的行为为规格，自己写成 Java）：
  - ✅ 6.7 已完成：**古城定位**改为纯 Java 自写（`utils/AncientCityLocatorLogic`），不再走 cubiomes 的 region 扫描；
    结构数据（58 个 NBT + 7 个 JSON）打进 mod 资源，由 `ancient/core/AncientResourceSource` 从 jar 内直读。
    半径语义：半径单位是区块，距离按**方块距离**、基准点为城市原点（旧实现是区块距离、基准点原点 + 8）。
  - ✅ 6.8 已完成：**结构重放 + 箱子提取 + 战利品掷骰**全部纯 Java。新增 `AncientLootTable` / `AncientEnchantData` /
    `AncientEnchantAlgorithm` / `AncientLootEvaluator`，战利品表与附魔数据（107 个文件）打包进资源。
    **已删除 `libs/fo-ancient-blackbox.jar`**（657 个混淆类）；两个 Windows DLL 抽到 `src/main/resources/natives/windows/`
    继续供群系验证用，jar 从 3.19 MiB 降到 1.98 MiB。
    ✅ 6.12 起：「>= 26.1」选项已真正生效（6.13 实机验证：3C3U 服 ≥26.1 物品全对——
    即 26.x 的掷骰仍是每箱 LootTableSeed + JRand，只有表变了；misaka 反编译里那套
    「26.1 Xoroshiro 随机序列」与实机不符，仅留档不实现）。
  - ✅ 6.9 已完成：**双高亮 + 本地持久化 + 自动清理**。世界内高亮（附魔金=金、迅捷潜行3=蓝、两者=紫，
    靠近才画、半径可调）+ Xaero 路径点颜色改正；命中记录写到游戏目录 `fo-ancient-chests.json`
    （`AncientChestStore`，按「服务器+维度」分作用域），跨重启保留；箱子被开/被挖则两处高亮一并删除。
  - ✅ 6.10 已完成：修复 **Xaero 路径点联动全线失效**。旧桥要求 `xaero.common.minimap.waypoints.WaypointSet`
    （26.x 已删除）与 `xaero.map.mods.SupportMods`（属于世界地图模组），且「缺一个就整体判定未安装」，
    在现行 Xaero 上必然误报「Xaero地图模组未安装」。已重写 `XaeroWaypointBridge` 为
    **分层探测 + 双通道自动选**（官方 `ThirdPartyWaypoints` 优先，退化到玩家 `WaypointSet`），
    颜色改用 `WaypointColor` 枚举按名取。⚠️ 排查 Xaero 兼容性时切记：**对着真实 jar 反编译核对类名与签名**，
    不要凭记忆或旧资料（这次就是吃了这个亏）。
  - ✅ 6.11 已完成：**紧急修复 6.10 崩溃**。⚠️ 铁律：**Xaero 路径点容器不是线程安全的**，
    其渲染线程每帧遍历；从后台线程写会让渲染线程抛 `ConcurrentModificationException` 整局崩掉。
    规定「后台线程只算，一切共享状态（本地记录 + Xaero）只在客户端线程改」，
    模块用 `pendingMarks` 队列 + `TickEvent.Post` 出队实现；`AncientChestStore` 另加 `synchronized` 兜底。
  - ✅ 6.12 已完成：**「&gt;= 26.1」的战利品表落地**。实测 4 份官方 jar（1.21.11/26.1.2/26.2/26.3）后确认：
    26.x 的古城表只比 1.21.11 少一个 `minecraft:name_tag`（权重 86→84），单这一项就会让每次加权抽取错位
    （实机表现：位置对、物品全错）；26.3 换了 JSON 键名但语义相同。
    26.x 官方表逐字节放进 `resources/fo/ancient/v26/loot_table/chests/`，由
    `AncientResourceSource.bundledLootTables(from26)` 按「游戏版本」设置选表。
    ⚠️ 注意 `AncientLootEvaluator.roll` 的**三参重载固定走 MC_NATIVE**，做兼容性对照时必须用四参版显式传 mode，
    否则会变成「生产模式 vs 兼容模式」的苹果比橘子（这次排查绕了很久就栽在这里）。
    ⚠️ 另注：Gradle 的 `-D` **不会**传给 fork 出来的测试 JVM，探针要读参数请用环境变量；
    且 `test` 任务默认走 build cache，改参数跑探针要加 `--no-build-cache`。
  - ✅ 6.13 已完成：**「游戏版本」四档化 + 高亮假命中修复**。四档 = ≤1.20.6 / 1.21~1.21.5 /
    1.21.6~1.21.11 / ≥26.1（差异清单见 GameVersionOption 类注释，全部来自官方 jar 逐条目实测）；
    掷骰器支持 ≤1.20.6 旧语法。高亮修复三件套：搜索结果【整体替换】作用域记录（不再合并）、
    记录 v2 格式带种子（兼容 v1）、渲染按当前种子过滤（拿不到种子则不过滤）。
    ⚠️ 命中记录的语义是「该作用域最近一次搜索的结果」——改这里之前先想清楚这个约定。
  - ✅ 6.14 已完成：**目标柱子 + 路径点生命周期管理**。柱子走 `Render3DEvent` 的
    `event.renderer`（`NO_DEPTH_TEST` 管线，穿墙可见；`depthRenderer` 才受遮挡），地表 Y 用
    `World.getTopY(Heightmap.Type.WORLD_SURFACE, x, z)` 并按 (x,z) 缓存。
    路径点生命周期：搜索整批替换时先撤旧点 → 开箱/被挖即删 → 关模块全收（按记录坐标删，不碰玩家手动的）→
    重开模块验证后重写。「已开」判定用自适应策略 `AncientOpenedChestPolicy`（先确认服务端会同步
    LootTable 字段才允许凭 null 判已开；限制：开箱后区块未重载时检测不到）。
    路径点写入方式默认改为「仅玩家路径点集」（官方接口代码保留可切回）。
  - ✅ 6.15 已完成：默认参数调整——搜索类型=两者都搜索、游戏版本=≥26.1（对齐 3C3U）、
    高亮半径=256。只改 `defaultValue`，逻辑零变动。
  - ✅ 6.16 已完成：**路径点只在世界地图显示**。写入时把可见性设为
    `WaypointVisibilityType.WORLD_MAP_LOCAL`（Xaero 26.x；`AbstractWaypointRenderProvider`
    对 WORLD_MAP_* 的点直接跳过小地图与世界内渲染）。「全局/本地」选本地——记录按服务器+维度存。
    旧版 Xaero 无该枚举，反射取不到则保持默认。Xaero 兼容性排查依旧铁律：对真实 jar 反编译核实。
  - ✅ 6.17 已完成：修复「打开背包误删附近箱子标记」（玩家背包也是 HandledScreen；
    已显式排除 InventoryScreen + 指向记录 10 tick 时效），并支持开双箱时另一半标记一并删
    （搭档方向照抄原版 ChestBlock 字节码：LEFT→rotateYClockwise，RIGHT→rotateYCounterclockwise）。
    ⚠️ 教训：「有 GUI 弹出 = 开了准星指的方块」这类启发式必须把玩家自己的背包界面排除在外。
  - ✅ 6.18 已完成：**自动扔垃圾 misaka 式批量丢弃 + 附魔书等级过滤**。批量 = 每个扫描间隔
    全背包扫一遍、所有命中堆一次全扔（此前每轮只扔一种）；附魔书过滤四种模式（跟随名单 /
    保留等级达标 / 全部丢弃 / 全部保留）+ 等级阈值（1~5，默认 3），等级读
    `ItemEnchantmentsComponent` 取堆内最高级。判定纯逻辑在 `TrashLogic.decideBook`（可单测）。
    附魔台合并的多附魔书按最高级保留（宁留勿误删）。
  - ✅ 6.19 已完成：**「已开名单」持久化**（存储 v3）。开过的箱子记入 per-scope `opened` 坐标集合
    （永久保留；换种子时随作用域整份清空），三个检测点（开箱判定 / 恢复记录 lootTable 检测 /
    周期清理 lootTable 检测）都写入；新搜索落地时跳过名单坐标。⚠️ 语义要点：`clear(scope)` 全清
    （换种子），`clearMarks(scope)` 只清标记（整批替换/重建时用，保已开名单与种子）——别混用。
    ⚠️ 行为变化：误开的箱子不能再靠重搜恢复（用户确认接受）。
    顺手加固 `AncientJson`：不平衡 JSON 由裸越界改为带信息异常。
  - ✅ 6.20 已完成：**自动扔垃圾附魔书过滤重做**（旧「附魔书模式 + 全局等级阈值」删除，用户反馈鸡肋）。
    改为「附魔书规则」文本列表（StringListSetting，游戏内直接打中文）：每行一条
    「附魔中文名 + 可选最低等级」（如「迅捷潜行3」「经验修补」，不写数字=任意等级），
    `utils/BookRuleIndex` 负责解析与目录匹配（中文名走 Meteor Names；英文 ID 精确唯一命中，
    中文名按「包含」收集——「潜行」→ 迅捷潜行，「保护」→ 全部保护系，「杀手」→ 亡灵杀手/节肢杀手，
    一个词可一匹配多附魔）。
    **打通黑白名单**：命中任一规则（书内任一附魔达标即中，宽松策略）的书视同「在名单里」，
    统一走 `TrashLogic.shouldDrop`；规则为空时附魔书完全跟随物品名单；
    整本全留/全丢 = 把「附魔书」物品加进/排除出物品列表（物品列表含 ENCHANTED_BOOK 即命中全部书）。
    纯逻辑：行解析 `TrashLogic.parseRuleLine` + 命中 `TrashLogic.bookMatches` + 目录匹配
    `BookRuleIndex.resolve` 均可单测；无法识别的行启动时聊天栏提示一次。
  - ✅ 6.21 已完成：**附魔书规则改为每附魔一条设置**（6.20 的文本规则要打字，用户不认可；
    方案经用户确认后实施）。删除文本规则（`BookRuleIndex` 已删、`parseRuleLine` 已删），
    改为 `EnchantList` 数据表驱动的 **43 个原版附魔、每附魔一条 IntSetting**（组「附魔书过滤」，
    中文名、滑条 0~该附魔真实最高等级；0=不启用 / 1=任意等级命中 / 2~max=≥该等级命中）。
    **数据来源已逐条核实**（铁律 4）：清单与最高等级来自官方客户端 jar 的
    `data/minecraft/enchantment/*.json`（1.21.11 与 26.1.2 逐条一致，含 26.x 新附魔 lunge/突进）；
    中文名来自 26.1.2 官方 `assets/minecraft/lang/zh_cn.json`（客户端 jar 不带语言文件，
    语言文件走资产索引单独下载，需外取）。判定逻辑不变：书内任一附魔达标即命中 →
    视同「在名单里」走黑白名单；全部 0 = 跟随物品名单。注意：`EnchantListTest` 用官方 43 个
    ID 清单做锚点断言，未来 MC 新增附魔需同步更新 `EnchantList` 与该测试。
  - ✅ 6.22 已完成：**附魔书列表改为一个自定义设置项**（6.21 的 43 条滑条把模块设置页撑爆，
    用户要求做成"物品列表那样"的一个条目；方案经用户确认后实施）。删除「附魔书过滤」设置组，
    新增 `utils/EnchantLevelMapSetting`（附魔 ID→最低等级映射 Setting，仿 Meteor 官方
    `StatusEffectAmplifierMapSetting`：NBT 持久化 value 复合标签、parseImpl 文本解析、只存 >0 条目）
    + `gui/EnchantLevelMapSettingScreen`（全 43 附魔逐行配等级 + 搜索框，仿官方药水界面），
    通过公开 API `SettingsWidgetFactory.registerCustomFactory` 注册控件（AddonTemplate.onInitialize，
    设置页里是「选择」按钮）。位置：sgGeneral 的物品列表与丢弃间隔之间。
    纯逻辑 `TrashLogic.rulesFromLevelMap`（映射→规则）可单测；判定语义不变（≥ 最低等级，
    命中视同在名单走黑白名单）。6.21 的 43 条滑条设置不迁移旧配置（形态不同，用户重配一次）。
  - ✅ 6.24 已完成：**FO 杀戮光环显示名补空格 + 新增「切换回来」**（对齐彗星端官方 KillAura 的 `swap-back`：
    切武器前记住原快捷栏槽位、攻击会话结束时切回；只在会话首次记录，会话中反复切武器不覆盖；
    仅「目标为空 / 手上非可接受武器 / 关闭模块」结束会话，暂停类守卫不结束。默认关闭。
    状态机 `utils/SwapBackTracker` 可单测）。显示名 `FO杀戮光环` → `FO 杀戮光环`（其余模块均为 `FO ` + 空格）。
  - ✅ 6.25 已完成：**古城模块新增「自动偷取」**（用户确认后实施）。移植彗星端 Meteor 官方
    `InventoryTweaks` 的 Auto Steal（源码 + 本机 1.21.11-85 字节码双向核对），差别两点：
    ① 官方只按 `Item` 匹配、**认不出附魔书附的是哪条附魔**，拿不到「迅捷潜行3」——本模块读
       `ItemEnchantmentsComponent` 按附魔 ID + 等级判定（`utils/AncientStealLogic`，可单测）；
    ② 官方在后台线程 `Thread.sleep` 后点槽位（跨线程发交互包），本模块改为「`InventoryEvent` 里
       只快照槽位 → `TickEvent.Post` 里逐个取」，全程客户端线程（对齐 6.11 的教训）。
    触发点用 `InventoryEvent`（服务端同步容器内容时）而非每 tick 扫，天然避开「刚开箱槽位还是空的」误判。
    默认只偷附魔金苹果 + 迅捷潜行3 附魔书，其余物品不动；含「偷取统计」聊天栏汇报。
  - ⏳ 7.0 待做：**群系验证**自写（替代 cubiomes 的 `isViableAncientCity`），然后删掉那两个 Windows DLL
  - 黄金回归测试：`AncientCityGoldenTest`（拼图块 + 箱子坐标 + LootTableSeed）与 `AncientLootEvaluatorTest`
    （113 箱掷骰结果逐箱锁死），两者都**不依赖黑盒**。
  - ⚠️ 已知与参考实现的偏差（我们按 MC 本体语义实现，是刻意修正）：参考实现的 `enchant_with_levels` 忽略 options
    用全量附魔、`enchant_randomly` 的标签不展开嵌套；修正后 113 箱里 21 箱的物品清单会不同。
    对照验证时可用 `AncientEnchantData.SourceMode.REFERENCE_COMPAT` 复刻它的偏差。
- 6.2 已删除 6.0 的 icehack-2 鞘翅套件（自动鞘翅飞行/自动不祥宝库/实时平均速度 三模块 + HUD），当前无 elytra 套件

## 硬性规范（每次改动必须执行）

1. **每次改动完成后，都必须创建一个对应的 Git commit**，以便后续追踪和回滚。
2. **每次改动后，都必须编写或更新相关测试**，并在交付给用户前，确保所有测试和验证全部通过。
3. **前端全中文**：所有用户可见的界面文本（模块名、设置名、设置描述、下拉框选项、开关、按钮、提示消息、通知、聊天输出）一律使用中文，不得出现英文 UI 文本（代码标识符、物品 ID、内部类名除外）。
4. **务实说话**：没从代码里实际看到的、没验证过的，不说。不根据猜测推断功能，不编结论。不确定就说"不知道"或"需要看代码确认"。
5. **先讨论后动手**：涉及功能方案、模块设计、功能取舍、行为变化、默认值调整等方向性决策，必须先给出方案由用户拍板，未经确认不得直接改代码（与 `.github/skills/fo-development/SKILL.md` 铁律 5 一致）。
6. **每次发版在 GitHub 建正式 Release（jar 唯一分发渠道）**：每个版本（`x.y`）交付时，构建产物 `FO-x.y-<MC版本>.jar` **不再提交到 releases/ 目录**（`.gitignore` 已用 `releases/*.jar` 拦截）：① commit（信息含模块名+摘要）→ ② 打 tag `x.y`（**不带 V 前缀**，指向该版本 commit）→ ③ 在 GitHub（jialebot6666/FO）创建正式 Release（标题 `FO x.y-<MC版本>`、说明写入该版本更新介绍、jar 作为附件，附件名 = 构建产物名）→ ④ 在 `releases/README.md` 版本清单加一行（版本 | commit | 说明）。历史 jar 仍完整保留在 git 历史中，可随时 `git show <commit>:releases/FO-Vx.y.jar` 取回（旧归档名带 V，属历史遗留，不必回改）。
7. **仓库拉取一律浅克隆**：新设备/新环境拉取 FO 仓库用 `git clone --depth=1 https://gh-proxy.com/https://github.com/jialebot6666/FO.git`（只拉最新代码，秒级完成；github.com 直连慢且常超时，历史里还保留着 100+ 个 jar 二进制，全量 clone 要很久）。日常开发增量 `git pull` 即可；确需完整历史时才 `git fetch --unshallow`。不要全量 clone。

## 构建与测试

- 构建 + 测试（一条命令）：`cd fo && ./gradlew build`（JUnit 5 随 build 一起跑，交付前必须 0 failures / 0 errors）
- JDK：21
  - Linux 侧：`/home/user/jdks/jdk-21.0.12.1+1/bin/`
  - Windows 侧：用 Minecraft 启动器自带的 runtime（**必须选带 `javac.exe` 的那个**），
    完整流程见 `docs/windows-build.md`
- ⚠️ `fo/gradle.properties` 里写死了 `org.gradle.java.home=/home/user/jdks/jdk-21.0.12.1+1`（Linux agent 的环境）。
  **不要改这个文件**，Windows 上用 `-Dorg.gradle.java.home=<本地 JDK 路径>` 覆盖。
- ⚠️ 国内网络：`services.gradle.org` 会 connection reset，别用 `gradlew.bat`；Gradle 9.2.0 走腾讯镜像下载后用 `gradle.bat` 直接跑。
  `github.com` 的 git 传输在本机超时，clone 一律**浅克隆**：`git clone --depth=1 https://gh-proxy.com/https://github.com/jialebot6666/FO.git`
  （硬性规范 7）；提交与发版走 `api.github.com` REST API（见 `docs/scripts/gh-deliver.ps1`）。
- ⚠️ **依赖下载走国内镜像**：`repo1.maven.org` 直连实测仅约 32 KB/s，Loom 冷启动会直接 `SocketTimeoutException: Read timed out`。
  解决办法（用户级配置，**不改仓库文件**）与实测数据见 `docs/windows-build.md` 第 10 节。
- Baritone 是仓库内的本地 jar（`fo/libs/baritone-api-fabric-1.21.11-SNAPSHOT.jar`，`modCompileOnly`，运行时由 baritone mod 提供），不需要额外下载。
- 产物：`fo/build/libs/FO-6.25-1.21.11.jar`（`FO-<mod版本>-<MC版本>.jar`，两段都从 `fo/gradle/libs.versions.toml` 的 `mod-version` 与 `minecraft` 自动拼）
- 交付：最终 jar 必须通过 `present_files` 交给用户

## 命名规范（硬性）

**本节是唯一权威来源**；任何窗口 / 任何 AI 接手本项目，一律按本节执行，不得沿用旧写法。

1. **版本号一律不带 `V` 前缀**。正确写法是 `6.25`，不是 `V6.25`。历史遗留的 `V6.25` / `FO-V6.25` 写法已废弃，新版本不得再出现。
2. **构建产物名 = `FO-<mod版本>-<MC版本>.jar`**，例：`mod-version = 6.25` + `minecraft = 1.21.11` → **`FO-6.25-1.21.11.jar`**。
   - mod 版本来自 `fo/gradle/libs.versions.toml` 的 `mod-version`；MC 版本来自同文件的 `minecraft`。
   - 两段都由 `fo/build.gradle.kts` 的 `base { version = "$modVersion-$minecraftVersion" }` 自动拼，**不要在别处写死版本串**；升 MC 版本时只改 `minecraft` 一处，产物名自动跟随。
3. **Git tag = `<mod版本>`**（如 `6.25`），不再用 `V6.25` 或 `FO-V6.25`。
4. **GitHub Release 标题 = `FO <mod版本>-<MC版本>`**（如 `FO 6.25-1.21.11`），**附件名必须与构建产物同名**（`FO-6.25-1.21.11.jar`）。
5. 插件名统一大写 **FO**；mod 显示名（`fabric.mod.json` 的 `name`）保持 `FO`。
6. `fabric.mod.json` 的 `version` 用**纯 mod 版本**（如 `6.25`），**不带 MC 后缀** —— 带 `-1.21.11` 会被 Fabric 的 semver 解析成 6.25 的预发布版，影响依赖判定。该字段由 `fo/build.gradle.kts` 的 `processResources` 注入。
7. mod id（`fabric.mod.json` 的 `id`）按 Fabric 硬性规范保持小写 `fo`；Java 包名 `com.fo.addon` 保持小写（Java 惯例）。

示例对照表：

| 项目 | 旧（已废弃） | 新（唯一正确） |
| --- | --- | --- |
| mod-version | `V6.25` | `6.25` |
| 构建产物 | `FO-V6.25.jar` | `FO-6.25-1.21.11.jar` |
| Git tag | `V6.25` | `6.25` |
| Release 标题 | `FO V6.25` | `FO 6.25-1.21.11` |
| Release 附件 | `FO-V6.25.jar` | `FO-6.25-1.21.11.jar` |

## 详细执行流程

涉及 FO 模块功能开发、Bug 修复、参数调整、测试与交付时，**必须加载 `.github/skills/fo-development/SKILL.md`**，严格按其工作流执行。

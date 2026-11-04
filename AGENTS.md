# AGENTS.md

## 项目

FO — Minecraft **Meteor Client (Fabric) 生存辅助全自动插件**（MC 1.21.11，模块统一带 `FO ` 前缀、全汉化）。
- 代码根：`fo/`（包 `com.fo.addon`）；`ref-src/` 是本机外部参考源码目录，被 `.gitignore` 排除、不入库
- 当前版本：`mod-version = 6.8p10`（见 `fo/gradle/libs.versions.toml`），产物名 = `FO-6.8p10-1.21.11.jar`（详见「命名规范」）
- 现有模块（全部注册在 `AddonTemplate.onInitialize()`，统一放进 `Category("FO")`，按注册顺序）：
  1. FO 鞘翅采集（`modules/ElytraCollector`）
  2. FO 自动种树（`modules/AutoTree`）
  3. FO 自动扔垃圾（`modules/AutoTrash`）
  4. FO 自动LogPlus（`modules/AutoLog`）
  5. FO 自动挖沙（`modules/AutoMineSand`）
  6. FO 自动挖矿（`modules/AutoMining`）
  7. FO 杀戮光环（`modules/FOKillAura`）
  8. FO 古城战利品搜索（`ancient/modules/FOAncientCitySearch`，6.3 新增：附魔金苹果/迅捷潜行3 搜索 + Xaero 路径点，半自动）
  9. FO 自动搬运（`modules/StashMover`，6.7 新增：珍珠超穿自动搬运，三种模式——搬运端（双号）/拉珍珠端（双号）/三号模式）
  10. FO 发包挖掘（`modules/FOPacketMine`，6.8p2 新增、6.8p4 补全 Grim 协议：移植 Hachimi V2 SpeedMine——对准方块左键接管，客户端自算破坏进度、到阈值（3C3U 实战 0.7）直接发 STOP_DESTROY_BLOCK 包破坏；Grim 绕过四模式（无/Disabler/NewDisabler/时间校验，实战 NewDisabler：START 后补发 START(0,-999,0) 伪造包重指向服务端挖掘追踪 + DoubleBreak 补 STOP）、280ms 会话冷却、RetryTicks 重开会话、InstantMine 即时重破 STOP 流、自动切最快工具、暂停/取消快捷键、聊天提示（Notify）、进度色渲染；全部挖掘包带 accesswidener 取的 block-change sequence（与 BlockClicker 同款）；纯逻辑 `utils/SpeedMineLogic`，单测 `SpeedMineLogicTest`。刻意不移植：Hold/HoldTicks 换持回切、Desync、NoCollision、MineAir、EnderChest 按键、Multitask 并发体系）
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
  - ✅ 6.26 已完成：**自动偷取收紧为「只偷箱子」**（V6.25 实机翻车修复）。V6.25 沿用彗星端官方行为
    ——不判断开的是什么容器，只要 `InventoryEvent` 到达就偷，结果把用户自己**存在潜影盒里的**
    附魔金苹果/迅捷潜行书也一起取走了。现改为：必须确证「打开的是箱子方块（`ChestBlock`）」才偷，
    判断依据复用本模块「刚打开的是哪个方块」的准星判定（`TARGET_FRESH_TICKS` = 10 tick 时效），
    潜影盒 / 木桶 / 末影箱等一律不偷；**依据缺失或过期时保守不偷**（fail-safe，宁可漏偷）。
    ⚠️ 教训：移植别的模块时，凡涉及「动不动玩家物品」的行为，边界条件（对哪些容器生效）
      必须逐条确认后再上线，别默认照搬上游语义。
  - ✅ 6.27 已完成：**自动偷取累计统计**（用户要求：写本地文件、跨重启、**不自动清零**，长期累加）。
    设置页「自动偷取」开关正下方新增只读文字块，显示
    `您已使用本插件共计获取了 / N 个附魔金苹果 / M 迅捷潜行III`（若把「迅捷潜行最低等级」调低、
    确实偷到过 I/II 级书，才补出对应两行）。
    - 数据落 `fo-steal-stats.json`（游戏目录）：`{"version":1,"items":{物品ID:N},"books":{附魔ID:{"等级":N}}}`。
      键用 **物品 ID / 附魔 ID**（与显示文案解耦），附魔书按**等级分开计**，以后改文案/加统计项不用迁数据。
    - 纯逻辑 `ancient/core/AncientStealStats`（计数 / 展示文案 / 序列化，可单测）；
      文件读写 `ancient/core/StealStatsFile`（由 `AddonTemplate.onInitialize()` 在加载期读一次——
      用户可能一进游戏就打开设置看总数，那时模块还没被激活过）。
    - 只读展示控件：`utils/StealStatsSetting`（**不承载任何配置**，save/load 全空、不往配置里写键）
      + `AddonTemplate` 里注册 `SettingsWidgetFactory.registerCustomFactory`，用 `theme.verticalList()` 放多行标签。
      ⚠️ 设置名的左侧标签是 Meteor **框架自动加**的（取 `Setting.title`），工厂只需补右侧内容。
    - ⚠️ **记账口径 = 真的取到手才算**：`InvUtils.shiftClick()` 返回 void、不报成败，背包满时点击等于白点；
      直接在点击时记账会让累计数字虚高。实现是「点击时记下槽位原本内容 → 下一刻回读核对
      （槽位变空/变少才算取到）」，界面关掉来不及核对的那一次按乐观口径计入。
  - ⏳ 待做（用户可按需追加）：自动偷取的「只偷本模块搜索命中的古城箱子」更严格模式（当前按用户口径只判「是不是箱子」）
  - ✅ 6.28 已完成：**「应用种子」点一次就记住**（用户要求：不想每次开模块都重设）。
    `manualSeed` 原本只在内存里、模块构造时归零，所以每次启动游戏都得重点一次。
    现在点「应用种子」会写进 **`fo-ancient-seed.json`（全局一份）**，模块激活时自动带上；
    换种子 = 改「种子」输入框再点一次应用（覆盖保存）；**清空输入框再点应用 = 清除保存的种子**（关闭自动应用，
    原来这种情况只报「请先输入种子」，现在给了用户一个退出自动应用的出口）。
    - 为什么单独一个文件：`fo-ancient-chests.json` 有「换种子=换世界就整批清空」的生命周期
      （`clear(scope)` 连作用域种子一起删），「上次应用过的种子」是**输入**、不该被结果的清理牵连。
    - 持久化 `ancient/core/AncientSeedStore`（纯逻辑 toJson/fromJson 可单测 + 文件读写）。
    - ⚠️ 刻意**只影响搜索用的种子**，不动 `currentSeedGuess()`（记录种子校验 + 渲染过滤），
      避免"自动带种子"顺带触发清档/隐藏高亮等新副作用。
  - ⚠️ 全局一份种子的已知取舍：换服务器时保存的种子会跟着带过去（用户选的口径）。若将来想要
    「按服务器+维度各存一份」，改 `AncientSeedStore` 为 map 即可，数据格式已经带 version 字段。
  - ✅ 6.29 已完成：**累计统计改成独立可折叠分组 + 加"框"**（用户反馈 V6.27 那个文字块太丑、没对齐、没框）。
    - 位置：新建 `sgStats = settings.createGroup("累计统计")`，**建在 sgSteal 之后** → 渲染在「自动偷取」
      分组下方（即「初始延迟」下面）。⚠️ 分组按**创建顺序**渲染，位置靠创建时机决定。
    - 「能展开能关闭」不用自己写：Meteor 的 `SettingGroup` 就是用 `WSection` 渲染的（`DefaultSettingsWidgetFactory.group`
      → `theme.section(group.name, group.sectionExpanded)`），折叠状态存在 `SettingGroup.sectionExpanded`。
    - 「黑框」也用现成控件：factory 里不再堆裸标签，而是 `table.add(theme.section(抬头, true))` 拿 `WSection`
      当框（与「高亮」「自动偷取」同一种控件），抬头行当 section 标题、计数行当框内内容 → 左对齐 + 内边距 + 有框。
    - 纯逻辑配合拆分：`AncientStealStats.displayHeader()`（抬头，不含数字）/ `displayBody()`（计数行）/
      `displayLines()`（两者拼接，保留给测试与复用）。
    - ⚠️ 设置页里**设置名的左侧标签是 Meteor 框架自动加的**（`DefaultSettingsWidgetFactory.group` 里
      先 `table.add(theme.label(setting.title))` 再调工厂的 `Factory.create(WTable, Setting<?>)`，后者返回 void），
      所以工厂只能决定"右侧内容"，左侧标题躲不掉；这也是为什么设置名要起得短（当前叫「战果」）。
  - ✅ 6.30 已完成：**累计统计展示控件定稿**（V6.29 那版实机"卡成一团"）。
    - 失败原因（实机截图确认）：把 `WSection` 嵌在设置行里当框 —— `WSection` 的标题走**大号字体**
      （`TITLE_TEXT_SCALE`），直接盖住左侧标签「战果」，且数行挤在一起。
    - 定稿方案：`WTable` 两列 —— 第一列物品名、第二列**只读黑框**放数值，列宽自动对齐。
      只读框 = `theme.textBox(文本, CharFilter)`，过滤器 `(text, c) -> false` 一律拒绝输入
      → 得到与「高亮半径 [256]」「种子 [...]」**完全同款的原生框**，且点不进、改不动。
      首行仍是抬头「您已使用本插件共计获取了」（普通字号，占满一行）。
    - 纯逻辑配套：`AncientStealStats.displayRows()` 返回 `List<StatRow>(名称, 数值)`（数值为纯数字串），
      `displayHeader()` 抬头，`displayLines()` = 抬头 + "名称 数值"（供断言/将来复用）。
      ⚠️ 教训：**凡是能在设置页里用的控件，优先挑"框架已经在用的那种"**（分组=WSection、
      行内块=WTable、只读值=textBox+拒绝过滤器），不要自绘或塞大号字体的控件——看渲染效果的人只有用户，
      一次猜错就得多发一版。
  - ✅ 6.31 已完成：**累计统计去掉左标签 + 说明改悬停**（用户反馈：删掉「战果」左标签、其余内容左对齐，
    截图里的说明文案放到「您已使用本插件共计获取了」上悬停可看）。
    - 发现：`DefaultSettingsWidgetFactory.group` 调工厂**之前**已把左侧标题标签加进当前行
      （当前行当时只有这一个控件），且 `WTable.removeRow(i)` 是 public —— 工厂里可以
      `table.getRow(table.rowI()).get(0).widget()` 摘下标签（顺手拿走它的 `tooltip`，即设置 description），
      再 `removeRow` 整行删除，自己从零画 → 左标签彻底消失、内容顶到最左。
    - 抬头行补 `marginTop(6)` 对齐相邻行（框架给标题标签的也是 6）。
    - ⚠️ 修订 6.29 的结论："左侧标题躲不掉"其实躲得掉，只是要整行删掉重画（`Setting.title` 是 final、
      改不了，只能删行）。
  - ⚠️ 6.31 实机崩溃（打开设置页 `Ticking screen` → `IndexOutOfBoundsException: -1 for length 0`）：
      `WTable.removeRow(i)` 内部会把行游标 `rowI` 减一；「累计统计」是它分组的**第一个**设置，
      删掉第 0 行后 rowI = -1，工厂里下一句 `table.add` 走 `rows.get(-1)` 直接越界。
      修复（6.32）：删行后**必须补一次 `table.row()`** 把游标移回，框架在工厂返回后还会再调一次
      row()，正好恢复原节奏。教训：用框架的"删行"类 API 前先读源码确认游标/索引副作用。
  - ✅ 6.32 已完成：6.31 崩溃修复（见上条 ⚠️），无其他改动。
  - ✅ 6.8p1 已完成：**新增 FO 发包挖掘模块**（用户要求：移植 OpenHachimi 反编译源码里的
    Hachimi V2 AutoMine，3C3U PvP 用）。模块 `modules/FOPacketMine`：自动发包挖目标玩家的
    脚里(Burrow)/头顶(Face)/围城(Surround)方块，目标范围/挖掘范围/过滤模式（全部/仅黑名单/
    仅白名单，默认黑名单蜘蛛网）/检查支撑（只挖黑曜石基岩支撑位）/重复发包间隔均可配，
    HUD 显示目标名，Render3DEvent 高亮当前挖掘格。
    - 几何计算抽纯逻辑 `utils/PacketMineLogic`（Burrow=脚Y平面±0.25四角、Face=y+1.5平面、
      Surround=包围盒脚层地板格），单测 `PacketMineLogicTest` 9 用例锚死规则。
    - 挖掘动作走彗星端官方 `BlockUtils.breakBlock`（旋转+切槽+发包管线），**不复刻**上游
      BreakManager（依赖其私有 Mixin/Manager 体系）。
    - 刻意不移植三处（上游反编译语义不可证，模块头注释有记录）：Surround 空气位候选 /
      FriendSync（需全服玩家挖掘状态表，彗星端无此数据）/ 双格并发挖掘（改单会话重复发包）。
  - ⏳ 7.0 待做：**群系验证**自写（替代 cubiomes 的 `isViableAncientCity`），然后删掉那两个 Windows DLL
  - ✅ 6.8p2 已完成：**新增 FO 发包挖掘（SpeedMine 移植）+ 旧模块改名**（用户指正：6.8p1 的
    「FO 发包挖掘」实际移植的是上游 AutoMine（自动挖目标脚下方块），不是用户要的发包挖掘）。
    - 6.8p1 模块改名 **「FO 自动挖脚」**（`modules/FOAutoMine`，代码零变动）。
    - 新增 **「FO 发包挖掘」**（`modules/FOPacketMine` 重写）：移植 Hachimi V2 SpeedMine
      （源码类 BlockPosBoxMultitask，1083 行）。核心：拦截 AttackBlockEvent 接管方块 →
      客户端自算 `calcBlockBreakingDelta` 进度 → 到阈值（Speed，实战 0.7）直接发
      `STOP_DESTROY_BLOCK` 包破坏 + `cancelBlockBreaking()` 复位；自动切热栏最快工具；
      暂停/取消快捷键（KeybindSetting）；渲染红→绿进度色。默认参数照搬实战配置
      （Speed=0.7/Range=6/仅黑名单(火、重生锚)/不挥手/不转向/三个暂停条件全关）。
      纯逻辑 `utils/SpeedMineLogic`（进度判定/工具选择/颜色渐变），单测 `SpeedMineLogicTest` 9 用例。
    - ⚠️ 刻意不移植（模块头注释 + 本条记录）：Grim「NewDisabler」快速挖绕过、Desync、
      InstantMine/DoubleBreak 原版漏洞、NoReset/NoCollision、EnderChest 按键、
      Hold(1.21+ 持握)、Multitask 并发体系——这些依赖上游私有 Manager/Mixin 体系或需实机调试。
      Grim 服被拦时先调高「挖掘进度」到 0.85~0.95。
  - ✅ 6.8p3 已完成：**删除 FO 自动挖脚 + SpeedMine 参数补全对齐实战**（用户要求）。
    - 删除 `modules/FOAutoMine`、`utils/PacketMineLogic`、`PacketMineLogicTest`（Hachimi
      AutoMine 移植版整体移除，注册已注销——该模块是"自动挖目标脚下方块"，非用户所需）。
    - `FOPacketMine` 补齐 cheng.json SpeedMine 段漏掉的参数：**聊天提示**（Notify=true，
      破坏后聊天栏报坐标）；渲染色对齐实战 hex——StartSide=0x3f7e7ef5（青）、
      EndSide=0x066e6ee1（深青）、StartLine=EndLine=0xff9595ff（粉红），线框色独立可配
      （此前线框色是从面色派生的）。
  - ✅ 6.8p10 已完成：**修复双挖第二个方块挖不掉 / 单挖偶尔失败（会话调度对齐上游）**：
    - 根因一（双挖卡死）：镜像会话误调 tryBreak 每 tick 发 STOP。上游镜像只做 xs()（进度）+
      xu()（完成判定），**从不发 STOP**；镜像的 STOP 把共享的 280ms 冷却时钟与时间校验时钟
      每 tick 刷新 → 点第二个方块后新会话 START 被冷却永久卡死、时间校验永不放行。
      修复：镜像只推进度 + 完成计数（progress≥1 后 breakAttempts++ > MiningTicks 清除）。
    - 根因二（单挖偶尔失败）：主会话同时挂 tryBreak + checkDone（上游 xu 仅镜像专用），
      同 tick 双重 breakAttempts++（阈值语义减半）且 AutoSwitch 分支同 tick 双 STOP。
      修复：删除主会话的 checkDone 调用与方法（主会话完成=done；InstantMine 开启时保留
      done 会话，方块重生后秒补 STOP——上游 f/d 不重置语义）。
    - 根因三：tryBreak 用会话创建时定死的破坏面/无范围复查，转身/远离后 STOP 面非法被 Grim 拒。
      修复：tryBreak 实时重算 dir（上游 xt 每次 PlacementUtils.Fi 重算）+ 距离超 range 不发。
    - 其他：mine() 补上游 alH 守卫（DoubleBreak 开且请求位=镜像位 → 拒绝）；渲染对齐上游 aly
      （主会话 done 不画防空气位残留满格盒；镜像 !done 且与主会话同位去重不画）。
    - 排除项：上游 125ms list（STOP 去重）在可用源码中只写不读（消费方为未移植联动模块），
      挖掘路径无此门控，不移植。
  - ✅ 6.8p9 已完成：**修复 6.8p7 起的挖掘回弹（Grim 拒包）**（用户实测 6.8p5 完美、6.8p7 经常回弹，diff 两版+上游逐包比对定位）：
    - **根因：NewDisabler 伪造包 START(0,-999,0) 在 6.8p7 全量重写时误用裸 new（sequence=0）**。
      上游 xE 是 SequencedPacketCreator（`new PlayerActionC2SPacket(START, (0,-999,0), UP, n2)`，
      经 iQ() 取号）——伪造包必须带正确 block-change sequence，否则 Grim 不认伪造追踪包，
      后续 STOP 落回 vanilla 1.21.2+ 快挖防护 → 拒绝 = 回弹。6.8p5 用 sendDig（带号）所以完美。
      修复：改回 sendDig。
    - 次因：6.8p7 无条件挥手（实战 Swing=None）。新增「挥手」设置默认关，swing() 受其控制。
    - 排除项：进度累计的持错工具线性缩放误差方向偏保守（STOP 偏晚不偏早）；假破块门控
      （markBreak/Desync=SetAir，上游靠它挡住 STOP 重发）6.8p7 已正确实现；280ms 冷却、
      sequence 的 START/DoubleBreak/STOP 均与 6.8p5 一致。
  - ✅ 6.8p8 已完成：**静默换持（SwapMode.Screen 容器级换持）移植**（用户要求全面移植）：
    - 新设置「换持方式」（SwapActionMode：快捷栏/静默换持，手动切换时隐藏）。
    - 快捷栏 = 原 Hotbar 直切（InvUtils.swap，UpdateSelectedSlot）。
    - 静默换持 = 上游 SwapMode.Screen：遍历整个 ScreenHandler（playerScreenHandler
      覆盖背包 36 格+副手，打开容器时含容器全部槽位）找最快工具（选槽纯逻辑
      `SpeedMineLogic.fastestScreenSlot`，可单测），用
      `clickSlot(syncId, slotId, button, SWAP, player)` 容器点击包把工具与当前选中
      快捷栏位对调——不发 UpdateSelectedSlot（真·鬼手）。包序：SWAP 换上 → STOP → SWAP 换回，
      与直切一致；保持换持时存 ScreenHandler 槽位号/按钮/syncId，切回时校验 syncId
      环境守卫（上游 xy/xB，界面环境变了等下一 tick）。额外切换（AutoSwitch）分支同款支持。
    - ⚠️ 仍未移植：SwapMode.Pick（点击换持）；MainHand/Blink/AutoAnchor 联动不适用（Hachimi 专属）。
  - ✅ 6.8p7 已完成：**SpeedMine 全量移植**（用户要求"全部移植包括渲染"；版本号按用户明确要求
    沿用 6.8 系递进，不擅自升大版本）：
    - 渲染全套：盒子动画（随进度向中心收缩）+ 28 种中文缓动曲线枚举（utils/Easing，数学对齐上游）
      + 方块真实轮廓贴合 + tickDelta 帧间平滑。
    - 双会话 Multitask：主会话（左键/API 请求）+ DoubleBreak 镜像会话（1.0 速度第二进度线）；
      对外 mine(pos, priority) API 带优先级抢占。
    - 原版加速（Damage）模式：Mixin accessor 读写 currentBreakingProgress，
      原版进度 ≥ Speed 直接置满秒破；附属无冷却（清 blockBreakingCooldown）、
      不重置（换手恢复进度快照）、不中断（Mixin 拦截 cancelBlockBreaking）。
    - 碰撞穿透（NoCollision）：Mixin 取消 AbstractBlockState.getCollisionShape 对本人碰撞。
    - 末影箱按键 + PlaceRange/PlaceRotate（BlockUtils.place）。
    - 时序全项：MiningTicks/AutoSwitch/Hold/HoldTicks/Manual/MineAir/IgnoreBroken/StrictCheck/
      CheckGround/CheckFly（applyStateFlags 修正 1.21.11 离地 /5 惩罚）。
    - Mixin 基建：fo.mixins.json + fabric.mod.json "mixins" 声明 + 3 个 mixin 类。
    - ⚠️ SwapMode 仅 Hotbar；MainHand/Blink/AutoAnchor 联动不移植（Hachimi 专属）。
  - ✅ 6.8p6 已完成：**删除长按自动接管（bugfix）**。6.8p5 的"按住攻击键自动接管"存在按键状态卡死漏洞——
    按住左键时打开设置界面再松开，松键事件被界面吞掉，`attackKey.isPressed()` 永远为 true，
    模块不点击也自动挖掘（用户实测触发）。删除该逻辑，恢复纯点击接管；DoAttackEvent 从无条件
    cancel 改回仅有效目标 cancel（否则普通方块挖不动）。⚠️ 若未来重做"长按挖掘"，必须加：
    屏幕打开守卫 + 松键守卫，且默认关闭。
  - ✅ 6.8p5 已完成：**长按持续发包 + 全中文界面 + 假破块**（用户反馈三项）：
    - 长按：按住攻击键时空闲即接管准星方块（对齐上游按住=连续发包挖掘），会话期间每 tick
      `cancelBlockBreaking()` 压制原版挖掘，不会回落到原版手动破坏；DoAttackEvent 改为
      无条件 cancel（对齐上游 Packet 模式 aim() 无条件调用，无效目标交给原版长按路径）。
    - 全中文：GrimMode 枚举值改 无/旧版绕过/新版绕过/时间校验（原 Disabler/NewDisabler 英文）；
      形状模式改自建中文枚举 BoxMode（全部/仅线框/仅填充）映射 Meteor ShapeMode
      （其 Both/Lines/Sides 是英文）；聊天提示去掉 "px" 英文残留。
    - 新增「假破块」（上游 Desync=SetAir，实战开启）：发 STOP 后客户端本地 removeBlock，
      方块视觉瞬间消失。Desync 由此从"刻意不移植"清单移除。
    - 仍未移植：Hold/HoldTicks 换持回切、NoCollision、MineAir、EnderChest 按键、Multitask。
  - ✅ 6.8p4 已完成：**FOPacketMine 补全 Grim 绕过协议**（用户实测 3C3U 挖不掉——6.8p3
    只做了"进度到 0.7 发裸 STOP"，被 Grim fastbreak 拦截；通读上游 BlockPosBoxMultitask
    + BlockPosPlayerHelper 后把 Packet 模式的完整协议逐包移植）：
    - 新增「Grim绕过」设置（无/Disabler/NewDisabler/时间校验，默认 NewDisabler=实战值）：
      NewDisabler 在 START 后补发 START(0,-999,0) 伪造包把服务端/Grim 的挖掘位置追踪
      重指向假方块，真实 STOP 不再受 vanilla 1.21.2+ 快挖防护与 Grim fastbreak 约束；
      Disabler 在 START 前先发 STOP；时间校验（上游 Strict/Time 合并）STOP 前等够
      ceil(1/最大单tick进度)*50ms 真实时间（差 25ms 内放行）。
    - 新增「双重破坏」（DoubleBreak，实战 true）：会话开始补一个 STOP（原版双进度漏洞）。
    - 新增「重试等待」（RetryTicks，实战 10）：STOP 发出后方块未消失，超时重开整个会话；
      新增「即时重破」（InstantMine，实战 true）：破坏完成后每 tick 对原位置补发 STOP 流
      （方块被刷新回来立即再破），窗口=RetryTicks。
    - 280ms 会话冷却（上游 alP()，NewDisabler/时间校验生效）。
    - 所有挖掘包改带正确 block-change sequence（复用 fo.accesswidener 已有的
      getPendingUpdateManager/sequence 条目，与 BlockClicker 同款取号）——此前发的是
      sequence=0 裸包。
    - 纯逻辑新增 `SpeedMineLogic.inSessionCooldown` / `timeModeStopAllowed`，单测 +4 用例。
    - 刻意不移植（本版仍不做）：Hold/HoldTicks 换持回切、Desync、NoCollision、MineAir、
      EnderChest 按键、Multitask 并发体系。
  - 黄金回归测试：`AncientCityGoldenTest`（拼图块 + 箱子坐标 + LootTableSeed）与 `AncientLootEvaluatorTest`
    （113 箱掷骰结果逐箱锁死），两者都**不依赖黑盒**。
  - ⚠️ 已知与参考实现的偏差（我们按 MC 本体语义实现，是刻意修正）：参考实现的 `enchant_with_levels` 忽略 options
    用全量附魔、`enchant_randomly` 的标签不展开嵌套；修正后 113 箱里 21 箱的物品清单会不同。
    对照验证时可用 `AncientEnchantData.SourceMode.REFERENCE_COMPAT` 复刻它的偏差。
- 6.2 已删除 6.0 的 icehack-2 鞘翅套件（自动鞘翅飞行/自动不祥宝库/实时平均速度 三模块 + HUD），当前无 elytra 套件
- ⚠️ 6.33 是一次**走错路的改动**（同日发布、当天即被 6.34 取代，**请勿使用 6.33**）：
  当时依据 Meteor 的 `Pitch40` 模式与参考实现 `miles352/meteor-stashhunting-addon` 的 `Pitch40Util`，
  把巡航改成「快拉慢压俯仰曲线 (5.45/0.90) + 按需补烟花 + 自动调界」。错在两点：
  ① Pitch40Util 的 auto-firework / auto-bounds 是那个 addon **额外加的**，不属于飞法本身；
  ② 用户要的「P40」是 **Ying 原版的飞法**，而 Ying 原版巡航段**一颗烟花都不烧**。
- ✅ 6.34 已完成：**鞘翅采集巡航与进降落距离完全回到 Ying 原版**（用户提供 Ying 源码逐行核对后确认）。
  - ⚠️ **术语澄清（重要）**：用户说的「P40 / pitch40」= **Ying 原版那套飞法**（角度数值即彗星/Meteor 的
    54.77 / 37.72），**不是 40 度角**，也**不是** Pitch40Util 那套。
  - Ying 原版巡航段（`com.door.hunt` 的 `ElytraCollectorModule`，L837~866）：
    ```
    float pitch = climbing ? -54.77f : 37.72f;
    rotateTo(yaw, pitch);              // 由「俯仰速度」(默认 10 度/tick) 限速 → 对称过渡
    mc.options.forwardKey.setPressed(true);
    // ← 巡航段没有任何 useFirework()
    ```
    全模块 `useFirework()` 仅 3 处：**起飞段** + **无鞘翅紧急拉升**。**巡航段零烟花、零调界。**
    Ying 的 `min-height 180 / max-height 220 / rise-height 230 / pitch-speed 10 / firework-interval 2.0`
    与 FO 默认值完全一致——FO 本就是它的直接移植。
  - 6.34 改动：① 巡航俯仰回到 `rotateTo(yaw, climbing ? -巡航爬升角度 : 巡航滑翔角度)`；
    ② 删除 6.33 新增的 7 个设置（爬升抬头速度 / 滑翔下压速度 / 自动调界 / 调界高度间隔 / 巡航按需补烟花 /
    补烟花速度阈值 / 补烟花冷却）与 3 个纯逻辑类 + 3 个测试类；
    ③ `FlightApproachLogic.LANDING_HANDOFF_DISTANCE` 80 → **3**（Ying 原版就是 3）；
    ④ 「巡航爬升角度」保持**可见可调**（默认 54.77）——4.48 把它隐藏是纯粹的退步，不回退这一点。
  - ⚠️ **与「撞墙死掉」的关系（重要，别搞混）**：用户反馈的「采集完鞘翅立马起飞 → 撞墙 → 死掉」，其修复在
    `relaunchAfterShip()`（已降落且离降落点 > 2.5 格 → 先走回降落点 `LEAVE_SHIP` 修正起飞方向再起飞），
    **与 `LANDING_HANDOFF_DISTANCE` 无关**；6.34 保留该修复不动。将来若又出现撞墙，优先查这里而非降落距离。
  - ⚠️ 4.48 新增的「接近防撞塔 (`FlightApproachLogic`)」「安全复飞 (`FlightEscapeLogic`)」「低Y退出关 KillAura」
    属降落 / 安全侧，**不在本次回退范围内**，保持原样。
  - 测试：删除 23 个（6.33 新增的），`cd fo && gradle build` 共 **372 测试全绿**（0 failures / 0 errors）。
- ✅ 6.35 已完成：**「起飞前先回降落点」开放为可配设置**（用户要求，先讨论后实施）。
  - 背景核实：FO 里 `horizontalDistance(waypoints.landing) > 2.5` 有**两处**——`relaunchAfterShip()`
    （采集完成后起飞）与 `leaveShipNow()`（跳过没鞘翅的船）。⚠️ **Ying 原版只有后者**
    （`leaveShipNow` L1178）；`relaunchAfterShip()`（Ying L1152-1163）是**直接 `state = RISING` 起飞**。
    所以「采集完立马起飞撞墙死掉」的修复是 **FO 在 4.45 自己加的**，并不属于「对齐 Ying」。
  - 新增设置（默认分组）：`起飞前先回降落点`（Bool，默认**开**=保留修复）
    + `回降落点判定距离`（Double，默认 **2.5** 格，范围 0~20 / 滑条 0~10，可见性跟随开关）。
    两处共用同一个值；距离用**严格大于**判定（正好等于阈值 = 已在降落点，直接起飞）。
  - **刻意不动**（用户拍板）：`LEAVE_SHIP` 步骤的到位判定仍是 `reached(waypoints.landing.down(4), 2.5)`
    —— 它是**另一个量**（参考点是降落点下方 4 格的甲板站立点）；超时行为也不变
    （`relaunchAfterReturn=true` 采集完成超时 → 停止任务；`false` 跳过船超时 → 原地起飞）。
  - 纯逻辑：`ReturnPathLogic.shouldReturnBeforeTakeoff(...)` + `DEFAULT_RETURN_DISTANCE = 2.5`；
    模块侧收敛为 `wantsReturnToLanding()` 单点判定（两处调用）。
  - 测试：新增 7 个（开关关 / 未落地 / 无目标点 / 小于等于大于阈值 / 阈值 0 与 10 / 默认值 2.5），
    共 **379 测试全绿**。
  - ⚠️ 用法提醒：判定距离调大会让回位走更远，**30 秒超时可能不够而触发「停止任务」**；
    超时阈值这次刻意没做成设置（用户确认），需要时再提。
- ✅ 6.36 已完成：**删除 4.48 新增的「接近防撞塔」与「安全复飞」，这块完全回到 Ying 原版**
  （用户实机反馈「没有鞘翅的船会降落→起飞→回原地反复循环」后定位）。
  - 根因：`onFlying()` 里 **`if (onApproachSafety()) return;` 排在 `if (frameAirEmpty) { … leaveShipNow(); }` 之前**；
    而 `onApproachSafety()` 的 `shouldIntervene(hDist, mode)` = `hDist <= 200 || mode != NORMAL` ——
    **只要进入 200 格接近区就接管并 return**，于是「跳过该船」那段**永远执行不到**（船就在 200 格内）；
    同时它唯一的降落出口 `shouldHandoffToLanding()` 要求「框里有鞘翅」，空框时同样出不去 →
    只剩 `CLIMB ⇄ REAPPROACH` 振荡（表现出来就是"降落 ⇄ 起飞"），原地死循环，
    且该船永远进不了黑名单（`addBlacklist` 只在 `leaveShipNow` 里）。
  - ⚠️ Ying 对照：Ying 是「检查展示框 → **直接** `if (frameAirEmpty) { 跳过 }`」中间没有任何拦截；
    `onLanding` 与 Ying **逐行一致**（只差 1.21.1→1.21.11 的 `isFallFlying()`→`isGliding()` 改名）。
    即「防撞塔 / 安全复飞」是 4.48 独有的 FO 增量 → 本次全部移除。
  - 改动：① 删 `onApproachSafety()` / `onSafeEscape()` 两个方法及调用点；
    ② 删 `approachState` / `escapeState` 字段与重置点（onActivate、stopTask）；
    ③ 删 `utils/FlightApproachLogic`、`utils/FlightEscapeLogic` 与两个测试类；
    ④ 降落交接条件回到 Ying 的 3 格字面量。
  - ⚠️ **刻意保留的 1 处差异（已告知用户）**：降落交接条件保留了 FO 的
    `!climbing && frameAirChecked && !frameAirEmpty` 三个守卫（Ying 只有 `headFound && hDist <= 3`）。
    理由：`frameAirChecked` 防止"还没确认框里有没有鞘翅就降落"，是安全冗余；用户如要求可再去掉。
  - ⚠️ 「低Y退出」当时未动（FO 断开服务器 + 关 FOKillAura，Ying 只是停任务）——已于 **6.37 统一收敛**，见下。
  - 测试：删除 24 个（两个测试类），共 **355 测试全绿**。
- ✅ 6.37 已完成：**「停游戏」下线保护统一收敛**（用户要求：「低Y逻辑要停游戏！补给失败也要停游戏！」）
  - 新增私有 helper `emergencyLogout(warningMsg, disconnectReason, stopReason)`，固定顺序：
    **警告 → 关 FOKillAura → `disconnect()` 断开服务器退回主菜单 → `stopTask()` → `toggle()` 关闭本模块本身**。
    关本模块是用户本轮追加的要求（让角色下线瞬间不再残留任何采集器动作）。
  - 接入的 **5 个触发点**：
    ① 低Y（`onTick`，`lowYExit` + `lowYThreshold`）；
    ② **物资不足**（末影箱遍历完仍缺，原 `stopTask("物资不足 …")`）；
    ③ **背包无空位、无法补货**（原 `stopTask("背包无空位放潜影盒，无法补货 …")`）；
    ④ **末影箱已满、无法归还潜影盒**（原 `stopTask("末影箱已满，无法归还潜影盒 …")`）；
    ⑤ 末影箱与背包均无可用潜影盒（原本已下线，本次改为走同一 helper，行为一致）。
  - ⚠️ **刻意未改 1 处**：`背包无空位放潜影盒，无法存鞘翅` 仍是纯 `stopTask`——
    用户确认覆盖范围是「补给失败」，**存货失败不算补给失败**，故不下线。如要一并下线，说一声即可。
  - ⚠️ **务必不要按「对齐 Ying」回退**：Ying 原版低Y分支只有 `stopTask`（其源码注释写着
    「只停止任务，绝不关闭游戏」），本节的断开服务器 + 关模块是 **FO 刻意的安全增强、且是用户明确要求**。
  - ⚠️ 现有 `disconnect()` 用的是 `mc.player.networkHandler.getConnection().disconnect(...)`
    （断连 → 退回主菜单，客户端仍在跑）；`AutoLog` 模块用的是另一种写法
    `mc.player.networkHandler.onDisconnect(new DisconnectS2CPacket(...))`。本节沿用前者。
  - 测试：无新增（这两个分支都是 Minecraft 客户端强耦合的副作用路径，纯逻辑无法覆盖），
    `cd fo && gradle build --no-daemon` 共 **355 测试全绿**（0 failures / 0 errors）。

- ✅ 6.7 已完成：**新增「FO 自动搬运」**（移植 misaka StashMover 搬运端 + miku PearlBot 拉珍珠端，
  本地分支，未推 GitHub，等用户实机验证后再发版）。一个模块三种模式：
  - **搬运端（双号）**：misaka 状态机（开箱取物 → 发「拉我N」→ 等「收到N」→ 走到活板门上方
    投滞留珍珠（pitch 90°）→ 还多余珍珠 → 卸货进存储箱 → 自杀（默认 `/suicide --confirm`）/岩浆
    → 重生回待搬运箱继续）；三种待搬运方式（自动搜索/单箱子/区域框选）；双箱成对拉黑。
  - **拉珍珠端（双号）**：miku 珍珠大管家全套——珍珠点映射表（`StringMapSetting`，用户名→坐标）、
    右键活板门录入模式、告示牌识别（珍珠 = 告示牌正下方，已确认用户场地贴法）、关键字「拉我」、
    距离检查、Baritone 寻路、miku 交互工具点活板门、赶路超时、可选回出发点。
  - **三号模式**：三个互斥角色开关（1号搬运端 / 2号待搬运箱端 / 3号存储箱端），三个号各开一个；
    1 号在两区纯拉珍珠往返、**不自杀**，卡住只报警暂停；珍珠箱全路线一个，<4 精确补到 16（逐颗
    右键补、不整堆搬、不还珍珠）；另有「只开1号搬运端」开关（2/3 号交给 ZenithProxy 插件）。
  - **私信协议**：`/m`（可切 `/tell`）+「拉我N」/「收到N」（N 内存计数每次+1，镜像配对）；
    确认判定 = 来自预期搭档 + 含「确认关键字」（默认“收到”），**带数字必须匹配 N，不带也放行**
    （兼容 ZenithProxy/miku 风格回复）；「等待确认」关掉则发完直接走流程。
  - **修掉 misaka 的 3 个 bug**：① 等确认无超时（改 300 tick 超时 + 重发 3 次报警）；
    ② 珍珠滞留检测不认主人（改只认 `owner == 自己`）；③ 投掷前「准星指方块就等待」在标准水柱
    结构下永不通过（已删除，直接 pitch 90° 投）。
  - **新增工具**：`utils/BlockClicker`（miku `BaritoneUtil.clickBlock` 流程：面中心 + 挥手 + 旋转 +
    带序列号 `PlayerInteractBlockC2SPacket`（走 accesswidener 的 `PendingUpdateManager`）+ 视角归位）；
    `utils/StringMapSetting`（miku 同款键值表格设置）；`utils/PeerMessageLogic`、`utils/StashPearlLogic`、
    `utils/StashMoveLogic`、`utils/StashStats`、`utils/StashStatsSetting`（全部纯逻辑可单测）。
    复用仓库已有 `utils/Rotation`、`utils/RotationManager`（V4.35 为杀戮光环移植的 miku 同款）。
  - **统计**：只记本次在线（趟数 + 潜影盒数），关模块不清零、断线/退服（GameLeftEvent）清零、
    设置页「重置」真按钮手动清零（前端重做，不用古城那套只读框）。
  - ⚠️ **双号搬运的硬性前提**：重生点必须设在待搬运箱旁边（箱子搜索半径最大 20 格，
    超出会一直报警）；活板门正下方 1 格必须是水。
  - ⚠️ 刻意未做（用户要求测试后再评估）：活板门点击后的翻转验证、N 计数器持久化、
    背包可堆叠空间判定、私信白名单/请求冷却、自动暂停其他模块。
  - 测试：新增 39 个（私信解析/配对 13、珍珠补货 7、分区与双箱配对 9、统计 5、界面文本 5），
    全仓 **50 个测试套件 394 个测试全绿**（0 failures / 0 errors）。
- ✅ 6.7p1 已修复：**「只拿潜影盒」同时作用于卸货 + 珍珠永不入库**（用户实机报的 bug）。
  - 卸货过滤 `StashMoveLogic.shouldUnload(itemId, onlyShulker)`：末影珍珠任何时候都不存
    （三号模式珍珠是随身回程票；双号模式还珍珠失败也不能存掉）；开了「只拿潜影盒」时只存潜影盒，
    非潜影盒留在背包。
  - 卸货完成判定由「背包全空」改为「**没有可卸物品**」——否则带珍珠时会永远卡在卸货状态反复开箱。
  - 单测 +4（珍珠永不入库 / 只拿潜影盒 / 普通模式 / 空 ID），全仓 **50 套件 398 测试全绿**。
  - ⚠️ **版本号命名（用户口径）**：bug 修复小版本用 `6.7p1` 这种「pN」写法（不带 V、不用点号补丁号）；
    Fabric 的 Version.parse 实测接受 `6.7p1`（解析结果 friendly string 仍是 6.7p1），
    产物名 `FO-6.7p1-1.21.11.jar`。
  - ⚠️ 已知取舍：双号模式自杀时，背包里留下的非潜影盒（食物/工具）会掉在原地；
    若服务器不是死亡不掉落，可后续加「自杀前把剩余物品先存箱」开关。
- ✅ 6.7p2 已修复：**三号模式投珍珠的两处逻辑问题**（用户实机报的 bug）。
  - ① **投掷前先检测气泡柱**：用严格判定 `ownPearlInColumn(water)`（XZ 误差 ≤0.9 格、Y 在水柱内、
    owner==自己）——柱里已有自己的珍珠就直接跳过投掷去发「拉我N」，不再多投一颗。
    同一判定也用于投后校验（旧的 Y±8 宽松范围会把落在活板门上的珍珠也算成功）。双号模式同样加了这层检查
    （`dualWalkWater` 一进门就查，有珍珠连路都不用走）。
  - ② **不再走到水上**：`pearlStandSpot()` 在活板门 2 格半径内找「脚下实心、身体两格空气」的站位
    （排除活板门格与水格），Baritone `moveToPrecise` 过去；投掷前若活板门关着先用 BlockClicker 点开
    （之前完全没开活板门，珍珠会落在活板门上）。
  - ③ **潜行 + 瞄准 + 后退**（用户口径）：投掷时按住 shift，视线用 `FacingLogic.yawPitchTo` 瞄准
    **气泡柱方块中心**（不是原来的 pitch=90 垂直向下），投完立刻按 S 后退 10 tick，防止脚踩气泡柱触发珍珠。
    关闭模块/卡住时 `releaseMovementKeys()` 统一释放前进/潜行/后退键。
  - ⚠️ 注意：柱里没珍珠时仍然是「先投（充值回程票）再发拉我N」——这是刻意顺序，
    否则被拉走后本区没有可释放的珍珠，下一趟回不来。
  - 寻路统一加 `isPathing()` 守卫，避免每 tick 重置 Baritone 目标。
- ✅ 6.7p3 已修复：**三号模式补珍珠不补 + 投掷流程按用户口径重做**。
  - ① **容器识别支持潜影盒**（用户场景：珍珠箱是「放下的潜影盒」，珍珠一组组散放在里面）：
    `containerHandler()` 同时认 `GenericContainerScreenHandler`（箱子/木桶/漏斗/发射器）与
    `ShulkerBoxScreenHandler`；纯逻辑 `StashMoveLogic.isSupportedContainer(...)` 可单测。
    旧实现只认前者 → 潜影盒界面永远判定“没打开箱子”，补货逻辑从不执行——这就是「不补珍珠」的根因。
  - ② **三号状态机重排**（用户口径：投珍珠 → 取货/存货 → 才发私信）：
    `SETTLE（校正站位）→ RESTOCK（补货）→ THROW（投掷子流程）→ LOOT/UNLOAD → SEND → WAIT_TELEPORT`；
    到达新区域与模块启动都从 SETTLE 开始。
  - ③ **投掷子流程 5 步**（THROW 内部 `throwSub` 0~6）：
    0 检气泡柱（已有自己的珍珠直接跳过）；1 校正站位（不在气泡柱/活板门上，远离时先寻路回来）；
    2 检查活板门，关着就用 BlockClicker 点开；3 按住 shift + pitch 90 朝柱心按前进键，
    `isStalled` 连续 3 tick 位移 <0.05 格 = 走不动（30 tick 上限）；4 投掷（正上方 ≤0.5 格时
    pitch 90 垂直向下，否则 `FacingLogic.yawPitchTo` 瞄准柱心）；5 松 shift + 按 S 退回原位；
    6 校验柱内自有珍珠，失败重试整个流程最多 3 次 → 报警暂停。
  - ⚠️ 用户明确要求：**双号模式本轮不改**，先只修三号模式（容器识别是公共函数，双号顺带支持潜影盒）。
  - 测试：新增 2 个（容器识别 / 走不动判定），全仓 **50 套件 400 测试全绿**。
- ✅ 6.7p4 已修复：**投掷前先脱困、慢速走近、按距离后退、多时机校验珍珠、补货重试**（用户实机反馈）。
  - ① **先脱困（关键顺序修正）**：之前是「先走出水柱 → 再开活板门」，但人常被关闭的活板门压住/泡在
    气泡柱里，走不动就死循环，只能手动开活板门。现在 `settlePosition` 检测「人在活板门格 / 脚下是
    活板门 / 站在水里」→ **先点开活板门 → 再朝最近安全格游/走（水里同时按跳）**，60 tick 出不来就报警。
    找不到可站实心格时不再静默放行，直接报警。
  - ② **走近改慢速点按**：每 3 tick 只按 1 tick 前进键（原来每 tick 按住，容易冲进柱子）；
    走近途中有一步踩进水里 → 立即停手转后退重试（返回码 2）。
  - ③ **后退按距离判定**：从「固定 10 tick」改为「退到离柱心 ≥2.5 格」（`needMoreBackoff` 纯逻辑），
    60 tick 只是上限；在水里额外按跳帮助脱离。
  - ④ **校验时机从一次性改成三处**：投完校验（原有）+ **发私信前再校验**（珍珠丢了就回去重投，
    不发拉我N）+ **取货/存货期间每 5 秒校验**（丢了立刻中断、回去重投）。
    另加「干活期间位置突跳 >32 格 = 意外传送」→ 报警暂停。
  - ⑤ **补珍珠**：取消 64 格距离闸（珍珠不够就寻路过去补）；开新容器前先关掉残留界面；
    容器里读到 0 颗珍珠时**重开确认 2 次**（排除槽位同步延迟/开错容器），并把
    「界面类型 / 槽位总数 / 找到的珍珠槽位」打到调试输出。
  - 测试：新增 1 个（后退距离判定），全仓 **50 套件 401 测试全绿**。
- ✅ 6.7p5 已修复：**离箱子稍远就点不开 / 所有交互不转头 / 待搬运箱没有高亮**（用户实机反馈）。
  - ① **寻路每 tick 重设（点不开的根因）**：自动搜索 / 单箱 / 存储箱 / 珍珠箱等分支原来**每 tick 裸调
    `moveTo`**，Baritone 每 tick 重算路径 → 人原地不动，箱子离远一点就永远走不到。新增 `pathTo()`
    守卫：同目标正在走就不重复下发；走 240 tick 未到才重设（最多 2 次）；Baritone 自行停止时隔
    20 tick 再试一次。
  - ② **开箱前必须站够近**：新增 `approachForOpen()`，眼睛到方块中心 ≤ 3.5 格才允许点击（服务端对
    交互有距离校验，隔空点击会被静默丢弃）；`openChestAt()` 连续 8 次点不开直接报警，不再无限沉默重试。
    站在方块正上/正下方（XZ 重合 ≤1.2 格）时直接放行，避免 GoalXZ 判定“已到达”却因高度差死循环。
  - ③ **真正转头**：`BlockClicker` 的旋转从 miku 的 `RotationManager`（内部是 Meteor 的
    `Rotations.setCamRotation`，只影响第三人称身体朝向，第一人称完全不转）改成 Meteor 的
    `Rotations.rotate(yaw, pitch, priority, callback)`：移动包先带目标朝向，回调里发交互包，客户端
    会真的把视角转过去再归位。
  - ④ **点面选择修正**：原来按“眼睛→方块中心”向量取最大分量，站在箱子斜上方会选**顶面**，而箱子
    顶上接了漏斗时顶面命中点落在漏斗里 → 服务端拒绝。现在 `StashMoveLogic.pickClickFace()` 优先
    水平四个侧面，顶面被占或眼睛不高于方块中心时绝不选顶面，**永远不选底面**。
  - ⑤ **待搬运箱高亮补齐**：渲染新增「待搬运箱坐标」（品红 `255,0,200,160`）、「自动/框选扫到的
    队列箱子」（淡品红 `Sides`）、「当前正在操作的箱子」（白色 `Both` 全亮）；原来只画了活板门/珍珠箱/
    存储箱/拉黑箱，待搬运箱坐标压根没画。
  - 测试：新增 4 个（选面优先级），全仓 **50 套件 405 测试全绿**。
- ✅ 6.7p6 已修复：**「气泡柱周围找不到可站的实心格」误报**（用户实机反馈，明明旁边能站人）。
  - ① **可站判定改用碰撞箱**：原来用 `isAir()` 判本格/头顶，草、雪层、藤蔓、告示牌、按钮、
    打开的活板门这类「非空气但无碰撞」方块全被判“站不了”。现在 `isStandable()` 用
    `getCollisionShape().isEmpty()`：本格与头顶无碰撞即可通行、脚下有碰撞支撑且不含流体。
  - ② **搜索半径 2 → 4，Y 范围 -1..1 → -2..3**：按「圈」由近到远扫（半径 1→4），找到最近一圈就停。
    原来珍珠房稍宽一点（水柱 3 格宽、岸在第 3 圈，或岸在水柱上一层）就必然判“没有实心格”。
  - ③ **每区记住安全格**：`zoneSafeSpot` 缓存（三号模式两区各自记住），第一次找到后后续传送复用，
    结构没变就不用重搜。
  - ④ **脱困更稳**：目标格每 20 tick 重算（玩家在水里会浮沉）；目标在斜上方时同时按跳；
    脱困超时 60 → 120 tick；找不到格时先潜行后退 40 tick 兜底再报警；失败时把
    「玩家格/脚下方块/是否在水里/活板门开关状态/水格方块」打到调试输出。
  - 测试：全仓 **50 套件 405 测试全绿**（本次无新增纯逻辑，均为 Minecraft 世界相关判定）。
- ✅ 6.7p7 已修复：**三号模式到达后不等、节奏太赶、全程不转头 + 调试输出看不到东西**（用户实机反馈）。
  - ① **到达先等 2 秒**（根因：用户从 6.7 建模块起就反馈「被拉回来直接就开始开活板门/脱离」）。
    新增状态 `TrioState.ARRIVE_WAIT`（插在 SETTLE 之前）：`trioWaitTeleport` 检测到传送后不再直接进 SETTLE，
    而是先记 `arriveWaitStart`，40 tick（2 秒）内只做「松键 + 停寻路」，什么都不判断。
    原因写清楚：刚落地时区块还没同步，脚下方块可能还是空气，此时判「站没站稳/活板门开没开」必然全错。
  - ② **节奏整体放宽**（用户口径「一步一步来，不要那么紧凑」）：新增节奏常量
    `PACE_SETTLE_DONE=10` / `PACE_STEP=6`（投掷子步骤，原 2）/ `PACE_TRAPDOOR_CLICK=14`（等方块状态同步）/
    `PACE_THROW_DONE=10` / `PACE_RESTOCK_DONE=10`；走近点按 `%3 → %4`、上限 `45 → 60` tick、
    投掷校验超时 `60 → 80` tick。
  - ③ **全程转头**：新增 `faceColumn()`，每 tick 用 Meteor 的 `Rotations.rotate(yaw, pitch, 5)` 把视角
    锁在本区气泡柱上（SETTLE / THROW 全流程）。⚠️ `throwSub==4`（正在投掷）那一 tick **跳过**，
    否则低优先级旋转会盖掉 `doThrowPearl` 的高优先级瞄准。
    `doThrowPearl` 也从 miku 的 `RotationManager` 换成 `Rotations.rotate(..., 60, callback)`，
    回调里再发交互包（顺序：移动包带朝向 → 交互包），与 6.7p5 修 `BlockClicker` 的思路一致。
  - ④ **活板门做成显式可确认的一步**：关着就点（用户口径：大部分时间都是关的），点完等 14 tick 再看结果，
    连续 8 次打不开才报警，报警文案带「眼睛距」便于判断是不是坐标填错/被方块挡住。
  - ⑤ **调试输出重做**（用户要求，下次实机测试要开）：
    - `setTrioState()` / `setThrowSub()` 两个包装器接管所有状态与子步骤切换，
      **一变就打印**（`[三号][状态] A → B（原因）`、`[三号][投掷] 子步骤 2 → 3 开活板门（原因）`）。
    - `trioHeartbeat()`：每 2 秒一条摘要（区域/状态/子步骤/珍珠数/距柱心/活板门开关/是否在水里），
      内容不变则每 10 秒补一条「仍在…」，既能看到进度又不会刷屏。
    - `enterStuck()` 增加一行现场快照（区域/状态/子步骤/珍珠数/原因）。
    - `openChestAt()` 每次点击打印目标、第几次、眼睛距（对比 `OPEN_REACH=3.5`）、当前界面类型。
  - ⑥ **修「有时候不会打开箱子」的一个真实 bug**：`openAttempts`（连点失败计数）原来**只在容器真的打开时**
    才归零，换到下一个箱子仍继续累加 —— 连着几个箱子各点两三次没开就凑够 8 次，误报
    「连续 8 次都打不开箱子」并永久暂停，表现正是「有时候不给我开箱」。现在**换目标即归零**
    （`if (!pos.equals(openChest)) openAttempts = 0`）。
  - ⑦ 顺手修 UI 文案：提示里的 `请先设置SOURCE/STORE的活板门位置` 是英文枚举名，改走 `zoneName()`（中文）。
  - 测试：全仓 **50 套件 405 测试全绿**（本次改动全是 Minecraft 客户端时序/世界耦合路径，
    无新增纯逻辑；断言未变，故测试数不变）。
  - ⚠️ 待用户实机确认：取货/存货「有时不开箱」是否只剩上面的计数 bug（用户表示要自己测定位）。
- ✅ 6.7p8 已修复：**启动位置随机导致的三趟冤枉路 + 跨区补珍珠死结**（用户指出「脱离→确认站位→补珍珠」这个固定顺序不对）。
  - **问题**：原状态机把「先站到柱子边」当成第一步，而 1 号启动时可能站在 2 号点、3 号点、
    珍珠箱旁或两区之外。表现是「柱子 → 珍珠箱 → 柱子」白跑两趟；更糟的是珍珠箱全路线只有一个，
    若它不在本区，补珍珠就得跨区（而跨区只能靠珍珠超穿，等于死结）。
  - ① **`SETTLE` 降级成纯脱困**：拆出 `escapeIfNeeded()`，只处理「人在气泡柱里/站在活板门格上」；
    「走去柱子」从 `SETTLE` 移除，改由 `THROW` 的第 1 子步骤（`settlePosition`）负责。
    这样启动时人不在柱子旁就一秒通过，不会被硬拽过去。
  - ② **补珍珠提到最前**：新顺序 `判区 → 脱困（可秒过）→ 补珍珠 → 补投 → 取货/存货 → 发拉我`。
    启动与被拉回来走同一条链，区别只是后者前面多一个 `ARRIVE_WAIT`（2 秒）。
  - ③ **珍珠箱按区配置**：新增「待搬运箱区珍珠箱」「存储箱区珍珠箱」，原「珍珠箱位置」
    保留名字不变（只把描述改成「通用的珍珠箱」），作为两个分区都没填时的回退项。
    ⚠️ **刻意不改名**：Meteor 用「设置名」当配置键，改名会让老配置里已存的坐标读不出来。
    取值走 `zonePearlChest()`：本区专用 → 通用 → `ORIGIN`（真没配就报警，不再静默寻路到 0,0,0）。
    ⚠️ 用途：补珍珠永远在本区内完成，彻底消除「人在 2 号点、珍珠箱在 3 号点」的跨区死结。
  - ④ **启动距离门槛**：新增「启动最远距离」（默认 64 格）。判区前先算
    `nearestZoneDistanceXZ()`（离两个区锚点的最近水平距离）：超过上限**报警暂停**、让人把 1 号走到
    2 号点或 3 号点再开模块；没超就自己走过去（用户口径「近的自己走、远的报警」）。
  - ⑤ **看门狗修正**：长距离寻路（走去珍珠箱/柱子）原来会被「2 分钟无进展」误杀。
    现在「正在寻路 + 这一 tick 真的移动了」也算进展（Baritone 原地打转不算，照样报警）。
  - ⑥ **修 `onActivate` 绕过距离检查**：原来它直接 `trioZone = initialZone()`，把 ④ 的门槛绕过去了；
    现在启动时保持 `UNKNOWN`，判区与门槛统一在第一次 tick 里做。
  - ⑦ `settlePosition` 找不到岸边时不再无限重试，改为 `dumpStandSpotFailure` + 报警。
  - ⚠️ 行为变化（需用户知悉）：只填了「珍珠箱位置」（通用）而它又不在本区时，补珍珠仍会尝试走过去，
    走不到由看门狗报警——**建议两个区各填一个**（用户已确认会这么配）。
  - 测试：全仓 **50 套件 405 测试全绿**（改动均为时序/世界耦合路径，无新增纯逻辑）。

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

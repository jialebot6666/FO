# AGENTS.md

## 项目

FO — Minecraft **Meteor Client (Fabric) 生存辅助全自动插件**（MC 1.21.11，模块统一带 `FO ` 前缀、全汉化）。
- 代码根：`fo/`（包 `com.fo.addon`）；`ref-src/` 是本机外部参考源码目录，被 `.gitignore` 排除、不入库
- 当前版本：`mod-version = 6.7p28`（见 `fo/gradle/libs.versions.toml`），产物名 = `FO-6.7p28-1.21.11.jar`（详见「命名规范」）
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
  - ⏳ 7.0 待做：**群系验证**自写（替代 cubiomes 的 `isViableAncientCity`），然后删掉那两个 Windows DLL
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
- ✅ 6.7p9 已修复：**「卡脚」**（用户实机反馈；对照 ZenithProxy futa 插件 `AntiStuck` 的做法重做）。
  - **参考来源**：用户给的 `appfuta.zip` 是 GraalVM **编译产物**（不是源码），但包名 `com.github.futa` 可查，
    公开源码在 `xperiavnc/futa-plugin`（旧版，缺 StashMove/PearlDrop，但 `AntiStuck`/`PearlPlusModule` 在）。
    从中确认两条关键做法：**① 从不用手动按方向键走路，全程 `BARITONE.pathTo(...)` + 完成回调；
    ② 用「位置校正包频率」判定卡脚**（5 秒 ≥6 次 `ServerboundAcceptTeleportationPacket` → 去最近安全点）。
  - ① **新增防卡机制** `TeleportFloodDetector`（纯逻辑可单测）：统计
    `PlayerPositionLookS2CPacket`（服务端位置校正）在 5 秒窗口内的次数，≥6 判定卡脚。
    ⚠️ 收包回调在**网络线程**，内部只 `record` + 置 `antiStuckPending`，真正处理放 `onTick`（客户端线程）。
    触发后：松键 → 停寻路 → 等 20 tick 稳住 → 在 5 格内找最近的 `isStandable` 格子精确寻路过去 →
    冷却 5 秒；同时打印 `我卡住了！我在：x,y,z`（照抄参考实现给用户看的定位信息）。
  - ② **删掉「潜行 + 按住前进走到走不动」**（`walkToColumnCenter`，卡脚主因：顶着箱子/水柱边沿一直推，
    服务端不停把位置拉回来）。投掷第 3 子步骤改为**只做校验**：在水里 → 退回；离柱心 >3 格 → 回第 1 步
    用寻路重新站位；否则直接进投掷。全局不再有「按住前进键硬顶」的走位。
  - ③ 脱困（`escapeIfNeeded`）加了**周期性按跳**：不在水里但被一格高边沿卡住时，原地推是推不上去的，
    每 12 tick 跳 3 tick。
  - ④ `walkToPrecise()` 增加 20 秒超时（同一目标），超时直接报警带坐标，不再无限重试。
  - 测试：新增 `TeleportFloodDetectorTest` 6 个用例（阈值/窗口裁剪/边界/清空），
    全仓 **51 套件 418 测试全绿**。
  - ⚠️ 未能核实：ZenithProxy 的 `StashMoveModule` / `PearlDrop` 只在编译产物里（公开仓库是旧版），
    本次只对齐了「防卡」与「不手动推着走」两条可确证的做法，其余仍按用户口述的流程。
- ✅ 6.7p10 已修复：**「静默转头」→ 真实转头 + 1 格级站位不再用寻路**（用户实机反馈 + 指定参考实现）。
  - **参考来源**：`RSTminecraft/RustElytraClient`（原 `c4r0d/RustElytraClient`，仓库已改名）的
    `SupplyTask.lookAt()`：`player.setYaw(yaw); player.setPitch(pitch);` 直接改真实朝向、
    **不恢复**，然后 `delay(3)` 再交互。我们在 6.7p5/p7 用的 Meteor `Rotations.rotate(...)` 是
    **静默转头**——发包前改朝向、发包后立刻 `resetPreRotation`，画面上只有一帧闪动，
    用户看到的「跟没转一样」就是这个原因。
  - ① **换掉所有静默转头**：新增 `turnTo(yaw,pitch)` / `lookAt(Vec3d)` / `realClick(BlockPos)`。
    `faceColumn()`、`stepToward()`、`sneakStepToEdge()`、`doThrowPearl()` 全部改成直接设玩家朝向。
  - ② **交互顺序**：新增 `afterMoveAction` + `SendMovementPacketsEvent.Post` 钩子——
    在 TickEvent.Pre 里把朝向真的设过去 → 本 tick 移动包自然带上它 → 移动包发完再发交互包。
    这样既保证「服务端先看到朝向再收到交互」，又不会把视角弹回去。
  - ③ `BlockClicker` 新增 `clickBlockNoRotate(pos[, side], hand)`（只发包、不碰视角），
    原 `clickBlock(..., rotate=true)` 仍保留给其他调用方；本模块 4 处点击全部改走 `realClick`。
  - ④ **1 格级站位不再调寻路**（用户口径：巴里顿寻路会卡脚 → 珍珠投掷失败）：
    新增 `throwStandSpot()`（柱子四个邻格按碰撞箱筛，活板门 `FACING` 那一侧降权，
    全不行才退回 `pearlStandSpot`）+ `stepToward()`（面向格中心、按 2 tick 松 2 tick、
    卡住先跳、有超时）。`settlePosition` 只在**距离 > 6 格**时才用 Baritone，
    6 格内一律短按走；`sneakStepToEdge` 负责最后 1 格的潜行贴边（蹲着不会掉进柱子）。
  - 测试：全仓 **51 套件 418 测试全绿**（本轮改动是朝向/走位的实时耦合逻辑，无新增纯逻辑）。
  - ⚠️ 已知取舍：真实转头会**接管玩家视角**（每 tick 强行设 yaw/pitch），
    开着模块时手动转视角会被拽回——这是用户明确要的效果。关模块后立即恢复自由。
- ✅ 6.7p11 已修复：**按 PearlDrop 反编译源码校准投掷判定**（用户提供 `comet-stashmove-spec.zip`）。
  - **材料说明**：该包里 `PearlDrop` 是 CFR 反编译的完整源码（`[实证-JAR]`，等价原实现）；
    `StashMove` 只有骨架（SPEC 自己标了 G1–G6 缺口，明令不许填"合理默认值"）→ 只借鉴前者。
  - ① **投掷成功判定改成「背包珍珠数下降」**（PearlDrop 的行为指纹）：
    `pearlCountBefore = countPearls()` → 投完 `countPearls() < pearlCountBefore` 即成功。
    ⚠️ 原来用 `ownPearlInColumn()`（扫客户端实体 + 判 owner），实体同步一慢就误判成"没投出去"
    → 重投 → 一次少两颗。现在实体检测降级为**事后核验/日志**，不再参与成败判定。
  - ② **居中判定**（PearlDrop APPROACH）：抽成纯逻辑 `StashMoveLogic.nextCenterStreak` /
    `readyToThrow`——必须**「没位移」且「离柱心 <1.2 格」连续 6 tick**才允许投。
    只判"走不动"会在被箱子腿/门框挡住时误判成到边缘，然后投偏。
  - ③ **站位候选试两层高度**（PearlDrop findRim）：先 `trapdoorY` 再 `trapdoorY+1`，
    低层优先——覆盖用户反馈的「落在活板门上 / 卡在门沿」。
  - ④ **瞄腔底而不是瞄水面**（PearlDrop pitchToSoulSand）：新增 `columnAimPoint()`，
    从水面往下走到第一格非水方块（灵魂沙）再算 pitch，角度更垂直、不易被柱壁吃掉。
  - ⑤ **阈值/超时统一**：`TRIO_CENTER_DIST=1.2`、`TRIO_CENTER_STALL_TICKS=6`、
    `TRIO_THROW_TIMEOUT=40`、`TRIO_MAX_THROW_RETRIES=3`、`TRIO_APPROACH_TIMEOUT=120`、
    `TRIO_RETREAT_TIMEOUT=80`；**退回距离 2.5 → 1.0 格**（PearlDrop retreatDistance）。
  - ⚠️ **刻意不抄的一条**：PearlDrop 在投之前会把**开着**的活板门**关掉**（`closeOpenTrapdoor=true`）。
    用户明确指出「门关着珍珠就进不去柱子」，且其实机做法是**开着投**。本条保持现状（开→投），
    并在代码注释里标注为「刻意偏离」，避免以后有人"对齐参考实现"给改回去。
  - 测试：新增 `StashMoveLogicTest` 里 4 个用例（居中计数/被挡不算/移动清零/6 tick 才投），
    全仓 **51 套件 422 测试全绿**。
- ✅ 6.7p12 已修复：**活板门点不开 / 补珍珠丢珍珠 / 站位不在正对面 + 后退松 Shift**（用户实机反馈 + 截图）。
  - ① **bug1 活板门点不开**：点击改走**真实玩家交互路径** —— `realClick` 转头后用
    `mc.crosshairTarget`（准星 raycast）+ `mc.interactionManager.interactBlock(...)`，
    命中不中才退回手搓包。原因有二：手搓包的命中点是「中心+面方向×0.5」正好落在方块边界，
    部分服务端判无效交互；手搓包**不做客户端预测**，门开了客户端状态不更新 → 下一轮又点一次
    → 把刚开的门关回去（截图里同一行刷 53 次就是这种抖动）。
    另按用户口径加「**连点 6 次没开 → 先退开 25 tick 换角度再来**」，最多 2 轮，仍失败才报警。
  - ② **bug2 补珍珠丢珍珠**：原流程「放回余量」和「关界面」在**同一个 tick**，服务端可能先关界面，
    光标上那堆直接掉地上；更隐蔽的是箱里那堆刚好等于需要量时光标本来就是空的，
    最后那次「放回」会把整堆**重新拿起来**再关界面 → 丢失。
    现在拆成显式子步骤 `restockPhase`（拿取→逐颗→放回→确认光标空→才关界面），
    新增 `cursorEmpty()/cursorPearlCount()/putbackSlot()`；`need==0` 的早退只在 phase==0 时生效
    （补货中途不能跳走，光标上还拿着珍珠）；界面被外部关掉时重置子步骤并告警。
  - ③ **bug3 站位不在正对面**：新增 `openLidSide()` —— 直接读开着的活板门**碰撞箱**，
    门板是 3/16 厚的竖板，包围盒能看出它贴在自己那一格的哪一侧（maxX≈1→东……）。
    `throwStandSpot` 改为：**门板对面优先（不加惩罚）**，垂直两侧次之（+100），
    门板那一侧最后（+1000）。原理（用户截图确认）：门板立在柱子一格的某一侧，
    站对面贴到边缘也碰不到它；站侧面时按住 Shift 前贴，人物碰撞箱（0.6 宽）会顶到门板
    → 走不到位 → 投偏。原 `FACING` 降权逻辑废弃（碰撞箱才是事实）。
  - ④ **后退全程按 Shift**（用户口径）：`doThrowPearl` 投完不再 `releaseSneak()`，
    `backOffToSafe()` 开头改为 `sneakForThrow()` 保持蹲姿，退到离柱心 ≥1.0 格、
    校验完成后由 `finishTrioThrow()` 统一松开。
  - 测试：全仓 **51 套件 422 测试全绿**（改动为交互/走位实时耦合，无新增纯逻辑）。
- ✅ 6.7p13 已修复：**把 6.7p7~p12 修的所有 bug 适配到双号搬运端与拉珍珠端**（用户确认：双号珍珠房结构和 1 号一样，活板门靠墙、门板占一侧；2/3 号一起加固）。
  - 采用「**共用工具箱**」而非复制：抽出一份模式无关的投掷子流程，两种模式各自驱动，
    逻辑只有一份，以后修一处两边同生效。双号的「借珍珠/还珍珠/自杀回重生点」主链**一行没动**。
  - **阶段0（先修外溢回归）**：① `doThrowPearl` 之前去掉 `releaseSneak()` + 设 `backoffTicks`，
    导致双号投完一直蹲着 + 莫名后退 → 现在 `doThrowPearl` 只负责「转头 + 投」，
    收尾（后退/松键）交回调用方（trio 补 `backoffTicks`，dual 补 `releaseSneak`）；
    ② 「寻路中算进展」的看门狗从 `tickTrioMover` 上移到 `onTick` 顶层，双号长距离寻路不再被误杀。
  - **阶段2（抽共享投掷子流程）**：新增 `ThrowSeq` 枚举 + `tickThrowSequence(stand, onSuccess)`，
    把 1 号验证过的「站正对面→开活板门→贴边居中→瞄腔底投→珍珠数下降判定→蹲着后退」整条链参数化。
    `trioThrow` 变薄壳，`dualThrow` 也调同一份；删掉双号旧的 `prepareThrowPosition`/`dualVerify`/
    `MoverState.VERIFY`/`throwAttempts` 字段（数量判定已进共享子流程，成功后双号直接 PUT_BACK）。
    `faceColumn`→`faceColumnAt(stand)`、`backOffToSafe`→`backOffToSafe(water)` 参数化；
    `setThrowSub` 前缀去掉「三号」（两个模式共用）。
  - **阶段3（拉珍珠端加固）**：`tickPuller`（双号 PEARL + 三人 2/3 号共用）点活板门
    改为「真实交互 + **门状态翻转确认 + 最多 3 次重试**」，3 次都不翻转也发 ack（别让 1 号干等）；
    防卡 `TeleportFloodDetector` 覆盖从 `isMoverRole()` 扩到 `isMoverRole() || isPullerRole()`。
  - **附带**：StashMover 日志去坐标（无政府服防泄露），新增 `NoCoordinatesInLogsTest`
    防回归（只查自动搬运模块的日志语句不含绝对坐标）。
  - 测试：全仓 **52 套件 423 测试全绿**（本轮是重构 + 行为对齐，无新增纯逻辑函数）。
  - ⚠️ 待用户实机确认：取货/存货「有时不开箱」是否只剩上面的计数 bug（用户表示要自己测定位）。
- ✅ 6.7p14 已完成：**双号搬运端改成「按路线走」**（用户实机反馈「拉回存储区后不储存、反而寻路回去」，
  方案经讨论后确认：取整堆珍珠 + 保留还珍珠，自杀延时必须 ≥600 tick）。
  - **根因**：① 收到「收到N」就进 `WALK_WATER` 并**当场**走向存储区活板门 —— 此时人还在待搬运箱区，
    存储区区块没加载 → `pearlStandSpot` 必然返回 null → 把活板门当归宿点开始跨图寻路；
    被珍珠拉过去后 Baritone 旧路径没停（`dualWalkWater` 是裸调 `moveToPrecise`），人顺着旧路往回走。
    对比三号的 `trioWaitTeleport`（识别落点突跳 → 停路 → ARRIVE_WAIT 2 秒），双号三件事一件都没有。
    ② 卸货排在投珍珠之后，投掷链路任何一环不过，`WALK_STORE` 永远轮不到 —— 表现就是「不储存」。
  - **新路线（与三号同构，双号专用状态）**：
    `LOOT → SEND → WAIT_ACK → WAIT_TELEPORT → ARRIVE_WAIT → SETTLE → UNLOAD → TAKE_PEARL
    → THROW → PUT_BACK → CONFIRM_PEARL → SUICIDE_PREP → DEAD_WAIT → LOOT`
    （删掉 `WALK_WATER` / `WALK_STORE` 两个状态与 `dualWalkWater` / `dualWalkStore` 两个方法；
    双号方向固定：传送=存储区、自杀=源区，所以**不需要**三号那套判区）
  - **WAIT_TELEPORT**：松键 + 停路，`teleportJumped(...,32)` 识别落地；超时（新增设置
    「等待被拉超时」，默认 **100 tick = 5 秒**，可调 40~400）就**原样重发**「拉我N」——
    新增 `resendPullRequest`，**N 不递增**（`sendPullRequest` 会 ++，重发会让搭档按新数字再点一次门、
    多触一颗珍珠），最多 3 次后报警。
  - **ARRIVE_WAIT / SETTLE**：落地先静默 40 tick（复用 `ARRIVE_WAIT_TICKS`）等区块；
    `SETTLE` 只做「离开气泡柱」（`dualSettle`：在水里/站在活板门上就走到 `pearlStandSpot`），
    **不把人硬拽到柱子旁**——卸货不需要站位。
  - **CONFIRM_PEARL（回程票）**：投完 + 还完余量后，连续 `PEARL_CONFIRM_TICKS=6` tick 在腔室里
    检测到自己的珍珠才算确认（实体同步有延迟，一帧不算）；100 tick 没看到就重投，最多 3 次。
  - **自杀门槛**：`自杀延时` 设置下限锁死 **600**（默认 660，3C3U 上低于 600 珍珠会被吞掉），
    计时起点改为 **确认成功那一 tick**；`pearlConfirmedTick<=0` 时**一律不许自杀**（退回重投）。
    删除原「投掷珍珠后延时自杀」开关（默认 false，等于投完立刻死，与 600 tick 要求冲突）。
    纯逻辑 `StashMoveLogic.canSuicide(...)` / `pearlConfirmed(...)` 可单测。
  - **附带**：`dualUnload` 补「存储箱未设置」校验；`dualPutBack` 补「珍珠箱未设置」校验；
    重生后 cooldown 20 → 40 tick 等区块；新增 6 个单测。
  - 测试：全仓 **52 套件 429 测试全绿**。
  - ⚠️ 已知取舍：`finalTrip`（最后一趟）仍是卸完货直接关模块，**不给腔室补回程票**；
    下次开局需要人工确认腔室里还有没有自己的珍珠（或用已有那颗）。
- ✅ 6.7p16 已完成：**拉珍珠请求安全 + 开箱交互兜底 + 双箱搭档命中**。
  本版合并了「未发布的 p15」（同一台机器上另一窗口做的，jar 造过但没入库），并修掉三处：
  双号白名单改成**必填**、双箱另一半也认命中、修正测试套件数。主状态机（双号/三号）未改。
  - ① **请求关键字改为正文开头匹配**（p15）：`PeerMessageLogic.matchRequestKeyword`；
    「不要拉我」「别拉我」「我先拉我一下」不再误触发。确认消息仍用原来的宽松匹配。
  - ② **拉珍珠白名单 + 冷却**（p15，白名单语义在 p16 收紧）：新增「拉珍珠白名单」与「拉珍珠冷却」
    （默认 40 tick）。⚠️ **p16 起双号拉珍珠端必须填白名单**，留空 = 拒绝所有请求并每 100 tick 提醒一次
    （判据 `PeerMessageLogic.pullGateOpen`）；三号模式仍由「1 号搬运端」锁定，白名单可空。
    p15 原实现里双号 `expected` 传空、白名单空 → 任何人发「拉我N」都会被接受，
    真正拦人的只有「珍珠点映射表查不到」——这就是 p16 要收紧的原因。
  - ③ **realClick 改用真实朝向 raycast**（p15）：不再依赖上一帧的 `mc.crosshairTarget`；
    `mc.player.raycast(OPEN_REACH + 1.0, 1.0F, false)` 命中目标时走 `interactBlock`（有客户端预测），
    避开手搓包「命中点在方块边界、门状态不预测」的坑；手搓兜底命中点向方块内收 0.001 格（`0.499`）。
  - ④ **双箱搭档也算命中**（p16）：新增 `chestPartner(pos)`，射线/准星打在**另一半**时同样走真实交互。
    旧写法只认记录的那半块，人站到搭档侧时每次点击都会掉到手搓包兜底（没有客户端预测）。
    `blacklistChest` 也改成复用 `chestPartner`，不再复制一份偏移算法。
  - 测试：p15 新增 8 个（请求起点/白名单/搭档/冷却），p16 再加 2 个（准入闸门：三号靠 1 号、双号必须白名单），
    全仓 **52 套件 437→439 测试全绿**（p15 说明里写的「53 套件」是笔误：新增测试都在 `PeerMessageLogicTest` 里，套件数不变）。
  - 已知取舍不变：finalTrip 仍不补回程票；自杀仍会掉非潜影盒；N 计数仍只存在内存。
- ✅ 6.7p17 已完成：**双号入口判区 + 自家仓库永不进待搬运队列**（用户实机反馈：在存储区开模块会寻路去源区取货）。
  - ① **`ZONE_CHECK` 入口**：`onActivate` 不再写死 `moverState = LOOT`，先判「我在哪一侧」再决定从路线哪一段进场。
    不判区的后果：自动搜索会在脚下把存储箱当待搬运箱搬走；单箱 / 框选则直接寻路去源区。
  - ② **判据与三号同源**：单箱 = `目标箱子位置`、框选 = `pos1/pos2` 中心 → 直接复用
    `StashMoveLogic.nearerZone` **双锚点取近**（与三号同一个函数，不共用设置项，避免读不出老配置）；
    自动搜索没有源区锚点（语义是「在重生点脚下搜箱子」）→ 退化 `StashMoveLogic.storeZoneByDistance`
    （离存储箱 / 珍珠箱 / 活板门最近 ≤ 半径）。新增设置「存储区判定半径」默认 **32**（8~128），
    ⚠️ 只在自动搜索模式可见且生效；单箱 / 框选严格走双锚点，不看这个值。
  - ③ **在存储区开局**：直接进 `ARRIVE_WAIT(2s) → SETTLE(只脱困) → UNLOAD → TAKE_PEARL → THROW
    → PUT_BACK → CONFIRM_PEARL → SUICIDE`；腔室已有自己的珍珠时投掷步骤自动跳过（不多烧一颗）。
  - ④ **硬保险**：`nextFromQueue` 扫出的队列执行 `removeIf(isStoreSideChest)` —— 存储箱 / 珍珠箱
    及其**双箱搭档**永远不进待搬运队列。判区只管「走哪一段」，这份名单管「哪些箱子永远不能碰」，
    两件事互不依赖，判区失误也不会把自家仓库搬走。
  - 测试：新增 4 个（半径边界含等号 / 锚点未配按源区 / 半径 0 / 双锚点取近），**52 套件 443 测试全绿**。
- ✅ 6.7p18 已完成：**投珍珠站位修到「正前方」+ 双号投掷改「投→确认→还」+ 三人没珍珠不再卡死 + 两个默认值**。
  - ① **「站不到正前方」的根因**（用户长期反馈）：站位发生在开门**之前**，
    `openLidSide` 读到的是平铺薄板 → 返回 null → 只按「离得近」挑一格，没有正前方概念；
    挑出来的格子还写进 `throwStandCache` 并**永久短路**，门开了也不重算。
    修法：`throwStandSpot` 在门关着时只给 `pearlStandSpot` 的临时落脚点（先脱离气泡柱），
    门开后才按 `lid.getOpposite()` 取正对面；**删掉 `throwStandCache`**（每次现算，不过几次方块查询）。
    `tickThrowSequence` 的子步骤 2 改成「确认门已开 → **再 settle 一次纠到正前方**」，
    12 秒没到位算一次重走，最多 3 次（`FRONT_ALIGN_MAX_TRIES`），超了报警而不是硬投。
  - ② **双号投掷顺序改为「取珍珠 → 投 → 确认腔室 → 还余量 → 自杀」**（用户口径：成功之后再放回）。
    旧顺序「投→还→确认」在确认失败时珍珠已还回箱子，得再跑一趟珍珠箱；确认失败现在直接回
    `TAKE_PEARL`（背包还有珍珠会自动跳过开箱直接重投）。还完余量后用 `afterPutBack()` 分流：
    已确认→自杀，未确认→回去补确认（没票绝不自杀）。
  - ③ **三人模式没珍珠不再卡死**：`trioThrow` 的 `NO_PEARL` 从 `enterStuck` 改成回 `RESTOCK` 补货。
  - ④ **自杀延时**：默认 660 → **1200**，范围 **0~2000**（去掉 600 硬夹；3C3U 实测 660 仍会吞珍珠）。
    `canSuicide` 改为三参。⚠️ 设 0 = 确认完立刻死，必掉珍珠。
  - ⑤ **「待搬运方式」默认 AUTO → 单箱子**（双号）。⚠️ 只影响新建配置/重置；老配置里存的是 AUTO，需手动切一次。
  - 测试：`canSuicide` 相关 4 条按新规则重写（含延时 0/负值），全仓 **52 套件 443 测试全绿**。
- ✅ 6.7p19 已修复：**双号被拉到活板门下面出不来**（用户实机：搭档点完门是**关着**的）。
  - 根因：`dualSettle` 是 p14 单独写的一套，只有 `pearlStandSpot` + `walkToPrecise`，
    **从来不去开活板门**。人被拉进 1×1 气泡柱、头顶门是关的，气泡柱持续上顶 + Baritone 在水里
    来回作废路径，表现就是贴着门底反复浮沉，300 tick 后报警。
  - 修法：`dualSettle` 直接复用三号的 `settlePosition` —— 人在柱子里且门关着**先点开**，
    近距离用短按前进 + 跳（水里一直按 + 跳），走不动才交给寻路；整体超时 300 tick。
    `inColumn` 判定同时去掉「站在门顶上」（站在门顶不该开门，走下去就行）。
  - 三号本来就走 `settlePosition`，本次未改；`dualSettle` 里那段自写的 `pearlStandSpot + walkToPrecise` 删除。
  - 测试：无新增纯逻辑（全是世界耦合路径），全仓 **52 套件 443 测试全绿**。
- ✅ 6.7p20 已修复：**拉珍珠端远距离赶路会挖穿地形、一路挖到基岩层**（用户实机）。
  - 根因：`tickPuller` 只有一句 `moveTo(pullPos, true)` = Baritone **GoalXZ（只看 XZ、不看 Y）**；
    而 Baritone 的 `allowBreak` 默认是**开**的，为了到那个 XZ 会挖穿挡路的方块，
    在洞穴系统里就可能一路挖到基岩层。更别扭的是它「到没到」只看 XZ，
    而我们的点击判定是 3D 的 `distSq <= 16` —— 两个判定不同源，会出现「寻路说到了、点击说没到」的来回抖。
  - 修法：① `IPathManager` 新增 `moveToNear(pos, range)` → `GoalNear(pos, 2)`（Y 参与判定）；
    ② 新增 `setAllowBreak(boolean)`（反射改 Baritone 的 allowBreak，`resetPull` / `onDeactivate` 恢复）；
    ③ 点击判定换成 `StashPearlLogic.nearPearlPoint`（水平 ≤2.5 且 |ΔY| ≤2.5），与目标同一套尺度；
    ④ 新增设置「**赶路不挖掘**」默认开（路线本来就得挖一段的可以关掉）。
  - ⚠️ 已知取舍：路被挡死时会**超时放弃**并提示，而不是挖穿人家基地。
  - 测试：新增 3 条 `nearPearlPoint` 断言，全仓 **52 套件 446 测试全绿**。
- ✅ 6.7p21 已修复：**三个 P0（只拿潜影盒误拉黑 / 三号仓库没保护 / 古城偷取越界）**。
  - ① **「只拿潜影盒」把普通物资箱当空箱拉黑**：`takeFromContainer()` 原来返回 boolean，
    「箱空」和「箱里有东西但都不符合过滤」都是 `false`，调用方一律当成「已搬空」→ `blacklistChest`
    → 双号每趟重复「开箱→关箱拉黑」，三号直接误报「待搬运箱区已搬空」。
    改成三态 `StashMoveLogic.TakeOutcome`：`TAKEN / EMPTY / NO_MATCH`。
    `NO_MATCH` **不拉黑**，只进 `skippedUntil`（10 秒后可重试），并在 30 秒一条的提示里说明
    「N 个箱子不符合只拿潜影盒」（`warnFiltered` 顺带 `progress()`，不会被 2 分钟看门狗误杀）。
    ⚠️ 语义要点：**只有 EMPTY 才是「搬空」**，NO_MATCH 是「这个箱子我不要」。
  - ② **三号模式自家仓库没纳入保护**：`isStoreSideChest` 原来只比双号的 `storeChest` / `pearlChest`。
    现在 `protectedChestAnchors()` 收全双号 + 三号的 6 个锚点（`trioStoreChest` / `trioPearlChest` /
    `sourcePearlChest` / `storePearlChest`），每个锚点再展开双箱搭档，统一走
    `StashMoveLogic.isProtectedChest`。修的是「框选把本区珍珠箱当物资箱搬空 → 补货报『珍珠箱里没有珍珠』」。
  - ③ **古城自动偷取对任何箱子生效**：新增设置「**只偷搜索命中的箱子**」（默认**开**）。
    判定 `AncientChestStore.isSearchHit(scope, x, z, 3)` = 命中记录 **或** 已开名单，水平半径 3 格
    （双箱两半可能只记了一半；开箱瞬间记录会被移除，靠已开名单兜住同一 tick 的容器同步事件）。
    顺手修掉「玩家开过的任何 ChestBlock 都写进已开名单」——现在只有命中箱才 `markOpened`，
    `opened` 不再无界膨胀，也不会把自家箱子误判成命中。没有搜索记录时聊天栏提示一次。
    ⚠️ 想恢复旧行为（任何箱子都偷）把该开关关掉即可。
  - 测试：新增 7 条（取货三态 3 + 仓库保护 1 + 命中判定 3），全仓 **52 套件 453 测试全绿**。
- ✅ 6.7p22 已修复：**1 号搬运端「投掷/检测向两旁扩展」**（用户实机：珍珠两旁有 1 格障碍时会从侧面投）。
  - ① **门板朝向判错（4 个朝向错 2 个）**：`openLidSide` 按 `maxX→东 / minX→西 / maxZ→南 / minZ→北`
    顺序判；门板贴南/北时它横跨整个 X，`maxX` 同样是 1.0 → 命中「东」，正对面被算成垂直的侧面。
    改为先找**薄轴**（厚 0.1875）再判方向：`StashMoveLogic.thinPanelSide(minX,minZ,maxX,maxZ)`（可单测）；
    形状不是「一薄一满」的竖板时返回 `UNKNOWN` → `openLidSide` 返回 null → **拒绝投掷**，不再猜一侧。
  - ② **正前方站不了就退侧面**：旧实现打分挑侧面（门板侧 +1000 / 垂直两侧 +100），最后还会退回
    `pearlStandSpot` 的环形搜索。新增 `SettlePurpose{ESCAPE, THROW}`：脱困阶段允许就近站，
    **投掷阶段只返回正前方那一格**（`frontStandSpot`），站不了就 null → 重走 3 次后报警停下。
    `sneakStepToEdge` 起投前再校验一次「人就在正前方那一格」（`FRONT_CELL_TOLERANCE = 0.45`），
    不在就返回 3 → 回子步骤 2 重站。⚠️ **行为变化**：正前方被实心方块挡住时会停下报警，
    不再从侧面凑合投（用户要求「只在正对面」）。
  - ③ **珍珠检测 ±0.9 太宽**：落在旁边方块上的珍珠也算「柱内」（回程票没进去却以为成功）。
    收紧到 ±0.55（1×1 半宽 0.5 + 0.05 余量），逻辑抽到 `StashMoveLogic.pearlInColumn`（可单测）。
  - ⚠️ 「告示牌」说明（**已被 6.7p24 取代**）：p22 当时**不读告示牌**，正对面只按「门板的对侧」算；
    p24 起告示牌是投掷侧的**第一优先级来源**，详见下一条。
  - 测试：新增 5 条（门板四向 / 平放与异常 / 珍珠柱内 / 正前方贴边不误退 / 南北向正前方），全仓 **52 套件 458 测试全绿**。
- ✅ 6.7p23 已修复：**1 号搬运端跨层取货会「向下挖」**（用户实机：待搬运箱在搬运端上方 2 格，寻路却一路往下挖/往下走，自动搬运直接卡死）。
  - 根因：`approachForOpen` 一律 `pathTo(pos, true)` = Baritone **GoalXZ**，判定只有 `x == goal.x && z == goal.z`，
    **Y 完全不参与**。当箱子和人不在同一层（拉珍珠返回的落点常比箱子低一层）时，「到那个 XZ」在任意一层都算达成，
    而 `allowBreak` 默认开 → A* 在「找台阶上去」和「在脚下挖一条通到那个 XZ 的路」之间挑便宜的那条，
    通常都是后者，于是表现成一路向下挖、向下走，永远上不了箱子那一层。
  - 修法：新增 `StashMoveLogic.sameFloorForPath(feetY, targetY)`（|ΔY| ≤ 1 算同层）。同层继续用 GoalXZ；
    **跨层改用 `moveToNear(pos, 1)` = GoalNear(pos,1)**（纯 3D 球，站在下面两层不算到达）。
    ⚠️ 不能用 `GoalGetToBlock` 顶替：它的判定对负 dy 有 `dy+1` 补偿，站在箱子正下方 **2 格**、XZ 对齐时
    也算「到达」，「挖到箱子正下方」依然能满足目标。
  - `pathTo` 增加目标类型参数（`PathGoal.XZ / NEAR`）：同坐标从 XZ 切成 3D 时也会重新下发
    （原来会被「同目标不重复下发」的守卫吞掉）；新增 `clearPathTarget()` 统一清坐标 + 类型（6 处调用点）。
  - `trioRestock` 的「20 格外走去珍珠箱」同样改走 `pathToContainer`（珍珠箱在另一层时症状一样）。
  - 删掉 `approachForOpen` 里「XZ 距离 ≤1.2 就直接允许点击」的短路：它唯一能通过的场景是
    「XZ 重合、但垂直差 >3.3 格」（隔着楼板硬点）。跨层改走 3D 目标后不再需要，同层本来也不会死循环。
  - 跨层接近 15 秒还没走到会提示「请确认有台阶/梯子能到那一层（不会为了凑坐标向下挖）」，不再无声空转。
  - 测试：新增 2 条 `sameFloorForPath`（±1 同层 / ±2 跨层），全仓 **52 套件 460 测试全绿**。
- ✅ 6.7p24 已修复：**投掷侧不读告示牌**（用户实机：p22 在门关着时只能退回环形搜索，站到南/北侧投）。
  - 用户场地铁律：① 活板门**正上方 1~3 格内必定有告示牌**；② 人站在**活板门与告示牌的正对面**投掷；
    ③ 活板门放置朝向决定它朝哪一侧（门朝西 → 人站东侧投）。
  - 根因：p22 的 `throwStandSpot` 只认 `openLidSide`（开启后的**门板碰撞箱**）。门关着时它必然返回 null，
    投掷阶段就直接判「没有站位」→ 早期版本退回 `pearlStandSpot` 环形搜索（挑到南/北侧），
    p22 改成拒绝侧站后又变成「门关着就永远站不好」。**告示牌本来在门关着时也能读**，却一行没读。
  - 修法：新增 `throwFrontSide`，三个来源按优先级 `StashMoveLogic.pickThrowSide` 合成：
    ① **告示牌牌面朝向**（`signThrowSide`：扫 `trapdoor.up(1..3)`，先正上方再四邻；
    用 `AbstractSignBlock.getRotationDegrees` + `Direction.fromHorizontalDegrees`，立牌/墙牌/挂牌/墙挂牌通用）；
    ② **活板门 FACING**（原版门板恒在 FACING 对侧，所以站 FACING 侧 = 站门板对面，门关着也能读）；
    ③ **开启后的门板**（p22 的几何口径，取对面）。
    **物理否决**：选中的一侧若正好是门板所在侧（告示牌贴反），自动改成门板对侧 —— 否则人会被门板顶住。
  - `throwStandSpot` / `sneakStepToEdge` / 超时文案 / `dumpStandSpotFailure` 全部改用 `throwFrontSide`；
    调试输出新增一行 `[朝向] 告示牌=X 门FACING=Y 门板=Z → 站 W 侧`（只在结论变化时打印）。
  - 测试：新增 6 条（三来源一致 / 门关着靠告示牌 / 退回 FACING / 门板物理否决 / 只剩门板 / 对侧换算），
    全仓 **52 套件 466 测试全绿**。
- ✅ 6.7p25 已修复：**寻路会走到箱子正上方**（用户实机：p23 之后人爬到箱子上/走下去）。
  - 用户口径：① **当前位置能开就直接开**；② 要寻路就**走到能开就停**；③ **绝不走到箱子正上方**。
  - 根因：p22 `GoalXZ` 只认 XZ（往下挖）；p23 虽换 3D，但目标是**箱子方块本身**，
    `GoalNear(箱子,1)` 把「站在箱子正上方」（dy=1）也算到达 —— 两条都不符合口径。
    p23 的 `sameFloorForPath` / `PathGoal.XZ` / `pathToContainer` 已被本版取代并删除。
  - 修法：新增**搬运专用**的 `utils/StashChestSpotLogic`（**刻意不复用挖沙的 StandSpotLogic**）：
    水平 1→2→3 圈 × {chestY, chestY+1}，**排除箱子自身那一列**；排序 = 有视线 > 离得近 > 同层。
    模块侧 `findChestApproach` 用 `isStandable` 过滤 + 从落脚点眼睛位置 raycast（COLLIDER）判视线，
    结果按箱子缓存；区块没加载/箱子已消失时不写「无落脚点」缓存（防止把珍珠箱永久拉黑）。
  - `approachChest` 三态 `IN_RANGE / MOVING / NO_SPOT`：能开 → `stopApproachPath()`（停路+松键）再开；
    不能开 → 走向**落脚空气格**（近处 PRECISE、>8 格 NEAR）；四周没有落脚点 → 单箱模式报警暂停，
    搜索/框选模式拉黑换下一个。30 秒走不到同样跳过（单箱继续试）。
  - 选箱子优先有视线：`nextFromQueue` 向后看 4 个，挑「落脚点能直视箱子」的那个，没有才退第一个。
  - 统计：每趟卸货完成（双号 `onUnloadDone` / 三号 `trioUnload`）额外输出 `本次共 X 趟 · Y 个潜影盒`。
  - 测试：新增 `StashChestSpotLogicTest` 6 条（候选不含箱子列 / 圈数与总数 / 无重复 / 视线优先 /
    近的优先 / 同层优先），删除已被取代的 2 条 `sameFloorForPath`，全仓 **53 套件 470 测试全绿**。
- ✅ 6.7p26：**双号模式同步性审计**（用户要求确认 p21~p25 是否只修了三号）。
  - 结论：p21~p25 的修复都落在**双号与三号共用的方法**里（`takeFromContainer` / `isStoreSideChest` /
    `tickThrowSequence` + `settlePosition` + `sneakStepToEdge` + `throwFrontSide` / `goOpenChest` /
    `nextFromQueue`），双号已同步；古城偷取那条属于独立模块，与双号/三号无关。
  - 审计中发现 **1 处双号专属漏洞已修**：`dualUnload` 原来直连 `openChestAt(store)`，
    而 p25 起 `openChestAt` 只在「已经够得着」时点击（不再负责走过去）。被拉到存储区后
    只要离存储箱 >3.5 格，双号就会原地空转（三号走 `goOpenChest` 没这个问题）。
    现在改成 `goOpenChest(store)`，与取货/珍珠箱同一条路径。
  - 顺带把 `approachChest` 的「没落脚点/超时」判定从「按箱子模式（单箱/搜索）」改成
    「按是否队列箱子」：队列箱子跳过换下一个，固定目标（存储箱/珍珠箱/单箱）报警并继续，
    避免把存储箱拉黑或误伤。
  - 测试：无新增纯逻辑，全仓 **53 套件 470 测试全绿**。
- ✅ 6.7p27 新增：**末影箱中转**（双号 + 三号 + 只开1号，默认**开启**）。
  - 用户口径：末影箱预先放在待取货箱区和存储区附近；取货装满后直接存进去，到存储区再取出来 ——
    单趟多搬 **27 个潜影盒**；不需要寻路（距离足够近）。
  - 检测：以玩家脚所在格为中心、`末影箱搜索半径`（默认 6）扫立方体取最近一台；
    命中缓存复用（每 tick 校验方块还在），未命中缓存 20 tick，避免每 tick 扫 13³。
  - 触发：`dualLoot`/`trioLoot` 检测到**背包装满** → `ENDER_PUT`（存）→ 回 `LOOT` 继续取；
    `dualUnload`/`trioUnload` 卸空后若本趟存过 → `ENDER_TAKE`（取）→ 回 `UNLOAD` 再卸一轮。
    一趟只存一次、只取一次（`enderPutDone` / `enderTakeDone`）。
  - 容量：存到末影箱 **27 格全满**就停手，剩余继续背着走到存储区；取货要求背包有空位
    （正常流程先卸空，36 格 ≥ 27 格）。
  - 界面识别：`GenericContainerScreenHandler.getInventory() instanceof EnderChestInventory` ——
    普通箱子也是 27 格，只看槽位数会认错，防止把货倒进普通箱子。
  - 降级：附近没有末影箱 / 打不开（5 次）/ 15 秒超时 → 本趟跳过中转，不影响正常搬运；
    趟末「存了没取」会提示（货留在末影箱，下一趟继续）。
  - 珍珠永不存入：`StashMoveLogic.shouldStoreInEnder`（只排除末影珍珠）。
  - 测试：新增 2 条（珍珠不存 / 普通战利品可存），全仓 **53 套件 472 测试全绿**。
- ✅ 6.7p28 已修复：**开箱距离口径与服务端不一致 → 点了没反应**（用户实机：眼距 3.33 点击无效）。
  - 根因：`OPEN_REACH = 3.5` 量的是「眼睛 → 方块**中心**」；服务端
    `PlayerEntity.canInteractWithBlockAt` 量的是「眼睛 → 方块**包围盒**」。3.33 在反作弊严格的服已超限，
    服务端静默拒绝 → 表现为反复「点击第 N 次」，最后报「连续 8 次打不开」。
  - 修法：新增设置「开箱距离」（默认 **2.0**，范围 1.0~4.0），判定统一改成
    `new Box(pos).squaredMagnitude(eyePos)`；**选落脚点时也按同一口径预筛**
    （`eyeBoxDistanceAt(spot, chest) ≤ 开箱距离`），避免选到 2 格外、走过去也开不了的点。
  - 点不开自动靠近：同一箱子**连点 2 次没开** → 找「离箱子最近且站过去够得着」的落脚点再走一格
    （`StashChestSpotLogic.nearestToChest`），到位后继续点；已到 1.2 格内不再往前挤（`CLOSER_STOP_DIST`）。
  - 调试行改成 `眼→箱体 X / 上限 Y`，与服务端同口径；射线距离改用设置值 +1.5。
  - 测试：新增 2 条 `nearestToChest`，全仓 **53 套件 474 测试全绿**。
- ⚠️ 6.7p15 是**未入库**的中间版本（另一窗口产出，只交付了 jar）：内容已全部并入 6.7p16，**不要单独发 p15**。
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
   - **推送前必须做一次凭据自检**（仓库是公开的）：`git grep -nE 'ghp_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}'`，
     除文档里的占位符（如 `ghp_xxx`）外不得有任何命中；一旦命中，先吊销 token 再谈推送。
     本地 token 只许放 `.ghtoken`（已在 `.gitignore` 里），用完删掉。
2. **每次改动后，都必须编写或更新相关测试**，并在交付给用户前，确保所有测试和验证全部通过。
3. **前端全中文**：所有用户可见的界面文本（模块名、设置名、设置描述、下拉框选项、开关、按钮、提示消息、通知、聊天输出）一律使用中文，不得出现英文 UI 文本（代码标识符、物品 ID、内部类名除外）。
4. **务实说话**：没从代码里实际看到的、没验证过的，不说。不根据猜测推断功能，不编结论。不确定就说"不知道"或"需要看代码确认"。
5. **先讨论后动手**：涉及功能方案、模块设计、功能取舍、行为变化、默认值调整等方向性决策，必须先给出方案由用户拍板，未经确认不得直接改代码（与 `.github/skills/fo-development/SKILL.md` 铁律 5 一致）。
6. **每次发版在 GitHub 建正式 Release（jar 唯一分发渠道，且仓库里只保留最新版一个 jar）**：
   每个版本（`x.y`）交付时，构建产物 `FO-x.y-<MC版本>.jar` **不再提交到 releases/ 目录**
   （`.gitignore` 已用 `releases/*.jar` 拦截），流程固定为五步：
   ① commit（信息含模块名+摘要）→
   ② 打 tag `x.y`（**不带 V 前缀**，指向该版本 commit）→
   ③ 在 GitHub（jialebot6666/FO）创建正式 Release：标题 `FO x.y-<MC版本>`、说明写该版本更新介绍、
      **附件就是构建产物本身，附件名必须与产物同名**（`FO-<mod版本>-<MC版本>.jar`）。
      ⚠️ **禁止**改名、加 `V`、加短哈希/日期后缀 —— 名字对不上就会被当成另一个文件，新旧两版并存；
   ④ **删除此前所有 Release 里的 `.jar` 附件**（做法见下方「Release 附件清理」），
      保证仓库里有且只有一个 jar，就是本次这个 →
   ⑤ 在 `releases/README.md` 版本清单加一行（版本 | commit | 说明）。
   - ⚠️ 这条**只约束发版**：纯文档 / 纯代码推送但没有新 jar 时，**不要**为了「清 jar」去动已有 Release 附件
     （会把唯一的下载入口删掉，仓库里就没有 jar 了）。
   - ⚠️ 用户口径：旧版 jar 不再提供下载；Release 的历史说明与 tag 保留，**只删 jar 附件**，
     不要为了清 jar 去删整个 Release 或 tag。
   - ⚠️ 历史 jar 仍可在 git 历史里回溯（`releases/FO-Vx.y.jar` 及更早，用 `git show <commit>:<path>`）；
     但「停止把 jar 归档进 releases/」之后的版本没有 git 内副本，删了附件就真的没有了。

7. **仓库拉取一律浅克隆**：新设备/新环境拉取 FO 仓库用 `git clone --depth=1 https://gh-proxy.com/https://github.com/jialebot6666/FO.git`（只拉最新代码，秒级完成；github.com 直连慢且常超时，历史里还保留着 100+ 个 jar 二进制，全量 clone 要很久）。日常开发增量 `git pull` 即可；确需完整历史时才 `git fetch --unshallow`。不要全量 clone。

### Release 附件清理（每次发版必做，硬性规范 6 第 ④ 步）

用 GitHub REST API（token 需要 **Contents: Read and write**）。顺序不能反：**先建好新 Release 并传完附件，再删旧的**。

```bash
# 1) 列出全部 Release（默认只返回 30 条，必须带 per_page=100；超过 100 个再翻页）
curl -s -H "Authorization: Bearer $TOKEN" \
  "https://api.github.com/repos/jialebot6666/FO/releases?per_page=100"

# 2) 对每个 tag_name != 本次版本 的 Release，列出它的附件
curl -s -H "Authorization: Bearer $TOKEN" \
  "https://api.github.com/repos/jialebot6666/FO/releases/{release_id}/assets"

# 3) 逐个删除 .jar 附件（只删 .jar；其它附件如 .sha1 / 说明文件不要动）
curl -s -X DELETE -H "Authorization: Bearer $TOKEN" \
  "https://api.github.com/repos/jialebot6666/FO/releases/assets/{asset_id}"
```

**删完必须校验**（目标输出只有一行，且 tag 是本次版本）：

```bash
curl -s -H "Authorization: Bearer $TOKEN" \
  "https://api.github.com/repos/jialebot6666/FO/releases?per_page=100" \
  | python -c "import sys,json; [print(r['tag_name'],a['name']) for r in json.load(sys.stdin) for a in r['assets'] if a['name'].endswith('.jar')]"
```

判定标准：① 只有一行；② 文件名 = 本次构建产物名；③ tag = 本次版本。三条都满足才算发版完成。

Windows / PowerShell 等价写法（没有 `python` / `jq` 时用）：

```powershell
$H = @{ Authorization = "Bearer $env:GH_TOKEN"; "User-Agent" = "fo-release" }
$rels = Invoke-RestMethod -Headers $H "https://api.github.com/repos/jialebot6666/FO/releases?per_page=100"
$rels | ForEach-Object { $t = $_.tag_name; $_.assets | Where-Object { $_.name -like '*.jar' } |
    ForEach-Object { "$t`t$($_.name)" } }
```

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

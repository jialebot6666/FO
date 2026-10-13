# FO 架构参考

## 目录约定

```
FO/                                ← 仓库根，Gradle 工程在这里（没有 fo/ 子目录）
├── build.gradle.kts
├── gradle/libs.versions.toml      ← 版本号唯一权威来源
├── libs/                          ← 源码级二进制依赖（仅 baritone）
├── src/main/java|resources
├── src/test/java                  ← 含 xaero/** 桩类（Xaero 兼容层单测用）
├── .github/workflows/ci.yml       ← 唯一的 .github，嵌套 .github 是死文件
├── releases/README.md             ← 只放版本清单，不放 jar
└── .github/skills/fo-development/ ← 本 skill（含 scripts/*.ps1 构建与交付脚本）
```

- 普通模块：`src/main/java/com/fo/addon/modules/`
- 古城模块：`src/main/java/com/fo/addon/ancient/modules/` + `ancient/core/`
- 通用纯逻辑：`src/main/java/com/fo/addon/utils/`
- 寻路：`pathing/`（`IPathManager` / `BaritonePathManager` / `NopPathManager` / `PathManagers`）
- 自定义设置控件：`gui/`（如 `EnchantLevelMapSettingScreen`）

## 线程与共享状态铁律

⚠️ **Xaero 路径点容器不是线程安全的**，其渲染线程每帧遍历；从后台线程写会让渲染线程抛
`ConcurrentModificationException` 整局崩掉（6.11 实机事故）。

规定：**后台线程只做计算，一切共享状态（本地记录 + Xaero）只在客户端线程改。**
模块用 `pendingMarks` 队列 + `TickEvent.Post` 出队实现；`AncientChestStore` 另加 `synchronized` 兜底。
移植任何涉及「动不动玩家物品」的上游模块时，同样走这条（后台 `Thread.sleep` 后点槽位=跨线程发交互包，禁止）。

## 古城战利品搜索模块演进史

目标「彻底零原生依赖」，以 zychen027/AncientCityLootPredictor 的行为为**规格**，全部自己写成 Java。
按版本记录关键决策与踩坑，新改动前先确认对应约束是否仍然成立。

- **6.7古城定位改纯 Java**（`utils/AncientCityLocatorLogic`），不再走 cubiomes region 扫描。
  结构数据（58 个 NBT + 7 个 JSON）打进 mod 资源，由 `ancient/core/AncientResourceSource` 从 jar 内直读。
  半径语义：单位是**区块**，距离按**方块距离**，基准点为城市原点（旧实现是区块距离、原点+8）。
- **6.8 结构重放 + 箱子提取 + 战利品掷骰全部纯 Java**。新增 `AncientLootTable` / `AncientEnchantData` /
  `AncientEnchantAlgorithm` / `AncientLootEvaluator`，战利品表与附魔数据（107 个文件）打包进资源。
  删除 `libs/fo-ancient-blackbox.jar`（657 个混淆类）；jar 从 3.19 MiB 降到 1.98 MiB。
  两个 Windows DLL 抽到 `src/main/resources/natives/windows/` 继续供群系验证用。
- **6.9 双高亮 + 本地持久化 + 自动清理**。世界内高亮（附魔金=金、迅捷潜行3=蓝、两者=紫，靠近才画、半径可调）
  + Xaero 路径点；命中记录写游戏目录 `fo-ancient-chests.json`（`AncientChestStore`，按「服务器+维度」分作用域）；
  箱子被开/被挖则两处高亮一并删除。
- **6.10 修复 Xaero 路径点联动全线失效**。旧桥要求 `xaero.common.minimap.waypoints.WaypointSet`
  （26.x 已删除）与 `xaero.map.mods.SupportMods`（属于世界地图模组），且「缺一个就整体判定未安装」，
  在现行 Xaero 上必然误报「未安装」。重写 `XaeroWaypointBridge` 为**分层探测 + 双通道自动选**
  （官方 `ThirdPartyWaypoints` 优先，退化到玩家 `WaypointSet`），颜色用 `WaypointColor` 枚举按名取。
- **6.12 「>= 26.1」战利品表落地**。实测 4 份官方 jar（1.21.11 / 26.1.2 / 26.2 / 26.3）确认：
  26.x 的古城表只比 1.21.11 少一个 `minecraft:name_tag`（权重 86→84），
  单这一项就会让每次加权抽取错位（实机表现：位置对、物品全错）；26.3 换了 JSON 键名但语义相同。
  官方表逐字节放进 `resources/fo/ancient/v26/loot_table/chests/`，
  由 `AncientResourceSource.bundledLootTables(from26)` 按「游戏版本」设置选表。
  ⚠️ `AncientLootEvaluator.roll` 的**三参重载固定走 MC_NATIVE**，做兼容性对照必须用四参版显式传 mode，
  否则是「生产模式 vs 兼容模式」的苹果比橘子（排查曾栽在这里）。
  ⚠️ Gradle 的 `-D` **不会**传给 fork 出来的测试 JVM，探针读参数要用环境变量；
  且 `test` 任务默认走 build cache，改参数跑探针要加 `--no-build-cache`。
- **6.13 「游戏版本」四档化 + 高亮假命中修复**。四档 = ≤1.20.6 / 1.21~1.21.5 / 1.21.6~1.21.11 / ≥26.1
  （差异清单见 `GameVersionOption` 类注释，全部来自官方 jar 逐条目实测）；掷骰器支持 ≤1.20.6 旧语法。
  高亮修复三件套：搜索结果**整体替换**作用域记录（不再合并）、记录 v2 格式带种子（兼容 v1）、
  渲染按当前种子过滤（拿不到种子则不过滤）。
  ⚠️ 命中记录的语义是「该作用域最近一次搜索的结果」——改这里前先想清楚这个约定。
- **6.14 目标柱子 + 路径点生命周期管理**。柱子走 `Render3DEvent` 的 `event.renderer`
  （`NO_DEPTH_TEST` 管线，穿墙可见；`depthRenderer` 才受遮挡），地表 Y 用
  `World.getTopY(Heightmap.Type.WORLD_SURFACE, x, z)` 并按 (x,z) 缓存。
  路径点生命周期：整批替换时先撤旧点 → 开箱/被挖即删 → 关模块全收（按记录坐标删，不碰玩家手动的）→
  重开模块验证后重写。「已开」判定用 `AncientOpenedChestPolicy`
  （先确认服务端会同步 LootTable 字段才允许凭null 判已开；限制：开箱后区块未重载时检测不到）。
- **6.15 默认参数调整**：搜索类型=两者都搜索、游戏版本=≥26.1（对齐 3C3U）、高亮半径=256。只改 `defaultValue`。
- **6.16 路径点只在世界地图显示**。写入时可见性设为 `WaypointVisibilityType.WORLD_MAP_LOCAL`
  （Xaero 26.x；`AbstractWaypointRenderProvider` 对 WORLD_MAP_* 的点跳过小地图与世界内渲染）。
  选「本地」——记录按服务器+维度存。旧版 Xaero 无该枚举，反射取不到则保持默认。
- **6.17 修复「打开背包误删附近箱子标记」**（玩家背包也是 HandledScreen；已显式排除 `InventoryScreen`
  + 指向记录 10 tick 时效），并支持开双箱时另一半标记一并删
  （搭档方向照抄原版 `ChestBlock` 字节码：LEFT→rotateYClockwise，RIGHT→rotateYCounterclockwise）。
  ⚠️ 教训：「有 GUI 弹出 = 开了准星指的方块」这类启发式必须把玩家自己的背包界面排除在外。
- **6.19 「已开名单」持久化（存储 v3）**。开过的箱子记入 per-scope `opened` 坐标集合（永久保留；
  换种子时随作用域整份清空），三个检测点（开箱判定 / 恢复记录lootTable 检测 / 周期清理 lootTable 检测）都写入；
  新搜索落地时跳过名单坐标。
  ⚠️ `clear(scope)` 全清（换种子），`clearMarks(scope)` 只清标记（整批替换/重建用，保已开名单与种子）——别混用。
  ⚠️ 行为变化：误开的箱子不能再靠重搜恢复（用户确认接受）。
  顺手加固 `AncientJson`：不平衡 JSON 由裸越界改为带信息异常。
- **6.24 杀戮光环新增「切换回来」**（对齐官方 KillAura的 `swap-back`）：切武器前记住原快捷栏槽位、
  攻击会话结束时切回；只在会话首次记录；仅「目标为空 / 手上非可接受武器 / 关闭模块」结束会话，
  暂停类守卫不结束。默认关闭。状态机 `utils/SwapBackTracker` 可单测。
- **6.25~6.27 自动偷取**。移植官方 `InventoryTweaks` 的 Auto Steal，两点差异：
  ① 官方只按 `Item` 匹配、认不出附魔书附的是哪条附魔——本模块读 `ItemEnchantmentsComponent`
  按附魔 ID + 等级判定（`utils/AncientStealLogic`）；② 官方在后台线程 sleep 后点槽位，
  本模块改为「`InventoryEvent` 里只快照槽位 → `TickEvent.Post` 里逐个取」，全程客户端线程。
  6.26 收紧为**只偷箱子**：必须确证打开的是 `ChestBlock`，潜影盒/木桶/末影箱一律不偷，
  依据缺失或过期时保守不偷（fail-safe，宁可漏偷）——
  ⚠️ 6.25 沿用官方行为把用户存潜影盒里的附魔金苹果也偷走了。
  6.27 加累计统计：落`fo-steal-stats.json`，键用物品 ID / 附魔 ID（与显示文案解耦），
  附魔书按等级分开计。⚠️ **记账口径 = 真的取到手才算**：`InvUtils.shiftClick()` 返回 void、不报成败，
  实现是「点击时记下槽位原本内容 → 下一刻回读核对」。
- **6.28 「应用种子」点一次就记住**：写进全局一份 `fo-ancient-seed.json`，模块激活时自动带上；
  清空输入框再点应用 = 清除保存的种子。⚠️ 刻意**只影响搜索用的种子**，
  不动 `currentSeedGuess()`（记录种子校验 + 渲染过滤），避免顺带触发清档/隐藏高亮。
- **6.29~6.31 累计统计展示控件**：独立可折叠分组 `sgStats`（建在 `sgSteal` 之后 → 渲染在「初始延迟」下面；
  分组按**创建顺序**渲染）→ 定稿为「名称 + 只读黑框」两列 `WTable`（只读框 = `theme.textBox` + 拒绝过滤器）
  → 最后删掉「战果」左标签、说明改悬停抬头。
  发现：`DefaultSettingsWidgetFactory.group` 调工厂**之前**已把左侧标题标签加进当前行，
  工厂里可 `table.getRow(table.rowI()).get(0).widget()` 摘下标签再 `removeRow` 整行删掉自己重画
  （`Setting.title` 是 final 改不了，只能删行）。⚠️ 设置名的左侧标签是框架自动加的，工厂只能补右侧内容。
- **6.31→6.32 崩溃修复**：`WTable.removeRow(i)` 内部把行游标 `rowI` 减一；「累计统计」是分组的**第一个**设置，
  删掉第 0 行后 `rowI = -1`，下一句 `table.add` 走 `rows.get(-1)` 越界。
  修复：删行后**必须补一次 `table.row()`** 把游标移回（框架在工厂返回后还会再调一次 row()，正好恢复原节奏）。
  教训：用框架的「删行」类 API 前先读源码确认游标/索引副作用。
- **⏳ 7.0 待做**：群系验证自写（替代 cubiomes 的 `isViableAncientCity`），然后删掉那两个 Windows DLL。
- **⏳ 待做**：「只偷本模块搜索命中的古城箱子」更严格模式（当前按用户口径只判「是不是箱子」）。

### 黄金回归测试

- `AncientCityGoldenTest`：拼图块 + 箱子坐标 + LootTableSeed。
- `AncientLootEvaluatorTest`：113 箱掷骰结果逐箱锁死。
- 两者都**不依赖黑盒**。
- ⚠️ 已知与参考实现的偏差（我们按 MC 本体语义实现，是刻意修正）：参考实现的 `enchant_with_levels`
  忽略 options 用全量附魔、`enchant_randomly` 的标签不展开嵌套；修正后 113 箱里 21 箱的物品清单会不同。
  对照验证时用 `AncientEnchantData.SourceMode.REFERENCE_COMPAT` 复刻它的偏差。

## 自动扔垃圾的附魔书过滤（6.18~6.22 反复重做，可借鉴的教训）

形态换过三轮：6.18 四模式 + 全局等级阈值 → 6.20 文本规则列表 → 6.21 每附魔一条滑条（43 条撑爆设置页）
→ **6.22 定稿：一个自定义设置项** `utils/EnchantLevelMapSetting`
（附魔 ID→最低等级映射 Setting，仿Meteor 官方 `StatusEffectAmplifierMapSetting`：NBT 持久化复合标签、
parseImpl 文本解析、只存 >0 条目）+ `gui/EnchantLevelMapSettingScreen`（全43 附魔逐行配等级 + 搜索框），
经公开 API `SettingsWidgetFactory.registerCustomFactory` 注册控件，位置在物品列表与丢弃间隔之间。

- 判定语义（未变）：书内任一附魔达标即命中 →视同「在名单里」走黑白名单；全部 0 = 跟随物品名单；
  整本全留/全丢 = 把「附魔书」物品加进/排除出物品列表。
- 纯逻辑 `TrashLogic.rulesFromLevelMap` 可单测。
- 数据来源已逐条核实：43 个附魔清单与最高等级来自官方客户端 jar 的 `data/minecraft/enchantment/*.json`
  （1.21.11 与 26.1.2 一致，含 26.x 新附魔 lunge/突进）；中文名来自 26.1.2 官方
  `assets/minecraft/lang/zh_cn.json`（客户端 jar 不带语言文件，需外取）。
- `EnchantListTest` 用官方 43 个 ID 清单做锚点断言，**未来 MC 新增附魔需同步更新 `EnchantList` 与该测试**。
- 形态变更不迁移旧配置（用户重配一次），交付时要说明。

## 外部兼容性排查铁律

⚠️ **对着真实 jar 反编译核对类名与签名**，不要凭记忆或旧资料。
Xaero（6.10）、Baritone（2026-10改用编译期 API 后）、官方 Meteor（6.25 Auto Steal）都因此踩过坑。

- Baritone 必须用**原版** `baritone`（mod id `baritone`），不是 Meteor 打包的 `baritone-meteor`——
  玩家装的是原版，两者 mod id 与运行时实现都不同。升级 Baritone 时须用 `javap` 重新核对签名。
- Xaero 在测试里有桩类（`src/test/java/xaero/**`），改兼容层时桩类要同步。

## 残留待清理

- `src/main/java/com/custom/addon/util/CubiomesJNI.java`（111 行，JNI 符号硬绑定此包名）与
  `src/main/java/com/fo/addon/ancient/core/CubiomesJNI.java`（30 行，转发到前者）是**两套并存的桥**。
  7.0 自写群系验证后两者连同 DLL 一并删除。
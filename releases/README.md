# FO 版本归档

每个修复/功能版本交付前，把构建产物（`fo/build/libs/FO-<mod版本>.jar`，当前
`FO-V6.2.jar`）复制为 `FO-V<版本号>.jar`（不带 commit 短哈希）并提交到本目录，
保证版本留存。mod 版本号在 `fo/gradle/libs.versions.toml` 的 `mod-version`，升版时同步改。

## 命名规范

- 产物文件名统一大写：`FO-V<版本>.jar`（不带 commit 短哈希，与构建产物、GitHub Release 附件同名）。
- 产物名与 mod 版本号统一到版本体系：产物 = `FO-<mod版本>.jar`，升版同步改 `mod-version`。

## 版本清单

V2.4–V4.62 与 V6.0 的说明按各版本 commit 原文回追，只去掉了版本标签重复和「归档Vx.y」「+Vx.y升版」这类
记账尾巴；V0.9–V2.3 以及 V4.15、V5.0–V5.2 沿用原先手写说明。

| 文件 | 版本 | 说明 |
|---|---|---|
| FO-5PrWPQNWZm.jar | 首版 | 初版 |
| FO-YoNbGZGn7b.jar | 汉化 | 前端汉化 |
| FO-csbnufGjfX.jar | 汉化+前缀 | 汉化 + FO 前缀 |
| FO-f295ca3.jar | V0.9 | ElytraCollector 三 bug 修复 |
| FO-9cfe2e0.jar | V1.0 | 默认物资金胡萝卜 |
| FO-V1.1.jar | V1.1 | 存鞘翅新流程 + 方向过滤 + StorageRecovery |
| FO-V1.2.jar | V1.2 | 默认物资加不死图腾 |
| FO-V1.3.jar | V1.3 | 自动拿空盒开关 |
| FO-V1.4.jar | V1.4 | 默认白名单 26 项 |
| FO-V1.4a.jar | V1.4a | 白名单补齐 61 项 |
| FO-V1.5.jar | V1.5 | 存鞘翅绝不用补给盒 + 无盒自动下线 |
| FO-V2.3.jar | V2.3 | 低Y退出真正退出游戏 |
| FO-V2.4.jar | V2.4 | feat(AutoLog): 新增 FO 自动LogPlus 模块 |
| FO-V2.5.jar | V2.5 | feat(AutoLog): 新增生命值/图腾数量下线触发 |
| FO-V2.6.jar | V2.6 | fix(AutoLog): 生命值阈值改为整数滑块(IntSetting) |
| FO-V2.7.jar | V2.7 | 新增FO自动挖沙模块(Baritone+Nuker双模式/GrimV3序列补偿/FO补给盒自动补给/存沙/联动AutoTrash) |
| FO-V2.8.jar | V2.8 | 重写自动挖沙-改用Baritone mineProcess(参考miku)/Nuker用BlockUtils.breakBlock/修卡住bug |
| FO-V2.9.jar | V2.9 | 自动挖沙加潜影盒高亮(补给绿/存沙黄) 存沙盒满了自动找下一个不高亮 |
| FO-V3.0.jar | V3.0 | Nuker模式重写-参考SlimefunHelper(原版reach/4tick刷新/单目标挖/不瞬间挖) 防被踢 |
| FO-V3.1.jar | V3.1 | 修存沙(等服务器同步/卡住检测) 保护潜影盒不被Baritone挖 高亮透明度30% |
| FO-V3.2.jar | V3.2 | 修Nuker距离校验-每tick复查目标在reach内 走路中不重复发moveTo |
| FO-V3.3.jar | V3.3 | 存沙提速-开着潜影盒时每tick处理 每tick最多移9组 卡住检测改10tick |
| FO-V3.4.jar | V3.4 | 修存沙误存物品-用pendingStoreSlots跟踪整批槽位 全部确认空了才移下一批 防止重复点击把别的物品存进去 |
| FO-V3.5.jar | V3.5 | 存沙重写-参考ElytraCollector成熟逻辑 用sh.getSlot读服务器同步状态 一次移一组 卡住检测满盒 |
| FO-V3.6.jar | V3.6 | 修补给铲子一次拿多把(加stuckSlot检测) 启动时检查补给盒和存沙盒不存在就停 缓存补给盒位置 |
| FO-V3.7.jar | V3.7 | 存沙盒满了直接停止模块 不再找下一个 |
| FO-V3.8.jar | V3.8 | 存沙逻辑-一个盒子满了找下一个 所有盒子都满了才停模块 |
| FO-V3.9.jar | V3.9 | 启动时先逐个打开附近潜影盒同步名字 再识别补给盒和存沙盒 开始挖沙 |
| FO-V4.0.jar | V4.0 | 修铲子补给-先把没耐久的铲子放回补给盒再拿新的 补给盒里也没好铲子就停模块 |
| FO-V4.1.jar | V4.1 | 被攻击打断寻路后自动续上-GOING_STORE/GOING_SUPPLY每tick重新moveTo |
| FO-V4.2.jar | V4.2 | 启动扫描加速-打开盒子等10tick同步名字再关 关完5tick开下一个 |
| FO-V4.3.jar | V4.3 | 修Baritone保护潜影盒-直接改Setting.value字段 不再调不存在的value()方法 |
| FO-V4.14.jar | V4.14 | 修存沙死循环自转+存不进-开盒超时改原地重试不回MINING(对齐miku),满盒改读盒槽同步判满立即换盒(只认沙杜绝miku误存),storeTickCounter每次开盒重置,存沙盒选最近且只从INIT_SCAN同步过名字的盒选(防存进补给盒),新增StoreOpenLogic/StoreSlotLogic/NearestBoxLogic+17单测 |
| FO-V4.15.jar | V4.15 | 存沙路径重构：goToStore 选最近盒（只在 INIT_SCAN 同步过名字的盒中选，排除满盒/不可开盒）+ tickOpenStore 开盒超时原地重试不回 MINING |
| FO-V4.16.jar | V4.16 | 存沙/补给/INIT_SCAN寻路固定Y轴(新增StandSpotLogic站立点候选,goToStore/goToSupply/tickGoing*/tickInitScan改寻路到盒子旁空气格而非GoalXZ忽略Y,修复盒子在沙丘顶/坑里时3D到达判定永不满足卡死)+开盒六面检测(FacingLogic新增bestFaceIndex按视线选正对的面,openShulker/onOpenEc不再固定UP,顶面被盖/站位刁钻也能开,InteractionUtils改对准面中心交互),升V4.16,补13单测 |
| FO-V4.17.jar | V4.17 | FO 自动挖沙: INIT_SCAN 打开即关提速(10tick→3tick,关屏等待5tick→1tick) |
| FO-V4.18.jar | V4.18 | FO 自动挖沙: INIT_SCAN 每个盒子先寻路到面前站立点再开(去掉3格内直接开;atStand判定=玩家在站立点2格内,站立点每盒缓存),站立点排除盒子正下方(站下面开盒打不开,StandSpotLogic新增withoutBelow纯逻辑+2单测),升V4.18 |
| FO-V4.19.jar | V4.19 | FO 自动挖沙: 盒子寻路贴脸+到达判定收紧+补给限次停模块,升V4.19 - 站立点=盒子紧邻格(水平±1同层/上一层,StandSpotLogic新增adjacentCandidates,一格空间都不留;紧邻格全被占才放宽±2) - 精确寻路:IPathManager新增moveToPrecise(Baritone GoalNear 0.5,玩家中心进目标格,替代GoalGetToBlock停在相邻格) - 到达判定收紧:INIT_SCAN/存储/补给全部改为玩家在站立点格1格内才开盒(原3格/2格判定让玩家停2-3格处够不到开盒,抬头重试循环) - 补给盒连续5次打不开报错停模块(原failLimit=1000000无限循环重试) - OPEN/GOING状态跳过操作延迟节流(delay=5时40tick超时被放大成10秒) |
| FO-V4.20.jar | V4.20 | FO 自动挖沙: 修复 GoalNear 反射签名导致 Baritone 桥接整体失效,升V4.20 - GoalNear 实际构造器是 (BlockPos, int) 不是 (BlockPos, double)，写 double 抛 NoSuchMethodException - 该反射在 BaritonePathManager 构造器里，一处失败整个桥接初始化失败，PathManagers 回退空实现   → moveTo/mine 全失效（'不能调用baritone'） - range 语义修正：range=0 = 玩家脚所在格必须等于目标格（一格空间都不留）；range=1 会允许停相邻格等于白改 |
| FO-V4.21.jar | V4.21 | FO 自动挖沙: 存沙改一组一组存(每tick只搬一组,QUICK_MOVE节流),升V4.21 - doStore moved上限27→1:一次批量27组瞬间发出易被服务器吞包/触发反作弊丢沙 - 配合storeStuckSlot回写等待,服务端回写前不搬下一组,每组间隔>=1tick - 一盒打开期间持续搬,全部搬完才关盒回挖矿 |
| FO-V4.22.jar | V4.22 | FO 自动挖沙: 核爆下限-6+扔垃圾默认参数+挖沙联动自动扔垃圾,升V4.22 - 核爆纵向下限默认0→-6(脚下6格到头上6格) - TrashDefaults白名单加minecraft:sand(61→62项,挖沙场景沙保留攒满背包触发存沙,杂物被丢;测试同步62+新增sand断言) - AutoTrash排除快捷栏默认true→false(快捷栏杂物也丢,主手槽始终跳过) - AutoMineSand联动AutoTrash:onActivate强制白名单+确保列表含沙(缺sand自动补,尊重用户其他配置)+弹通知;onDeactivate仅关挖沙联动开启的(联动来源标记,用户手动开的不误关) |
| FO-V4.23.jar | V4.23 | FO 自动挖沙: 补给盒内容指纹兜底+食物补给锁一组四选一+一次补给只做一件事,升V4.23 - 内容指纹:服务器清掉长时间放置盒子名字时,INIT_SCAN打开时读盒内内容,含钻石/合金铲记为补给候选(SupplyFingerprintLogic硬性标准),finishInitScan名字找不到时按内容兜底;内容识别的补给盒从存沙候选显式排除(防沙存进补给盒) - 食物补给:删除金萝卜目标数量设置,新增食物种类四选一(面包/金苹果/牛排/金胡萝卜,FoodType枚举);背包所选食物为0时shift整组64恰好一组,无叠加超量 - 一次补给流程只做一件事(supplyJob锁定):补铲子=先拿新铲再放旧铲(防打断手无铲),补食物=拿一组,补图腾=逐个拿满target;盒里没有对应物资报错停模块(防空转死循环) - 新增6单测(FoodType 3+指纹 3),共106全绿 |
| FO-V4.24.jar | V4.24 | FO: 修复内容指纹补给盒高亮不变绿+提示不暴露坐标+食物种类下拉框英文,升V4.24 - 高亮:内容指纹识别的补给盒(名字被服务器清掉)也显示绿色(supplyBoxPos匹配),不再误显示成存沙盒黄 - 坐标:AutoMineSand补给盒识别提示去掉toShortString坐标;ElytraCollector飞向/确认龙头/检测未找到/Y警告全部去掉坐标(3C3U无政府服禁止暴露坐标,防录屏泄露);debugLog写文件保留 - 汉化:FoodType枚举补toString返回中文(EnumSetting下拉框走toString,否则显示BREAD等英文枚举名,违反前端全汉化);ElytraCollector朝向英文枚举→中文(东/南/西/北) - 新增1单测(FoodType toString中文),共107全绿 |
| FO-V4.25.jar | V4.25 | FO 自动挖沙: 铲子补给确定性落主手+挖掘自动切铲子,升V4.25 - B:补给流程拿新铲后 InvUtils.swap 切到主手选中槽(确定性落点,不再靠 quickMove 随机落背包);选中槽回写用'等非空'确认新铲上手(supplyWaitNotEmpty);流程=拿新铲→切主手→放旧铲→关盒(phase 1/2/3) - A:tickMining 挖掘前手持不是铲子时自动从背包/快捷栏找够耐久铲子切主手(任何来源的铲子都自动上手,不依赖补给落点);找铲子过滤耐久,防把旧废铲切上手 - 107 单测全绿 |
| FO-V4.26.jar | V4.26 | FO 自动挖沙: 修复快捷栏满时铲子落背包(切主手竞态),升V4.26 - 根因:原版拾取物品快捷栏空位优先,挖沙时快捷栏被沙占满;quickMove拿新铲落背包后,swap切主手的'等非空'判断在选中槽原本有物品(沙子/旧铲)时提前误判通过→提前关盒→swap包作废→铲子留背包 - 修复:phase=2等待改为严格等'选中槽是够耐久的铲子'(swap真正完成才放行),绝不提前关盒;加60tick超时保护(异常时报错停模块,不再无限等待) - 107 单测全绿 |
| FO-V4.27.jar | V4.27 | FO 自动挖沙: 铲子补给改'主手旧铲↔盒新铲直接替换',升V4.27 - 替换为主:选中槽是铲子时,pickup拿起主手旧铲→pickup点击盒新铲槽(交换:旧铲进盒,新铲上cursor)→放下到选中槽。无空位要求(交换不需要空位,根治'盒满存不进卡住'原始担忧)、无随机落点(新铲确定进选中槽)、无竞态(每步等回写,最后等选中槽是够耐久铲子) - 兜底:选中槽被杂物占用时回退V4.26流程(quickMove+swap+放旧铲,phase 11/12/13) - 防御:盒里没有够耐久铲子时,先把cursor旧铲放回主手(防丢铲)再报错停(phase 9);超时保护沿用(60tick) - 107 单测全绿 |
| FO-V4.28.jar | V4.28 | FO 自动挖沙: 铲子补给改回'先放旧铲再拿新铲'+盒子没位置报错,升V4.28 - 用户拍板弃用V4.27替换方案,改回:先放旧铲回盒(quickMove)→再拿新铲(quickMove)→切主手(swap+严格确认) - 新增:放旧铲前检查盒子空位,盒子满(没位置)时明确报错'补给盒没位置了,补给铲子失败,模块停止'(用户要求此提醒,防放不进去卡住) - 移除V4.27替换流程死代码(pickup方法) - 107 单测全绿 |
| FO-V4.29.jar | V4.29 | FO 自动挖沙: 删除V4.26补给流程切主手代码,补给只做放旧拿新,升V4.29 - 按用户要求删除V4.26的补给流程内'切主手'(InvUtils.swap+严格确认)阶段 - 铲子补给简化为:先放旧铲回盒→再拿新铲→关盒完成;新铲自动上手由A逻辑(tickMining挖掘前自动切够耐久铲子到主手)负责,回MINING后1tick内生效 - 清理死逻辑:supplyWaitNotEmpty字段及赋值全部移除(无true赋值) - 107 单测全绿 |
| FO-V4.30.jar | V4.30 | FO 自动挖矿(AutoMining) 移植misaka 23态状态机(钻石/残骸模式, 存储/末影箱/拾取/深暗逃离/石英修镐), pathing层isMining+挖掘避让, AutoTrash挖矿联动, 单测AutoMiningLogicTest |
| FO-V4.31.jar | V4.31 | FO 自动挖矿 V4.31 方案B: Baritone autoTool=false(FO锁时运镐, 挖矿/石英每20tick强制时运, 防精准采集挖钻石掉原矿), 挖末影箱优先精准采集镐回收本体(无精准镐回退消耗式), 工具策略pickaxeStrategy纯逻辑+单测 |
| FO-V4.32.jar | V4.32 | FO 清理用户可见文本中的开发备注(模块描述去掉'参考misaka移植'字样, 前端全中文规范自查) |
| FO-V4.33.jar | V4.33 | FO 删除挖沙设置描述中的来源参考(SlimefunHelper原版/NO_BYPASS), 保留Baritone/Nuker专名(用户拍板) |
| FO-V4.34.jar | V4.34 | FO 修复自动挖矿2个bug - ①锁时运镐加AutoEat保护(isUsingItem/eating时跳过, MiningGuard纯逻辑+单测) ②对齐misaka:删isSolidSurrounding就地分支,findSafeSpot半径3→128递增扫描Y±3非液体,findPlacePosition先玩家Y层3x3再safeSpot 3x3x3 |
| FO-V4.35.jar | V4.35 | 移植FO杀戮光环：miku KillAuraMiku移植+挖矿/挖沙成对联动+锁时运保护第三参数 |
| FO-V4.36.jar | V4.36 | 修复钻石模式存盒链：合成后先存潜影盒再挖工作台（对齐misaka CRAFTING→PLACING_SHULKER→STORING→MINING_SHULKER→MINING_CRAFTING_TABLE） |
| FO-V4.37.jar | V4.37 | 修钻石模式两bug：①合成拖拽槽位换算修正(n+46/n+10)+联动白名单确保钻石块/残骸保留(防合成产物被丢) ②拾取检测修复(pickupSeen：掉落物消失即完成，不再300tick误超时+不挖工作台) |
| FO-V4.38.jar | V4.38 | 纠正V4.37引入的槽位换算错误：对齐misaka x0005(热键+37/主背包+1)，抽CraftingSlotMath纯函数+3测试锁定，防回归 |
| FO-V4.39.jar | V4.39 | 对齐misaka拾取与合成：①拾取判定改背包计数+1式(startPickup记录基数) ②合成取出容量兜底(背包满丢一组钻石腾空间) ④潜影盒200tick超时断开/其余重试(PickupTimeoutLogic纯函数+测试) ⑤拾取寻路周期性重发+掉落物Y非整数取up格(修复挖台后不拾取) |
| FO-V4.40.jar | V4.40 | 修启动检查+末影箱取盒死循环：①满盒+末影箱可启动(StartupCheckLogic纯函数+测试,缺文案'空潜影盒或末影箱') ②末影箱取到空盒后接MINING_ENDER_CHEST回收(修复循环取盒取光空盒才断开) |
| FO-V4.41.jar | V4.41 | 修复Baritone设置反射bug：minYLevelWhileMining=6(基岩上6格起挖,对齐misaka)+躲避怪物4设置+blocksToAvoid从未生效,原实现对settings对象操作value字段抛异常被吞;改对Setting对象操作+Long/Integer类型适配,新增备份/恢复(排查:其他模块protectShulkerBoxes/setAutoTool写法正确无此bug) |
| FO-V4.42.jar | V4.42 | 新增FO设置项[捡取挖掘掉落物]默认开：Baritone mineScanDroppedItems=true(找不到新矿时把掉落物当目标捡起,兜住挖了没捡钻石/残骸),关闭模块随resetMiningAvoidance恢复 |
| FO-V4.43.jar | V4.43 | 默认值调整: FO杀戮光环[武器类型]默认剑/自动切换默认开/暂停Baritone默认关, FO自动丢垃圾[丢弃延迟]默认1 |
| FO-V4.44.jar | V4.44 | FO自动LogPlus设置全部改整数: Y高度/护甲耐久阈值/无响应秒数/触发距离/重连等待秒数 Double→Int(滑块无小数), 保留默认值不变 |
| FO-V4.45.jar | V4.45 | 鞘翅采集回起飞点对齐Ying: EXIT_P1/EXIT_LANDING/LEAVE_SHIP每20tick或未寻路重发moveTo+超时停止任务不原地起飞+relaunchAfterShip先回降落点(ReturnPathLogic纯函数+6单测) |
| FO-V4.46.jar | V4.46 | ElytraCollector 对齐Ying ①三飞行角度设置化+②滑翔省烟花+③忽略已访问开关+④搜船回退50000格环形外扩 |
| FO-V4.47.jar | V4.47 | AutoMining 修3bug ①末影箱链挂待挖工作台标志 ②拾取超时先挖工作台+每tick无条件重发寻路 ③钻石不足9返回true关界面进存储链 |
| FO-V4.48.jar | V4.48 | ElytraCollector 全面对齐 Ying (V4.48): ①进LANDING距离3→80 ②移植ElytraApproachSafety22防撞塔(FlightApproachLogic) ③巡航爬升改45°(takeoffPitch, cruiseClimbPitch隐藏) ④移植ElytraSafeEscape安全复飞三段式(FlightEscapeLogic) ⑤低Y退出关闭FOKillAura |
| FO-V4.49.jar | V4.49 | AutoMining 满盒立即换空盒对齐misaka - 拾取潜影盒后立即检测背包满盒(hasFullShulkerInInventory对齐x0024),满盒先放末影箱换空盒(PLACING_ENDER_CHEST)再挖工作台,新增PickupNextLogic决策表+单测 |
| FO-V4.50.jar | V4.50 | AutoMining 修复拾取卡死+放末影箱放不下 - 拾取改用Baritone pickup持续追踪(对齐挖沙,原moveTo/GoalGetToBlock只到相邻格踩不到掉落物),放置位置搜索safeSpot 3x3x3优先不依赖玩家位置(对齐misaka x0016),放置判定抽纯布尔canPlaceOn+4单测 |
| FO-V4.51.jar | V4.51 | AutoMining 安全位置搜索全面对齐 misaka x0005：Y 下限(主世界-58/地狱6)+5x5x5全实体检查, 修复背包满时选到基岩层, 补 SafeSpotTest |
| FO-V4.52.jar | V4.52 | AutoMining 选盒对齐 misaka x0014：优先含目标物最多的全目标盒/其次空盒, 修复含钻石块未满盒被无视导致不存盒+末影箱取盒失败, 补 StorageBoxLogicTest |
| FO-V4.53.jar | V4.53 | AutoMining A组5处对齐misaka：满堆叠判定/末影箱取盒扩展/背包满交换/放盒前查满盒/合成背包满先存储（V4.53） |
| FO-V4.54.jar | V4.54 | AutoMining 行为级差异6处对齐misaka |
| FO-V4.55.jar | V4.55 | AutoMining 方案C：联动Meteor AutoTool替代自锁时运镐 |
| FO-V4.56.jar | V4.56 | AutoMining 修复放置格选中玩家脚底 |
| FO-V4.57.jar | V4.57 | AutoMining 放置前强制回洞里站位+排除全身碰撞箱 |
| FO-V4.58.jar | V4.58 | AutoMining 打开容器界面守卫+20秒超时报错断开 |
| FO-V4.59.jar | V4.59 | AutoMining 启动检查强制时运镐+末影箱+工作台+精准采集镐+提醒自动LogPlus |
| FO-V4.60.jar | V4.60 | AutoMining 残骸模式Y范围限制开关(默认开8~22) |
| FO-V4.61.jar | V4.61 | AutoMining Y范围按目标动态切换+范围变化弹通知 |
| FO-V4.62.jar | V4.62 | AutoMining 挖矿重启对齐misaka有条件重启(idle才重启) |
| FO-V5.0.jar | V5.0 | 移植 IceHack 鞘翅套件：FO 自动鞘翅飞行 + FO 自动开宝库 + FO 实时平均速度（含 HUD FO 实时速度）；Baritone 桥接改纯反射；前端全汉化 |
| FO-V5.1.jar | V5.1 | 修复「挖掘异常？取消挖掘」误报：等待计数按用途拆开 + 方块还在时绝不打断 Baritone 挖潜影盒（末影箱同形问题一并修） |
| FO-V5.2.jar | V5.2 | 挖回潜影盒/末影箱改用 FO 自己的挖掘（默认关掉 Baritone 挖掘）+ 背包满自动丢垃圾腾位 + 槽位差捡盒判定 + 火球时不判挖掘超时 |
| FO-V6.0.jar | V6.0 | 全面移植 icehack-2 鞘翅套件（删除 V5.x 旧套件）：移植 39 类（AutoElytraFlight/AutoOminousVault/SpeedMeter 三模块 + SpeedHud + core 状态机 BounceProbe/JunkDropper/TimelinessCounter/OrderedItemListSetting 等），删掉 TerrainProbe/SegFailWindow/FreeSlotLogic/InventoryPickupLogic/MineWaitLogic，依赖改 modCompileOnly baritone-api，15 个 UI 枚举加中文 label，重建 ElytraUiTextTest 守护测试（231 测试全绿） |
| FO-V6.1.jar | V6.1 | 修复自动鞘翅飞行补给降落 bug（方案B转向 + abort 清理），存档命名统一 FO-V<版本>.jar 不带哈希 |
| FO-V6.2.jar | V6.2 | 删除 V6.0 新增的 icehack-2 鞘翅套件（自动鞘翅飞行/自动开宝库/实时速度模块+HUD），回归 7 模块 |
| FO-V6.3.jar | V6.3 | 新增 FO 古城战利品搜索（半自动）：移植 misaka 古城模块（附魔金苹果/迅捷潜行3 搜索 + Xaero 路径点），黑盒 657 类（com/w + w 数据类）与 2 个 DLL 入包，核心逻辑源码级重写 com.fo.addon.ancient.*，默认种子 -7346913998703726680 |
| FO-V6.4.jar | V6.4 | 修复古城搜索无结果：CubiomesJNI native 符号绑定原包 com.custom.addon.util（新增桥类+FO 转发层），距离判定对齐原版方块坐标，catch Throwable 防 Error 中断，DLL 加载失败明确报错 |

> 备注：8430096（AutoTrash 白名单修复）的云端交付链接已失效，
> 本地无 jar 副本；其源码仍完整保留在 Git 历史中（commit 8430096），
> 如需可随时重建。

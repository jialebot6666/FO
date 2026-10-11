# FO 版本归档

每个修复/功能版本交付前，把构建产物（`fo/build/libs/FO-<mod版本>.jar`，当前
`FO-V6.21.jar`）复制为 `FO-V<版本号>.jar`（不带 commit 短哈希）并提交到本目录，
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
| FO-V6.5.jar | V6.5 | 修复古城搜索中断：Meteor info() 内部 String.format，进度消息裸 %（%）抛 UnknownFormatConversionException（Conversion = ')'）——已转义 %%；新增「调试模式」开关：输出 DLL 加载/生成器/城市定位/箱子判定明细 + 完整异常堆栈到聊天 |
| FO-V6.6.jar | V6.6 | 修正 V6.4 的坐标空间错误（V6.4 说明里「距离判定对齐原版方块坐标」其实对齐反了）：搜索半径单位是区块，misaka 原实现在用 <<4 后的方块坐标复核 isViableAncientCity 之后会**再取回区块坐标**做距离判定并传给结构重放。V6.4~V6.5 直接拿方块距离比区块半径，有效半径被压成 1/16（默认 1000 区块只剩约 62 区块），且把方块坐标传给 generateAt 造成二次 <<4。现统一走 AncientSearchLogic.chunkDistance / blockToChunk，新增 4 条坐标回归测试 |
| FO-V6.7.jar | V6.7 | 古城「彻底零原生依赖」第 1 步：**古城定位**改为纯 Java 自写（`utils/AncientCityLocatorLogic`，每 24×24 区块一个区域、LCG 推偏移、setLargeFeatureSeed 推旋转与起始件），不再走 cubiomes 的 region 扫描；**结构数据入包**（MC 1.21.11 客户端 jar 的 58 个古城结构 NBT + 7 个拼图池 JSON，共 281.8 KiB），新增 `ancient/core/AncientResourceSource` 从 jar 内直读（`DirectoryResourceSource` 供测试与解压目录、`ClasspathResourceSource` 供运行时），不再需要解压临时目录；顺带修掉两个隐患（`AncientTemplate/AncientPool` 的缓存改 `ConcurrentHashMap`、缓存键带上数据源身份）。半径语义按用户拍板改为**新语义**：半径单位仍是区块，但距离按方块距离、基准点为城市原点（旧实现是区块距离、基准点原点 + 8，两者在半径边界差半个区块）。新增黄金回归测试 `AncientCityGoldenTest`：期望值来自独立参考实现 zychen027/AncientCityLootPredictor（纯 Python），5 座古城 89/100/101/87/92 个拼图块、18/26/28/21/20 共 113 个箱子的坐标+LootTableSeed+战利品表逐项全等，且**不依赖黑盒 jar**（V6.8 删黑盒后依然有效）；新增 `AncientResourceSourceTest` 覆盖资源加载/缺失退化/缓存数据源隔离/并发安全。36 套件 255 测试全绿；构建产物 3.05 MiB |
| FO-V6.8.jar | V6.8 | 古城「彻底零原生依赖」第 2 步：**战利品掷骰改为自研实现，删掉黑盒 jar**。新增 `ancient/core/AncientLootTable`（战利品表解析）、`AncientEnchantData`（附魔与标签数据 + 标签递归展开）、`AncientEnchantAlgorithm`（附魔台算法：getAvailableEnchantmentResults / selectEnchantment / 互斥过滤）、`AncientLootEvaluator`（池掷次数 → 加权抽条目 → 逐个函数 → 命中判定）。数据入包：2 张战利品表 + 43 个附魔 + 22 个附魔标签 + 32 个可达物品标签 + `index.json` 索引（共 107 个文件，来自 MC 1.21.11 客户端 jar）。**删掉 `libs/fo-ancient-blackbox.jar`（657 个混淆类，压缩 890 KiB）**，黑盒里的两个 Windows DLL 抽到 `src/main/resources/natives/windows/` 继续供群系验证用，jar 从 3.19 MiB 降到 **1.98 MiB**。新增 `AncientLootEvaluatorTest` 把 113 个箱子的掷骰结果逐箱锁死（坐标+命中标签+完整物品清单），另附附魔标签语义、互斥规则、RNG 退化分支等单测，37 套件 261 测试全绿。**等价性验证**：本实现与参考实现 zychen027/AncientCityLootPredictor 在同一份 MC 1.21.11 数据下做到 **113/113 逐箱全等**（物品清单 + 附魔清单），并据此修正了参考实现的两处偏差（`enchant_with_levels` 忽略 options 用全量附魔、`enchant_randomly` 的标签不展开嵌套），这两处会改变 113 箱里 21 箱的物品清单。同时发现参考实现自带的是 MC 26.1.2 的战利品表（比 1.21.11 少一个 `minecraft:name_tag` 条目），本版以 mod 自身版本 1.21.11 为准。**已知缺口**：「>= 26.1」选项对应的 26.x 战利品表与 random_sequence 子系统尚未自写，选中该选项时会明确提示 |
| FO-V6.9.jar | V6.9 | 古城目标箱子**双高亮 + 本地持久化 + 自动清理**。**世界内高亮**：走近古城时给命中的箱子画高亮框，附魔金=金色、迅捷潜行3=蓝色、两者=紫色（`Render3DEvent` + `event.renderer.box`，靠近才画，半径默认 64 格可调，颜色三项都可调）。**Xaero 路径点颜色改正**：原来「金/书/星」三组用 14/3/7，注释还写错了（3 不是蓝、7 不是紫），现按原版 16 色改为一组标签一色：附魔金=6（金）、迅捷潜行3=9（蓝）、两者=13（浅紫），路径点名字直接用中文标签。**本地持久化**：命中记录写到游戏目录下的 `fo-ancient-chests.json`（新增纯逻辑类 `AncientChestStore`，按「服务器+维度」分作用域，手写最小 JSON 无第三方依赖），跨重启保留，每个玩家各存一份。**自动清理**：箱子被打开（用上一 tick 的准星指向判断开的是哪个箱子）或被挖掉/替换（周期检查方块类型，带区块加载守卫，未加载区块不会被误判成被挖），则把世界高亮与 Xaero 路径点**一并删除**；只有真的删掉东西才写盘，避免每秒一次磁盘 IO。只标记这两样物品的箱子，其余战利品不记录位置。新增 `AncientChestStoreTest`（9 条：落盘往返、去重与标签更新、按水平坐标删除、作用域隔离、三维半径筛选含负坐标、坏文件降级、空记录、特殊字符转义），38 套件 270 测试全绿。⚠️ Xaero 颜色索引 6/9/13 未在装 Xaero 的客户端实机核对，落点颜色不对改三个常量即可 |
| FO-V6.10.jar | V6.10 | 修复 **Xaero 路径点联动全线失效**（表现为装了 Xaero 却提示「Xaero地图模组未安装」）。根因：旧桥要求 6 个类，其中两个不存在——`xaero.common.minimap.waypoints.WaypointSet`（26.x 已删除，现为 `xaero.hud.minimap.waypoint.set.WaypointSet`）与 `xaero.map.mods.SupportMods`（属于「Xaero 的世界地图」这个独立模组，小地图并不自带），且是「缺一个就整体判定未安装」的全有或全无写法；两个类分属新旧两代架构，拼不出任何能成立的版本组合，因此在所有现行 Xaero 上都误报。**重写 `XaeroWaypointBridge`**：① 分层探测，把「没装 Xaero」与「装了但版本对不上」分开报，不再混成一句「未安装」；② 两条写入通道运行时自动选——**官方第三方接口** `ThirdPartyWaypoints`（26.x 起，路径点挂独立来源、不写进玩家自己的路径点文件，默认即启用）与**玩家路径点集** `WaypointSet`（24.7.1 起，等同于玩家自己按 B 加的点，可管理可传送），新增设置项「路径点写入方式」（自动/仅官方/仅路径点集）以备切换；③ 颜色改用 `WaypointColor` 枚举按名取（GOLD/BLUE/PURPLE），**不再靠数字索引猜**——此前遗留的「颜色索引未实机核对」风险项就此消除；④ 删除时两条通道都试一遍并各自兜异常，任一成功即算删除；⑤ Identifier 类型从方法签名反推（生产环境是 intermediary 命名空间，硬编码 `net.minecraft.util.Identifier` 会在正式游戏里找不到类），并优先挑 `of` 而非 `ofVanilla`。新增 `XaeroWaypointBridgeTest`：测试类路径上放 13 个**按真实 jar 抄签名的仿冒 Xaero 类**，把反射管线完整跑通（含 `remove(Waypoint)` 与 `remove(int)` 重载选型、`setSetChangedTime(long)` 基本类型参数、按坐标去重、双通道删除），10 条全绿。39 套件 280 测试全绿 |
| FO-V6.11.jar | V6.11 | **紧急修复 V6.10 崩溃**（实机表现：游戏一搜就整个崩掉，`ConcurrentModificationException` 抛在 Xaero 渲染线程）。根因：搜索跑在 `CompletableFuture` 后台线程，V6.10 修好路径点桥后，后台线程一路直接调到了 Xaero 的 `ThirdPartyWaypoints.add(...)`；而 Xaero 的渲染线程每帧都在遍历同一张 HashMap，于是渲染线程抛 CME 整局崩溃 —— 即 V6.9/V6.10 的桥本身没问题，问题是**没人管线程约束**（这个隐患从 misaka 原实现继承而来，只是因为旧桥一直没生效才没暴露）。**修法**：确立「后台线程只算、共享状态只在客户端线程改」——搜索结果先进 `pendingMarks` 队列，由 `TickEvent.Post`（客户端线程 = 渲染线程）出队写入本地记录与 Xaero。另外给 `AncientChestStore` 的实例方法加 `synchronized` 作兜底（它原本是普通 LinkedHashMap + ArrayList，后台写与客户端线程清理会打架，属于同一类隐患）。新增两条并发回归测试（并发写入断言**条数精确相等**、并发删除断言**删除数精确相等**）——刻意用「精确计数」而不是「不抛异常」，因为 `marks()` 返回拷贝不会触发 fail-fast，无锁版本往往不报错、只是**悄悄丢记录**；已实测：去掉 `synchronized` 这两条会分别抛 ConcurrentModificationException 与 NullPointerException。39 套件 282 测试全绿 |
| FO-V6.12.jar | V6.12 | **实现「>= 26.1」的战利品表**（此前只打提示、实际仍按 1.21.11 掷骰）。用户实机反馈：单人档（1.21.11）位置与物品都对，3C3U 服（≥26.1）**位置对但物品错**——位置只取决于世界种子，物品取决于战利品表，症状直接指向版本差异。**对着 4 份官方客户端 jar 实测**（1.21.11 / 26.1.2 / 26.2 / 26.3）：26.x 的 `chests/ancient_city.json` 比 1.21.11 **只少一个 `minecraft:name_tag` 条目**（池权重 86→84，条目 28→27），而少这一个就足以让**每一次加权抽取都错位**；26.3 虽把 JSON 键名换成 `modifier`（`{"function":X}` → `{"type":X}`），但三版语义完全相同。冰窖表、43 个附魔（仅 `effects` 运行时字段变化，掷骰不读）、22 个附魔标签（逐字节相同）、32 个可达物品标签**全部一致**，因此只有古城表需要按版本切换。新增 `fo/ancient/v26/loot_table/chests/`（26.1.2 官方文件逐字节），`AncientResourceSource.bundledLootTables(from26)` 按「游戏版本」设置选表，模块启动时打印实际用的表。新增 `AncientLootEvaluatorV26Test`：锁定 5 座城 113 箱的生产模式黄金数据 + 两张表的条目数/权重/name_tag 差异 + 冰窖表一致。40 套件 289 测试全绿。**旁证**：参考实现 zychen027 自带的就是 26.1.2 官方表（逐字节相同），同样是兼容模式对比时，我们走 26.x 表只差 3 箱、走 1.21.11 表差 24 箱 |
| FO-V6.13.jar | V6.13 | **「游戏版本」扩成四档 + 修复实机「别的箱子也高亮」。**① **四档版本变体**：对着 1.20.6 / 1.21.4 / 1.21.5 / 1.21.6 / 1.21.9 / 1.21.11 / 26.1.2 / 26.3 八份官方客户端 jar 逐条目实测，古城表在版本间只有三处差异——≤1.20.6 附魔用旧语法（treasure / enchantments 列表，候选池=全部附魔）；1.21.6 起池 0 第 8 条由 saddle（set_count 常量 1，**不消耗随机数**）变成 leather（uniform 1~5，**消耗一次**）；≥26.1 少一个 name_tag（权重 86→84）。设置项改为 ≤1.20.6 / 1.21~1.21.5 / 1.21.6~1.21.11 / ≥26.1 四档（原 BELOW_26_1 常量名保留以兼容老配置），新增 v1206 / v1215 两套表资源，掷骰器支持旧语法（enchant_randomly 的 enchantments 列表回退、enchant_with_levels 无 options 时=全部附魔）。② **修复「不是标记位置的箱子也高亮」**：命中记录按「服务器+维度」分作用域但**不分种子**，且历次搜索是合并写入——换种子重搜后，旧记录留在文件里，只要那个位置恰好有真实箱子就会被高亮（清理检查只看「是不是箱子」）；现改为每轮搜索【整体替换】该作用域记录、记录 v2 格式携带种子（兼容 v1 旧文件）、渲染时按当前种子过滤（单人问内置服、联机退回已应用的种子设置；都拿不到则不过滤以保留跨重启高亮）。③ 依据 misaka 反编译源码的调研结论：其内置的「26.1 Xoroshiro 随机序列」掷骰路径与实机不符（用户实测 3C3U 服 ≥26.1 物品全部正确，即 26.x 仍是每箱 LootTableSeed + JRand），仅记录不实现。新增 AncientLootEvaluatorLegacyTest（两套新表 10 城 226 箱黄金数据 + 四变体结构锚点 + 旧语法行为）与种子持久化用例。41 套件 306 测试全绿 |
| FO-V6.14.jar | V6.14 | **目标柱子 + 路径点生命周期管理**。① **Baritone 风格目标柱**：给命中箱子画一根通到地表的同色细柱（金/蓝/紫，全宽 1/8 格），从箱子顶画到地表首个非空气方块上方——古城在 y≈-51 的地下，柱子走 Meteor `Render3DEvent` 的 `NO_DEPTH_TEST` 管线（对着 Meteor 源码确认），**穿墙可见**，站在地表就能看到落点；地表 Y 用 Heightmap 查询并按 (x,z) 缓存（维度切换时失效），不逐帧重扫；可见半径与「高亮半径」共用，可单独关。② **路径点写入方式默认改为「仅玩家路径点集」**（官方第三方接口代码保留，设置可切回）。③ **路径点与高亮完全同步**：每轮搜索【整体替换】记录时先把上一批写入的路径点撤掉（换种子不再残留旧点）。④ **关闭模块自动收点**：`onDeactivate` 按记录坐标把本模块写入的路径点全部移除（不碰玩家手动加的点），本地记录保留。⑤ **重开模块恢复**：开启时验证本地记录——种子对不上整批清空；被挖/被替换的删；「确认被开过」的删（原理：原版区块包会带箱子的 LootTable 字段，开箱生成战利品后服务端将其置空，对着 MC 字节码确认 `LootableInventory.generateLoot` 首行即 `setLootTable(null)`）；采用**自适应策略**（`AncientOpenedChestPolicy`）：只有确认服务端会下发该字段（见过至少一个非空）后，才允许凭 null 判「已开」，避免服务端剥离 NBT 时整批误删。日常清理循环也接入同一策略，能抓到「关模块期间被开、区块已重载」的箱子。已知限制：开箱后区块未重载时客户端块实体字段是旧值，那种情况检测不到（模块开着时开的箱子不受影响，当时即删）。新增 `AncientOpenedChestPolicyTest`。42 套件 309 测试全绿 |
| FO-V6.15.jar | V6.15 | **默认参数按用户使用习惯调整**（仅改默认值，逻辑零变动）：「搜索类型」默认由 附魔金苹果 → **两者都搜索**（`LootTarget.BOTH`，任一命中即收）；「游戏版本」默认由 1.21.6~1.21.11 → **≥ 26.1**（对齐用户常玩的 3C3U 服，单人档需手动改回）；「高亮半径」默认由 64 → **256 格**（柱子与高亮共用此半径）。注意：默认值只影响**全新安装/未保存过设置**的情况，已保存过配置的客户端不会被改动。42 套件 309 测试全绿 |
| FO-V6.16.jar | V6.16 | **路径点只在世界地图显示（视角与小地图不再显示）**。利用 Xaero 26.x 的 `WaypointVisibilityType`（LOCAL/GLOBAL/WORLD_MAP_LOCAL/WORLD_MAP_GLOBAL，即路径点编辑界面里的「世界地图本地/全局」选项），写入时把可见性设为 **`WORLD_MAP_LOCAL`**——对着真实 jar 的过滤字节码（`AbstractWaypointRenderProvider`）核实：小地图与世界内渲染器（视角里的点/光柱）对这两种可见性直接跳过，只有世界地图界面显示。选「本地」而非「全局」：记录本就按服务器+维度存储，GLOBAL 会把这几百个点显示到该服务器所有维度的地图上。官方第三方接口与玩家路径点集两条通道都生效；重复写入已存在的点时幂等补设；Xaero < 26.x 没有该枚举，反射取不到则静默保持默认行为。仿冒 Xaero 测试类补 `WaypointVisibilityType` 与 `Waypoint.setVisibility/getVisibility`，新增断言用例覆盖两条通道与幂等分支。42 套件 310 测试全绿 |
| FO-V6.17.jar | V6.17 | **修复实机「打开背包后附近箱子的高亮/光柱消失且不恢复」**。根因：V6.9 的开箱判定是「有容器界面弹出 → 把上一 tick 准星指向的方块当成开箱位置 → 删标记」，而玩家背包(E)也是 `HandledScreen`（`RecipeBookScreen<PlayerScreenHandler>`），走到箱子前按 E 开背包就被误判成开箱，标记被从本地记录删掉（V6.15 高亮半径改 256 后近距离操作变多才暴露）。修复：① 显式排除 `InventoryScreen`；② 指向记录带 10 tick 时效，防止过期指向被无关 GUI 触发。③ 顺带（用户确认）：**开双箱时另一半的标记一并删**——搭档方向照抄原版 `ChestBlock` 字节码（`CHEST_TYPE == LEFT ? facing.rotateYClockwise() : facing.rotateYCounterclockwise()`），只按这一个方向找搭档，不会误伤相邻摆放的同朝向单箱/另一组双箱。42 套件 310 测试全绿 |
| FO-V6.18.jar | V6.18 | **自动扔垃圾：misaka 式批量丢弃 + 附魔书等级过滤**。① **批量丢弃**：此前每轮扫描只扔一种（循环 break），捡到一个扔一个、眼花缭乱——对照 misaka 反编译源码确认其行为是「每 N tick 全背包扫一遍、把所有命中的堆一次全扔」，已照此改造：「丢弃间隔」语义变为扫描间隔（0~60 tick，默认 20），每轮把所有命中堆在同一个 tick 内全部扔出。② **附魔书过滤**（新设置组，参考 xiaohe66-meteor-lotus 「按附魔等级分价值」的思路自研，未复制其 GPL 代码）：四种模式——跟随名单 / 保留等级达标的书 / 全部丢弃 / 全部保留，加等级阈值（1~5，默认 3：古城的迅捷潜行为 1~3 级，阈值 3 = 只留满级书）。等级从物品的 `ItemEnchantmentsComponent` 读取（取堆内最高等级，附魔台合并的多附魔书按最高级保留，宁留勿误删）。③ 顺手两处防误丢：开着其他界面（箱子等）时暂停扫描（槽位映射按玩家背包设计，界面打开时会错位）；白名单为空的提示改为只提示一次（原实现每 tick 刷聊天栏）。新增 TrashLogic.decideBook 纯逻辑与 4 条测试。42 套件 314 测试全绿 |
| FO-V6.19.jar | V6.19 | **修复实机「重开模块/重新搜索后，开过的箱子又被加回路径点与高亮」**（方案经用户确认后实施）。根因：开过的箱子只被从记录里**删除**，「已开」这一信息没有持久化；重开模块时「自动搜索」按种子重扫，开过的箱子方块还在（世界生成不看箱子是否被开过）照样命中，整批替换落地时全部写回。修复：**存储升级 v3**——每个作用域新增「已开名单」（`opened` 坐标集合，永久保留不清理：箱子被挖后死记录反而让搜索跳过该位置、不给空气标点；换种子=换世界时随记录整份清空），**三个检测点**（开箱判定 / 恢复记录的 lootTable 检测 / 周期清理的 lootTable 检测）命中时都把坐标记入名单；**新搜索结果落地时跳过名单中的坐标**（不写标记、不恢复高亮、不加路径点），搜索完成输出「已跳过 N 个开过的箱子」。行为变化：误开的箱子不再能靠重搜恢复（用户已确认接受）。`AncientChestStore` 新增 `clearMarks`（只清标记、保留种子与已开名单）与 `clear`（全清，换种子用）两级语义；v1/v2 旧文件兼容读取（已开名单为空，之后重新学习）。顺手加固 `AncientJson`：不平衡 JSON 此前会裸抛 `StringIndexOutOfBoundsException`（本版排查时被自己写错的测试JSON 触发暴露），现给出带信息的异常且 `fromJson` 退化安全。新增存储 v3 / 已开名单持久化 / clearMarks 语义 / v2 兼容 / 不平衡 JSON 退化 共 6 条测试。42 套件 319 测试全绿 |
| FO-V6.20.jar | V6.20 | **自动扔垃圾「附魔书过滤」重做**（旧版只有一个全局等级阈值管所有附魔书、无法按附魔分规则，用户反馈鸡肋；方案经用户确认后实施）。删除旧设置「附魔书模式 / 附魔等级阈值」，改为「**附魔书规则**」文本列表（StringListSetting，游戏内直接打中文），每行一条「附魔中文名 + 可选最低等级」：`迅捷潜行3` = 迅捷潜行≥3 命中，`经验修补` = 任意等级命中（不写数字=任意）；支持模糊（「潜行」→ 迅捷潜行，「保护」→ 全部保护系，一匹配多），也认英文 ID（`minecraft:swift_sneak 3`）；识别不了的行聊天栏提示一次。**打通黑白名单**：命中任一规则（书内任一附魔达标即中，合并书宽松保留）视同「在名单里」，白名单=保留、黑名单=丢弃；规则为空时附魔书完全跟随物品名单；整本全留/全丢 = 物品列表加/不加「附魔书」物品。纯逻辑 `TrashLogic.parseRuleLine`/`bookMatches` + `BookRuleIndex.resolve` 可单测，新增 18 条断言（解析/命中/目录匹配/端到端） |
| FO-V6.21.jar | V6.21 | **附魔书规则改为每附魔一条设置**（V6.20 的文本规则要用户打字，用户不认可；方案经用户确认后实施）。删除文本规则（`BookRuleIndex` 类与 `parseRuleLine` 解析随之删除），改为 `EnchantList` 数据表驱动的 **43 个原版附魔、每附魔一条独立等级设置**（设置组「附魔书过滤」，中文名滑条，上限=该附魔真实最高等级；0=不启用 / 1=任意等级命中 / 2~max=≥该等级命中），全部在设置界面点选、无需打字。**数据逐条核实**：清单与最高等级对照官方客户端 jar 的 `data/minecraft/enchantment/*.json`（1.21.11 与 26.1.2 一致，共 43 个，含 26.x 新附魔 lunge=突进）；中文名取自 26.1.2 官方 zh_cn 语言文件（客户端 jar 不带语言文件，需外取）。判定逻辑不变：书内任一附魔达标即命中 → 视同「在名单里」走黑白名单；全部 0 = 跟随物品名单；整本全留/全丢 = 物品列表加/不加「附魔书」物品。新增 `EnchantListTest`（43 个 ID 官方清单锚点、中文名无重复且纯中文、等级越界、关键附魔抽查）；命中判定断言保留（合并书/等级达标/空规则）。MC 未来新增附魔需同步更新 `EnchantList` 与锚点测试 |

> 备注：8430096（AutoTrash 白名单修复）的云端交付链接已失效，
> 本地无 jar 副本；其源码仍完整保留在 Git 历史中（commit 8430096），
> 如需可随时重建。

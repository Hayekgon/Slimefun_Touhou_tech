# Touhou（TH Tech）进度总览 + 知识索引

> **这份文件是给"下一个没有上下文的会话"看的唯一入口。** 读完它就能开工；
> 细节按 §3 的知识索引**按需加载**，不要一次全读（`modules\08` 单篇 1500 行）。
>
> 建立时间：本轮；**版本 v1.0.1**。本文件不含产品代码，只做索引与状态快照。
> **凡本文件与其它文档冲突，以 `SKILL.md` / `modules\*` / 源码为准，并回来改这里。**

---

## 1. 一句话现状

**一个独立的 Slimefun4 附属（`com.example.touhou` / 插件名 `Touhou`），Paper 1.20.4-499 + Slimefun 2026.07（GuguProject fork），
`javac` 直编 + 手工打包、无 Maven。内容涵盖反应堆与赛钱箱两套多方块、自研 POWER 能源网络、两件符卡道具、
数台单方块机器，以及大量 INFO 展示物。整体处于"物品体系基本齐、反应堆结构件配方仍缺、弹幕方向暂缓"的状态。**

| 项 | 值 | 出处 |
|---|---|---|
| 版本 | **1.0.1** | `build.ps1` 的 `param([string]$Version = "1.0.1")` —— **唯一出处**，被替换进打进 jar 的 `plugin.yml` 的 `${project.version}` |
| 产物 | `Touhou-1.0.1.jar` | `build.ps1` 打包段 |
| 源码规模 | `AddSlimefunItems` 里 **38 个 `register(` 调用**、`AddItems` 里 **38 个模板字段**（本轮实点，与下面读数一致） | `src\main\java\com\example\touhou\core\AddSlimefunItems.java` / `AddItems.java` |

### 1.1 运行期读数（**计数只认这个口径**）

```
[TOUHOU] items total=38 withRealRecipe=17 noRecipe=21
[TOUHOU] items byMachine={ENHANCED_CRAFTING_TABLE=5, MAGIC_WORKBENCH=12}
[TOUHOU] acquisition miss=0 loreMiss=0 table=38
[TOUHOU] altar list count=3
[TOUHOU] altar test result=OK recipes=3
```

★ **上面这五行是本轮（`docs\PROGRESS.md` 建立时）用无头服重新采集的**，不是抄文档：
部署的 `Touhou-1.0.1.jar` 与 `build.ps1` 的产物**哈希/大小/时间戳一致**（562245 B / 同一次构建），
用 `D:\DS_work\slimefun\_tools\headless_run.ps1 -CommandsFile <清单>` 起服后发
`touhou item all` / `touhou acquisition all` / `touhou altar list` / `touhou altar test` / `touhou groups`，
按 **GBK(936)** 解码 `logs\latest.log` 逐个抓出来；**测完已优雅 `stop`**（日志末尾 `Closing Server`）。
交叉印证：`acquisition` 的 `[OK] TOUHOU_…` 行数 = **38** = `table=38`；
`item all` 的 `byMachine` 两项相加 = 17；源码实点 `register(` = 38 个、模板字段 = 38 个。

| 项 | 数 | 可自行求和核对 |
|---|---|---|
| 物品总数 | **38** | 幻想之物 8 + 幻想之缘起 4 + 多方块 9 + 单方块 1 + 符卡 2 + INFO 9 + POWER 5 |
| 有真配方 | **17** | 增强型工作台 **5** + 魔法工作台 **12** |
| 没有任何配方 | **21** | 38 − 17 = 机制获得 2（落叶 / 回响）+ 炙热的灰烬 1 + 反应堆构件 6 + INFO 9 + 祭坛祈愿产出 3 |
| 其中"暂未开放"屏障（= 真正待补） | **7** | 炙热的灰烬 1 + 反应堆构件 6（`Acquisition` 里登记为 `Source.pending()`） |
| 祭坛祈愿配方 | **3** | 空白符卡 / 灵梦的大蝴蝶结 / 梦想封印 集 |

**★ 读数来源与采集方式（后来者照此复采，别抄本文件的数字）**：

- 权威出处 = **`docs\todo-items.md` §0「当前真实状态（实机读数，先看这里）」**，那一节是从
  `/touhou item all` + `/touhou acquisition all` + `/touhou guide` + `/touhou altar list|test` 抄下来的原文；
  `docs\touhou-tree.md` 文末「四、怎么自己复核这份树」给了同一套命令。
- 复采最短路径（**必须是先构建部署、再起服发命令**，见 §2）：
  ```powershell
  powershell -NoProfile -ExecutionPolicy Bypass -File D:\DS_work\slimefun\_tools\headless_run.ps1 `
      -CommandsFile D:\DS_work\slimefun\_tools\count_cmds.txt
  ```
  清单内容（纯 ASCII，一行一条）：
  ```
  touhou item all
  touhou acquisition all
  touhou altar list
  touhou altar test
  touhou guide
  ```
- **数据源文件（唯一权威）**：`docs\todo-items.md`（最新、逐项复核过）>
  `docs\touhou-tree.md`（**部分计数已过期且自相矛盾，见 §1.2**）> 本文件。

### 1.2 ★ 文档计数口径（矛盾已就地修掉，本轮）

**★★ 规矩**：**所有计数一律以运行期读数为准** ——
`/touhou item all` 的 `total / withRealRecipe / noRecipe` + `byMachine={…}`、
`/touhou acquisition all` 的 `table=<N>`、`/touhou altar list` 的 `count=<N>`。
**不许拿另一份文档的数字当依据**（手写计数本项目已经错过多次：32 / 36 / 18 / 0 都出现过）。

**★ 本轮用源码实点 + 运行期读数复核的一处**：`AddSlimefunItems.setup()` 里用
`RecipeType.ENHANCED_CRAFTING_TABLE` 的物品是
`SPRING_MUD`(338) / `POINT`(401) / `P_ENGINE`(413) / `UTSUHO_REACTOR_CORE`(488) / `MURDEROUS_LILY`(591)
—— **这 5 件**（`FANTASY_SEAL` 已改走祭坛门面，`lilyRecipe` 的竖条现在是唯一一条）。
加上 **12** 件魔法工作台 = **17**，与 `todo-items.md` §0 的 `ENHANCED_CRAFTING_TABLE=5` / `withRealRecipe=17` **自洽**。

| 位置 | 曾经写的 | 现值（运行期口径） | 处置 |
|---|---|---|---|
| `docs\touhou-tree.md`「一、」复核实录 | `items total=36 withRealRecipe=18 noRecipe=18` + `byMachine={ENHANCED_CRAFTING_TABLE=6, …}` | **38 / 17 / 21** + `{ENHANCED_CRAFTING_TABLE=5, MAGIC_WORKBENCH=12}` | ★ **已就地改成现值**，并补上"三个数都能自行求和核对"的算式 |
| `docs\touhou-tree.md`「一、」第三轮沿革 | 真配方 **18 → 17**、无配方 **18 → 21** | **11 → 17**、**25 → 21** | ★ **已就地改正**（2026-09-24 那轮的正确值是 11；读数 `withRealRecipe=18` 与它自己写的"无配方 25"对不上，18 这个数从未存在过） |
| `docs\touhou-tree.md`「三、」上方沿革 | 真配方 11 → **18**、无配方 25 → **18** | **11 → 17**、**25 → 21** | ★ **已就地改正** |
| `docs\touhou-tree.md`「四、」复核命令注释 | 注释里的统计示例 `total=36 … ENHANCED_CRAFTING_TABLE=6` | **38 / 17 / 21**、`=5` | ★ **已就地改成现值**，并注明"本文件其它地方的计数若与它不符，以它为准" |
| `docs\touhou-tree.md`「四、」最后一行 | "SaizenbakoRecipes 的条数（**现为 0**）" | 祭坛配方 **3** 条 | ★ **已就地改成 3**（与「一、」的 `count=3` 一致） |
| `docs\touhou-tree.md`「三、」按机器归并表 | 增强型工作台 5 / 魔法 12 / 祭坛 3 / 无配方 21 | **与现值一致**（5 + 12 = 17、38 − 17 = 21） | 表本身**对**；已补一行"求和口径"，把三处互印的算式写出来 |
| `docs\todo-items.md` 开头沿革 | 真配方记成 **18** | **17**（2026-09-24 那轮是 **11**） | ★ **已就地加一条更正注记**（18 从未存在过） |
| `docs\todo-items.md` §B 小标题 | "提交 `61a5e7e`（**2026-09-24**）" 却挂着 5 件 POWER 设备的魔法工作台配方 | 那段是 2026-09-25 那轮的事（§0 与 §C 都写 2026-09-25） | ⚠ **日期存疑、未改**（见下方"判不准"清单） |

**★ 判不准、故意没改的（清单）**：

1. `docs\touhou-tree.md`「三、」里 **2026-09-24 那轮的沿革句**仍写着"真配方 11 → **17**、无配方 25 → **21**…
   当天读数 `total=36 withRealRecipe=18 noRecipe=18` 已废弃" —— 这是**历史段**，改了就篡改历史；
   但它现在与"那天到底是多少"存在两种说法（11 还是 18）。**只保留现值与废弃标记，不再追认历史。**
2. `docs\todo-items.md` §B 的日期（2026-09-24 vs 09-25）：`61a5e7e` 的实际提交时间需要
   `git show -s --format=%ci 61a5e7e` 才能定，本轮**没查**（不在计数范围内）。
3. `docs\touhou-tree.md:80` 的"6 个 1 级组"：`AddGroups` 里 1 级组是
   MATERIAL / CHARACTER / MACHINE / PARTY_ITEM / INFO / POWER = **6 个**，
   另加 0 级 `TH_TECH` 与 2 级的 COMPLEX/SIMPLE —— **自洽**，未改。
4. `docs\todo-items.md` §D 的"25 件自有物品从不出现在任何配方里（38 − 13…）"：
   38 − 13 = **25**、且正文列举出来正好 **25 件** —— **自洽**，未改。

---

## 2. 15 分钟上手

### 2.1 读什么（按顺序，前两步必做）

1. **本文件**（§1 状态 → §3 索引 → §4 硬规则）。
2. **`SKILL.md`**（顶层）前两节 + §3 十三条硬规则 + §5 维护约定。
3. 按任务挑模块（§3 表）。**任何编码任务都带上 `modules\08-pitfalls.md` 与 `modules\01` 前两节**；
   任何"要跑起来才算完"的任务带上 `modules\07`。
4. 仓库内两份人读快照：`docs\touhou-tree.md`（全物品与配方）、`docs\todo-items.md`（缺口清单）。

> ⚠ **子代理看不到 skill 目录**：派活时必须把相关模块**正文粘进提示词**（配方见 `SKILL.md` §1）。

### 2.2 最短路径（构建 → 部署 → 无头验证）

```powershell
# 0) 工程根 / 测试服
#    工程： D:\DS_work\slimefun\_fromscratch-touhou
#    服务： D:\MC\Server\paper   (Paper 1.20.4-499 / Java 25 / Slimefun 2026.07-release)

# 1) 编译 + 打包（每次先删 out\ 与 build\，避免增量编译"混合态"）
cd D:\DS_work\slimefun\_fromscratch-touhou
powershell -ExecutionPolicy Bypass -File .\build.ps1
#    → 期望 "javac OK" + "package OK -> ...\Touhou-1.0.1.jar"
#    → 参数：-Root / -Lib / -ExtraLib / -Version / -SkipPackage

# 2) 部署（★ 服务端必须先停！运行中占用 jar 会导致复制失败或半写）
Copy-Item .\Touhou-1.0.1.jar D:\MC\Server\paper\plugins\ -Force
(Get-FileHash .\Touhou-1.0.1.jar -Algorithm MD5).Hash
(Get-FileHash D:\MC\Server\paper\plugins\Touhou-1.0.1.jar -Algorithm MD5).Hash   # 两串必须相同

# 3) 无头验证（起服 → 轮询日志就绪 → 逐条发命令 → stop；清单必须纯 ASCII）
Set-Content D:\DS_work\slimefun\_tools\my_cmds.txt -Encoding ASCII -Value @'
touhou acquisition all
touhou item all
'@
powershell -NoProfile -ExecutionPolicy Bypass -File D:\DS_work\slimefun\_tools\headless_run.ps1 `
    -CommandsFile D:\DS_work\slimefun\_tools\my_cmds.txt [-TimeoutSec 180]
```

> ★ **工具位置注意**：harness 在**工作区根的 `D:\DS_work\slimefun\_tools\`**，
> **不是** `_fromscratch-touhou\_tools\`（后者不存在）。项目自己的脚本在 `_fromscratch-touhou\tools\`。

### 2.3 不起服务端的验证（优先做）

布局 / 常量 / 纯逻辑这类东西，写进 `_verify_src\` 用**普通 Java 程序直接调产品代码**，秒级出结果
（编译运行方式与 `_lib` 组装见 `modules\07` §7.3；**必须加 `-Dstdout.encoding=UTF-8`**）。
现有 4 个：`LayoutVerify` / `Grid` / `RotateVerify` / `DirectionDetectVerify`，
另有 `CheckAcquisition` / `CheckAcquisitionFacade`（获取方式门面核查）。

**判"成功"的层次**（只看前两层等于没验）：插件加载 → enable → 物品注册（`describe()` 全 `=OK`）
→ **功能真的对**（自检命令结论）→ **优化真的生效**（计数器差值）。

---

## 3. 知识索引（**本文件的核心**）

> 用法：先看"我要干什么"，只读那一篇的**那一节**。
> 路径相对 `D:\DS_work\slimefun\`（工作区根）；skill 在 `.dsh\skills\touhou-dev\`。

| 我要干什么 | 读哪个文件 / 模块（什么时候读它） | 里面最关键的一条 |
|---|---|---|
| **开工第一件事**：搞清工程目录、启动顺序、构建 | `.dsh\skills\touhou-dev\modules\01-project-skeleton.md` §2 | 注册顺序**不能乱**：`saveDefaultConfig → AddGroups → AddItems → TouhouRecipeTypes → AddSlimefunItems → SaizenbakoRecipes → 监听器 → 自检/充能 → 结构预热`；`SlimefunItem#register` 返回 **void** |
| 新建物品组 / 怀疑"组跑错位、主菜单多一格" | `modules\01` §3.1 / §3.2 | `NestedItemGroup extends FlexItemGroup` ⇒ **每个都会在指南主菜单顶层占一格**；实测：老方案会让"TH Tech"与"机器"并列 |
| **新建/改一件物品**（模板、材质、名字） | `modules\02-item-registration.md` §1 | `SlimefunItemStack` 有 **13 条构造器**；最常用 `(id, Material, name, lore…)`；多产出用 `(SlimefunItemStack, int)` |
| 头贴图 / 头颅 Value | `modules\02` §2 | `"ey"` 开头 = base64 **原样传**；64 位十六进制 = Slimefun 自动拼 URL 包 base64；**其它字符串直接抛异常** |
| 渐变物品名（`§x§R§R§G§G§B§B`） | `modules\02` §3 | **不能写 `&x`** —— `translateAlternateColorCodes('&',…)` 不认它，玩家会看到字面 `&x&f…`；要用 `ChatColor.of("#rrggbb")` |
| 物品 lore 约定 / "现算" lore | `modules\02` §4 | 第一行固定 `""`；参数放最后用 `&8`；`applyStructureLore` 必须在 `setup()` **最后**调，且数据源是 `AddonConfig` 的层图，**不是** `ReactorManager.structure()`（那时是 null，会毁掉启用） |
| 附魔光效 / 要不要 `HIDE_ENCHANTS` | `modules\02` §5 | 只要光效 = 无用附魔 + `HIDE_ENCHANTS`；要看到附魔 = 挂**有意义**的附魔（炙热灰烬用 `FIRE_ASPECT(15)`） |
| **★ 每件物品必须标注「获取方式」** | `modules\02` §8（唯一总表）；代码 `Acquisition.SOURCE_BY_ID` | 写在**指南页槽 10**（不是物品 lore）；静态块里图标**必须惰性** `Source.recipe("…", () -> SlimefunItems.X)`，否则 `ExceptionInInitializerError` |
| 加配方 / 选配方类型 | `modules\03-recipes-and-guide-pages.md` §3（24 种本体 `RecipeType` 全表） | `RecipeType#register` **只有两条路**（`registerConsumer` / `machine` 是 `MultiBlockMachine`）；两条都不通就什么都不注册 |
| **一次合成出多个** | `modules\03` §2 | **必须用 `SlimefunItem` 第 5 参数 `recipeOutput`**，模板数量保持 1（改模板会触发本体 `illegal stack size` 告警并连累 `/sf give`） |
| 指南页槽 10 是空气 / 想显示"这台机器是什么" | `modules\03` §4 | 门面型自定义 `RecipeType`（`namespace = 插件名`、`callback = null`）；**不会**让物品变成可合成 |
| 自定义粘液书配方页 | `modules\03` §5 | 实现 `RecipeDisplayItem#getDisplayRecipes()`；`RecipePages.block` 对齐规则"输入靠上、输出靠下"；条目**必须 `clone()` 再改**（模板被 `lock()`） |
| 排查"配方撞车 / 合不出来" | `modules\03` §6 | 增强工作台按 **9 格图案 + 配方类型**匹配 ⇒ **同图案同类型，后注册覆盖先注册** |
| 写一台机器 / 加按钮 | `modules\04-machines-and-gui.md` §1、§3、§9 | 三种钩子选一种：自有界面 `BlockMenuPreset` / 右键做事 `BlockUseHandler` / 定时 `BlockTicker`；**`BlockUseHandler` 与自有界面互斥** |
| **★ GUI 槽位安全（占位符被拿走）** | `modules\04` §3 全文 | 一律 `GuiLock.wrap(preset)` + `markRealSlot(...)` + 结尾 **`autoGuard()`**；`MenuListener` 是 `setCancelled(!onClick(...))` ⇒ **`return true` = 放行**（与直觉相反） |
| 拖拽 / 双击 / 背包 Shift 绕过 | `modules\04` §4 | 必须靠全局 `PortGuiListener`，且**优先级 `HIGH`**（`LOW` 会被 Slimefun 的 `NORMAL` 重新放开）；**新机器要加进 `covers(...)`** |
| 机器 GUI 布局 / 进度条槽位 | `modules\04` §5 | 进度条第 **22 号槽是本体硬编码**的 ⇒ 产物输出区必须避开 22；`inv.hasViewer()` 才写 GUI |
| 面向玩家的消息 / 刷屏 | `modules\04` §6 | 一律走 `Notify`（四档）；**玩家点击的直接反馈要用 `warn`**（`info` 默认被静默）；每台机器自己的 `Scope` |
| 方块数据读写 | `modules\04` §7 + `modules\09` §6（key 总表） | `loc == null` 一律当"读不到/不用写"；用 `StorageCacheUtils.getBlock(loc)`；`isDataLoaded()==false` **不能当空值** |
| **碰自研 POWER 能源** | `modules\05-power-network.md` §1、§2、§3 | 节点类型 4 个，`GENERATOR` **只捐不取**；组网 = 6 面邻接 + **切比雪夫（立方体）**跳接 + 反向扫一次 |
| 排查"机器不发电 / 电网不通" | `modules\05` §2、§11 | `REVERSE_SCAN_RADIUS = 8` 是**硬常量且无校验**，把 `jump-range`/`range` 设 >8 会**静默**破坏发现对称性 |
| 加一台 POWER 设备 | `modules\05` §7 | 继承 `AbstractPowerBlock`（已挂 ticker + 放置/破坏钩子），**不要再自己注册 item handler**；产能逻辑写在 `onPowerTick` |
| **加/改多方块结构、层图** | `modules\06-multiblock.md` §2、§3 | 层图必须**嵌套列表**（每层一个 `- -`）；`C` 可在任意位置（偏心要 `allowOffCenterCore=true`）；**`.` 不能当 legend 键**（用 `_`） |
| 结构"转 90° 就不认" / 朝向 | `modules\06` §3 | `Direction.rotate`：`NORTH (dx,dz)` / `EAST (-dz,dx)` / `SOUTH (-dx,-dz)` / `WEST (dz,-dx)`；枚举序号 = 探测序号 = 落盘数字 |
| `#标签`：接口可替代保护罩 | `modules\06` §4 | `ItemTags.tag("touhou:reactor_shell", …)` + **必须 `setDefaultDisplay`**（投影图标不能"取成员第一个"）；登记位置在 `AddItems.setup()` **末尾** |
| 结构归属登记（uid） | `modules\06` §5 | `touhou:mb-uid`（核心+构件共用）+ `touhou:mb-sta`（`0`/`1`/`-1`）；清空写**字面量 `"null"`** 不删键 |
| **结构检测（事件驱动）** | `modules\06` §6 | 事件 → `ReactorManager.onStructureEdited`（同 tick 去抖）；**判据：空闲时"结构检测次数/找核心扫描次数"必须是 0** |
| 多方块投影 | `modules\06` §7 | `render` 顺序**不可乱**：上限检查 → **`hide(loc)` 先清旧组** → 父实体 → 逐格 `ItemDisplay`；**禁用异步**（`CACHE` 是普通 `HashMap`） |
| 两个方向键搞混 | `modules\06` §7.5 | `touhou:structure-dir`（结构朝向）≠ `touhou:mb-holo-dir`（**只有投影写**）；`/touhou proj rotate` 会同时打两个键 |
| **构建 / 部署 / 读日志** | `modules\07-verification-toolchain.md` §1、§5、§9 | Paper 日志按 **GBK(936)** 解码；`Process.BeginOutputReadLine()` 抓 stdout 会**假死** ⇒ 读 `logs\latest.log` |
| 判定"验到什么程度" | `modules\07` §6、§9.5、§9.6 | 五层判据；**无头测不了就跳过并登记**（§9.6 有照抄即用的报告模板，这是硬规则 11） |
| **查任何"诡异现象"** | `modules\08-pitfalls.md`（先读；本文 §5 是它的索引） | 全部真机实测，附 `类:行号` |
| 用 `/touhou` 自检 / 加子命令 / 改配置 | `modules\09-command-reference.md` §2、§3、§4、§5、§6 | **参数下标整体前移一位**（`copyOfRange` 之后）；新增子命令必须**三处一起加** |
| 查某个数值在 `config.yml` 还是 `Items.yml` | `modules\09` §4（config 全表）+ §5（`ItemSetting` 全表） | `ItemSetting` 的值**优先于代码默认值**（硬规则 6） |
| **抄通用 Slimefun4 API / 读 LogiTech** | `modules\10-slimefun-api-and-logitech.md` §1、§2 | 「能编译过」≠「运行时被调用」（`AGenerator#getGeneratedOutput` 的**重载不是重写**，见 §4 硬规则相关条目） |
| **做激光 / 弹幕 / 命中判定 / 伤害** | `modules\11-hitscan-and-projectile.md` §1、§2、§5、§10 | 步进式射线 + 逐点谓词（`true` = 继续，`false` = 停）；**透明判据要单独抽函数**（`isAir()||isTransparent()||WATER/BUBBLE||LAVA`） |
| 范围伤害 / 追踪箭顺序 | `modules\11` §2.3 + `modules\08` §F.2 | **先**激光 → 喷泉 → **生成追踪箭**，**最后**才 `damageSphere` |
| **做 6×6（任意 >9 格）工作台** | `modules\12-large-workbench.md` §3、§4、§5、§6；骨架 `docs\large-workbench-template.java` | **`getInputSlots()` 的数组顺序 = 配方下标**（行优先、每行只取前 6 格）；空位必须是 `null` 占位 |
| 6×6 工作台的注册时序 | `modules\12` §5.2 | 本体四参 `RecipeType` 带回调但**没有"立即补发"** ⇒ 用推荐姿势 A（`TouhouRecipeTypes.setup()` 阶段就构造 `TYPE`）或对静态 `PENDING` 一次性认领 |
| 物品名 / 材料名要写中文 | `docs\material-names.md`（全表） + `/touhou names` | **一律用客户端官方译名**（钢筋板 / 起泡锭 / 黑金刚石 / 奇怪的下界粘液 / 魔法结晶 - III / GPS 发射器）；「布」= `SlimefunItems.CLOTH`（材质 `PAPER`）**不是**白色羊毛 |
| 查一个物品的全部现状 | `docs\touhou-tree.md`（全物品 + 3×3 + 产出 + 获取方式） | ★ 计数段**已过期**（§1.2），配方行仍可用 |
| 查"物品体系还差什么" | `docs\todo-items.md` §0 / §A / §E | 唯一大缺口 = **反应堆 6 件结构件无配方** |
| 重启弹幕方向 | `docs\danmaku-plan.md` + `modules\11` §10 | 落方块**关重力后真的无重力**（实测 y 单调上升），形状**不会挤压散架**（`maxDrift=0`），但**关重力 ≠ 无阻力**（每 tick ×≈0.85） |

---

## 4. 硬规则总表（一屏内）

> 出处：`.dsh\skills\touhou-dev\SKILL.md` §3 —— **共 13 条**（不是十条；已多次演进）。
> 违反后果一律写"实际踩过什么"。

| # | 一句话 | 违反后果 | 详情在哪 |
|---|---|---|---|
| 1 | 注册顺序不能乱：`AddGroups → AddItems → TouhouRecipeTypes → AddSlimefunItems`；`register` 返回 void | `PrematureCodeException`；物品组 namespace 不是插件名会被警告 | `modules\01` §2；`modules\10` §1.1 |
| 2 | 物品 id 铁律 `TOUHOU_"组"_"英文名"` 全大写；**发布后慎改** | 小写直接抛异常；改 id 让存档里旧物品变"未知物品"（「梦想封印 集」付过这个代价） | `modules\01` §4；`modules\08` §I.9 |
| 3 | GUI 一律 `GuiLock` 注册槽位，**默认锁死**，结尾必调 `autoGuard()` | 占位符能被拿走（**三个 GUI 各犯过一次**，都是玩家反馈才发现） | `modules\04` §3；`modules\08` §B.1 |
| 4 | 跨槽操作必须靠全局 `PortGuiListener`（拖拽 / 双击 / 背包 Shift），新机器要加进 `covers(...)` | 拖拽"涂"进锁死格、双击把占位玻璃板收集走 | `modules\04` §4；`modules\08` §B.3–B.6 |
| 5 | 面向玩家输出一律走 `Notify`（四档），每台机器自己的 `Scope` | 消息栏刷屏；祭坛提示顶着「[灵乌路空反应堆]」前缀 | `modules\04` §6；`modules\08` §B.11 |
| 6 | `ItemSetting` 的值**优先于**代码默认值，持久化在 `plugins\Slimefun\Items.yml` | **只改 Java 默认值不生效** —— 本项目已因此翻车多次（`capacity`/`range`/`max-seconds`/…） | `modules\09` §5；`modules\08` §C.1 |
| 7 | `saveDefaultConfig()` 只在文件不存在时生成 | 老 `config.yml` 不补新段 ⇒"配置改了没反应"（缺过 `lily:`/`seal:`/`supply:`/`echo:`） | `modules\09` §4.1；`modules\08` §C.2 |
| 8 | 结构检测是**事件驱动**的，空闲时必须 0 次检测 | 退化回轮询：实测空闲找核心 ≈9 次/秒、整场检测上千次 | `modules\06` §6；`modules\08` §D.7 |
| 9 | 读 Slimefun 方块数据时 `isDataLoaded()==false` **不能当空值** | "数据未加载"被当成"这里没有东西" ⇒ 跨区块网被判断、数床数成 0、结构误判 | `modules\04` §7；`modules\08` §D.8 |
| 10 | `RecipeType` 决定"怎么合成"与"显示在哪页"；**多产出必须用第 5 参数 `recipeOutput`** | 改模板数量会触发本体 `illegal stack size` 告警并连累 `/sf give` | `modules\03` §2、§4 |
| 11 | ★★ **无头测不了的，跳过并登记 —— 绝不绕路、绝不编证据** | 拿 mob 代替玩家、造假事件 ⇒ 用假证据把错的当成对的（最坏结果） | `modules\07` §9.6；`modules\08` §M.5 |
| 12 | ★★ 改完物品/配方**必须同步 `docs\touhou-tree.md`**，计数一律用**运行期读数**复核 | 手写计数会静默漂移（实测曾同时漂 4 处：32→36→38、INFO 8→9、无配方 20→25→21、待补 14→13→7） | `modules\08` §P.1；本文件 §1 |
| 13 | ★★ **玩家可见处禁止写日期 / 改动来历**（最多写注释） | 实测：粘液书配方页上玩家直接看到 `配方: 空白符卡（2026-09-25 用户给定）` | `SKILL.md` §3 条 13；`modules\08` §S |

**判据一句话（第 13 条）**：玩家读了这句话，会知道"这是某个日期改的 / 这是哪次需求加的"吗？会 ⇒ 违规。
玩家可见 = `sendMessage`（含**命令回显**）/ `Notify.*` / **物品 lore** / 指南页（含配方页、槽 10 文案）/
GUI 文案 / **配方展示**（`RecipePages`、`Saizenbako#getDisplayRecipes`、`SaizenbakoRecipe#note`）/
命令帮助行 / `plugin.yml` 的 description。

---

## 5. 踩坑索引（只留**仍然有效**的）

> **这里只做索引，不搬正文。** 每条 = 现象 → 根因 → 解法 → 详情位置。
> 已废弃方案只留一句"别再用它"，细节在模块里。

### 5.1 构造 / 注册

| 现象 | 根因 | 解法 | 详情 |
|---|---|---|---|
| 插件完全起不来，`Error occurred while enabling` | `BlockMenuPreset` **在构造器里**调 `init()`，那一刻 `Location` 必然是 `null` | `TouhouData` 所有读方法 `loc==null` 返回默认值、写方法直接 `return`；动态内容交给 `newInstance` / ticker | `modules\08` §A.1 |
| 不想踩上面那条 | — | **不需要界面的机器就别建 `BlockMenuPreset`** | `modules\08` §A.1 末尾 |
| 插件 enable 前建模板就炸 | `SlimefunItemStack` 构造器检查 `Slimefun.instance() == null` | 模板全在 `AddItems.setup()` 里建；字段只声明不 new | `modules\08` §A.2 |
| 注册后改物品 meta ⇒ **整个插件启用失败** | `SlimefunItem#register` 会 `lock()` 模板 | 所有改模板 meta 的代码写在**注册之前**（`AddItems.setup()` 内） | `modules\08` §K.1 |
| 4 个属性修饰符只生效 1~2 个（静默丢失） | `addAttributeModifier` 按 **UUID** 去重 | 每个属性一个稳定 UUID（`nameUUIDFromBytes`） | `modules\08` §K.3 |
| 属性诊断 4 项全报"缺失"但清单齐全 | `Attribute#getKey()` 返回**带命名空间**的完整键 | 比较前去掉 `minecraft:` 前缀 | `modules\08` §K.4 |
| 用了新版 API 文档里的常量/类 ⇒ 编译不过 | `EquipmentSlotGroup`（1.20.5+）、`EquipmentSlot.MAIN_HAND`（1.20.5+）、`Particle.HAPPY_VILLAGER`（1.20.5+）在 1.20.4 **都不存在** | 本版用 `EquipmentSlot.HAND`、两参 `addAttributeModifier`、`Particle.VILLAGER_HAPPY`；**凭记忆写常量名 = 高危动作，一律先 `javap` 运行期 jar** | `modules\08` §K.2、§N.5、§I.7 |
| 覆盖了本体的 `getGeneratedOutput` 但机器行为不对（模式/阈值不生效、进度条由本体画） | 编译依赖 2025.1 与运行期 2026.07 **签名不同**（`SlimefunBlockData` vs `ASlimefunDataContainer`）⇒ **重载不是重写** | 真实现挂在 `ASlimefunDataContainer` 签名上（该重载**不能写 `@Override`**），再留一个转调 | `modules\10` §2；`modules\07` §8.1 |

### 5.2 GUI

| 现象 | 根因 | 解法 | 详情 |
|---|---|---|---|
| 空手点击占位玻璃板 / 按钮，图标进背包 | `MenuListener` 是 `setCancelled(!onClick(...))` ⇒ **`return true` = 放行** | `GuiLock` 默认锁死 + `markRealSlot` 显式开槽 + `autoGuard()` | `modules\08` §B.1 |
| "先 addItem 带保护、再 addMenuClickHandler 换业务" ⇒ 保护失效 | `ChestMenu#addItem` 内部就注册 handler，**按槽位唯一、后注册覆盖前注册** | 用 `GuiLock.button(slot, icon, cb)` 一次完成 | `modules\08` §B.2 |
| 拖拽能把物品"涂"进任何格子 | `InventoryDragEvent` 一次覆盖多槽，逐槽 handler 管不到 | 全局 `PortGuiListener#onDrag`；**不要覆写 `onDrag`**（Paper 1.20.4 的 `InventoryHolder` 只有 `getInventory()`） | `modules\08` §B.3 |
| 双击任意槽位，背包凭空多出占位玻璃板 | `DOUBLE_CLICK` 是**跨槽收集**，且 `ClickAction` **拿不到 `ClickType`** | `PortGuiListener#onClick` 取消 + `Notify.warn` 提示 | `modules\08` §B.4 |
| 从玩家背包 Shift 塞入，逐槽 handler 一次都没被调用 | `rawSlot >= size` 时槽位指向**玩家背包** | `PortGuiListener` 接管，机器实现 `GuiShiftGuard` 声明 `shiftInsertSlots()`；残量必须 `clone()` 再 `setAmount`（`new ItemStack(type,n)` 会丢粘液 id） | `modules\08` §B.5 |
| 上面那条"一直失效" | `PortGuiListener` 注册在 `LOW`，被 Slimefun `MenuListener`（`NORMAL`）重新 `setCancelled(false)` | **改成 `HIGH`** | `modules\08` §B.6 |
| 输出槽产物能被拿走 / 能往里塞东西刷物品 | 同"返回值语义反了" | `GuiLock.outputSlot(slot)`（空手放行 / 手上有物品点本槽取消 / 点玩家背包放行） | `modules\08` §B.7 |
| 注册了 `BlockUseHandler` 后自有 GUI 打不开 | `callItemHandler` 的返回值是"**有没有注册该 handler**"，不是业务结果 | **想开自有界面就不要注册 `BlockUseHandler`** | `modules\08` §B.10；`modules\04` §1.1 |
| 机器产物被每 tick 的进度条覆盖 | 进度条第 **22 号槽是本体硬编码** | 产物输出区避开 22 | `modules\04` §5 |
| `getSize()` 算槽位不可靠 | 未 `setSize` 时由"被碰过的格子数"反推 | 用自己的槽位常量，或 `getPresetSlots()`；`GuiLock` 内部 `Math.min(size, 54)` | `modules\04` §2.2；`modules\08` §B.8 |
| 点几下界面聊天栏被刷满 | 没有分档，17 处就地 `sendMessage` | 全局 `Notify` 四档；**点击的直接反馈用 `warn`**（`info` 默认静默） | `modules\08` §B.11 |

### 5.3 多方块与朝向

| 现象 | 根因 | 解法 | 详情 |
|---|---|---|---|
| 结构检测一直回退成"永远完整" | 层图写成**扁平列表**（YAML 序列表达不出层分隔） | 每层一个 `- -` 开头（`List<List<String>>`） | `modules\08` §D.1 |
| 启动就报"用了未登记的字符 '.'" | Bukkit 的 YAML 把 `.` 当路径分隔符 | 空气键写 `_`（读取时 `.` 会被统一改写为 `_`） | `modules\08` §D.2 |
| 结构绕纵轴转 90° 就永远激活不了，报错**完全不提"方向"** | 坐标换算原本只有平移、没有旋转（5×5×5 恰好对称才没暴露） | 四向逐个探测 + `computeSymmetric()` + 朝向落盘（`touhou:structure-dir`）；全部失败时报**最接近**的方向 | `modules\08` §D.4 |
| 用正中那一格当标记测朝向，四个方向结果一样 | `C` 是**旋转不动点** | 标记格要选不在旋转轴上的（如左上角） | `modules\08` §D.3 |
| `setblock … air` "拆掉"构件后结构照样报完整 | `setblock` 只改世界方块，不碰 Slimefun 方块数据 | 用 `/touhou remove`；**Slimefun 没有 `sf-remove` 子命令**（照抄会静默什么都不做） | `modules\08` §D.5；`modules\07` §10.1 |
| 爆炸 / 末影人 / 活塞破坏方块后不触发检测 | 它们**都不发** `BlockBreakEvent` | `StructureBuildListener` 监听 5 个事件、`MONITOR` + `ignoreCancelled` | `modules\08` §D.6 |
| "接口不能替代保护罩" | 判据用了具体 id 而不是标签 | `ItemTags` 挂 `touhou:reactor_shell`，层图写 `#touhou:reactor_shell` | `modules\08` §D.10 |
| 投影图标"悄悄变成另一个方块" | 取的是"标签成员里第一个"（登记顺序的副产品） | **必须 `ItemTags.setDefaultDisplay(...)`** | `modules\08` §D.10 |
| 物品描述写着 `#touhou:reactor_shell* ×36` | 标签登记位置放在 `AddSlimefunItems` 太晚 | 标签登记放在 `AddItems.setup()` **末尾** | `modules\08` §D.10 |
| 输入口与输出口不能同时出现（或同类能放多个） | `unique-part` 是**单个 id** | 改 `structure.unique-parts`（列表）= "每类各自最多一个" | `modules\08` §D.11 |
| 投影旧的一组 `Display` 永远飘在空中 | `render` 没先清旧组 | `render` 第二步就 `hide(loc)`；`clearAll()` 在 `onDisable`；`setPersistent(false)` | `modules\06` §7.2 |
| 结构"不支持投影 / 落点 0 格"，而结构检测一切正常 | 包装类**忘了转发** `cells()` 这个后加的 `default` 方法 | 给接口加 `default` 方法后，包装类逐项检查；`verifyDelegation()` 兜底 | `modules\06` §7.7 |

### 5.4 能源（自研 POWER）

| 现象 | 根因 | 解法 | 详情 |
|---|---|---|---|
| 同一张网，从不同起点查询得到不同节点数（35 / 28 / 1） | 建网只往"邻居半径内"走 + 缓存失效粒度会劈开合并网 | BFS 里加**反向跳接**（只在起点做一次）；`REVERSE_SCAN_RADIUS=8` | `modules\05` §2 |
| 跨区块的网被判成断的 | "未加载的方块"被提前拉黑 | 未加载 ⇒ **保持"未访问"，不下任何结论** | `modules\05` §2；`modules\08` §D.8 |
| 两个未连接的核心被判同网 | 核心自带 `range=7`（切比雪夫立方体） | 这是**预期行为**（要关就把 `range` 与 `items.yml` 里的值都改 0） | `modules\08` §G.2 |
| 以为"r=7 是沿直线 7 格" | **度量不同**：本模组切比雪夫 3374 格 vs 原生轴向十字 42 格（差约 80 倍） | 文档与 GUI 写清"立方体范围，含斜向与上下" | `modules\08` §G.1 |
| 改了 `jump-range`/`range` 没反应 | 它们是 `ItemSetting`（`Items.yml`），不是 `config.yml` | 同时改 `Items.yml` 或删键 | `modules\05` §2；`modules\08` §C.1 |
| 把跳接半径设成 >8 | `REVERSE_SCAN_RADIUS=8` 是硬常量、**无任何校验** | 别超过 8 | `modules\05` §2 |
| 发电机凭空多出电 / 电量消失 | `settle()` 的比例分配 + 发电机回填语义 | 发电机**只捐不取**；`extractPower` 明写"发电机缓冲里的电也能被抽走" | `modules\05` §1.1、§3 |
| 发电暂停阈值永远触发不了 | `mode-threshold >= energy-capacity` | 启动时告警并自动钳到上限 90% | `modules\08` §G.4 |
| 电量溢出 | 原生 `EnergyNetComponent` 是 int | 本项目 POWER 用 **long**，每次读写都 clamp | `modules\08` §G.3 |
| 机器不产电（4 张床算成 0 张） | "数据未加载就跳过"的保护 | **不要加这种保护** —— 会让机器静默失效 | `modules\05` §7 第 4 条 |

### 5.5 实体与弹幕

| 现象 | 根因 | 解法 | 详情 |
|---|---|---|---|
| 召唤的 mob"原地什么都没留下" | ★★★ **无头服上没有玩家在线时，任何 mob 都会在 1 tick 后被服务器清掉**（连原版苦力怕也一样） | 收尾锚定**召唤那一刻记下的 `Location`**，实体已不在世界就跳过 `remove()`、其余照做；报告里 `creeperRemoved` 与 `creeperAlreadyGone` **分开报** | `modules\08` §N.1、§N.6 |
| 误判"是 `setAI(false)` 让实体被清" | 症状出现在"我们改过的东西"附近 ≠ 那就是根因 | 做**对照组**（plain / named / full 三组同跑） | `modules\08` §N.2 |
| 延迟任务静默什么都不做 | `runTaskLater` 里的异常被调度器吞掉 | 调度器真正调用的那层包 `try/catch` 并 `Log.warn` | `modules\08` §N.4 |
| 在收尾里加 `Log.info` 看不到任何线索 | `logging.console-info` 默认 **false** | 诊断线索用 `Log.always(...)`；失败路径 `Log.warn` | `modules\08` §N.3 |
| 伤害静默退化 / 抛 `IllegalArgumentException: Direct entity must be set…` | `DamageSource` 设了 causing 就必须同时设 direct | 用真箭当 direct，没有就退化成发射者自己；整个 `damage()` 包 try/catch 并放行到原版路径 | `modules\08` §F.1 |
| 范围伤害"实际扣血 0 个" | 先结算范围伤害、后生成衍生弹幕 | 顺序：① 激光 → ② 喷泉 → ③ 追踪箭 → ④ 命中点范围伤害 | `modules\08` §F.2 |
| `StackOverflowError`（不是编译错误） | `int` 伤害没显式转 `double`，重载解析挑中了本方法自己 | `(double) configuredImpactDamage()` | `modules\08` §F.4 |
| 激光"打出去立刻消失"或"穿人而过" | 谓词返回值得写反了 | `true` = 继续、`false` = 停；**透明判据只跳过"空气"不够**（玻璃/水会挡光） | `modules\08` §F.5 |
| 追踪箭永远活着 / 被误杀 | 只限距离 or 只限时间；"区块没人看就杀箭" | `max-distance` 与 `max-seconds` **都要有**；不能按"区块有无观众"杀箭 | `modules\11` §4.4 |
| 落方块弹幕"跑着跑着变慢" | **关重力 ≠ 无阻力**（实测每 tick ×≈0.85） | 要么每 tick 硬写速度、要么按 `v(t)` 求值；**别假设"初速 = 全程速度"** | `docs\danmaku-plan.md` §3.4 |
| 以为落方块会互相挤压把形状挤散 | 实测否掉（`maxDrift=0`、`minGap=1.0`）；本版**没有** `setPushable` API | 同速 + 整数格偏移 + 无重力 ⇒ 不需要它 | `docs\danmaku-plan.md` §3.2 |

### 5.6 验证与日志

| 现象 | 根因 | 解法 | 详情 |
|---|---|---|---|
| 读日志中文全是乱码 | Paper 按**平台编码（GBK/936）**写盘 | `[System.IO.File]::ReadAllLines($p, [Text.Encoding]::GetEncoding(936))` | `modules\07` §5；`modules\08` §H.3 |
| `Select-String` 抓的"关键读数"看起来是空的 | **工具管道会吃掉 `§` 字符** | 关键读数改成纯 ASCII 输出（颜色写 `#RRGGBB`）；诊断输出先经 `Notify.plain(...)` | `modules\08` §H.4 |
| 脚本永远等不到服务端就绪 | `Process.BeginOutputReadLine()` 的 `DataReceived` 回调需要事件循环泵 | 改读 `logs\latest.log` | `modules\08` §H.5 |
| 服务端 `Can't keep up! … Running 1398874ms behind` | `Select-Object -First N` 提前关闭管道 | 先收进变量再筛选 | `modules\08` §H.6 |
| 后台任务"秒退 + 退出码 1" | `pwsh -Command` 里 **`$PSScriptRoot` 是空的**，相对路径指到盘根 | 命令行一律写绝对路径；**先读 stderr 第一行** | `modules\08` §H.8 |
| 含中文的 `.ps1` 报 `Unexpected token '}'`（指着没错的花括号） | Windows PowerShell 5.1 读**无 BOM** 的 UTF-8 会按 ANSI 解码。★ **编辑工具写回时会去掉 BOM** | 脚本必须 UTF-8 **带 BOM**；改完补回并做语法检查 | `modules\07` §9.3；`modules\08` §H.2 |
| PowerShell 改源文件后中文全成乱码（**毁过一个 1400 行文件**） | `Get-Content -Raw` + `Set-Content` 按 ANSI/GBK 解码 UTF-8 | **不要用 PowerShell 改项目源文件**；用编辑工具或显式 UTF-8 `WriteAllText` | `modules\08` §H.1 |
| 源码改了、行为不变 | 增量编译留下"混合态" class | `build.ps1` 已内建"先删 `out\` 与 `build\`"；排查时手动重来一遍 | `modules\08` §H.11 |
| `Cannot attach type annotations … ComponentDecoder not found` | `_lib` 的 adventure 是 4.14、服务端是 4.16；**报错里一个字都没提版本** | 服务端同名 artifact 放进 `_lib_extra`，`build.ps1` 按同名覆盖 | `modules\07` §4；`modules\08` §H.12 |
| `javap` 输出"有内容"但完全误导 | 没保持包目录结构 ⇒ 读到的是**上一个临时目录的残留**；且没查 `$LASTEXITCODE` | 建好 `tmp\com\example\touhou\…` 再 `javap`，并检查退出码 | `modules\08` §H.9 |
| 诊断报 0，据此判定"功能没生效" | **工具自己会骗人**（`countMachineRecipesFor` 扫 `getDisplayRecipes()` 对魔法工作台恒为空；`IndexOf` 子串歧义；`$Matches` 被覆盖；`javap` 残留） | **工具报 0 时先怀疑工具**；布局类改成静态方法 + 纯 Java 直接调产品代码 | `modules\08` §H.14、§J.2 |
| 健康检查漏掉了整个新物品却仍报 OK | 检查只覆盖代码里**手写的字段清单** | 按**注册表**（`Slimefun.getRegistry()` / `items.yml`）核 | `modules\08` §H.15 |
| 无头验证"通过"了但是假阳性 | 诊断命令**直接调**产品方法，绕过了真实调用链（如 `EnergyNet` 的门槛） | 能走真实入口就走（`/touhou place`、`/touhou harvest test`）；不能就**写明"这一步绕过了 X"** | `modules\07` §11.1 |
| "数量对不上"（4 条记录只看得到 2 行） | `xyzText()` 不带世界名 + grep 过滤条件把另外两行滤掉 | 诊断输出**必须带世界名**；**先看全量再过滤** | `modules\08` §J.1 |
| 验收脚本 grep 出假阳性 | 诊断**成功文案**里写了失败判据的字面串（`illegal stack size`、`[MISS]`） | 诊断输出里不要出现失败判据的字面串；判据收紧成"行首为 `[MISS]`" | `modules\08` §O.1 |
| 无头测试里 `Notify` 无人可发 | `/touhou edit` 是控制台发的，没有玩家 | 只能靠控制台 `[MBREACTOR]` 行间接验证分档（IMPORTANT 有、NORMAL 没有） | `modules\07` §10.4；`modules\08` §J.5 |

### 5.7 文档与文案

| 现象 | 根因 | 解法 | 详情 |
|---|---|---|---|
| 粘液书配方页上玩家看到 `（2026-09-25 用户给定）` | `SaizenbakoRecipe#note` **是玩家可见文案**（`describe()` / `getDisplayRecipes()` 都渲染它），而"note"这个名字听起来像开发者备注 | 删掉 `.note(...)` 调用；改动来历写进**代码注释**；javadoc 上写醒目警告 | `modules\08` §S.1（硬规则 13） |
| 文档里的"物品总数 / 无配方数"静默漂移，且**从文档自身读不出它错在哪** | 汇总量是**派生量**却写成字面量，没有任何校验 | 别信文档里的数字，用运行期读数重算；非要手写就写"**算式**"（`20（= 36 − 16）`） | `modules\08` §P.1 |
| 中文组名在 GBK 日志里变乱码（看着像写错了） | 名字里有非 GBK 字符（日文 `の`），是**显示层**问题 | 诊断行额外打一份 **ASCII 码点**（`U+XXXX`），任何编码下都能逐字核对 | `modules\08` §M.2 |
| 想在无头里核对"组的显示名" ⇒ 编译失败 | `ItemGroup#getItem()/getDisplayName()` **都要 `Player`** | 建组时自己登记一份显示名（`AddGroups.rememberName` / `nameOf`） | `modules\08` §M.1 |
| 改组显示名时顺手把 `NamespacedKey` / 字段名一起改了 | key 是研究解锁/引用/诊断的锚点 | **只把显示名抽成常量**，key 与字段名原样不动 | `modules\08` §M.3 |
| 新加的 1 级组在指南主菜单**顶层单独占一格** | 用了 `TouhouNestedGroup`（继承 `FlexItemGroup`，`isVisible` 恒真） | 叶子组一律 `SubItemGroup`；容器组才用 `TouhouNestedGroup` 且 `showInMainMenu=false` | `modules\08` §M.4 |
| 对声明为 `SubItemGroup` 的字段写 `instanceof FlexItemGroup` ⇒ **编译不过** | 两个类互不相干，静态类型不可能成立时 Java 直接判错 | 按字段的**声明类型**决定，别加无意义的 `instanceof` | `modules\08` §P.2 |
| Java 字符串里的中文引号导致编译不过 | 把全角引号写成了 ASCII 双引号，字符串提前结束 | 中文术语里的引号用**全角「」/『』** | `modules\08` §L.4 |
| 配方里写 `Material.WHITE_WOOL`，"布"怎么都匹配不上 | 游戏里叫「布」的是粘液的 `CLOTH`（材质 `PAPER`），与白色羊毛是**不同身份** | 配方写 `SlimefunItems.CLOTH`；**需求给的中文名/材质名都要拿运行期读数核一遍** | `modules\08` §R.1 |
| 自研机制产出物用了本体 `ANCIENT_ALTAR` ⇒ 玩家能在古代祭坛里合成它 | 本体 `RecipeType` = "配方落在哪台**本体机器**上" | 自研机制一律用**门面类型**（`registerConsumer == null` + `machine` 指向本插件物品） | `modules\08` §R.2 |
| 自研机制的配方"没法无头验证" | 命令把"需要坐标"的入口与"纯查表"的读数绑在一起 | 把纯逻辑抽成 `selfTest()`，用**机器用的同一个 `match()` 入口**，再用不需要坐标的 `/touhou altar [list\|test]` 暴露 | `modules\08` §R.3 |
| 行尾 `§r§7` 的断言恒为 false | Paper 把 lore 存成 Adventure Component，**行尾孤立的 `§r§7` 是空操作、会被规范化掉** | 别用 `endsWith("§r§7")` 当判据；看"真实尾巴"（`tail=\u00a77`）与 `grayStrike=true` | `modules\08` §Q.2 |
| 逐字符颜色清单里 `&7&m` 消失、`distinct=1` 假读数 | `guideLine` 走 `Notify.plain` = `stripColor(...)`，把颜色记号**连同内容删掉** | 要断言的读数放进**纯 ASCII 汇总行**（不经 `plain`） | `modules\08` §Q.3 |
| 子类够不到本体的 5 参 `recipeOutput` 构造器 | **构造器不参与继承的重载解析** | 链上每层补一条 5 参构造器，4 参转发到 5 参传 `null` | `modules\08` §Q.1 |

### 5.8 已废弃方案（**别再用**，细节见对应模块）

| 曾经的方案 | 为什么不能再用 | 详情 |
|---|---|---|
| 用 `setblock … air` 拆机器做无头测试 | 只改世界方块、不清 Slimefun 数据 ⇒ 结构照样报完整 | `modules\08` §D.5 |
| 用 `ping -n N` 赌服务端启动时间 | 冷启动时长会波动 ⇒ 命令全部没执行 | `modules\07` §9.5 |
| 用 `Process.BeginOutputReadLine()` 抓服务端 stdout | 纯 PowerShell 里等不到 `DataReceived` | `modules\08` §H.5 |
| 用 `BlockUseHandler` 做"右键映射核心 GUI" | 注册了就恒为 true ⇒ **自有界面再也打不开**（后来直接删掉该功能） | `modules\08` §B.10 |
| 用 `Set<Player>` + 异步清理当冷却表 | 并发不安全且持有强 `Player` 引用 | `modules\11` §4.1 |
| 用 `ItemSetting` 的 Java 默认值期望"改了生效" | 旧值持久化在 `Items.yml` 且优先 | `modules\08` §C.1 |
| 用"扫 5×5×5"找核心 / 每 tick 轮询结构 | 空闲 ≈9 次/秒；已改事件驱动 + uid 落盘 | `modules\08` §D.7 |
| 用 `mb\engine` 的 schema 引擎做当前结构 | **项目内零调用者**；日常改结构只碰 `core\` 那套层图引擎 | `modules\06` 开头 |
| 用多方块建造器搭"以方块状态为配置"的机器 | `setType(...)` 不触发 `BlockPlaceEvent` ⇒ 状态缺失、静默失效（本项目当前只有投影、无建造器） | `modules\08` §D.9 |
| `docs\saizenbako-layers.yml` 当赛钱箱层图的权威副本 | 该文件**不是 UTF-8，中文注释在磁盘上已乱码**；权威副本是 `AddonConfig.saizenLayers` | `modules\06` §2.5 |

---

## 6. 常用工具与脚本

### 6.1 构建 / 部署 / 验证

| 路径 | 一句话说明 |
|---|---|
| `_fromscratch-touhou\build.ps1` | **唯一构建入口**：`javac --release 21` 直编 → 手工替换 `${project.version}` → 打包 → jar 自检（7 个关键点别破坏，见 `modules\07` §2） |
| `D:\DS_work\slimefun\_tools\headless_run.ps1` | ★★ **首选无头 harness**（工作区根，**不在项目里**）：轮转日志 → 起服 → 轮询 `Done (` → 逐条发命令 → `stop`；只回打印 ASCII 判据行。参数 `-Jar` / `-WorkDir` / `-CommandsFile`（必填）/ `-TimeoutSec` / `-IntervalMs` |
| `_fromscratch-touhou\tools\autotest.ps1` | 项目早期的无人值守联机实测脚本：起服 → `forceload` → `/touhou place` **真搭 5×5×5 反应堆** → 两个接口各取代一格保护罩 → 跑全部验证 → `stop`（含 `edit`/`structure`/`scan` 的完整姿势） |
| `_fromscratch-touhou\tools\layoutcheck.ps1` | 只跑一条命令（`touhou reactor <xyz> io layout`）的 IO 接口布局自检；从日志里抽 `合计=`/`无重叠`/`OK 54` 等行 |
| `_fromscratch-touhou\tools\layoutcheck2.ps1` | 同上，**无管道版本**（避免 `Select-Object` 提前关闭管道把服务端挂住） |
| `_fromscratch-touhou\tools\probe-groups.ps1` | 起服跑 `/touhou groups`，核对**真实注册的物品组内容** |
| `_fromscratch-touhou\tools\probe-ids.ps1` | 对**新旧 id** 分别 `/sf give`，看哪个能取到（查 id 是否还存在；纯查询，用完自动关服） |
| `_fromscratch-touhou\_verify_src\` | **纯 Java 离线验证器**（不用起服务端）：`LayoutVerify`（期望矩阵逐格比对）/ `Grid` / `RotateVerify`（旋转数学）/ `DirectionDetectVerify`（对称性与四向偏移）/ `CheckAcquisition` + `CheckAcquisitionFacade`（获取方式门面）。编译运行方式见 `modules\07` §7.3，**必须 `-Dstdout.encoding=UTF-8`** |
| `_fromscratch-touhou\_verify_out\` | 上者的 class 输出目录（不是源码） |
| `_tools\_cmp1\` / `_cmp2\CustomRecipeType.java` | 工作区根的反编译对照素材（`modules\12` 用过） |
| `_tools\sf-dec\` / `_tools\wiki*\` / `_tools\tpl*\` | 本体反编译资源 / 官方开发者文档 / 附属模板（**通用参考**，与 Touhou 无关） |
| 工作区反编译活字典 | `_java_slimefun\`（本体 API 权威）> `_java\`（LogiTech）> `LogiTech-Build 3 (git 51ee3a5)_smali\`；见 `modules\10` §0 |

### 6.2 `/touhou` 命令族（按用途分类，权限 `touhou.command`，默认 op）

**诊断 / 读数（只读，随便跑）**

| 命令 | 用途 |
|---|---|
| `/touhou item all` / `/touhou item <id>` | ★ 计数的唯一复核口径；逐件读"模板 / 名称与描述逐字符颜色 / 三条产出路径 / 9 格逐格" |
| `/touhou acquisition all` / `<id>` / `rule` | ★ 获取方式总表核查（无 `[MISS]` 即全覆盖） |
| `/touhou groups` | 物品组层级 + 菜单内容 + **主菜单预览** + ASCII 码点形式的组名 |
| `/touhou names [sf\|ours]` | ★ 材料官方译名的**唯一权威读数**（`ItemMeta#getDisplayName()`） |
| `/touhou layout` / `gui [<xyz>]` | 反应堆 GUI 布局自检 / **GUI 锁槽自检**（`N` 必须等于设计的真实槽数） |
| `/touhou power [rebuild]` / `<x> <y> <z>` | POWER 网络视角诊断（节点数 / 逐类计数 / 现算 `measure()`） |
| `/touhou dreamcatcher <x> <y> <z>` | 幻梦捕捉器状态（床数 / 缓冲 / 产速） |
| `/touhou structure <x> <y> <z> [alldirs]` | 现场结构检测（`alldirs` 四向强制探测） |
| `/touhou reactor <x> <y> <z> info\|scan\|gate\|inv\|raw\|tags\|particles` | 反应堆状态 / **检测与扫描计数（判退化）** / 运行门 + 7 格电网 / 背包 dump / 标签表 / 粒子开关 |
| `/touhou reactor <x> <y> <z> io [layout\|guard\|count\|scans]` | 物流接口布局 / 锁槽 / 标记物计数 / **找核心扫描速率** |
| `/touhou saizen <x> <y> <z> [check\|slots\|posts\|recipe\|info\|guard]` | 赛钱箱结构 / 布局 / 6 根木桩绑定 / 配方表 |
| `/touhou proj [list\|count\|info\|mapping\|clean]` | 投影组数 / 实体数 / 落点与对称性 / **图标解析链** / 清孤儿 |
| `/touhou messages` / `msg` | 只读查看消息栏档位策略 |
| `/touhou seal <玩家名>` / `seal probe <xyz>` / `seal selfcheck` | 梦想封印 集读数 / 真实取电链路 |
| `/touhou lily …`（`selfcheck`/`dir`/`beam`/`fire`/`laser`/`impact`/`tracers`/`cleanup`） | 杀意的百合：方向裁决 / 几何探测 / 完整发射仿真 / 实弹 / 爆发 / 衍生箭 |
| `/touhou echo …`（`selfcheck`/`rule`/`container`/`probe`/`convert`/`cooldown`） | 回响转化：判定矩阵 / 临时箱子实跑内核 / 冷却表 |
| `/touhou harvest …`（`selfcheck`/`cell`/`rng`/`test`/`coolown`/`clear`） | 丰收之时：单格骨粉 / 骨粉行为实验台 / **标准 9×9 测试田全量验证** |
| `/touhou guide [reactor\|saizen\|echo\|all]` | 粘液书自定义配方页 + **可合成性核查**（都应该是 0） |
| `/touhou clickinfo <x> <y> <z>` | 模拟点击核心 GUI 信息格（**与真人点击同一条路径**） |

**核对 / 自检（会改动本机运行时状态或世界）**

| 命令 | 用途 |
|---|---|
| `/touhou altar [list\|test]` | ★ 祭坛 3 条祈愿配方的**纯逻辑自测**（不需要坐标、不需要摆机器） |
| `/touhou reactor <xyz> test [n]` | 注入 1 个原油桶、开进程、跑 n 次发电 tick 并统计 |
| `/touhou reactor <xyz> io seed\|fill\|reset` | 往接口塞标记物 / 原油桶 / 清搬运计数 |
| `/touhou saizen <xyz> activate\|deactivate\|seed\|tick\|charge` | 激活（**与点 GUI 同一条路径**）/ 停机 / 按配方投料 / 手动推演 / 置 POWER |
| `/touhou autobuild <x> <y> <z> [manual\|auto] [n]` | 构建模式演变验证（断言用） |
| `/touhou edit` \| `clickpart` `<x> <y> <z> [placed\|broken]` | ★ 触发"世界变动 → 结构检测"的唯一可靠方式（`/touhou place` **不触发** `BlockPlaceEvent`） |
| `/touhou place <x> <y> <z> <sfId>` | 走 `createBlock` **真放**一个粘液方块（无头搭结构用） |
| `/touhou remove <x> <y> <z>` | 清方块数据 + 置空气（**拆机器的正确姿势**） |
| `/touhou reload` | 重载配置（同时清 GUI 登记表与结构内存表） |
| `/touhou reactor <xyz> mode\|buildmode\|activate\|abort\|charge\|start` | 模式 / 构建模式 / 激活 / 中止 / 置储电 / 试开进程 |

**实验台（非产品代码，验证构想用）**

| 命令 | 用途 |
|---|---|
| `/touhou danmaku probe\|fall\|clean\|rule <x> <y> <z> [n]` | 实体弹幕实测台（落方块 / 末影水晶），见 `docs\danmaku-plan.md` |
| `/touhou lilywhite [all\|check\|selfcheck\|name\|gradient\|recipe\|craft]` | 三条可独立证明的论断：头颅 Value 一致 / 逐字符渐变 / **产出 2 且模板未被污染** |
| `/touhou lily · harvest · echo · saizen` 的 `rng`/`cell`/`container`/`rule` | 上述各族的"造场景 + 跑内核 + 回读 + 清场"实验子命令 |

> ★ **`/touhou supply` 与 `/touhou tags` 不存在**：`PowerSupplyUnit` 的 javadoc 与物品 lore 宣传过
> `supply`，但 dispatch switch 里没有这个 case ⇒ 那两个诊断方法是**死代码**（替代：`/touhou power <xyz>`）；
> `tags` 只加了 tab-complete 与 `commands()`，**能用的形式是 `/touhou reactor <xyz> tags`**。
> 新增子命令必须**三处一起加**（`onCommand` 的 case / `onTabComplete` / `commands()`），见 `modules\09` §1、§7。

---

## 7. 未完成 / 待用户拍板

> 出处：`docs\todo-items.md`（§A/§D/§E）+ `docs\danmaku-plan.md` + `modules\12` §8。
> **我不擅自定档位 / 不擅自选方案。**

### 7.1 ★ 唯一的大缺口：反应堆 **6 件结构件无配方**

| 物品 | id | 现状 |
|---|---|---|
| 反应堆框架 | `TOUHOU_COMPLEX_MACHINE_REACTOR_FRAME` | `RecipeType.NULL` + `noRecipe()`；槽 10 显示"暂未开放 —— 目前只能由管理员发放（配方待补）" |
| 反应堆保护罩 | `TOUHOU_COMPLEX_MACHINE_REACTOR_SHIELD` | 同上 |
| 反应堆稳定器 | `TOUHOU_COMPLEX_MACHINE_REACTOR_STABILIZER` | 同上 |
| 反应堆基座 | `TOUHOU_COMPLEX_MACHINE_REACTOR_BASE` | 同上 |
| 反应堆输入接口 | `TOUHOU_COMPLEX_MACHINE_REACTOR_INPUT_PORT` | 同上 |
| 反应堆输出接口 | `TOUHOU_COMPLEX_MACHINE_REACTOR_OUTPUT_PORT` | 同上 |

- **缺什么**：6 件各一条 3×3 配方（哪台机器、哪 9 格、单次产出几个）。
- **影响**：核心 `UTSUHO_REACTOR_CORE` **已经有**增强型工作台配方（9 格全满），但这 6 件一件都做不出来
  ⇒ **玩家造不出结构** ⇒ 这台多方块机器目前只能靠管理员发构件（投影 / 结构检测在真实玩家手里也跑不通）。
- **需要用户给什么**：每件的**材料档位**（"用什么材料、放在哪台机器"这种粒度），例如
  「反应堆框架 = 增强型工作台：钢筋板×8 + 铁块×1」。★ 材料名请用客户端官方译名（`docs\material-names.md`）。
- **读数复核**：`/touhou item <上述 id>` ⇒ `declaredOutput=1 machineTableOutput=-1 gridFilled=0 machineRecipes=0`。

### 7.2 依赖 7.1 的机制：反应堆"烧原油 → 发电 + 炙热的灰烬"

- **缺什么**：`TOUHOU_MATERIAL_LOGIC_SINGULARITY`（炙热的灰烬）的产出机制。设计上它是**反应堆的产物**，
  而"烧原油 → 发电 + 产灰"这条链路**未做**。
- **影响**：炙热的灰烬现在**既拿不到、也没人消耗它**（它原先唯一的消耗方是「梦想封印 集」的旧增强型工作台配方，
  那条已按用户要求删除 ⇒ 它回到"拿不到、也没人要"的状态）。
- **需要用户给什么**：机制口径（每桶原油产几个灰 / 是否与产物模式绑定 / 是否需要新 GUI 元素）。
- **附带**：反应堆的**输出能力本身也未验证**（搭不起来就验不了）。

### 7.3 暂缓：实体弹幕（落方块 / 末影水晶 / 形状弹幕）

- **状态**：`docs\danmaku-plan.md` —— **2026-09-24 用户决定"先完善现有物品体系"，暂缓**。
- **已有资产（别重做）**：实测台 `/touhou danmaku probe|fall|clean|rule`；`FallingBlock` / `EnderCrystal`
  在本版的**全部可用开关已 `javap` 核实**；结论摘要见 `modules\11` §10。
- **还欠的三条实测**（重启方向时的第一步）：① 落地接触那一帧没抓到（只有"消失后扫描 0 方块"的间接证据）；
  ② **客户端观感**（插值抖动、方块间视觉滑动）**必须真人看**；③ 阻力因子是否为常数（只在一个速度点测过）。
- **需要用户给什么**：重启这个方向的**优先级**，以及"外观可接受度"（决定走 `Display` 还是落方块 + 每 tick 硬驱动）。

### 7.4 需要用户拍板的其它项

| 项 | 缺什么 / 影响 | 需要用户给什么 |
|---|---|---|
| **材料档位平衡的新配方** | 只有当"新物品需要被谁消耗"时才需要做；现有 13 件材料出口已够用 | 是否要做、做哪几件 |
| **反应堆 6 件构件的显示名** | 层图/命令/文档用简称「反应堆框架」，而客户端显示名带「旧地狱-」前缀（`docs\material-names.md` 第二节已如实标出） | 改不改（改了要连层图与命令一起统一） |
| **6×6 大型工作台是否落地** | 骨架 `docs\large-workbench-template.java` 已存档，**未接入项目**（本轮只加模板、未动产品代码） | 是否要做、接哪个 `RecipeType`、36 格配方从哪来 |
| **`touhou.debugReactor` / `touhou.debugStructure`** | 是 JVM 系统属性，不是配置项 —— 正式包默认零噪音 | 需要时在启动参数里加即可，无需拍板 |
| **`PowerIntegratedCore.range = 7` 导致的"多核心并网"** | 会弹「检测到存在多核心，位于（…）」提示 —— **那不是误报**（并网是预期行为） | 是否要关（改成 0，需同时改 `Items.yml`） |

---

## 8. 工程事实速查（防重犯）

| 事实 | 具体口径 |
|---|---|
| **日志编码** | Paper 日志按**平台编码 GBK(936)** 写盘 ⇒ 读它必须 `[Text.Encoding]::GetEncoding(936)`；用 `-Encoding UTF8` 读中文全是乱码 |
| **编排脚本编码** | `headless_run.ps1` / `.bat` 这类编排脚本**必须纯 ASCII**（cmd 用 GBK 解析 bat 里的 UTF-8 中文会把括号/引号当命令） |
| **含中文的 `.ps1`** | 必须 **UTF-8 带 BOM**；★ **编辑工具写回时会去掉 BOM** —— 本轮刚踩过：编辑后 BOM 丢失 ⇒ `Unexpected token '}'`（指向一个根本没错的花括号）。改完必须补 BOM 并做语法检查（`modules\07` §9.3） |
| **不要用 PowerShell 改项目源文件** | `Get-Content -Raw` + `Set-Content` 在 5.1 下按 ANSI/GBK 解码 UTF-8（**毁过一个 1400 行文件**）。用编辑工具，或显式 `WriteAllText(..., UTF8Encoding($true))` |
| **无头服没有真玩家** | ⇒ 测不了的一律**"跳过并登记给用户亲测"**（硬规则 11）：不许绕路、不许造假证据。禁止的绕路名单见 `modules\07` §9.6；报告模板在那一节，照抄 |
| **无头服上实体不可依赖** | 没有玩家在线时**任何 mob 都会在 1 tick 后被服务器清掉**（连原版苦力怕也一样）⇒ 延迟任务的收尾**锚定坐标**，不锚定实体 |
| **玩家可见处禁止写日期 / 改动来历** | 最多写注释。判据："玩家读了会知道这是某个日期改的 / 这是哪次需求加的"吗？会 ⇒ 违规。范围含**命令回显**、**物品 lore**、**配方页**（`note` 是玩家可见文案！） |
| **文档计数必须用运行期读数复核** | 手写计数已错过多次（实测同一轮漂 4 处）。三条互不依赖的读数互印：`/touhou item all` 的汇总、`/touhou acquisition all` 的 `[OK]` 行数与 `table=`、`/touhou groups` 的逐组物品数。写成"可自行求和核对"的形式（如 `20（= 36 − 16）`） |
| **只 add 自己的文件** | 多个执行者并发写同一个 git 仓库会互相覆盖 ⇒ 提交前 `git status`，只 `git add` 自己的文件（`modules\07` §13） |
| **`build.ps1` 的 7 个关键点** | `--release 21`（本机 JDK 25，不加会产出 major=69 的类）· **不加 `-sourcepath`** · 每次先删 `out\` 与 `build\`** · `_lib_extra` 同名覆盖 · 手工替换 `${project.version}` · 打包自检（不得含 `io/github/thebusybiscuit*` 与 `org/bukkit*`）· javac/jar 前后临时放宽 `$ErrorActionPreference` |
| **依赖 jar** | `_lib` 里实际是 **19 个 jar**（**不是** 18）；`_lib_extra` 里 2 个（adventure-api/key **4.16.0**）。只加 paper-api + Slimefun4 会因缺 `NotNull` / adventure 而 `CompletionFailure` |
| **编译依赖 vs 运行期** | 编译用 Slimefun4-**2025.1**、运行是 **2026.07**；已核实**只有一处**签名差异（`AGenerator#getGeneratedOutput`）。**用新 API 一律先 `javap` 运行期 jar** |
| **当前 git 状态** | `main` **与 `origin/main` 已同步**（`ahead=0`）。HEAD = `ec1e97a`（`plugin.yml` 作者拼写 `Haykegon` → `Hayekgon`）← `3c113b0`（docs 计数统一为运行期口径 + 修掉 docs 之间的自相矛盾）← `d758920`（作者由占位符 `YourName` 改为 `Ning_Meng__(Hayekgon)`）← `d8199e1`（本文件建立）← `bb9f7e8`（版本号 1.0.0 → 1.0.1 + 补回 `build.ps1` 丢失的 UTF-8 BOM）。★ 早期提到的 `08eadfe` 已被 amend 成 `3c113b0`，**该 hash 已不存在**。 |
| **已发布的 Release / tag** | tag `v1.0.0`（指向 `daea28f`）、`v1.0.1`（指向 `ec1e97a`）**均已推送**；两个 GitHub Release **已发布**并各挂一个 jar 附件：`releases/tag/v1.0.0`（首个正式发布）、`releases/tag/v1.0.1`（**Latest**）。源码文件由 GitHub 自动附带的 `Source code (zip/tar.gz)` 提供。本地留档在 `D:\DS_work\slimefun\_release\`（两个 jar + 两份发布说明）。 |
| **★ 多人/多会话并发写同一个仓库** | 本项目真实发生过：本会话 `git commit --amend` 时报 `index.lock File exists` —— 另一个执行者**同时**提交了 `plugin.yml`。⇒ 提交前后都要 `git status` 复核、**只 `add` 自己的文件**、别用 `--amend`（会改写别人的提交） |

---

## 9. 维护约定（改这份文件时）

1. **§1 的计数只能来自运行期读数**，并在改动处标明"哪一轮、从哪条命令读到"。
2. **§3 新增一行**：只要新加了 `modules\` 模块或 `docs\` 文件，就在这里登记"什么时候该读它"。
3. **§4 的行数必须与 `SKILL.md` 当前条数一致** —— 改硬规则时回来改这张表。
4. **§5 只加仍然有效的坑**；废弃方案只留一句"别再用它"，正文仍在 `modules\08`。
5. **§7 的每一项都必须写"缺什么 / 影响 / 需要用户给什么"三要素**，不许只写"待办"。
6. 本文与 `SKILL.md` / `modules\*` / 源码冲突时，**以那三者为准**，并回来修本文。
7. ★ **`docs\` 之间出现计数分歧时，一律以运行期读数为准**（`/touhou item all` +
   `/touhou acquisition all` + `/touhou altar list`），**不许拿另一份文档的数字当依据**；
   就地改错的那一份，并把改动记进 §1.2 的"处置"列（含"判不准而保留"的清单）。
   —— 本轮据此修掉 6 处（4 处 `touhou-tree.md` 计数 + 1 处 `todo-items.md` 沿革 + 1 处 `touhou-tree.md` "现为 0"）。

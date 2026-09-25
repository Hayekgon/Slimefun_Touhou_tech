# Touhou (TH Tech) 全景树状图 —— 物品组 / 物品 / 配方

> **数据来源**：直接从代码的**唯一出处**读出（`AddGroups` 建组与显隐、`AddSlimefunItems.setup()` 的注册调用、
> `AddItems.setup()` 的模板、`Acquisition.SOURCE_BY_ID` 的获取方式、各 `xxxRecipe()` 方法的 9 格数组）。
> 生成时 HEAD = **`b5a3a0d`**（本地领先 origin 19 个提交）。
>
> ★ **2026-09-25 复核**：本文件的**全部计数与逐件配方**已按运行期读数逐项复核过一遍
> （`/touhou item all` + `/touhou item <id>` + `/touhou acquisition all` + `/touhou guide`），
> 结论见文末「三」的复核注记 —— **本文件与当前代码一致**。
>
> ★ **材料名一律用客户端官方译名**（钢筋板 / 起泡锭 / 黑金刚石 / 奇怪的下界粘液 / 魔法结晶 - III…）：
> 权威对照表见 **`docs\material-names.md`**（读数是 `/touhou names` 打出来的
> `ItemMeta#getDisplayName()`）。旧叫法与官方名的对应关系在那里一次性列出。
>
> ★ 配方一律写成 **3×3 阅读顺序**（上→下、左→右），与原版合成格布局一致。
> 物品后面括号里是**粘液 id**（发布后慎改）；`•` 缩进表示层级。
> 「获取方式」= 指南页槽 10 显示的那句话（`Acquisition` 总表）。

---

## 一、物品组层级

```
TH Tech  (0 级容器 TOUHOU_TH_TECH, key touhou:touhou_th_tech, 指南主菜单唯一入口)
│
├── 幻想之物        (1 级 TOUHOU_MATERIAL,         key touhou:touhou_material)      ← 原名「材料」
│     ├── 落叶                  TOUHOU_MATERIAL_FALLEN_LEAVES
│     ├── 另一个世界的回响        TOUHOU_MATERIAL_ECHO_OF_ANOTHER_WORLD
│     ├── 炙热的灰烬             TOUHOU_MATERIAL_LOGIC_SINGULARITY
│     ├── 春泥                  TOUHOU_MATERIAL_SPRING_MUD
│     ├── POINT                 TOUHOU_MATERIAL_POINT
│     ├── P引擎                 TOUHOU_MATERIAL_P_ENGINE
│     ├── 空白符卡               TOUHOU_MATERIAL_BLANK_SPELLCARD      （祭坛祈愿）
│     └── 灵梦的大蝴蝶结          TOUHOU_MATERIAL_REIMU_RIBBON         （祭坛祈愿）
│
├── 幻想之缘起      (1 级 TOUHOU_CHARACTER,        key touhou:touhou_character)
│     ├── 报春の妖精             TOUHOU_CHARACTER_SPRING_HERALD
│     ├── 红叶飞散の天狗          TOUHOU_CHARACTER_MOMIJI_TENGU
│     ├── 冰の妖精               TOUHOU_CHARACTER_CIRNO
│     └── 雾中の妖精             TOUHOU_CHARACTER_FAIRY_IN_MIST
│
├── 科学世纪        (1 级容器 TOUHOU_MACHINE,       key touhou:touhou_machine)      ← 原名「机器」
│   │                 ★ 容器组：只装子组、不装物品；指南主菜单里隐藏
│   ├── 多方块大型机器 (2 级 TOUHOU_COMPLEX_MACHINE, key touhou:touhou_complex_machine)
│   │     ├── 旧地狱-灵乌路空反应堆  TOUHOU_COMPLEX_MACHINE_UTSUHO_REACTOR_CORE   （核心 / 发电机）
│   │     ├── 反应堆框架            TOUHOU_COMPLEX_MACHINE_REACTOR_FRAME          〔配方待补〕
│   │     ├── 反应堆保护罩          TOUHOU_COMPLEX_MACHINE_REACTOR_SHIELD         〔配方待补〕
│   │     ├── 反应堆稳定器          TOUHOU_COMPLEX_MACHINE_REACTOR_STABILIZER    〔配方待补〕
│   │     ├── 反应堆基座            TOUHOU_COMPLEX_MACHINE_REACTOR_BASE          〔配方待补〕
│   │     ├── 反应堆输入接口        TOUHOU_COMPLEX_MACHINE_REACTOR_INPUT_PORT    〔配方待补〕
│   │     ├── 反应堆输出接口        TOUHOU_COMPLEX_MACHINE_REACTOR_OUTPUT_PORT   〔配方待补〕
│   │     ├── 神社的木桩            TOUHOU_COMPLEX_MACHINE_SHRINE_POST           （魔法工作台合成）
│   │     └── 赛钱箱               TOUHOU_COMPLEX_MACHINE_SAIZENBAKO            （核心 / POWER 存储）
│   └── 单方块机器   (2 级 TOUHOU_SIMPLE_MACHINE,    key touhou:touhou_simple_machine)
│         └── 丰收之时              TOUHOU_SIMPLE_MACHINE_HARVEST_TIME
│
├── Flee into Gensokyo (1 级 TOUHOU_PARTY_ITEM,    key touhou:touhou_party_item)
│     ├── 梦想封印 集              TOUHOU_PARTY_ITEM_FANTASY_SEAL_CONVERGE
│     └── 杀意的百合               TOUHOU_PARTY_ITEM_MURDEROUS_LILY
│
├── INFO            (1 级 TOUHOU_INFO,             key touhou:touhou_info)
│     ├── 模式切换玻璃板            TOUHOU_INFO_MODESHIFT          （GUI 功能件）
│     ├── 插件消息                TOUHOU_INFO_PLUGIN_MESSAGE
│     ├── 声明 1/3                TOUHOU_INFO_DECLARATION_1
│     ├── 声明 2/3                TOUHOU_INFO_DECLARATION_2
│     ├── 声明 3/3                TOUHOU_INFO_DECLARATION_3
│     ├── 酒石酸菌                TOUHOU_INFO_TARTARIC_ACID
│     ├── 上海爱丽丝幻乐团          TOUHOU_INFO_TEAM_SHANGHAI_ALICE
│     ├── Ning_Meng__            TOUHOU_INFO_NING_MENG
│     └── matl114                TOUHOU_INFO_MATL114
│
└── Power           (1 级 TOUHOU_POWER,            key touhou:touhou_power)
      ├── POWER集成核心            TOUHOU_POWER_POWER_INTEGRATED_CORE   （魔法工作台 · 产出 2）
      ├── POWER中继器              TOUHOU_POWER_POWER_REPEATER          （魔法工作台 · 产出 8）
      ├── POWER存储单元            TOUHOU_POWER_POWER_STORAGE_UNIT      （魔法工作台）
      ├── 幻梦捕捉器               TOUHOU_POWER_DREAMCATCHER            （魔法工作台）
      └── POWER供给单元            TOUHOU_POWER_POWER_SUPPLY_UNIT       （魔法工作台）
```

**小计**：1 个 0 级容器 + 6 个 1 级组（其中「科学世纪」自身是容器）+ 2 个 2 级组；**物品 38 件**
（幻想之物 8 / 幻想之缘起 4 / 多方块 9 / 单方块 1 / 符卡 2 / INFO 9 / Power 5）。
其中**有真配方（会进合成表）的 17 件**（增强型工作台 5 + 魔法工作台 12），
**没有任何配方的 21 件**（= 38 − 17）：落叶 · 回响（这 2 件靠机制获得）· 炙热的灰烬 ·
多方块组里除反应堆核心 / 神社的木桩 / 赛钱箱之外的 6 件（反应堆框架 / 保护罩 / 稳定器 /
基座 / 输入接口 / 输出接口）· INFO 9 件 · **祭坛祈愿产出 3 件**（空白符卡 / 灵梦的大蝴蝶结 /
梦想封印 集 —— 它们的配方在赛钱箱自己的注册表里，不在物品的 9 格数组上）。
这 21 件里有 **7 件标着〔待补〕**（炙热的灰烬 + 6 件反应堆构件），
它们的指南页槽 10 会显示**屏障图标 + 「暂未开放 —— 目前只能由管理员发放（配方待补）」**；
祭坛那 3 件显示的是**赛钱箱图标 + 「在赛钱箱（祭坛）里祈愿产出」**（门面类型
`touhou:saizen_altar`，不是屏障）。
★ **祭坛祈愿配方本身有 3 条**（`SaizenbakoRecipes`，用 `/touhou altar list` 复核）——
它们不出现在"有真配方"那一栏里，因为 `RecipeType` 上挂的是门面而不是机器。
★ 上面这几组数字都能用 **`/touhou item all`** 一行复核：
`items total=36 withRealRecipe=18 noRecipe=18` + `byMachine={ENHANCED_CRAFTING_TABLE=6, MAGIC_WORKBENCH=12}`。
（2026-09-24 那一轮：7 件物品从"无配方 / 待补"改成魔法工作台，所以真配方 11 → 18、无配方 25 → 18、待补 13 → 7。）

---

## 二、物品与配方（逐件）

图例：`【类型】` 后面是配方类型（= 在哪台机器里合成）；`产出` 是**单次合成**得到几个；
`〔待补〕` = 目前不可合成；`—` = 非合成品（靠机制获得，见「获取方式」）。

### 1. 幻想之物（MATERIAL）

| 物品 | id | 配方类型 | 配方（3×3 阅读顺序） | 产出 | 获取方式 |
|---|---|---|---|---|---|
| 落叶 | `TOUHOU_MATERIAL_FALLEN_LEAVES` | — | — | — | 用手或普通工具破坏树叶时 20% 掉落 2~7 个（剪刀与精准采集不掉） |
| 另一个世界的回响 | `TOUHOU_MATERIAL_ECHO_OF_ANOTHER_WORLD` | 维度穿梭（门面） | — | — | 穿过维度之门时，把身上的 Slimefun 能量水晶化作 1 个（比例 1:1） |
| 炙热的灰烬 | `TOUHOU_MATERIAL_LOGIC_SINGULARITY` | — | 〔待补〕 | — | 暂未开放 —— 目前只能由管理员发放（配方待补） |
| 春泥 | `TOUHOU_MATERIAL_SPRING_MUD` | 增强型工作台 | 泥土·泥土·泥土 / 蒲公英·泥土·虞美人 / 泥土·泥土·泥土 | 1 | 在增强型工作台合成 |
| POINT | `TOUHOU_MATERIAL_POINT` | 增强型工作台 | 青金石·雾中の妖精·青金石 / 红叶飞散の天狗·魔法结晶 - III·报春の妖精 / 青金石·冰の妖精·青金石 | 1 | 在增强型工作台合成 |
| P引擎 | `TOUHOU_MATERIAL_P_ENGINE` | 增强型工作台 | 铜线·铜线·铜线 / 碳·钢板·锌锭 / 碳·POINT·锌锭 | **8** | 在增强型工作台合成 |
| 空白符卡 | `TOUHOU_MATERIAL_BLANK_SPELLCARD` | 祭坛祈愿（门面 `touhou:saizen_altar`） | 〔不是 3×3：6 根木桩 →〕POINT×4 · 纸×12 · 青金石×8 · 回响×4 · 红色染料×16 · 红石粉×24 | 1 | 在赛钱箱（祭坛）里祈愿产出 |
| 灵梦的大蝴蝶结 | `TOUHOU_MATERIAL_REIMU_RIBBON` | 祭坛祈愿（门面 `touhou:saizen_altar`） | 〔6 根木桩 →〕红色染料×4 · 白色染料×1 · 布×4 · 布×4 · POINT×1 · 回响×2 | 1 | 在赛钱箱（祭坛）里祈愿产出 |

> ★ **"祭坛祈愿"这种配方不在 3×3 里**：它是"6 根木桩各放什么 + 数量"，由 `SaizenbakoRecipes`
> 注册、`SaizenbakoManager` 匹配。物品自己的 `RecipeType` 是**门面类型** `touhou:saizen_altar`
> （指南页槽 10 显示赛钱箱图标 + "在赛钱箱（祭坛）里祈愿产出"），配方数组是 9 格全空
> ⇒ 任何工作台/机器里都摆不出来（运行期读数：`gridFilled=0 machineRecipes=0`）。
> ★ 「布」= `SlimefunItems.CLOTH`（粘液本体物品，材质 `PAPER`）——**不是**原版白羊毛，
> 见 `docs\material-names.md` 的同名小节。

### 2. 幻想之缘起（CHARACTER）

| 物品 | id | 配方类型 | 配方（3×3） | 产出 | 获取方式 / 特殊效果 |
|---|---|---|---|---|---|
| 报春の妖精 | `TOUHOU_CHARACTER_SPRING_HERALD` | 魔法工作台 | 春泥·春泥·春泥 / 春泥·水桶·春泥 / 春泥·春泥·春泥 | **2** | 在魔法工作台合成 |
| 红叶飞散の天狗 | `TOUHOU_CHARACTER_MOMIJI_TENGU` | 魔法工作台 | 红色染料·落叶·魔法结晶 - III / 落叶·另一个世界的回响·落叶 / 魔法结晶 - III·落叶·红色染料 | 1 | 在魔法工作台合成；戴在头上：+60% 速度 / +12 生命上限 / +3 盔甲 / +1 盔甲韧性 |
| 冰の妖精 | `TOUHOU_CHARACTER_CIRNO` | 魔法工作台 | 水桶·春泥·冰 / 春泥·报春の妖精·春泥 / 浮冰·春泥·蓝冰 | 1 | 在魔法工作台合成；放下后右键：9×9×9 内水（水源+水流）→ 冰、范围内实体缓慢 IX 5 秒、聊天栏蓝色 `Bakabaka…`；冷却 8 秒/方块 |
| 雾中の妖精 | `TOUHOU_CHARACTER_FAIRY_IN_MIST` | 魔法工作台 | 落叶·TNT·落叶 / 春泥·春泥·春泥 / 空白符文·春泥·空白符文 | 1 | 在魔法工作台合成；**对空气右键**消耗 1 个 → 身前 1 格生成无敌 `Bomb` 苦力怕，1 秒后消失并留下绿色粒子 + 带「保护 IX」的 Bomb 物品 + 聊天栏绿色 `Bomb`；右键方块 = 放下（放下后的方块右键无反应） |

### 3. 科学世纪 → 多方块大型机器（COMPLEX_MACHINE）

| 物品 | id | 配方类型 | 配方（3×3） | 产出 | 获取方式 |
|---|---|---|---|---|---|
| 旧地狱-灵乌路空反应堆（核心） | `TOUHOU_COMPLEX_MACHINE_UTSUHO_REACTOR_CORE` | 增强型工作台 | 钢筋板·起泡锭·钢筋板 / 电动马达·奇怪的下界粘液·电动马达 / 钢筋板·黑金刚石·钢筋板 | 1 | 在增强型工作台合成，再搭建完整的多方块结构（结构见物品描述） |
| 反应堆框架 | `TOUHOU_COMPLEX_MACHINE_REACTOR_FRAME` | — | 〔待补〕 | — | 暂未开放（待补） |
| 反应堆保护罩 | `TOUHOU_COMPLEX_MACHINE_REACTOR_SHIELD` | — | 〔待补〕 | — | 暂未开放（待补） |
| 反应堆稳定器 | `TOUHOU_COMPLEX_MACHINE_REACTOR_STABILIZER` | — | 〔待补〕 | — | 暂未开放（待补） |
| 反应堆基座 | `TOUHOU_COMPLEX_MACHINE_REACTOR_BASE` | — | 〔待补〕 | — | 暂未开放（待补） |
| 反应堆输入接口 | `TOUHOU_COMPLEX_MACHINE_REACTOR_INPUT_PORT` | — | 〔待补〕 | — | 暂未开放（待补） |
| 反应堆输出接口 | `TOUHOU_COMPLEX_MACHINE_REACTOR_OUTPUT_PORT` | — | 〔待补〕 | — | 暂未开放（待补） |
| 神社的木桩 | `TOUHOU_COMPLEX_MACHINE_SHRINE_POST` | 魔法工作台 | 红色染料·橡木原木·红色染料 / 纸·线·纸 / 红色染料·橡木原木·红色染料 | 1 | 在魔法工作台合成（描述整行红色） |
| 赛钱箱（核心） | `TOUHOU_COMPLEX_MACHINE_SAIZENBAKO` | 魔法工作台 | 橡木原木·另一个世界的回响·橡木原木 / 橡木原木·POWER集成核心·橡木原木 / 橡木原木·橡木原木·橡木原木 | 1 | 在魔法工作台合成，再搭完整结构 + 点信息格激活 |

**赛钱箱的祈愿配方**（不是 3×3，而是"6 根木桩各自放什么"；木桩编号按 +X → +Z 顺序 0~5）：

| 配方名 | 木桩 0 | 木桩 1 | 木桩 2 | 木桩 3 | 木桩 4 | 木桩 5 | 产物 |
|---|---|---|---|---|---|---|---|
| 空白符卡 | POINT×4 | 纸×12 | 青金石×8 | 另一个世界的回响×4 | 红色染料×16 | 红石粉×24 | 空白符卡×1 |
| 灵梦的大蝴蝶结 | 红色染料×4 | 白色染料×1 | 布×4 | 布×4 | POINT×1 | 另一个世界的回响×2 | 灵梦的大蝴蝶结×1 |
| 梦想封印 集 | （不限制） | POWER存储单元×2 | 空白符卡×1 | 灵梦的大蝴蝶结×4 | 红色染料×16 | （不限制） | 梦想封印 集×1 |

> ★ 「布」= `SlimefunItems.CLOTH`（材质 `PAPER`）；「红石粉」= `Material.REDSTONE`。
> ★ 「（不限制）」= 该格**没登记要求**（用户原文写的是"无"），放什么都行、也不参与消耗。
> ★ 匹配是"按注册顺序取第一条命中的"，但这 3 条**互不冲突**：它们在同一批槽位上放的料
> 两两不同，而匹配要求"该槽位存在且满足"。运行期由 **`/touhou altar test`** 的
> 交叉匹配矩阵证明（3 条各自只命中自己，且两个反例都被拦下）。

> ★★ <b>历史：2026-09-24 原来的「赛钱箱-基础祈愿」曾被删除</b>
> （那条是 红色染料 / 钻石 / 下界之星 / POWER集成核心 / 红色染料 / 红色染料 → **POWER存储单元 ×4**，
> 因为 POWER存储单元 改成了魔法工作台合成）。当时配方表因此变成 **0 条**、
> 机器"永不产出"（GUI 显示「无匹配配方」，不是静默）。
> ★ **2026-09-25 起不再是这样**：上面那 3 条把配方表补回 **3 条**，
> 于是赛钱箱"能搭、也能产出"了（用量 `/touhou altar list` 复核；消耗仍是每次祈愿另扣 **2 POWER**）。

### 4. 科学世纪 → 单方块机器（SIMPLE_MACHINE）

| 物品 | id | 配方类型 | 配方（3×3） | 产出 | 获取方式 / 效果 |
|---|---|---|---|---|---|
| 丰收之时 | `TOUHOU_SIMPLE_MACHINE_HARVEST_TIME` | 魔法工作台 | 骨块·小麦·骨块 / 小麦种子·另一个世界的回响·小麦种子 / 骨块·小麦·骨块 | 1 | 在魔法工作台合成；右键：9×9×3 内作物强制催熟；冷却 5 秒 |

### 5. Flee into Gensokyo（PARTY_ITEM）

| 物品 | id | 配方类型 | 配方（3×3） | 产出 | 获取方式 |
|---|---|---|---|---|---|
| 梦想封印 集 | `TOUHOU_PARTY_ITEM_FANTASY_SEAL_CONVERGE` | 祭坛祈愿（门面 `touhou:saizen_altar`） | 〔6 根木桩 →〕（空）· POWER存储单元×2 · 空白符卡×1 · 灵梦的大蝴蝶结×4 · 红色染料×16 ·（空） | 1 | 在赛钱箱（祭坛）里祈愿产出（★ 2026-09-25 从增强型工作台改过来） |
| 杀意的百合 | `TOUHOU_PARTY_ITEM_MURDEROUS_LILY` | 增强型工作台 | （空）·黑金刚石·（空） / （空）·钢筋板·（空） / （空）·电动马达·（空） | 1 | 在增强型工作台合成 |

> ★ 2026-09-25：梦想封印 集 的原增强型工作台配方（竖条 炙热灰烬 / 钢筋板 / 电动马达）
> **已按用户要求删除**，改走赛钱箱祈愿 ⇒ 现在增强型工作台这条竖条**只剩杀意的百合**一张
> （原先"靠顶端材料不同避免撞车"的顾虑实际上已不存在，但百合的配方保持原样未动）。
> —— 9 格图案 + 配方类型完全相同的话，**后注册的会覆盖前一个**。

### 6. INFO（说明与 GUI 功能件）

| 物品 | id | 配方 | 获取方式 |
|---|---|---|---|
| 模式切换玻璃板 | `TOUHOU_INFO_MODESHIFT` | 无 | 代码内置的 GUI 功能件（不通过合成获得） |
| 插件消息 | `TOUHOU_INFO_PLUGIN_MESSAGE` | 无 | 代码内置的说明纸品（不通过合成获得） |
| 声明 1/3 / 2/3 / 3/3 | `TOUHOU_INFO_DECLARATION_1/2/3` | 无 | 同上 |
| 酒石酸菌 | `TOUHOU_INFO_TARTARIC_ACID` | 无 | 同上 |
| 上海爱丽丝幻乐团 | `TOUHOU_INFO_TEAM_SHANGHAI_ALICE` | 无 | 同上 |
| Ning_Meng__ | `TOUHOU_INFO_NING_MENG` | 无 | 同上 |
| matl114 | `TOUHOU_INFO_MATL114` | 无 | 同上 |

### 7. Power（自研 POWER 能源）

| 物品 | id | 配方类型 | 配方（3×3） | 产出 | 获取方式 / 作用 |
|---|---|---|---|---|---|
| POWER集成核心 | `TOUHOU_POWER_POWER_INTEGRATED_CORE` | 魔法工作台 | 能源调节器·P引擎·（空） / 冰の妖精·POINT·报春の妖精 / （空）·P引擎·能源调节器 | **2** | 在魔法工作台合成；联网核心（跳接半径 7，切比雪夫距离） |
| POWER中继器 | `TOUHOU_POWER_POWER_REPEATER` | 魔法工作台 | （空）·红石中继器·（空） / 魔法糖·P引擎·魔法糖 / （空）·红石中继器·（空） | **8** | 在魔法工作台合成；延长/转接网络 |
| POWER存储单元 | `TOUHOU_POWER_POWER_STORAGE_UNIT` | 魔法工作台 | 硅·镁盐·硅 / 红石块·POWER集成核心·青金石块 / 硅·镁盐·硅 | 1 | 在魔法工作台合成（祭坛那条配方已删）；容量 25 |
| 幻梦捕捉器 | `TOUHOU_POWER_DREAMCATCHER` | 魔法工作台 | （空）·红色床·（空） / 报春の妖精·另一个世界的回响·报春の妖精 / P引擎·红色床·P引擎 | 1 | 在魔法工作台合成；发电机：东南西北四面各算一张床（一面只算一张，头上脚下不算），每轮 1 点 POWER，间隔 = 8 秒 ÷ 床数（1 张床 8 秒 1 点、4 张床 2 秒 1 点）；电优先并入网络，自身缓冲上限 15 点，没有床则完全不产出 |
| POWER供给单元 | `TOUHOU_POWER_POWER_SUPPLY_UNIT` | 魔法工作台 | （空）·GPS 发射器·（空） / P引擎·POWER集成核心·P引擎 / （空）·GPS 发射器·（空） | 1 | 在魔法工作台合成；无线供电（给手上的符卡充能） |

> ★ 五件的**名字与描述都是红 → 白逐字符渐变**（幻梦捕捉器是黑紫 → 深蓝），
> 描述最后一行「使你充满了抛瓦。」是**灰 + 删除线**（`§7§m…`）——
> 用户口径是"无特殊说明，物品介绍的字体与物品名字字体一致"，只有被 `//` 标注的行例外。

---

## 三、配方总览（按"在哪台机器合成"归并）

| 机器 | 配方条数 | 物品 |
|---|---|---|
| **增强型工作台** | 5 | 春泥 · **反应堆核心** · **杀意的百合** · **POINT** · **P引擎**（产出 8） |
| **魔法工作台** | 12 | **报春の妖精**（产出 2）· **红叶飞散の天狗** · **丰收之时** · **冰の妖精** · **雾中の妖精** · **POWER集成核心**（产出 2）· **POWER中继器**（产出 8）· **POWER存储单元** · **幻梦捕捉器** · **POWER供给单元** · **神社的木桩** · **赛钱箱** |
| **赛钱箱（祭坛）祈愿** | 3 | **空白符卡** · **灵梦的大蝴蝶结** · **梦想封印 集**（★ 这三条是"6 根木桩 × 数量"，不是 3×3；用 `/touhou altar list` 复核） |
| **无配方**（机制获取 / 待补 / 祭坛祈愿门面） | 21 | 落叶 · 回响（维度穿梭）· 炙热的灰烬 · 反应堆构件 6 件 · INFO 9 件 · 祭坛祈愿产出 3 件（空白符卡 / 灵梦的大蝴蝶结 / 梦想封印 集） |

**待补配方的 7 件**：炙热的灰烬、反应堆框架/保护罩/稳定器/基座/输入接口/输出接口。
（另有 9 件 INFO 纸品是**刻意不给配方**的说明物，不算待补；
落叶 / 回响 靠机制获得 —— 它们**已经被当作材料消耗**：落叶用于红叶飞散の天狗 ×4 与雾中の妖精 ×2，
回响用于红叶飞散の天狗 / 丰收之时 / 幻梦捕捉器 / 赛钱箱 / **空白符卡 / 灵梦的大蝴蝶结 / 梦想封印 集** 各若干。）

> ★ 与上一版的差异（2026-09-24）：**7 件**从"无配方 / 待补 / 核心门面"改成了魔法工作台合成
> （5 件 POWER 设备 + 神社的木桩 + 赛钱箱）⇒ 真配方 11 → **18**、无配方 25 → **18**、待补 13 → **7**。
> 两个核心（反应堆 / 赛钱箱）现在**都有**真配方，所以"无配方"那一行不再有"核心门面"这一项。

> ★★ **2026-09-25（第二轮）**：新增 **空白符卡 · 灵梦的大蝴蝶结**（祭坛祈愿产出），
> 并把 **梦想封印 集** 从增强型工作台改成**祭坛祈愿**（原 `goheiRecipe()` 已删）⇒
> 物品 **36 → 38**、真配方 **18 → 17**（增强型工作台 6 → **5**）、无配方 **18 → 21**、
> **祭坛祈愿 0 → 3 条**；待补仍是 7。
> ★ 运行期读数（可自行核对）：`/touhou item all` →
> `items total=38 withRealRecipe=17 noRecipe=21` + `byMachine={ENHANCED_CRAFTING_TABLE=5, MAGIC_WORKBENCH=12}`；
> `/touhou altar list` → `count=3`；`/touhou altar test` → 3 条全部 ✔（正例命中自己 / 交叉命中=无 / 两个反例都拦下）。

> ★★ **2026-09-25 逐件复核**：用 `/touhou item <id>` 把
> **全部有配方的物品**的「配方类型 / 9 格逐格 / 三条产出路径」与上表逐行对照，**完全一致**
> （含 POWER集成核心 2、POWER中继器 8、报春の妖精 2、P引擎 8 四处多产出）；
> 无配方物品（含祭坛那 3 件）的 `gridFilled=0 machineRecipes=0` 也与本表一致。

> ★★ **2026-09-25 材料名统一为客户端官方译名**（用户拍板）：本表的 3×3 里不再用项目习惯叫法 ——
> 旧叫法 → 官方译名：**强化板 → 钢筋板**、**鼓胀锭III → 起泡锭**、**碳素 → 黑金刚石**、
> **下界粘液球 → 奇怪的下界粘液**、**魔法结晶III → 魔法结晶 - III**（官方名带 ` - `、没有 III 后缀）、
> **GPS发射器 → GPS 发射器**（官方名中间有一个空格）；机器名统一写 **增强型工作台**。
> ★ 权威来源 = `/touhou names` 读出的 `ItemMeta#getDisplayName()`（**不是凭记忆**），
> 完整对照表（含本体字段名 / 粘液 id）见 **`docs\material-names.md`**。
> ★ **只有"给人看的名字"变了**：字段名 / 粘液 id / 9 格图案 / 产出数量一律未动。
> ★ 仍未改的一处：反应堆那 6 件构件在本表里是简称（「反应堆框架」），
> 完整显示名带「旧地狱-」前缀（见 `docs\material-names.md` 第二节）—— 改不改由用户定。

---

## 四、怎么自己复核这份树

```powershell
# 组层级 / 组显示名与 key（含"改了名没改 key"的核对）
touhou groups
# 全部物品的获取方式标注（唯一出处 = Acquisition.SOURCE_BY_ID；无 [MISS] 即全覆盖）
touhou acquisition all
# ★ 物品统计（本文件里所有计数的唯一复核口径）：
#   items total=36 withRealRecipe=18 noRecipe=18 / byMachine={ENHANCED_CRAFTING_TABLE=6, MAGIC_WORKBENCH=12}
touhou item all
# ★ 通用逐件读数：模板(id/组/材质/数量) + 名称与描述逐字符颜色 + 三条产出路径 + 9 格逐格
touhou item TOUHOU_POWER_POWER_INTEGRATED_CORE      # 产出 2 的样板
touhou item TOUHOU_POWER_POWER_REPEATER             # 产出 8 的样板
touhou item TOUHOU_POWER_POWER_STORAGE_UNIT TOUHOU_POWER_DREAMCATCHER
touhou item TOUHOU_POWER_POWER_SUPPLY_UNIT
touhou item TOUHOU_COMPLEX_MACHINE_SHRINE_POST TOUHOU_COMPLEX_MACHINE_SAIZENBAKO
# 逐件的专用自检（读数更细，例如头贴图逐字符比对）
touhou springherald recipe / touhou pengine recipe
touhou point recipe / touhou cirno recipe / touhou momiji recipe / touhou fairy recipe
# 祭坛侧：SaizenbakoRecipes 的条数（现为 0）+ 粘液书自定义配方页内容
touhou guide
```

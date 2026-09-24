# Touhou (TH Tech) 全景树状图 —— 物品组 / 物品 / 配方

> **数据来源**：直接从代码的**唯一出处**读出（`AddGroups` 建组与显隐、`AddSlimefunItems.setup()` 的注册调用、
> `AddItems.setup()` 的模板、`Acquisition.SOURCE_BY_ID` 的获取方式、各 `xxxRecipe()` 方法的 9 格数组）。
> 生成时 HEAD = **`b5a3a0d`**（本地领先 origin 19 个提交）。
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
│     └── P引擎                 TOUHOU_MATERIAL_P_ENGINE
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
│   │     ├── 神社的木桩            TOUHOU_COMPLEX_MACHINE_SHRINE_POST           〔配方待补〕
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
      ├── POWER集成核心            TOUHOU_POWER_POWER_INTEGRATED_CORE   〔配方待补〕
      ├── POWER中继器              TOUHOU_POWER_POWER_REPEATER          〔配方待补〕
      ├── POWER存储单元            TOUHOU_POWER_POWER_STORAGE_UNIT      〔配方待补〕
      ├── 幻梦捕捉器               TOUHOU_POWER_DREAMCATCHER            〔配方待补〕
      └── POWER供给单元            TOUHOU_POWER_POWER_SUPPLY_UNIT       〔配方待补〕
```

**小计**：1 个 0 级容器 + 6 个 1 级组（其中「科学世纪」自身是容器）+ 2 个 2 级组；**物品 36 件**
（幻想之物 6 / 幻想之缘起 4 / 多方块 9 / 单方块 1 / 符卡 2 / INFO 9 / Power 5）。
其中**有真配方（会进合成表）的 11 件**（增强型工作台 6 + 魔法工作台 5），
**没有任何配方的 25 件**（= 36 − 11）：落叶 · 回响（这 2 件靠机制获得）· 炙热的灰烬 ·
多方块组里除反应堆核心外的 8 件（7 件结构件 + 赛钱箱核心门面）· INFO 9 件 · POWER 5 件。
这 25 件里有 **13 件标着〔待补〕**（炙热的灰烬 + 7 件多方块结构件 + 5 件 POWER 设备），
它们的指南页槽 10 会显示**屏障图标 + 「暂未开放 —— 目前只能由管理员发放（配方待补）」**。

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
| 春泥 | `TOUHOU_MATERIAL_SPRING_MUD` | 增强型工作台 | 泥土·泥土·泥土 / 蒲公英·泥土·玫瑰 / 泥土·泥土·泥土 | 1 | 在增强型工作台合成 |
| POINT | `TOUHOU_MATERIAL_POINT` | 增强型工作台 | 青金石·雾中の妖精·青金石 / 红叶飞散の天狗·魔法结晶III·报春の妖精 / 青金石·冰の妖精·青金石 | 1 | 在增强型工作台合成 |
| P引擎 | `TOUHOU_MATERIAL_P_ENGINE` | 增强型工作台 | 铜线·铜线·铜线 / 碳·钢板·锌锭 / 碳·POINT·锌锭 | **8** | 在增强型工作台合成 |

### 2. 幻想之缘起（CHARACTER）

| 物品 | id | 配方类型 | 配方（3×3） | 产出 | 获取方式 / 特殊效果 |
|---|---|---|---|---|---|
| 报春の妖精 | `TOUHOU_CHARACTER_SPRING_HERALD` | 魔法工作台 | 春泥·春泥·春泥 / 春泥·水桶·春泥 / 春泥·春泥·春泥 | **2** | 在魔法工作台合成 |
| 红叶飞散の天狗 | `TOUHOU_CHARACTER_MOMIJI_TENGU` | 魔法工作台 | 红色染料·落叶·魔法结晶III / 落叶·另一个世界的回响·落叶 / 魔法结晶III·落叶·红色染料 | 1 | 在魔法工作台合成；戴在头上：+60% 速度 / +12 生命上限 / +3 盔甲 / +1 盔甲韧性 |
| 冰の妖精 | `TOUHOU_CHARACTER_CIRNO` | 魔法工作台 | 水桶·春泥·冰 / 春泥·报春の妖精·春泥 / 浮冰·春泥·蓝冰 | 1 | 在魔法工作台合成；放下后右键：9×9×9 内水（水源+水流）→ 冰、范围内实体缓慢 IX 5 秒、聊天栏蓝色 `Bakabaka…`；冷却 8 秒/方块 |
| 雾中の妖精 | `TOUHOU_CHARACTER_FAIRY_IN_MIST` | 魔法工作台 | 落叶·TNT·落叶 / 春泥·春泥·春泥 / 空白符文·春泥·空白符文 | 1 | 在魔法工作台合成；**对空气右键**消耗 1 个 → 身前 1 格生成无敌 `Bomb` 苦力怕，1 秒后消失并留下绿色粒子 + 带「保护 IX」的 Bomb 物品 + 聊天栏绿色 `Bomb`；右键方块 = 放下（放下后的方块右键无反应） |

### 3. 科学世纪 → 多方块大型机器（COMPLEX_MACHINE）

| 物品 | id | 配方类型 | 配方（3×3） | 产出 | 获取方式 |
|---|---|---|---|---|---|
| 旧地狱-灵乌路空反应堆（核心） | `TOUHOU_COMPLEX_MACHINE_UTSUHO_REACTOR_CORE` | 增强型工作台 | 强化板·鼓胀锭III·强化板 / 电动马达·下界粘液球·电动马达 / 强化板·碳素·强化板 | 1 | 在增强型工作台合成，再搭建完整的多方块结构（结构见物品描述） |
| 反应堆框架 | `TOUHOU_COMPLEX_MACHINE_REACTOR_FRAME` | — | 〔待补〕 | — | 暂未开放（待补） |
| 反应堆保护罩 | `TOUHOU_COMPLEX_MACHINE_REACTOR_SHIELD` | — | 〔待补〕 | — | 暂未开放（待补） |
| 反应堆稳定器 | `TOUHOU_COMPLEX_MACHINE_REACTOR_STABILIZER` | — | 〔待补〕 | — | 暂未开放（待补） |
| 反应堆基座 | `TOUHOU_COMPLEX_MACHINE_REACTOR_BASE` | — | 〔待补〕 | — | 暂未开放（待补） |
| 反应堆输入接口 | `TOUHOU_COMPLEX_MACHINE_REACTOR_INPUT_PORT` | — | 〔待补〕 | — | 暂未开放（待补） |
| 反应堆输出接口 | `TOUHOU_COMPLEX_MACHINE_REACTOR_OUTPUT_PORT` | — | 〔待补〕 | — | 暂未开放（待补） |
| 神社的木桩 | `TOUHOU_COMPLEX_MACHINE_SHRINE_POST` | — | 〔待补〕 | — | 暂未开放（待补） |
| 赛钱箱（核心） | `TOUHOU_COMPLEX_MACHINE_SAIZENBAKO` | 核心门面 `touhou:saizenbako` | 〔无 3×3；配方见下方「祭坛祈愿」〕 | — | 搭建完整的多方块结构后放入核心（核心件为神社的木桩） |

**赛钱箱的祈愿配方**（不是 3×3，而是"6 根木桩各自放什么"；木桩编号按 +X → +Z 顺序 0~5）：

| 配方名 | 木桩 0 | 木桩 1 | 木桩 2 | 木桩 3 | 木桩 4 | 木桩 5 | 产物 |
|---|---|---|---|---|---|---|---|
| 赛钱箱-基础祈愿 | 红色染料 ×1 | 钻石 ×1 | 下界之星 ×1 | POWER集成核心 ×1 | 红色染料 ×1 | 红色染料 ×1 | **POWER存储单元 ×4** |

（消耗：每次祈愿另扣 **2 POWER**。加配方只需在 `SaizenbakoRecipes.setup()` 里 `register(...)` 一条，**注册在前面的优先匹配**。）

### 4. 科学世纪 → 单方块机器（SIMPLE_MACHINE）

| 物品 | id | 配方类型 | 配方（3×3） | 产出 | 获取方式 / 效果 |
|---|---|---|---|---|---|
| 丰收之时 | `TOUHOU_SIMPLE_MACHINE_HARVEST_TIME` | 魔法工作台 | 骨块·小麦·骨块 / 小麦种子·另一个世界的回响·小麦种子 / 骨块·小麦·骨块 | 1 | 在魔法工作台合成；右键：9×9×3 内作物强制催熟；冷却 5 秒 |

### 5. Flee into Gensokyo（PARTY_ITEM）

| 物品 | id | 配方类型 | 配方（3×3） | 产出 | 获取方式 |
|---|---|---|---|---|---|
| 梦想封印 集 | `TOUHOU_PARTY_ITEM_FANTASY_SEAL_CONVERGE` | 增强型工作台 | （空）·炙热的灰烬·（空） / （空）·强化板·（空） / （空）·电动马达·（空） | 1 | 在增强型工作台合成 |
| 杀意的百合 | `TOUHOU_PARTY_ITEM_MURDEROUS_LILY` | 增强型工作台 | （空）·碳素·（空） / （空）·强化板·（空） / （空）·电动马达·（空） | 1 | 在增强型工作台合成 |

> ★ 两条都是"竖着一条"，靠**顶端材料不同**（炙热的灰烬 vs 碳素）避免配方撞车
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

| 物品 | id | 配方 | 获取方式 | 作用 |
|---|---|---|---|---|
| POWER集成核心 | `TOUHOU_POWER_POWER_INTEGRATED_CORE` | 〔待补〕 | 暂未开放（待补） | 联网核心（跳接半径 7，切比雪夫距离） |
| POWER中继器 | `TOUHOU_POWER_POWER_REPEATER` | 〔待补〕 | 暂未开放（待补） | 延长/转接网络 |
| POWER存储单元 | `TOUHOU_POWER_POWER_STORAGE_UNIT` | 〔待补〕（祭坛可产出 4 个） | 暂未开放（待补）；另有赛钱箱祈愿产出 | 容量 25 |
| 幻梦捕捉器 | `TOUHOU_POWER_DREAMCATCHER` | 〔待补〕 | 暂未开放（待补） | 发电机：相邻床 N/S/E/W，8 秒 1 POWER |
| POWER供给单元 | `TOUHOU_POWER_POWER_SUPPLY_UNIT` | 〔待补〕 | 暂未开放（待补） | 无线供电（给手上的符卡充能） |

---

## 三、配方总览（按"在哪台机器合成"归并）

| 机器 | 配方条数 | 物品 |
|---|---|---|
| **增强型工作台** | 6 | 春泥 · **反应堆核心** · **梦想封印 集** · **杀意的百合** · **POINT** · **P引擎**（产出 8） |
| **魔法工作台** | 5 | **报春の妖精**（产出 2）· **红叶飞散の天狗** · **丰收之时** · **冰の妖精** · **雾中の妖精** |
| **古代祭坛**（赛钱箱祈愿） | 1 | 基础祈愿 → POWER存储单元 ×4 |
| **无配方**（机制获取 / 待补 / 核心门面） | 25 | 落叶 · 回响（维度穿梭）· 炙热的灰烬 · 多方块结构件 7 件 · 赛钱箱（核心门面）· INFO 9 件 · POWER 5 件 |

**待补配方的 13 件**：炙热的灰烬、反应堆框架/保护罩/稳定器/基座/输入接口/输出接口、神社的木桩、
POWER集成核心、POWER中继器、POWER存储单元、幻梦捕捉器、POWER供给单元。
（另有 9 件 INFO 纸品是**刻意不给配方**的说明物，不算待补；
赛钱箱也不是待补 —— 它靠搭完整结构 + 放入核心件获得。）

---

## 四、怎么自己复核这份树

```powershell
# 组层级 / 组显示名与 key（含"改了名没改 key"的核对）
touhou groups
# 全部物品的获取方式标注（唯一出处 = Acquisition.SOURCE_BY_ID；无 [MISS] 即全覆盖）
touhou acquisition all
# 逐件的模板 / 颜色 / 配方读数（三条产出路径 + 9 格逐格打印）
touhou springherald recipe      # 产出 2 的样板
touhou pengine recipe           # 产出 8 的样板
touhou point recipe / touhou cirno recipe / touhou momiji recipe / touhou fairy recipe
```

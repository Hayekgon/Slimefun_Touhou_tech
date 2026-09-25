# 粘液材料名对照表（**客户端官方译名**为准）

> **用途**：代码注释与文档里凡是要写"某个材料叫什么"，一律**以本表为准**。
> 以前用的是**项目习惯叫法**（强化板 / 鼓胀锭III / 碳素 / 下界粘液球 / 魔法结晶III），
> 玩家按那些名字在游戏里**找不到东西** —— 现在统一成客户端实际显示的名字。
>
> **数据来源 = 运行期读数，不是凭记忆写的**：命令 `/touhou names`（2026-09-25 实测）
> 读的是 `((ItemStack) stack).getItemMeta().getDisplayName()` —— **客户端显示的那个字符串**。
> 这条路不需要 Player（不像 `ItemGroup#getItem(Player)` / `ItemStack#getDisplayName(Player)`），
> 所以无头环境读到的就是权威值。
>
> ★ 本表**只统一"给人看的名字"**：`SlimefunItems` 字段名 / 粘液 id / `Material` 常量 /
> 9 格图案 / 产出数量 / 渐变 / lore **一律没动**（配方零变化）。
>
> **复现命令**
> ```powershell
> touhou names          # 本体 + 本项目都打
> touhou names sf       # 只打本体（SlimefunItems 那 23 个）
> touhou names ours     # 只打本项目（TOUHOU_ 前缀 36 件，按 id 排序）
> ```
> 机器可读行形如
> `[TOUHOU] names field=<字段名> id=<粘液id> name=<去色可见名> raw=<原样名（§ 转义成 \u00a7）>`。

---

## 一、本体材料（`SlimefunItems`）—— 本项目实际用到的 21 个 + 同族的 2 个

| 本体字段名 | 粘液 id | **官方译名（以本列为准）** | 项目旧叫法 / 备注 |
|---|---|---|---|
| `REINFORCED_PLATE` | `REINFORCED_PLATE` | **钢筋板** | 旧叫法「强化板」——**本轮已统一** |
| `BLISTERING_INGOT_3` | `BLISTERING_INGOT_3` | **起泡锭** | 旧叫法「鼓胀锭III」——官方名**没有 III 后缀**，本轮已统一 |
| `CARBONADO` | `CARBONADO` | **黑金刚石** | 旧叫法「碳素」——本轮已统一 |
| `STRANGE_NETHER_GOO` | `STRANGE_NETHER_GOO` | **奇怪的下界粘液** | 旧叫法「下界粘液球」——本轮已统一 |
| `MAGIC_LUMP_3` | `MAGIC_LUMP_3` | **魔法结晶 - III** | 旧叫法「魔法结晶III」——官方是 ` - ` 分隔，本轮已统一 |
| `MAGIC_LUMP_2` | `MAGIC_LUMP_2` | 魔法结晶 - II | 本工程未使用，仅列出便于看全"魔法结晶"这一族 |
| `MAGIC_LUMP_1` | `MAGIC_LUMP_1` | 魔法结晶 - I | 同上 |
| `ENERGY_REGULATOR` | `ENERGY_REGULATOR` | 能源调节器 | 与旧写法一致，无需改 |
| `MAGIC_SUGAR` | `MAGIC_SUGAR` | 魔法糖 | 一致 |
| `SILICON` | `SILICON` | 硅 | 一致 |
| `MAGNESIUM_SALT` | `MAGNESIUM_SALT` | 镁盐 | 一致 |
| `GPS_TRANSMITTER` | `GPS_TRANSMITTER` | **GPS 发射器** | 旧写法「GPS发射器」少了中间的空格 —— 本轮已统一 |
| `STEEL_PLATE` | `STEEL_PLATE` | 钢板 | 一致 |
| `COPPER_WIRE` | `COPPER_WIRE` | 铜线 | 一致 |
| `CARBON` | `CARBON` | 碳 | 一致 |
| `ELECTRIC_MOTOR` | `ELECTRIC_MOTOR` | 电动马达 | 一致 |
| `BLANK_RUNE` | `BLANK_RUNE` | 空白符文 | 一致 |
| `ZINC_INGOT` | `ZINC_INGOT` | 锌锭 | 一致 |
| `OIL_BUCKET` | **`BUCKET_OF_OIL`** | 原油桶 | ★ **字段名与粘液 id 不同**，容易写错 |
| `FUEL_BUCKET` | **`BUCKET_OF_FUEL`** | 燃料桶 | ★ 同上 |
| `POWER_CRYSTAL` | `POWER_CRYSTAL` | 能量水晶 | 一致 |
| `ENHANCED_CRAFTING_TABLE` | `ENHANCED_CRAFTING_TABLE` | 增强型工作台 | 一致（用户口中的"强化工作台"**就是它**） |
| `MAGIC_WORKBENCH` | `MAGIC_WORKBENCH` | 魔法工作台 | 一致 |
| `ANCIENT_ALTAR` | `ANCIENT_ALTAR` | 古代祭坛 | 一致 |

★ 清单口径 = 用 `(?<![A-Za-z_])SlimefunItems\.` 在 `src` 下扫出的**全部 21 个字段**，
外加 `MAGIC_LUMP_1 / MAGIC_LUMP_2`（同族，便于对照）。

---

## 二、本项目自己的 36 件（核对"文档里的叫法"与显示名是否一致）

| 粘液 id | 显示名（客户端可见） | 名称里的颜色 | 与文档的关系 |
|---|---|---|---|
| `TOUHOU_CHARACTER_CIRNO` | 冰の妖精 | §x 逐字符渐变 ×4 | 一致 |
| `TOUHOU_CHARACTER_FAIRY_IN_MIST` | 雾中の妖精 | §x 逐字符渐变 ×5 | 一致 |
| `TOUHOU_CHARACTER_MOMIJI_TENGU` | 红叶飞散の天狗 | §x 逐字符渐变 ×7 | 一致 |
| `TOUHOU_CHARACTER_SPRING_HERALD` | 报春の妖精 | §x 逐字符渐变 ×5 | 一致 |
| `TOUHOU_COMPLEX_MACHINE_REACTOR_BASE` | 旧地狱-反应堆基座 | 传统色码 | ★ 文档里简称「反应堆基座」（省略了「旧地狱-」前缀） |
| `TOUHOU_COMPLEX_MACHINE_REACTOR_FRAME` | 旧地狱-反应堆框架 | 传统色码 | ★ 同上（「反应堆框架」） |
| `TOUHOU_COMPLEX_MACHINE_REACTOR_INPUT_PORT` | 旧地狱-反应堆输入接口 | 传统色码 | ★ 同上（「反应堆输入接口」） |
| `TOUHOU_COMPLEX_MACHINE_REACTOR_OUTPUT_PORT` | 旧地狱-反应堆输出接口 | 传统色码 | ★ 同上（「反应堆输出接口」） |
| `TOUHOU_COMPLEX_MACHINE_REACTOR_SHIELD` | 旧地狱-反应堆保护罩 | 传统色码 | ★ 同上（「反应堆保护罩」） |
| `TOUHOU_COMPLEX_MACHINE_REACTOR_STABILIZER` | 旧地狱-反应堆稳定器 | 传统色码 | ★ 同上（「反应堆稳定器」） |
| `TOUHOU_COMPLEX_MACHINE_SAIZENBAKO` | 赛钱箱 | §x 逐字符渐变 ×3 | 一致 |
| `TOUHOU_COMPLEX_MACHINE_SHRINE_POST` | 神社的木桩 | §x 逐字符渐变 ×5 | 一致 |
| `TOUHOU_COMPLEX_MACHINE_UTSUHO_REACTOR_CORE` | 旧地狱-灵乌路空反应堆 | 传统色码 | 文档写作「旧地狱-灵乌路空反应堆（核心）」✓ 一致 |
| `TOUHOU_INFO_DECLARATION_1` | 声明-1 | 传统色码 | 文档标签「声明 1/3」（说明性标签，非物品名） |
| `TOUHOU_INFO_DECLARATION_2` | 声明-2 | 传统色码 | 同上 |
| `TOUHOU_INFO_DECLARATION_3` | 声明-3 | 传统色码 | 同上 |
| `TOUHOU_INFO_MATL114` | matl114 | 传统色码 | 一致（★ 显示名全小写，id 全大写） |
| `TOUHOU_INFO_MODESHIFT` | 模式切换 | 传统色码 | 文档标签「模式切换玻璃板」（说明性标签） |
| `TOUHOU_INFO_NING_MENG` | Ning_Meng__ | 传统色码 | 一致 |
| `TOUHOU_INFO_PLUGIN_MESSAGE` | 插件消息 | 传统色码 | 一致 |
| `TOUHOU_INFO_TARTARIC_ACID` | 酒石酸菌 | 传统色码 | 一致 |
| `TOUHOU_INFO_TEAM_SHANGHAI_ALICE` | 上海爱丽丝幻乐团 | 传统色码 | 一致 |
| `TOUHOU_MATERIAL_ECHO_OF_ANOTHER_WORLD` | 另一个世界的回响 | 传统色码 | 一致 |
| `TOUHOU_MATERIAL_FALLEN_LEAVES` | 落叶 | §x 逐字符渐变 ×2 | 一致 |
| `TOUHOU_MATERIAL_LOGIC_SINGULARITY` | 炙热的灰烬 | 传统色码 | 一致 |
| `TOUHOU_MATERIAL_POINT` | POINT | §x 逐字符渐变 ×5 | 一致 |
| `TOUHOU_MATERIAL_P_ENGINE` | P引擎 | §x 逐字符渐变 ×3 | 一致 |
| `TOUHOU_MATERIAL_SPRING_MUD` | 春泥 | 传统色码 | 一致 |
| `TOUHOU_PARTY_ITEM_FANTASY_SEAL_CONVERGE` | 梦想封印 集 | 传统色码 | 一致（中间那个空格是名字的一部分） |
| `TOUHOU_PARTY_ITEM_MURDEROUS_LILY` | 杀意的百合 | 传统色码 | 一致 |
| `TOUHOU_POWER_DREAMCATCHER` | 幻梦捕捉器 | §x 逐字符渐变 ×5 | 一致 |
| `TOUHOU_POWER_POWER_INTEGRATED_CORE` | POWER集成核心 | §x 逐字符渐变 ×9 | 一致 |
| `TOUHOU_POWER_POWER_REPEATER` | POWER中继器 | §x 逐字符渐变 ×8 | 一致 |
| `TOUHOU_POWER_POWER_STORAGE_UNIT` | POWER存储单元 | §x 逐字符渐变 ×9 | 一致 |
| `TOUHOU_POWER_POWER_SUPPLY_UNIT` | POWER供给单元 | §x 逐字符渐变 ×9 | 一致 |
| `TOUHOU_SIMPLE_MACHINE_HARVEST_TIME` | 丰收之时 | §x 逐字符渐变 ×4 | 一致 |

★ "名称里的颜色"一列来自 `/touhou names ours` 的 `raw=`（**原样串，刻意没 strip**）：
`§x 逐字符渐变 ×N` = 该名字里有 N 个 `§x§R§R§G§G§B§B` 序列（每个可见字符一个）；
`传统色码` = 用 `§a` / `§f` 这类传统码，没有十六进制序列。
★ 唯一值得注意的**不是**译名问题：反应堆那 6 件构件在文档里长期写成「反应堆框架」等简称，
而显示名带「旧地狱-」前缀 —— 本表如实标出，改不改由用户定（本轮未改，避免与 `/touhou` 命令
与结构层图里的简称产生新的不一致）。

---

## 三、原版材质（`Material`）—— 官方译名的来源，以及本轮改掉的两个

★ **原版材质没有"服务端中文名"**：`/touhou names` 读的是 `ItemMeta#getDisplayName()`，
而原版物品的 meta 里根本没有中文名（无头服上读出来是 `Dirt` / `Poppy` 这种**英文材质名**）。
所以原版名字的权威来源是**客户端自带的语言文件**：

```
<客户端>/.minecraft/assets/indexes/<asset-index>.json   → 查出 minecraft/lang/zh_cn.json 的 hash
<客户端>/.minecraft/assets/objects/<hash 前两位>/<hash>  → 就是 zh_cn.json
```

本次核实用的是用户本机客户端（Minecraft 1.20.4 / Fabric，asset index `12`）里的那一份，
键值形如 `block.minecraft.hay_block = 干草捆` / `item.minecraft.poppy`（旧键）等。

| 本项目用到的 `Material` | 官方译名（客户端 `zh_cn.json`） | 与文档的关系 |
|---|---|---|
| `POPPY` | **虞美人** | ★ 旧写法「玫瑰」——**本轮已统一**（1.14 之前官方叫玫瑰，代码注释里也早注明过） |
| `HAY_BLOCK` | **干草捆** | ★ 旧写法「干草块」——本轮已统一 |
| `DANDELION` / `DIRT` | 蒲公英 / 泥土 | 一致 |
| `KELP` / `TARGET` / `ECHO_SHARD` | 海带 / 标靶 / 回响碎片 | 一致 |
| `RED_DYE` / `REDSTONE_BLOCK` / `LAPIS_BLOCK` / `LAPIS_LAZULI` | 红色染料 / 红石块 / 青金石块 / 青金石 | 一致 |
| `OAK_LOG` / `DARK_OAK_LOG` | 橡木原木 / 深色橡木原木 | 一致 |
| `PAPER` / `STRING` | 纸 / 线 | 一致 |
| `WATER_BUCKET` / `ICE` / `PACKED_ICE` / `BLUE_ICE` | 水桶 / 冰 / 浮冰 / 蓝冰 | 一致 |
| `RED_BED` / `TNT` / `BONE_BLOCK` / `WHEAT` / `WHEAT_SEEDS` | 红色床 / TNT / 骨块 / 小麦 / 小麦种子 | 一致 |
| `MOSS_BLOCK` / `GUNPOWDER` | 苔藓块 / 火药 | 一致 |
| `REPEATER` | 红石中继器 | 一致（★ 就是"红石中继器"，**不是** `REDSTONE_TORCH`） |
| `SEA_LANTERN` / `LOOM` / `RED_CONCRETE` / `FLOWER_BANNER_PATTERN` | 海晶灯 / 织布机 / 红色混凝土 / 旗帜图案 | 一致 |
| `NETHER_QUARTZ_ORE` / `RED_NETHER_BRICKS` / `RED_STAINED_GLASS` / `BLUE_STAINED_GLASS` / `PURPLE_STAINED_GLASS_PANE` / `TINTED_GLASS` / `GLOWSTONE` / `ANCIENT_DEBRIS` | 下界石英矿石 / 红色下界砖块 / 红色染色玻璃 / 蓝色染色玻璃 / 紫色染色玻璃板 / 遮光玻璃 / 荧石 / 远古残骸 | 一致 |

★ 本表只覆盖**本项目用到过的**原版材质；以后要用新的，按上面那条路径查客户端语言文件即可
（**别凭记忆写**）。

# 未完成构想：实体弹幕（落方块 / 末影水晶 / 形状弹幕）

> **状态：暂缓**（2026-09-24 用户决定"先完善现有物品体系"）。
> 本文件把已有的**分析 + 实测读数**存下来，重启这个方向时不必从头再来。
> 相关实现：`/touhou danmaku probe|fall|clean|rule`（实测台，已在仓库里）；
> 结论摘要也在 skill `modules\11` §10。

---

## 1. 最初的问题与结论一览

| 问题 | 结论 | 证据 |
|---|---|---|
| 用**掉落的方块**当弹幕可行吗 | ✅ 可行 | 见 §3.1 |
| 用**末影水晶**当弹幕可行吗 | ⚠️ 可行但风险高 | 见 §4 |
| 给末影水晶套**无敌**能不能不炸 | ✅ 能（挡在 `hurt` 入口，不触发爆炸分支） | 见 §4 |
| 多个落方块**堆成形状**当大弹幕 | ✅ 可行 | 见 §3.2 |
| 方块之间会不会**互相挤压**把形状挤散 | ✅ **不会**（实测 `maxDrift=0`、`minGap=1.0`） | §3.2 |
| 落地**会不会生成方块** | ✅ **不会**（实测两次都是 `NO-BLOCK-GENERATED`） | §3.3 |
| 速度会不会衰减 | ⚠️ **会**（关重力也有阻力项，实测每 tick 约 ×0.85） | §3.4 |
| 能否**定死速度** | ✅ 能（每 tick 重写速度），代价见 §5 | 分析 |

---

## 2. 本版本可用/不可用的 API（`javap` 在 `paper-api-1.20.4.jar` 上核实）

**`FallingBlock` 全部可用开关**：
`setGravity` · `setVelocity`（继承自 `Entity`）· `setDropItem` · `setCancelDrop` ·
`setHurtEntities` · `setDamagePerBlock` · `setMaxDamage` · `shouldAutoExpire` · `getSourceLoc`。

**`EnderCrystal` 全部可用方法**：只有 `isShowingBottom` / `setShowingBottom` / `getBeamTarget` / `setBeamTarget`
（+ 继承自 `Entity` 的 `setInvulnerable` / `setGravity` 等）。

| 想要的开关 | 本版本有吗 |
|---|---|
| `Entity#setPushable(boolean)`（Paper 后来的"可被推动"） | ❌ **没有**（1.20.5+ 才有） |
| `setCollidable(boolean)` | ❌ 只在 `LivingEntity` 上（`FallingBlock` 不是） |
| 推挤事件 | 只有 `EntityPushedByEntityAttackEvent`（"被攻击击退"，不是实体间挤压） |
| `EntityRemoveEvent` | ✅ 有（带 `getCause()`，可当"意外被移除"的兜底） |
| `Display#setInterpolationDuration` / `setTeleportDuration` | ✅ 有（`Display` 基类上，`javap` 核实） |

⇒ ★ **"取消推挤"在本版本没有 API 可调**；好在实测也**不需要**它（见 §3.2）。

---

## 3. 落方块：实测读数

实测台：`/touhou danmaku <probe|fall|clean|rule> <x> <y> <z> [n]`，落方块一律设
`setGravity(false)` + `setCancelDrop(true)` + `setDropItem(false)` + `setHurtEntities(false)`
+ `shouldAutoExpire(false)` + `setPersistent(false)`。

### 3.1 无重力是真的（推翻了我一开始的猜测）

`danmaku fall 100 120 100 3`（给 **+0.6 向上**初速度）：9 个方块**一路升到 y≈148**，
32 次采样 y **单调上升**、`onGround` 始终 `false`。

⇒ ① **关重力后的落方块 = 一次设速、永不衰减的完美可编程弹丸**；
② 想让它真的落地，**必须自己给向下速度**。

### 3.2 形状不会散（"挤压"问题实测否掉）

`danmaku probe 100 120 100 3`（3×3=9 个、无重力悬停 300 tick、16 次采样）：
```
alive=9/9  maxDrift=0.0000  minGap=1.0000  onGround=false  tps=20.5    ← 16 次全部相同
verdict aliveAtEnd=9/9  ourBlocksPlaced=0  entitiesRemoved=9  [NO-BLOCK-GENERATED]
```
⇒ `maxDrift` 恒 0、`minGap` 恒 `1.0000` ⇒ **同速 + 整数格偏移 + 无重力 ⇒ 严丝合缝、一动不动**。

### 3.3 不生成方块、不掉物品、不砸伤

`probe`（悬停 15 秒后清场）与 `fall`（穿过地形后消失）两个模式的 verdict 都是
`ourBlocksPlaced=0` / `[NO-BLOCK-GENERATED]`。
⇒ `setCancelDrop(true)` + `setDropItem(false)` + `setHurtEntities(false)` 这套确实有效。

### 3.4 ★ 会衰减（我理论分析时没料到）

`fall` 模式观测到 y 递减量逐 tick 变小：`1.36 → 1.13 → 0.96 …`，即 **`v ← v × ≈0.85`**。
⇒ **关重力 ≠ 无阻力**；**轨迹规划不能假设"初速 = 全程速度"**。

### 3.5 还欠的实测（诚实登记）

1. **落地接触那一帧**没抓到：`fill` 造了 3×3 石台（`Successfully filled 25 block(s)`），
   但速度 −1.0 时两次采样之间**直接穿过平台**，降到 −0.35 又没在窗口内接触
   ⇒ 目前只有"消失后扫描 0 方块"这条**间接证据**（足够支持结论，但不如抓到 `onGround=true` 硬）。
2. **客户端观感**：插值下的抖动、方块间视觉滑动 —— 无头环境看不到，**必须真人看**。
3. **阻力因子是否为常数**、与速度是否相关 —— 只在一个速度点（−0.35 格/tick）测过。

---

## 4. 末影水晶：分析结论（未实测）

- **爆炸只在"被摧毁"时触发**，而 `Entity.hurt` 对 `invulnerable` 实体直接 `return false`
  ⇒ **`setInvulnerable(true)` 挡在受伤入口，根本不进入爆炸分支**（不是"挡爆炸"而是"不触发"）。
- 副作用：**伤害没进来 ⇒ `EntityDamageEvent` 也不发** ⇒ "在监听器里 return 掉伤害"这类代码**收不到事件**，写它等于没写。
- **挡不住的销毁路径**：`/kill`、其它插件 `remove()`、结构替换、区块/世界卸载
  ⇒ 弹幕生死**不能依赖"被打爆"**，要用自己的参数（存活秒数 / 命中 / 越界 / 所有者下线）；
  "异常消失"当**提前结束**处理（可用 `EntityRemoveEvent` 记一行原因）。
- **玩家会把它当靶子**：打不掉的飞行水晶会被反复攻击 ⇒ 建议取消 `PlayerInteractEntityEvent`，
  并且**不要给它 `setBeamTarget`**（那是"可锁定目标"的视觉暗示）。
- 固有麻烦：原版**浮动位移仍在**（要用每 tick `teleport` 压制）、**不随速度转头**、碰撞箱大而近似立方体。
- ★ **替代方案**：`ItemDisplay` / `BlockDisplay` **零物理、零爆炸**，且 `Display` 自带
  `setInterpolationDuration` / `setTeleportDuration`（平滑是原生参数）；项目里多方块投影已有 `ItemDisplay` 基建。

---

## 5. 速度定死：分析与待办

**机制**：`setVelocity` 是每 tick 的**绝对赋值**，而原版在之后跑一次 `v ← (v − 重力) × 阻力`。
⇒ **每 tick 都写 ⇒ 衰减不累积**，宏观匀速。

| 口径 | 做法 | 结果 |
|---|---|---|
| A 常量硬写 | 每 tick 写常数 `V` | 不衰减，但实际位移 = `V × 阻力`（有固定折扣） |
| B 解析补偿 | 每 tick 写 `目标速度(t) ÷ 阻力因子` | **精确匀速**（除法要自己算） |

**代价**：每 tick N 次调用（9 个方块可忽略，60~80 个要算）；位移↔速度换算要按 tps 与阻力因子；高速时要注意服务端-客户端偏差（判定用服务端位置即可，但观感可能错位）。

**替代路线**：① 每 tick `teleport` 解析解（绝对精确、但位置包多、可能抖）；
② 换 `Display`（无物理、平滑靠原生参数）；③ 直线弹幕每 K tick 补一次（需先测衰减曲线）。

**动手前该跑的实验（建议加进 `/touhou danmaku`）**：
固定速度点矩阵 `0.1 / 0.35 / 1.0 / 2.0 格/tick` × `写一次 vs 每 tick 写`，逐 tick 记录位移
⇒ 同时回答"阻力曲线是否常数""定死是否精确""慢速会不会被抹成 0"。

---

## 6. 轨迹编辑的手法（与实体无关，先记着）

把轨迹**参数化**、每 tick 求值驱动：
直线/匀速 `p0 + v·t` · 抛物线 `p0 + v·t + ½g·t²` · 螺旋/正弦（在前进方向的正交基上加 sin/cos 偏移）·
追踪（速度方向向目标做定量旋转）· 贝塞尔 `p(s)` · 环绕（圆周 + 递减半径）。
每条弹幕持有一个"参数随时间增长"的求值器；**换轨迹 = 换一个求值函数**，不是改运动代码。

---

## 7. 重启这个方向时的最短路径

1. 先补 §3.5 的三条实测（尤其"阻力曲线"矩阵）——它决定 §5 选 A 还是 B；
2. 选定驱动方式后，用 **`Display` 优先**（若外观可接受）或**落方块 + 每 tick 硬驱动**；
3. 命中链直接复用项目既有模式（每 tick 自算 AABB + 路径分段采样，见 `modules\11` §2）；
4. 清理钩子：`setPersistent(false)` + 插件禁用/玩家下线全清（**这是本项目已经吃过教训的一条**）。

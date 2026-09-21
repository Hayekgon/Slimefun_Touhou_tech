# TH Tech（Touhou 附属）

一个基于 [Slimefun4](https://github.com/Slimefun/Slimefun4) 的 Minecraft 附属插件，
以「东方 Project」为主题，加入多方块机器、自研能源体系与符卡道具。

- 目标平台：Paper 1.20.4+ / Java 21
- 依赖：Slimefun4

## 内容概览

| 模块 | 说明 |
|---|---|
| **旧地狱-灵乌路空反应堆** | 5×5×5 多方块产能机器，烧桶装原油产出电力与「逻辑奇点」 |
| **祭坛（赛钱箱）** | 9×6×9 多方块机器，6 根木桩投料 → 核心按配方产出，使用自研 POWER 能源 |
| **POWER 能源体系** | 自研电网：集成核心 / 中继器 / 存储单元 / 幻梦捕捉器（产能）/ 供给单元（无线充电） |
| **符卡道具** | 「梦想封印 集」与「杀意的百合」，射击与追踪弹幕 |
| **多方块投影** | 用 `ItemDisplay` 实体把整座结构以幻影形式投出来（照搬 LogiTech 的做法） |
| **粘液书自定义配方页** | 多方块核心的指南页从配方注册表**动态生成**，加配方即自动出现 |

## 构建

无 Maven，使用自带的 PowerShell 脚本：

```powershell
.\build.ps1
```

产物为 `Touhou-1.0.0.jar`。依赖 jar 放在 `mods\hakurei_gohei\_lib\`（需自行准备）。

## 配置要点

- `plugins/Touhou/config.yml` —— 控制台日志总开关、多方块结构层图、投影、消息档位
- `plugins/Slimefun/Items.yml` —— 各物品的 `ItemSetting` 数值（**优先于代码默认值**）

> ⚠ `Items.yml` 是 Slimefun 的活文件。**服务器运行时不要改写它** —— 实测在写入过程中被读取会导致
> `Cannot load ... Items.yml`（YAML 解析失败）。要改请先停服。

> ⚠ `config.yml` 的新段不会自动补进老配置文件（`saveDefaultConfig()` 只在文件不存在时生成）。
> 升级后如果发现某个新配置项"改了没反应"，先确认那个段在你本地的 `config.yml` 里是否存在。

## 许可

本项目采用**分许可**，详见 [`声明-完整版.txt`](声明-完整版.txt)：

| 部分 | 许可 |
|---|---|
| 原创代码与内容 | **GPL**（全文见 [`LICENSE`](LICENSE)） |
| 「多方块结构-祭坛」（改编自 酒石酸菌《车万女仆模组》） | **CC BY-NC-SA 4.0** |

本插件是基于「东方 Project」的二次创作（同人）作品，与上海爱丽丝幻乐团及东方 Project 官方
无任何隶属、赞助、认可或授权关系。免费发布，不以营利为目的。

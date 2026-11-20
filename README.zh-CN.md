# VisualVein: GTCEu（可视化探矿）

[English](README.md)

一个面向 **Minecraft 1.20.1 / Forge** 的 **GregTech CEu Modern（GTCEu）** 矿脉辅助模组。
它把 GTCEu 世界生成中的矿脉信息记录下来，并显示在 **JourneyMap（旅行地图）** 的全屏地图和小地图上。

> **Beta b0.02** —— 公开测试版，功能和数据格式仍可能变化。

- **模组 ID**：`visualprospecting`（为了兼容旧存档和配置，ID 保持不变）
- **版本**：`b0.02` Beta
- **前置**：Forge 47.x、GTCEu Modern 1.20.1（1.4.4 及以上）、JourneyMap 1.20.1（仅客户端需要）
- **JourneyMap 版本**：支持 5.x（API v1，例如 5.10.3）和 6.x（API v2，例如 6.0.7）。
  JourneyMap 6.x 还需要 **Common Networking**（`common-networking-forge-1.20.1`）。
- **安装位置**：客户端与服务端都需要安装本模组；JourneyMap 只需装在客户端。

## 功能

### 自动发现，手动标记

GTCEu 的矿脉按原点网格生成，同一个原点可能同时有石头层和深板岩层矿脉。模组在区块加载时向
GTCEu 的 `OreGenerator` 查询元数据并建立服务端数据库，但**不会自动把矿脉画到地图上**。

默认 `showOnlyMarked=true`，只有玩家手动标记过的矿脉才会显示。标记方式：

- **破坏/右键 GTCEu 地表碎矿（Surface Rock）**：按碎矿材料寻找最近的对应矿脉并标记；
- **破坏完整 GTCEu 矿石块**：归因到矿脉并标记，同时累计近似开采数；
- **手持 GTCEu 探矿仪（`gtceu:prospector.lv/hv/luv`）右键**：一键扫描周围矿脉并全部标记。

每次新增地图标记时，聊天框会提示“已标记矿脉：…（x, z）”。探矿仪会提示一次标记数量。
同一条矿脉只标记一次，重复操作不会刷屏。

### JourneyMap 地图图层

- 已标记矿脉以彩色 16×16 方块显示，颜色取自主要矿石材料颜色。
- **按层选择**：地表/白天地图显示石头层（或下界岩/末地石层），地下地图根据观察 Y 选择最接近的层，
  避免深板岩矿脉盖住地表矿脉。
- **不再绘制地表碎矿的额外标记圈**；地表碎矿材料只保留在悬停详情中。
- **默认不显示常驻地图文字**，避免文字锚点造成的向下偏移和互相重叠；需要时可在 VP 面板中打开
  “地图文字”，此时文字向上偏移。
- 悬停显示矿脉名、层级、中心、Y、尺寸、密度、权重百分比、同原点其他层、地表矿、已开采近似值、
  标记来源和枯竭状态。
- 地图右侧 **VP** 按钮或 `V` 键打开控制面板，可开关矿脉图层、地图文字、小地图显示、流体矿脉。
- **右键已标记矿脉**可切换“已枯竭/未枯竭”，状态保存并同步。
- **双击已标记矿脉或流体矿脉**可创建/移除 JourneyMap 路径点。
- 鼠标悬停矿脉时按 **Delete** 可直接切换枯竭状态，无需打开右键菜单。

### 流体矿脉优先级

- 读取 GTCEu 的 `BedrockFluidVeinSavedData`，显示 8×8 区块流体区域。
- **同一区域已经有矿物矿脉标记时，优先显示矿物矿脉，隐藏该流体方块和它的悬停框**，避免两个
  大框和两段文字叠在一起。
- 没有矿物矿脉的区域仍单独显示流体矿脉；流体名称只放在悬停详情中，不再画常驻文字。
- 流体图层可单独开关。

### 开采统计

玩家破坏 GTCEu 矿石时，模组按坐标和材料归因到矿脉并累加“已开采”近似值。深层变种按材料 ID
归并到同一条矿脉。这个数字不是精确剩余矿石数。

## 命令

| 命令 | 说明 |
|---|---|
| `/vp` | 查看帮助 |
| `/vp list [页码]` | 按距离列出当前维度矿脉（按原点合并） |
| `/vp nearest` | 显示最近矿脉详情 |
| `/vp scan [半径]` | 扫描周围矿脉网格并标记 |
| `/vp stats` | 显示已记录矿脉数量 |
| `/vp clear` | 清空当前维度记录（OP） |
| `/vp clear all` | 清空所有维度记录（OP） |
| `/vp reload` | 重新扫描玩家周围已加载区块（OP） |
| `/vp debug` | 输出扫描和流体矿脉诊断（OP） |
| `/vpwaypoint` | 为最近矿脉创建 JourneyMap 路径点 |

## 按键

| 默认按键 | 功能 |
|---|---|
| `V` | 打开 VP 控制面板 |
| `B` | 在最近矿脉创建路径点 |
| `Delete` | 切换鼠标悬停矿脉的枯竭状态 |

## 安装

1. 安装 Minecraft 1.20.1 + Forge 47.x。
2. 把 `gtceu-1.20.1-*.jar`、`journeymap-1.20.1-*.jar` 放进 `mods`；
   JourneyMap 6.x 还需 `common-networking-forge-1.20.1-*.jar`。
3. 把 `visualprospecting-b0.02.jar` 放进 **客户端和服务端** 的 `mods`。
4. 启动游戏，进入世界后手动标记矿脉；打开 JourneyMap 查看。

> 旧存档无需清空：自动扫描会补齐数据，未标记矿脉默认隐藏。

## 构建

ForgeGradle 6 + Gradle 8.1.1 + JDK 17：

```bash
set JAVA_HOME=C:\Program Files\Java\jdk-17
gradlew.bat build
```

产物在 `build/libs/visualprospecting-b0.02.jar`。`libs/` 中的 GTCEu/JourneyMap jar 仅用于编译，
已被 `.gitignore` 排除，不会提交到仓库。克隆后需要自行放入对应版本的编译依赖。

## 实现说明

- `VeinScanner`：区块加载时查询 `OreGenerator#generateMetadata`，按“原点区块 + 层级”保存；
  自动扫描不标记，交互处理器负责手动标记。
- `VeinGroup`：同一原点的多层矿脉合并显示；地表选石头层，地下按观察 Y 选择。
- `VeinWorldData`：`SavedData` 持久化，保存标记、枯竭状态、地表碎矿材料和坐标。
- `VeinInteractionHandler`：处理地表碎矿、完整矿石块、探矿仪三类标记，并发送聊天提示。
- `VPJourneyMapPluginV2`：JourneyMap 6.x 图层、右键枯竭菜单、VP 菜单；`VPJourneyMapPlugin`
  保留给 JourneyMap 5.x 基础图层。
- `VPFluidOverlayV2`：流体矿脉图层；若附近已有已标记矿物矿脉则让位给矿物图层。

## 已知限制

- 权重百分比不是精确方块数，“已开采”也是近似值。
- 如果整合包用 KubeJS 运行时改矿脉定义，已记录数据需 `/vp clear` 后重新扫描。
- 地表碎矿自动归属只在矿脉中心周围 8 格内进行；距离很远时仍可破坏/右键该碎矿手动匹配。
- JourneyMap 5.x 仅保留基础图层；分层、右键状态、流体优先级和 VP 面板以 6.x 为主要目标。

## 许可

MIT。`libs/` 中的 GTCEu 与 JourneyMap jar 仅用于本地编译，版权归各自作者所有。

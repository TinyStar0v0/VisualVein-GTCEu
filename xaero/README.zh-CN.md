![](https://github.com/TinyStar0v0/VisualVein-GTCEu/blob/main/639307842200860044.png)
# VisualVein: GTCEu（Xaero 版）

[English](README.md)<br>
[![CodeFactor](https://www.codefactor.io/repository/github/tinystar0v0/visualvein-gtceu/badge)](https://www.codefactor.io/repository/github/tinystar0v0/visualvein-gtceu)<br>

Minecraft **1.20.1 / Forge** 的 **GregTech CEu Modern** 辅助模组。记录 GTCEu 矿脉，并把玩家**手动发现**的矿脉显示在 **Xaero's World Map** 和 **Xaero's Minimap** 上。

本分支是**不依赖 JourneyMap** 的 Xaero 专用版本。

- **模组 ID**：`visualvein`
- **版本**：`1.0.0-xaero`
- **协议**：LGPL-3.0-only
- **依赖**：Forge 47.x、GTCEu 1.20.1（1.4.4+）、Xaero's World Map 1.47.0+、Xaero's Minimap 26.6.0+
- **安装**：客户端和服务端都需要本模组；Xaero 两件套仅客户端需要

## 功能

- 只有手动标记的矿脉才会显示：地表岩石、矿石方块、GTCEu 探矿器；流体通过探矿器 FLUID 模式或空手点基岩发现。
- 矿石标记使用 JM 版合成图标：宿主岩 + GTCEu 矿石 + 材质染色 + 枯竭红叉。
- 右侧栏使用 Xaero 原生图标按钮：矿石、流体、隐藏、纹理/颜色、搜索。
- 流体矿脉按 **16×16 区块整块染色**，区块之间有黄色分隔线；悬停显示单区块产量、区域产量、剩余作业数；可采集次数为 0 时不显示。
- Xaero 原生右键菜单：创建路径点、安全传送、标记枯竭、标记未枯竭。
- 安全传送由服务端寻找两格空气、下方为固体且非流体的安全落点。
- 搜索支持中文、全拼（`tongkuang`）和首字母（`tk`），在地图和小地图同步高亮/变暗。
- 内置英文 `en_us` 和简体中文 `zh_cn`。

## 构建

```bash
./gradlew build
```

产物：`build/libs/visualvein-xaero-1.0.0-xaero.jar`

Xaero 两件套和 GTCEu 不随 jar 分发；GTCEu 纹理在运行时从已安装的模组读取。

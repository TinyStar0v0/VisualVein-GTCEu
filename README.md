# VisualVein: GTCEu

[简体中文](README.zh-CN.md)
[![CodeFactor](https://www.codefactor.io/repository/github/tinystar0v0/visualvein-gtceu/badge)](https://www.codefactor.io/repository/github/tinystar0v0/visualvein-gtceu)

A Minecraft **1.20.1 / Forge** addon for **GregTech CEu Modern (GTCEu)** that records GTCEu ore veins and
renders them on **JourneyMap**. Inspired by the classic 1.7.10 VisualProspecting.

> **v1.00-hotfix-02** — VisualVein is now licensed under **LGPL-3.0-only**. GTCEu and Minecraft textures
> are loaded from the installed mods at runtime and are no longer bundled. Functionally this keeps the
> v1.00 feature set, including the red cross on depleted texture markers.

- **Mod ID**: `visualvein`
- **Version**: `1.0.2` (release name 1.00-hotfix-02)
- **License**: LGPL-3.0-only
- **Status**: Stable hotfix
- **Requirements**: Forge 47.x, GTCEu Modern 1.20.1 (1.4.4+), JourneyMap 1.20.1 (client only)
- **JourneyMap support**: 5.x (API v1, e.g. 5.10.3) and 6.x (API v2, e.g. 6.0.7).
  JourneyMap 6.x also requires **Common Networking** (`common-networking-forge-1.20.1`).
- **Install side**: the mod is required on **both client and server**; JourneyMap is client-only.

## Features

### Automatic discovery, manual map markers

GTCEu generates ore veins on a chunk grid, with independent stone/deepslate/netherrack/endstone layers
at the same origin. The mod queries GTCEu's `OreGenerator` while chunks load and keeps a server-side
database, but **does not put veins on the map automatically**.

With the default `showOnlyMarked=true`, only manually marked veins are rendered. A vein is marked by:

- **Breaking or right-clicking a GTCEu surface rock** — matches the nearest vein by material;
- **Breaking a complete GTCEu ore block** — attributes it to its vein and increments the mined counter;
- **Right-clicking with a GTCEu prospector** (`gtceu:prospector.lv/hv/luv`) — scans the surrounding
  vein grid and marks every vein in range in one action.

Every newly added map marker sends a chat message such as `Marked vein: ... (x, z)`.
The prospector sends one summary message. A vein is marked only once, so repeated actions stay quiet.

### JourneyMap layer

- Marked veins use pre-packed GT ore textures rendered as ImageOverlays, composited over a host-rock
  base: stone/deepslate in the Overworld, netherrack in the Nether, end stone in the End, and stone on
  modded planets/dimensions. The overlay dimension is set explicitly (required by the JourneyMap API).
  The VP menu can switch between **Texture** and **Color** marker styles.
- **Layer-aware rendering**: surface/day map shows the stone layer (or netherrack/endstone); the
  underground map picks the layer closest to the observed Y. This prevents a deepslate vein from
  covering the stone vein at the same origin.
- **No extra surface-rock marker circles**; surface materials are shown in the hover tooltip only.
- **Permanent map labels are off by default** to avoid label-anchor offsets and overlapping text.
  They can be enabled in the VP panel; when enabled they are shifted upward.
- Hover tooltip shows vein name, layer, center, Y, size, density, ore weight percentages, other
  layers at the same origin, surface ores, approximate mined count, mark source, and depletion state.
- The map toolbar has three mode buttons on the right: **Ore Veins**, **Fluid Veins** and
  **Hide Overlays**. Only one mode is active at a time; Hide Overlays clears both.
  `V`/the VP button opens a small settings panel for labels, minimap and scan.
- Newly marked veins appear on the **minimap immediately**; you do not need to open the fullscreen map
  first (minimap rendering can be disabled in the settings panel).
- **Right-click a marked vein** to toggle it between depleted and active; the state is saved and synced.
- **Double-click a marked vein or fluid field** to create/remove a JourneyMap waypoint.
- Hover a vein and press **Delete** to toggle its depleted state without opening the popup.
- The VP panel no longer lists nearby veins; use `B`, double-click or `/vpwaypoint` instead.
- Created overlays are cached across map close/reopen, so the layer appears much faster on reopen.

### Fluid vein discovery

- Fluid veins are **not** read or synced automatically. A field is only revealed after discovery:
  - Right-click with a GTCEu prospector in **FLUID mode** (`gtceu:prospector.hv/luv`), or
  - Right-click bedrock with an **empty hand** to reveal the field under that bedrock.
- At close fullscreen zoom a discovered field is rendered as a full **8×8 chunk grid**, each chunk
  showing its own yield. At far zoom and on the minimap it collapses to one region polygon for FPS.
  Yield text is smaller, appears only at close zoom, and is **fullscreen-map only**.
- Discovered regions are persisted per dimension and synced to the player.
- The **Fluid Veins** map button switches the layer on and turns the ore layer off; the **Ore Veins**
  button does the reverse; **Hide Overlays** turns both off. There is no stacking/priority now.

### Mining statistics

Breaking a GTCEu ore block increments an approximate mined counter on the matching vein. Deepslate and
other variants are merged by material ID. This is a rough activity indicator, not an exact remaining
ore count.

## Commands

| Command | Description |
|---|---|
| `/vp` | Show help |
| `/vp list [page]` | List veins in the current dimension, grouped by origin |
| `/vp nearest` | Show the nearest vein |
| `/vp scan [radius]` | Scan the surrounding vein grid and mark it |
| `/vp stats` | Show recorded vein counts |
| `/vp clear` | Clear the current dimension (OP) |
| `/vp clear all` | Clear all dimensions (OP) |
| `/vp reload` | Rescan loaded chunks around players (OP) |
| `/vp debug` | Print scanner and fluid-vein diagnostics (OP) |
| `/vpwaypoint` | Create a JourneyMap waypoint at the nearest vein |

## Key bindings

| Default | Action |
|---|---|
| `V` | Open the VP control panel |
| `B` | Create a waypoint at the nearest vein |
| `Delete` | Toggle depleted status of the hovered vein |

## Installation

1. Install Minecraft 1.20.1 + Forge 47.x.
2. Put `gtceu-1.20.1-*.jar` and `journeymap-1.20.1-*.jar` into `mods/`.
   For JourneyMap 6.x, also install `common-networking-forge-1.20.1-*.jar`.
3. Put `visualvein-1.0.2.jar` into **both client and server** `mods/`.
4. Launch the game, enter a world, mark veins manually, then open JourneyMap.

Old worlds do not need to be cleared: automatic scanning fills the database, and unmarked veins stay
hidden by default.

## Limitations

- Ore weight percentages are relative GTCEu weights, not exact block counts; `mined` is approximate.
- Runtime vein changes made by KubeJS require `/vp clear` and a rescan.
- Surface-rock auto-attribution only scans 8 blocks around the vein center; distant surface rocks can
  still be matched by breaking/right-clicking them.
- JourneyMap 5.x only keeps the basic layer; layer selection, right-click status, fluid priority and
  the VP panel are primarily tested with JourneyMap 6.x.

## License

VisualVein code is licensed under **LGPL-3.0-only**. See [LICENSE](LICENSE) for the full text and
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for third-party attribution.

VisualVein is an independent project, not a fork of VisualProspecting, GTCEu, or JourneyMap. GTCEu ore
textures and Minecraft host-rock textures are read from the installed game at runtime; they are not
bundled in the VisualVein jar. GTCEu and JourneyMap jars under `libs/` are compile-only.

## 许可证与分发要求（中文）

VisualVein: GTCEu 的源代码及本项目原创资源采用 **GNU Lesser General Public License v3.0 only（LGPL-3.0-only）** 授权，版权归 VisualVein contributors 所有。任何分发者，无论分发原版还是修改版，都必须：保留本项目的版权声明、SPDX 标识、完整 LGPL-3.0 许可证文本和 `THIRD_PARTY_NOTICES.md`，不得移除、修改或规避这些声明；按 LGPL-3.0 及所并入的 GPL-3.0 第 6 条，向接收者提供本模组对应的完整源代码，或提供符合该条规定的有效源代码获取方式；若分发修改版，必须清楚标注修改内容，并继续以 LGPL-3.0-only 授权该修改版，不得转为闭源或附加额外限制。LGPL 允许闭源整合包包含**未修改**的本模组，也允许仅通过公开 API 交互的独立闭源附属模组，但不允许将本模组本身或其修改版闭源分发。GTCEu、Minecraft、JourneyMap 的代码和资源不属于本项目的 LGPL 授权范围，仍受各自许可证约束。若违反上述条件，依据 LGPL-3.0 所并入的 GPL-3.0 第 8 条，您对本模组的使用和分发授权将自动终止，必须立即停止分发并删除相关副本；继续分发可能构成版权侵权。本项目按“现状”提供，不附带任何担保；本段为许可证摘要，法律效力以 [LICENSE](LICENSE) 中的完整 LGPL-3.0 文本为准。

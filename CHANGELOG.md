# Changelog

## 1.0.0 — first stable release (v1.00)

### Map layer

- Marked veins render as ore textures composited over a host-rock base:
  stone/deepslate in the Overworld, netherrack in the Nether, end stone in the End, and stone
  on modded planets/dimensions.
- Texture and flat-color marker styles can be switched in the VP panel.
- Ore and fluid layers are mutually exclusive modes: **Ore Veins**, **Fluid Veins**, **Hide Overlays**.
- Newly marked veins refresh the minimap immediately; the fullscreen map no longer has to be opened first.
- Overlays are cached across map close/reopen, and only rebuilt on dimension/data changes.
- Hover tooltips show vein layer, center, Y, size, density, ore weights, other layers, surface ores,
  mined count, mark source and depletion state.
- Right-click or `Delete` toggles depleted/active state; the state is persisted and synced.
- Double-click creates/removes a JourneyMap waypoint.

### Discovery

- Ore veins are recorded from GTCEu generation data; only manually marked veins are shown.
- Mark by breaking/right-clicking a surface rock, breaking an ore block, or using a GTCEu prospector.
- Fluid veins are never read automatically. Discover with a prospector in FLUID mode or by
  right-clicking bedrock with an empty hand.

### Fluid fields

- Discovered fields persist per dimension and sync to the player.
- Close fullscreen zoom shows a full 8×8 chunk grid with each chunk's yield.
- Far zoom and the minimap collapse to one region polygon for performance.
- Yield text is small, fullscreen-only, and appears only at close zoom.

### Fixes

- Depleted veins can be restored to active (the latest cached vein record is resolved on every click).
- Fluid overlay counts no longer cause severe FPS drops when zoomed out.
- Texture overlays set the dimension explicitly, so JourneyMap no longer drops them silently.
- Forge no longer rejects the version string; all releases use numeric versions.

### Requirements

- Minecraft 1.20.1, Forge 47.x
- GregTech CEu Modern 1.4.4+
- JourneyMap 1.20.1 5.9.0+ (6.x also needs Common Networking)
- Install on both client and server

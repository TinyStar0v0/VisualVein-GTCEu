# Changelog

## 1.1.2 — hotfix (1.1.0-hotfix-02)

- Right-clicking the fullscreen map no longer clears or unfocuses the search field; multiple matching
  veins can be marked while the search remains active.
- Closing the fullscreen map no longer clears the query; reopening restores it.
- Search highlighting/dimming now also applies to the minimap.
- Search box moved to the bottom-right to avoid JourneyMap's theme switcher in the bottom-left.
- Ore texture markers are brightened and get a light 1px border for readability over dark map tiles;
  the marker footprint is 8×8 blocks.
- Waypoints use a server-recorded surface Y (falling back to the client heightmap or nearest surface
  rock) so they no longer land inside the vein/ground.
- Full pinyin (`tongkuang`) and pinyin initials / 半拼 (`tk`) search are included.
- Internal Forge version: `1.1.2`; release name: `1.1.0-hotfix-02`.

## 1.1.1 — hotfix (1.1.0-hotfix-01)

- Chinese pinyin search for the fullscreen map search box: full pinyin (`tongkuang`, `huangtong`) and
  pinyin initials/半拼 (`tk`, `ht`) now match localized ore/vein names through ICU4J's Han-Latin
  transliterator.
- The search box moved to the bottom-right so it no longer competes with JourneyMap's theme selector
  in the bottom-left.
- Direct Chinese and English/material-ID matching remains.
- Search hint now mentions pinyin support.
- Internal Forge version: `1.1.1`; release name: `1.1.0-hotfix-01`.

## 1.1.0

- Added a fullscreen-map search box at the bottom-left, shown while the Ore or Fluid layer is active.
- Matches localized ore/fluid names and material IDs, so `铜`, `黄铜`, `copper`, etc. match every vein
  containing that material, not only the vein's primary name.
- Matching markers are highlighted; non-matching ore and fluid overlays are dimmed. Works in both
  texture and color marker modes.
- Clearing the search restores normal rendering. Closing the fullscreen map clears the search.
- Search never applies to the minimap.
- New JourneyMap waypoints are placed at the surface height of the vein column instead of the
  underground vein center (issue #1).

## 1.0.2 — license hotfix (1.00-hotfix-02)

- Project license changed from MIT to **LGPL-3.0-only**.
- Added the full LGPL-3.0 text (`LICENSE`) and `THIRD_PARTY_NOTICES.md`.
- `LICENSE` and `THIRD_PARTY_NOTICES.md` are included in `META-INF` inside the jar.
- GTCEu ore textures and Minecraft host-rock textures are now read at runtime from the installed
  game/mods; `textures/oresets` and `textures/bases` are no longer bundled.
- Includes the 1.0.1 depleted-cross and overlay-recreation fixes.
- Internal Forge version is `1.0.2`; release name is `1.00-hotfix-02`.

## 1.0.1 — hotfix (1.00-hotfix-01)

- Texture-mode depleted veins now draw a red cross over the ore marker on the map.
- The cross disappears immediately when the vein is restored to active.
- Internal Forge version is `1.0.1` (numeric); the release name is `1.00-hotfix-01`.

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

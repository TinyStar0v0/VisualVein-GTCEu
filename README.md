# VisualVein: GTCEu

Minecraft 1.20.1 / Forge addon for **GregTech CEu Modern** that records ore veins and displays manually
discovered veins on a map mod.<br>
<a href="https://www.curseforge.com/minecraft/mc-mods/visualvein"><img src="https://cf.way2muchnoise.eu/visualvein.svg?badge_style=for_the_badge" alt="CurseForge"></a>
<a href="https://modrinth.com/mod/visualvein"><img src="https://img.shields.io/modrinth/dt/VisualVein?logo=modrinth&label=&suffix=%20&style=for-the-badge&color=2d2d2d&labelColor=5ca424&logoColor=1c1c1c" alt="Modrinth"></a>
<br>

This repository contains two independent builds:

| Folder | Map mod | Status |
| --- | --- | --- |
| [`journeymap/`](journeymap/README.md) | JourneyMap 5.x / 6.x | JourneyMap edition |
| [`xaero/`](xaero/README.md) | Xaero's World Map + Minimap | Xaero edition, no JourneyMap dependency |

## Important

- The two builds use the same mod id (`visualvein`). **Do not install both at once.**
- Each folder is a standalone Gradle project. Run Gradle from inside the folder you want to build.
- Both editions require GTCEu Modern 1.20.1 on client and server.
- JourneyMap or Xaero is client-only.

## Releases

See the [Releases](https://github.com/TinyStar0v0/VisualVein-GTCEu/releases) page.

- JourneyMap edition: previous `v1.1.0-hotfix-03` line.
- Xaero edition: `v1.0.0-xaero`.

## License

VisualVein is licensed under **LGPL-3.0-only**. See [LICENSE](LICENSE).
Third-party notices are in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Xaero's mods are All Rights Reserved and are not bundled with this project. GTCEu and Minecraft
textures are loaded from the installed game at runtime.

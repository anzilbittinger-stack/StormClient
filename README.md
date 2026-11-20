# StormClient

Fabric client mod for Minecraft Java 26.2.

## Build
GitHub Actions builds the mod with Java 25 and Gradle 9.5.1. Open the **Actions** tab and download the `StormClient-Fabric-26.2` artifact after a successful run.

## Current features
- Right Shift opens the ClickGUI.
- Movement: Air Jump and Auto Sprint.
- Visuals: Fullbright and hold-C Zoom.
- Utility: Base Finder scans nearby loaded chunks for likely base activity. Use `#base`, `#base list`, and `#base clear` in chat. Saved coordinates currently last for the game session; visible waypoint rendering and disk persistence are not implemented yet.

## Multiplayer limits
A client mod cannot grant server-side Creative mode, force server-authoritative flight, duplicate server inventory items, negate server-authoritative damage, or teleport on a server that does not permit it. Base detection is heuristic and can only analyse chunks the client has actually received/loaded.

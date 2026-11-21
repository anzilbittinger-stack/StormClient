# StormClient

Fabric client utility mod for Minecraft Java 26.2.

## Build
GitHub Actions builds the mod with Java 25 and Gradle 9.5.1. Open the **Actions** tab and download the `StormClient-Fabric-26.2` artifact after a successful run.

## Controls
- **Right Shift** opens the ClickGUI.
- **C** is hold-to-zoom.

## Modules
- **Movement:** Air Jump, Auto Sprint, and Elytra Assist.
- **Visuals:** Fullbright and hold-C Zoom.
- **HUD:** FPS counter, coordinates, facing direction, held item, armor-piece count, and active-module list. Each HUD line can be toggled in the HUD category.
- **Utility:** Base Finder scans nearby loaded chunks for likely base activity. Use `#base`, `#base list`, and `#base clear` in chat. Saved markers currently last for the game session.
- **Dupe Lab:** read-only inventory and bundle diagnostics. It does not duplicate items or alter inventory slots.

## Public-server note
StormClient is focused on client-side information and convenience. It does not include KillAura, TriggerBot, reach cheats, through-wall player/container ESP, anti-cheat bypasses, packet spam, or server exploit automation. Movement helpers may still be disallowed by individual server rules; check the rules before enabling them.

A client mod cannot grant server-side Creative mode, force server-authoritative flight, duplicate server inventory items, negate server-authoritative damage, or teleport on a server that does not permit it. Base detection is heuristic and can only analyse chunks the client has actually received/loaded.

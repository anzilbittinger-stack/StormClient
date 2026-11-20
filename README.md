# StormClient

Fabric client mod for Minecraft Java 26.2.

## Build
GitHub Actions builds the mod with Java 25 and Gradle 9.5.1. Open the **Actions** tab and download the `StormClient-Fabric-26.2` artifact after a successful run.

## Current status
This commit is the buildable project foundation only. It does not yet contain the planned ClickGUI, ESP, X-Ray, movement modules, or Base Finder. Those will be added and tested incrementally; this repository will not label an untested scaffold as a finished client.

## Multiplayer limits
A client mod cannot grant server-side Creative mode, force flight, duplicate server inventory items, negate server-authoritative damage, or teleport on a server that does not permit it. Base detection can only analyse chunks the client has actually received/loaded and is heuristic rather than guaranteed.

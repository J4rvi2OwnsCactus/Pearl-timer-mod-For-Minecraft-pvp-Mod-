# Pearl Timer (Fabric, Minecraft 1.21.11)

Whenever a player throws an ender pearl, a 15 second countdown appears above their head (green, fading to red).

- **Client-only**: install it just on your own game. No server mod needed, and it works on servers.
- Requires Java 21, Minecraft 1.21.11, Fabric Loader 0.18.4+ and Fabric API.
- Only pearls thrown within your render distance are seen.
- The timer is drawn as a screen overlay at the player's head position, so it shows through walls and can sit slightly off while your FOV changes (sprinting, speed effects).

## Build the jar

```
gradle build
```

The jar is `build/libs/pearl-timer-1.0.0+1.21.11.jar` (not the `-sources` one). Put it, plus Fabric API, in your `mods` folder.

Or push this folder to GitHub: the workflow in `.github/workflows/build.yml` builds the jar and attaches it to the run as an artifact.

## Tweaks

- Timer length: `TIMER_TICKS` in `PearlTimerClient.java` (20 ticks = 1 second).
- Look: `PearlHud.java`.

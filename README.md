# Darkheart

A modern, feature-rich **Minecraft 1.8.9 Forge** client built on the OpenMyau codebase, rebuilt with a clean visual identity, an injectable native loader, and a custom injector.

![Minecraft 1.8.9](https://img.shields.io/badge/Minecraft-1.8.9-blue) ![Forge](https://img.shields.io/badge/Forge-11.15.1-orange)

## Features

- **Modern visuals** — rebuilt HUD, ClickGUI, and main menu with a unified dark glass design language
- **Liquid Glass ClickGUI** — translucent rounded panels with animated module entries
- **Injection-first architecture** — the client self-bootstraps via a native DLL loader (`myau_native.dll`) that can retransform loaded classes at runtime
- **Custom injector** — `MyauInjector.exe`, a WinForms GUI that attaches the DLL to any `javaw` process
- **Wide module library**:
  - Combat: `NewKillAura`, `SmartAttack`, `AutoBlock`, `BlockHit`, `Velocity`
  - Movement: `Scaffold`, `NewScaffold` (with Clutch keep-y), `Freecam`, `InventoryMove`, `AutoThrow` (egg/snowball hot-swap)
  - Player: `Clutch`, `NoSlow`, `KeepSprint`
  - Render: `HUD`, `TargetHUD`-style indicators, `CuteVisuals`, `SnowFog`
  - Misc: `Disabler`, `BackTrack`, `Blink`, `FriendManager`, `TargetManager`

## Getting started

### Requirements

- JDK 17 (runtime uses JDK 17 toolchain; game targets Java 8 bytecode)
- CMake + a C++ toolchain (VS2022) for the native loader
- .NET Framework 4.x `csc` for the injector

### Build

```bash
./gradlew assemble
```

Artifacts land in `build/libs/`:

| Artifact | Purpose |
| --- | --- |
| `Darkheart-1.0.0.jar` | The client (Mixin + inject bootstrap) |
| `myau_native.dll` | Native injection DLL (embeds the jar) |
| `myau_loader.exe` / `myau_loader_local.exe` | Standalone loaders |
| `MyauInjector.exe` | GUI injector (pick process → inject DLL) |

### Inject

1. Start Minecraft 1.8.9 (vanilla or any Forge-free launch)
2. Open `MyauInjector.exe`
3. Select the `javaw` process and the `myau_native.dll` path
4. Click **Inject**

The client bootstraps inside the game without being a conventional Forge mod install.

## Architecture

```
src/main/java/myau/
├── inject/        # NativeBridge + HookTransformer bootstrap (injection contract)
├── module/        # Module base + all modules (flat layout)
├── ui/            # ClickGUI, LiquidClickGUI, modern components
├── util/          # Render, rotation, color, font helpers
├── mixin/         # Mixin classes (targeted via mixins.darkheart.json)
├── management/    # Rotation, blink, lag, blockage managers
myau-natives/      # C++ DLL/loader sources (CMake)
myau-injector/     # C# WinForms injector source
```

## Disclaimer

This project is a personal/educational client. Use at your own risk on servers that permit such clients. Do not use it to harass or harm other players.

## Credits

- Built on the **OpenMyau** codebase (original author: TTHILLTT)
- Native injection technique inspired by the InjectMyau project
- UI patterns inspired by Rise and Leader-Lite client designs

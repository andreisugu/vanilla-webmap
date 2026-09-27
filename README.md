# VanillaWebMap

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21%2B%20%7C%2026.2%20%7C%2026.3-brightgreen.svg)](https://www.curseforge.com/minecraft/mc-mods/vanillawebmap)
[![Fabric Mod](https://img.shields.io/badge/Mod%20Loader-Fabric-blue.svg)](https://fabricmc.net/)
[![Build Status](https://github.com/andreisugu/vanilla-webmap/actions/workflows/release.yml/badge.svg)](https://github.com/andreisugu/vanilla-webmap/actions/workflows/release.yml)
[![CurseForge](https://img.shields.io/badge/CurseForge-VanillaWebMap-orange.svg)](https://www.curseforge.com/minecraft/mc-mods/vanillawebmap)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B%20%7C%2025-red.svg)](https://adoptium.net/)

[CurseForge](https://www.curseforge.com/minecraft/mc-mods/vanillawebmap) | [GitHub](https://github.com/andreisugu/vanilla-webmap) | [Issues](https://github.com/andreisugu/vanilla-webmap/issues) | [License](LICENSE)

A lightweight, zero-lag 2D web map for Fabric Minecraft servers with live player tracking.

## About

Unlike traditional web map plugins that generate thousands of PNG files on disk or freeze server ticks with heavy raytracing calculations, VanillaWebMap keeps things fast and simple. It captures block map palettes as chunks are naturally loaded and streams compact region tiles directly to an HTML5 canvas in the browser.

Because all map rendering happens on the player's browser GPU rather than the server CPU, server tick rates (TPS) remain stable even during rapid chunk generation with pregen mods like Chunky.

This mod is purely server-side. Players joining your server do not need to install anything on their client—they just open a web link in their browser.

## Features

* **Authentic Vanilla Look:** Renders terrain using vanilla Minecraft map colors and palettes.
* **Low Server Overhead:** Captures block palettes in memory during chunk load events and serves them straight from RAM.
* **Live Player Tracking:** Shows player positions in real time with direction markers, custom nametags, dimension indicators, and optional health bars.
* **Follow Player Mode:** Clicking a player in the sidebar automatically locks the camera to follow their movement across dimensions.
* **Full Multi-Dimension Support:** View the Overworld, The Nether (with cavern roof culling so lava lakes and fortresses are visible), and The End.
* **4 UI Themes:** Includes Parchment, Slate Dark, AMOLED Black, and Light modes.
* **Interactive 360° Rotation:** Rotate the map at any angle with `Ctrl + Drag` or right-click drag, complete with a live compass needle.
* **Chunk Grid Overlay:** Toggle an F3+G style chunk boundary overlay.
* **Coordinate Search:** Dedicated X and Z input boxes to quickly jump to specific coordinates.
* **Map Screenshots:** One-click PNG screenshot export directly from the browser interface.
* **Fully Server-Side:** No client mod required.

## Compatibility

| Minecraft | Loader | Status | Notes |
| :--- | :--- | :--- | :--- |
| 26.3 | Fabric | Supported | Tested on Fabric Loader 0.19.5+ |
| 26.2 | Fabric | Supported | Tested on Fabric Loader 0.19.3+ |
| 1.21.x | Fabric | Supported | Requires Fabric API |

## Installation

1. Download `vanilla-webmap-1.0.0.jar` from [CurseForge](https://www.curseforge.com/minecraft/mc-mods/vanillawebmap) or [GitHub Releases](https://github.com/andreisugu/vanilla-webmap/releases).
2. Drop the jar into your server's `mods/` directory.
3. Make sure Fabric Loader (0.16+) and Fabric API are installed.
4. Start your server. The web map will start automatically on port `8105`.

Access the map in your browser at `http://<server-ip>:8105/`.

## Commands & Permissions

Compatible with LuckPerms, Fabric Permissions API, and vanilla permission levels:

| Command | Permission Node | Default Level | Description |
| :--- | :--- | :--- | :--- |
| `/webmap status` | `vanillawebmap.command.status` | All Players (0) | Displays HTTP port, online player count, and cached tile stats. |
| `/webmap hide` | `vanillawebmap.command.hide` | All Players (0) | Hides yourself from the live web map. |
| `/webmap hide <player>` | `vanillawebmap.command.hide.others` | Operators (2) | Hides another player from the live web map. |
| `/webmap show` | `vanillawebmap.command.show` | All Players (0) | Unhides yourself on the live web map. |
| `/webmap show <player>` | `vanillawebmap.command.show.others` | Operators (2) | Unhides another player on the live web map. |
| `/webmap render [radius]` | `vanillawebmap.command.render` | Operators (2) | Force-scans and renders chunks around the player. |
| `/webmap reload` | `vanillawebmap.command.reload` | Operators (2) | Reloads `config/vanilla-webmap.json` without restarting the server. |
| `/webmap clear` | `vanillawebmap.command.clear` | Operators (2) | Clears in-memory and disk tile caches. |

> Master permission `vanillawebmap.admin` grants access to all VanillaWebMap commands.

## Configuration

The config file is generated at `config/vanilla-webmap.json`:

```json
{
  "version": 1,
  "serverTitle": "My Minecraft Server",
  "serverIp": "play.myserver.com",
  "headerLinkUrl": "https://myserver.com",
  "httpPort": 8105,
  "bindAddress": "0.0.0.0",
  "scanRadius": 8,
  "scanIntervalTicks": 40,
  "tileCacheDir": "./world_webmap_cache",
  "showPlayerHealth": true,
  "showCoordinates": true
}
```

* `serverTitle`: Title shown in the browser tab and top header.
* `serverIp`: IP address copied when clicking the server widget.
* `headerLinkUrl`: External link opened when clicking the server title.
* `httpPort`: Port for the built-in HTTP server (Default: `8105`).
* `bindAddress`: Network interface (`0.0.0.0` for all interfaces).
* `tileCacheDir`: Directory where `.vmap` tile cache files are saved.
* `showPlayerHealth`: Toggle player health indicators on the map.
* `showCoordinates`: Toggle player coordinates on the map.

## Reverse Proxy (Nginx)

To run the map under a domain with SSL:

```nginx
location / {
    proxy_pass http://127.0.0.1:8105;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

## Building from Source

### Prerequisites
* JDK 21 or higher (Java 25 recommended)

### Quick Build
```bash
# 1. Download dependencies (first time only)
chmod +x scripts/setup-deps.sh
./scripts/setup-deps.sh

# 2. Build mod jar
chmod +x build.sh
./build.sh
```

Compiled jar will be located at `vanilla-webmap-1.0.0.jar`.

## License

Licensed under the [Apache License, Version 2.0](LICENSE).

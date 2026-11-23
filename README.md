<div align="center">

<img src=".github/icon.png" width="96" height="96" alt="">

# ServerTransfer

A server selector for Minecraft that moves players with the transfer packet, no proxy needed

[![build](https://img.shields.io/github/actions/workflow/status/AxAce67/ServerTransfer/ci.yml?branch=main&label=build)](https://github.com/AxAce67/ServerTransfer/actions/workflows/ci.yml) [![release](https://img.shields.io/github/v/release/AxAce67/ServerTransfer?include_prereleases&label=release)](https://github.com/AxAce67/ServerTransfer/releases) [![Modrinth](https://img.shields.io/modrinth/dt/servertransfer?logo=modrinth&label=Modrinth&color=00AF5C)](https://modrinth.com/plugin/servertransfer) [![license](https://img.shields.io/github/license/AxAce67/ServerTransfer)](LICENSE)<br>
![Minecraft](https://img.shields.io/badge/Minecraft-1.20.5%E2%80%9326.3-62B47A) ![server](https://img.shields.io/badge/server-Paper%20%7C%20Purpur-1E88E5) ![Java](https://img.shields.io/badge/Java-21%2B-E76F00)

**English** | [日本語](README.ja.md)

</div>

Download: [Modrinth](https://modrinth.com/plugin/servertransfer) · [GitHub Releases](https://github.com/AxAce67/ServerTransfer/releases)

A server selector plugin for Minecraft servers (Paper / Purpur). Players pick a server from a menu and are moved there with Minecraft's
transfer packet (1.20.5+), with no proxy needed.

- Menu with each server's online status, player count and ping
- Countdown before the move, cancelled by moving or taking damage; cooldown between moves
- Refuses to send players to a server that does not answer
- `/st <server>` and `/st send <player|@a> <server>` for direct moves, e.g. from NPCs or command blocks
- Optional hotbar item that opens the menu
- Add, remove and arrange servers in game
- MiniMessage or `&` colour codes; English and Japanese, per player

## Requirements

- Paper 1.20.5 – 26.x, or a fork such as Purpur. Spigot and Folia are not supported.
- Java 21 or newer (whatever your Paper version needs)
- **Every target server needs `accepts-transfers=true` in `server.properties`**, otherwise the player is
  disconnected with an error. The server running this plugin does not need it.

Players arrive on the target server as a new connection, so their inventory and position there are that
server's own. The target can be any server that accepts transfers, including a proxy (Velocity
`accepts-transfers = true`).

## Getting started

1. Put the jar in `plugins/` and start the server.
2. Add a server, holding the item you want as its icon:
   `/st add lobby lobby.example.com` (or `host:port`)
3. Type `/st` to open the menu. Arrange it with `/st edit`, and change names and descriptions in
   `plugins/ServerTransfer/config.yml`, then `/st reload`.

Upgrading from 1.x: the old `config.yml` is converted automatically on first start and kept as
`config-1.x.yml`.

## Commands

`/st` is short for `/servertransfer`.

| Command | Permission | |
|---|---|---|
| `/st` | `servertransfer.use` | Open the menu |
| `/st <server>` | `servertransfer.use` | Move to a server |
| `/st list` | `servertransfer.use` | Servers with status; click a line to join |
| `/st send <player\|@a> <server>` | `servertransfer.admin` | Send players (no countdown) |
| `/st add <id> <address> [slot]` | `servertransfer.admin` | Add a server; the icon is the item in your hand |
| `/st remove <server>` | `servertransfer.admin` | Remove a server |
| `/st edit [rows]` | `servertransfer.admin` | Drag icons into place, close to save |
| `/st reload` | `servertransfer.admin` | Reload config and messages |

`servertransfer.use` is granted to everyone, `servertransfer.admin` to operators.
`servertransfer.bypass` (nobody by default) skips the countdown and cooldown.
A server with a `permission` is hidden from players without it.

## Configuration

```yaml
language: auto          # auto (each player's game language), en, ja

menu:
  title: "<dark_gray>Server Selector"
  rows: 1               # 1-6, grows when a slot does not fit
  filler: ""            # e.g. GRAY_STAINED_GLASS_PANE
  show-status: true
  show-players: true

status:
  refresh-seconds: 5
  timeout-ms: 1500

transfer:
  countdown-seconds: 3  # 0 = immediately
  cancel-on-move: true
  cancel-on-damage: true
  check-online: true
  cooldown-seconds: 3

join-item:
  enabled: false
  material: COMPASS
  slot: 8
  name: "<aqua><bold>Server Menu</bold> <gray>(Right click)"
  lore: []
  locked: true          # cannot be dropped or moved, kept on death

servers:
  lobby:
    address: "lobby.example.com"
    name: "<green>Lobby"
    icon: GRASS_BLOCK
    slot: 0
    lore:
      - "<gray>Players: {online}/{max}"
      - "<yellow>Click to join"
    permission: ""
```

Names and lore accept the placeholders `{online}`, `{max}` and `{ping}`.

Addresses are sent to the player as written, so the client looks up SRV records itself and proxies or
hosts that route by hostname keep working. For the status ping the plugin resolves SRV records the same
way the client does.

To change messages, create `plugins/ServerTransfer/lang/messages_en.properties` (or `messages_ja`) with
only the keys you want to change. The keys are in
[messages.properties](src/main/resources/lang/messages.properties).

## Building

```bash
./gradlew build                     # jar in build/libs, runs the unit tests
./gradlew runServer                 # test server
scripts/smoke-test.sh 1.20.5 26.3   # real servers per version
```

## License

[MIT](LICENSE)

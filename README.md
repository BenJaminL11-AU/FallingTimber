# FallingTimber

FallingTimber is a lightweight Paper 26.2 plugin that fells a whole natural
tree when a player breaks one log with an allowed axe. Logs fall in a controlled
top-down cascade, use normal drops, respect axe durability and protection
plugins, and are guarded by conservative structure detection.

## Highlights

- Natural-tree detection using connected logs, non-persistent leaves and rooted
  ground checks.
- Protection against flat log structures, oversized trees, concurrent chopping
  and optionally logs touching doors, beds, containers, signs or workstations.
- Persistent player `/timber toggle` and `/timber debug` preferences.
- Per-world blacklist or whitelist and configurable allowed axes.
- Axe durability pre-check, optional enchantment/name requirements, TPS guard,
  cooldown, rate limit and distance cancellation.
- Batched felling, optional batched leaf decay, sounds, particles and action-bar
  progress.
- Optional single- and 2x2-tree replanting.
- Player statistics, leaderboards and admin tree inspection.
- Asynchronous, cached GitHub update checking with admin-only join notices.
- Automatic configuration migration with timestamped backups.

## Requirements

- Paper 26.2
- Java 25 or newer as required by Paper 26.2

## Installation

1. Stop the server.
2. Copy `FallingTimber-1.2.0.jar` into the root of the `plugins` folder.
3. Remove any older FallingTimber JAR so only one version remains.
4. Start the server.
5. Edit `plugins/FallingTimber/config.yml` if desired.
6. Run `/timber reload` after configuration changes.

When upgrading, FallingTimber backs up an older `config.yml` under
`plugins/FallingTimber/config-backups` and adds the v1.2 settings. Persistent
preferences and statistics are stored in `player-data.yml`.

## Commands

| Command | Purpose |
| --- | --- |
| `/timber toggle` | Persistently enable or disable tree felling for yourself. |
| `/timber status` | Show your current FallingTimber state. |
| `/timber debug` | Toggle action-bar rejection explanations. |
| `/timber inspect` | Inspect the targeted log and explain tree detection. |
| `/timber stats [player]` | Show saved tree and log statistics. |
| `/timber top` | Show the top five tree fellers. |
| `/timber version` | Show installed/latest versions and the Releases link. |
| `/timber reload` | Reload configuration. |
| `/timber help` | Show command help. |

Aliases: `/fallingtimber`, `/timber`, `/ftimber`.

## Permissions

| Permission | Default | Purpose |
| --- | --- | --- |
| `fallingtimber.use` | Everyone | Fell detected trees. |
| `fallingtimber.command` | Everyone | Use FallingTimber commands. |
| `fallingtimber.reload` | Operators | Reload configuration. |
| `fallingtimber.inspect` | Operators | Inspect targeted trees. |
| `fallingtimber.stats.others` | Operators | View another player's statistics. |
| `fallingtimber.update-notify` | Operators | Receive update-available join notices. |

## Important configuration areas

- `worlds`: blacklist or whitelist specific worlds.
- `detection`: natural-tree and structure safeguards.
- `tools`: allowed axes, durability protection and optional item requirements.
- `safety`: minimum TPS, cooldown, rate limit and cancellation distance.
- `effects`: progress, sounds and particles.
- `replant`: delayed single/2x2 sapling replacement.
- `leaf-decay`: delayed, batched leaf removal.
- `statistics`: persistent player stats.
- `updates`: cached GitHub checks and administrator notifications.

The stricter nearby-building-block check is available but disabled by default,
as a naturally grown tree close to a house may legitimately touch a sign, door
or container.

## Build from source

Install JDK 25 and Gradle, then run:

```bash
gradle clean build
```

The output is `build/libs/FallingTimber-1.2.0.jar`.

Releases: https://github.com/BenJaminL11-AU/FallingTimber/releases

## Notes

- FallingTimber targets Paper and is not a Fabric, Forge or NeoForge mod.
- Nether wart blocks do not expose Minecraft's natural/player-placed leaf flag.
  Strict natural-leaf detection therefore leaves Nether fungi untouched.
- Trees of the same log type that physically touch may be detected together;
  the size, radius, shape and optional building-block checks limit the risk.

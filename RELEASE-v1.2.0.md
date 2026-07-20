## FallingTimber v1.2.0

FallingTimber v1.2.0 is a major quality-of-life, safety and customisation
update for Paper 26.2.

### Player improvements

- `/timber toggle` and `/timber debug` choices now persist across restarts.
- Added optional action-bar progress, sounds and particles while trees fall.
- Added `/timber stats` and `/timber top` with persistent tree statistics.
- Added an axe durability pre-check to avoid unexpectedly breaking tools.
- Added optional accelerated leaf decay and improved 2x2 tree replanting.
- Expanded support for 2x2 jungle trees, giant spruce and other large vanilla
  trees without silently chopping only part of an over-limit tree.

### Administrator improvements

- Added `/timber inspect` to explain why a targeted tree is accepted or
  rejected.
- Added per-world blacklist/whitelist controls.
- Added configurable allowed axes, required enchantment and custom axe name.
- Added TPS protection, cooldowns, per-minute limits and distance cancellation.
- Added optional detection for logs touching common building blocks.
- Added automatic configuration migration with timestamped backups.
- Update notifications are now administrator-only and appear only when an
  update is available by default.

### Safety improvements

- Rejects suspiciously wide or flat log formations.
- Prevents two players from felling the same tree simultaneously.
- Accelerated decay only removes unsupported natural leaves, protecting nearby
  trees and player-placed leaves.
- Continues to require natural leaves and rooted tree-growing ground by default.

### Requirements

- Paper 26.2
- Java 25

### Upgrade instructions

1. Stop the server.
2. Remove the older FallingTimber JAR from `plugins`.
3. Place `FallingTimber-1.2.0.jar` in `plugins`.
4. Start the server.

The previous configuration is backed up automatically under
`plugins/FallingTimber/config-backups`. New settings are then added to the
active configuration.

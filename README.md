# FallingTimber

FallingTimber is a small Paper 26.2 plugin: break one log with an axe and the
connected tree is felled in a quick top-down cascade. Logs drop normally, axe
durability and Unbreaking are respected, and leaves are left to decay using
Minecraft's normal rules.

## Included safeguards

- Requires a configurable number of logs and nearby leaves, so an ordinary
  wooden wall is not mistaken for a tree.
- Only follows the original log material by default.
- Refuses trees above the configured size/radius limits.
- Processes large trees over multiple ticks.
- Fires a `BlockBreakEvent` for every extra log so region/protection plugins
  can cancel individual breaks.
- Does not run in Creative by default.
- Hold Sneak to bypass the plugin and break one block normally.

## Requirements

- Paper 26.2
- Java 25 to run Paper 26.2

Paper 26.2 was still marked experimental when this version was produced. This
release was compiled against and load-tested on Paper 26.2 build 60 (beta). Keep
Paper updated and test the plugin on a copy of your world before production use.

## Install

1. Stop the Minecraft server.
2. Copy `FallingTimber-1.0.0.jar` into the server's `plugins` folder.
3. Start the server.
4. Edit `plugins/FallingTimber/config.yml` if desired.
5. Run `/fallingtimber reload`, or restart the server, after changing config.

## Commands

| Command | Purpose |
| --- | --- |
| `/timber toggle` | Toggle Timber for yourself for the current server session. |
| `/timber status` | Show whether Timber is enabled for you. |
| `/timber reload` | Reload configuration; requires `fallingtimber.reload`. |
| `/timber help` | Show command help. |

Aliases: `/fallingtimber`, `/timber`, `/ftimber`.

## Permissions

| Permission | Default | Purpose |
| --- | --- | --- |
| `fallingtimber.use` | Everyone | Fell whole trees. |
| `fallingtimber.command` | Everyone | Use player commands. |
| `fallingtimber.reload` | Operators | Reload configuration. |

## Notes

- Replanting is optional and disabled by default. It plants one sapling or
  mangrove propagule at the detected base; 2x2 trees therefore receive one
  sapling.
- Connected trees of the same log type can be treated as one tree if their logs
  touch. The maximum size and radius settings limit the impact.
- Player `/timber toggle` choices intentionally reset when the server restarts.

# Changelog

## 1.4.2

- Add support for Paper and Minecraft Java Edition 26.3.
- Compile against Paper 26.3 build 40 using Java 25.
- Support the new poplar tree automatically through Paper's log tag, natural
  leaf data and matching poplar sapling material.

## 1.4.1

- Restore the version panel on every player join by default.
- Show installed version, latest GitHub release, update status and the clickable
  Releases link even when the plugin is current.
- Add `updates.notify-permission-required` for optional administrator-only
  notifications.
- Retain `updates.only-when-update-available` as an optional quiet mode.
- Automatically migrate older configurations to the restored default behaviour.

## 1.4.0

- Add `/timber global on`, `/timber global off`, `/timber global toggle` and
  `/timber global status`.
- Add persistent personal `/timber leaves on|off|toggle|status` controls.
- Add global `/timber global leaves on|off|toggle|status` controls.
- Add in-game slow, normal, fast, very-fast and instant leaf-decay presets.
- Keep tree felling and fast leaf decay as independent controls.
- Persist global changes directly to `config.yml`.
- Add the operator-only `fallingtimber.global` permission.
- Safely cancel active tree-felling jobs when globally disabled.
- Keep `/timber toggle` as each player's persistent personal preference.
- Expand `/timber status` to show the global, personal and effective states of
  both tree felling and fast leaf decay.

## 1.3.0

- Enable accelerated leaf decay by default.
- Process leaf decay in configurable batches to avoid server lag spikes.
- Retry decay in multiple passes while Minecraft recalculates leaf support.
- Only remove non-persistent leaves at their maximum support distance.
- Preserve player-placed leaves and leaves supported by neighbouring trees.
- Avoid loading unloaded chunks solely to process leaf decay.
- Back up and migrate older configurations to configuration format 3.

## 1.2.0

- Persist player toggle and debug preferences across restarts.
- Add `/timber debug`, `/timber inspect`, `/timber stats` and `/timber top`.
- Add persistent per-player tree, log, largest-tree and favourite-tree stats.
- Add world blacklist/whitelist controls and configurable allowed axes.
- Add optional required enchantment and custom axe name.
- Add conservative axe durability pre-check.
- Add minimum-TPS protection, cooldown, per-minute limit and distance cancel.
- Prevent two players from processing the same tree simultaneously.
- Add suspicious flat-formation detection and optional building-block proximity
  detection.
- Expand default scan limits for 2x2 jungle trees, giant spruce and other large
  vanilla trees; over-limit trees are rejected rather than partially felled.
- Add configurable progress messages, sounds and particles.
- Add improved single/2x2 replanting and batched accelerated leaf decay.
- Limit join update notices to authorised administrators and, by default, only
  when a newer version exists.
- Add automatic configuration migration with timestamped backups.
- Add project website metadata and new permission nodes.

## 1.1.1

- Require naturally generated leaves and rooted tree-growing ground by default.
- Protect ordinary player-built wooden structures from whole-tree felling.

## 1.1.0

- Add asynchronous GitHub release checks, join version information and
  `/timber version`.

## 1.0.0

- Initial release.

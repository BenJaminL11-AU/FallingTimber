# Changelog

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

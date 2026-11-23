# Changelog

All notable changes to this project are documented here. Versions follow
[Semantic Versioning](https://semver.org/).

## Unreleased

## 2.0.0 - 2026-11-23

A rewrite. The 1.x config is converted automatically and kept as `config-1.x.yml`.

### Added

- `/st <server>` to move directly, `/st list` with status and ping, `/st send <player|@a> <server>`.
- `/st add` and `/st remove`; `/st edit` now only arranges the existing servers.
- Status ping with the current protocol, run in parallel and cached; ping shown in the list.
- Cooldown between transfers, cancel on damage, `servertransfer.bypass`.
- Menu filler item, `{online}`, `{max}` and `{ping}` placeholders, MiniMessage text.
- Messages per player language, overridable from the plugin folder.
- Support for Paper 1.20.5 through 26.x, tested on real servers in CI.

### Changed

- Players are transferred to the address as written. The client resolves SRV records itself, so proxies
  and hosts that route by hostname work.
- Config layout (`menu`, `status`, `transfer`, `join-item`; servers use `name` and `icon`).

### Fixed

- Items placed in the editor were lost, and menu icons could be taken out of the menu.
- Saving the editor dropped the lore and permission of servers.
- Menus were recognised by their title, so other inventories with the same title reacted.
- Clicks were matched by display name, so two servers with the same name went to the same place.
- A countdown kept running after the player left, and clicking twice started two countdowns.
- Any compass opened the menu, not only the join item; `join-item-name` was ignored.
- Config and permissions were read off the main thread; servers were pinged one after another.
- `/st setting abc` threw an exception.
- The Gradle wrapper was missing from the repository.

## 1.0

First release.

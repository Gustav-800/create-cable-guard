# Changelog

All notable changes to this project are documented in this file.

## [1.0.0] - 2026-10-24

### Added

- 首次发布 / Initial release.
- 蓝图打印时保留 createaddition 电线连接（`nodes` NBT）。
- 打印会话期间暂停 `WireGraph` 的悬空电线清理，避免蓝图炮按 10 tick/块打印时先落地的端点被误删。
- 旋转 / 镜像打印时，按 Create 的 `StructurePlaceSettings` 同步旋转电线相对坐标。
- 内置自测（`-Dcableguard.selftest=true`），覆盖数据保留、误剪复现、打印顺序与旋转/镜像。

### Compatibility

- Minecraft 1.21.1
- NeoForge 21.1.262
- Create 6.0.10
- Create: Crafts & Additions 1.7.2

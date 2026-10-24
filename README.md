# 机械动力：线缆保护 / Create: Cable Guard

[中文](#中文) | [English](#english)

一个 **Create: Crafts & Additions（机械动力：创想附加）** 的附属修复模组：让已连接的电线在 Create 蓝图保存/打印后保留下来，并且在**旋转、镜像**打印时跟着结构一起转向。

> 非官方附属模组，与 Create / Create: Crafts & Additions 的开发团队无关，不包含它们的任何代码。

| | |
|---|---|
| 中文名 | 机械动力：线缆保护 |
| English name | Create: Cable Guard |
| Mod ID | `cableguard` |
| 游戏版本 | Minecraft 1.21.1 |
| 加载器 | NeoForge 21.1.262 |
| 依赖 | Create 6.0.10、createaddition 1.7.2 |
| 许可证 | MIT |

---

## 中文

### 它解决什么问题

1. **蓝图打印丢线**
   createaddition 把每根线的两端存在连接器方块实体的 `nodes`（相对坐标）里，但 Create 6.x 打印时只搬运经过安全过滤的 NBT，连接器继承的默认 `writeSafe()` 不写 `nodes`，于是打印出来的连接器是空的。

2. **蓝图炮打印顺序导致误剪**
   createaddition 的 `WireGraph` 发现“区块已加载、目标位置却没有线节点”时，下一 tick 就判定电线失效并删除。蓝图炮默认每 10 tick 才放一块，先落地的端点会被误杀。

3. **旋转/镜像打印后电线错位**
   Create 会旋转方块位置和方块状态，但不会旋转方块实体里的任意 NBT；`nodes` 里的相对偏移不跟着转，打印后就指向错误位置。

### 原理

四个 Mixin，不改动 Create / createaddition 的任何文件：

| Mixin | 作用 |
|---|---|
| `BlockHelperMixin` | 打印取 NBT 时，对 createaddition 方块实体返回只含 `nodes` 的标签 |
| `WireGraphMixin` | 打印会话期间跳过 `pruneDanglingWires`，避免先落地的端点被剪 |
| `SchematicannonMixin` | 打印进度刷新会话，最后几块还在飞行时也不会提前恢复剪线 |
| `SchematicPrinterMixin` | `loadSchematic` 结束时按同一份 `StructurePlaceSettings` 旋转 `nodes[x,y,z]`（支持镜像） |

### 安装

1. 安装 Minecraft 1.21.1 + NeoForge 21.1.262；
2. 安装 Create 6.0.10 与 createaddition 1.7.2；
3. 把 `cableguard-1.0.0.jar` 放进 `mods/`。

- 单人：客户端安装即可（集成服务端会加载）。
- 多人：**服务端必须安装**，客户端可装可不装。

### 构建

需要 JDK 21（Gradle 会通过 wrapper 自动下载）：

```bash
./gradlew build
# Windows: .\gradlew.bat build
```

产物：`build/libs/cableguard-1.0.0.jar`。

### 测试

在复制自整合包的独立实例（62 个模组）中实机运行：

```
[CABLEGUARD] ROT_TEST -> PASS
[CABLEGUARD] rotated wire nodes of 2 block entities (rotation=CLOCKWISE_90, mirror=NONE)
[CABLEGUARD] ROT_HOOK expected=BlockPos{x=0, y=0, z=3} / BlockPos{x=0, y=0, z=-3} foundA=true foundB=true -> PASS
[CABLEGUARD] DATA_TEST ... -> PASS
[CABLEGUARD] CONTROL ... -> PASS
[CABLEGUARD] SURVIVE_IN_SESSION ... -> PASS
[CABLEGUARD] RECONNECT ... -> PASS
[CABLEGUARD] RESULT rotationMath=true rotationHook=true data=true controlPruned=true survive=true reconnect=true => ALL_PASS
```

- `ROT_TEST`：12 组旋转/镜像组合下，电线偏移变换与连接器方块 `facing` 的旋转结果一致；
- `ROT_HOOK`：真实调用 Create 的 `SchematicPrinter.loadSchematic` 载入 90° 旋转蓝图，打印器内部连接器 NBT 被正确改写；
- `CONTROL`：无打印会话时单端电线确实会被原逻辑剪掉（复现 bug）；
- `DATA_TEST` / `SURVIVE` / `RECONNECT`：覆盖数据保留与打印顺序。

### 已知限制

- 镜像打印的数学已覆盖，但未单独跑一整张镜像蓝图的完整打印，首次使用建议先用小结构确认。
- 打印中途暂停超过 10 分钟，先落地的端点仍可能被剪。
- 只接管 createaddition 的 `nodes` 字段，其它模组 NBT 行为保持 Create 原样。

### 许可证

MIT，见 [LICENSE](LICENSE)。

---

## English

**Create: Cable Guard** is an addon for **Create: Crafts & Additions** that keeps wired connections alive when a Create schematic is printed, including **rotated** and **mirrored** blueprints.

> Unofficial addon. Not affiliated with the Create or Create: Crafts & Additions teams. Contains no code from those mods.

### What it fixes

1. **Wires disappear after printing**
   createaddition stores both wire endpoints as relative offsets in each connector's block entity NBT (`nodes`). Create 6.x only copies NBT through its safe-NBT filter, and the connector's inherited `writeSafe()` never writes `nodes`, so printed connectors are blank.

2. **The cannon prunes wires that are still being printed**
   createaddition's `WireGraph` treats "chunk loaded, no wire node at target" as a removed wire and deletes it on the next tick. The cannon launches one block every 10 ticks, so the first endpoint is pruned before its partner lands.

3. **Rotated / mirrored blueprints misplace wires**
   Create rotates block positions and block states, but not arbitrary block entity NBT. The relative `nodes` offsets are left unrotated and end up pointing at the wrong place.

### How it works

Four Mixins, no changes to Create or createaddition files:

| Mixin | Purpose |
|---|---|
| `BlockHelperMixin` | Returns a tag containing `nodes` for createaddition block entities when Create collects print data |
| `WireGraphMixin` | Skips `pruneDanglingWires` while a print session is active |
| `SchematicannonMixin` | Refreshes the print session so blocks still in flight are not pruned |
| `SchematicPrinterMixin` | Rotates `nodes[x,y,z]` with the same `StructurePlaceSettings` (rotation + mirror) used for the blocks |

### Install

1. Minecraft 1.21.1 + NeoForge 21.1.262
2. Create 6.0.10 + Create: Crafts & Additions 1.7.2
3. Drop `cableguard-1.0.0.jar` into `mods/`

Singleplayer: client-side is enough. Multiplayer: **the server must have it**; clients may install it too.

### Build

Requires JDK 21:

```bash
./gradlew build
# Windows: .\gradlew.bat build
```

Output: `build/libs/cableguard-1.0.0.jar`.

### Known limitations

- Mirror printing is covered by the rotation math test, but a full mirrored print was not run end-to-end yet; test on a small structure first.
- If a cannon pauses mid-print for more than 10 minutes, earlier endpoints may still be pruned.
- Only createaddition's `nodes` field is handled; other mods keep Create's default NBT behaviour.

### License

MIT, see [LICENSE](LICENSE).

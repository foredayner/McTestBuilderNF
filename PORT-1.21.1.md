# Minecraft 1.21.1 NeoForge port (branch `neoforge-1.21.1`)

The same mod as the 1.20.1 Forge version on `main` (see [PORT-1.20.1.md](PORT-1.20.1.md)), for
**Minecraft 1.21.1 NeoForge** (built against NeoForge 21.1.250). Jar: release `mc1.21.1-neoforge-latest`.

Note: unlike 1.20.1, a 1.21.1 NeoForge jar does **not** run on MinecraftForge 1.21.1 (they are separate loaders).

## Install

NeoForge 21.1.x for Minecraft 1.21.1, Java 21. Put `AIBuildAssistantNF-<version>+mc1.21.1.jar` into `mods/`
on the server and every player's game. Groovy is bundled.

## How it was made

Loader code comes from the 1.20.1 Forge port (renamed to NeoForge's packages); the Minecraft side goes
back to the 1.21 ways that the 1.21.4 Fabric version already used.

| Area | 1.20.1 Forge (`main`) | 1.21.1 NeoForge (this branch) |
| --- | --- | --- |
| Toolchain | ModDevGradle legacyforge, Java 17, reobf + mixin refmap | ModDevGradle, Java 21, Mojang names at runtime (no refmap) |
| Metadata | `META-INF/mods.toml` | `META-INF/neoforge.mods.toml` (lists the mixin config) |
| Networking | `SimpleChannel`, `FriendlyByteBuf` messages | `CustomPacketPayload` + `StreamCodec` (as in 1.21.4), registered with `RegisterPayloadHandlersEvent` |
| Item data | NBT through `ItemData` | data components through the same `ItemData` helper |
| Gear stats | every behaviour handed to the vanilla item | attribute, tool and durability components copied (as 1.21.4); armour attributes through `getDefaultAttributeModifiers`; enchanting by item tags (`enchantable/*`) |
| Bows | Forge `customArrow` | vanilla `createProjectile` (as 1.21.4) |
| Item renderers | `initializeClient` on the item | `RegisterClientExtensionsEvent` |
| HUD | `RegisterGuiOverlaysEvent` | `RegisterGuiLayersEvent` |
| Screens | 1-axis scroll | 2-axis scroll, `renderBackground` override (as 1.21.4) |
| Game tests | own mock player with an in-memory channel | vanilla mock player (already has one) |
| Unit tests | plain JUnit with a tolerant bootstrap | NeoForge's JUnit loader (`unitTest`) |
| Data folders | `tags/items`, `structures` | `tags/item`, `structure` |

Unchanged from 1.21.4: the render-state-free mixins, item renderers (`builtin/entity` models) and the
worn-gear render layer from the 1.20.1 port, since 1.21.1 predates render states and special item models.

CI on this branch: build, unit tests, 15 server game tests, client smoke test (screenshots); logs go to
the `ci-logs-1.21.1` branch.

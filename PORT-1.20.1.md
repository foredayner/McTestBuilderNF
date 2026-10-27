# Minecraft 1.20.1 Forge / NeoForge port

This repository ports **AI Build Assistant** from Minecraft 1.21.4 Fabric
([foredayner/McTestBuilder](https://github.com/foredayner/McTestBuilder)) to **Minecraft 1.20.1 Forge**.
The 1.21.4 version is itself a port of [Minecraft-AI-Build-Assistant](https://github.com/Fyzugx-Lab/Minecraft-AI-Build-Assistant)
by Fyzugx-Lab (CC0 1.0). All credit for the mod goes to the original authors.

Features are the same as the 1.21.4 Fabric version (see [PORT-1.21.4.md](PORT-1.21.4.md)).
The scenario map builder is not part of this port.

## Install

1. Install **Forge 47.x** for Minecraft 1.20.1 (or **NeoForge 47.1.x** for 1.20.1).
2. Put `AIBuildAssistantNF-<version>.jar` (not the `-sources` one) into `mods/` on **both** the server
   and every player's game. Groovy (which runs the AI's build scripts) is bundled inside the jar.
3. The AI 공방 (O key) is for operators (permission level 2); in singleplayer, turn cheats on.

No other mods are needed (Fabric API is not used).

## Build

Java 17. `./gradlew build` makes `build/libs/AIBuildAssistantNF-<version>.jar`.

| Task | What it does |
| --- | --- |
| `./gradlew build` | compile, unit tests, jar |
| `./gradlew runClient` / `runServer` | play in a dev game |
| `./gradlew runGameTestServer` | 15 in-world server tests (build pipeline, undo, objects, gear, bows, permissions, two players) |
| `./gradlew runSmokeClient` | opens a flat world, equips AI gear, takes screenshots, quits (CI runs it under xvfb) |
| `./gradlew build runGameTestServer -Pplatform=neoforge` | the same against NeoForge 1.20.1 |

CI (GitHub Actions) runs all of these on every push, pushes logs and screenshots to the `ci-logs` /
`ci-logs-neoforge` branches, and publishes the jar to the `mc1.20.1-latest` release from `main`.

## What changed from 1.21.4 Fabric

| Area | 1.21.4 Fabric | 1.20.1 Forge |
| --- | --- | --- |
| Toolchain | Loom, Yarn names, Java 21 | ModDevGradle (legacyforge), Mojang names + Parchment, Java 17 |
| Mod id / package | `minecraft-ai-build-assistant`, `com.example` | `aibuildassistantnf` (Forge ids cannot have `-`), `io.github.foredayner.aibnf` |
| Entry points | `ModInitializer`, `ClientModInitializer` | `@Mod` class; client setup only on the client (`AiBuildAssistantClient.init`) |
| Registration | `Registry.register` at init | `RegisterEvent` (fields stay plain `Item` / `EntityType`) |
| Networking | `CustomPacketPayload` + `StreamCodec`, Fabric networking | `SimpleChannel`; every message writes/reads a `FriendlyByteBuf`; client handlers registered from client code only |
| Events | Fabric callbacks | Forge events: server tick, entity join, chat, commands, right-click block, client tick, key mappings, GUI overlay, level render stage |
| Item data | data components (custom data, name, lore, glint) | stack NBT (`ItemData` helper); blueprint glint = one empty enchantment entry |
| Gear stats | copied components (tool, attributes, enchantable, repairable, equippable) | `GearToolItem` hands each behaviour to its iron vanilla item (destroy speed, correct tool, tool actions, attributes, enchanting, repair); worn kinds are `GearWornItem` (`Equipable`) |
| Item looks | "special" item models (`items/*.json` + `SpecialModelRenderer`) | `builtin/entity` item models + Forge `IClientItemExtensions` custom renderer, display transforms copied from vanilla handheld / bow / generated |
| Worn gear | Fabric `ArmorRenderer` | a `RenderLayer` added to every humanoid renderer and player skin |
| Arm motions | mixins on render states (1.21.2+) | one mixin on `HumanoidModel.setupAttackAnimation`, which gets the entity in 1.20.1 |
| Culling of big gear | `EntityRenderer.getBoundingBoxForCulling` | `Entity.getBoundingBoxForCulling` |
| Bows | `createProjectile` | Forge `customArrow` hook (plain arrows become AI arrows) |
| Arrows | `ArrowRenderState` | classic `ArrowRenderer.render(entity, ...)` |
| Spark effects | `WorldRenderEvents.AFTER_ENTITIES` (camera turn in the model view) | `RenderLevelStageEvent.AFTER_ENTITIES` (camera turn in the pose stack) |
| Vertices | `addVertex().setColor()...` | `vertex().color()...endVertex()` (`Vtx` helper) |
| Screens | 2-axis scroll, background drawn by `Screen` | 1-axis `mouseScrolled`, no background call |
| Groovy | plain class path | bundled with Jar-in-Jar; scripts compile with the mod's class loader |
| Item tags | `tags/item/`, incl. `enchantable/*`, `head_armor`, `chest_armor` | `tags/items/` (swords, axes, ...); enchanting is decided in code |
| Hammer icon | mace | anvil (no mace in 1.20.1) |
| Server tests | Fabric game tests | Forge `@GameTestHolder`; test players get an in-memory network channel (Forge's join code needs one) |
| Client tests | Fabric client game tests (screenshots) | `runSmokeClient` (the mod drives the game itself when `-Daib.smoketest=true`) |

Unchanged: the AI providers and prompts, the Groovy sandbox and linter, build/object/gear pipelines,
blueprints and 보관함, undo, multiplayer rules, config files (`config/ai_builder/`) and world data
(`<world>/ai_builder/`).

## Known differences

- Worn gear does not lose durability when the wearer is hit (1.20.1 only damages real armour items).
- Saved items from the 1.21.4 version do not carry over (different mod id and item data format).

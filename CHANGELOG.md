# Changelog

All notable changes to this project are documented here.

## [1.0.2] - 2026-07-17

### Changed

- Block placement now uses vanilla `/setblock` and `/fill` as the approving player
- Building requires cheats / operator permission level 2 (no more Survival material bypass via `setBlockState`)
- Large shapes (`placeFloor`, `placeWall`, `placePillar`, `placeBox`, `clearArea`) prefer `/fill` for fewer commands

### Security

- Public Survival servers without OP/cheats can no longer place free blocks through this mod's placement path

## [1.0.1] - 2026-06-15

### Fixed

- Bundle Groovy 3.0.17 in the release JAR (`include implementation`) so scripts run outside the dev environment
- Groovy sandbox: resolve `SecureASTCustomizer` import whitelist/blacklist conflict
- Groovy sandbox: stop blocking `Object`-typed variables (fixes Accept → `ai.placeBlock(...)` scripts)
- Add `AiBuildScriptBase` so the `ai` receiver is typed correctly at compile time

### Changed

- Default `debugLogEnabled` is now `false` (prompts and generated code are not logged unless enabled)

## [1.0.0] - 2026-06-08

### Added

- Minecraft AI Build Assistant panel (O key) and settings screen
- Ollama, OpenAI, and Custom provider support
- OpenAI web search (Responses API) with manual Web:ON/OFF toggle
- Build approval flow (Accept / Cancel)
- Terrain scan (forward, ground height, obstacles) in AI prompts
- Groovy build API (placement, clearing, terrain inspection, block catalog)
- Groovy sandbox (`ScriptSecurityValidator`, `SafeGroovyShellFactory`, `SecureASTCustomizer`)
- Forbidden block management (icon picker UI + config file)
- Block placement queue (Build Speed slider)

### Fixed

- OpenAI API URL normalization (404 fix)
- Groovy numeric argument types (`Number` support)
- API calls without the `ai.` prefix
- Block name as first or last argument in placement methods
- `clearArea` 5-argument overload (single horizontal layer)

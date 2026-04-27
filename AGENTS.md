# AGENTS.md

## Scope
- This guide applies to `FSF_MilitaryCorporation` only (Starsector mod, gameVersion `0.98a-RC8`).
- Mod entrypoint is `data.scripts.FSFModPlugin` in `mod_info.json`.

## Big Picture Architecture
- Runtime boot: `src/data/scripts/FSFModPlugin.java` handles app load, new game generation, plugin wiring, and AI picks.
- Campaign layer: `src/data/scripts/campaign/FSFCampaignPlugin.java` is an `EveryFrameScript` that runs periodic campaign maintenance (NPCs, market conditions, memory keys, unlocks).
- Worldgen layer: `src/data/scripts/world/aEP_gen.java` delegates to system generators in `src/data/scripts/world/aEP_systems/` (notably `aEP_FSF_DWR43.kt`).
- Combat FX layer: `src/data/scripts/aEP_CombatEffectPlugin.kt` is the central lifecycle/render manager for custom effects (`aEP_BaseCombatEffect`).
- Utility/constants layer: `src/data/scripts/utils/aEP_Tool.kt` is a large shared helper file; `aEP_ID` constants are also defined there.

## Key Data Flows
- New game flow: `FSFModPlugin.onNewGame()` -> `aEP_gen.generate()` / `randomGenerate()` -> add `FSFCampaignPlugin` script.
- Campaign state uses memory keys heavily (`$aEP_*`) for progression and unlocks; do not rename keys without full audit.
- GraphicsLib integration loads texture/light CSVs on app load; keep data paths in sync with `data/config/lights/*.csv`.
- Localization switches by `data/config/settings.json` key `aEP_UseEnString`; text retrieval is centralized in `aEP_DataTool.txt(...)`.

## Build/Run/Debug Workflow (Observed)
- IntelliJ artifact `FSF_MilitaryCorporation:jar` outputs `jars/FSF_MilitaryCorporation.jar` (`.idea/artifacts/FSF_MilitaryCorporation_jar.xml`).
- Project run config launches `../../starsector-core/starsector.bat` (`.idea/runConfigurations/Run_SS_bat_.xml`).
- Remote debug config targets `localhost:5005` and depends on the artifact build (`.idea/runConfigurations/Remote_SS_Debugger.xml`).
- No automated test suite found; practical verification is mission/campaign smoke testing.

## Practical Validation Patterns
- Use mission sandbox `data/missions/aEP_test` + `src/data/missions/aEP_test/MissionDefinition.java` to quickly validate ship/weapon/combat changes.
- For campaign/world changes, start a new save and verify `aEP_FSF_*` entities/markets are spawned and hidden flags behave as expected.
- For effect-heavy hullmods (example: `src/data/scripts/hullmods/aEP_EmergencyReconstruct.kt`), watch per-frame work and cleanup (`removeListener`, unmodify stats, restore collision/alpha).

## Project-Specific Conventions
- ID naming is strict and cross-file: faction/entity/market/variant IDs are `aEP_*` and referenced in Java/Kotlin/JSON/rules.
- This repo is mixed Java + Kotlin; style is 2-space indent (`.idea/codeStyles/Project.xml`).
- Keep `aEP_` prefixes and existing bilingual content strategy; many strings and content files have CN/EN variants.
- `swap_CN_EN.py` is the official language swap tool and edits many files atomically; avoid ad-hoc manual CN/EN mass renames.
- `create_texture_csv.py` is used to regenerate shader texture CSV data; run it after normal/material/surface texture map changes.

## Integration Boundaries
- Hard dependencies in `mod_info.json`: LazyLib (`lw_lazylib`), MagicLib, GraphicsLib (`shaderLib`).
- Optional behavior gates: LunaLib (`lunalib`) and Nexerelin (`nexerelin`) checks in `FSFModPlugin`/`FSFCampaignPlugin`.
- External integrations present in data/code:
  - MagicBounty definitions in `data/config/modFiles/magicBounty_data.json`.
  - Nex custom start in `src/exerelin/campaign/customstart/aEP_EliteFrigateStart.kt`.
  - Luna settings in `data/config/LunaSettings.csv`.

## Agent Guardrails For This Repo
- Prefer additive, local edits; this codebase has dense cross-references between `src/` and `data/` IDs.
- When introducing new gameplay content, update both code hooks and data definitions in one change (variants/weapons/hulls/strings/markets as needed).
- Do not move or rename stable IDs like `aEP_FSF_SpaceFactory`, `aEP_FSF_Earth`, `aEP_FSF_Stationplanet` unless all references are updated.
- Preserve existing plugin registration paths in `mod_info.json` and `data/config/settings.json`.

## Existing AI Instruction Sources
- No existing `AGENTS.md`/`AGENT.md`/`CLAUDE.md`/copilot instruction file was found in this mod.
- `.github/agents/` exists but is currently empty.


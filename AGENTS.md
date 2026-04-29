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

## Starsector Core API Reference

### API Package Layout (`com.fs.starfarer.api`)
The game API is organized into these key packages under `starfarer.api.zip`:
- `com.fs.starfarer.api` — Root: `Global`, `BaseModPlugin`, `EveryFrameScript`, `ModPlugin`
- `com.fs.starfarer.api.campaign` — Campaign layer: `SectorAPI`, `StarSystemAPI`, `LocationAPI`, `CampaignFleetAPI`, `SectorEntityToken`, `FactionAPI`, `MarketAPI`, `PlanetAPI`, `CargoAPI`, `InteractionDialogPlugin`, `CampaignPlugin`, `BaseCampaignPlugin`, `CustomCampaignEntityPlugin`, `SectorGeneratorPlugin`, `JumpPointAPI`
- `com.fs.starfarer.api.campaign.ai` — Fleet AI: `CampaignFleetAIAPI`, `ModularFleetAIAPI`, `AbilityAIPlugin`, `AssignmentModulePlugin`, `NavigationModulePlugin`, `StrategicModulePlugin`, `TacticalModulePlugin`
- `com.fs.starfarer.api.campaign.econ` — Economy: `EconomyAPI`, `Industry`, `CommoditySpecAPI`, `SubmarketAPI`, `MarketConditionAPI`, `MarketConditionPlugin`
- `com.fs.starfarer.api.campaign.rules` — Rules/Memory: `MemoryAPI`, `RulesAPI`, `RuleAPI`, `CommandPlugin`, `HasMemory`
- `com.fs.starfarer.api.campaign.listeners` — Event listeners: `ListenerManagerAPI`, `FleetEventListener`, `ShowLootListener`, `DialogCreatorUI`
- `com.fs.starfarer.api.campaign.comm` — Intel/messages: `IntelManagerAPI`, `IntelInfoPlugin`, `CommMessageAPI`
- `com.fs.starfarer.api.combat` — Combat layer: `CombatEngineAPI`, `ShipAPI`, `WeaponAPI`, `MissileAPI`, `BeamAPI`, `DamagingProjectileAPI`, `CombatEntityAPI`, `ShieldAPI`, `ShipSystemAPI`, `MutableShipStatsAPI`, `HullModEffect`, `BaseHullMod`, `EveryFrameCombatPlugin`, `CombatEnginePlugin`, `ShipAIPlugin`, `MissileAIPlugin`, `AutofireAIPlugin`, `ShipVariantAPI`, `ShipHullSpecAPI`, `FleetMemberAPI`
- `com.fs.starfarer.api.characters` — Characters: `PersonAPI`, `MutableCharacterStatsAPI`, `AbilityPlugin`
- `com.fs.starfarer.api.fleet` — Fleet data: `FleetMemberAPI`, `FleetDataAPI`, `FleetMemberStatusAPI`, `FleetMemberType`
- `com.fs.starfarer.api.graphics` — Rendering: `SpriteAPI`
- `com.fs.starfarer.api.ui` — UI: `TooltipMakerAPI`, `CustomPanelAPI`, `CustomUIPanelPlugin`
- `com.fs.starfarer.api.plugins` — Plugin hooks: `ShipSystemStatsScript`
- `com.fs.starfarer.api.util` — Utilities: `Misc`, `WeightedRandomPicker`

### Critical Base Classes & Interfaces

#### `BaseModPlugin` (extend instead of `ModPlugin`)
Lifecycle hooks used by FSF:
- `onApplicationLoad()` — load mod data, register GraphicsLib CSVs, compile shaders
- `onNewGame()` — generate sector content (calls `aEP_gen.generate()`)
- `onNewGameAfterProcGen()` — post-procgen setup
- `onNewGameAfterEconomyLoad()` — markets are valid here
- `onNewGameAfterTimePass()` — after initial 2-month simulation
- `onGameLoad(boolean newGame)` — re-add transient plugins/scripts
- `beforeGameSave()` / `afterGameSave()` / `onGameSaveFailed()`
- `onDevModeF8Reload()` — dev mode script reload
- `pickShipAI()`, `pickWeaponAutofireAI()`, `pickDroneAI()`, `pickMissileAI()` — AI overrides
- `configureXStream(XStream x)` — custom serialization

#### `EveryFrameScript`
Campaign tick script interface. Used by `FSFCampaignPlugin`:
- `advance(float amount)` — called every frame; use `SectorAPI.getClock()` to convert to campaign days
- `isDone()` — return `true` to clean up
- `runWhilePaused()` — whether to tick while paused

#### `BaseCampaignPlugin` (extend instead of `CampaignPlugin`)
Key methods:
- `getId()` — unique ID for registration/unregistration
- `isTransient()` — `true` if data should NOT be saved (re-register in `onGameLoad`)
- `pickInteractionDialogPlugin()` — custom dialog for entities
- `pickBattleCreationPlugin()` — custom battlefields
- `updateEntityFacts()` / `updateFactionFacts()` / `updateGlobalFacts()` / `updatePersonFacts()` / `updatePlayerFacts()` / `updateMarketFacts()` — dynamic memory injection
- `pickFleetInflater()` / `pickAutofitPlugin()` — fleet generation overrides

#### `HullModEffect` / `BaseHullMod`
Hullmod lifecycle (note: one instance per application session, do NOT store campaign data in fields):
- `init(HullModSpecAPI spec)` — called once on load
- `applyEffectsBeforeShipCreation(HullSize, MutableShipStatsAPI, String id)` — stat modifications (affects campaign)
- `applyEffectsAfterShipCreation(ShipAPI ship, String id)` — visual/logic setup (combat only)
- `advanceInCombat(ShipAPI ship, float amount)` — per-frame combat logic
- `advanceInCampaign(FleetMemberAPI member, float amount)` — per-frame campaign logic
- `addPostDescriptionSection()` / `addSModEffectSection()` — tooltip customization
- `isApplicableToShip()` / `getUnapplicableReason()` — install restrictions
- `canBeAddedOrRemovedNow()` / `getCanNotBeInstalledNowReason()` — dock/spaceport requirements

#### `EveryFrameCombatPlugin` / `CombatEnginePlugin`
Combat scripting hooks:
- `init(CombatEngineAPI engine)` — deprecated but still usable with null checks
- `advance(float amount, List<InputEventAPI> events)` — main tick
- `processInputPreCoreControls()` — input handling
- `renderInWorldCoords(ViewportAPI viewport)` — world-space rendering
- `renderInUICoords(ViewportAPI viewport)` — UI-space rendering

#### `ShipSystemStatsScript`
For custom ship systems:
- `apply(MutableShipStatsAPI stats, String id, State state, float effectLevel)` — apply/remove stats by state
- `unapply(MutableShipStatsAPI stats, String id)` — cleanup
- `getStatusData()` — HUD status text
- Override methods: `getActiveOverride()`, `getInOverride()`, `getOutOverride()`, `getUsesOverride()`, `getRegenOverride()`, `getDisplayNameOverride()`

#### `CustomCampaignEntityPlugin`
For custom campaign entities:
- `init(SectorEntityToken entity, Object params)` — setup
- `advance(float amount)` — tick
- `render(CampaignEngineLayers layer, ViewportAPI viewport)` — layered rendering; respect `viewport.getAlphaMult()`
- `getRenderRange()` — culling distance
- `hasCustomMapTooltip()` / `createMapTooltip()` / `appendToCampaignTooltip()` — tooltip hooks

#### `SectorGeneratorPlugin`
World generation:
- `generate(SectorAPI sector)` — create star systems, planets, markets, fleets

### Global Access Patterns
- `Global.getSettings()` — `SettingsAPI`: load JSON/CSV, get sprites, mod manager
- `Global.getSector()` — `SectorAPI`: persistent data, scripts, plugins, factions, markets
- `Global.getFactory()` — `FactoryAPI`: create fleets, persons, cargo
- `Global.getCombatEngine()` — `CombatEngineAPI`: ships, projectiles, particles, damage (combat only)
- `Global.getLogger(Class c)` — Apache log4j logger

### Memory & Rules System
- `MemoryAPI` — key/value store with expiration: `set(key, value)`, `set(key, value, expireDays)`, `getString()`, `getFloat()`, `getBoolean()`, `getEntity()`, `getFleet()`, `expire()`, `unset()`
- `SectorEntityToken.getMemory()` / `FactionAPI.getMemory()` / `MarketAPI.getMemory()` — entity-attached memory
- `SectorAPI.getMemory()` — global memory
- `SectorAPI.getPersistentData()` — survives save/load; safe for mod data
- `RulesAPI` — drives `rules.csv` dialog logic; `performTokenReplacement()` for string substitution

### Important ID Constants (from `com.fs.starfarer.api.impl.campaign.ids`)
- `Tags` — `NO_AUTOFIT`, `AUTOMATED`, `UNRECOVERABLE`, `NO_BATTLE_SALVAGE`, `HULL_UNRESTORABLE`, `MONSTER`, `FULL_CR_RECOVERY`, `RESTRICTED`, `MILITARY_MARKET_ONLY`, `THEME_HIDDEN`, `PK_SYSTEM`, `SYSTEM_ABYSSAL`, etc.
- `MemFlags` — `$willHasslePlayer`, `$cfai_doNotIgnorePlayer`, `$cfai_ignoreOtherFleets`, `$stationFleet`, `$isPatrol`, `$isWarFleet`, `$isRaider`, `$recentlySalvaged`, `$noShipRecovery`, `$missionImportant`, `$sourceMarket`, etc.

### Combat Entity Hierarchy
- `CombatEntityAPI` — base: location, velocity, facing, owner, collision, shield, hull level, custom data
  - `ShipAPI` — ships/fighters: weapons, systems, stats, hull spec, variant, AI flags
  - `MissileAPI` — missiles: engine controller, AI, flare, flight time, arming, EMP resistance
  - `DamagingProjectileAPI` — projectiles: damage, weapon source, fading
  - `BeamAPI` — beams: from/to, weapon source, width, colors, damage target

### Campaign Entity Hierarchy
- `SectorEntityToken` — base: location, orbit, market, cargo, faction, tags, memory, custom description
  - `CampaignFleetAPI` — fleets: assignments, logistics, commander, fleet data, AI
  - `PlanetAPI` — planets: type, spec, light color, conditions
  - `JumpPointAPI` — jump points: destinations
  - `CustomCampaignEntityAPI` — custom entities
  - `OrbitalStationAPI` — stations
- `StarSystemAPI` extends `LocationAPI` — stars, planets, jump points, asteroid belts, ring bands
- `LocationAPI` — entities, spawn points, background, tokens

### Economy & Market
- `MarketAPI` — size, conditions, industries, commodities, submarkets, connected entities
- `Industry` — supply/demand, income/upkeep, build progress, AI cores, special items
- `SubmarketAPI` — cargo, faction, tariff, plugin
- `EconomyAPI` — markets, commodity prices, updates

### Fleet & Ship Data
- `FleetMemberAPI` — ship in fleet: captain, variant, hull spec, status, repair tracker, CR, deploy cost
- `ShipVariantAPI` — hull mods, weapons, wings, flux vents/caps, weapon groups, source
- `ShipHullSpecAPI` — hull ID, name, size, hints, shield spec, engine spec, built-in mods
- `FleetDataAPI` — fleet composition, members, sorting

### Rendering & Visuals
- `SpriteAPI` — textures, colors, alpha, angle, size
- `ViewportAPI` — view bounds, alpha mult
- `CampaignEngineLayers` — rendering layer enum for campaign entities
- GraphicsLib integration: load normal/material/surface maps via CSV in `data/config/lights/`

### Key Conventions from API
- **Do not store campaign data in hullmod/combat plugin fields** — use `SectorAPI.getPersistentData()` or `MemoryAPI`
- **Transient plugins** — set `isTransient() = true` and re-register in `onGameLoad()` to avoid save bloat/mod removal issues
- **Plugin priority** — `CampaignPlugin.PickPriority`: `CORE_GENERAL` < `MOD_GENERAL` < `MOD_SET` < `MOD_SPECIFIC` < `HIGHEST`
- **Memory key prefixing** — mod-added memory keys MUST use a mod-specific prefix (`$aEP_*` for FSF)
- **Generics warning** — generics cannot be used in scripts (rules.csv, some JSON hooks); use raw types

## Existing AI Instruction Sources
- No existing `AGENTS.md`/`AGENT.md`/`CLAUDE.md`/copilot instruction file was found in this mod.
- `.github/agents/` exists but is currently empty.


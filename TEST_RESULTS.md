# MCreatorMCP Expanded Server — Test Results

Workspace used: `MCPTtest7` (Neoforge 1.21.1 generator, mod id `mcptest7`). `MCPTtest8` was used for earlier Phase 1 tests.

## Environment
- MCreator: bundled install `/home/ubuntu/repos/MCreator20262`
- JDK: bundled OpenJDK 25 (`/home/ubuntu/repos/MCreator20262/jdk`)
- Plugin artifact: `/home/ubuntu/repos/MCreatorMCP/build/libs/MCreatorMCP.zip`
- MCP endpoint: `http://localhost:5175/mcp`
- Health endpoint: `http://localhost:5175/health`

## Tool Count
`tools/list` returned **146 tools** after the Phase 6 GUI/Overlay/Structure/model/advanced-block/in-world/workspace/API expansion (all previous categories + full GUI/Overlay component editing + Structure NBT import + entity model/animation pipeline + advanced block property aliases + in-world automated block/entity testing + workspace export/import + installed plugin/mod API integration helpers).

## Core Workspace Tools

| Tool | Result | Notes |
|------|--------|-------|
| `getWorkspaceInfo` | OK | returned workspace metadata and element count |
| `getWorkspaceSettings` | OK | returned `modName`, `version`, `author`, `generator`, etc. |
| `listModElements` | OK | listed all mod elements with type/name filters |
| `validateWorkspace` | OK | reported invalid references from earlier test elements; new elements normalized correctly |
| `regenerateCode` | OK | triggered Gradle `build` and generated Java + JSON |
| `buildWorkspace` | OK | completed successfully |
| `buildForJavaEdition` | OK | produced `/home/ubuntu/MCreatorWorkspaces/MCPTtest7/build/libs/modid-1.0.jar` (server reached `Done!` after a manual build) |

## Element Creation Tests

### Java Edition elements

| Tool | Payload (summary) | Result |
|------|-------------------|--------|
| `createItem` | `elementName=McpRuby`, basic properties | created and loaded |
| `createBlock` | `elementName=TopazOre`, `texture=topaz`, `hardness=3` | created and generated block class |
| `createTool` | `elementName=McpPickaxe`, `toolType=Pickaxe`, `material=DIAMOND` | created and generated tool class |
| `createArmor` | `elementName=McpEmeraldArmor`, `armorTextureType=emerald` | created and generated armor class |
| `createRecipe` | `elementName=McpRubyRecipe`, `output=mcptest8:mcp_ruby` | created (older build had unnormalized reference; current normalization maps custom refs to `CUSTOM:McpRuby`) |
| `createEnchantment` | `elementName=McpMojo` | created and loaded |
| `createPotion` | `elementName=McpPotion` | created and loaded |
| `createFluid` | `elementName=McpOil` | created after adding `type=water` default |
| `createBiome` | `elementName=McpBiome` | created and loaded |
| `createDimension` | `elementName=McpDimension` | created and loaded |
| `createAchievement` | `elementName=McpAdvancement` | created after adding `triggerxml` default |
| `createLootTable` | `elementName=McpLootTable` | created and loaded |
| `createFunction` | `elementName=McpFunction` | created and loaded |

### Property mapping verification
- `getElementProperties("TestDrops")` returned full `GeneratableElement` JSON with `customDrop: "Items.DIAMOND"` showing that `minecraft:diamond` was normalized through the `blocksitems` datalist fallback.

## Asset / Variable / Localization / Validation Tools

| Tool | Result |
|------|--------|
| `importTexture` | imported placeholder textures into correct asset folder |
| `listTexturesByType` | returned textures filtered by `BLOCK` / `ITEM` |
| `createVariable` | created workspace variables |
| `listWorkspaceVariables` | listed variables |
| `setLocalization` / `getLocalizations` / `addLanguage` | worked for English (`en_us`) and new languages |
| `validateElement` | reported issues for elements with missing textures |
| `validateWorkspace` | found 4 invalid references from pre-normalization test elements and passed cleanly for new elements |
| `exportResourcePack` | copied resources to `/tmp/mcptest8_resource_pack` |
| `deployToGameFolder` | copied `modid-1.0.jar` to `/tmp/mcptest8_deploy/mods/` |

## Known Issues / Notes
1. **Pre-existing test element references** — `SapphireOre`, `TopazOre`, `McpPickaxe`, and the first `McpRubyRecipe` (in `MCPTtest8`) were created before the item/block normalization fallback was complete. `validateWorkspace` flags them as invalid references. New elements created after the fix normalize `minecraft:diamond`, `minecraft:iron_ingot`, and custom items correctly.
2. **Block `itemTexture`** — The first block tests set `itemTexture` to the same block texture name, causing missing texture warnings in the client. This has been fixed: `applyBlockSingleTexture` no longer copies the block texture to `itemTexture`, and `applyGeneratableElementDefaults` no longer adds a placeholder `itemTexture` for blocks.
3. **Bedrock/resource-pack tools** are registered as MCP tools that write the corresponding pack JSON/manifest files into the workspace. Full Bedrock generation depends on the MCreator generator for the active workspace; the tools provide the programmatic bridge to create those assets.
4. **Recipe chains** — `createRecipeChain` validates that each recipe's output appears in the next recipe's inputs, but it does not verify that referenced items/blocks exist. Agents should create ingredient items/blocks before the chain, or use `minecraft:*` references.

## Advanced Tools & Workflow Tests (MCPTtest7)

| Tool | Payload (summary) | Result |
|------|-------------------|--------|
| `createProcedure` | `elementName=ProcRightClick`, valid Blockly XML | created; generated `ProcRightClickProcedure.class` |
| `updateEventProcedure` | `EventSword.onRightClickedInAir -> ProcRightClick` | linked; XML persisted |
| `getEventProcedures` | `elementName=EventSword` | returned all event hooks with field types and linked XML |
| `createTextureSet` | `ruby_set` (file-based PNG + animation) | created block/item textures and `.mcmeta` file |
| `createModelFromDefinition` | `ruby_block_model` (cube_all) | wrote JSON model to assets |
| `createRecipeChain` | smelt + craft chain | created two linked recipe elements (note: test inputs used non-existent items, so server reported recipe parse errors) |
| `createParticleEffect` | `SparkParticle`, placeholder texture | created and generated particle classes |
| `createSoundEvent` | `mcp_beep` OGG, category `master` | registered sound, copied `.ogg`, generated `sounds.json` |
| `createBedrockResourcePack` / `createBedrockBehaviorPack` | `mcp_test_rp` / `mcp_test_bp`, v1.0.0 | created folders with `manifest.json` |
| `buildBedrockProject` | `packName=mcp_test` | packaged `.mcpack` files for resource and behavior packs |
| `compareElementVersions` | `TestItem`, current vs current | returned identical element JSON and empty diff |
| `generateTestReport` | default `run/logs/latest.log` | parsed log and summarized errors/warnings |
| `runServer` | via MCP tool | returned immediately; server log reached `Done (1.291s)!` |
| `updateWorkspaceSettings` | `modid` and `modElementsPackage` | both fields updated (reflection for `modid`, setter for `modElementsPackage`) |

## In-Game Verification (MCPTtest7)
- `runClient()` launched Minecraft 1.21.1 with NeoForge 21.1.232 and loaded the mod classes.
- `runServer()` (launched both manually and via MCP tool) reached `Done (1.291s)!`.
- Server log confirmed the mod loaded; only expected recipe warnings were present from the test chain (non-existent `RubyIngot` and empty crafting slots mapped to `minecraft:air`).

## Build Artifacts
- Plugin ZIP: `/home/ubuntu/repos/MCreatorMCP/build/libs/MCreatorMCP.zip`
- Test mod JAR: `/home/ubuntu/MCreatorWorkspaces/MCPTtest7/build/libs/modid-1.0.jar`

## Phase 3 Tests — Tags, Creative Tabs, Backups, Generators, Procedures, In-Game Verification

| Tool | Payload (summary) | Result |
|------|-------------------|--------|
| `createTag` | `gems`, `ITEMS`, entries `[minecraft:diamond, minecraft:emerald, CUSTOM:TestItem]` | created tag; `gems.json` generated with `mcptest7:test_item` |
| `updateTag` | add `CUSTOM:TestItem` to `gems` | updated; custom references normalized through `NameMapper` reverse-lookup |
| `deleteTag` | `gems2` / `gems3` / `Gems` | deleted workspace tags and stale generated JSON files |
| `listTags` | no args | returned `ruby_ores` (BLOCKS) and `gems` (ITEMS) with managed/unmanaged entry info |
| `createCreativeTab` | `RubyTab`, icon `Items.DIAMOND` | created `Tab` element; generated `Mcptest7ModTabs.java` |
| `updateCreativeTabs` | `RubyTab` -> `[TestItem, SapphireBlock, TestBlock]` | items/blocks linked via `CUSTOM:RubyTab` and rendered in tab |
| `listCreativeTabs` | no args | returned tab elements and tab order |
| `createBackup` / `listBackups` / `restoreBackup` | partial name match | checkpoint created, listed, restored |
| `listProcedures` | no args | returned `TestItemOnRightClickedInAir` and `ProcRightClick` XML lengths |
| `updateProcedure` | replace `ProcRightClick` Blockly XML | XML persisted and procedure regenerated |
| `switchGenerator` / `listGenerators` | list only | current generator `neoforge-1.21.1`; datapack/Bedrock add-on generators listed |
| `validateModel` | `ruby_block_model.json` | validated JSON model has textures and parent |
| `convertModel` | `ruby_block_model.json` -> `block/ruby_block_model` | no-op for already-JSON; OBJ parsing implemented |
| `verifyServerLoads` | `timeoutSeconds=120` | server reached `Done (...)`; no `[ERROR]` lines in `latest.log` |
| `verifyClientLoads` | `timeoutSeconds=120` | client created texture atlas; only OpenAL/SoundSystem error (headless/no audio device) |
| `buildForJavaEdition` | no args | produced `modid-1.0.jar` |

### Phase 3 Fixes Verified
- `regenerateCode`/`buildWorkspace` now call `Generator.generateBase(true)`, iterate all `ModElement`s with `Generator.generateElement(..., true, true)`, and `runResourceSetupTasks()`.
- Custom tag references (`CUSTOM:TestItem`) now map to `<modid>:test_item` in generated tag JSON.
- Tag resource paths are lowercased by `normalizeTagName` to avoid `Invalid path in pack` warnings.
- `deleteTag` removes stale generated tag JSONs from `src/main/resources` and `build/resources/main`.
- `TabEntry` custom names keep original case (`CUSTOM:RubyTab`) so the generator registers the correct creative tab.
- `createSoundEvent` removes an existing `SoundElement` before re-adding, and uses `subtitles.<soundName>` consistently for `sounds.json` and localization.
- `verifyServerLoads` checks `[Server thread/INFO] ... Done (` and `verifyClientLoads` checks `[Render thread/INFO] ... Created: ... blocks.png-atlas` across the full tail of the log.

## Known Issues / Notes (Post-Phase 3)
1. Headless client verification reports a single OpenAL `SoundSystem` error because the VM has no audio device. This is environmental and does not affect mod functionality.
2. Old pre-normalization test elements from `MCPTtest8` (and early `MCPTtest7` elements) may still be flagged if they are loaded in a workspace; fresh elements validate cleanly.
3. `verifyServerLoads`/`verifyClientLoads` error counts may include stale `/ERROR` lines from previous runs when logs are not rotated. The status field is the reliable pass/fail signal.
4. Bedrock pack tools write manifest and element JSON files into the workspace; full Bedrock build output depends on selecting the Bedrock add-on generator and running `buildBedrockProject`.

## Phase 4 Tests — Lifecycle, Fine-Grained Editing, Asset Pipeline, In-Game Verification, CI Automation

| Tool | Payload (summary) | Result |
|------|-------------------|--------|
| `createCommand` | `elementName=McpNewCommand`, `argsxml` with `args_start` | created and generated `Command` element; server loads |
| `createFeature` | `elementName=McpFeatureFix`, `featurexml` with `feature_container` | created and generated `Feature` element; code compiles |
| `createStructure` | `elementName=McpTower` | created `Structure` element with `SURFACE_STRUCTURES` generation step |
| `createPlant` | `elementName=McpBerry` | created `Plant` element with default block/plant textures |
| `createProjectile` | `elementName=McpFireball` | created `Projectile` element and generated entity class |
| `createVillagerProfession` | `elementName=McpVillagerFix` | created with `Blocks.CRAFTING_TABLE` POI and default sound |
| `createVillagerTrade` | `elementName=McpVillagertrade` | created `VillagerTrade` element |
| `createPotionEffect` | `elementName=McpPotioneffect` | created with default particle/sound/color; no FreeMarker errors |
| `createAttribute` | `elementName=McpAttribute` | created `Attribute` element |
| `createKeyBinding` | `elementName=McpKeybind` | created `KeyBinding` element |
| `createDamageType` | `elementName=McpDamagetype` | created `DamageType` element |
| `createPainting` | `elementName=McpPainting` | created `Painting` element |
| `createBannerPattern` | `elementName=McpBannerpattern` | created `BannerPattern` element |
| `cloneElement` | `sourceElementName=TestBlock`, `newElementName=ClonedBlock` | cloned element JSON persisted |
| `renameElement` | `elementName=ClonedBlock`, `newName=RenamedBlock` | renamed and workspace consistent |
| `moveElement` | `elementName=RenamedBlock`, `folderPath=""` | moved to workspace root |
| `editRecipe` | `McpRubyRecipe` with `output`, `outputCount`, inputs | recipe regenerated cleanly |
| `editAdvancement` | `McpAdvance` displayName/description/rewardXP | updated and regenerated cleanly |
| `editLootTable` | `McpSapphireLoot` with pools/entries/item | entries normalized to `CUSTOM:TestItem`; regenerated cleanly |
| `processTexture` | `test_item` resize + recolor + pad | produced 32x32 recolored texture in assets folder |
| `generateMcmeta` | `test_item` frameTime=2, interpolate | wrote `.mcmeta` with `animation.interpolate=true` |
| `convertBlockbenchModel` | `/tmp/test_blockbench.json` -> `test_converted` | wrote Minecraft JSON block model |
| `executeServerCommand` | `say MCreatorMCP RCON works` | RCON command sent; server logged message |
| `runTestScenario` | `phase4-logs` with `say` command | server started, command executed, stopped, errors counted |
| `runCIBuild` | timeout 300s | regenerated code, built JAR, started server, command executed |
| `exportModrinth` | output `/tmp/mcptest7.mrpack` | produced `.mrpack` containing built JAR |
| `buildForJavaEdition` | no args | returned path to `modid-1.0.jar` |
| `generateTestReport` | default `run/logs/latest.log` | parsed latest server log, summarized errors/warnings |

### Phase 4 Fixes Verified
- `McpElementPropertyApplier` now aliases `potioneffect`/`achievement`/`villagerprofession` keys and re-applies safe defaults after `GEValidator` resets.
- `Feature` `featurexml` defaults to a valid `feature_container` block and `generationStep` is restored to `UNDERGROUND_ORES` after validation.
- `Command` `argsxml` defaults to a valid `args_start` block.
- `PotionEffect` `onAddedSound` and `particle` defaults use empty-value checks so `setDefaultMappableElement` placeholders are overridden.
- `VillagerProfession` defaults `pointOfInterest` to `Blocks.CRAFTING_TABLE` and `actionSound` to `entity.villager.work_librarian`.
- `LootTable` pool editing normalizes `name`/`item` and `rolls` into `LootTable.Pool`/`Entry` objects.
- `RConClient` was rewritten to use raw `ByteBuffer`/`InputStream`/`OutputStream` because `DataInputStream`/`DataOutputStream` caused `EOFException` with the Minecraft RCON server.
- `runTestScenario` now clears stale `latest.log`/`debug.log` files and waits for both `Done (` and `RCON running on` before connecting.
- `patchInitImports` adds wildcard imports for `block`, `item`, `potion`, `entity`, `client.particle`, `world.features`, and `potion` subpackages so generated init classes compile.

### Known Issues / Notes (Post-Phase 4)
1. The test workspace contains multiple villager professions that all point to `minecraft:crafting_table`, so the server logs `Skipping villager profession ... POI block already in use` and related tag/advancement errors. These are test-data conflicts, not code defects.
2. `runClient` still reports a single OpenAL `SoundSystem` error in the headless VM; this is environmental and expected.
3. `executeServerCommand` requires an already-running server (use `runServer` or `runTestScenario` first).

## Phase 5 Tests — GUI/Overlay/Code/Bedrock/Datapack/Publishing/Client Verification

| Tool | Payload (summary) | Result |
|------|-------------------|--------|
| `createGui` / `createOverlay` / `createGamerule` / `createItemextension` / `createArmortrim` / `createCode` | minimal properties | created and generated code; server loaded without FreeMarker/NullPointer errors |
| `createDatapackFeature` | `featureName="Ruby Ore Cluster"`, `featureType="ore"`, `target="minecraft:stone_ore_replaceables"`, `state="minecraft:redstone_ore"`, `count=6` | wrote `configured_feature/ruby_ore_cluster.json` and `placed_feature/ruby_ore_cluster.json`; server loaded without datapack registry errors |
| `createBedrockBehaviorJson` | `packName=mcp_bedrock`, `elementType=item`, `elementName=test_item` | wrote valid Bedrock behavior pack JSON under the workspace |
| `getLatestLog` | `lines=20` | returned the latest server `latest.log` tail |
| `getGradleLog` | `lines=50` | returned the Gradle runserver/build log tail |
| `getBuildProgress` | `maxChars=4000` | returned `READY`/console tail after build completed |
| `publishToModrinth` | dummy token | reached Modrinth API and returned `401 unauthorized` (HTTP code captured in output) |
| `publishToCurseForge` | dummy token | reached CurseForge API and returned `404 Not Found` (HTTP code captured in output) |
| `verifyClientInGame` | `timeoutSeconds=120`, `outputPath=/tmp/mcp_screenshot2.png` | launched Minecraft client under Xvfb, captured a PNG of the main menu |
| `runTestScenario` | `summon minecraft:cow`, `say Final in-game verification passed` | server started, RCON connected, entity spawned, message broadcasted |

### Phase 5 Fixes Verified
- `McpPublishingAndVerificationService` registers `getLatestLog`, `getGradleLog`, `getBuildProgress`, `publishToModrinth`, `publishToCurseForge`, `createDatapackFeature`, `createBedrockBehaviorJson`, and `verifyClientInGame`.
- `McpElementPropertyApplier` and `MCPToolsService` now alias and default `gui`, `overlay`, `gamerule`, `itemextension`, `armortrim`, and `code`/`customelement` so their generated code compiles and loads.
- `createDatapackFeature` sanitizes file names to lowercase/underscore, outputs a string `feature` reference, and adds the required `discard_chance_on_air_exposure` field for `minecraft:ore` configs.
- `publishToModrinth` sends the required `dependencies`/`featured`/`file_parts` fields and appends the HTTP response code to the tool output.
- `verifyClientInGame` uses a virtual display, waits for the title screen, and captures the in-game screenshot via `import`/xdotool.

### Phase 6 Tests — GUI/Overlay/Structure, Model/Animation, Advanced Block Properties, In-World Verification, Workspace/API Integration

| Tool | Payload (summary) | Result |
|------|-------------------|--------|
| `createGui` | `elementName=TestGui`, `width=256`, `height=200`, `components` with `label` and `button` | created GUI element with serialized `GUIComponent` list; code regenerated and built successfully |
| `createOverlay` | `elementName=TestOverlay`, `components` with `image` and `label`, `baseTexture` | created Overlay element; generated code compiled cleanly |
| `createStructure` | `elementName=McpTower`, `structureFile=/tmp/test_structure.nbt` | NBT file copied to `src/main/resources/data/mcptest7/structure/test_structure.nbt`; `structure` field set to `test_structure` |
| `createLivingentity` | `elementName=ModelMob`, `model=Default`, `texture=model_mob_texture`, `animations=[{animation:walk}]` | `mobModelName`, `mobModelTexture`, `modelLayers`, `aixml`, and `animations` defaulted/persisted; generated renderer class compiled |
| `createBlock` | `elementName=AdvPropBlock`, `rotation=y_axis`, `render=cutout`, `transparencyType=CUTOUT`, `tint=Grass`, `toolClass=pickaxe`, `toolLevel=2`, `customModelName=cube_all` | created block with `rotationMode`, `renderType`, `transparencyType`, `tintType`, `destroyTool`, and `vanillaToolTier` mapped; built successfully |
| `runTestScenario` | `place-break-inspect`: `setblock 0 70 0 mcptest7:adv_prop_block`, `data get entity`, `setblock 0 70 0 air` | server reached `Done`, RCON connected, custom block placed/broken, `ModelMob` summoned and Health inspected |
| `exportWorkspace` | `outputPath=/tmp/mcptest7_workspace.zip` | produced shareable 814 KB workspace ZIP |
| `importWorkspace` | `zipPath=/tmp/mcptest7_workspace.zip`, `targetFolder=/tmp/mcptest7_imported` | extracted workspace files; `.mcreator` present and directory structure intact |
| `listRecentWorkspaces` | no args | returned `MCPTtest7` recent entry with path/version |
| `listInstalledPlugins` | no args | returned built-in plugins including `mcreator_mcp_plugin`, `generator-1.21.1`, etc. |
| `listModAPIs` | no args | returned `mcreator_link` API available for `neoforge-1.21.1` |
| `enableModAPI` / `disableModAPI` | `apiId=mcreator_link` | enabled API dependency, regenerated code, built JAR with `Loaded APIs: mcreator_link`; disabled cleanly |
| `exportModrinth` | `outputPath=/tmp/mcptest7.mrpack` | produced `.mrpack` with `modrinth.index.json` and `overrides/mods/modid-1.0.jar` |

### Phase 6 Fixes Verified
- `McpElementPropertyApplier` now parses `GUIComponent`/`Overlay` `components` and `gridSettings` through the official `GSONAdapter`, supports `Structure` NBT import via `WorkspaceFolderManager.getStructuresDir()`, and applies `LivingEntity` `mobModelName`/`mobModelTexture`/`modelLayers`/`animations` defaults including `XML_BASE` for `aixml`.
- Added Block property aliases (`rotationmode`, `rendertype`, `transparent`, `transparencytype`, `tinttype`, `custommodel`, `itemtexture`, `particle`) and string-to-int parsers for `renderType` (`solid/cutout/translucent/cutout_mipped`) and `rotationMode` (`none/y_axis/all_axis/block_y_axis/block_all_axis/log`).
- `McpLifecycleToolsService` adds workspace import/export, recent-workspace listing, installed plugin listing, and mod API enable/disable helpers.
- `GameRule` displayName and `LivingEntity` mobName/label/behaviour/creatureType/aiBase/aixml are now defaulted in `postProcess` to prevent `GEValidator` load failures.

### Known Issues / Notes (Post-Phase 6)
1. `executeServerCommand` still requires an RCON-enabled server; use `runTestScenario()` for automated command chains.
2. `publishToModrinth`/`publishToCurseForge` are end-to-end wired but return 401/404 when using dummy credentials; a real API token/project ID is required for actual uploads.
3. The test workspace contains overlapping villager-profession POI blocks and an advancement with a missing item, which produce non-fatal server log errors.
4. `importWorkspace` extracts the workspace `.zip` but cannot automatically open it in the running MCreator window; open the extracted `.mcreator` file manually or restart MCreator with that path.
4. Multi-workspace/no-workspace mode and safe generator migration are not exposed as tools because MCreator's active `MCreator` instance is bound to a single workspace window; these remain documented limitations.

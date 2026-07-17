# MCreatorMCP Expanded Server — Test Results

Workspace used: `MCPTtest7` (Neoforge 1.21.1 generator, mod id `mcptest7`). `MCPTtest8` was used for earlier Phase 1 tests.

## Environment
- MCreator: bundled install `/home/ubuntu/repos/MCreator20262`
- JDK: bundled OpenJDK 25 (`/home/ubuntu/repos/MCreator20262/jdk`)
- Plugin artifact: `/home/ubuntu/repos/MCreatorMCP/build/libs/MCreatorMCP.zip`
- MCP endpoint: `http://localhost:5175/mcp`
- Health endpoint: `http://localhost:5175/health`

## Tool Count
`tools/list` returned **98 tools** after the Phase 2 advanced-tools expansion (workspace/element/asset/variable/build/export/validation + per-type `create<TYPE>` shortcuts + procedure/event/workflow tools + Bedrock pack tools + versioning/test-report tools).

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

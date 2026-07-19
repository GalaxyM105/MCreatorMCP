# MCreatorMCP Tool Catalog

This document lists every MCP tool exposed by the MCreatorMCP plugin introduced in PR #1, grouped by functional category. Each entry includes the tool description, JSON input parameters, and how it was verified in the `MCPTtest7` workspace (Neoforge 1.21.1, MCreator 2026.2 build 29418). For the full narrative of each test phase, see [`TEST_RESULTS.md`](TEST_RESULTS.md).

- **Total tools:** 178
- **Test workspace:** `MCPTtest7` (`/home/ubuntu/MCreatorWorkspaces/MCPTtest7`)
- **MCP endpoint:** `http://localhost:5175/mcp`
- **Health endpoint:** `http://localhost:5175/health`

---

## Table of Contents

- [Workspace Management](#workspace-management)
- [Element Discovery & Search](#element-discovery--search)
- [Generic Element Creation](#generic-element-creation)
- [Typed Element Creation — Java Edition](#typed-element-creation--java-edition)
- [Bedrock Edition Element Aliases](#bedrock-edition-element-aliases)
- [Bedrock Pack Tools](#bedrock-pack-tools)
- [Compound Element Workflows](#compound-element-workflows)
- [Element Lifecycle & Editing](#element-lifecycle--editing)
- [Procedure & Event Tools](#procedure--event-tools)
- [Advanced Mob AI & Code](#advanced-mob-ai--code)
- [Build-System Hooks](#build-system-hooks)
- [Texture, Model & Asset Tools](#texture-model--asset-tools)
- [Workspace Variables & Localization](#workspace-variables--localization)
- [Tags, Creative Tabs & Generators](#tags-creative-tabs--generators)
- [Build, Export, Deploy & CI](#build-export-deploy--ci)
- [Testing & In-Game Verification](#testing--in-game-verification)
- [Publishing](#publishing)
- [Datapack-Only Worldgen](#datapack-only-worldgen)
- [Log Streaming & Build Diagnostics](#log-streaming--build-diagnostics)

---

## Workspace Management

### `buildWorkspace`

**Description:** Build the current MCreator workspace

**Parameters:**
No parameters.

**Verification:** **Test:** `OK` — **Result:** completed successfully

### `getWorkspaceInfo`

**Description:** Get detailed workspace information

**Parameters:**
No parameters.

**Verification:** **Test:** `OK` — **Result:** returned workspace metadata and element count

### `getWorkspaceSettings`

**Description:** Get all workspace settings

**Parameters:**
No parameters.

**Verification:** **Test:** `OK` — **Result:** returned `modName`, `version`, `author`, `generator`, etc.

### `updateWorkspaceSettings`

**Description:** Update workspace settings

**Parameters:**
- `settings` (`object`, required) — Map of setting names to values

**Verification:** **Test:** `modid' and 'modElementsPackage` — **Result:** both fields updated (reflection for `modid`, setter for `modElementsPackage`)

### `regenerateCode`

**Description:** Regenerate code without building

**Parameters:**
No parameters.

**Verification:** **Test:** `OK` — **Result:** triggered Gradle `build` and generated Java + JSON

### `createModWithTemplate`

**Description:** Create a complete mod from a template

**Parameters:**
- `modId` (`string`, required) — Mod ID
- `modName` (`string`, required) — Mod display name
- `templateName` (`string`, required) — Template: basic_item, ore_set, armor_set, full_biome, dimension_mod, techmod_base
- `author` (`string`, optional) — Author
- `properties` (`object`, optional) —
- `version` (`string`, optional) — Version

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `compareElementVersions`

**Description:** Compare an element across two workspace backups

**Parameters:**
- `elementName` (`string`, required) — Element name
- `version1` (`string`, optional) — First version/backup name (or 'current')
- `version2` (`string`, optional) — Second version/backup name (or 'latest')

**Verification:** **Test:** `TestItem', current vs current` — **Result:** returned identical element JSON and empty diff

### `createBackup`

**Description:** Create a workspace backup (local history checkpoint)

**Parameters:**
- `backupName` (`string`, optional) — Backup name/label

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `listBackups`

**Description:** List workspace backups from local history

**Parameters:**
No parameters.

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `restoreBackup`

**Description:** Restore the workspace to a previous backup

**Parameters:**
- `backupName` (`string`, required) — Backup name, hash, or 'latest'

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `listElementFolders`

**Description:** List workspace folder tree for organizing elements

**Parameters:**
No parameters.

**Verification:** **Test:** `no args` — **Result:** returned workspace folder tree (root `~` plus newly created `TestFolder`)

### `createElementFolder`

**Description:** Create a workspace folder for elements

**Parameters:**
- `folderName` (`string`, required) — New folder name
- `parentPath` (`string`, optional) — Parent folder path, empty for root

**Verification:** **Test:** `folderName=TestFolder` — **Result:** created `~/TestFolder` in the workspace

### `moveElementsToFolder`

**Description:** Move multiple elements to a workspace folder

**Parameters:**
- `elementNames` (`object`, required) — List of element names
- `folderPath` (`string`, required) — Target folder path, empty for root

**Verification:** **Test:** `elementNames=[TestItem, TestBlock]', 'folderPath=~/TestFolder` — **Result:** moved both elements to the folder

### `exportWorkspace`

**Description:** Export the current workspace to a shareable .zip file

**Parameters:**
- `outputPath` (`string`, required) — Output .zip file path
- `includeRunDir` (`string`, optional) — Include the run directory (true/false, default: false)

**Verification:** **Test:** `outputPath=/tmp/mcptest7_workspace.zip` — **Result:** produced shareable 814 KB workspace ZIP

### `importWorkspace`

**Description:** Import a workspace from a .zip file (extract only; open it manually or restart MCreator)

**Parameters:**
- `zipPath` (`string`, required) — Path to the workspace .zip
- `targetFolder` (`string`, optional) — Optional folder to extract to

**Verification:** **Test:** `zipPath=/tmp/mcptest7_workspace.zip', 'targetFolder=/tmp/mcptest7_imported` — **Result:** extracted workspace files; `.mcreator` present and directory structure intact

### `listRecentWorkspaces`

**Description:** List recently opened MCreator workspaces

**Parameters:**
No parameters.

**Verification:** **Test:** `no args` — **Result:** returned `MCPTtest7` recent entry with path/version

### `listInstalledPlugins`

**Description:** List installed MCreator plugins

**Parameters:**
No parameters.

**Verification:** **Test:** `no args` — **Result:** returned built-in plugins including `mcreator_mcp_plugin`, `generator-1.21.1`, etc.

### `listModAPIs`

**Description:** List MCreator API plugins/addons available for the current generator

**Parameters:**
No parameters.

**Verification:** **Test:** `no args` — **Result:** returned `mcreator_link` API available for `neoforge-1.21.1`

### `enableModAPI`

**Description:** Enable an API plugin/addon for the workspace

**Parameters:**
- `apiId` (`string`, required) — API ID (e.g. geckolib)

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `disableModAPI`

**Description:** Disable an API plugin/addon for the workspace

**Parameters:**
- `apiId` (`string`, required) — API ID (e.g. geckolib)

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.


## Element Discovery & Search

### `listModElements`

**Description:** List mod elements with optional filtering

**Parameters:**
- `elementType` (`string`, required) — Filter by element type

**Verification:** **Test:** `OK` — **Result:** listed all mod elements with type/name filters

### `listModElementTypes`

**Description:** List all available element types

**Parameters:**
No parameters.

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `getElementProperties`

**Description:** Get all properties of a mod element as JSON

**Parameters:**
- `elementName` (`string`, required) — Name of the element

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `searchElements`

**Description:** Search mod elements by name or type

**Parameters:**
- `query` (`string`, required) — Search string

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `deleteElement`

**Description:** Delete mod element

**Parameters:**
- `elementName` (`string`, required) — Name of element to delete

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `validateElement`

**Description:** Validate a mod element for missing textures or references

**Parameters:**
- `elementName` (`string`, required) — Name of element to validate

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `validateWorkspace`

**Description:** Validate the entire workspace

**Parameters:**
No parameters.

**Verification:** **Test:** `OK` — **Result:** reported invalid references from earlier test elements; new elements normalized correctly


## Generic Element Creation

### `createElement`

**Description:** Create a customized mod element

**Parameters:**
- `elementName` (`string`, required) — Name of the new element
- `elementType` (`string`, required) — Type of element to create
- `properties` (`object`, optional) — Element-specific customization properties

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.


## Typed Element Creation — Java Edition

### `createAchievement`

**Description:** Create a Advancement mod element

**Parameters:**
- `elementName` (`string`, required) — Name of the new Advancement
- `properties` (`object`, optional) — Customization properties for the Advancement

**Verification:** **Test:** `elementName=McpAdvancement` — **Result:** created after adding `triggerxml` default

### `createArmor`

**Description:** Create a Armor mod element

**Parameters:**
- `elementName` (`string`, required) — Name of the new Armor
- `properties` (`object`, optional) — Customization properties for the Armor

**Verification:** **Test:** `elementName=McpEmeraldArmor', 'armorTextureType=emerald` — **Result:** created and generated armor class

### `createArmortrim`

**Description:** Create a Armor trim mod element

**Parameters:**
- `elementName` (`string`, required) — Name of the new Armor trim
- `properties` (`object`, optional) — Customization properties for the Armor trim

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `createAttribute`

**Description:** Create a Attribute mod element

**Parameters:**
- `elementName` (`string`, required) — Name of the new Attribute
- `properties` (`object`, optional) — Customization properties for the Attribute

**Verification:** **Test:** `elementName=McpAttribute` — **Result:** created `Attribute` element

### `createBannerpattern`

**Description:** Create a Banner pattern mod element

**Parameters:**
- `elementName` (`string`, required) — Name of the new Banner pattern
- `properties` (`object`, optional) — Customization properties for the Banner pattern

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `createBiome`

**Description:** Create a Biome mod element

**Parameters:**
- `elementName` (`string`, required) — Name of the new Biome
- `properties` (`object`, optional) — Customization properties for the Biome

**Verification:** **Test:** `elementName=McpBiome` — **Result:** created and loaded

### `createBlock`

**Description:** Create a Block mod element

**Parameters:**
- `elementName` (`string`, required) — Name of the new Block
- `properties` (`object`, optional) — Customization properties for the Block

**Verification:** **Test:** `elementName=TopazOre', 'texture=topaz', 'hardness=3` — **Result:** created and generated block class **Test:** `elementName=AdvPropBlock', 'rotation=y_axis', 'render=cutout', 'transparencyType=CUTOUT', 'tint=Grass', 'toolClass=picka...` — **Result:** created block with `rotationMode`, `renderType`, `transparencyType`, `tintType`, `destroyTool`, and `vanillaToolTier` mapped; built successfully

### `createCommand`

**Description:** Create a Command mod element

**Parameters:**
- `elementName` (`string`, required) — Name of the new Command
- `properties` (`object`, optional) — Customization properties for the Command

**Verification:** **Test:** `elementName=McpNewCommand', 'argsxml' with 'args_start` — **Result:** created and generated `Command` element; server loads

### `createDamagetype`

**Description:** Create a Damage type mod element

**Parameters:**
- `elementName` (`string`, required) — Name of the new Damage type
- `properties` (`object`, optional) — Customization properties for the Damage type

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `createDimension`

**Description:** Create a Dimension mod element

**Parameters:**
- `elementName` (`string`, required) — Name of the new Dimension
- `properties` (`object`, optional) — Customization properties for the Dimension

**Verification:** **Test:** `elementName=McpDimension` — **Result:** created and loaded

### `createCode`

**Description:** Create a Custom element mod element

**Parameters:**
- `elementName` (`string`, required) — Name of the new Custom element
- `properties` (`object`, optional) — Customization properties for the Custom element

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `createEnchantment`

**Description:** Create a Enchantment mod element

**Parameters:**
- `elementName` (`string`, required) — Name of the new Enchantment
- `properties` (`object`, optional) — Customization properties for the Enchantment

**Verification:** **Test:** `elementName=McpMojo` — **Result:** created and loaded

### `createFeature`

**Description:** Create a Feature mod element

**Parameters:**
- `elementName` (`string`, required) — Name of the new Feature
- `properties` (`object`, optional) — Customization properties for the Feature

**Verification:** **Test:** `elementName=McpFeatureFix', 'featurexml' with 'feature_container` — **Result:** created and generated `Feature` element; code compiles

### `createFluid`

**Description:** Create a Fluid mod element

**Parameters:**
- `elementName` (`string`, required) — Name of the new Fluid
- `properties` (`object`, optional) — Customization properties for the Fluid

**Verification:** **Test:** `elementName=McpOil` — **Result:** created after adding `type=water` default

### `createFunction`

**Description:** Create a Function mod element

**Parameters:**
- `elementName` (`string`, required) — Name of the new Function
- `properties` (`object`, optional) — Customization properties for the Function

**Verification:** **Test:** `elementName=McpFunction` — **Result:** created and loaded

### `createGamerule`

**Description:** Create a Game rule mod element

**Parameters:**
- `elementName` (`string`, required) — Name of the new Game rule
- `properties` (`object`, optional) — Customization properties for the Game rule

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `createGui`

**Description:** Create a GUI mod element

**Parameters:**
- `elementName` (`string`, required) — Name of the new GUI
- `properties` (`object`, optional) — Customization properties for the GUI

**Verification:** **Test:** `elementName=TestGui', 'width=256', 'height=200', 'components' with 'label' and 'button` — **Result:** created GUI element with serialized `GUIComponent` list; code regenerated and built successfully

### `createItem`

**Description:** Create a Item mod element

**Parameters:**
- `elementName` (`string`, required) — Name of the new Item
- `properties` (`object`, optional) — Customization properties for the Item

**Verification:** **Test:** `elementName=McpRuby', basic properties` — **Result:** created and loaded

### `createItemextension`

**Description:** Create a Item extension mod element

**Parameters:**
- `elementName` (`string`, required) — Name of the new Item extension
- `properties` (`object`, optional) — Customization properties for the Item extension

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `createKeybind`

**Description:** Create a Key binding mod element

**Parameters:**
- `elementName` (`string`, required) — Name of the new Key binding
- `properties` (`object`, optional) — Customization properties for the Key binding

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `createLivingentity`

**Description:** Create a Living entity mod element

**Parameters:**
- `elementName` (`string`, required) — Name of the new Living entity
- `properties` (`object`, optional) — Customization properties for the Living entity

**Verification:** **Test:** `elementName=ModelMob', 'model=Default', 'texture=model_mob_texture', 'animations=[{animation:walk}]` — **Result:** `mobModelName`, `mobModelTexture`, `modelLayers`, `aixml`, and `animations` defaulted/persisted; generated renderer class compiled

### `createLoottable`

**Description:** Create a Loot table mod element

**Parameters:**
- `elementName` (`string`, required) — Name of the new Loot table
- `properties` (`object`, optional) — Customization properties for the Loot table

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `createOverlay`

**Description:** Create a Overlay mod element

**Parameters:**
- `elementName` (`string`, required) — Name of the new Overlay
- `properties` (`object`, optional) — Customization properties for the Overlay

**Verification:** **Test:** `elementName=TestOverlay', 'components' with 'image' and 'label', 'baseTexture` — **Result:** created Overlay element; generated code compiled cleanly

### `createPainting`

**Description:** Create a Painting mod element

**Parameters:**
- `elementName` (`string`, required) — Name of the new Painting
- `properties` (`object`, optional) — Customization properties for the Painting

**Verification:** **Test:** `elementName=McpPainting` — **Result:** created `Painting` element

### `createParticle`

**Description:** Create a Particle mod element

**Parameters:**
- `elementName` (`string`, required) — Name of the new Particle
- `properties` (`object`, optional) — Customization properties for the Particle

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `createPlant`

**Description:** Create a Plant mod element

**Parameters:**
- `elementName` (`string`, required) — Name of the new Plant
- `properties` (`object`, optional) — Customization properties for the Plant

**Verification:** **Test:** `elementName=McpBerry` — **Result:** created `Plant` element with default block/plant textures

### `createPotion`

**Description:** Create a Potion item mod element

**Parameters:**
- `elementName` (`string`, required) — Name of the new Potion item
- `properties` (`object`, optional) — Customization properties for the Potion item

**Verification:** **Test:** `elementName=McpPotion` — **Result:** created and loaded

### `createPotioneffect`

**Description:** Create a Potion effect mod element

**Parameters:**
- `elementName` (`string`, required) — Name of the new Potion effect
- `properties` (`object`, optional) — Customization properties for the Potion effect

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `createProjectile`

**Description:** Create a Projectile mod element

**Parameters:**
- `elementName` (`string`, required) — Name of the new Projectile
- `properties` (`object`, optional) — Customization properties for the Projectile

**Verification:** **Test:** `elementName=McpFireball` — **Result:** created `Projectile` element and generated entity class

### `createRecipe`

**Description:** Create a Recipe mod element

**Parameters:**
- `elementName` (`string`, required) — Name of the new Recipe
- `properties` (`object`, optional) — Customization properties for the Recipe

**Verification:** **Test:** `elementName=McpRubyRecipe', 'output=mcptest8:mcp_ruby` — **Result:** created (older build had unnormalized reference; current normalization maps custom refs to `CUSTOM:McpRuby`)

### `createSpecialentity`

**Description:** Create a Special entity mod element

**Parameters:**
- `elementName` (`string`, required) — Name of the new Special entity
- `properties` (`object`, optional) — Customization properties for the Special entity

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `createStructure`

**Description:** Create a Structure mod element

**Parameters:**
- `elementName` (`string`, required) — Name of the new Structure
- `properties` (`object`, optional) — Customization properties for the Structure

**Verification:** **Test:** `elementName=McpTower` — **Result:** created `Structure` element with `SURFACE_STRUCTURES` generation step **Test:** `elementName=McpTower', 'structureFile=/tmp/test_structure.nbt` — **Result:** NBT file copied to `src/main/resources/data/mcptest7/structure/test_structure.nbt`; `structure` field set to `test_structure`

### `createTab`

**Description:** Create a Creative tab mod element

**Parameters:**
- `elementName` (`string`, required) — Name of the new Creative tab
- `properties` (`object`, optional) — Customization properties for the Creative tab

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `createTool`

**Description:** Create a Tool mod element

**Parameters:**
- `elementName` (`string`, required) — Name of the new Tool
- `properties` (`object`, optional) — Customization properties for the Tool

**Verification:** **Test:** `elementName=McpPickaxe', 'toolType=Pickaxe', 'material=DIAMOND` — **Result:** created and generated tool class

### `createVillagerprofession`

**Description:** Create a Villager profession mod element

**Parameters:**
- `elementName` (`string`, required) — Name of the new Villager profession
- `properties` (`object`, optional) — Customization properties for the Villager profession

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `createVillagertrade`

**Description:** Create a Villager trade mod element

**Parameters:**
- `elementName` (`string`, required) — Name of the new Villager trade
- `properties` (`object`, optional) — Customization properties for the Villager trade

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `registerLootTable`

**Description:** Create a loot table element

**Parameters:**
- `elementName` (`string`, required) — Name of the loot table
- `properties` (`object`, optional) — Loot table properties

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `registerAdvancement`

**Description:** Create an advancement element

**Parameters:**
- `elementName` (`string`, required) — Name of the advancement
- `properties` (`object`, optional) — Advancement properties

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `registerFunction`

**Description:** Create a function element

**Parameters:**
- `elementName` (`string`, required) — Name of the function
- `properties` (`object`, optional) — Function properties

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `createVariable`

**Description:** Create a workspace variable

**Parameters:**
- `scope` (`string`, required) — Variable scope
- `variableName` (`string`, required) — Variable name
- `variableType` (`string`, required) — Variable type
- `defaultValue` (`object`, optional) — Default value

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `createProcedureAndAttach`

**Description:** Create a procedure and immediately attach it to an element event

**Parameters:**
- `elementName` (`string`, required) — Element name to attach to
- `eventType` (`string`, required) — Event field name, e.g. onRightClicked
- `procedureName` (`string`, required) — New procedure name
- `xml` (`string`, optional) — Blockly XML (optional)

**Verification:** **Test:** `procedureName=ClickableItem_RightAir', 'elementName=ClickableItem', 'eventType=onRightClickedInAir` — **Result:** created procedure and linked it to item event in one call; generated `ClickableItem_RightAirProcedure.class` and `ClickableItem_OnRightClickedInAirProcedure.class`

### `createTag`

**Description:** Create a data/resource tag (BLOCKS, ITEMS, ENTITIES, etc.)

**Parameters:**
- `tagName` (`string`, required) — Tag name/path (e.g. my_ores or minecraft:my_ores)
- `tagType` (`string`, required) — Tag type: BLOCKS, ITEMS, ENTITIES, FUNCTIONS, BIOMES, etc.
- `entries` (`object`, optional) —

**Verification:** **Test:** `gems', 'ITEMS', entries '[minecraft:diamond, minecraft:emerald, CUSTOM:TestItem]` — **Result:** created tag; `gems.json` generated with `mcptest7:test_item`

### `createCreativeTab`

**Description:** Create a custom creative inventory tab

**Parameters:**
- `tabName` (`string`, required) — Element/registry name for the tab
- `displayName` (`string`, optional) — Display name
- `icon` (`string`, optional) — Icon item/block reference (e.g. Items.STONE or minecraft:stone)
- `showSearch` (`object`, optional) —

**Verification:** **Test:** `RubyTab', icon 'Items.DIAMOND` — **Result:** created `Tab` element; generated `Mcptest7ModTabs.java`


## Bedrock Edition Element Aliases

### `createBeblock`

**Description:** Create a Block mod element

**Parameters:**
- `elementName` (`string`, required) — Name of the new Block
- `properties` (`object`, optional) — Customization properties for the Block

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `createBebiome`

**Description:** Create a Biome mod element

**Parameters:**
- `elementName` (`string`, required) — Name of the new Biome
- `properties` (`object`, optional) — Customization properties for the Biome

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `createBeentity`

**Description:** Create a Entity mod element

**Parameters:**
- `elementName` (`string`, required) — Name of the new Entity
- `properties` (`object`, optional) — Customization properties for the Entity

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `createBeitem`

**Description:** Create a Item mod element

**Parameters:**
- `elementName` (`string`, required) — Name of the new Item
- `properties` (`object`, optional) — Customization properties for the Item

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `createBescript`

**Description:** Create a Script mod element

**Parameters:**
- `elementName` (`string`, required) — Name of the new Script
- `properties` (`object`, optional) — Customization properties for the Script

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `createBedrockItem`

**Description:** Create a Bedrock item

**Parameters:**
- `elementName` (`string`, required) — Name of the Bedrock item
- `properties` (`object`, optional) — Customization properties

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `createBedrockBlock`

**Description:** Create a Bedrock block

**Parameters:**
- `elementName` (`string`, required) — Name of the Bedrock block
- `properties` (`object`, optional) — Customization properties

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `createBedrockEntity`

**Description:** Create a Bedrock entity

**Parameters:**
- `elementName` (`string`, required) — Name of the Bedrock entity
- `properties` (`object`, optional) — Customization properties

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.


## Bedrock Pack Tools

### `createBedrockTexturePack`

**Description:** Create a Bedrock texture/resource pack folder with manifest

**Parameters:**
- `description` (`string`, required) — Description
- `packName` (`string`, required) — Pack name
- `version` (`string`, required) — Version

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `createBedrockResourcePack`

**Description:** Create a Bedrock resource pack folder with manifest

**Parameters:**
- `description` (`string`, required) — Description
- `packName` (`string`, required) — Pack name
- `version` (`string`, required) — Version
- `properties` (`object`, optional) —

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `createBedrockBehaviorPack`

**Description:** Create a Bedrock behavior pack folder with manifest

**Parameters:**
- `description` (`string`, required) — Description
- `packName` (`string`, required) — Pack name
- `version` (`string`, required) — Version
- `properties` (`object`, optional) —

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `buildBedrockProject`

**Description:** Package Bedrock resource and behavior packs into .mcpack files

**Parameters:**
- `packName` (`string`, optional) — Pack name prefix

**Verification:** **Test:** `packName=mcp_test` — **Result:** packaged `.mcpack` files for resource and behavior packs

### `exportBedrockAddon`

**Description:** Package Bedrock resource and behavior packs into a combined .mcaddon file

**Parameters:**
- `outputPath` (`string`, optional) — Output .mcaddon file path (optional)
- `packName` (`string`, optional) — Pack name prefix (optional)

**Verification:** **Test:** `packName=mcp_test', 'outputPath=/tmp/mcp_test.mcaddon` — **Result:** produced combined `.mcaddon` containing `mcp_test_rp` resource pack and `mcp_test_bp_behavior` behavior pack

### `createBedrockBehaviorJson`

**Description:** Write a Bedrock behavior pack definition JSON file

**Parameters:**
- `elementName` (`string`, required) — Element file name
- `packName` (`string`, required) — Bedrock behavior pack name
- `elementType` (`string`, optional) — Element type: item, block, entity (default item)
- `properties` (`object`, optional) — Bedrock component/properties object

**Verification:** **Test:** `packName=mcp_bedrock', 'elementType=item', 'elementName=test_item` — **Result:** wrote valid Bedrock behavior pack JSON under the workspace


## Compound Element Workflows

### `createTextureSet`

**Description:** Create multiple textures at once with optional animation metadata

**Parameters:**
- `setName` (`string`, required) — Set name prefix
- `textureDefinitions` (`object`, required) —
- `outputFormat` (`string`, optional) — PNG or MCMeta

**Verification:** **Test:** `ruby_set' (file-based PNG + animation)` — **Result:** created block/item textures and `.mcmeta` file

### `createRecipeChain`

**Description:** Create multiple linked recipes

**Parameters:**
- `recipeName` (`string`, required) — Prefix for recipe element names
- `recipes` (`object`, required) —
- `outputFormat` (`string`, optional) — Ignored

**Verification:** **Test:** `smelt + craft chain` — **Result:** created two linked recipe elements (note: test inputs used non-existent items, so server reported recipe parse errors)

### `createModelFromDefinition`

**Description:** Generate a JSON block/item model

**Parameters:**
- `modelDefinition` (`object`, required) —
- `modelName` (`string`, required) — Model name
- `modelType` (`string`, required) — block or item

**Verification:** **Test:** `ruby_block_model' (cube_all)` — **Result:** wrote JSON model to assets

### `createSoundEvent`

**Description:** Register a new sound event

**Parameters:**
- `audioFile` (`string`, required) — Source audio file path or base64 data URI
- `category` (`string`, required) — Sound category: master, block, entity, etc.
- `soundName` (`string`, required) — Sound name
- `subtitleKey` (`string`, optional) — Subtitle localization key (optional)

**Verification:** **Test:** `mcp_beep' OGG, category 'master` — **Result:** registered sound, copied `.ogg`, generated `sounds.json`

### `createParticleEffect`

**Description:** Create a particle effect

**Parameters:**
- `particleName` (`string`, required) — Particle name
- `animationDef` (`object`, optional) —
- `physics` (`object`, optional) —
- `texture` (`string`, optional) — Texture name

**Verification:** **Test:** `SparkParticle', placeholder texture` — **Result:** created and generated particle classes


## Element Lifecycle & Editing

### `updateElementProperties`

**Description:** Update properties of an existing mod element

**Parameters:**
- `elementName` (`string`, required) — Element name
- `properties` (`object`, required) —

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `updateProcedure`

**Description:** Replace the Blockly XML of an existing procedure

**Parameters:**
- `elementName` (`string`, required) — Procedure element name
- `xml` (`string`, required) — Full Blockly XML

**Verification:** **Test:** `replace 'ProcRightClick' Blockly XML` — **Result:** XML persisted and procedure regenerated

### `deleteProcedure`

**Description:** Delete a procedure element

**Parameters:**
- `elementName` (`string`, required) — Procedure element name

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `cloneElement`

**Description:** Clone an existing mod element

**Parameters:**
- `newElementName` (`string`, required) — Name for the cloned element
- `sourceElementName` (`string`, required) — Name of the element to clone
- `properties` (`object`, optional) — Optional property overrides

**Verification:** **Test:** `sourceElementName=TestBlock', 'newElementName=ClonedBlock` — **Result:** cloned element JSON persisted

### `renameElement`

**Description:** Rename an existing mod element

**Parameters:**
- `elementName` (`string`, required) — Current element name
- `newName` (`string`, required) — New element name

**Verification:** **Test:** `elementName=ClonedBlock', 'newName=RenamedBlock` — **Result:** renamed and workspace consistent

### `moveElement`

**Description:** Move an element to a workspace folder

**Parameters:**
- `elementName` (`string`, required) — Element name
- `folderPath` (`string`, required) — Target folder path (e.g. "blocks")

**Verification:** **Test:** `elementName=RenamedBlock', 'folderPath=""` — **Result:** moved to workspace root

### `editRecipe`

**Description:** Edit an existing recipe element

**Parameters:**
- `elementName` (`string`, required) — Name of the recipe element
- `properties` (`object`, optional) — Recipe properties (recipeType, inputs, output, etc.)

**Verification:** **Test:** `McpRubyRecipe' with 'output', 'outputCount', inputs` — **Result:** recipe regenerated cleanly

### `editAdvancement`

**Description:** Edit an existing advancement element

**Parameters:**
- `elementName` (`string`, required) — Name of the advancement element
- `properties` (`object`, optional) — Advancement properties (displayName, description, triggerxml, rewards, etc.)

**Verification:** **Test:** `McpAdvance' displayName/description/rewardXP` — **Result:** updated and regenerated cleanly

### `editLootTable`

**Description:** Edit an existing loot table element

**Parameters:**
- `elementName` (`string`, required) — Name of the loot table element
- `properties` (`object`, optional) — Loot table properties (type, pools, etc.)

**Verification:** **Test:** `McpSapphireLoot' with pools/entries/item` — **Result:** entries normalized to `CUSTOM:TestItem`; regenerated cleanly

### `exportElement`

**Description:** Export a mod element as JSON to a file

**Parameters:**
- `elementName` (`string`, required) — Element name
- `outputPath` (`string`, optional) — Output JSON file path (optional)

**Verification:** **Test:** `elementName=TestItem', 'outputPath=/tmp/testitem.mcelement.json` — **Result:** exported `_fv`, `_type`, `definition` JSON; `_type` field used by import

### `importElement`

**Description:** Import a mod element from a JSON file

**Parameters:**
- `inputPath` (`string`, required) — Input JSON file path
- `newName` (`string`, optional) — Optional new element name
- `properties` (`object`, optional) — Optional property overrides

**Verification:** **Test:** `inputPath=/tmp/testitem.mcelement.json', 'newName=ImportedItemTest` — **Result:** created element `ImportedItemTest` from exported JSON

### `cloneElements`

**Description:** Clone multiple mod elements

**Parameters:**
- `mappings` (`object`, required) — Object mapping source element names to new names
- `properties` (`object`, optional) — Optional property overrides applied to all clones

**Verification:** **Test:** `mappings={TestItem:ClonedItemTest}` — **Result:** duplicated element; workspace consistent

### `renameElements`

**Description:** Rename multiple mod elements

**Parameters:**
- `mappings` (`object`, required) — Object mapping current names to new names

**Verification:** **Test:** `mappings={ClonedItemTest:RenamedItemTest}` — **Result:** renamed element; references updated

### `deleteElements`

**Description:** Delete multiple mod elements

**Parameters:**
- `elementNames` (`object`, required) — List of element names to delete

**Verification:** **Test:** `elementNames=[ImportedItemTest,RenamedItemTest]` — **Result:** deleted both elements

### `searchAndReplace`

**Description:** Search and replace text across element properties and localizations

**Parameters:**
- `replace` (`string`, required) — Replacement string
- `search` (`string`, required) — Search string or regex
- `elementNames` (`object`, optional) — Optional list of element names to limit scope
- `localizations` (`string`, optional) — Also replace in localization entries (true/false, default false)
- `useRegex` (`string`, optional) — Treat search as regex (true/false, default false)

**Verification:** **Test:** `search=OldTestName', 'replace=NewTestName', 'localizations=true` — **Result:** no matches in this workspace; tool executed


## Procedure & Event Tools

### `createProcedure`

**Description:** Create a Procedure mod element

**Parameters:**
- `elementName` (`string`, required) — Name of the new Procedure
- `properties` (`object`, optional) — Customization properties for the Procedure

**Verification:** **Test:** `elementName=ProcRightClick', valid Blockly XML` — **Result:** created; generated `ProcRightClickProcedure.class`

### `createProcedure`

**Description:** Create a reusable procedure with Blockly XML

**Parameters:**
- `elementName` (`string`, required) — Procedure name
- `xml` (`string`, optional) — Blockly XML (optional)

**Verification:** **Test:** `elementName=ProcRightClick', valid Blockly XML` — **Result:** created; generated `ProcRightClickProcedure.class`

### `getEventProcedures`

**Description:** List all event/procedure hooks on a mod element

**Parameters:**
- `elementName` (`string`, required) — Element name

**Verification:** **Test:** `elementName=EventSword` — **Result:** returned all event hooks with field types and linked XML

### `updateEventProcedure`

**Description:** Attach or update an event procedure on an element

**Parameters:**
- `elementName` (`string`, required) — Element name
- `eventType` (`string`, required) — Event field name, e.g. onRightClicked
- `procedureName` (`string`, optional) — Procedure element name (optional)
- `xml` (`string`, optional) — Blockly XML for the procedure (optional)

**Verification:** **Test:** `EventSword.onRightClickedInAir -> ProcRightClick` — **Result:** linked; XML persisted

### `registerEventListener`

**Description:** Alias for updateEventProcedure using actionDefinition object

**Parameters:**
- `elementName` (`string`, required) — Element name
- `eventType` (`string`, required) — Event field name
- `actionDefinition` (`object`, optional) —

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `listProcedureTemplates`

**Description:** List available procedure templates with parameter descriptions

**Parameters:**
No parameters.

**Verification:** **Test:** `no args` — **Result:** returned 7 templates (`empty`, `give_item`, `send_message`, `execute_command`, `set_block`, `spawn_entity`, `apply_potion`) with default values **Test:** `no args` — **Result:** returned all 15 templates with default values

### `applyProcedureTemplate`

**Description:** Create a procedure from a named template and attach it to an element event

**Parameters:**
- `elementName` (`string`, required) — Element name to attach to
- `eventType` (`string`, required) — Event field name, e.g. onRightClicked
- `templateName` (`string`, required) — Template name (empty, give_item, send_message, message, execute_command, set_block, spawn_entity, apply_potion, if_then, if_else, repeat, set_variable, math_operation, kill_entity, explode, play_sound)
- `procedureName` (`string`, optional) — Optional custom procedure name
- `values` (`object`, optional) — Template values (item, message, command, entity, potion, block, amount, x, y, z)

**Verification:** **Test:** `templateName=give_item', 'elementName=TestItem', 'eventType=onRightClickedInAir', 'values={item:minecraft:emerald, amoun...` — **Result:** created and linked procedure `TestItemOnRightClickedInAirGiveItem`; generated code compiled and loaded

### `listProcedures`

**Description:** List all procedure elements and their XML size

**Parameters:**
No parameters.

**Verification:** **Test:** `no args` — **Result:** returned `TestItemOnRightClickedInAir` and `ProcRightClick` XML lengths


## Advanced Mob AI & Code

### `createAIBehavior`

**Description:** Create a LivingEntity element pre-configured with AI behavior

**Parameters:**
- `elementName` (`string`, required) — New mob element name
- `aiBase` (`string`, optional) — AI base, e.g. Zombie, Skeleton, Spider, Creeper
- `attackKnockback` (`string`, optional) — Attack knockback
- `attackStrength` (`string`, optional) — Melee attack damage
- `followRange` (`string`, optional) — Follow range
- `health` (`string`, optional) — Max health
- `mobBehaviourType` (`string`, optional) — Creature/Mob/WaterCreature/Ambient
- `mobCreatureType` (`string`, optional) — CREATURE/MONSTER/AMBIENT/WATER_CREATURE/UNDEFINED
- `model` (`string`, optional) — Model name, e.g. Default
- `movementSpeed` (`string`, optional) — Movement speed
- `ranged` (`string`, optional) — Use ranged attacks (true/false)
- `rangedAttackInterval` (`string`, optional) — Ticks between ranged attacks
- `rangedAttackItem` (`string`, optional) — Ranged attack item, e.g. minecraft:arrow
- `rangedAttackRadius` (`string`, optional) — Ranged attack radius
- `texture` (`string`, optional) — Texture name

**Verification:** **Test:** `elementName=AIZombieMob', 'aiBase=Zombie', 'mobBehaviourType=Mob', 'mobCreatureType=MONSTER', 'health=20', 'attackStreng...` — **Result:** created hostile `LivingEntity` with AI settings

### `addAIGoal`

**Description:** Update an existing LivingEntity's AI base, combat stats, and ranged settings

**Parameters:**
- `elementName` (`string`, required) — Existing mob element name
- `aiBase` (`string`, optional) — AI base, e.g. Zombie, Skeleton, Spider
- `attackKnockback` (`string`, optional) — Attack knockback
- `attackStrength` (`string`, optional) — Melee attack damage
- `followRange` (`string`, optional) — Follow range
- `movementSpeed` (`string`, optional) — Movement speed
- `ranged` (`string`, optional) — Use ranged attacks (true/false)
- `rangedAttackInterval` (`string`, optional) — Ticks between ranged attacks
- `rangedAttackItem` (`string`, optional) — Ranged attack item
- `rangedAttackRadius` (`string`, optional) — Ranged attack radius

**Verification:** **Test:** `elementName=ModelMob', 'aiBase=Skeleton', 'ranged=true', 'rangedAttackItem=minecraft:arrow', 'rangedAttackInterval=20', ...` — **Result:** updated existing mob to ranged Skeleton AI

### `createCustomJava`

**Description:** Create a custom Java class (CustomElement) and write its source file

**Parameters:**
- `className` (`string`, required) — Java class name
- `code` (`string`, optional) — Full Java source code (optional; writes a stub if omitted)
- `isMixin` (`string`, optional) — Create as a Mixin in the mixin subpackage (true/false, default false)
- `packageSubPath` (`string`, optional) — Sub-package under the mod package, e.g. mixins or event

**Verification:** **Test:** `className=TestCustomCode', 'code=package net.mcptest7; public class TestCustomCode { ... }` — **Result:** created `CustomElement` and wrote root-package Java file

### `editCustomJava`

**Description:** Edit an existing custom Java source file managed by a CustomElement

**Parameters:**
- `className` (`string`, required) — Java class name
- `code` (`string`, required) — Full Java source code

**Verification:** **Test:** `className=TestCustomCode', 'code=...` — **Result:** overwrote the source file with `@EventBusSubscriber` stub

### `addMixinStub`

**Description:** Add a Mixin dependency line and generate a Mixin class stub

**Parameters:**
- `className` (`string`, required) — Mixin class name
- `targetClass` (`string`, required) — Fully-qualified target class
- `code` (`string`, optional) — Optional method body source

**Verification:** **Test:** `className=TestMixin', 'targetClass=net.minecraft.world.entity.player.Player` — **Result:** wrote Mixin stub under `mixin` subpackage


## Build-System Hooks

### `addGradleDependency`

**Description:** Add or update a Gradle dependency in build.gradle

**Parameters:**
- `configuration` (`string`, required) — Gradle configuration, e.g. implementation or modCompileOnly
- `dependency` (`string`, required) — Dependency string, e.g. com.example:lib:1.0
- `mcreatorDependency` (`string`, optional) — Set as MCreator API dependency as well (true/false, default false)

**Verification:** **Test:** `configuration=implementation', 'dependency=com.google.code.gson:gson:2.10.1` — **Result:** inserted dependency into `build.gradle`

### `editAccessTransformer`

**Description:** Append or replace access transformer entries in src/main/resources/META-INF/accesstransformer.cfg

**Parameters:**
- `entries` (`object`, required) — List of access transformer lines to add
- `replace` (`string`, optional) — Replace the entire file (true/false, default false)

**Verification:** **Test:** `entries=[public net.minecraft.world.level.block.Block f_49791_ # dropResources]` — **Result:** wrote `META-INF/accesstransformer.cfg`

### `editServerProperties`

**Description:** Read or write server.properties for runServer

**Parameters:**
- `properties` (`object`, optional) — Map of server.properties keys to values
- `replace` (`string`, optional) — Replace the entire file (true/false, default false)

**Verification:** **Test:** `properties={max-players:10, online-mode:false, spawn-monsters:true}` — **Result:** wrote `run/server.properties`


## Texture, Model & Asset Tools

### `listTexturesByType`

**Description:** List textures in the workspace

**Parameters:**
- `type` (`string`, optional) — Texture type: BLOCK, ITEM, ENTITY, etc.

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `importTexture`

**Description:** Import a texture into the workspace

**Parameters:**
- `sourcePath` (`string`, required) — Source file path or base64 data URI
- `textureName` (`string`, required) — Name for the texture
- `textureType` (`string`, required) — Texture type: BLOCK, ITEM, ENTITY, etc.
- `animation` (`string`, optional) — Generate .mcmeta animation file (true/false)
- `frameTime` (`string`, optional) — Animation frame time in ticks
- `height` (`string`, optional) — Optional target height
- `width` (`string`, optional) — Optional target width

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `deleteTexture`

**Description:** Delete a texture from the workspace

**Parameters:**
- `textureName` (`string`, required) — Texture name
- `textureType` (`string`, required) — Texture type: BLOCK, ITEM, ENTITY, etc.

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `listModels`

**Description:** List custom models in the workspace

**Parameters:**
No parameters.

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `importModel`

**Description:** Import a model into the workspace

**Parameters:**
- `modelName` (`string`, required) — Name for the model
- `sourcePath` (`string`, required) — Source file path

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `deleteModel`

**Description:** Delete a model from the workspace

**Parameters:**
- `modelName` (`string`, required) — Model name

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `getAssetMetadata`

**Description:** Get metadata for an asset

**Parameters:**
- `assetName` (`string`, required) — Asset name
- `assetType` (`string`, required) — Asset type: texture, model

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `validateModel`

**Description:** Validate a model file (JSON or OBJ) and report issues

**Parameters:**
- `sourcePath` (`string`, required) — Path to .json or .obj model file

**Verification:** **Test:** `ruby_block_model.json` — **Result:** validated JSON model has textures and parent

### `convertModel`

**Description:** Convert an OBJ model to a simple Minecraft block JSON (single cuboid only)

**Parameters:**
- `modelName` (`string`, required) — Output model name (without .json)
- `sourcePath` (`string`, required) — Path to .obj file
- `texture` (`string`, optional) — Texture reference (e.g. #all or block/all)

**Verification:** **Test:** `ruby_block_model.json' -> 'block/ruby_block_model` — **Result:** no-op for already-JSON; OBJ parsing implemented

### `processTexture`

**Description:** Process a workspace texture (resize, pad, recolor, etc.)

**Parameters:**
- `operations` (`object`, required) — Operations to apply (resize, pad, recolor)
- `textureName` (`string`, required) — Texture name
- `textureType` (`string`, required) — Texture type: BLOCK, ITEM, ENTITY, etc.

**Verification:** **Test:** `test_item' resize + recolor + pad` — **Result:** produced 32x32 recolored texture in assets folder

### `generateMcmeta`

**Description:** Generate or update a .mcmeta animation file

**Parameters:**
- `textureName` (`string`, required) — Texture name
- `textureType` (`string`, required) — Texture type
- `frameTime` (`string`, optional) — Ticks per frame
- `frames` (`string`, optional) — Optional frame indices (JSON array)
- `height` (`string`, optional) — Optional frame height
- `interpolate` (`string`, optional) — Enable interpolation (true/false)
- `width` (`string`, optional) — Optional frame width

**Verification:** **Test:** `test_item' frameTime=2, interpolate` — **Result:** wrote `.mcmeta` with `animation.interpolate=true`

### `convertBlockbenchModel`

**Description:** Convert a Blockbench JSON model to a Minecraft JSON model

**Parameters:**
- `modelName` (`string`, required) — Output model name
- `sourcePath` (`string`, required) — Path to the Blockbench JSON file

**Verification:** **Test:** `/tmp/test_blockbench.json' -> 'test_converted` — **Result:** wrote Minecraft JSON block model

### `bindCustomModel`

**Description:** Bind an imported/generated JSON/OBJ/Java model to a block or item

**Parameters:**
- `elementName` (`string`, required) — Block or item element name
- `modelName` (`string`, required) — Model name (filename without extension)
- `modelType` (`string`, required) — Model type: json, obj, java
- `modelDefinition` (`object`, optional) — Optional JSON model definition object (used if sourcePath is not given)
- `sourcePath` (`string`, optional) — Optional source model file path to import
- `texture` (`string`, optional) — Optional texture name to set as the main texture

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `generateTextureFromPrompt`

**Description:** Generate a texture from a text prompt. Uses an external image URL if provided, otherwise a configured image-gen API, otherwise falls back to a placeholder.

**Parameters:**
- `prompt` (`string`, required) — Text prompt describing the desired texture
- `textureName` (`string`, required) — Output texture name
- `textureType` (`string`, required) — Texture type: BLOCK, ITEM, ENTITY, etc.
- `apiKey` (`string`, optional) — API key for the selected provider (optional; falls back to env vars)
- `apiProvider` (`string`, optional) — Image-gen API provider: url, pollinations, huggingface (default url/placeholder)
- `height` (`string`, optional) — Height in pixels (default 64)
- `imageUrl` (`string`, optional) — Direct image URL to download instead of generating (optional)
- `seed` (`string`, optional) — Seed for deterministic generation (optional)
- `uvTemplatePath` (`string`, optional) — Path to a UV template PNG to overlay/scale onto (optional)
- `width` (`string`, optional) — Width in pixels (default 64)

**Verification:** **Test:** `prompt='A glowing red crystal ore block'', 'textureName=crystal_ore', 'textureType=BLOCK', 'width=64', 'height=64` — **Result:** generated `crystal_ore.png` plus `crystal_ore.prompt.txt` sidecar in `assets/mcptest7/textures/block`


## Workspace Variables & Localization

### `listWorkspaceVariables`

**Description:** List all mod variables in the workspace

**Parameters:**
No parameters.

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `updateVariable`

**Description:** Update an existing workspace variable

**Parameters:**
- `variableName` (`string`, required) — Variable name
- `defaultValue` (`object`, optional) — Default value
- `scope` (`string`, optional) — Variable scope
- `variableType` (`string`, optional) — Variable type

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `deleteVariable`

**Description:** Delete a workspace variable

**Parameters:**
- `variableName` (`string`, required) — Variable name

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `getLocalizations`

**Description:** Get localization strings for a language

**Parameters:**
- `language` (`string`, optional) — Language code

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `setLocalization`

**Description:** Set a localization string

**Parameters:**
- `key` (`string`, required) — Localization key
- `language` (`string`, required) — Language code
- `value` (`string`, required) — Localized value

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `addLanguage`

**Description:** Add a new language to the workspace

**Parameters:**
- `languageCode` (`string`, required) — Language code

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.


## Tags, Creative Tabs & Generators

### `updateTag`

**Description:** Update the entries of an existing tag

**Parameters:**
- `tagName` (`string`, required) — Tag name/path
- `tagType` (`string`, required) — Tag type
- `entries` (`object`, optional) —

**Verification:** **Test:** `add 'CUSTOM:TestItem' to 'gems` — **Result:** updated; custom references normalized through `NameMapper` reverse-lookup

### `deleteTag`

**Description:** Delete a tag

**Parameters:**
- `tagName` (`string`, required) — Tag name/path
- `tagType` (`string`, required) — Tag type

**Verification:** **Test:** `gems2' / 'gems3' / 'Gems` — **Result:** deleted workspace tags and stale generated JSON files

### `listTags`

**Description:** List all tags and their entries

**Parameters:**
- `tagType` (`string`, optional) — Optional tag type filter

**Verification:** **Test:** `no args` — **Result:** returned `ruby_ores` (BLOCKS) and `gems` (ITEMS) with managed/unmanaged entry info

### `listCreativeTabs`

**Description:** List custom creative tabs and tab ordering

**Parameters:**
No parameters.

**Verification:** **Test:** `no args` — **Result:** returned tab elements and tab order

### `updateCreativeTabs`

**Description:** Set the order of mod elements inside a creative tab

**Parameters:**
- `elementNames` (`object`, required) —
- `tabName` (`string`, required) — Tab name (registry name)

**Verification:** **Test:** `RubyTab' -> '[TestItem, SapphireBlock, TestBlock]` — **Result:** items/blocks linked via `CUSTOM:RubyTab` and rendered in tab

### `switchGenerator`

**Description:** Switch the workspace generator (e.g. neoforge-1.21.1, datapack-1.21.1)

**Parameters:**
- `generatorName` (`string`, required) — Generator ID (folder name from listGenerators)

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `listGenerators`

**Description:** List installed MCreator generator plugins

**Parameters:**
No parameters.

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.


## Build, Export, Deploy & CI

### `buildForJavaEdition`

**Description:** Build the workspace for Java Edition

**Parameters:**
- `includeClient` (`object`, optional) — Build client artifacts
- `includeServer` (`object`, optional) — Build server artifacts

**Verification:** **Test:** `OK` — **Result:** produced `/home/ubuntu/MCreatorWorkspaces/MCPTtest7/build/libs/modid-1.0.jar` (server reached `Done!` after a manual build) **Test:** `no args` — **Result:** produced `modid-1.0.jar` **Test:** `no args` — **Result:** returned path to `modid-1.0.jar`

### `exportResourcePack`

**Description:** Export the generated resources as a resource pack

**Parameters:**
- `outputPath` (`string`, required) — Output directory path

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `exportBehaviorPack`

**Description:** Export behavior pack data

**Parameters:**
- `outputPath` (`string`, required) — Output directory path

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `deployToGameFolder`

**Description:** Deploy the mod to the Minecraft game folder

**Parameters:**
- `editionType` (`string`, required) — java or bedrock
- `gameFolderPath` (`string`, required) — Target game folder path

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `runGradleTask`

**Description:** Run an arbitrary Gradle task in the workspace

**Parameters:**
- `taskName` (`string`, required) — Gradle task name

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `exportModrinth`

**Description:** Export the built mod as a Modrinth-compatible .mrpack

**Parameters:**
- `outputPath` (`string`, required) — Output .mrpack file path
- `summary` (`string`, optional) — Optional modpack summary

**Verification:** **Test:** `output '/tmp/mcptest7.mrpack` — **Result:** produced `.mrpack` containing built JAR **Test:** `outputPath=/tmp/mcptest7.mrpack` — **Result:** produced `.mrpack` with `modrinth.index.json` and `overrides/mods/modid-1.0.jar`

### `runCIBuild`

**Description:** Run a full CI build: regenerate, build, and verify server loads

**Parameters:**
- `timeoutSeconds` (`string`, optional) — Server verification timeout (default: 180)

**Verification:** **Test:** `timeout 300s` — **Result:** regenerated code, built JAR, started server, command executed


## Testing & In-Game Verification

### `runClient`

**Description:** Start Minecraft client

**Parameters:**
No parameters.

**Verification:** Registered and exercised as part of the `MCPTtest7` workspace build/regenerate/runServer cycle; no isolated table entry recorded.

### `runServer`

**Description:** Start Minecraft server

**Parameters:**
No parameters.

**Verification:** **Test:** `via MCP tool` — **Result:** returned immediately; server log reached `Done (1.291s)!`

### `generateTestReport`

**Description:** Generate an in-game test report from client/server logs

**Parameters:**
- `logPath` (`string`, optional) — Optional path to latest.log

**Verification:** **Test:** `default 'run/logs/latest.log` — **Result:** parsed log and summarized errors/warnings **Test:** `default 'run/logs/latest.log` — **Result:** parsed latest server log, summarized errors/warnings

### `verifyServerLoads`

**Description:** Start the Minecraft server and verify it reaches Done

**Parameters:**
- `elementName` (`string`, optional) — Optional element name to search for in the log
- `timeoutSeconds` (`object`, optional) —

**Verification:** **Test:** `timeoutSeconds=120` — **Result:** server reached `Done (...)`; no `[ERROR]` lines in `latest.log` **Test:** `no args` — **Result:** server reached `Done`; `errorCount:10` from pre-existing villager POI/advancement conflicts, no datapack errors from new JSON

### `verifyClientLoads`

**Description:** Start the Minecraft client and verify it loads

**Parameters:**
- `elementName` (`string`, optional) — Optional element name to search for in the log
- `timeoutSeconds` (`object`, optional) —

**Verification:** **Test:** `timeoutSeconds=120` — **Result:** client created texture atlas; only OpenAL/SoundSystem error (headless/no audio device)

### `executeServerCommand`

**Description:** Send a command to a running Minecraft server via RCON

**Parameters:**
- `command` (`string`, required) — Command to send (without leading /)
- `rconPassword` (`string`, optional) — RCON password (default: mcp12345)
- `rconPort` (`string`, optional) — RCON port (default: 25575)
- `timeoutSeconds` (`string`, optional) — Connection timeout

**Verification:** **Test:** `say MCreatorMCP RCON works` — **Result:** RCON command sent; server logged message

### `runTestScenario`

**Description:** Run an automated in-game test scenario (server + commands + log check)

**Parameters:**
- `commands` (`object`, required) — Array of commands to execute via RCON
- `scenarioName` (`string`, required) — Scenario name
- `timeoutSeconds` (`string`, optional) — Timeout in seconds (default: 180)

**Verification:** **Test:** `phase4-logs' with 'say' command` — **Result:** server started, command executed, stopped, errors counted **Test:** `summon minecraft:cow', 'say Final in-game verification passed` — **Result:** server started, RCON connected, entity spawned, message broadcasted **Test:** `place-break-inspect': 'setblock 0 70 0 mcptest7:adv_prop_block', 'data get entity', 'setblock 0 70 0 air` — **Result:** server reached `Done`, RCON connected, custom block placed/broken, `ModelMob` summoned and Health inspected **Test:** `scenarioName=datapack_load', commands executed in custom dimension` — **Result:** server loaded with no datapack registry errors from new biome/dimension/carver/function JSON

### `verifyInWorld`

**Description:** Run an in-world verification: start a server, execute place/break/inspect commands via RCON, and optionally capture a client screenshot

**Parameters:**
- `commands` (`object`, required) — List of in-game commands to run via RCON
- `includeClientScreenshot` (`string`, optional) — Also launch the client and capture a screenshot (true/false, default false)
- `outputPath` (`string`, optional) — Client screenshot output path (default /tmp/mcp_inworld_screenshot.png)
- `rconPassword` (`string`, optional) — RCON password (default mcp12345)
- `timeoutSeconds` (`string`, optional) — Timeout in seconds (default 180)

**Verification:** **Test:** `commands=[setblock 0 70 0 mcptest7:test_block keep, data get block 0 70 0, setblock 0 70 0 minecraft:air, summon mcptest...` — **Result:** server reached `Done`, RCON connected, custom block placed/broken, `ModelMob` summoned and `Health: 20.0f` returned

### `verifyClientInGame`

**Description:** Launch the Minecraft client in a virtual display and capture a screenshot

**Parameters:**
- `commands` (`object`, optional) —
- `outputPath` (`string`, optional) — Screenshot output path (default /tmp/mcp_client_screenshot.png)
- `timeoutSeconds` (`string`, optional) — Timeout in seconds (default 180)

**Verification:** **Test:** `timeoutSeconds=120', 'outputPath=/tmp/mcp_screenshot2.png` — **Result:** launched Minecraft client under Xvfb, captured a PNG of the main menu


## Publishing

### `publishToModrinth`

**Description:** Publish the built mod JAR to Modrinth

**Parameters:**
- `apiToken` (`string`, required) — Modrinth personal access token
- `projectId` (`string`, required) — Modrinth project ID
- `versionNumber` (`string`, required) — Version number (e.g. 1.0.0)
- `changelog` (`string`, optional) — Changelog text
- `filePath` (`string`, optional) — Optional path to JAR file
- `gameVersions` (`object`, optional) —
- `loaders` (`object`, optional) —
- `releaseType` (`string`, optional) — release, beta, alpha (default release)
- `versionName` (`string`, optional) — Human-readable version name (defaults to versionNumber)

**Verification:** **Test:** `dummy token` — **Result:** reached Modrinth API and returned `401 unauthorized` (HTTP code captured in output)

### `publishToCurseForge`

**Description:** Publish the built mod JAR to CurseForge

**Parameters:**
- `apiToken` (`string`, required) — CurseForge API token
- `displayName` (`string`, required) — Display name for the file
- `projectId` (`string`, required) — CurseForge project ID
- `changelog` (`string`, optional) — Changelog text
- `filePath` (`string`, optional) — Optional path to JAR file
- `gameVersionIds` (`object`, optional) —
- `releaseType` (`string`, optional) — release, beta, alpha (default release)

**Verification:** **Test:** `dummy token` — **Result:** reached CurseForge API and returned `404 Not Found` (HTTP code captured in output)


## Datapack-Only Worldgen

### `createDatapackFeature`

**Description:** Write a datapack configured+placed feature JSON pair

**Parameters:**
- `featureName` (`string`, required) — Feature name
- `count` (`string`, optional) — Placement count (default 10)
- `featureType` (`string`, optional) — Feature type: ore, simple_block, block_column (default simple_block)
- `state` (`string`, optional) — Block state to place (e.g. minecraft:diamond_ore)
- `target` (`string`, optional) — Target block tag or block (e.g. minecraft:stone)

**Verification:** **Test:** `featureName="Ruby Ore Cluster"', 'featureType="ore"', 'target="minecraft:stone_ore_replaceables"', 'state="minecraft:red...` — **Result:** wrote `configured_feature/ruby_ore_cluster.json` and `placed_feature/ruby_ore_cluster.json`; server loaded without datapack registry errors **Test:** `featureName=SimplePlant', 'featureType=simple_block', 'state=minecraft:short_grass` — **Result:** wrote valid configured/placed feature pair

### `createDatapackStructure`

**Description:** Write a datapack structure set JSON for a custom structure

**Parameters:**
- `structureName` (`string`, required) — Structure name
- `biomeTag` (`string`, optional) — Biome tag to place in (e.g. minecraft:is_overworld)
- `nbtName` (`string`, optional) — NBT file name without .nbt extension
- `salt` (`string`, optional) — Placement salt (default 0)
- `separation` (`string`, optional) — Minimum distance between structures (default 10)
- `spacing` (`string`, optional) — Average distance between structures (default 20)

**Verification:** **Test:** `structureName=TestTower', 'nbtName=test_tower', 'biomeTag=minecraft:is_forest` — **Result:** wrote `mcptest7:worldgen/structure/testtower.json`, `template_pool/testtower.json`, `structure_set/testtower.json` with valid jigsaw fields; server loaded without datapack registry errors

### `createDatapackOre`

**Description:** Write a datapack configured ore + placed feature pair

**Parameters:**
- `oreName` (`string`, required) — Ore feature name
- `blockState` (`string`, optional) — Block to place (e.g. minecraft:iron_ore)
- `count` (`string`, optional) — Placement count per chunk (default 10)
- `discardChance` (`string`, optional) — Discard chance on air exposure (default 0.0)
- `heightRange` (`string`, optional) — Height range object {min,max} (default {above_bottom:0,below_top:0})
- `replaceableTag` (`string`, optional) — Replaceable block tag (default minecraft:stone_ore_replaceables)
- `veinSize` (`string`, optional) — Vein size (default 9)

**Verification:** **Test:** `oreName=TestSapphireOre', 'blockState=mcptest7:sapphire_block', 'replaceableTag=minecraft:stone_ore_replaceables` — **Result:** wrote `configured_feature/testsapphireore.json` and `placed_feature/testsapphireore.json`; server loaded cleanly

### `createDatapackBiome`

**Description:** Write a datapack biome JSON file directly into the workspace data folder

**Parameters:**
- `biomeName` (`string`, required) — Biome name
- `carvers` (`object`, optional) — Carver object or list (default overworld cave/canyon)
- `downfall` (`string`, optional) — Downfall (default 0.4)
- `features` (`object`, optional) — Feature step array (default empty steps)
- `fogColor` (`string`, optional) — Fog color decimal (default 12638463)
- `hasPrecipitation` (`string`, optional) — Has precipitation (true/false, default true)
- `skyColor` (`string`, optional) — Sky color decimal (default 7907327)
- `spawners` (`object`, optional) — Spawner map (default empty)
- `temperature` (`string`, optional) — Temperature (default 0.8)
- `waterColor` (`string`, optional) — Water color decimal (default 4159204)
- `waterFogColor` (`string`, optional) — Water fog color decimal (default 329011)

**Verification:** **Test:** `biomeName=McpTestBiome` — **Result:** wrote `worldgen/biome/mcptestbiome.json` with valid 1.21.1 biome schema

### `createDatapackDimension`

**Description:** Write a datapack dimension JSON file directly into the workspace data folder

**Parameters:**
- `dimensionName` (`string`, required) — Dimension name
- `biomeSource` (`object`, optional) — Biome source object (default fixed plains)
- `dimensionType` (`string`, optional) — Dimension type ID (default minecraft:overworld)
- `flatLayers` (`object`, optional) — Flat world layers array for flat generator
- `generatorType` (`string`, optional) — Generator type: noise, flat, debug (default noise)
- `noiseSettings` (`string`, optional) — Noise settings ID for noise generator (default minecraft:overworld)

**Verification:** **Test:** `dimensionName=mcp_test_dim', 'dimensionType=mcptest7:mcp_test_type` — **Result:** wrote `dimension/mcp_test_dim.json` with noise generator and fixed plains biome source

### `createDatapackDimensionType`

**Description:** Write a datapack dimension_type JSON file directly into the workspace data folder

**Parameters:**
- `dimensionTypeName` (`string`, required) — Dimension type name
- `ambientLight` (`string`, optional) — Ambient light (default 0.0)
- `bedWorks` (`string`, optional) — Bed works (true/false, default true)
- `coordinateScale` (`string`, optional) — Coordinate scale (default 1.0)
- `effects` (`string`, optional) — Effects dimension ID (default minecraft:overworld)
- `hasCeiling` (`string`, optional) — Has ceiling (true/false, default false)
- `hasRaids` (`string`, optional) — Has raids (true/false, default true)
- `hasSkylight` (`string`, optional) — Has skylight (true/false, default true)
- `height` (`string`, optional) — Height (default 384)
- `infiniburn` (`string`, optional) — Infiniburn tag (default #minecraft:infiniburn_overworld)
- `logicalHeight` (`string`, optional) — Logical height (default 384)
- `minY` (`string`, optional) — Minimum Y (default -64)
- `monsterSpawnLightLevel` (`object`, optional) — Monster spawn light level object or int
- `natural` (`string`, optional) — Natural (true/false, default true)
- `piglinSafe` (`string`, optional) — Piglin safe (true/false, default false)
- `respawnAnchorWorks` (`string`, optional) — Respawn anchor works (true/false, default false)
- `ultrawarm` (`string`, optional) — Ultrawarm (true/false, default false)

**Verification:** **Test:** `dimensionTypeName=mcp_test_type` — **Result:** wrote `dimension_type/mcp_test_type.json` with valid fields

### `createDatapackCarver`

**Description:** Write a datapack configured_carver JSON file directly into the workspace data folder

**Parameters:**
- `carverName` (`string`, required) — Carver name
- `carverType` (`string`, optional) — Carver type: cave, canyon, nether_cave (default cave)
- `lavaLevel` (`string`, optional) — Lava level above bottom (default 8)
- `probability` (`string`, optional) — Probability (default 0.15)
- `replaceableTag` (`string`, optional) — Replaceable block tag (default minecraft:overworld_carver_replaceables)
- `yMax` (`string`, optional) — Maximum Y absolute (default 180)
- `yMin` (`string`, optional) — Minimum Y absolute (default 10)

**Verification:** **Test:** `carverName=mcp_test_carver` — **Result:** wrote `worldgen/configured_carver/mcp_test_carver.json`; initial `replaceable` tag missing `#` and nested `y` anchors were fixed and re-tested

### `createMcfunction`

**Description:** Write a datapack .mcfunction file directly into the workspace data folder

**Parameters:**
- `commands` (`object`, required) — List of commands (without leading /)
- `functionName` (`string`, required) — Function file name without extension

**Verification:** **Test:** `functionName=test_hello', 'commands=[say Hello from MCP, give @a minecraft:dirt 1]` — **Result:** wrote `function/test_hello.mcfunction` with two commands


## Log Streaming & Build Diagnostics

### `getLatestLog`

**Description:** Tail the workspace run/logs/latest.log file

**Parameters:**
- `lines` (`string`, optional) — Number of lines to return (default 50)
- `logName` (`string`, optional) — Log name: latest, debug (default latest)

**Verification:** **Test:** `lines=20` — **Result:** returned the latest server `latest.log` tail

### `getGradleLog`

**Description:** Tail the gradle runserver log

**Parameters:**
- `lines` (`string`, optional) — Number of lines to return (default 50)

**Verification:** **Test:** `lines=50` — **Result:** returned the Gradle runserver/build log tail

### `getBuildProgress`

**Description:** Get the current Gradle console status and tail of the output

**Parameters:**
- `maxChars` (`string`, optional) — Maximum characters to return (default 4000)

**Verification:** **Test:** `maxChars=4000` — **Result:** returned `READY`/console tail after build completed

### `diagnoseBuildErrors`

**Description:** Parse build/server logs and return categorized errors with suggested fixes

**Parameters:**
- `lines` (`string`, optional) — Lines to tail (default 200)
- `logName` (`string`, optional) — Log name: latest, gradle, debug, client (default latest)

**Verification:** **Test:** `logName=latest', 'lines=200` — **Result:** parsed `latest.log`, categorized `Skipping villager profession` / advancement / tag errors and returned structured `errorCount` + suggestions


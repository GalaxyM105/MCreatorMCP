# MCreator MCP Integration Plugin

A lightweight Model Context Protocol (MCP) server implementation for MCreator that enables LLM applications to interact with MCreator workspaces, build projects, manage mod elements, and access workspace resources.

## Features

### 🛠️ **Direct Integration**
- Native MCP server implementation without external dependencies
- Direct MCreator API integration for optimal performance
- JSON-RPC 2.0 protocol compliance according to MCP specification
- No separate JAR processes or IPC overhead

### 🔧 **Core Tools**
- **Workspace Management**: Build, regenerate code, get info, settings
- **Element Operations**: List, create, edit, delete mod elements
- **Testing**: Run Minecraft client/server with mods
- **Resources**: Access workspace overview, elements, project structure

### 🌐 **Multiple Transport Support**
- **HTTP**: `http://localhost:<port>/mcp` (standard MCP protocol)
- **SSE**: `http://localhost:<port>/mcp/sse` (legacy compatibility)
- **Stdio**: Traditional MCP client support
- **Health**: `http://localhost:<port>/health` (monitoring)

### 📊 **Rich Resources**
- Complete workspace overview with metadata and statistics
- Project structure and file organization
- Live workspace statistics and element information
- Configuration and build information

## Quick Start

### Prerequisites
- Java 21+
- MCreator 2025.2+
- Git (for development)

### Installation

1. **Download/Build the Plugin**:
   ```bash
   git clone <repository-url>
   cd MCreatorMCP1
   ./gradlew jar
   ```

2. **Install Plugin**:
   - Copy `build/libs/MCreatorMCP.zip` to your MCreator plugins folder
   - Or use `./gradlew runMCreatorWithPlugin` for development

3. **Enable Java Plugins** in MCreator preferences

4. **Start MCreator** - the MCP server will start automatically

### Connecting LLM Clients

The MCP server exposes endpoints at (default port 5175 unless dynamically selected):
- **HTTP**: `http://localhost:<port>/mcp` (standard MCP protocol)
- **SSE**: `http://localhost:<port>/mcp/sse` (legacy compatibility)
- **Stdio**: Connect directly via stdin/stdout for traditional MCP clients
- **Health Check**: `http://localhost:<port>/health`

Configure your MCP-compatible client to connect to one of these endpoints.

## Architecture

### Simplified Direct Integration
```
┌─────────────────┐     Direct      ┌─────────────────┐
│   MCreator      │◄───────────────►│   LLM Client    │
│   + MCP Plugin  │    MCP Protocol │   (Editor/Agent)│
└─────────────────┘                 └─────────────────┘
```

- **MCreator Plugin**: Contains integrated MCP server with direct API access
- **No External Processes**: Everything runs within the plugin JVM
- **Direct Integration**: No IPC overhead, immediate MCreator API access
- **Clean Architecture**: Lightweight, fast, and maintainable

Note: Ports are dynamically selected to avoid conflicts. Check the plugin status dialog or logs for the actual port.

### Project Structure
```
MCreatorMCP/
├── src/main/java/net/mcreator/MCreatorMCP/
│   ├── MCreatorMCP.java              # Main plugin entry point
│   ├── MCPToolsService.java          # MCreator tool implementations
│   └── mcp/                          # MCP server implementation
│       ├── McpServer.java            # Core MCP server
│       ├── JsonRpcMessage.java       # JSON-RPC message handling
│       ├── McpTypes.java             # MCP protocol data types
│       ├── McpHttpTransport.java     # HTTP/SSE transport
│       └── McpStdioTransport.java    # Stdio transport
├── build.gradle                     # Plugin build configuration
└── settings.gradle                  # Project setup
```

## Available Tools

The MCP server now exposes **151 tools** across workspace, element, asset, build, localization, validation, tags, creative tabs, backups, generators, procedures, lifecycle, fine-grained editing, texture/model processing, in-game verification, publishing, datapack/Bedrock helpers, log streaming, CI automation, workspace import/export, addon/plugin integration, custom model binding, and build-error diagnostics. Call the `tools/list` endpoint to receive the full live list with JSON input schemas.

### Workspace Management
- `buildWorkspace()` - Build the current workspace
- `getWorkspaceInfo()` - Get detailed workspace information
- `getWorkspaceSettings()` - Get all workspace settings
- `updateWorkspaceSettings(settings)` - Update workspace settings
- `regenerateCode()` - Regenerate code without building
- `exportWorkspace(outputPath, includeRunDir?)` - Export the current workspace to a shareable `.zip`
- `importWorkspace(zipPath, targetFolder?)` - Import a workspace `.zip` (extract only; open manually or restart MCreator)
- `listRecentWorkspaces()` - List recently opened MCreator workspaces

### Element Discovery & Search
- `listModElements(elementType?)` - List mod elements with optional filtering
- `listModElementTypes()` - List all available element types
- `getElementProperties(elementName)` - Get all properties of a mod element as JSON
- `searchElements(query)` - Search mod elements by name or type
- `deleteElement(elementName)` - Delete mod element
- `validateElement(elementName)` - Validate a mod element
- `validateWorkspace()` - Validate the entire workspace

### Generic & Typed Element Creation
- `createElement(elementType, elementName, properties?)` - Create any mod element with a rich JSON property object
- `createItem`, `createBlock`, `createTool`, `createArmor`, `createFood`, `createEnchantment`, `createPotion`, `createLivingEntity`, `createRecipe`, `createParticle`, `createFluid`, `createBiome`, `createDimension`, `createAchievement`, `createLootTable`, `createFunction`, `createCommand`, `createFeature`, `createStructure`, `createPlant`, `createProjectile`, `createVillagerProfession`, `createVillagerTrade`, `createPotionEffect`, `createAttribute`, `createKeyBinding`, `createDamageType`, `createPainting`, `createBannerPattern`, `createGui`, `createOverlay`, `createGamerule`, `createItemextension`, `createArmortrim`, `createCode`, ... — one shortcut per registered `ModElementType`
- `createBedrockItem`, `createBedrockBlock`, `createBedrockEntity` — Bedrock Edition aliases (where the generator supports them)
- `registerLootTable`, `registerAdvancement`, `registerFunction` — data-pack style helpers

### Texture & Asset Management
- `listTexturesByType(type?)` - List textures, optionally filtered by type
- `importTexture(textureName, sourcePath, textureType)` - Import a PNG texture
- `deleteTexture(textureName, textureType)` - Delete a texture
- `listModels()` - List custom models
- `importModel(modelName, sourcePath)` - Import a model (Java JSON/OBJ)
- `bindCustomModel(elementName, modelName, modelType, sourcePath?, modelDefinition?, texture?)` - Bind an imported/generated JSON/OBJ/Java model to a block or item (writes the companion `.json.textures` mapping for JSON models)
- `deleteModel(modelName)` - Delete a model
- `getAssetMetadata(assetName, assetType)` - Get asset metadata

### Installed Plugins & Mod APIs
- `listInstalledPlugins()` - List all installed MCreator plugins and generators
- `listModAPIs()` - List API plugins/addons available for the current generator
- `enableModAPI(apiId)` / `disableModAPI(apiId)` - Enable or disable an API dependency (e.g. `mcreator_link`)

### Workspace Variables & Localization
- `listWorkspaceVariables()` - List all workspace variables
- `createVariable(variableName, variableType, scope, defaultValue?)` - Create a variable
- `updateVariable(variableName, variableType?, scope?, defaultValue?)` - Update a variable
- `deleteVariable(variableName)` - Delete a variable
- `getLocalizations(language?)` - Get localization strings
- `setLocalization(key, language, value)` - Set a localization string
- `addLanguage(languageCode)` - Add a new language

### Build, Export, Deploy & Testing
- `buildForJavaEdition(includeClient?, includeServer?)` - Build Java Edition mod and return JAR path
- `exportResourcePack(outputPath)` - Copy built resources to a folder
- `exportBehaviorPack(outputPath)` - Copy built data resources to a folder
- `deployToGameFolder(editionType, gameFolderPath)` - Copy the built JAR to a `mods` folder
- `runClient()` - Start the Minecraft client
- `runServer()` - Start the Minecraft server

### Log Streaming, Build Progress & Diagnostics
- `getLatestLog(lines?, logName?)` - Tail `run/logs/latest.log` or `debug.log`
- `getGradleLog(lines?)` - Tail the Gradle runserver/build log
- `getBuildProgress(maxChars?)` - Return the current Gradle console status (`READY`/`RUNNING`/`ERROR`) and the tail of its output
- `diagnoseBuildErrors(logName?, lines?)` - Parse a build/server log and return categorized errors with per-line suggested fixes

### Procedures, Events & Workflows
- `createProcedure(elementName, xml?)` - Create a reusable Blockly procedure
- `createProcedureAndAttach(procedureName, elementName, eventType, xml?)` - Create a procedure and immediately attach it to an element event in one call
- `getEventProcedures(elementName)` - List all event/procedure hooks on a mod element
- `updateEventProcedure(elementName, eventType, procedureName?, xml?)` - Attach or update an event procedure
- `registerEventListener(elementName, eventType, actionDefinition)` - Alias for `updateEventProcedure` using an action definition object
- `createModWithTemplate(templateName, modName, modId, author?, version?, properties?)` - Create a complete mod from a template (`basic_item`, `ore_set`, `techmod_base`, `armor_set`, `full_biome`, `dimension_mod`)
- `createTextureSet(setName, textureDefinitions, outputFormat?)` - Create multiple textures at once, optionally with `.mcmeta` animation files
- `createRecipeChain(recipeName, recipes, outputFormat?)` - Create multiple linked recipes
- `createModelFromDefinition(modelName, modelType, modelDefinition)` - Generate a JSON block/item model
- `createSoundEvent(soundName, audioFile, category, subtitleKey?)` - Register a new sound event
- `createParticleEffect(particleName, texture?, animationDef?, physics?)` - Create a particle effect
- `updateElementProperties(elementName, properties)` - Update properties of an existing mod element

### Bedrock Edition Support
- `createBedrockTexturePack(packName, version, description)` - Create a Bedrock texture pack folder with `manifest.json`
- `createBedrockResourcePack(packName, version, description, properties?)` - Create a Bedrock resource pack folder
- `createBedrockBehaviorPack(packName, version, description, properties?)` - Create a Bedrock behavior pack folder
- `createBedrockBehaviorJson(packName, elementType, elementName, properties?)` - Write a Bedrock behavior pack JSON definition for an item/block/entity
- `createBedrockItem`, `createBedrockBlock`, `createBedrockEntity` - Bedrock element aliases (where supported by the generator)
- `buildBedrockProject(packName?)` - Package resource and behavior packs into `.mcpack` files

### Datapack-Only Worldgen
- `createDatapackFeature(featureName, featureType?, target?, state?, count?)` - Write a vanilla datapack `configured_feature` + `placed_feature` JSON pair directly into the workspace data folder
- `createDatapackStructure(structureName, nbtName?, biomeTag?, spacing?, separation?, salt?)` - Write a vanilla datapack `structure` + `template_pool` + `structure_set` JSON set directly into the workspace data folder
- `createDatapackOre(oreName, blockState?, replaceableTag?, veinSize?, count?, heightRange?, discardChance?)` - Write a vanilla datapack `configured_feature` + `placed_feature` ore pair directly into the workspace data folder

### Publishing
- `publishToModrinth(apiToken, projectId, versionNumber, changelog?, loaders?, gameVersions?, releaseType?, filePath?)` - Publish the built JAR to Modrinth
- `publishToCurseForge(apiToken, projectId, displayName, changelog?, releaseType?, gameVersionIds?, filePath?)` - Publish the built JAR to CurseForge

### Versioning & Test Reports
- `compareElementVersions(elementName, version1?, version2?)` - Compare an element between workspace backups (`current`, `latest`, or backup name)
- `generateTestReport(logPath?)` - Parse the latest client/server log and produce a summary of errors, warnings, missing textures, and recipe errors

### Tags, Creative Tabs, Backups & Generators
- `createTag(tagType, tagName, entries)` / `updateTag(tagType, tagName, entries)` / `deleteTag(tagType, tagName)` / `listTags(tagType?)` - Manage data tags (items, blocks, entities, functions, biomes, etc.)
- `createCreativeTab(tabName, displayName, icon, showSearch?)` / `updateCreativeTabs(tabName, elementNames)` / `listCreativeTabs()` - Manage custom creative inventory tabs
- `createBackup(backupName?)` / `listBackups()` / `restoreBackup(backupName)` - Workspace local-history checkpoints
- `listGenerators()` / `switchGenerator(generatorName)` - Switch the active generator plugin (e.g. `neoforge-1.21.1`, `datapack-1.21.1`, `addon-26.1x`)

### Element Lifecycle & Fine-Grained Editing
- `cloneElement(sourceElementName, newElementName, properties?)` - Duplicate an existing element with optional overrides
- `renameElement(elementName, newName)` - Rename an element in the workspace
- `moveElement(elementName, folderPath)` - Move an element to a workspace folder
- `editRecipe(elementName, properties)` - Modify an existing recipe's type, inputs, and output
- `editAdvancement(elementName, properties)` - Modify an existing advancement's display, criteria, and rewards
- `editLootTable(elementName, properties)` - Modify an existing loot table's type and pools/entries

### Texture/Model Pipeline & In-Game Verification
- `processTexture(textureName, textureType, operations)` - Resize, pad, and recolor workspace textures
- `generateMcmeta(textureName, textureType, frameTime?, interpolate?, frames?)` - Create `.mcmeta` animation metadata
- `convertBlockbenchModel(sourcePath, modelName)` - Convert a Blockbench JSON model into a workspace model
- `executeServerCommand(command, rconHost?, rconPort?, rconPassword?)` - Send an RCON command to a running server
- `runTestScenario(scenarioName, commands, rconPassword?, rconPort?, timeoutSeconds?)` - Start a server, run commands, and return a log summary
- `verifyClientInGame(timeoutSeconds?, outputPath?, commands?)` - Launch the Minecraft client in a virtual display and capture a screenshot of the main menu
- `generateTestReport(logPath?)` - Parse a client/server log and return errors/warnings

### CI / Automation
- `runCIBuild(timeoutSeconds?)` - Regenerate code, build the JAR, start the server, and run a smoke-test command
- `exportModrinth(outputPath, summary?)` - Package the built JAR into a Modrinth `.mrpack`

### Model Validation & Conversion
- `validateModel(sourcePath)` - Validate a `.json` or `.obj` model file
- `convertModel(sourcePath, modelName, modelType, texture?)` - Convert an OBJ model into a Minecraft JSON model or copy an existing JSON model into the workspace

## Resources

### Available Resources
- `workspace://overview` - Complete workspace overview with metadata
- `workspace://elements` - All mod elements with properties and details
- `workspace://structure` - Project directory structure and organization

## Element Property Reference

`createElement` and the typed `create*` shortcuts accept an optional `properties` object. Property keys are matched to `GeneratableElement` fields (with aliases for common names). The following are commonly used keys for major Java Edition element types.

### Common properties
| Key | Type | Description |
|-----|------|-------------|
| `name` | string | Display/localized name |
| `texture` | string / object | Main texture name or `{all: "name", top: "...", side: "...", item: "..."}` |
| `creativeTabs` | array of strings | Creative tab entries |
| `rarity` | string | `COMMON`, `UNCOMMON`, `RARE`, `EPIC` |

### Item properties
| Key | Type | Description |
|-----|------|-------------|
| `stackSize` | int | Max stack size (1-64) |
| `isFood` | bool | Is edible |
| `nutritionalValue` / `foodHealAmount` | int | Food hunger restored |
| `saturation` / `foodSaturation` | float | Food saturation |
| `enchantability` | int | Enchantability |
| `immuneToFire` / `fireResistant` | bool | Is fireproof |
| `isBurnable` | bool | Burns in furnace |
| `creativeTabs` | array | Creative tab names |

### Block properties
| Key | Type | Description |
|-----|------|-------------|
| `hardness` | float | Mining hardness (-1 for unbreakable) |
| `resistance` | float | Blast resistance |
| `luminance` | int | Light level 0-15 |
| `slipperiness` | float | Friction (0.6 default, 0.98 for ice) |
| `soundOnStep` / `soundType` | string | `STONE`, `WOOD`, `METAL`, `GRAVEL`, etc. |
| `toolForHarvest` / `toolClass` | string | `Pickaxe`, `Axe`, `Shovel`, `Hoe` |
| `harvestLevel` / `toolLevel` | int | 0=hand, 1=wood, 2=stone, 3=iron, 4=diamond |
| `isFlammable` | bool | Can catch fire |
| `flammability` | int | Burn chance |
| `fireSpreadSpeed` | int | Fire spread speed |
| `drops` | string / object | `{item: "minecraft:diamond", count: 1}` |
| `hasTransparency` | bool | Whether the block is translucent |
| `transparencyType` | string | `SOLID`, `CUTOUT`, `CUTOUT_MIPPED`, `TRANSLUCENT` |
| `renderType` / `render` | int/string | `10`/`solid`, `12`/`cutout`, `cutout_mipped`, `translucent`, `json`, `obj`, `java` |
| `rotationMode` / `rotation` | string | `none`, `y_axis`, `all_axis`, `block_y_axis`, `block_all_axis`, `log` |
| `tintType` / `tint` | string | `No tint`, `Grass`, `Foliage`, `Water`, `Default foliage` |
| `texture` / `textures` | string/object | Single texture name, or `{top, bottom, side, front, back, left, right, all}` |
| `customModelName` / `model` | string | Custom model name for `json`/`obj`/`java` render types |
| `particleTexture` / `particle` | string | Particle texture name |
| `itemTexture` / `item` | string | Item/inventory texture name (defaults to block texture if omitted) |

### Tool / Armor / Food
| Key | Type | Description |
|-----|------|-------------|
| `material` / `armorMaterial` | string | `WOOD`, `STONE`, `IRON`, `DIAMOND`, `GOLD`, `NETHERITE` |
| `attackDamage` / `damageModifier` | float | Attack damage |
| `attackSpeed` | float | Attack speed |
| `efficiency` | float | Mining speed |
| `repairItems` | array of strings | Items that repair the tool/armor |
| `armorValues` | int[4] | Helmet, body, leggings, boots protection values |
| `armorDurability` / `maxDamage` | int/array | Durability per piece |
| `knockbackResistance` | float | Knockback resistance |

### Recipe properties
| Key | Type | Description |
|-----|------|-------------|
| `recipeType` | string | `Crafting`, `Smelting`, `Blasting`, `Smoking`, `Campfire cooking`, `Stone cutting`, `Smithing`, `Brewing` |
| `inputs` | array | Ingredients (item names, tags, or maps) |
| `output` | string/map | Result item or `{item: "...", count: N}` |
| `experience` | float | XP for smelting recipes |
| `cookingTime` / `cookTime` | int | Ticks to cook |

### Fluid, Biome, Dimension, Mob, etc.
The property applier maps JSON keys to every `GeneratableElement` field by reflection, so you can pass any documented MCreator field name. Use `getElementProperties(elementName)` to inspect the exact JSON structure of an existing element.

## Example JSON Payloads

### Create a customized item
```json
{
  "name": "createItem",
  "arguments": {
    "elementName": "Ruby",
    "properties": {
      "name": "Ruby",
      "stackSize": 64,
      "rarity": "RARE",
      "creativeTabs": ["MATERIALS"],
      "isFood": false
    }
  }
}
```

### Create a block with drops
```json
{
  "name": "createBlock",
  "arguments": {
    "elementName": "SapphireOre",
    "properties": {
      "name": "Sapphire Ore",
      "texture": "sapphire",
      "hardness": 3,
      "resistance": 3,
      "toolForHarvest": "Pickaxe",
      "harvestLevel": 2,
      "drops": { "item": "minecraft:diamond", "count": 1 },
      "creativeTabs": ["BUILDING_BLOCKS"]
    }
  }
}
```

### Create a crafting recipe
```json
{
  "name": "createRecipe",
  "arguments": {
    "elementName": "RubyFromCoal",
    "properties": {
      "recipeType": "Crafting",
      "inputs": ["minecraft:coal", "minecraft:coal", "minecraft:coal", "minecraft:coal"],
      "output": { "item": "Ruby", "count": 1 }
    }
  }
}
```

### Create a tool
```json
{
  "name": "createTool",
  "arguments": {
    "elementName": "SapphirePickaxe",
    "properties": {
      "name": "Sapphire Pickaxe",
      "texture": "iron_tool",
      "toolType": "Pickaxe",
      "material": "DIAMOND",
      "efficiency": 8,
      "attackDamage": 5,
      "enchantability": 15,
      "repairItems": ["minecraft:iron_ingot"]
    }
  }
}
```

### Update workspace settings
```json
{
  "name": "updateWorkspaceSettings",
  "arguments": {
    "settings": {
      "modName": "My MCP Mod",
      "version": "1.0.0",
      "author": "AI Agent"
    }
  }
}
```

### Build and deploy
```json
{
  "name": "buildForJavaEdition",
  "arguments": { "includeClient": true, "includeServer": true }
}
```

## Configuration

The MCP server is automatically configured by the plugin with sensible defaults:

- **HTTP Port**: Auto-selected starting from 5175
- **Transport Methods**: HTTP, SSE, and Stdio enabled by default
- **Workspace Integration**: Automatic detection when MCreator workspace loads
- **Tool Registration**: All MCreator tools automatically registered

No additional configuration needed - just install and run!

## Development

### Building
```bash
# Build the plugin
./gradlew jar

# Run MCreator with plugin
./gradlew runMCreatorWithPlugin

# Clean build
./gradlew clean build
```

### Debugging
- Plugin logs: MCreator console output
- MCP Protocol: Enable DEBUG logging in MCreator console
- Network traffic: Monitor HTTP endpoints with browser dev tools

### Testing Tools
```bash
# Test MCP endpoints
curl http://localhost:<port>/health
curl -X POST http://localhost:<port>/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"test-client","version":"1.0"}}}'
```

## Troubleshooting

### Common Issues

**MCP Server won't start**:
- Check Java 21+ is available
- Verify the HTTP port is free (default 5175). The plugin will auto-select a free port.
- Check MCreator console for errors

**Tools not working**:
- Ensure MCreator workspace is loaded
- Check plugin status via "MCP Server Status" menu
- Restart MCP server if needed

**Client connection issues**:
- Try different transport methods (HTTP vs SSE vs Stdio)
- Check CORS settings for web clients
- Verify MCP client protocol version compatibility

### Monitoring
- Health checks: `http://localhost:<port>/health`
- Plugin status: "MCP Server Status" menu in MCreator
- Console logs: Enable DEBUG logging in MCreator

## Contributing

1. Fork the repository
2. Create a feature branch
3. Implement changes with tests
4. Update documentation
5. Submit a pull request

## License

This repository is licensed under the **GNU General Public License v2.0 only
(GPL-2.0-only)** - see the [LICENSE](LICENSE) file for details.

The original MCreatorMCP code by Pylo was released under the MIT License. The
MIT License is compatible with the GPL and that code is included in this
combined work under GPL-2.0-only, with the original MIT copyright and
permission notices preserved in the [LICENSE](LICENSE) file for attribution.

## Links

- [MCreator](https://mcreator.net/) - Minecraft mod creation platform
- [Model Context Protocol](https://modelcontextprotocol.io/) - Protocol specification
- [MCreator Plugin Development](https://mcreator.net/wiki/developing-mcreator-plugins) - Plugin development guide
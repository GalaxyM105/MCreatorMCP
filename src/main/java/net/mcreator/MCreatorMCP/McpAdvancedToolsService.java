/*
 * MCreatorMCP advanced tools: events/workflows/textures/Bedrock/test reporting.
 * SPDX-License-Identifier: GPL-2.0-only
 */
package net.mcreator.MCreatorMCP;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.mcreator.MCreatorMCP.mcp.McpElementPropertyApplier;
import net.mcreator.MCreatorMCP.mcp.McpServer;
import net.mcreator.MCreatorMCP.mcp.McpTypes;
import net.mcreator.element.GeneratableElement;
import net.mcreator.element.ModElementType;
import net.mcreator.element.ModElementTypeLoader;
import net.mcreator.element.parts.procedure.*;
import net.mcreator.element.util.GEValidator;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.workspace.resources.TextureType;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.ModElement;
import net.mcreator.workspace.elements.SoundElement;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class McpAdvancedToolsService {

    private static final Logger LOG = LogManager.getLogger("MCP-AdvancedTools");

    private static final String PROCEDURE_XML_BASE =
            "<xml xmlns=\"https://developers.google.com/blockly/xml\"><block type=\"event_trigger\" deletable=\"false\" x=\"40\" y=\"40\"><field name=\"trigger\">no_ext_trigger</field></block></xml>";

    private static final Map<String, ProcedureTemplate> PROCEDURE_TEMPLATES;
    static {
        Map<String, ProcedureTemplate> map = new LinkedHashMap<>();
        map.put("empty", new ProcedureTemplate("empty", "Empty trigger-only procedure", Map.of()));
        map.put("give_item", new ProcedureTemplate("give_item", "Give an item to the entity",
                Map.of("item", "minecraft:diamond", "amount", "1")));
        map.put("send_message", new ProcedureTemplate("send_message", "Send a chat message to the entity",
                Map.of("message", "Hello!", "actbar", "false")));
        map.put("execute_command", new ProcedureTemplate("execute_command", "Execute a command at the event position",
                Map.of("command", "say Hello")));
        map.put("set_block", new ProcedureTemplate("set_block", "Set a block at the event position",
                Map.of("block", "minecraft:stone")));
        map.put("spawn_entity", new ProcedureTemplate("spawn_entity", "Spawn an entity at the event position",
                Map.of("entity", "minecraft:cow")));
        map.put("apply_potion", new ProcedureTemplate("apply_potion", "Apply a potion effect to the entity",
                Map.of("potion", "minecraft:regeneration", "level", "1", "duration", "60")));
        PROCEDURE_TEMPLATES = Collections.unmodifiableMap(map);
    }

    private record ProcedureTemplate(String name, String description, Map<String, String> defaultValues) {}

    private final MCPToolsService host;
    private final McpServer mcpServer;
    private final MCreator mcreator;
    private final ObjectMapper objectMapper;

    public McpAdvancedToolsService(MCPToolsService host, McpServer mcpServer, MCreator mcreator) {
        this.host = host;
        this.mcpServer = mcpServer;
        this.mcreator = mcreator;
        this.objectMapper = new ObjectMapper();
    }

    public void registerTools() {
        // Event / procedure tools
        mcpServer.registerTool("createProcedure", "Create a reusable procedure with Blockly XML",
                host.objectSchema(host.props(
                        "elementName", host.stringSchema("Procedure name"),
                        "xml", host.stringSchema("Blockly XML (optional)")
                ), "elementName"),
                params -> createProcedure(params));
        mcpServer.registerTool("getEventProcedures", "List all event/procedure hooks on a mod element",
                host.objectSchema(host.props(
                        "elementName", host.stringSchema("Element name")
                ), "elementName"),
                params -> getEventProcedures(params));
        mcpServer.registerTool("updateEventProcedure", "Attach or update an event procedure on an element",
                host.objectSchema(host.props(
                        "elementName", host.stringSchema("Element name"),
                        "eventType", host.stringSchema("Event field name, e.g. onRightClicked"),
                        "procedureName", host.stringSchema("Procedure element name (optional)"),
                        "xml", host.stringSchema("Blockly XML for the procedure (optional)")
                ), "elementName", "eventType"),
                params -> updateEventProcedure(params));
        mcpServer.registerTool("registerEventListener", "Alias for updateEventProcedure using actionDefinition object",
                host.objectSchema(host.props(
                        "elementName", host.stringSchema("Element name"),
                        "eventType", host.stringSchema("Event field name"),
                        "actionDefinition", host.objectSchema(host.props(
                                "procedureName", host.stringSchema("Procedure element name"),
                                "xml", host.stringSchema("Blockly XML")
                        ))
                ), "elementName", "eventType"),
                params -> registerEventListener(params));
        mcpServer.registerTool("createProcedureAndAttach", "Create a procedure and immediately attach it to an element event",
                host.objectSchema(host.props(
                        "procedureName", host.stringSchema("New procedure name"),
                        "elementName", host.stringSchema("Element name to attach to"),
                        "eventType", host.stringSchema("Event field name, e.g. onRightClicked"),
                        "xml", host.stringSchema("Blockly XML (optional)")
                ), "procedureName", "elementName", "eventType"),
                params -> createProcedureAndAttach(params));
        mcpServer.registerTool("listProcedureTemplates", "List available procedure templates with parameter descriptions",
                host.objectSchema(Map.of()),
                params -> listProcedureTemplates(params));
        mcpServer.registerTool("applyProcedureTemplate", "Create a procedure from a named template and attach it to an element event",
                host.objectSchema(host.props(
                        "templateName", host.stringSchema("Template name (e.g. empty, give_item, send_message, execute_command, set_block, spawn_entity, apply_potion)"),
                        "elementName", host.stringSchema("Element name to attach to"),
                        "eventType", host.stringSchema("Event field name, e.g. onRightClicked"),
                        "procedureName", host.stringSchema("Optional custom procedure name"),
                        "values", host.objectPropSchema("Template values (item, message, command, entity, potion, block, amount, x, y, z)")
                ), "templateName", "elementName", "eventType"),
                params -> applyProcedureTemplate(params));

        // Compound workflow tools
        mcpServer.registerTool("createModWithTemplate", "Create a complete mod from a template",
                host.objectSchema(host.props(
                        "templateName", host.stringSchema("Template: basic_item, ore_set, armor_set, full_biome, dimension_mod, techmod_base"),
                        "modName", host.stringSchema("Mod display name"),
                        "modId", host.stringSchema("Mod ID"),
                        "author", host.stringSchema("Author"),
                        "version", host.stringSchema("Version"),
                        "properties", host.objectSchema(host.props(
                                "baseName", host.stringSchema("Base name for generated elements"),
                                "color", host.stringSchema("Hex color for placeholders")
                        ))
                ), "templateName", "modName", "modId"),
                params -> createModWithTemplate(params));
        mcpServer.registerTool("createTextureSet", "Create multiple textures at once with optional animation metadata",
                host.objectSchema(host.props(
                        "setName", host.stringSchema("Set name prefix"),
                        "textureDefinitions", host.objectSchema(null),
                        "outputFormat", host.stringSchema("PNG or MCMeta")
                ), "setName", "textureDefinitions"),
                params -> createTextureSet(params));
        mcpServer.registerTool("createRecipeChain", "Create multiple linked recipes",
                host.objectSchema(host.props(
                        "recipeName", host.stringSchema("Prefix for recipe element names"),
                        "recipes", host.objectSchema(null),
                        "outputFormat", host.stringSchema("Ignored")
                ), "recipeName", "recipes"),
                params -> createRecipeChain(params));
        mcpServer.registerTool("createModelFromDefinition", "Generate a JSON block/item model",
                host.objectSchema(host.props(
                        "modelName", host.stringSchema("Model name"),
                        "modelType", host.stringSchema("block or item"),
                        "modelDefinition", host.objectSchema(null)
                ), "modelName", "modelType", "modelDefinition"),
                params -> createModelFromDefinition(params));
        mcpServer.registerTool("createSoundEvent", "Register a new sound event",
                host.objectSchema(host.props(
                        "soundName", host.stringSchema("Sound name"),
                        "audioFile", host.stringSchema("Source audio file path or base64 data URI"),
                        "category", host.stringSchema("Sound category: master, block, entity, etc."),
                        "subtitleKey", host.stringSchema("Subtitle localization key (optional)")
                ), "soundName", "audioFile", "category"),
                params -> createSoundEvent(params));
        mcpServer.registerTool("createParticleEffect", "Create a particle effect",
                host.objectSchema(host.props(
                        "particleName", host.stringSchema("Particle name"),
                        "texture", host.stringSchema("Texture name"),
                        "animationDef", host.objectSchema(null),
                        "physics", host.objectSchema(null)
                ), "particleName"),
                params -> createParticleEffect(params));

        // Generic element editing
        mcpServer.registerTool("updateElementProperties", "Update properties of an existing mod element",
                host.objectSchema(host.props(
                        "elementName", host.stringSchema("Element name"),
                        "properties", host.objectSchema(null)
                ), "elementName", "properties"),
                params -> updateElementProperties(params));

        // Bedrock pack tools
        mcpServer.registerTool("createBedrockTexturePack", "Create a Bedrock texture/resource pack folder with manifest",
                host.objectSchema(host.props(
                        "packName", host.stringSchema("Pack name"),
                        "version", host.stringSchema("Version"),
                        "description", host.stringSchema("Description")
                ), "packName", "version", "description"),
                params -> createBedrockTexturePack(params));
        mcpServer.registerTool("createBedrockResourcePack", "Create a Bedrock resource pack folder with manifest",
                host.objectSchema(host.props(
                        "packName", host.stringSchema("Pack name"),
                        "version", host.stringSchema("Version"),
                        "description", host.stringSchema("Description"),
                        "properties", host.objectSchema(null)
                ), "packName", "version", "description"),
                params -> createBedrockResourcePack(params));
        mcpServer.registerTool("createBedrockBehaviorPack", "Create a Bedrock behavior pack folder with manifest",
                host.objectSchema(host.props(
                        "packName", host.stringSchema("Pack name"),
                        "version", host.stringSchema("Version"),
                        "description", host.stringSchema("Description"),
                        "properties", host.objectSchema(null)
                ), "packName", "version", "description"),
                params -> createBedrockBehaviorPack(params));
        mcpServer.registerTool("buildBedrockProject", "Package Bedrock resource and behavior packs into .mcpack files",
                host.objectSchema(host.props(
                        "packName", host.stringSchema("Pack name prefix")
                )),
                params -> buildBedrockProject(params));
        mcpServer.registerTool("exportBedrockAddon", "Package Bedrock resource and behavior packs into a combined .mcaddon file",
                host.objectSchema(host.props(
                        "packName", host.stringSchema("Pack name prefix (optional)"),
                        "outputPath", host.stringSchema("Output .mcaddon file path (optional)")
                )),
                params -> exportBedrockAddon(params));

        // Test & reporting
        mcpServer.registerTool("generateTestReport", "Generate an in-game test report from client/server logs",
                host.objectSchema(host.props(
                        "logPath", host.stringSchema("Optional path to latest.log")
                )),
                params -> generateTestReport(params));
        mcpServer.registerTool("compareElementVersions", "Compare an element across two workspace backups",
                host.objectSchema(host.props(
                        "elementName", host.stringSchema("Element name"),
                        "version1", host.stringSchema("First version/backup name (or 'current')"),
                        "version2", host.stringSchema("Second version/backup name (or 'latest')")
                ), "elementName"),
                params -> compareElementVersions(params));
    }

    // ------------------------------------------------------------------
    // Event / procedure tools
    // ------------------------------------------------------------------

    private McpTypes.ToolResult createProcedure(Map<String, Object> params) {
        String name = (String) params.get("elementName");
        String xml = (String) params.get("xml");

        Map<String, Object> props = new HashMap<>();
        props.put("procedurexml", xml != null && !xml.isEmpty() ? xml : PROCEDURE_XML_BASE);

        Map<String, Object> createParams = new HashMap<>();
        createParams.put("elementName", name);
        createParams.put("properties", props);

        return host.createTypedElement(mcreator, "procedure", createParams);
    }

    private McpTypes.ToolResult getEventProcedures(Map<String, Object> params) {
        String elementName = (String) params.get("elementName");
        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) return host.createErrorResult("No workspace loaded");

            ModElement modElement = workspace.getModElementByName(elementName);
            if (modElement == null) return host.createErrorResult("Element not found: " + elementName);

            GeneratableElement ge = modElement.getGeneratableElement();
            if (ge == null) return host.createErrorResult("Element has no generatable data");

            List<Map<String, Object>> events = new ArrayList<>();
            for (Field field : getAllFields(ge.getClass())) {
                field.setAccessible(true);
                if (Procedure.class.isAssignableFrom(field.getType())) {
                    Object value = field.get(ge);
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("eventType", field.getName());
                    entry.put("fieldType", field.getType().getSimpleName());
                    if (value instanceof Procedure proc) {
                        entry.put("procedureName", proc.getName());
                        String xml = getProcedureXml(workspace, proc.getName());
                        entry.put("procedureXml", xml);
                    } else {
                        entry.put("procedureName", null);
                        entry.put("procedureXml", null);
                    }
                    events.add(entry);
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("elementName", elementName);
            result.put("events", events);
            return host.createSuccessResult("Events for '" + elementName + "':\n" + objectMapper.writeValueAsString(result));
        } catch (Exception e) {
            LOG.error("Error listing event procedures", e);
            return host.createErrorResult("Failed to list event procedures: " + e.getMessage());
        }
    }

    private McpTypes.ToolResult updateEventProcedure(Map<String, Object> params) {
        String elementName = (String) params.get("elementName");
        String eventType = (String) params.get("eventType");
        String procedureName = (String) params.get("procedureName");
        String xml = (String) params.get("xml");
        return doUpdateEventProcedure(elementName, eventType, procedureName, xml);
    }

    private McpTypes.ToolResult registerEventListener(Map<String, Object> params) {
        String elementName = (String) params.get("elementName");
        String eventType = (String) params.get("eventType");
        Object actionDef = params.get("actionDefinition");

        String procedureName = null;
        String xml = null;
        if (actionDef instanceof Map<?, ?> map) {
            procedureName = (String) map.get("procedureName");
            xml = (String) map.get("xml");
        }
        return doUpdateEventProcedure(elementName, eventType, procedureName, xml);
    }

    private McpTypes.ToolResult doUpdateEventProcedure(String elementName, String eventType, String procedureName, String xml) {
        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) return host.createErrorResult("No workspace loaded");
            if (elementName == null || elementName.isEmpty()) return host.createErrorResult("elementName is required");
            if (eventType == null || eventType.isEmpty()) return host.createErrorResult("eventType is required");

            ModElement modElement = workspace.getModElementByName(elementName);
            if (modElement == null) return host.createErrorResult("Element not found: " + elementName);

            GeneratableElement ge = modElement.getGeneratableElement();
            if (ge == null) return host.createErrorResult("Element has no generatable data");

            Field eventField = findFieldIgnoreCase(ge.getClass(), eventType);
            if (eventField == null || !Procedure.class.isAssignableFrom(eventField.getType()))
                return host.createErrorResult("Event '" + eventType + "' not found or not a procedure field on " + elementName);

            eventField.setAccessible(true);

            if (procedureName == null || procedureName.isEmpty())
                procedureName = sanitizeJavaName(elementName + "_" + eventType);

            if (workspace.getModElementByName(procedureName) == null) {
                createProcedure(procedureName, xml != null && !xml.isEmpty() ? xml : PROCEDURE_XML_BASE);
            } else if (xml != null && !xml.isEmpty()) {
                updateProcedureXml(procedureName, xml);
            }

            Procedure procInstance = newProcedureInstance(eventField.getType(), procedureName);
            eventField.set(ge, procInstance);

            workspace.getModElementManager().storeModElement(ge);
            workspace.markDirty();

            return host.createSuccessResult("Event '" + eventType + "' on '" + elementName + "' linked to procedure '" + procedureName + "'");
        } catch (Exception e) {
            LOG.error("Error updating event procedure", e);
            return host.createErrorResult("Failed to update event procedure: " + e.getMessage());
        }
    }

    private McpTypes.ToolResult createProcedureAndAttach(Map<String, Object> params) {
        String procedureName = (String) params.get("procedureName");
        String elementName = (String) params.get("elementName");
        String eventType = (String) params.get("eventType");
        String xml = (String) params.get("xml");

        if (procedureName == null || elementName == null || eventType == null)
            return host.createErrorResult("procedureName, elementName, and eventType are required");

        McpTypes.ToolResult createResult = createProcedure(Map.of("elementName", procedureName, "xml", xml != null ? xml : ""));
        if (Boolean.TRUE.equals(createResult.getIsError()))
            return createResult;

        return doUpdateEventProcedure(elementName, eventType, procedureName, xml);
    }

    // ------------------------------------------------------------------
    // Compound workflow tools
    // ------------------------------------------------------------------

    private McpTypes.ToolResult createModWithTemplate(Map<String, Object> params) {
        String templateName = (String) params.get("templateName");
        String modName = (String) params.get("modName");
        String modId = (String) params.get("modId");
        String author = (String) params.get("author");
        String version = (String) params.get("version");
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = params.get("properties") instanceof Map ? (Map<String, Object>) params.get("properties") : new HashMap<>();

        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) return host.createErrorResult("No workspace loaded");

            WorkspaceSettings oldSettings = workspace.getWorkspaceSettings();
            if (modId != null && !modId.isEmpty()) {
                WorkspaceSettings settings = new WorkspaceSettings(modId);
                settings.setModName(modName != null ? modName : oldSettings.getModName());
                settings.setVersion(version != null ? version : oldSettings.getVersion());
                settings.setDescription(oldSettings.getDescription());
                settings.setAuthor(author != null ? author : oldSettings.getAuthor());
                settings.setWebsiteURL(oldSettings.getWebsiteURL());
                settings.setLicense(oldSettings.getLicense());
                settings.setCredits(oldSettings.getCredits());
                settings.setServerSideOnly(oldSettings.isServerSideOnly());
                settings.setUpdateURL(oldSettings.getUpdateURL());
                settings.setModPicture(oldSettings.getModPicture());
                settings.setModElementsPackage("net." + modId.toLowerCase(Locale.ROOT));
                settings.setCurrentGenerator(oldSettings.getCurrentGenerator());
                settings.setRequiredMods(new HashSet<>(oldSettings.getRequiredMods()));
                settings.setDependencies(new HashSet<>(oldSettings.getDependencies()));
                settings.setDependants(new HashSet<>(oldSettings.getDependants()));
                settings.setMCreatorDependencies(new HashSet<>(oldSettings.getMCreatorDependencies()));
                workspace.setWorkspaceSettings(settings);
            } else {
                if (modName != null) oldSettings.setModName(modName);
                if (version != null) oldSettings.setVersion(version);
                if (author != null) oldSettings.setAuthor(author);
            }
            workspace.markDirty();

            String base = properties.get("baseName") instanceof String s ? s : host.capitalize(templateName.replaceAll("[^a-zA-Z0-9]", ""));
            if (base.isEmpty()) base = "Template";

            String color = properties.get("color") instanceof String c ? c : null;
            if (color != null && !color.isEmpty()) {
                createPlaceholderColorTexture(workspace, TextureType.ITEM, base.toLowerCase(Locale.ROOT) + "_ingot", color);
                createPlaceholderColorTexture(workspace, TextureType.BLOCK, base.toLowerCase(Locale.ROOT) + "_ore", color);
                createPlaceholderColorTexture(workspace, TextureType.ITEM, base.toLowerCase(Locale.ROOT) + "_pickaxe", color);
                createPlaceholderColorTexture(workspace, TextureType.ITEM, base.toLowerCase(Locale.ROOT) + "_armor", color);
            }

            List<String> created = new ArrayList<>();
            Map<String, Object> result = new HashMap<>();

            switch (templateName.toLowerCase(Locale.ROOT)) {
                case "basic_item" -> {
                    created.add(createFromTemplate("item", base, Map.of("name", base, "creativeTabs", List.of("MATERIALS"))));
                }
                case "ore_set", "techmod_base" -> {
                    String oreName = createFromTemplate("block", base + "Ore", Map.of(
                            "name", base + " Ore",
                            "texture", base.toLowerCase(Locale.ROOT) + "_ore",
                            "hardness", 3,
                            "resistance", 3,
                            "destroyTool", "Pickaxe",
                            "requiresCorrectTool", true,
                            "vanillaToolTier", "IRON",
                            "creativeTabs", List.of("BUILDING_BLOCKS")
                    ));
                    String ingotName = createFromTemplate("item", base + "Ingot", Map.of(
                            "name", base + " Ingot",
                            "texture", base.toLowerCase(Locale.ROOT) + "_ingot",
                            "creativeTabs", List.of("MATERIALS")
                    ));
                    String pickaxeName = createFromTemplate("tool", base + "Pickaxe", Map.of(
                            "name", base + " Pickaxe",
                            "toolType", "Pickaxe",
                            "texture", base.toLowerCase(Locale.ROOT) + "_pickaxe",
                            "material", "IRON",
                            "efficiency", 6,
                            "attackDamage", 4,
                            "attackSpeed", -2.8,
                            "usageCount", 250,
                            "repairItems", List.of(ingotName),
                            "creativeTabs", List.of("TOOLS")
                    ));
                    createFromTemplate("recipe", base + "OreSmelting", Map.of(
                            "recipeType", "Smelting",
                            "inputs", ingotName,
                            "output", Map.of("item", ingotName, "count", 1),
                            "experience", 1.0,
                            "cookingTime", 200
                    ));
                    createFromTemplate("recipe", base + "PickaxeRecipe", Map.of(
                            "recipeType", "Crafting",
                            "inputs", List.of(ingotName, ingotName, ingotName, "", "minecraft:stick", "", "", "minecraft:stick", ""),
                            "output", Map.of("item", pickaxeName, "count", 1)
                    ));
                    created.addAll(List.of(oreName, ingotName, pickaxeName));
                }
                case "armor_set" -> {
                    String ingotName = createFromTemplate("item", base + "Ingot", Map.of(
                            "name", base + " Ingot",
                            "texture", base.toLowerCase(Locale.ROOT) + "_ingot",
                            "creativeTabs", List.of("MATERIALS")
                    ));
                    String armorName = createFromTemplate("armor", base + "Armor", host.props(
                            "name", base + " Armor",
                            "armorTextureFile", base.toLowerCase(Locale.ROOT) + "_armor",
                            "enableHelmet", true,
                            "enableBody", true,
                            "enableLeggings", true,
                            "enableBoots", true,
                            "helmetName", base + " Helmet",
                            "bodyName", base + " Chestplate",
                            "leggingsName", base + " Leggings",
                            "bootsName", base + " Boots",
                            "maxDamage", 25,
                            "damageValueHelmet", 3,
                            "damageValueBody", 6,
                            "damageValueLeggings", 5,
                            "damageValueBoots", 2,
                            "enchantability", 12,
                            "repairItems", List.of(ingotName),
                            "creativeTabs", List.of("COMBAT")
                    ));
                    created.addAll(List.of(ingotName, armorName));
                }
                case "full_biome" -> {
                    String biomeName = createFromTemplate("biome", base + "Biome", host.props(
                            "name", base + " Biome",
                            "airColor", "#78A7FF",
                            "grassColor", "#55AA55",
                            "foliageColor", "#559955",
                            "waterColor", "#3F76E4",
                            "temperature", 0.8,
                            "rainingPossibility", 0.5,
                            "spawnBiome", true,
                            "treesPerChunk", 1,
                            "defaultFeatures", List.of("Lakes", "Ores", "Monster_Rooms", "Plain_Vegetation"),
                            "groundBlock", "minecraft:grass_block",
                            "undergroundBlock", "minecraft:dirt",
                            "underwaterBlock", "minecraft:gravel"
                    ));
                    created.add(biomeName);
                }
                case "dimension_mod" -> {
                    String igniterName = createFromTemplate("item", base + "Igniter", Map.of(
                            "name", base + " Igniter",
                            "creativeTabs", List.of("TOOLS")
                    ));
                    String dimName = createFromTemplate("dimension", base + "Dimension", host.props(
                            "name", base + " Dimension",
                            "enableIgniter", true,
                            "igniterName", base + " Igniter",
                            "igniterRarity", "COMMON",
                            "enablePortal", true,
                            "portalFrame", "minecraft:obsidian",
                            "mainFillerBlock", "minecraft:stone",
                            "fluidBlock", "minecraft:water",
                            "canRespawnHere", false,
                            "imitateOverworldBehaviour", false,
                            "biomesInDimension", List.of("minecraft:plains"),
                            "creativeTabs", List.of("TOOLS")
                    ));
                    created.addAll(List.of(igniterName, dimName));
                }
                default -> {
                    return host.createErrorResult("Unknown template: " + templateName + ". Available: basic_item, ore_set, armor_set, full_biome, dimension_mod, techmod_base");
                }
            }

            result.put("template", templateName);
            result.put("modName", workspace.getWorkspaceSettings().getModName());
            result.put("modId", workspace.getWorkspaceSettings().getModID());
            result.put("createdElements", created);
            return host.createSuccessResult("Template '" + templateName + "' applied. Created elements: " + created + "\n" + objectMapper.writeValueAsString(result));
        } catch (Exception e) {
            LOG.error("Error applying mod template", e);
            return host.createErrorResult("Failed to apply mod template: " + e.getMessage());
        }
    }

    private McpTypes.ToolResult createTextureSet(Map<String, Object> params) {
        String setName = (String) params.get("setName");
        @SuppressWarnings("unchecked")
        Map<String, Object> defs = params.get("textureDefinitions") instanceof Map ? (Map<String, Object>) params.get("textureDefinitions") : new HashMap<>();
        String outputFormat = (String) params.get("outputFormat");
        boolean mcmeta = "MCMETA".equalsIgnoreCase(outputFormat) || "MCMETA".equalsIgnoreCase(String.valueOf(outputFormat));

        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) return host.createErrorResult("No workspace loaded");

            List<Map<String, Object>> created = new ArrayList<>();
            for (Map.Entry<String, Object> entry : defs.entrySet()) {
                String name = entry.getKey();
                Object value = entry.getValue();
                if (!(value instanceof Map<?, ?> rawDef)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> def = (Map<String, Object>) rawDef;

                String sourcePath = (String) def.get("sourcePath");
                String base64 = (String) def.get("base64");
                String typeName = def.get("type") instanceof String s ? s : "ITEM";
                TextureType textureType = TextureType.valueOf(typeName.trim().toUpperCase(Locale.ROOT));

                BufferedImage image = loadImage(sourcePath, base64);
                if (image == null) continue;

                int width = toInt(def.get("width"), image.getWidth());
                int height = toInt(def.get("height"), image.getHeight());
                if (width != image.getWidth() || height != image.getHeight()) {
                    image = resizeImage(image, width, height);
                }

                File targetFile = workspace.getFolderManager().getTextureFile(name, textureType);
                targetFile.getParentFile().mkdirs();
                ImageIO.write(image, "png", targetFile);

                boolean animation = toBoolean(def.get("animation"), false);
                int frameTime = toInt(def.get("frameTime"), 1);
                if (animation || mcmeta) {
                    File mcmetaFile = new File(targetFile.getParentFile(), name + ".png.mcmeta");
                    String mcmetaJson = "{\"animation\":{\"frametime\":" + frameTime + "}}";
                    Files.writeString(mcmetaFile.toPath(), mcmetaJson);
                }

                Map<String, Object> info = new HashMap<>();
                info.put("name", name);
                info.put("path", targetFile.getAbsolutePath());
                info.put("width", image.getWidth());
                info.put("height", image.getHeight());
                created.add(info);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("setName", setName);
            result.put("created", created);
            return host.createSuccessResult("Texture set '" + setName + "' created:\n" + objectMapper.writeValueAsString(result));
        } catch (Exception e) {
            LOG.error("Error creating texture set", e);
            return host.createErrorResult("Failed to create texture set: " + e.getMessage());
        }
    }

    private McpTypes.ToolResult createRecipeChain(Map<String, Object> params) {
        String prefix = (String) params.get("recipeName");
        Object recipesObj = params.get("recipes");
        if (!(recipesObj instanceof List<?> rawList)) {
            return host.createErrorResult("'recipes' must be a list");
        }
        try {
            List<Map<String, Object>> recipes = new ArrayList<>();
            for (Object o : rawList) {
                if (o instanceof Map<?, ?> m) recipes.add(new HashMap<>((Map<String, Object>) m));
            }

            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) return host.createErrorResult("No workspace loaded");

            List<String> created = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            String previousOutput = null;
            for (int i = 0; i < recipes.size(); i++) {
                Map<String, Object> recipe = recipes.get(i);
                Object inputs = recipe.get("inputs");
                if (previousOutput != null && inputs instanceof List<?> list) {
                    boolean found = false;
                    for (Object in : list) {
                        String inStr = extractItemReference(in);
                        if (previousOutput.equalsIgnoreCase(inStr)) found = true;
                    }
                    if (!found) warnings.add("Recipe step " + i + " does not use output of previous step (" + previousOutput + ")");
                }

                String elementName = uniqueElementName(workspace, prefix + "Step" + i);
                Map<String, Object> createParams = new HashMap<>();
                createParams.put("elementName", elementName);
                createParams.put("properties", recipe);
                McpTypes.ToolResult result = host.createTypedElement(mcreator, "recipe", createParams);
                if (Boolean.TRUE.equals(result.getIsError())) return result;

                created.add(elementName);
                previousOutput = extractItemReference(recipe.get("output"));
            }

            Map<String, Object> result = new HashMap<>();
            result.put("createdRecipes", created);
            result.put("warnings", warnings);
            return host.createSuccessResult("Recipe chain created:\n" + objectMapper.writeValueAsString(result));
        } catch (Exception e) {
            LOG.error("Error creating recipe chain", e);
            return host.createErrorResult("Failed to create recipe chain: " + e.getMessage());
        }
    }

    private McpTypes.ToolResult createModelFromDefinition(Map<String, Object> params) {
        String modelName = (String) params.get("modelName");
        String modelType = (String) params.get("modelType");
        @SuppressWarnings("unchecked")
        Map<String, Object> def = params.get("modelDefinition") instanceof Map ? (Map<String, Object>) params.get("modelDefinition") : new HashMap<>();

        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) return host.createErrorResult("No workspace loaded");

            String modId = workspace.getWorkspaceSettings().getModID();
            String kind = "block".equalsIgnoreCase(modelType) ? "block" : "item";
            File modelFile = new File(workspace.getFolderManager().getWorkspaceFolder(),
                    "src/main/resources/assets/" + modId + "/models/" + kind + "/" + modelName + ".json");
            modelFile.getParentFile().mkdirs();

            Map<String, Object> modelJson = new LinkedHashMap<>();
            String parent = def.get("parent") instanceof String s ? s : ("block".equals(kind) ? "block/cube_all" : "item/generated");
            modelJson.put("parent", parent);

            if (def.get("textures") instanceof Map<?, ?> textures) {
                modelJson.put("textures", textures);
            }
            if (def.get("elements") instanceof List<?> elements) {
                modelJson.put("elements", elements);
            }
            if (def.get("display") instanceof Map<?, ?> display) {
                modelJson.put("display", display);
            }

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(modelFile, modelJson);
            return host.createSuccessResult("Model saved to " + modelFile.getAbsolutePath());
        } catch (Exception e) {
            LOG.error("Error creating model", e);
            return host.createErrorResult("Failed to create model: " + e.getMessage());
        }
    }

    private McpTypes.ToolResult createSoundEvent(Map<String, Object> params) {
        String soundName = (String) params.get("soundName");
        String audioFile = (String) params.get("audioFile");
        String category = (String) params.get("category");
        String subtitleKey = (String) params.get("subtitleKey");

        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) return host.createErrorResult("No workspace loaded");

            File soundsDir = workspace.getFolderManager().getSoundsDir();
            soundsDir.mkdirs();
            File target = new File(soundsDir, soundName + ".ogg");

            if (audioFile != null && audioFile.startsWith("data:")) {
                byte[] bytes = decodeBase64DataUri(audioFile);
                Files.write(target.toPath(), bytes);
            } else if (audioFile != null) {
                File source = new File(audioFile);
                if (!source.exists()) return host.createErrorResult("Audio file not found: " + audioFile);
                Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            List<String> files = new ArrayList<>();
            files.add(soundName);
            String cat = category != null ? category.toLowerCase(Locale.ROOT) : "master";
            // The generator creates the subtitle key as subtitles.<soundName> by default.
            String subtitle = subtitleKey != null ? subtitleKey : "subtitles." + soundName;
            SoundElement sound = new SoundElement(soundName, files, cat, subtitle);
            workspace.removeSoundElement(sound);
            workspace.addSoundElement(sound);

            String displayName = host.capitalize(soundName) + " sound";
            addLocalization(workspace, "en_us", subtitle, displayName);

            workspace.markDirty();
            return host.createSuccessResult("Sound event '" + soundName + "' registered at " + target.getAbsolutePath());
        } catch (Exception e) {
            LOG.error("Error creating sound event", e);
            return host.createErrorResult("Failed to create sound event: " + e.getMessage());
        }
    }

    private McpTypes.ToolResult createParticleEffect(Map<String, Object> params) {
        String name = (String) params.get("particleName");
        String texture = (String) params.get("texture");
        Object animationDef = params.get("animationDef");
        Object physics = params.get("physics");

        Map<String, Object> props = new HashMap<>();
        if (texture != null) props.put("texture", texture);
        if (animationDef instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) props.put(String.valueOf(e.getKey()), e.getValue());
        }
        if (physics instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) props.put(String.valueOf(e.getKey()), e.getValue());
        }

        Map<String, Object> createParams = new HashMap<>();
        createParams.put("elementName", name);
        createParams.put("properties", props);
        return host.createTypedElement(mcreator, "particle", createParams);
    }

    // ------------------------------------------------------------------
    // Generic element editing
    // ------------------------------------------------------------------

    private McpTypes.ToolResult updateElementProperties(Map<String, Object> params) {
        String elementName = (String) params.get("elementName");
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = params.get("properties") instanceof Map ? (Map<String, Object>) params.get("properties") : new HashMap<>();

        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) return host.createErrorResult("No workspace loaded");

            ModElement modElement = workspace.getModElementByName(elementName);
            if (modElement == null) return host.createErrorResult("Element not found: " + elementName);

            GeneratableElement ge = modElement.getGeneratableElement();
            if (ge == null) return host.createErrorResult("Element has no generatable data");

            new McpElementPropertyApplier(workspace, modElement.getType().getRegistryName(), elementName)
                    .applyProperties(ge, properties);

            try {
                GEValidator.validateAndTryToCorrect(ge, null);
            } catch (Exception e) {
                LOG.warn("GE validation after update for {}: {}", elementName, e.getMessage());
            }

            workspace.getModElementManager().storeModElement(ge);
            workspace.markDirty();

            return host.createSuccessResult("Updated properties for '" + elementName + "'");
        } catch (Exception e) {
            LOG.error("Error updating element properties", e);
            return host.createErrorResult("Failed to update element properties: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Bedrock pack tools
    // ------------------------------------------------------------------

    private McpTypes.ToolResult createBedrockTexturePack(Map<String, Object> params) {
        return createBedrockResourcePack(params);
    }

    private McpTypes.ToolResult createBedrockResourcePack(Map<String, Object> params) {
        String packName = (String) params.get("packName");
        String version = (String) params.get("version");
        String description = (String) params.get("description");

        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) return host.createErrorResult("No workspace loaded");

            File packDir = new File(workspace.getFolderManager().getWorkspaceFolder(), "bedrock/resource_packs/" + packName);
            packDir.mkdirs();
            writeBedrockManifest(packDir, packName, description, version, "resources", null);

            new File(packDir, "textures/blocks").mkdirs();
            new File(packDir, "textures/items").mkdirs();
            new File(packDir, "textures/entity").mkdirs();
            new File(packDir, "textures/environment").mkdirs();

            return host.createSuccessResult("Bedrock resource pack created at " + packDir.getAbsolutePath());
        } catch (Exception e) {
            LOG.error("Error creating Bedrock resource pack", e);
            return host.createErrorResult("Failed to create Bedrock resource pack: " + e.getMessage());
        }
    }

    private McpTypes.ToolResult createBedrockBehaviorPack(Map<String, Object> params) {
        String packName = (String) params.get("packName");
        String version = (String) params.get("version");
        String description = (String) params.get("description");

        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) return host.createErrorResult("No workspace loaded");

            File packDir = new File(workspace.getFolderManager().getWorkspaceFolder(), "bedrock/behavior_packs/" + packName);
            packDir.mkdirs();
            writeBedrockManifest(packDir, packName, description, version, "data", null);

            new File(packDir, "entities").mkdirs();
            new File(packDir, "blocks").mkdirs();
            new File(packDir, "recipes").mkdirs();
            new File(packDir, "loot_tables").mkdirs();
            new File(packDir, "functions").mkdirs();

            return host.createSuccessResult("Bedrock behavior pack created at " + packDir.getAbsolutePath());
        } catch (Exception e) {
            LOG.error("Error creating Bedrock behavior pack", e);
            return host.createErrorResult("Failed to create Bedrock behavior pack: " + e.getMessage());
        }
    }

    private McpTypes.ToolResult buildBedrockProject(Map<String, Object> params) {
        String packName = (String) params.get("packName");
        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) return host.createErrorResult("No workspace loaded");

            File root = workspace.getFolderManager().getWorkspaceFolder();
            List<String> packaged = new ArrayList<>();

            String prefix = packName != null ? packName : "";

            File rpDir = new File(root, "bedrock/resource_packs");
            if (rpDir.isDirectory()) {
                for (File f : rpDir.listFiles(File::isDirectory)) {
                    if (prefix.isEmpty() || f.getName().startsWith(prefix)) {
                        File out = new File(root, f.getName() + ".mcpack");
                        zipDirectory(f.toPath(), out.toPath());
                        packaged.add(out.getAbsolutePath());
                    }
                }
            }

            File bpDir = new File(root, "bedrock/behavior_packs");
            if (bpDir.isDirectory()) {
                for (File f : bpDir.listFiles(File::isDirectory)) {
                    if (prefix.isEmpty() || f.getName().startsWith(prefix)) {
                        File out = new File(root, f.getName() + "_behavior.mcpack");
                        zipDirectory(f.toPath(), out.toPath());
                        packaged.add(out.getAbsolutePath());
                    }
                }
            }

            if (packaged.isEmpty()) return host.createErrorResult("No Bedrock packs found for " + packName);
            return host.createSuccessResult("Packaged Bedrock packs:\n" + packaged);
        } catch (Exception e) {
            LOG.error("Error building Bedrock project", e);
            return host.createErrorResult("Failed to build Bedrock project: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Test / reporting
    // ------------------------------------------------------------------

    private McpTypes.ToolResult generateTestReport(Map<String, Object> params) {
        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) return host.createErrorResult("No workspace loaded");

            String logPath = (String) params.get("logPath");
            File logFile = logPath != null ? new File(logPath) :
                    new File(workspace.getFolderManager().getWorkspaceFolder(), "run/logs/latest.log");

            Map<String, Object> report = new HashMap<>();
            report.put("workspace", workspace.getWorkspaceSettings().getModID());
            report.put("modName", workspace.getWorkspaceSettings().getModName());
            report.put("elementCount", workspace.getModElements().size());

            File jar = new File(workspace.getFolderManager().getWorkspaceFolder(), "build/libs/" + workspace.getWorkspaceSettings().getModID() + "-1.0.jar");
            report.put("jarExists", jar.exists());
            report.put("jarSize", jar.exists() ? jar.length() : 0);

            int errors = 0, warnings = 0, missingTextures = 0, recipeErrors = 0;
            List<String> errorSamples = new ArrayList<>();
            List<String> warningSamples = new ArrayList<>();

            if (logFile.exists()) {
                List<String> lines = Files.readAllLines(logFile.toPath());
                for (String line : lines) {
                    if (line.contains("ERROR")) {
                        errors++;
                        if (errorSamples.size() < 10) errorSamples.add(line);
                    }
                    if (line.contains("WARN")) {
                        warnings++;
                        if (warningSamples.size() < 10) warningSamples.add(line);
                    }
                    if (line.toLowerCase(Locale.ROOT).contains("missing texture")) missingTextures++;
                    if (line.toLowerCase(Locale.ROOT).contains("parsing error loading recipe")) recipeErrors++;
                }
                report.put("logFile", logFile.getAbsolutePath());
            } else {
                report.put("logFile", null);
            }

            report.put("errors", errors);
            report.put("warnings", warnings);
            report.put("missingTextureWarnings", missingTextures);
            report.put("recipeParseErrors", recipeErrors);
            report.put("errorSamples", errorSamples);
            report.put("warningSamples", warningSamples);

            return host.createSuccessResult("Test report generated:\n" + objectMapper.writeValueAsString(report));
        } catch (Exception e) {
            LOG.error("Error generating test report", e);
            return host.createErrorResult("Failed to generate test report: " + e.getMessage());
        }
    }

    private McpTypes.ToolResult compareElementVersions(Map<String, Object> params) {
        String elementName = (String) params.get("elementName");
        String version1 = (String) params.get("version1");
        String version2 = (String) params.get("version2");

        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) return host.createErrorResult("No workspace loaded");

            File workspaceFolder = workspace.getFolderManager().getWorkspaceFolder();
            File currentFile = workspace.getFileManager().getWorkspaceFile();
            File backupsDir = new File(workspaceFolder, ".mcreator/workspaceBackups");

            JsonNode node1 = "current".equalsIgnoreCase(version1) || version1 == null
                    ? objectMapper.readTree(currentFile)
                    : readBackup(backupsDir, version1);
            JsonNode node2 = version2 == null || "latest".equalsIgnoreCase(version2) || "current".equalsIgnoreCase(version2)
                    ? (version2 != null && "current".equalsIgnoreCase(version2) ? objectMapper.readTree(currentFile) : readLatestBackup(backupsDir))
                    : readBackup(backupsDir, version2);

            if (node1 == null) return host.createErrorResult("Could not find version1: " + version1);
            if (node2 == null) return host.createErrorResult("Could not find version2: " + version2);

            JsonNode elem1 = findElementNode(node1, elementName);
            JsonNode elem2 = findElementNode(node2, elementName);

            if (elem1 == null) return host.createErrorResult("Element '" + elementName + "' not found in version1");
            if (elem2 == null) return host.createErrorResult("Element '" + elementName + "' not found in version2");

            List<String> diffs = computeJsonDiff(elem1, elem2, "");

            Map<String, Object> result = new HashMap<>();
            result.put("elementName", elementName);
            result.put("version1", version1);
            result.put("version2", version2);
            result.put("version1Json", elem1);
            result.put("version2Json", elem2);
            result.put("differences", diffs);
            return host.createSuccessResult("Element version comparison:\n" + objectMapper.writeValueAsString(result));
        } catch (Exception e) {
            LOG.error("Error comparing element versions", e);
            return host.createErrorResult("Failed to compare element versions: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private String createFromTemplate(String type, String baseName, Map<String, Object> properties) throws Exception {
        Workspace workspace = mcreator.getWorkspace();
        String name = uniqueElementName(workspace, baseName);
        Map<String, Object> params = new HashMap<>();
        params.put("elementName", name);
        params.put("properties", properties);
        McpTypes.ToolResult result = host.createTypedElement(mcreator, type, params);
        if (Boolean.TRUE.equals(result.getIsError())) throw new RuntimeException("Failed to create " + type + " " + name + ": " + result);
        return name;
    }

    private void createProcedure(String name, String xml) throws Exception {
        Map<String, Object> props = new HashMap<>();
        props.put("procedurexml", xml);
        Map<String, Object> params = new HashMap<>();
        params.put("elementName", name);
        params.put("properties", props);
        McpTypes.ToolResult result = host.createTypedElement(mcreator, "procedure", params);
        if (Boolean.TRUE.equals(result.getIsError())) throw new RuntimeException("Failed to create procedure " + name);
    }

    private void updateProcedureXml(String name, String xml) {
        try {
            Workspace workspace = mcreator.getWorkspace();
            ModElement me = workspace.getModElementByName(name);
            if (me == null) return;
            GeneratableElement ge = me.getGeneratableElement();
            if (ge instanceof net.mcreator.element.types.Procedure proc) {
                proc.procedurexml = xml;
                workspace.getModElementManager().storeModElement(proc);
                workspace.markDirty();
            }
        } catch (Exception e) {
            LOG.warn("Could not update procedure XML for {}: {}", name, e.getMessage());
        }
    }

    private String getProcedureXml(Workspace workspace, String procedureName) {
        if (procedureName == null || procedureName.isEmpty()) return null;
        try {
            ModElement me = workspace.getModElementByName(procedureName);
            if (me == null) return null;
            GeneratableElement ge = me.getGeneratableElement();
            if (ge instanceof net.mcreator.element.types.Procedure proc) return proc.procedurexml;
        } catch (Exception e) {
            LOG.warn("Could not read procedure XML for {}: {}", procedureName, e.getMessage());
        }
        return null;
    }

    private Procedure newProcedureInstance(Class<?> type, String name) {
        try {
            if (type == net.mcreator.element.parts.procedure.Procedure.class) {
                return new net.mcreator.element.parts.procedure.Procedure(name);
            }
            if (type == StringProcedure.class) {
                return new StringProcedure(name, "");
            }
            if (type == NumberProcedure.class) {
                return new NumberProcedure(name, 0.0);
            }
            if (type == LogicProcedure.class) {
                return new LogicProcedure(name, false);
            }
            if (type == StringListProcedure.class) {
                return new StringListProcedure(name, Collections.emptyList());
            }
            for (java.lang.reflect.Constructor<?> ctor : type.getDeclaredConstructors()) {
                ctor.setAccessible(true);
                Class<?>[] params = ctor.getParameterTypes();
                if (params.length == 2 && params[0] == String.class) {
                    Object second = defaultProcedureReturnValue(params[1]);
                    if (second != null) return (Procedure) ctor.newInstance(name, second);
                }
            }
            return new net.mcreator.element.parts.procedure.Procedure(name);
        } catch (Exception e) {
            LOG.warn("Could not create procedure instance of type {}: {}", type, e.getMessage());
            return new net.mcreator.element.parts.procedure.Procedure(name);
        }
    }

    private Object defaultProcedureReturnValue(Class<?> type) {
        if (type == String.class) return "";
        if (type == double.class || type == Double.class) return 0.0;
        if (type == boolean.class || type == Boolean.class) return false;
        if (List.class.isAssignableFrom(type)) return Collections.emptyList();
        return null;
    }

    private String uniqueElementName(Workspace workspace, String base) {
        if (workspace.getModElementByName(base) == null) return base;
        int i = 2;
        while (workspace.getModElementByName(base + i) != null) i++;
        return base + i;
    }

    private String extractItemReference(Object output) {
        if (output instanceof Map<?, ?> map) {
            Object item = map.get("item");
            return item != null ? String.valueOf(item) : "";
        }
        return output != null ? String.valueOf(output) : "";
    }

    private BufferedImage loadImage(String sourcePath, String base64) throws Exception {
        if (base64 != null && !base64.isEmpty()) {
            if (base64.startsWith("data:")) {
                int comma = base64.indexOf(',');
                if (comma > 0) base64 = base64.substring(comma + 1);
            }
            byte[] bytes = Base64.getDecoder().decode(base64);
            try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
                return ImageIO.read(bais);
            }
        }
        if (sourcePath != null && !sourcePath.isEmpty()) {
            return ImageIO.read(new File(sourcePath));
        }
        return null;
    }

    private byte[] decodeBase64DataUri(String dataUri) {
        String base64 = dataUri;
        int comma = base64.indexOf(',');
        if (comma > 0) base64 = base64.substring(comma + 1);
        return Base64.getDecoder().decode(base64);
    }

    private BufferedImage resizeImage(BufferedImage source, int width, int height) {
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(source, 0, 0, width, height, null);
        g.dispose();
        return scaled;
    }

    private void createPlaceholderColorTexture(Workspace workspace, TextureType type, String name, String colorHex) {
        try {
            File file = workspace.getFolderManager().getTextureFile(name, type);
            file.getParentFile().mkdirs();
            if (file.exists()) return;
            int c = parseColor(colorHex);
            BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setColor(new Color(c, true));
            g.fillRect(0, 0, 16, 16);
            g.setColor(Color.WHITE);
            g.drawRect(0, 0, 15, 15);
            g.dispose();
            ImageIO.write(img, "png", file);
        } catch (Exception e) {
            LOG.warn("Could not create placeholder texture {}: {}", name, e.getMessage());
        }
    }

    private int parseColor(String hex) {
        if (hex == null || hex.isEmpty()) return 0xFF2C9EEC;
        hex = hex.replace("#", "");
        if (hex.length() == 6) hex = "FF" + hex;
        try {
            return (int) Long.parseLong(hex, 16);
        } catch (Exception e) {
            return 0xFF2C9EEC;
        }
    }

    private void writeBedrockManifest(File packDir, String name, String description, String version, String moduleType, UUID resourceUuid) throws IOException {
        UUID packUuid = UUID.randomUUID();
        UUID moduleUuid = UUID.randomUUID();
        int[] v = parseVersion(version);

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("name", name);
        header.put("description", description != null ? description : name + " pack");
        header.put("uuid", packUuid.toString());
        header.put("version", v);
        header.put("min_engine_version", List.of(1, 20, 0));

        Map<String, Object> module = new LinkedHashMap<>();
        module.put("type", moduleType);
        module.put("uuid", moduleUuid.toString());
        module.put("version", v);

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("format_version", 2);
        manifest.put("header", header);
        manifest.put("modules", List.of(module));

        if (resourceUuid != null) {
            Map<String, Object> dep = new LinkedHashMap<>();
            dep.put("uuid", resourceUuid.toString());
            dep.put("version", v);
            manifest.put("dependencies", List.of(dep));
        }

        objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(packDir, "manifest.json"), manifest);
    }

    private int[] parseVersion(String version) {
        if (version == null || version.isEmpty()) version = "1.0.0";
        String[] parts = version.split("\\.");
        int[] v = new int[3];
        for (int i = 0; i < Math.min(parts.length, 3); i++) {
            try { v[i] = Integer.parseInt(parts[i]); } catch (Exception ignored) {}
        }
        return v;
    }

    private void zipDirectory(Path source, Path target) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(target)))) {
            Files.walk(source).forEach(path -> {
                if (path.equals(source)) return;
                String zipEntryName = source.relativize(path).toString().replace("\\", "/");
                try {
                    if (Files.isDirectory(path)) {
                        if (!zipEntryName.isEmpty()) zos.putNextEntry(new ZipEntry(zipEntryName + "/"));
                    } else {
                        zos.putNextEntry(new ZipEntry(zipEntryName));
                        Files.copy(path, zos);
                    }
                    zos.closeEntry();
                } catch (IOException e) {
                    LOG.warn("Could not zip {}: {}", path, e.getMessage());
                }
            });
        }
    }

    private void addLocalization(Workspace workspace, String language, String key, String value) {
        try {
            Map<String, ? extends Map<String, String>> map = workspace.getLanguageMap();
            Map<String, String> lang = map.get(language);
            if (lang == null) {
                LinkedHashMap<String, String> newLang = new LinkedHashMap<>();
                newLang.put(key, value);
                workspace.addLanguage(language, newLang);
            } else {
                lang.put(key, value);
            }
        } catch (Exception e) {
            LOG.warn("Could not add localization {}: {}", key, e.getMessage());
        }
    }

    private JsonNode readBackup(File backupsDir, String name) throws IOException {
        if (!backupsDir.exists()) return null;
        File[] files = backupsDir.listFiles((dir, n) -> n.contains(name));
        if (files == null || files.length == 0) return null;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        return objectMapper.readTree(files[0]);
    }

    private JsonNode readLatestBackup(File backupsDir) throws IOException {
        if (!backupsDir.exists()) return null;
        File[] files = backupsDir.listFiles();
        if (files == null || files.length == 0) return null;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        return objectMapper.readTree(files[0]);
    }

    private JsonNode findElementNode(JsonNode root, String elementName) {
        if (root == null) return null;
        JsonNode modElements = root.get("mod_elements");
        if (modElements == null || !modElements.isArray()) return null;
        for (JsonNode node : modElements) {
            JsonNode nameNode = node.get("name");
            if (nameNode != null && nameNode.asText().equals(elementName)) return node;
        }
        return null;
    }

    private List<String> computeJsonDiff(JsonNode a, JsonNode b, String path) {
        List<String> diffs = new ArrayList<>();
        if (a == null && b == null) return diffs;
        if (a == null) { diffs.add(path + " added in version2"); return diffs; }
        if (b == null) { diffs.add(path + " removed in version2"); return diffs; }
        if (a.isObject() && b.isObject()) {
            Iterator<String> it = ((ObjectNode) a).fieldNames();
            while (it.hasNext()) {
                String field = it.next();
                diffs.addAll(computeJsonDiff(a.get(field), b.get(field), path + "/" + field));
            }
            Iterator<String> it2 = ((ObjectNode) b).fieldNames();
            while (it2.hasNext()) {
                String field = it2.next();
                if (a.get(field) == null) diffs.add(path + "/" + field + " added in version2");
            }
        } else if (a.isArray() && b.isArray()) {
            for (int i = 0; i < Math.max(a.size(), b.size()); i++) {
                diffs.addAll(computeJsonDiff(i < a.size() ? a.get(i) : null, i < b.size() ? b.get(i) : null, path + "[" + i + "]"));
            }
        } else if (!a.toString().equals(b.toString())) {
            diffs.add(path + " changed: '" + a + "' -> '" + b + "'");
        }
        return diffs;
    }

    private List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        while (clazz != null && clazz != Object.class) {
            fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
            clazz = clazz.getSuperclass();
        }
        return fields;
    }

    private Field findFieldIgnoreCase(Class<?> clazz, String name) {
        for (Field field : getAllFields(clazz)) {
            if (field.getName().equalsIgnoreCase(name)) return field;
        }
        return null;
    }

    private String sanitizeJavaName(String name) {
        StringBuilder sb = new StringBuilder();
        boolean capitalize = true;
        for (char c : name.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                sb.append(capitalize ? Character.toUpperCase(c) : c);
                capitalize = false;
            } else {
                capitalize = true;
            }
        }
        return sb.isEmpty() ? "Procedure" : sb.toString();
    }

    private int toInt(Object o, int defaultValue) {
        if (o == null) return defaultValue;
        try {
            if (o instanceof Number n) return n.intValue();
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private boolean toBoolean(Object o, boolean defaultValue) {
        if (o == null) return defaultValue;
        if (o instanceof Boolean b) return b;
        return Boolean.parseBoolean(String.valueOf(o));
    }

    private McpTypes.ToolResult listProcedureTemplates(Map<String, Object> params) {
        try {
            List<Map<String, Object>> list = new ArrayList<>();
            for (ProcedureTemplate template : PROCEDURE_TEMPLATES.values()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", template.name());
                m.put("description", template.description());
                m.put("defaultValues", template.defaultValues());
                list.add(m);
            }
            return host.createSuccessResult(objectMapper.writeValueAsString(list));
        } catch (Exception e) {
            return host.createErrorResult("Failed to list procedure templates: " + e.getMessage());
        }
    }

    private McpTypes.ToolResult applyProcedureTemplate(Map<String, Object> params) {
        String templateName = (String) params.get("templateName");
        String elementName = (String) params.get("elementName");
        String eventType = (String) params.get("eventType");
        if (templateName == null || elementName == null || eventType == null)
            return host.createErrorResult("templateName, elementName, and eventType are required");

        String procedureName = (String) params.get("procedureName");
        if (procedureName == null || procedureName.isEmpty())
            procedureName = sanitizeJavaName(elementName + "_" + eventType + "_" + templateName);

        Map<String, Object> values = new HashMap<>();
        Object v = params.get("values");
        if (v instanceof Map<?, ?> vm) values.putAll((Map<String, Object>) vm);

        String xml = buildProcedureXml(templateName, values);
        return doUpdateEventProcedure(elementName, eventType, procedureName, xml);
    }

    private String buildProcedureXml(String templateName, Map<String, Object> values) {
        ProcedureTemplate template = PROCEDURE_TEMPLATES.getOrDefault(templateName, PROCEDURE_TEMPLATES.get("empty"));
        Map<String, String> defaults = new HashMap<>(template.defaultValues());
        for (Map.Entry<String, Object> e : values.entrySet()) {
            if (e.getValue() != null) defaults.put(e.getKey(), String.valueOf(e.getValue()));
        }

        String item = escapeXml(defaults.getOrDefault("item", "minecraft:diamond"));
        String amount = escapeXml(defaults.getOrDefault("amount", "1"));
        String message = escapeXml(defaults.getOrDefault("message", "Hello!"));
        String actbar = defaults.getOrDefault("actbar", "false");
        String command = escapeXml(defaults.getOrDefault("command", "say Hello"));
        String block = escapeXml(defaults.getOrDefault("block", "minecraft:stone"));
        String entity = escapeXml(defaults.getOrDefault("entity", "minecraft:cow"));
        String potion = escapeXml(defaults.getOrDefault("potion", "minecraft:regeneration"));
        String level = escapeXml(defaults.getOrDefault("level", "1"));
        String duration = escapeXml(defaults.getOrDefault("duration", "60"));

        String blockType;
        StringBuilder body = new StringBuilder();
        switch (templateName) {
        case "give_item" -> {
            blockType = "entity_add_item";
            body.append(valueXml("item", "mcitem_all", "<field name=\"value\">" + item + "</field>"));
            body.append(valueXml("amount", "math_number", "<field name=\"NUM\">" + amount + "</field>"));
            body.append(valueXml("entity", "entity_from_deps", ""));
        }
        case "send_message" -> {
            blockType = "entity_send_chat";
            body.append(valueXml("text", "text", "<field name=\"TEXT\">" + message + "</field>"));
            body.append(valueXml("actbar", "logic_boolean", "<field name=\"BOOL\">" + actbar.toUpperCase(Locale.ROOT) + "</field>"));
            body.append(valueXml("entity", "entity_from_deps", ""));
        }
        case "execute_command" -> {
            blockType = "execute_command";
            body.append(valueXml("command", "text", "<field name=\"TEXT\">" + command + "</field>"));
            body.append(valueXml("x", "coord_x", ""));
            body.append(valueXml("y", "coord_y", ""));
            body.append(valueXml("z", "coord_z", ""));
        }
        case "set_block" -> {
            blockType = "block_add";
            body.append(valueXml("block", "mcitem_allblocks", "<field name=\"value\">" + block + "</field>"));
            body.append(valueXml("x", "coord_x", ""));
            body.append(valueXml("y", "coord_y", ""));
            body.append(valueXml("z", "coord_z", ""));
        }
        case "spawn_entity" -> {
            blockType = "spawn_entity";
            body.append("<field name=\"entity\">").append(entity).append("</field>");
            body.append(valueXml("x", "coord_x", ""));
            body.append(valueXml("y", "coord_y", ""));
            body.append(valueXml("z", "coord_z", ""));
        }
        case "apply_potion" -> {
            blockType = "entity_add_potion";
            body.append(valueXml("level", "math_number", "<field name=\"NUM\">" + level + "</field>"));
            body.append(valueXml("duration", "math_number", "<field name=\"NUM\">" + duration + "</field>"));
            body.append(valueXml("entity", "entity_from_deps", ""));
            body.append("<field name=\"potion\">").append(potion).append("</field>");
        }
        default -> {
            return PROCEDURE_XML_BASE;
        }
        }
        return "<xml xmlns=\"https://developers.google.com/blockly/xml\"><block type=\"event_trigger\" deletable=\"false\" x=\"40\" y=\"40\"><field name=\"trigger\">no_ext_trigger</field><next><block type=\"" + blockType + "\">" + body + "</block></next></block></xml>";
    }

    private String valueXml(String name, String blockType, String inner) {
        return "<value name=\"" + name + "\"><block type=\"" + blockType + "\">" + inner + "</block></value>";
    }

    private String escapeXml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private McpTypes.ToolResult exportBedrockAddon(Map<String, Object> params) {
        String packName = (String) params.get("packName");
        String outputPath = (String) params.get("outputPath");
        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) return host.createErrorResult("No workspace loaded");
            File root = workspace.getFolderManager().getWorkspaceFolder();
            File bedrockDir = new File(root, "bedrock");
            File rpDir = new File(bedrockDir, "resource_packs");
            File bpDir = new File(bedrockDir, "behavior_packs");
            if (!rpDir.isDirectory() && !bpDir.isDirectory())
                return host.createErrorResult("No Bedrock packs found in " + bedrockDir);

            File tempDir = Files.createTempDirectory("mcp_bedrock_addon").toFile();
            List<String> added = new ArrayList<>();
            if (rpDir.isDirectory()) {
                for (File f : rpDir.listFiles(File::isDirectory)) {
                    if (packName == null || packName.isEmpty() || f.getName().startsWith(packName)) {
                        File dest = new File(tempDir, f.getName());
                        copyDirectory(f.toPath(), dest.toPath());
                        added.add("RP:" + f.getName());
                    }
                }
            }
            if (bpDir.isDirectory()) {
                for (File f : bpDir.listFiles(File::isDirectory)) {
                    if (packName == null || packName.isEmpty() || f.getName().startsWith(packName)) {
                        File dest = new File(tempDir, f.getName() + "_behavior");
                        copyDirectory(f.toPath(), dest.toPath());
                        added.add("BP:" + f.getName());
                    }
                }
            }
            if (added.isEmpty()) return host.createErrorResult("No matching Bedrock packs for prefix " + packName);

            File out = outputPath != null && !outputPath.isEmpty() ? new File(outputPath) :
                    new File(root, ((packName != null && !packName.isEmpty()) ? packName : workspace.getWorkspaceSettings().getModID()) + ".mcaddon");
            out.getParentFile().mkdirs();
            zipDirectory(tempDir.toPath(), out.toPath());
            deleteDirectory(tempDir);
            return host.createSuccessResult("Exported Bedrock addon to " + out.getAbsolutePath() + " containing " + added);
        } catch (Exception e) {
            LOG.error("Error exporting Bedrock addon", e);
            return host.createErrorResult("Failed to export Bedrock addon: " + e.getMessage());
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walk(source).forEach(path -> {
            try {
                Path dest = target.resolve(source.relativize(path));
                if (Files.isDirectory(path)) {
                    Files.createDirectories(dest);
                } else {
                    Files.copy(path, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) deleteDirectory(f);
                else f.delete();
            }
        }
        dir.delete();
    }
}

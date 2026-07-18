/*
 * MCreatorMCP extra tools: tags, creative tabs, backups, generator switching,
 * procedure editing, in-game verification, and model validation/conversion.
 * SPDX-License-Identifier: GPL-2.0-only
 */
package net.mcreator.MCreatorMCP;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.mcreator.MCreatorMCP.mcp.McpServer;
import net.mcreator.MCreatorMCP.mcp.McpTypes;
import net.mcreator.element.GeneratableElement;
import net.mcreator.element.ModElementType;
import net.mcreator.element.types.Procedure;
import net.mcreator.element.parts.TabEntry;
import net.mcreator.element.types.interfaces.ITabContainedElement;
import net.mcreator.generator.mapping.MappableElement;
import net.mcreator.minecraft.TagType;
import net.mcreator.ui.MCreator;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.ModElement;
import net.mcreator.workspace.elements.TagElement;
import net.mcreator.workspace.localhistory.HistoryCheckpoint;
import net.mcreator.workspace.localhistory.HistoryManager;
import net.mcreator.workspace.localhistory.LocalHistoryException;
import net.mcreator.workspace.misc.CreativeTabsOrder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class McpExtraToolsService {

    private static final Logger LOG = LogManager.getLogger("MCP-ExtraTools");

    private final MCPToolsService host;
    private final McpServer mcpServer;
    private final MCreator mcreator;
    private final ObjectMapper objectMapper;

    public McpExtraToolsService(MCPToolsService host, McpServer mcpServer, MCreator mcreator) {
        this.host = host;
        this.mcpServer = mcpServer;
        this.mcreator = mcreator;
        this.objectMapper = new ObjectMapper();
    }

    public void registerTools() {
        // Tag management
        mcpServer.registerTool("createTag", "Create a data/resource tag (BLOCKS, ITEMS, ENTITIES, etc.)",
                host.objectSchema(host.props(
                        "tagType", host.stringSchema("Tag type: BLOCKS, ITEMS, ENTITIES, FUNCTIONS, BIOMES, etc."),
                        "tagName", host.stringSchema("Tag name/path (e.g. my_ores or minecraft:my_ores)"),
                        "entries", host.objectSchema(host.props(), new String[]{})
                ), "tagType", "tagName"),
                params -> createTag(params));
        mcpServer.registerTool("updateTag", "Update the entries of an existing tag",
                host.objectSchema(host.props(
                        "tagType", host.stringSchema("Tag type"),
                        "tagName", host.stringSchema("Tag name/path"),
                        "entries", host.objectSchema(host.props(), new String[]{})
                ), "tagType", "tagName"),
                params -> updateTag(params));
        mcpServer.registerTool("deleteTag", "Delete a tag",
                host.objectSchema(host.props(
                        "tagType", host.stringSchema("Tag type"),
                        "tagName", host.stringSchema("Tag name/path")
                ), "tagType", "tagName"),
                params -> deleteTag(params));
        mcpServer.registerTool("listTags", "List all tags and their entries",
                host.objectSchema(host.props(
                        "tagType", host.stringSchema("Optional tag type filter")
                )),
                params -> listTags(params));

        // Creative tab management
        mcpServer.registerTool("createCreativeTab", "Create a custom creative inventory tab",
                host.objectSchema(host.props(
                        "tabName", host.stringSchema("Element/registry name for the tab"),
                        "displayName", host.stringSchema("Display name"),
                        "icon", host.stringSchema("Icon item/block reference (e.g. Items.STONE or minecraft:stone)"),
                        "showSearch", host.objectSchema(host.props(), new String[]{}) // boolean handled below
                ), "tabName"),
                params -> createCreativeTab(params));
        mcpServer.registerTool("listCreativeTabs", "List custom creative tabs and tab ordering",
                host.objectSchema(Map.of()),
                params -> listCreativeTabs(params));
        mcpServer.registerTool("updateCreativeTabs", "Set the order of mod elements inside a creative tab",
                host.objectSchema(host.props(
                        "tabName", host.stringSchema("Tab name (registry name)"),
                        "elementNames", host.objectSchema(host.props(), new String[]{}) // array handled below
                ), "tabName", "elementNames"),
                params -> updateCreativeTabs(params));

        // Backup / local history
        mcpServer.registerTool("createBackup", "Create a workspace backup (local history checkpoint)",
                host.objectSchema(host.props(
                        "backupName", host.stringSchema("Backup name/label")
                )),
                params -> createBackup(params));
        mcpServer.registerTool("listBackups", "List workspace backups from local history",
                host.objectSchema(Map.of()),
                params -> listBackups(params));
        mcpServer.registerTool("restoreBackup", "Restore the workspace to a previous backup",
                host.objectSchema(host.props(
                        "backupName", host.stringSchema("Backup name, hash, or 'latest'")
                ), "backupName"),
                params -> restoreBackup(params));

        // Generator switching
        mcpServer.registerTool("switchGenerator", "Switch the workspace generator (e.g. neoforge-1.21.1, datapack-1.21.1)",
                host.objectSchema(host.props(
                        "generatorName", host.stringSchema("Generator ID (folder name from listGenerators)")
                ), "generatorName"),
                params -> switchGenerator(params));
        mcpServer.registerTool("listGenerators", "List installed MCreator generator plugins",
                host.objectSchema(Map.of()),
                params -> listGenerators(params));

        // Procedure editing
        mcpServer.registerTool("listProcedures", "List all procedure elements and their XML size",
                host.objectSchema(Map.of()),
                params -> listProcedures(params));
        mcpServer.registerTool("updateProcedure", "Replace the Blockly XML of an existing procedure",
                host.objectSchema(host.props(
                        "elementName", host.stringSchema("Procedure element name"),
                        "xml", host.stringSchema("Full Blockly XML")
                ), "elementName", "xml"),
                params -> updateProcedure(params));
        mcpServer.registerTool("deleteProcedure", "Delete a procedure element",
                host.objectSchema(host.props(
                        "elementName", host.stringSchema("Procedure element name")
                ), "elementName"),
                params -> deleteProcedure(params));

        // In-game verification
        mcpServer.registerTool("verifyServerLoads", "Start the Minecraft server and verify it reaches Done",
                host.objectSchema(host.props(
                        "elementName", host.stringSchema("Optional element name to search for in the log"),
                        "timeoutSeconds", host.objectSchema(host.props(), new String[]{}) // int handled below
                )),
                params -> verifyServerLoads(params));
        mcpServer.registerTool("verifyClientLoads", "Start the Minecraft client and verify it loads",
                host.objectSchema(host.props(
                        "elementName", host.stringSchema("Optional element name to search for in the log"),
                        "timeoutSeconds", host.objectSchema(host.props(), new String[]{}) // int handled below
                )),
                params -> verifyClientLoads(params));

        // Model validation / conversion
        mcpServer.registerTool("validateModel", "Validate a model file (JSON or OBJ) and report issues",
                host.objectSchema(host.props(
                        "sourcePath", host.stringSchema("Path to .json or .obj model file")
                ), "sourcePath"),
                params -> validateModel(params));
        mcpServer.registerTool("convertModel", "Convert an OBJ model to a simple Minecraft block JSON (single cuboid only)",
                host.objectSchema(host.props(
                        "sourcePath", host.stringSchema("Path to .obj file"),
                        "modelName", host.stringSchema("Output model name (without .json)"),
                        "texture", host.stringSchema("Texture reference (e.g. #all or block/all)")
                ), "sourcePath", "modelName"),
                params -> convertModel(params));
    }

    // ------------------------------------------------------------------
    // Tag management
    // ------------------------------------------------------------------

    private McpTypes.ToolResult createTag(Map<String, Object> params) {
        Workspace ws = mcreator.getWorkspace();
        if (ws == null) return host.createErrorResult("No workspace loaded");

        String typeName = stringParam(params, "tagType", "BLOCKS");
        String tagName = stringParam(params, "tagName");
        if (tagName == null) return host.createErrorResult("tagName is required");

        TagType tagType;
        try {
            tagType = TagType.valueOf(typeName.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return host.createErrorResult("Unknown tag type: " + typeName);
        }

        String normalized = normalizeTagName(tagName);
        String resourcePath = normalized.contains(":") ? normalized : "mod:" + normalized;
        TagElement tag = new TagElement(tagType, resourcePath);

        ws.addTagElement(tag);
        List<TagElement.Entry> entries = ws.getTagElements().get(tag);
        if (entries == null) {
            ws.getTagElements().put(tag, new ArrayList<>());
            entries = ws.getTagElements().get(tag);
        } else {
            entries.clear();
        }

        List<String> entryStrings = listString(params.get("entries"));
        if (entryStrings != null && entries != null) {
            for (String entryStr : entryStrings) {
                TagElement.Entry entry = resolveTagEntry(ws, tagType, entryStr);
                if (entry != null) entries.add(entry);
            }
        }

        ws.markDirty();
        return host.createSuccessResult("Created tag " + tagType.name().toLowerCase(Locale.ROOT) + "/" + tag.getName());
    }

    private McpTypes.ToolResult updateTag(Map<String, Object> params) {
        Workspace ws = mcreator.getWorkspace();
        if (ws == null) return host.createErrorResult("No workspace loaded");

        String typeName = stringParam(params, "tagType", "BLOCKS");
        String tagName = stringParam(params, "tagName");
        if (tagName == null) return host.createErrorResult("tagName is required");

        TagType tagType;
        try {
            tagType = TagType.valueOf(typeName.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return host.createErrorResult("Unknown tag type: " + typeName);
        }

        String normalized = normalizeTagName(tagName);
        String resourcePath = normalized.contains(":") ? normalized : "mod:" + normalized;
        TagElement tag = new TagElement(tagType, resourcePath);
        List<TagElement.Entry> existing = ws.getTagElements().get(tag);
        if (existing == null) return host.createErrorResult("Tag not found: " + typeName + ":" + tagName);

        existing.clear();
        List<String> entryStrings = listString(params.get("entries"));
        if (entryStrings != null) {
            for (String entryStr : entryStrings) {
                TagElement.Entry entry = resolveTagEntry(ws, tagType, entryStr);
                if (entry != null) existing.add(entry);
            }
        }

        ws.markDirty();
        return host.createSuccessResult("Updated tag " + typeName.toLowerCase(Locale.ROOT) + "/" + tag.getName());
    }

    private TagElement.Entry resolveTagEntry(Workspace ws, TagType tagType, String entryStr) {
        if (entryStr == null || entryStr.isEmpty()) return null;

        String upper = entryStr.toUpperCase(Locale.ROOT);

        // Tag reference (other tag)
        if (entryStr.startsWith("#") || entryStr.startsWith("TAG:")) {
            String tag = entryStr.startsWith("#") ? entryStr.substring(1) : entryStr.substring(4);
            return TagElement.Entry.unmanaged("TAG:" + tag);
        }

        // Custom workspace element (strip CUSTOM:/MOD: prefix)
        String elementName = entryStr;
        if (elementName.contains(":"))
            elementName = elementName.substring(elementName.indexOf(':') + 1);

        ModElement modElement = ws.getModElementByName(elementName);
        if (modElement == null) {
            String camel = toCamelCase(elementName);
            modElement = ws.getModElementByName(camel);
        }
        if (modElement != null) {
            return TagElement.Entry.managedBy(modElement, "CUSTOM:" + modElement.getName());
        }

        // Vanilla / external mod reference: try to resolve to a datalist key so
        // MCreator's generator can map the registry name correctly.
        String resolved = findMappingKey(ws, tagType, entryStr);
        if (resolved != null) {
            return TagElement.Entry.unmanaged(resolved);
        }

        // Fallback: unmanaged reference
        return TagElement.Entry.unmanaged(entryStr);
    }

    private String toCamelCase(String input) {
        if (input == null || input.isEmpty()) return input;
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = true;
        for (char c : input.toCharArray()) {
            if (c == '_' || c == ' ' || c == '-') {
                nextUpper = true;
            } else if (nextUpper) {
                sb.append(Character.toUpperCase(c));
                nextUpper = false;
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    private String normalizeTagName(String tagName) {
        if (tagName == null || tagName.isEmpty()) return tagName;
        String namespace = null;
        String path = tagName;
        if (tagName.contains(":")) {
            int idx = tagName.indexOf(':');
            namespace = tagName.substring(0, idx);
            path = tagName.substring(idx + 1);
        }
        StringBuilder sb = new StringBuilder();
        for (char c : path.toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == '_' || c == '/' || c == '.') {
                sb.append(Character.toLowerCase(c));
            } else if (c == ' ' || c == '-') {
                sb.append('_');
            }
        }
        String result = sb.toString();
        if (namespace != null)
            return namespace.toLowerCase(Locale.ROOT) + ":" + result;
        return result;
    }

    private String findMappingKey(Workspace ws, TagType tagType, String value) {
        String mappingSource = getTagMappingSource(tagType);
        Integer mapIndex = getTagMapTypeIndex(tagType);
        if (mappingSource == null || mapIndex == null)
            return null;

        Map<?, ?> mapping;
        try {
            mapping = ws.getGenerator().getMappings().getMapping(mappingSource);
        } catch (Exception e) {
            LOG.warn("Could not load mapping source '{}': {}", mappingSource, e.getMessage());
            return null;
        }
        if (mapping == null)
            return null;

        String cleanValue = value;
        if (cleanValue.contains(":"))
            cleanValue = cleanValue.substring(cleanValue.indexOf(':') + 1);

        // Direct key match
        if (mapping.containsKey(value) && !value.contains(":"))
            return value;

        // Search for a key whose mapped value matches the requested registry name
        String bestMatch = null;
        for (Map.Entry<?, ?> e : mapping.entrySet()) {
            if (e.getKey() == null || e.getKey().toString().startsWith("_"))
                continue;
            Object val = e.getValue();
            Object candidate = null;
            if (val instanceof List<?> list && mapIndex < list.size()) {
                candidate = list.get(mapIndex);
            } else if (val instanceof String) {
                if (mapIndex == 0)
                    candidate = val;
            }
            if (candidate == null)
                continue;
            String cstr = candidate.toString();
            if (cstr.equals(value) || cstr.equals(cleanValue)) {
                bestMatch = e.getKey().toString();
                if (bestMatch.endsWith("#0"))
                    break; // prefer the default variant
            }
        }
        return bestMatch;
    }

    private String getTagMappingSource(TagType tagType) {
        return switch (tagType) {
            case BLOCKS, ITEMS -> "blocksitems";
            case ENTITIES -> "entities";
            case BIOMES -> "biomes";
            case STRUCTURES -> "structures";
            case DAMAGE_TYPES -> "damagesources";
            case ENCHANTMENTS -> "enchantments";
            case GAME_EVENTS -> "gameevents";
            case FUNCTIONS -> "functions";
            default -> null;
        };
    }

    private Integer getTagMapTypeIndex(TagType tagType) {
        return switch (tagType) {
            case BLOCKS, ITEMS, DAMAGE_TYPES, ENCHANTMENTS, GAME_EVENTS -> 1;
            case ENTITIES -> 2;
            case BIOMES, STRUCTURES -> 0;
            default -> null;
        };
    }

    private McpTypes.ToolResult deleteTag(Map<String, Object> params) {
        Workspace ws = mcreator.getWorkspace();
        if (ws == null) return host.createErrorResult("No workspace loaded");

        String typeName = stringParam(params, "tagType", "BLOCKS");
        String tagName = stringParam(params, "tagName");
        if (tagName == null) return host.createErrorResult("tagName is required");

        TagType tagType;
        try {
            tagType = TagType.valueOf(typeName.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return host.createErrorResult("Unknown tag type: " + typeName);
        }

        String normalized = normalizeTagName(tagName);
        String resourcePath = normalized.contains(":") ? normalized : "mod:" + normalized;
        TagElement tag = new TagElement(tagType, resourcePath);

        ws.removeTagElement(tag);

        // Remove any generated tag JSON files for this tag
        String tagFileName = tag.getName() + ".json";
        File workspaceFolder = ws.getFolderManager().getWorkspaceFolder();
        for (String base : new String[]{"src/main/resources", "build/resources/main"}) {
            File tagsRoot = new File(workspaceFolder, base + "/data/" + ws.getWorkspaceSettings().getModID() + "/tags");
            if (tagsRoot.isDirectory()) {
                try {
                    java.nio.file.Files.walk(tagsRoot.toPath())
                            .filter(p -> p.toFile().isFile() && p.getFileName().toString().equalsIgnoreCase(tagFileName))
                            .forEach(p -> {
                                try {
                                    java.nio.file.Files.deleteIfExists(p);
                                } catch (IOException ignored) {
                                }
                            });
                } catch (IOException ignored) {
                }
            }
        }

        ws.markDirty();
        return host.createSuccessResult("Deleted tag " + typeName.toLowerCase(Locale.ROOT) + "/" + tag.getName());
    }

    private McpTypes.ToolResult listTags(Map<String, Object> params) {
        Workspace ws = mcreator.getWorkspace();
        if (ws == null) return host.createErrorResult("No workspace loaded");

        String filterType = stringParam(params, "tagType", null);
        TagType filter = null;
        if (filterType != null) {
            try {
                filter = TagType.valueOf(filterType.toUpperCase(Locale.ROOT));
            } catch (Exception ignored) {
            }
        }

        List<Map<String, Object>> tags = new ArrayList<>();
        for (Map.Entry<TagElement, ArrayList<TagElement.Entry>> e : ws.getTagElements().entrySet()) {
            TagElement tag = e.getKey();
            if (filter != null && tag.type() != filter) continue;
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", tag.type().name());
            map.put("name", tag.getName());
            map.put("resourcePath", tag.resourcePath());
            List<String> entries = new ArrayList<>();
            for (TagElement.Entry entry : e.getValue()) {
                entries.add(entry.name() + (entry.isManaged() ? " (managed)" : " (unmanaged)"));
            }
            map.put("entries", entries);
            tags.add(map);
        }

        try {
            Map<String, Object> result = new HashMap<>();
            result.put("tags", tags);
            result.put("count", tags.size());
            return host.createSuccessResult(objectMapper.writeValueAsString(result));
        } catch (Exception ex) {
            return host.createErrorResult("Failed to serialize tags: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Creative tabs
    // ------------------------------------------------------------------

    private McpTypes.ToolResult createCreativeTab(Map<String, Object> params) {
        String tabName = stringParam(params, "tabName");
        if (tabName == null) return host.createErrorResult("tabName is required");

        String displayName = stringParam(params, "displayName", tabName);
        String icon = stringParam(params, "icon", "Blocks.STONE");
        boolean showSearch = toBoolean(params.get("showSearch"), false);

        Map<String, Object> properties = host.props(
                "name", displayName,
                "icon", icon,
                "showSearch", showSearch
        );
        Map<String, Object> createParams = host.props(
                "elementName", tabName,
                "properties", properties
        );

        McpTypes.ToolResult result = host.createTypedElement(mcreator, "tab", createParams);
        if (Boolean.TRUE.equals(result.getIsError())) return result;

        Workspace ws = mcreator.getWorkspace();
        if (ws != null) {
            String tabKey = tabName.startsWith("CUSTOM:") ? tabName : "CUSTOM:" + tabName;
            // Remove any legacy non-prefixed entry for this tab name
            ws.getCreativeTabsOrder().remove(tabName);
            if (!ws.getCreativeTabsOrder().containsKey(tabKey)) {
                ws.getCreativeTabsOrder().put(tabKey, new ArrayList<>());
            }
            ws.markDirty();
        }

        return result;
    }

    private McpTypes.ToolResult listCreativeTabs(Map<String, Object> params) {
        Workspace ws = mcreator.getWorkspace();
        if (ws == null) return host.createErrorResult("No workspace loaded");

        List<String> tabElements = new ArrayList<>();
        for (ModElement me : ws.getModElements()) {
            if (me.getType() == ModElementType.TAB) {
                tabElements.add(me.getName());
            }
        }

        CreativeTabsOrder order = ws.getCreativeTabsOrder();
        Map<String, List<String>> orderMap = new LinkedHashMap<>();
        for (Map.Entry<String, ArrayList<String>> e : order.entrySet()) {
            orderMap.put(e.getKey(), new ArrayList<>(e.getValue()));
        }

        try {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("tabElements", tabElements);
            result.put("tabOrder", orderMap);
            return host.createSuccessResult(objectMapper.writeValueAsString(result));
        } catch (Exception ex) {
            return host.createErrorResult("Failed to serialize tabs: " + ex.getMessage());
        }
    }

    private McpTypes.ToolResult updateCreativeTabs(Map<String, Object> params) {
        Workspace ws = mcreator.getWorkspace();
        if (ws == null) return host.createErrorResult("No workspace loaded");

        String tabName = stringParam(params, "tabName");
        List<String> elementNames = listString(params.get("elementNames"));
        if (tabName == null || elementNames == null) return host.createErrorResult("tabName and elementNames are required");

        List<ModElement> modElements = new ArrayList<>();
        for (String name : elementNames) {
            ModElement me = ws.getModElementByName(name);
            if (me != null) modElements.add(me);
        }

        String tabKey = tabName.startsWith("CUSTOM:") ? tabName : "CUSTOM:" + tabName;

        // Set the creativeTabs field on each element so the generator groups them under the custom tab
        TabEntry tabEntry = new TabEntry(ws, tabKey);
        for (ModElement me : modElements) {
            try {
                GeneratableElement ge = me.getGeneratableElement();
                if (ge instanceof ITabContainedElement) {
                    Field f = ge.getClass().getDeclaredField("creativeTabs");
                    f.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    List<TabEntry> tabs = (List<TabEntry>) f.get(ge);
                    if (tabs == null) tabs = new ArrayList<>();
                    // Remove any previous reference to this tab (case-insensitive) and add it at the end
                    tabs.removeIf(t -> tabKey.equalsIgnoreCase(t.getUnmappedValue()));
                    tabs.add(tabEntry);
                    f.set(ge, tabs);
                    ws.getModElementManager().storeModElement(ge);
                }
            } catch (Exception e) {
                LOG.warn("Could not update creativeTabs for {}: {}", me.getName(), e.getMessage());
            }
        }

        ws.getCreativeTabsOrder().setElementOrderInTab(tabKey, modElements);
        ws.markDirty();
        return host.createSuccessResult("Updated creative tab '" + tabKey + "' with " + modElements.size() + " elements");
    }

    // ------------------------------------------------------------------
    // Backup / local history
    // ------------------------------------------------------------------

    private McpTypes.ToolResult createBackup(Map<String, Object> params) {
        Workspace ws = mcreator.getWorkspace();
        if (ws == null) return host.createErrorResult("No workspace loaded");

        HistoryManager hm = ws.getHistoryManager();
        if (hm == null || !hm.isAvailable()) return host.createErrorResult("Local history is not available for this workspace");

        String name = stringParam(params, "backupName", "mcp-backup-" + System.currentTimeMillis());
        hm.importantCheckpoint("MCP backup: " + name);
        return host.createSuccessResult("Backup created: " + name);
    }

    private McpTypes.ToolResult listBackups(Map<String, Object> params) {
        Workspace ws = mcreator.getWorkspace();
        if (ws == null) return host.createErrorResult("No workspace loaded");

        HistoryManager hm = ws.getHistoryManager();
        if (hm == null || !hm.isAvailable()) return host.createErrorResult("Local history is not available");

        List<HistoryCheckpoint> checkpoints = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        hm.getCheckpoints(list -> {
            checkpoints.addAll(list);
            latch.countDown();
        });
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        List<Map<String, Object>> list = new ArrayList<>();
        for (HistoryCheckpoint cp : checkpoints) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", cp.name());
            map.put("hash", cp.hash());
            map.put("timestamp", cp.getTimestampString());
            list.add(map);
        }

        try {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("backups", list);
            return host.createSuccessResult(objectMapper.writeValueAsString(result));
        } catch (Exception ex) {
            return host.createErrorResult("Failed to serialize backups: " + ex.getMessage());
        }
    }

    private McpTypes.ToolResult restoreBackup(Map<String, Object> params) {
        Workspace ws = mcreator.getWorkspace();
        if (ws == null) return host.createErrorResult("No workspace loaded");

        HistoryManager hm = ws.getHistoryManager();
        if (hm == null || !hm.isAvailable()) return host.createErrorResult("Local history is not available");

        String requested = stringParam(params, "backupName", "latest");

        List<HistoryCheckpoint> checkpoints = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        hm.getCheckpoints(list -> {
            checkpoints.addAll(list);
            latch.countDown();
        });
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (checkpoints.isEmpty()) return host.createErrorResult("No backups found");

        HistoryCheckpoint target = null;
        if ("latest".equalsIgnoreCase(requested)) {
            target = checkpoints.get(0);
        } else {
            for (HistoryCheckpoint cp : checkpoints) {
                if (requested.equals(cp.name()) || requested.equals(cp.hash())) {
                    target = cp;
                    break;
                }
            }
            if (target == null) {
                // Allow partial name match (e.g. the user-supplied backup label)
                for (HistoryCheckpoint cp : checkpoints) {
                    if (cp.name().contains(requested)) {
                        target = cp;
                        break;
                    }
                }
            }
        }

        if (target == null) return host.createErrorResult("Backup not found: " + requested);

        try {
            hm.revertToCheckpoint(target);
            ws.reloadFromFileSystem();
            return host.createSuccessResult("Restored backup: " + target.name() + " (" + target.hash() + ")");
        } catch (LocalHistoryException e) {
            return host.createErrorResult("Failed to restore backup: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Generator switching
    // ------------------------------------------------------------------

    private McpTypes.ToolResult switchGenerator(Map<String, Object> params) {
        Workspace ws = mcreator.getWorkspace();
        if (ws == null) return host.createErrorResult("No workspace loaded");

        String generatorName = stringParam(params, "generatorName");
        if (generatorName == null) return host.createErrorResult("generatorName is required");

        String current = ws.getWorkspaceSettings().getCurrentGenerator();
        if (generatorName.equals(current)) {
            return host.createSuccessResult("Generator already set to: " + generatorName);
        }

        try {
            // Run generator switch on a background thread so the MCP request does not hang
            AtomicReference<String> result = new AtomicReference<>();
            AtomicReference<Exception> error = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);
            new Thread(() -> {
                try {
                    ws.switchGenerator(generatorName);
                    result.set("Switched generator to: " + generatorName);
                } catch (Exception e) {
                    error.set(e);
                } finally {
                    latch.countDown();
                }
            }, "MCP-SwitchGenerator").start();
            boolean finished = latch.await(30, TimeUnit.SECONDS);
            if (!finished) {
                return host.createErrorResult("Generator switch timed out after 30s; the workspace may need a manual restart to complete");
            }
            if (error.get() != null) throw error.get();
            ws.markDirty();
            return host.createSuccessResult(result.get());
        } catch (Exception e) {
            LOG.error("Failed to switch generator", e);
            return host.createErrorResult("Failed to switch generator: " + e.getMessage());
        }
    }

    private McpTypes.ToolResult listGenerators(Map<String, Object> params) {
        File pluginsDir = getMCreatorPluginsDir();
        if (pluginsDir == null || !pluginsDir.isDirectory()) {
            return host.createErrorResult("Could not locate MCreator plugins directory");
        }

        List<Map<String, Object>> generators = new ArrayList<>();
        File[] files = pluginsDir.listFiles((dir, name) -> name.startsWith("generator-") && name.endsWith(".zip"));
        if (files != null) {
            for (File zip : files) {
                try (ZipFile zf = new ZipFile(zip)) {
                    Enumeration<? extends ZipEntry> entries = zf.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();
                        if (entry.getName().endsWith("/generator.yaml")) {
                            String folder = entry.getName().substring(0, entry.getName().indexOf('/'));
                            try (InputStream is = zf.getInputStream(entry);
                                 BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                                Map<String, String> yaml = parseSimpleYaml(reader);
                                Map<String, Object> map = new LinkedHashMap<>();
                                map.put("id", folder);
                                map.put("name", yaml.getOrDefault("name", folder));
                                map.put("status", yaml.getOrDefault("status", "unknown"));
                                map.put("buildfileversion", yaml.getOrDefault("buildfileversion", ""));
                                generators.add(map);
                            }
                        }
                    }
                } catch (Exception e) {
                    LOG.warn("Could not read generator zip {}: {}", zip, e.getMessage());
                }
            }
        }

        try {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("current", mcreator.getWorkspace().getWorkspaceSettings().getCurrentGenerator());
            result.put("generators", generators);
            return host.createSuccessResult(objectMapper.writeValueAsString(result));
        } catch (Exception ex) {
            return host.createErrorResult("Failed to serialize generators: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Procedure editing
    // ------------------------------------------------------------------

    private McpTypes.ToolResult listProcedures(Map<String, Object> params) {
        Workspace ws = mcreator.getWorkspace();
        if (ws == null) return host.createErrorResult("No workspace loaded");

        Collection<ModElement> procedures = ws.getModElementsByType(ModElementType.PROCEDURE);
        List<Map<String, Object>> list = new ArrayList<>();
        for (ModElement me : procedures) {
            GeneratableElement ge = me.getGeneratableElement();
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", me.getName());
            if (ge instanceof Procedure p) {
                String xml = p.procedurexml != null ? p.procedurexml : "";
                map.put("xmlLength", xml.length());
                map.put("xmlPreview", xml.length() > 200 ? xml.substring(0, 200) + "..." : xml);
            } else {
                map.put("xmlLength", 0);
                map.put("xmlPreview", "");
            }
            list.add(map);
        }

        try {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("procedures", list);
            return host.createSuccessResult(objectMapper.writeValueAsString(result));
        } catch (Exception ex) {
            return host.createErrorResult("Failed to serialize procedures: " + ex.getMessage());
        }
    }

    private McpTypes.ToolResult updateProcedure(Map<String, Object> params) {
        Workspace ws = mcreator.getWorkspace();
        if (ws == null) return host.createErrorResult("No workspace loaded");

        String elementName = stringParam(params, "elementName");
        String xml = stringParam(params, "xml");
        if (elementName == null || xml == null) return host.createErrorResult("elementName and xml are required");

        ModElement me = ws.getModElementByName(elementName);
        if (me == null || me.getType() != ModElementType.PROCEDURE)
            return host.createErrorResult("Procedure element not found: " + elementName);

        GeneratableElement ge = me.getGeneratableElement();
        if (!(ge instanceof Procedure p))
            return host.createErrorResult("Element is not a procedure");

        p.procedurexml = xml;
        ws.getModElementManager().storeModElement(p);
        return host.createSuccessResult("Updated procedure XML for " + elementName);
    }

    private McpTypes.ToolResult deleteProcedure(Map<String, Object> params) {
        Workspace ws = mcreator.getWorkspace();
        if (ws == null) return host.createErrorResult("No workspace loaded");

        String elementName = stringParam(params, "elementName");
        if (elementName == null) return host.createErrorResult("elementName is required");

        ModElement me = ws.getModElementByName(elementName);
        if (me == null || me.getType() != ModElementType.PROCEDURE)
            return host.createErrorResult("Procedure element not found: " + elementName);

        ws.getModElementManager().removeModElement(me);
        ws.markDirty();
        return host.createSuccessResult("Deleted procedure: " + elementName);
    }

    // ------------------------------------------------------------------
    // In-game verification
    // ------------------------------------------------------------------

    private McpTypes.ToolResult verifyServerLoads(Map<String, Object> params) {
        Workspace ws = mcreator.getWorkspace();
        if (ws == null) return host.createErrorResult("No workspace loaded");

        String elementName = stringParam(params, "elementName", null);
        int timeout = toInt(params.get("timeoutSeconds"), 120);

        SwingUtilities.invokeLater(() -> mcreator.getActionRegistry().runServer.doAction());

        File logDir = new File(ws.getFolderManager().getWorkspaceFolder(), "run/logs");
        File latestLog = new File(logDir, "latest.log");
        File debugLog = new File(logDir, "debug.log");
        long start = System.currentTimeMillis();
        String status = "incomplete";
        int errorCount = 0;
        while (System.currentTimeMillis() - start < timeout * 1000L) {
            List<String> allLines = new ArrayList<>();
            if (latestLog.exists()) allLines.addAll(tailLog(latestLog, 500));
            if (debugLog.exists()) allLines.addAll(tailLog(debugLog, 500));

            boolean serverDone = false;
            for (String line : allLines) {
                if (line.contains("[Server thread/INFO]") && line.contains("Done (")) {
                    serverDone = true;
                }
                if (line.contains("/ERROR")) errorCount++;
                if (elementName != null && line.toLowerCase(Locale.ROOT).contains(elementName.toLowerCase(Locale.ROOT))) {
                    serverDone = true;
                }
            }
            if (serverDone) {
                status = elementName != null ? "done (element referenced)" : "done";
                break;
            }

            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        try {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", status);
            result.put("logFile", debugLog.getAbsolutePath());
            result.put("errorCount", errorCount);
            return host.createSuccessResult(objectMapper.writeValueAsString(result));
        } catch (Exception ex) {
            return host.createErrorResult("Failed to serialize verification result: " + ex.getMessage());
        }
    }

    private McpTypes.ToolResult verifyClientLoads(Map<String, Object> params) {
        Workspace ws = mcreator.getWorkspace();
        if (ws == null) return host.createErrorResult("No workspace loaded");

        String elementName = stringParam(params, "elementName", null);
        int timeout = toInt(params.get("timeoutSeconds"), 120);

        SwingUtilities.invokeLater(() -> mcreator.getActionRegistry().runClient.doAction());

        File logDir = new File(ws.getFolderManager().getWorkspaceFolder(), "run/logs");
        File latestLog = new File(logDir, "latest.log");
        File debugLog = new File(logDir, "debug.log");
        long start = System.currentTimeMillis();
        String status = "incomplete";
        int errorCount = 0;
        while (System.currentTimeMillis() - start < timeout * 1000L) {
            List<String> allLines = new ArrayList<>();
            if (latestLog.exists()) allLines.addAll(tailLog(latestLog, 500));
            if (debugLog.exists()) allLines.addAll(tailLog(debugLog, 500));

            boolean atlasCreated = false;
            for (String line : allLines) {
                if (line.contains("[Render thread/INFO]") && line.contains("Created: ") && line.contains("blocks.png-atlas")) {
                    atlasCreated = true;
                }
                if (line.contains("/ERROR")) errorCount++;
                if (elementName != null && line.toLowerCase(Locale.ROOT).contains(elementName.toLowerCase(Locale.ROOT))) {
                    atlasCreated = true;
                }
            }
            if (atlasCreated) {
                status = elementName != null ? "atlas_created (element referenced)" : "atlas_created";
                break;
            }

            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        try {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", status);
            result.put("logFile", debugLog.getAbsolutePath());
            result.put("errorCount", errorCount);
            return host.createSuccessResult(objectMapper.writeValueAsString(result));
        } catch (Exception ex) {
            return host.createErrorResult("Failed to serialize verification result: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Model validation / conversion
    // ------------------------------------------------------------------

    private McpTypes.ToolResult validateModel(Map<String, Object> params) {
        String sourcePath = stringParam(params, "sourcePath");
        if (sourcePath == null) return host.createErrorResult("sourcePath is required");

        File source = new File(sourcePath);
        if (!source.exists()) return host.createErrorResult("Model file not found: " + sourcePath);

        try {
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("path", source.getAbsolutePath());
            report.put("size", source.length());

            if (sourcePath.toLowerCase(Locale.ROOT).endsWith(".json")) {
                JsonNode root = objectMapper.readTree(source);
                boolean hasTextures = root.has("textures");
                boolean hasElements = root.has("elements");
                boolean hasParent = root.has("parent");
                report.put("format", "json");
                report.put("hasTextures", hasTextures);
                report.put("hasElements", hasElements);
                report.put("hasParent", hasParent);
                report.put("valid", hasParent || hasElements);
                List<String> missing = new ArrayList<>();
                if (!hasParent && !hasElements) missing.add("Model must have 'parent' or 'elements'");
                if (hasTextures) {
                    JsonNode textures = root.get("textures");
                    for (Iterator<String> it = textures.fieldNames(); it.hasNext(); ) {
                        String key = it.next();
                        String value = textures.get(key).asText();
                        if (value.isEmpty()) missing.add("Empty texture reference: " + key);
                    }
                }
                report.put("issues", missing);
            } else if (sourcePath.toLowerCase(Locale.ROOT).endsWith(".obj")) {
                List<float[]> vertices = new ArrayList<>();
                List<float[]> texCoords = new ArrayList<>();
                List<float[]> normals = new ArrayList<>();
                List<int[][]> faces = new ArrayList<>();
                parseObj(source, vertices, texCoords, normals, faces);
                report.put("format", "obj");
                report.put("vertexCount", vertices.size());
                report.put("faceCount", faces.size());
                report.put("valid", !vertices.isEmpty() && !faces.isEmpty());
                List<String> issues = new ArrayList<>();
                if (vertices.isEmpty()) issues.add("No vertices found");
                if (faces.isEmpty()) issues.add("No faces found");
                report.put("issues", issues);
            } else {
                report.put("format", "unknown");
                report.put("valid", false);
                report.put("issues", List.of("Only .json and .obj models are validated"));
            }

            return host.createSuccessResult(objectMapper.writeValueAsString(report));
        } catch (Exception e) {
            return host.createErrorResult("Failed to validate model: " + e.getMessage());
        }
    }

    private McpTypes.ToolResult convertModel(Map<String, Object> params) {
        String sourcePath = stringParam(params, "sourcePath");
        String modelName = stringParam(params, "modelName");
        String texture = stringParam(params, "texture", "#all");
        if (sourcePath == null || modelName == null)
            return host.createErrorResult("sourcePath and modelName are required");

        File source = new File(sourcePath);
        if (!source.exists()) return host.createErrorResult("Model file not found: " + sourcePath);

        Workspace ws = mcreator.getWorkspace();
        if (ws == null) return host.createErrorResult("No workspace loaded");

        try {
            List<float[]> vertices = new ArrayList<>();
            List<float[]> texCoords = new ArrayList<>();
            List<float[]> normals = new ArrayList<>();
            List<int[][]> faces = new ArrayList<>();
            parseObj(source, vertices, texCoords, normals, faces);

            if (vertices.isEmpty() || faces.isEmpty())
                return host.createErrorResult("OBJ has no vertices or faces");

            // Compute bounding box
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
            for (float[] v : vertices) {
                minX = Math.min(minX, v[0]); minY = Math.min(minY, v[1]); minZ = Math.min(minZ, v[2]);
                maxX = Math.max(maxX, v[0]); maxY = Math.max(maxY, v[1]); maxZ = Math.max(maxZ, v[2]);
            }

            float dx = maxX - minX, dy = maxY - minY, dz = maxZ - minZ;
            if (dx < 0.001f || dy < 0.001f || dz < 0.001f)
                return host.createErrorResult("Model is flat; cannot convert to a Minecraft block cuboid");

            // Map direction -> list of faces (normal indices and UVs)
            Map<String, List<int[]>> dirFaces = new HashMap<>();
            dirFaces.put("north", new ArrayList<>());
            dirFaces.put("south", new ArrayList<>());
            dirFaces.put("east", new ArrayList<>());
            dirFaces.put("west", new ArrayList<>());
            dirFaces.put("up", new ArrayList<>());
            dirFaces.put("down", new ArrayList<>());

            for (int[][] face : faces) {
                float[] n = computeFaceNormal(face, vertices, normals);
                String dir = classifyNormal(n);
                dirFaces.get(dir).add(face[0]);
                dirFaces.get(dir).add(face[1]);
                dirFaces.get(dir).add(face[2]);
                if (face.length > 3) dirFaces.get(dir).add(face[3]);
            }

            // Build JSON element
            ObjectNode root = objectMapper.createObjectNode();
            ObjectNode textures = root.putObject("textures");
            textures.put("all", texture);
            root.put("parent", "block/block");

            ObjectNode element = root.putArray("elements").addObject();
            element.putArray("from").add(minX * 16f).add(minY * 16f).add(minZ * 16f);
            element.putArray("to").add(maxX * 16f).add(maxY * 16f).add(maxZ * 16f);
            ObjectNode facesNode = element.putObject("faces");

            String[] dirs = {"north", "south", "east", "west", "up", "down"};
            for (String dir : dirs) {
                List<int[]> fList = dirFaces.get(dir);
                if (!fList.isEmpty()) {
                    ObjectNode faceNode = facesNode.putObject(dir);
                    float[] uv = computeUvForFaces(fList, texCoords);
                    faceNode.putArray("uv").add(uv[0]).add(uv[1]).add(uv[2]).add(uv[3]);
                    faceNode.put("texture", texture);
                }
            }

            File modelsDir = ws.getFolderManager().getModelsDir();
            modelsDir.mkdirs();
            File out = new File(modelsDir, modelName.replaceAll("\\.json$", "") + ".json");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(out, root);

            return host.createSuccessResult("Converted OBJ to " + out.getAbsolutePath());
        } catch (Exception e) {
            LOG.error("Failed to convert model", e);
            return host.createErrorResult("Failed to convert model: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private String stringParam(Map<String, Object> params, String key) {
        return stringParam(params, key, null);
    }

    private String stringParam(Map<String, Object> params, String key, String defaultValue) {
        Object v = params.get(key);
        return v != null ? String.valueOf(v) : defaultValue;
    }

    @SuppressWarnings("unchecked")
    private List<String> listString(Object value) {
        if (value == null) return null;
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object o : list) if (o != null) result.add(String.valueOf(o));
            return result;
        }
        if (value instanceof String s) {
            if (s.isEmpty()) return new ArrayList<>();
            return new ArrayList<>(Arrays.asList(s.split("\\s*,\\s*")));
        }
        return null;
    }

    private int toInt(Object value, int defaultValue) {
        if (value == null) return defaultValue;
        try {
            if (value instanceof Number n) return n.intValue();
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private boolean toBoolean(Object value, boolean defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Boolean b) return b;
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private File getMCreatorPluginsDir() {
        try {
            URL url = McpExtraToolsService.class.getProtectionDomain().getCodeSource().getLocation();
            File file = new File(url.toURI());
            // If we are inside a zip/jar, go up to plugins directory
            if (file.isFile() && file.getName().endsWith(".zip")) {
                return file.getParentFile();
            }
            // If extracted, traverse up until we find a plugins directory
            File dir = file;
            while (dir != null && !"plugins".equals(dir.getName())) {
                dir = dir.getParentFile();
            }
            if (dir != null && dir.isDirectory()) return dir;
            // Fallback: workspace sibling
            File mcreatorHome = new File(mcreator.getWorkspace().getFolderManager().getWorkspaceFolder(), "../..").getCanonicalFile();
            File plugins = new File(mcreatorHome, "plugins");
            if (plugins.isDirectory()) return plugins;
        } catch (Exception e) {
            LOG.warn("Could not locate MCreator plugins directory", e);
        }
        return null;
    }

    private Map<String, String> parseSimpleYaml(BufferedReader reader) throws IOException {
        Map<String, String> map = new HashMap<>();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.trim().startsWith("#") || line.trim().isEmpty()) continue;
            if (line.startsWith(" ") || line.startsWith("\t")) continue; // only top-level keys
            int colon = line.indexOf(':');
            if (colon > 0) {
                String key = line.substring(0, colon).trim();
                String value = line.substring(colon + 1).trim();
                if (value.contains("#")) value = value.substring(0, value.indexOf('#')).trim();
                if (value.startsWith("\"") && value.endsWith("\"")) value = value.substring(1, value.length() - 1);
                map.put(key, value);
            }
        }
        return map;
    }

    private List<String> tailLog(File logFile, int lines) {
        List<String> result = new ArrayList<>();
        try (RandomAccessFile raf = new RandomAccessFile(logFile, "r")) {
            long pos = raf.length() - 1;
            int count = 0;
            StringBuilder sb = new StringBuilder();
            while (pos >= 0 && count < lines) {
                raf.seek(pos);
                int ch = raf.read();
                if (ch == '\n') {
                    if (sb.length() > 0) {
                        result.add(sb.reverse().toString());
                        sb.setLength(0);
                        count++;
                    }
                } else if (ch != '\r') {
                    sb.append((char) ch);
                }
                pos--;
            }
            if (sb.length() > 0) result.add(sb.reverse().toString());
            Collections.reverse(result);
        } catch (Exception e) {
            LOG.warn("Could not read log", e);
        }
        return result;
    }

    private void parseObj(File source, List<float[]> vertices, List<float[]> texCoords, List<float[]> normals, List<int[][]> faces) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(source))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("v ")) {
                    String[] parts = line.split("\\s+");
                    vertices.add(new float[]{Float.parseFloat(parts[1]), Float.parseFloat(parts[2]), Float.parseFloat(parts[3])});
                } else if (line.startsWith("vt ")) {
                    String[] parts = line.split("\\s+");
                    texCoords.add(new float[]{Float.parseFloat(parts[1]), Float.parseFloat(parts[2])});
                } else if (line.startsWith("vn ")) {
                    String[] parts = line.split("\\s+");
                    normals.add(new float[]{Float.parseFloat(parts[1]), Float.parseFloat(parts[2]), Float.parseFloat(parts[3])});
                } else if (line.startsWith("f ")) {
                    String[] parts = line.split("\\s+");
                    int[][] face = new int[parts.length - 1][3];
                    for (int i = 1; i < parts.length; i++) {
                        String[] idx = parts[i].split("/");
                        face[i - 1][0] = Integer.parseInt(idx[0]); // v
                        face[i - 1][1] = idx.length > 1 && !idx[1].isEmpty() ? Integer.parseInt(idx[1]) : -1; // vt
                        face[i - 1][2] = idx.length > 2 ? Integer.parseInt(idx[2]) : -1; // vn
                    }
                    faces.add(face);
                }
            }
        }
    }

    private float[] computeFaceNormal(int[][] face, List<float[]> vertices, List<float[]> normals) {
        // Use explicit vn if present on first vertex
        if (!normals.isEmpty() && face.length > 0 && face[0][2] > 0) {
            float[] n = normals.get(face[0][2] - 1);
            return new float[]{n[0], n[1], n[2]};
        }
        float[] a = vertices.get(face[0][0] - 1);
        float[] b = vertices.get(face[1][0] - 1);
        float[] c = vertices.get(face[2][0] - 1);
        float[] ab = new float[]{b[0] - a[0], b[1] - a[1], b[2] - a[2]};
        float[] ac = new float[]{c[0] - a[0], c[1] - a[1], c[2] - a[2]};
        float[] n = new float[]{
                ab[1] * ac[2] - ab[2] * ac[1],
                ab[2] * ac[0] - ab[0] * ac[2],
                ab[0] * ac[1] - ab[1] * ac[0]
        };
        float len = (float) Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
        if (len > 0) {
            n[0] /= len; n[1] /= len; n[2] /= len;
        }
        return n;
    }

    private String classifyNormal(float[] n) {
        float ax = Math.abs(n[0]), ay = Math.abs(n[1]), az = Math.abs(n[2]);
        if (ax >= ay && ax >= az) return n[0] > 0 ? "east" : "west";
        if (ay >= ax && ay >= az) return n[1] > 0 ? "up" : "down";
        return n[2] > 0 ? "south" : "north";
    }

    private float[] computeUvForFaces(List<int[]> faceVertices, List<float[]> texCoords) {
        if (texCoords.isEmpty()) return new float[]{0f, 0f, 16f, 16f};
        float minU = 1f, minV = 1f, maxU = 0f, maxV = 0f;
        for (int[] fv : faceVertices) {
            if (fv[1] > 0) {
                float[] vt = texCoords.get(fv[1] - 1);
                minU = Math.min(minU, vt[0]);
                minV = Math.min(minV, vt[1]);
                maxU = Math.max(maxU, vt[0]);
                maxV = Math.max(maxV, vt[1]);
            }
        }
        if (maxU < minU || maxV < minV) return new float[]{0f, 0f, 16f, 16f};
        return new float[]{minU * 16f, (1f - maxV) * 16f, maxU * 16f, (1f - minV) * 16f};
    }
}

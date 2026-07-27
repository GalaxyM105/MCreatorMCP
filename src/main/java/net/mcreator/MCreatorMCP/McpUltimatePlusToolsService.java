package net.mcreator.MCreatorMCP;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.mcreator.MCreatorMCP.mcp.McpServer;
import net.mcreator.MCreatorMCP.mcp.McpTypes;
import net.mcreator.element.ModElementType;
import net.mcreator.generator.GeneratorConfiguration;
import net.mcreator.generator.GeneratorFlavor;
import net.mcreator.java.debug.JVMDebugClient;
import net.mcreator.minecraft.RegistryNameFixer;
import net.mcreator.minecraft.TagType;
import net.mcreator.ui.MCreator;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.ModElement;
import net.mcreator.workspace.elements.TagElement;
import net.mcreator.workspace.misc.CreativeTabsOrder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Even more MCP tools: bulk/localization operations, Gradle/code infrastructure,
 * run/debug helpers, generated-file verification, and launcher packaging.
 */
public class McpUltimatePlusToolsService {

    private static final Logger LOG = LogManager.getLogger("MCP-UltimatePlus");

    private final MCPToolsService host;
    private final McpServer mcpServer;
    private final MCreator mcreator;
    private final ObjectMapper objectMapper;

    public McpUltimatePlusToolsService(MCPToolsService host, McpServer mcpServer, MCreator mcreator) {
        this.host = host;
        this.mcpServer = mcpServer;
        this.mcreator = mcreator;
        this.objectMapper = new ObjectMapper();
    }

    public void registerTools() {
        // Localization & bulk workspace edits
        mcpServer.registerTool("bulkSetLocalizations",
                "Set multiple localization keys at once for a given language",
                host.objectSchema(host.props(
                        "language", host.stringSchema("Language code, e.g. en_us (default: en_us)"),
                        "translations", host.objectPropSchema("Map of localization keys to values")
                ), "translations"),
                params -> bulkSetLocalizations(params));

        mcpServer.registerTool("removeLocalization",
                "Remove a localization key from a language",
                host.objectSchema(host.props(
                        "language", host.stringSchema("Language code (default: en_us)"),
                        "key", host.stringSchema("Localization key to remove")
                ), "key"),
                params -> removeLocalization(params));

        mcpServer.registerTool("getMissingLocalizations",
                "List localization keys that are missing or empty in one language compared to another",
                host.objectSchema(host.props(
                        "baseLanguage", host.stringSchema("Base language to compare against (default: en_us)"),
                        "targetLanguage", host.stringSchema("Target language to check (default: en_us)")
                )),
                params -> getMissingLocalizations(params));

        mcpServer.registerTool("reorderCreativeTabs",
                "Reorder the mod elements inside a custom creative tab",
                host.objectSchema(host.props(
                        "tabName", host.stringSchema("Creative tab name or CUSTOM:Name"),
                        "elementNames", host.objectPropSchema("Ordered list of mod element names")
                ), "tabName", "elementNames"),
                params -> reorderCreativeTabs(params));

        mcpServer.registerTool("bulkTagUpdate",
                "Replace or append entries in a data/resource tag",
                host.objectSchema(host.props(
                        "tagType", host.stringSchema("Tag type: BLOCKS, ITEMS, ENTITIES, etc."),
                        "tagName", host.stringSchema("Tag name/path (e.g. my_tag or minecraft:my_tag)"),
                        "entries", host.objectPropSchema("List of entry names"),
                        "append", host.stringSchema("Append instead of replacing (true/false, default false)")
                ), "tagType", "tagName", "entries"),
                params -> bulkTagUpdate(params));

        // Generated code / mod infrastructure
        mcpServer.registerTool("listGradleDependencies",
                "List simple string dependencies declared in build.gradle and mcreator.gradle",
                host.objectSchema(),
                params -> listGradleDependencies(params));

        mcpServer.registerTool("removeGradleDependency",
                "Remove a Gradle dependency line from the workspace .gradle files",
                host.objectSchema(host.props(
                        "configuration", host.stringSchema("Gradle configuration, e.g. implementation"),
                        "dependency", host.stringSchema("Dependency string, e.g. com.example:lib:1.0")
                ), "configuration", "dependency"),
                params -> removeGradleDependency(params));

        mcpServer.registerTool("editModsToml",
                "Read or update top-level keys in META-INF/neoforge.mods.toml (or mods.toml)",
                host.objectSchema(host.props(
                        "properties", host.objectPropSchema("Map of top-level TOML key values")
                )),
                params -> editModsToml(params));

        mcpServer.registerTool("editPackMcmeta",
                "Read or update pack.mcmeta",
                host.objectSchema(host.props(
                        "pack", host.objectPropSchema("Map of pack properties to merge (e.g. pack_format, description)")
                )),
                params -> editPackMcmeta(params));

        // Testing / lifecycle
        mcpServer.registerTool("runDataGenerator",
                "Run the generator data task (runData) for this workspace",
                host.objectSchema(),
                params -> runDataGenerator(params));

        mcpServer.registerTool("runGameTestServer",
                "Run the game test server for this workspace",
                host.objectSchema(),
                params -> runGameTestServer(params));

        mcpServer.registerTool("debugClient",
                "Launch the Minecraft client with a JVM debug listener attached",
                host.objectSchema(),
                params -> debugClient(params));

        mcpServer.registerTool("stopClient",
                "Stop a running Minecraft client started by MCreator",
                host.objectSchema(),
                params -> stopClient(params));

        mcpServer.registerTool("stopServer",
                "Stop a running Minecraft server started by MCreator",
                host.objectSchema(),
                params -> stopServer(params));

        mcpServer.registerTool("verifyLootTable",
                "Verify that a generated loot table JSON exists and is valid JSON",
                host.objectSchema(host.props(
                        "elementName", host.stringSchema("Name of the loot table / element")
                ), "elementName"),
                params -> verifyGeneratedJson(params, "loot_tables"));

        mcpServer.registerTool("verifyRecipe",
                "Verify that a generated recipe JSON exists and is valid JSON",
                host.objectSchema(host.props(
                        "elementName", host.stringSchema("Name of the recipe / element")
                ), "elementName"),
                params -> verifyGeneratedJson(params, "recipes"));

        mcpServer.registerTool("verifyAdvancement",
                "Verify that a generated advancement JSON exists and is valid JSON",
                host.objectSchema(host.props(
                        "elementName", host.stringSchema("Name of the advancement / element")
                ), "elementName"),
                params -> verifyGeneratedJson(params, "advancements"));

        // Distribution / packaging
        mcpServer.registerTool("packageForModrinth",
                "Package the built mod JAR as a Modrinth-compatible .mrpack",
                host.objectSchema(host.props(
                        "outputPath", host.stringSchema("Output .mrpack file path")
                ), "outputPath"),
                params -> packageForModrinth(params));

        mcpServer.registerTool("packageForCurseForge",
                "Package the built mod JAR as a CurseForge/Overwolf .zip",
                host.objectSchema(host.props(
                        "outputPath", host.stringSchema("Output .zip file path")
                ), "outputPath"),
                params -> packageForCurseForge(params));

        mcpServer.registerTool("packageForPrismLauncher",
                "Package the built mod JAR as a Prism Launcher instance folder",
                host.objectSchema(host.props(
                        "outputPath", host.stringSchema("Output instance folder path")
                ), "outputPath"),
                params -> packageForPrismLauncher(params));

        mcpServer.registerTool("packageForMultiMC",
                "Package the built mod JAR as a MultiMC instance folder",
                host.objectSchema(host.props(
                        "outputPath", host.stringSchema("Output instance folder path")
                ), "outputPath"),
                params -> packageForMultiMC(params));

        mcpServer.registerTool("takeScreenshot",
                "Capture the current screen and save it as a PNG",
                host.objectSchema(host.props(
                        "outputPath", host.stringSchema("Output PNG file path (default /tmp/mcp_screenshot.png)")
                )),
                params -> takeScreenshot(params));
    }

    // ------------------------------------------------------------------
    // Localization & bulk operations
    // ------------------------------------------------------------------

    private McpTypes.ToolResult bulkSetLocalizations(Map<String, Object> params) {
        String language = stringParam(params, "language", "en_us");
        Object translationsObj = params.get("translations");
        if (!(translationsObj instanceof Map<?, ?> rawTranslations)) {
            return host.createErrorResult("translations map is required");
        }

        Workspace workspace = mcreator.getWorkspace();
        if (workspace == null) return host.createErrorResult("No workspace loaded");

        @SuppressWarnings("unchecked")
        Map<String, Object> translations = (Map<String, Object>) rawTranslations;

        Map<String, LinkedHashMap<String, String>> langMap = workspace.getLanguageMap();
        LinkedHashMap<String, String> map = langMap.get(language);
        if (map == null) {
            map = new LinkedHashMap<>();
            workspace.addLanguage(language, map);
        } else {
            workspace.markDirty();
        }

        int count = 0;
        for (Map.Entry<String, Object> e : translations.entrySet()) {
            if (e.getKey() == null) continue;
            map.put(e.getKey(), String.valueOf(e.getValue()));
            count++;
        }

        return host.createSuccessResult("Set " + count + " localization keys for " + language);
    }

    private McpTypes.ToolResult removeLocalization(Map<String, Object> params) {
        String language = stringParam(params, "language", "en_us");
        String key = stringParam(params, "key");
        if (key == null) return host.createErrorResult("key is required");

        Workspace workspace = mcreator.getWorkspace();
        if (workspace == null) return host.createErrorResult("No workspace loaded");

        Map<String, LinkedHashMap<String, String>> langMap = workspace.getLanguageMap();
        LinkedHashMap<String, String> map = langMap.get(language);
        if (map == null || !map.containsKey(key)) {
            return host.createErrorResult("Key not found in " + language + ": " + key);
        }

        map.remove(key);
        workspace.markDirty();
        return host.createSuccessResult("Removed key '" + key + "' from " + language);
    }

    private McpTypes.ToolResult getMissingLocalizations(Map<String, Object> params) {
        String baseLanguage = stringParam(params, "baseLanguage", "en_us");
        String targetLanguage = stringParam(params, "targetLanguage", "en_us");

        Workspace workspace = mcreator.getWorkspace();
        if (workspace == null) return host.createErrorResult("No workspace loaded");

        Map<String, LinkedHashMap<String, String>> langMap = workspace.getLanguageMap();
        Map<String, String> base = langMap.get(baseLanguage);
        if (base == null) return host.createErrorResult("Base language not found: " + baseLanguage);

        List<Map<String, String>> missing = new ArrayList<>();
        Map<String, String> target = langMap.get(targetLanguage);

        for (Map.Entry<String, String> e : base.entrySet()) {
            boolean isMissing = target == null || !target.containsKey(e.getKey());
            boolean isEmpty = target != null && (target.get(e.getKey()) == null || target.get(e.getKey()).isBlank());
            if (isMissing || isEmpty) {
                Map<String, String> item = new LinkedHashMap<>();
                item.put("key", e.getKey());
                item.put("baseValue", e.getValue());
                item.put("targetValue", target != null ? target.get(e.getKey()) : null);
                item.put("reason", isMissing ? "missing" : "empty");
                missing.add(item);
            }
        }

        try {
            return host.createSuccessResult(objectMapper.writeValueAsString(Map.of(
                    "baseLanguage", baseLanguage,
                    "targetLanguage", targetLanguage,
                    "missing", missing,
                    "count", missing.size()
            )));
        } catch (Exception ex) {
            return host.createErrorResult("Failed to serialize missing localizations: " + ex.getMessage());
        }
    }

    private McpTypes.ToolResult reorderCreativeTabs(Map<String, Object> params) {
        String tabName = stringParam(params, "tabName");
        List<String> elementNames = listString(params.get("elementNames"));
        if (tabName == null || elementNames == null) return host.createErrorResult("tabName and elementNames are required");

        Workspace workspace = mcreator.getWorkspace();
        if (workspace == null) return host.createErrorResult("No workspace loaded");

        String tabKey = tabName.startsWith("CUSTOM:") ? tabName : "CUSTOM:" + tabName;

        List<ModElement> modElements = new ArrayList<>();
        for (String name : elementNames) {
            ModElement me = workspace.getModElementByName(name);
            if (me == null) {
                String camel = toCamelCase(name);
                me = workspace.getModElementByName(camel);
            }
            if (me == null) {
                me = new ModElement(workspace, name, ModElementType.UNKNOWN);
            }
            modElements.add(me);
        }

        CreativeTabsOrder order = workspace.getCreativeTabsOrder();
        order.setElementOrderInTab(tabKey, modElements);
        workspace.markDirty();
        return host.createSuccessResult("Reordered " + modElements.size() + " elements in tab " + tabKey);
    }

    private McpTypes.ToolResult bulkTagUpdate(Map<String, Object> params) {
        String typeName = stringParam(params, "tagType", "BLOCKS");
        String tagName = stringParam(params, "tagName");
        List<String> entries = listString(params.get("entries"));
        boolean append = toBoolean(params.get("append"), false);

        if (tagName == null || entries == null) return host.createErrorResult("tagName and entries are required");

        Workspace workspace = mcreator.getWorkspace();
        if (workspace == null) return host.createErrorResult("No workspace loaded");

        TagType tagType;
        try {
            tagType = TagType.valueOf(typeName.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return host.createErrorResult("Unknown tag type: " + typeName);
        }

        String resourcePath = tagName.contains(":") ? tagName : "mod:" + tagName;
        TagElement tag = new TagElement(tagType, resourcePath);
        workspace.addTagElement(tag);

        List<TagElement.Entry> existing = workspace.getTagElements().get(tag);
        if (existing == null) {
            existing = new ArrayList<>();
            workspace.getTagElements().put(tag, (ArrayList<TagElement.Entry>) existing);
        }
        if (!append) existing.clear();

        List<TagElement.Entry> newEntries = new ArrayList<>();
        for (String entry : entries) {
            newEntries.add(resolveTagEntry(workspace, tagType, entry));
        }

        Set<TagElement.Entry> seen = new LinkedHashSet<>(existing);
        for (TagElement.Entry e : newEntries) {
            if (seen.add(e)) existing.add(e);
        }

        workspace.markDirty();
        return host.createSuccessResult("Updated tag " + tagType.name().toLowerCase(Locale.ROOT) + "/" + tag.getName() + " with " + existing.size() + " entries");
    }

    // ------------------------------------------------------------------
    // Generated code / mod infrastructure
    // ------------------------------------------------------------------

    private McpTypes.ToolResult listGradleDependencies(Map<String, Object> params) {
        Workspace workspace = mcreator.getWorkspace();
        if (workspace == null) return host.createErrorResult("No workspace loaded");

        File folder = workspace.getFolderManager().getWorkspaceFolder();
        List<File> files = listGradleFiles(folder);
        List<Map<String, String>> deps = new ArrayList<>();

        Pattern pattern = Pattern.compile("^\\s*(\\w+)\\s+[\"']([^\"']+)[\"']\\s*$");

        for (File file : files) {
            try {
                String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                for (String line : content.split("\\r?\\n")) {
                    Matcher m = pattern.matcher(line);
                    if (m.matches()) {
                        Map<String, String> dep = new LinkedHashMap<>();
                        dep.put("configuration", m.group(1));
                        dep.put("dependency", m.group(2));
                        dep.put("file", file.getName());
                        deps.add(dep);
                    }
                }
            } catch (Exception e) {
                LOG.warn("Could not read {}", file, e);
            }
        }

        try {
            return host.createSuccessResult(objectMapper.writeValueAsString(Map.of("dependencies", deps, "count", deps.size())));
        } catch (Exception e) {
            return host.createErrorResult("Failed to serialize dependencies: " + e.getMessage());
        }
    }

    private McpTypes.ToolResult removeGradleDependency(Map<String, Object> params) {
        String configuration = stringParam(params, "configuration");
        String dependency = stringParam(params, "dependency");
        if (configuration == null || dependency == null) return host.createErrorResult("configuration and dependency are required");

        Workspace workspace = mcreator.getWorkspace();
        if (workspace == null) return host.createErrorResult("No workspace loaded");

        File folder = workspace.getFolderManager().getWorkspaceFolder();
        List<File> files = listGradleFiles(folder);
        String lineRegex = "^\\s*" + Pattern.quote(configuration) + "\\s+[\"']" + Pattern.quote(dependency) + "[\"']\\s*;?\\s*$";
        Pattern pattern = Pattern.compile(lineRegex, Pattern.MULTILINE);

        boolean removed = false;
        for (File file : files) {
            try {
                String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                Matcher m = pattern.matcher(content);
                if (m.find()) {
                    String updated = m.replaceAll(Matcher.quoteReplacement(""));
                    Files.writeString(file.toPath(), updated, StandardCharsets.UTF_8);
                    removed = true;
                    break;
                }
            } catch (Exception e) {
                LOG.warn("Could not update {}", file, e);
            }
        }

        if (!removed) return host.createErrorResult("Dependency not found: " + configuration + " \"" + dependency + "\"");
        return host.createSuccessResult("Removed dependency: " + configuration + " \"" + dependency + "\"");
    }

    private McpTypes.ToolResult editModsToml(Map<String, Object> params) {
        Object propsObj = params.get("properties");
        if (!(propsObj instanceof Map<?, ?> rawProps)) return host.createErrorResult("properties map is required");

        Workspace workspace = mcreator.getWorkspace();
        if (workspace == null) return host.createErrorResult("No workspace loaded");

        File file = new File(workspace.getFolderManager().getWorkspaceFolder(), "src/main/resources/META-INF/neoforge.mods.toml");
        if (!file.exists()) file = new File(workspace.getFolderManager().getWorkspaceFolder(), "src/main/resources/META-INF/mods.toml");
        if (!file.exists()) return host.createErrorResult("mods.toml not found");

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) rawProps;
        String content;
        try {
            content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return host.createErrorResult("Could not read mods.toml: " + e.getMessage());
        }

        List<String> updated = new ArrayList<>();
        for (Map.Entry<String, Object> e : properties.entrySet()) {
            Object value = e.getValue();
            if (value instanceof Map || value instanceof List) continue;
            String line;
            if (value instanceof Boolean || value instanceof Number) {
                line = e.getKey() + " = " + value;
            } else {
                line = e.getKey() + " = \"" + String.valueOf(value).replace("\"", "\\\"") + "\"";
            }
            Pattern pattern = Pattern.compile("^\\s*" + Pattern.quote(e.getKey()) + "\\s*=.*$", Pattern.MULTILINE);
            Matcher m = pattern.matcher(content);
            if (m.find()) {
                content = m.replaceAll(Matcher.quoteReplacement(line));
            } else {
                content = content + "\n" + line + "\n";
            }
            updated.add(e.getKey());
        }

        try {
            Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
            return host.createSuccessResult("Updated mods.toml keys: " + updated);
        } catch (Exception e) {
            return host.createErrorResult("Could not write mods.toml: " + e.getMessage());
        }
    }

    private McpTypes.ToolResult editPackMcmeta(Map<String, Object> params) {
        Object packObj = params.get("pack");
        if (!(packObj instanceof Map<?, ?> rawPack)) return host.createErrorResult("pack map is required");

        Workspace workspace = mcreator.getWorkspace();
        if (workspace == null) return host.createErrorResult("No workspace loaded");

        File file = new File(workspace.getFolderManager().getWorkspaceFolder(), "src/main/resources/pack.mcmeta");
        ObjectNode root;
        try {
            if (file.exists()) {
                root = (ObjectNode) objectMapper.readTree(file);
            } else {
                root = objectMapper.createObjectNode();
            }
        } catch (Exception e) {
            return host.createErrorResult("Could not read pack.mcmeta: " + e.getMessage());
        }

        ObjectNode pack = root.withObject("/pack");
        @SuppressWarnings("unchecked")
        Map<String, Object> packProperties = (Map<String, Object>) rawPack;
        for (Map.Entry<String, Object> e : packProperties.entrySet()) {
            if (e.getValue() instanceof Number n) pack.put(e.getKey(), n.intValue());
            else if (e.getValue() instanceof Boolean b) pack.put(e.getKey(), b);
            else pack.put(e.getKey(), String.valueOf(e.getValue()));
        }

        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, root);
            return host.createSuccessResult("Updated pack.mcmeta: " + file.getAbsolutePath());
        } catch (Exception e) {
            return host.createErrorResult("Could not write pack.mcmeta: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Testing / lifecycle
    // ------------------------------------------------------------------

    private McpTypes.ToolResult runDataGenerator(Map<String, Object> params) {
        Workspace workspace = mcreator.getWorkspace();
        if (workspace == null) return host.createErrorResult("No workspace loaded");

        String task = workspace.getGeneratorConfiguration().getGradleTaskFor("run_data");
        if (task == null || task.isBlank()) task = "runData";
        mcreator.getGradleConsole().exec(task);
        return host.createSuccessResult("Started Gradle task: " + task);
    }

    private McpTypes.ToolResult runGameTestServer(Map<String, Object> params) {
        Workspace workspace = mcreator.getWorkspace();
        if (workspace == null) return host.createErrorResult("No workspace loaded");

        mcreator.getGradleConsole().exec("runGameTestServer");
        return host.createSuccessResult("Started Gradle task: runGameTestServer");
    }

    private McpTypes.ToolResult debugClient(Map<String, Object> params) {
        Workspace workspace = mcreator.getWorkspace();
        if (workspace == null) return host.createErrorResult("No workspace loaded");

        mcreator.getGradleConsole().exec("runClient", new JVMDebugClient());
        return host.createSuccessResult("Started debug client (runClient with JVMDebugClient)");
    }

    private McpTypes.ToolResult stopClient(Map<String, Object> params) {
        try {
            mcreator.getGradleConsole().cancelTask();
            runPkill("clientRunProgramArgs.txt");
            return host.createSuccessResult("Client stop requested");
        } catch (Exception e) {
            return host.createErrorResult("Failed to stop client: " + e.getMessage());
        }
    }

    private McpTypes.ToolResult stopServer(Map<String, Object> params) {
        try {
            mcreator.getGradleConsole().cancelTask();
            runPkill("serverRunProgramArgs.txt");
            return host.createSuccessResult("Server stop requested");
        } catch (Exception e) {
            return host.createErrorResult("Failed to stop server: " + e.getMessage());
        }
    }

    private McpTypes.ToolResult verifyGeneratedJson(Map<String, Object> params, String folder) {
        String elementName = stringParam(params, "elementName");
        if (elementName == null) return host.createErrorResult("elementName is required");

        Workspace workspace = mcreator.getWorkspace();
        if (workspace == null) return host.createErrorResult("No workspace loaded");

        String modid = workspace.getWorkspaceSettings().getModID();
        File root = workspace.getFolderManager().getWorkspaceFolder();
        List<String> searched = new ArrayList<>();
        List<String> found = new ArrayList<>();
        List<String> invalid = new ArrayList<>();

        Set<String> folderVariants = new LinkedHashSet<>();
        folderVariants.add(folder);
        if (folder.endsWith("s")) folderVariants.add(folder.substring(0, folder.length() - 1));
        else folderVariants.add(folder + "s");

        List<String> prefixes = new ArrayList<>();
        for (String variant : folderVariants) {
            prefixes.add("src/main/resources/data/" + modid + "/" + variant);
            prefixes.add("build/resources/main/data/" + modid + "/" + variant);
        }

        String targetBase = RegistryNameFixer.fromCamelCase(elementName);
        String target = targetBase + ".json";

        for (String prefix : prefixes) {
            File dir = new File(root, prefix);
            if (!dir.exists()) continue;
            searched.add(dir.getAbsolutePath());
            try (Stream<Path> paths = Files.walk(dir.toPath())) {
                for (Path p : paths.filter(Files::isRegularFile).collect(Collectors.toList())) {
                    String name = p.getFileName().toString();
                    if (name.endsWith(".json") && (name.equals(target) || name.startsWith(elementName)
                            || name.startsWith(targetBase))) {
                        try {
                            objectMapper.readTree(p.toFile());
                            found.add(p.toString());
                        } catch (Exception ex) {
                            invalid.add(p.toString() + ": " + ex.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                LOG.warn("Could not walk {}", dir, e);
            }
        }

        if (!found.isEmpty()) {
            return host.createSuccessResult("Valid JSON found: " + found);
        }
        if (!invalid.isEmpty()) {
            return host.createErrorResult("Found invalid JSON: " + invalid);
        }
        return host.createErrorResult("No generated JSON found for " + elementName + " in " + folder + " (searched: " + searched + ")");
    }

    // ------------------------------------------------------------------
    // Distribution / packaging
    // ------------------------------------------------------------------

    private McpTypes.ToolResult packageForModrinth(Map<String, Object> params) {
        Workspace workspace = mcreator.getWorkspace();
        if (workspace == null) return host.createErrorResult("No workspace loaded");

        File jar = getBuiltJar(workspace);
        if (jar == null) return host.createErrorResult("No built JAR found in build/libs");

        String outputPath = stringParam(params, "outputPath");
        if (outputPath == null) return host.createErrorResult("outputPath is required");
        if (!outputPath.toLowerCase(Locale.ROOT).endsWith(".mrpack")) outputPath += ".mrpack";

        GeneratorConfiguration gc = workspace.getGeneratorConfiguration();
        String mcVersion = gc.getGeneratorMinecraftVersion();
        String forgeVersion = gc.getGeneratorBuildFileVersion();
        String name = workspace.getWorkspaceSettings().getModName();
        String version = workspace.getWorkspaceSettings().getVersion();

        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("formatVersion", 1);
            root.put("game", "minecraft");
            root.put("versionId", version);
            root.put("name", name);
            ObjectNode deps = root.putObject("dependencies");
            deps.put("minecraft", mcVersion);
            if (forgeVersion != null && !forgeVersion.isBlank()) deps.put("neoforge", forgeVersion);
            root.putArray("files");

            File outFile = new File(outputPath);
            outFile.getParentFile().mkdirs();
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outFile))) {
                writeZipEntry(zos, "modrinth.index.json", root.toPrettyString().getBytes(StandardCharsets.UTF_8));
                writeZipEntry(zos, "overrides/mods/" + jar.getName(), Files.readAllBytes(jar.toPath()));
            }

            return host.createSuccessResult("Packaged Modrinth .mrpack: " + outFile.getAbsolutePath());
        } catch (Exception e) {
            return host.createErrorResult("Failed to package .mrpack: " + e.getMessage());
        }
    }

    private McpTypes.ToolResult packageForCurseForge(Map<String, Object> params) {
        Workspace workspace = mcreator.getWorkspace();
        if (workspace == null) return host.createErrorResult("No workspace loaded");

        File jar = getBuiltJar(workspace);
        if (jar == null) return host.createErrorResult("No built JAR found in build/libs");

        String outputPath = stringParam(params, "outputPath");
        if (outputPath == null) return host.createErrorResult("outputPath is required");
        if (!outputPath.toLowerCase(Locale.ROOT).endsWith(".zip")) outputPath += ".zip";

        GeneratorConfiguration gc = workspace.getGeneratorConfiguration();
        String mcVersion = gc.getGeneratorMinecraftVersion();
        String forgeVersion = gc.getGeneratorBuildFileVersion();
        String name = workspace.getWorkspaceSettings().getModName();
        String version = workspace.getWorkspaceSettings().getVersion();
        String author = workspace.getWorkspaceSettings().getAuthor();
        if (author == null) author = "";

        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("manifestType", "minecraftModpack");
            root.put("manifestVersion", 1);
            root.put("name", name);
            root.put("version", version);
            root.put("author", author);
            root.put("overrides", "overrides");
            ObjectNode minecraft = root.putObject("minecraft");
            minecraft.put("version", mcVersion);
            ArrayNode modLoaders = minecraft.putArray("modLoaders");
            ObjectNode loader = modLoaders.addObject();
            loader.put("id", "neoforge-" + forgeVersion);
            loader.put("primary", true);
            root.putArray("files");

            File outFile = new File(outputPath);
            outFile.getParentFile().mkdirs();
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outFile))) {
                writeZipEntry(zos, "manifest.json", root.toPrettyString().getBytes(StandardCharsets.UTF_8));
                writeZipEntry(zos, "overrides/mods/" + jar.getName(), Files.readAllBytes(jar.toPath()));
            }

            return host.createSuccessResult("Packaged CurseForge .zip: " + outFile.getAbsolutePath());
        } catch (Exception e) {
            return host.createErrorResult("Failed to package CurseForge .zip: " + e.getMessage());
        }
    }

    private McpTypes.ToolResult packageForPrismLauncher(Map<String, Object> params) {
        return packageForMultiMCInternal(params, "Prism Launcher");
    }

    private McpTypes.ToolResult packageForMultiMC(Map<String, Object> params) {
        return packageForMultiMCInternal(params, "MultiMC");
    }

    private McpTypes.ToolResult packageForMultiMCInternal(Map<String, Object> params, String label) {
        Workspace workspace = mcreator.getWorkspace();
        if (workspace == null) return host.createErrorResult("No workspace loaded");

        File jar = getBuiltJar(workspace);
        if (jar == null) return host.createErrorResult("No built JAR found in build/libs");

        String outputPath = stringParam(params, "outputPath");
        if (outputPath == null) return host.createErrorResult("outputPath is required");

        GeneratorConfiguration gc = workspace.getGeneratorConfiguration();
        String mcVersion = gc.getGeneratorMinecraftVersion();
        String forgeVersion = gc.getGeneratorBuildFileVersion();
        String name = workspace.getWorkspaceSettings().getModName();

        try {
            File outDir = new File(outputPath);
            outDir.mkdirs();

            File minecraftDir = new File(outDir, ".minecraft");
            File modsDir = new File(minecraftDir, "mods");
            modsDir.mkdirs();

            Files.copy(jar.toPath(), new File(modsDir, jar.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);

            ObjectNode mmc = objectMapper.createObjectNode();
            mmc.put("formatVersion", 1);
            ArrayNode components = mmc.putArray("components");
            ObjectNode mcComp = components.addObject();
            mcComp.put("uid", "net.minecraft");
            mcComp.put("version", mcVersion);
            ObjectNode forgeComp = components.addObject();
            forgeComp.put("uid", "net.neoforged");
            forgeComp.put("version", forgeVersion);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(outDir, "mmc-pack.json"), mmc);

            File cfg = new File(outDir, "instance.cfg");
            Files.writeString(cfg.toPath(), "name=" + name + "\nInstanceType=OneSix\n", StandardCharsets.UTF_8);

            return host.createSuccessResult("Packaged " + label + " instance: " + outDir.getAbsolutePath());
        } catch (Exception e) {
            return host.createErrorResult("Failed to package " + label + " instance: " + e.getMessage());
        }
    }

    private McpTypes.ToolResult takeScreenshot(Map<String, Object> params) {
        String outputPath = stringParam(params, "outputPath", "/tmp/mcp_screenshot.png");
        try {
            Robot robot = new Robot();
            Rectangle screen = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            BufferedImage image = robot.createScreenCapture(screen);
            File out = new File(outputPath);
            out.getParentFile().mkdirs();
            ImageIO.write(image, "png", out);
            return host.createSuccessResult("Screenshot saved to " + out.getAbsolutePath());
        } catch (Exception e) {
            return host.createErrorResult("Failed to take screenshot: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private TagElement.Entry resolveTagEntry(Workspace ws, TagType tagType, String entryStr) {
        if (entryStr == null || entryStr.isEmpty()) return null;

        if (entryStr.startsWith("#") || entryStr.startsWith("TAG:")) {
            String tag = entryStr.startsWith("#") ? entryStr.substring(1) : entryStr.substring(4);
            return TagElement.Entry.unmanaged("TAG:" + tag);
        }

        String elementName = entryStr;
        if (elementName.contains(":")) elementName = elementName.substring(elementName.indexOf(':') + 1);

        ModElement modElement = ws.getModElementByName(elementName);
        if (modElement == null) {
            String camel = toCamelCase(elementName);
            modElement = ws.getModElementByName(camel);
        }

        if (modElement != null) {
            return TagElement.Entry.managedBy(modElement, "CUSTOM:" + modElement.getName());
        }

        return TagElement.Entry.unmanaged(entryStr);
    }

    private File getBuiltJar(Workspace workspace) {
        File libs = new File(workspace.getFolderManager().getWorkspaceFolder(), "build/libs");
        if (!libs.isDirectory()) return null;
        File[] jars = libs.listFiles((dir, name) -> name.endsWith(".jar")
                && !name.contains("sources") && !name.contains("dev") && !name.contains("shadow") && !name.contains("slim"));
        if (jars == null || jars.length == 0) return null;
        for (File jar : jars) {
            if (jar.getName().matches("^[a-z_][a-z0-9_-]*-.*\\.jar$")) return jar;
        }
        return jars[0];
    }

    private List<File> listGradleFiles(File folder) {
        List<File> result = new ArrayList<>();
        File buildGradle = new File(folder, "build.gradle");
        if (buildGradle.isFile()) result.add(buildGradle);
        File mcreatorGradle = new File(folder, "mcreator.gradle");
        if (mcreatorGradle.isFile()) result.add(mcreatorGradle);
        return result;
    }

    private void writeZipEntry(ZipOutputStream zos, String name, byte[] data) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zos.putNextEntry(entry);
        zos.write(data);
        zos.closeEntry();
    }

    private void runPkill(String marker) {
        try {
            new ProcessBuilder("pkill", "-f", marker).inheritIO().start();
        } catch (Exception e) {
            LOG.warn("pkill failed for marker {}: {}", marker, e.getMessage());
        }
    }

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

    private boolean toBoolean(Object value, boolean defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Boolean b) return b;
        return Boolean.parseBoolean(String.valueOf(value));
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
}

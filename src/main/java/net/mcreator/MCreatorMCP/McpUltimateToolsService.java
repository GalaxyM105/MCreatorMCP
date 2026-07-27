/*
 * MCreatorMCP "ultimate" tools: asset pipeline, deep introspection, and workspace lifecycle additions.
 * SPDX-License-Identifier: GPL-2.0-only
 */
package net.mcreator.MCreatorMCP;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.mcreator.MCreatorMCP.mcp.McpServer;
import net.mcreator.MCreatorMCP.mcp.McpTypes;
import net.mcreator.element.GeneratableElement;
import net.mcreator.element.ModElementType;
import net.mcreator.element.ModElementTypeLoader;
import net.mcreator.element.NamespacedGeneratableElement;
import net.mcreator.element.parts.TextureHolder;
import net.mcreator.generator.mapping.MappableElement;
import net.mcreator.element.parts.procedure.Procedure;
import net.mcreator.generator.GeneratorConfiguration;
import net.mcreator.generator.GeneratorFlavor;
import net.mcreator.io.FileIO;
import net.mcreator.minecraft.DataListEntry;
import net.mcreator.minecraft.DataListLoader;
import net.mcreator.minecraft.MCItem;
import net.mcreator.minecraft.RegistryNameFixer;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.workspace.resources.TextureType;
import net.mcreator.util.FilenameUtilsPatched;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.ModElement;
import net.mcreator.workspace.elements.SoundElement;
import net.mcreator.workspace.elements.VariableElement;
import net.mcreator.workspace.resources.Animation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Additional MCP tools that make the server a more complete MCreator copilot.
 */
public class McpUltimateToolsService {

    private static final Logger LOG = LogManager.getLogger("MCP-Ultimate");

    private final MCPToolsService host;
    private final McpServer mcpServer;
    private final MCreator mcreator;
    private final ObjectMapper objectMapper;

    public McpUltimateToolsService(MCPToolsService host, McpServer mcpServer, MCreator mcreator) {
        this.host = host;
        this.mcpServer = mcpServer;
        this.mcreator = mcreator;
        this.objectMapper = new ObjectMapper();
    }

    public void registerTools() {
        // ---- Asset pipeline ----
        mcpServer.registerTool("importSound", "Import an .ogg audio file and register it as a sound event",
                host.objectSchema(host.props(
                        "soundName", host.stringSchema("Registry name of the sound"),
                        "audioFile", host.stringSchema("Path to .ogg file or base64 data URI"),
                        "category", host.stringSchema("Sound category (master, block, entity, etc.)"),
                        "subtitleKey", host.stringSchema("Optional subtitle localization key")
                ), "soundName", "audioFile"),
                params -> importSound(params));

        mcpServer.registerTool("listSounds", "List all registered sound events and their files",
                host.objectSchema(),
                params -> listSounds(params));

        mcpServer.registerTool("deleteSound", "Delete a sound event and its .ogg file",
                host.objectSchema(host.props(
                        "soundName", host.stringSchema("Registry name of the sound")
                ), "soundName"),
                params -> deleteSound(params));

        mcpServer.registerTool("importAnimation", "Import a Java animation file into the workspace",
                host.objectSchema(host.props(
                        "animationName", host.stringSchema("Name for the animation"),
                        "sourcePath", host.stringSchema("Path to the source .java animation file")
                ), "animationName", "sourcePath"),
                params -> importAnimation(params));

        mcpServer.registerTool("listAnimations", "List imported Java animations and their sub-animations",
                host.objectSchema(),
                params -> listAnimations(params));

        mcpServer.registerTool("deleteAnimation", "Delete an imported animation file",
                host.objectSchema(host.props(
                        "animationName", host.stringSchema("Animation file name without extension")
                ), "animationName"),
                params -> deleteAnimation(params));

        mcpServer.registerTool("importStructure", "Import an .nbt structure file into the workspace",
                host.objectSchema(host.props(
                        "structureName", host.stringSchema("Name for the structure (without .nbt)"),
                        "sourcePath", host.stringSchema("Path to the source .nbt file")
                ), "structureName", "sourcePath"),
                params -> importStructure(params));

        mcpServer.registerTool("listStructures", "List imported structure files",
                host.objectSchema(),
                params -> listStructures(params));

        mcpServer.registerTool("deleteStructure", "Delete an imported structure file",
                host.objectSchema(host.props(
                        "structureName", host.stringSchema("Structure name without .nbt")
                ), "structureName"),
                params -> deleteStructure(params));

        // ---- Deep element introspection ----
        mcpServer.registerTool("getElementPropertySchema", "Return the property schema for a mod element type",
                host.objectSchema(host.props(
                        "elementType", host.stringSchema("Mod element type (e.g. block, item, livingentity)")
                ), "elementType"),
                params -> getElementPropertySchema(params));

        mcpServer.registerTool("getElementDefaults", "Return default values for a mod element type",
                host.objectSchema(host.props(
                        "elementType", host.stringSchema("Mod element type"),
                        "elementName", host.stringSchema("Optional sample element name")
                ), "elementType"),
                params -> getElementDefaults(params));

        mcpServer.registerTool("getPropertyHelp", "Return MCreator's localized help text for an element property",
                host.objectSchema(host.props(
                        "elementType", host.stringSchema("Mod element type"),
                        "propertyName", host.stringSchema("Property/field name")
                ), "elementType", "propertyName"),
                params -> getPropertyHelp(params));

        mcpServer.registerTool("listDataListValues", "List entries from an MCreator data list",
                host.objectSchema(host.props(
                        "dataList", host.stringSchema("Data list name (e.g. blocksitems, entities, biomes)")
                ), "dataList"),
                params -> listDataListValues(params));

        mcpServer.registerTool("resolveRegistryName", "Convert a display name to a valid resource/registry name",
                host.objectSchema(host.props(
                        "name", host.stringSchema("Display or camelCase name")
                ), "name"),
                params -> resolveRegistryName(params));

        mcpServer.registerTool("getGeneratorInfo", "Return information about the current generator and target Minecraft version",
                host.objectSchema(),
                params -> getGeneratorInfo(params));

        // ---- Workspace lifecycle ----
        mcpServer.registerTool("closeWorkspace", "Close the currently loaded MCreator workspace",
                host.objectSchema(),
                params -> closeWorkspace(params));

        mcpServer.registerTool("cancelGradleTask", "Cancel a running Gradle task",
                host.objectSchema(),
                params -> cancelGradleTask(params));

        mcpServer.registerTool("getGeneratedSource", "Return the generated Java source for a mod element",
                host.objectSchema(host.props(
                        "elementName", host.stringSchema("Name of the mod element")
                ), "elementName"),
                params -> getGeneratedSource(params));
    }

    // ------------------------------------------------------------------
    // Asset pipeline
    // ------------------------------------------------------------------

    private McpTypes.ToolResult importSound(Map<String, Object> params) {
        String soundName = (String) params.get("soundName");
        String audioFile = (String) params.get("audioFile");
        String category = (String) params.get("category");
        String subtitleKey = (String) params.get("subtitleKey");

        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) return host.createErrorResult("No workspace loaded");

            if (soundName == null || soundName.isBlank())
                return host.createErrorResult("soundName is required");

            File soundsDir = workspace.getFolderManager().getSoundsDir();
            if (soundsDir == null) return host.createErrorResult("No sounds directory configured for this generator");
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
            String subtitle = subtitleKey != null ? subtitleKey : "subtitles." + soundName;
            SoundElement sound = new SoundElement(soundName, files, cat, subtitle);
            workspace.removeSoundElement(sound);
            workspace.addSoundElement(sound);

            addLocalization(workspace, "en_us", subtitle, host.capitalize(soundName) + " sound");
            workspace.markDirty();

            return host.createSuccessResult("Sound event '" + soundName + "' imported to " + target.getAbsolutePath());
        } catch (Exception e) {
            LOG.error("Error importing sound", e);
            return host.createErrorResult("Failed to import sound: " + e.getMessage());
        }
    }

    private McpTypes.ToolResult listSounds(Map<String, Object> params) {
        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) return host.createErrorResult("No workspace loaded");

            List<Map<String, Object>> result = new ArrayList<>();
            for (SoundElement sound : workspace.getSoundElements()) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("name", sound.getName());
                map.put("category", sound.getCategory());
                map.put("files", sound.getFiles());
                map.put("subtitle", sound.getSubtitle());
                result.add(map);
            }
            return host.createSuccessResult(objectMapper.writeValueAsString(Map.of("sounds", result, "count", result.size())));
        } catch (Exception e) {
            LOG.error("Error listing sounds", e);
            return host.createErrorResult("Failed to list sounds: " + e.getMessage());
        }
    }

    private McpTypes.ToolResult deleteSound(Map<String, Object> params) {
        String soundName = (String) params.get("soundName");
        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) return host.createErrorResult("No workspace loaded");

            SoundElement toRemove = null;
            for (SoundElement sound : workspace.getSoundElements()) {
                if (sound.getName().equals(soundName)) {
                    toRemove = sound;
                    break;
                }
            }
            if (toRemove == null) return host.createErrorResult("Sound not found: " + soundName);

            workspace.removeSoundElement(toRemove);
            workspace.markDirty();
            return host.createSuccessResult("Sound '" + soundName + "' deleted");
        } catch (Exception e) {
            LOG.error("Error deleting sound", e);
            return host.createErrorResult("Failed to delete sound: " + e.getMessage());
        }
    }

    private McpTypes.ToolResult importAnimation(Map<String, Object> params) {
        String animationName = (String) params.get("animationName");
        String sourcePath = (String) params.get("sourcePath");
        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) return host.createErrorResult("No workspace loaded");

            File animDir = workspace.getFolderManager().getModelAnimationsDir();
            if (animDir == null) return host.createErrorResult("No animation directory configured for this generator");
            animDir.mkdirs();

            File source = new File(sourcePath);
            if (!source.exists()) return host.createErrorResult("Animation source file not found: " + sourcePath);

            String safeName = RegistryNameFixer.fix(animationName).replace("/", "_") + ".java";
            File target = new File(animDir, safeName);
            Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);

            return host.createSuccessResult("Animation imported to " + target.getAbsolutePath());
        } catch (Exception e) {
            LOG.error("Error importing animation", e);
            return host.createErrorResult("Failed to import animation: " + e.getMessage());
        }
    }

    private McpTypes.ToolResult listAnimations(Map<String, Object> params) {
        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) return host.createErrorResult("No workspace loaded");

            List<Animation> animations = Animation.getAnimations(workspace);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Animation anim : animations) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("name", anim.getName());
                map.put("file", anim.getFile().getAbsolutePath());
                map.put("subanimations", anim.getSubanimations());
                result.add(map);
            }
            return host.createSuccessResult(objectMapper.writeValueAsString(Map.of("animations", result, "count", result.size())));
        } catch (Exception e) {
            LOG.error("Error listing animations", e);
            return host.createErrorResult("Failed to list animations: " + e.getMessage());
        }
    }

    private McpTypes.ToolResult deleteAnimation(Map<String, Object> params) {
        String animationName = (String) params.get("animationName");
        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) return host.createErrorResult("No workspace loaded");

            File animDir = workspace.getFolderManager().getModelAnimationsDir();
            if (animDir == null) return host.createErrorResult("No animation directory configured");

            File target = new File(animDir, animationName + ".java");
            if (!target.exists()) target = new File(animDir, RegistryNameFixer.fix(animationName).replace("/", "_") + ".java");
            if (!target.exists()) return host.createErrorResult("Animation file not found: " + animationName);

            target.delete();
            return host.createSuccessResult("Animation '" + target.getName() + "' deleted");
        } catch (Exception e) {
            LOG.error("Error deleting animation", e);
            return host.createErrorResult("Failed to delete animation: " + e.getMessage());
        }
    }

    private McpTypes.ToolResult importStructure(Map<String, Object> params) {
        String structureName = (String) params.get("structureName");
        String sourcePath = (String) params.get("sourcePath");
        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) return host.createErrorResult("No workspace loaded");

            File structuresDir = workspace.getFolderManager().getStructuresDir();
            if (structuresDir == null) return host.createErrorResult("No structures directory configured for this generator");
            structuresDir.mkdirs();

            File source = new File(sourcePath);
            if (!source.exists()) return host.createErrorResult("Structure source file not found: " + sourcePath);

            String safeName = RegistryNameFixer.fix(structureName);
            File target = new File(structuresDir, safeName + ".nbt");
            Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);

            return host.createSuccessResult("Structure imported to " + target.getAbsolutePath());
        } catch (Exception e) {
            LOG.error("Error importing structure", e);
            return host.createErrorResult("Failed to import structure: " + e.getMessage());
        }
    }

    private McpTypes.ToolResult listStructures(Map<String, Object> params) {
        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) return host.createErrorResult("No workspace loaded");

            List<String> structures = workspace.getFolderManager().getStructureList();
            return host.createSuccessResult(objectMapper.writeValueAsString(Map.of("structures", structures, "count", structures.size())));
        } catch (Exception e) {
            LOG.error("Error listing structures", e);
            return host.createErrorResult("Failed to list structures: " + e.getMessage());
        }
    }

    private McpTypes.ToolResult deleteStructure(Map<String, Object> params) {
        String structureName = (String) params.get("structureName");
        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) return host.createErrorResult("No workspace loaded");

            workspace.getFolderManager().removeStructure(structureName);
            return host.createSuccessResult("Structure '" + structureName + "' deleted");
        } catch (Exception e) {
            LOG.error("Error deleting structure", e);
            return host.createErrorResult("Failed to delete structure: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Deep introspection
    // ------------------------------------------------------------------

    private McpTypes.ToolResult getElementPropertySchema(Map<String, Object> params) {
        String elementType = (String) params.get("elementType");
        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) return host.createErrorResult("No workspace loaded");

            ModElementType<?> type = ModElementTypeLoader.getModElementType(elementType);
            if (type == null) return host.createErrorResult("Unknown element type: " + elementType);

            Class<? extends GeneratableElement> clazz = type.getModElementStorageClass();
            List<Map<String, Object>> fields = new ArrayList<>();

            Class<?> current = clazz;
            while (current != null && current != Object.class) {
                for (Field field : current.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || field.isSynthetic())
                        continue;
                    field.setAccessible(true);
                    Map<String, Object> info = new LinkedHashMap<>();
                    info.put("name", field.getName());
                    info.put("type", field.getType().getName());
                    info.put("declaredIn", current.getSimpleName());
                    info.put("alias", findAlias(elementType, field.getName()));

                    if (field.getType() == String.class) {
                        info.put("kind", "string");
                    } else if (field.getType() == int.class || field.getType() == Integer.class
                            || field.getType() == double.class || field.getType() == Double.class
                            || field.getType() == float.class || field.getType() == Float.class
                            || field.getType() == long.class || field.getType() == Long.class) {
                        info.put("kind", "number");
                    } else if (field.getType() == boolean.class || field.getType() == Boolean.class) {
                        info.put("kind", "boolean");
                    } else if (List.class.isAssignableFrom(field.getType())) {
                        info.put("kind", "list");
                    } else if (Map.class.isAssignableFrom(field.getType())) {
                        info.put("kind", "map");
                    } else if (TextureHolder.class.isAssignableFrom(field.getType())) {
                        info.put("kind", "texture");
                    } else if (MappableElement.class.isAssignableFrom(field.getType())) {
                        info.put("kind", "mappable");
                    } else if (Procedure.class.isAssignableFrom(field.getType())) {
                        info.put("kind", "procedure");
                    } else {
                        info.put("kind", "object");
                    }
                    fields.add(info);
                }
                current = current.getSuperclass();
            }

            return host.createSuccessResult(objectMapper.writeValueAsString(Map.of("elementType", elementType, "fields", fields)));
        } catch (Exception e) {
            LOG.error("Error getting element schema", e);
            return host.createErrorResult("Failed to get element schema: " + e.getMessage());
        }
    }

    private McpTypes.ToolResult getElementDefaults(Map<String, Object> params) {
        String elementType = (String) params.get("elementType");
        String elementName = (String) params.get("elementName");
        if (elementName == null || elementName.isBlank()) elementName = "Sample";
        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) return host.createErrorResult("No workspace loaded");

            ModElementType<?> type = ModElementTypeLoader.getModElementType(elementType);
            if (type == null) return host.createErrorResult("Unknown element type: " + elementType);

            ModElement modElement = new ModElement(workspace, elementName, type);
            GeneratableElement ge = type.getModElementStorageClass().getDeclaredConstructor(ModElement.class).newInstance(modElement);
            host.applyGeneratableElementDefaults(ge, workspace, elementName);

            Map<String, Object> defaults = new LinkedHashMap<>();
            Class<?> current = ge.getClass();
            while (current != null && current != Object.class) {
                for (Field field : current.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || field.isSynthetic())
                        continue;
                    field.setAccessible(true);
                    defaults.put(field.getName(), simplifyValue(field.get(ge)));
                }
                current = current.getSuperclass();
            }

            return host.createSuccessResult(objectMapper.writeValueAsString(Map.of("elementType", elementType, "defaults", defaults)));
        } catch (Exception e) {
            LOG.error("Error getting element defaults", e);
            return host.createErrorResult("Failed to get element defaults: " + e.getMessage());
        }
    }

    private McpTypes.ToolResult getPropertyHelp(Map<String, Object> params) {
        String elementType = (String) params.get("elementType");
        String propertyName = (String) params.get("propertyName");
        try {
            List<File> helpRoots = helpSearchRoots();
            String[] candidates = {
                    elementType + "/" + propertyName + ".md",
                    elementType + "/" + propertyName.replace("_", "") + ".md",
                    elementType + "/" + propertyName.toLowerCase(Locale.ROOT).replace("_", "") + ".md",
                    "common/" + propertyName + ".md",
                    "common/" + propertyName.toLowerCase(Locale.ROOT) + ".md"
            };

            for (File root : helpRoots) {
                for (String candidate : candidates) {
                    File f = new File(root, candidate);
                    if (f.exists()) {
                        String content = Files.readString(f.toPath());
                        return host.createSuccessResult(content);
                    }
                }
            }
            return host.createErrorResult("No help file found for " + elementType + "/" + propertyName);
        } catch (Exception e) {
            LOG.error("Error reading property help", e);
            return host.createErrorResult("Failed to read property help: " + e.getMessage());
        }
    }

    private McpTypes.ToolResult listDataListValues(Map<String, Object> params) {
        String dataList = (String) params.get("dataList");
        try {
            Collection<DataListEntry> entries = DataListLoader.loadDataList(dataList);
            if (entries == null) return host.createErrorResult("Unknown data list: " + dataList);

            List<Map<String, Object>> result = new ArrayList<>();
            for (DataListEntry entry : entries) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("name", entry.getName());
                map.put("readableName", entry.getReadableName());
                map.put("type", entry.getType());
                map.put("description", entry.getDescription());
                if (entry.getTexture() != null) map.put("texture", entry.getTexture());
                if (entry instanceof MCItem item) map.put("subtypes", !item.hasNoSubtypes());
                result.add(map);
            }
            return host.createSuccessResult(objectMapper.writeValueAsString(Map.of("dataList", dataList, "entries", result, "count", result.size())));
        } catch (Exception e) {
            LOG.error("Error listing data list", e);
            return host.createErrorResult("Failed to list data list: " + e.getMessage());
        }
    }

    private McpTypes.ToolResult resolveRegistryName(Map<String, Object> params) {
        String name = (String) params.get("name");
        if (name == null || name.isBlank()) return host.createErrorResult("name is required");
        Map<String, String> map = new LinkedHashMap<>();
        map.put("fromCamelCase", RegistryNameFixer.fromCamelCase(name));
        map.put("fix", RegistryNameFixer.fix(name));
        map.put("input", name);
        try {
            return host.createSuccessResult(objectMapper.writeValueAsString(map));
        } catch (Exception e) {
            return host.createErrorResult("Failed to serialize result: " + e.getMessage());
        }
    }

    private McpTypes.ToolResult getGeneratorInfo(Map<String, Object> params) {
        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) return host.createErrorResult("No workspace loaded");

            GeneratorConfiguration gc = workspace.getGeneratorConfiguration();
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("generatorName", gc.getGeneratorName());
            info.put("displayName", gc.toString());
            info.put("minecraftVersion", gc.getGeneratorMinecraftVersion());
            info.put("buildFileVersion", gc.getGeneratorBuildFileVersion());
            info.put("subVersion", gc.getGeneratorSubVersion());
            if (gc.getGeneratorFlavor() != null) {
                info.put("flavor", gc.getGeneratorFlavor().name());
                info.put("baseLanguage", gc.getGeneratorFlavor().getBaseLanguage() != null ? gc.getGeneratorFlavor().getBaseLanguage().name() : null);
                info.put("gamePlatform", gc.getGeneratorFlavor().getGamePlatform() != null ? gc.getGeneratorFlavor().getGamePlatform().name() : null);
            }
            return host.createSuccessResult(objectMapper.writeValueAsString(info));
        } catch (Exception e) {
            LOG.error("Error getting generator info", e);
            return host.createErrorResult("Failed to get generator info: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Workspace lifecycle
    // ------------------------------------------------------------------

    private McpTypes.ToolResult closeWorkspace(Map<String, Object> params) {
        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) return host.createErrorResult("No workspace loaded");

            mcreator.closeThisMCreator(false);
            return host.createSuccessResult("Workspace close requested");
        } catch (Exception e) {
            LOG.error("Error closing workspace", e);
            return host.createErrorResult("Failed to close workspace: " + e.getMessage());
        }
    }

    private McpTypes.ToolResult cancelGradleTask(Map<String, Object> params) {
        try {
            mcreator.getGradleConsole().cancelTask();
            return host.createSuccessResult("Gradle task cancellation requested");
        } catch (Exception e) {
            LOG.error("Error canceling Gradle task", e);
            return host.createErrorResult("Failed to cancel Gradle task: " + e.getMessage());
        }
    }

    private McpTypes.ToolResult getGeneratedSource(Map<String, Object> params) {
        String elementName = (String) params.get("elementName");
        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) return host.createErrorResult("No workspace loaded");

            String pkg = workspace.getWorkspaceSettings().getModElementsPackage().replace('.', '/');
            File srcRoot = new File(workspace.getFolderManager().getWorkspaceFolder(), "src/main/java/" + pkg);
            if (!srcRoot.exists()) return host.createErrorResult("Generated source root not found: " + srcRoot);

            List<File> matches = new ArrayList<>();
            try (Stream<Path> paths = Files.walk(srcRoot.toPath())) {
                paths.filter(p -> p.getFileName() != null && p.getFileName().toString().startsWith(elementName) && p.toString().endsWith(".java"))
                        .forEach(p -> matches.add(p.toFile()));
            }

            if (matches.isEmpty()) return host.createErrorResult("No generated source found for element: " + elementName);

            Map<String, Object> result = new LinkedHashMap<>();
            for (File f : matches) {
                result.put(f.getName(), Files.readString(f.toPath()));
            }
            return host.createSuccessResult(objectMapper.writeValueAsString(Map.of("elementName", elementName, "files", result)));
        } catch (Exception e) {
            LOG.error("Error getting generated source", e);
            return host.createErrorResult("Failed to get generated source: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private String findAlias(String elementType, String fieldName) {
        Map<String, String> aliases = getAliasesForType(elementType);
        for (Map.Entry<String, String> e : aliases.entrySet()) {
            if (fieldName.equals(e.getValue())) return e.getKey();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> getAliasesForType(String elementType) {
        try {
            Class<?> applierClass = Class.forName("net.mcreator.MCreatorMCP.mcp.McpElementPropertyApplier");
            java.lang.reflect.Field aliasesField = applierClass.getDeclaredField("ALIASES");
            aliasesField.setAccessible(true);
            Map<String, Map<String, String>> allAliases = (Map<String, Map<String, String>>) aliasesField.get(null);
            Map<String, String> typeAliases = allAliases.get(elementType.toLowerCase(Locale.ROOT));
            return typeAliases != null ? typeAliases : Map.of();
        } catch (Exception e) {
            return Map.of();
        }
    }

    private Object simplifyValue(Object value) {
        if (value == null) return null;
        if (value instanceof String || value instanceof Number || value instanceof Boolean) return value;
        if (value instanceof TextureHolder th) return th.getRawTextureName();
        if (value instanceof MappableElement me) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("unmappedValue", me.getUnmappedValue());
            map.put("mappedValue", me.toString());
            return map;
        }
        if (value instanceof Procedure p) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", p.getName());
            return map;
        }
        if (value instanceof Collection<?> c) return c.stream().map(this::simplifyValue).collect(Collectors.toList());
        if (value instanceof Map<?, ?> m) {
            Map<Object, Object> map = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) map.put(e.getKey(), simplifyValue(e.getValue()));
            return map;
        }
        try {
            return objectMapper.convertValue(value, Object.class);
        } catch (Exception e) {
            return value.toString();
        }
    }

    private List<File> helpSearchRoots() {
        List<File> roots = new ArrayList<>();
        // Plugin source bundle
        File repoHelp = new File("/home/ubuntu/repos/MCreatorMCP/MCreator/plugins/mcreator-localization/help/default");
        if (repoHelp.exists()) roots.add(repoHelp);
        // Installed MCreator plugins folder
        String mcreatorPath = System.getProperty("mcreator.path");
        if (mcreatorPath == null) mcreatorPath = "/home/ubuntu/repos/MCreator20262";
        File installHelp = new File(mcreatorPath, "plugins/mcreator-localization/help/default");
        if (installHelp.exists()) roots.add(installHelp);
        return roots;
    }

    private byte[] decodeBase64DataUri(String dataUri) {
        String base64 = dataUri;
        int comma = base64.indexOf(',');
        if (comma > 0) base64 = base64.substring(comma + 1);
        return Base64.getDecoder().decode(base64);
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
}

package net.mcreator.MCreatorMCP;

import net.mcreator.MCreatorMCP.mcp.McpElementPropertyApplier;
import net.mcreator.MCreatorMCP.mcp.McpServer;
import net.mcreator.MCreatorMCP.mcp.McpTypes;
import net.mcreator.element.GeneratableElement;
import net.mcreator.element.ModElementType;
import net.mcreator.element.ModElementTypeLoader;
import net.mcreator.element.parts.TextureHolder;
import net.mcreator.element.types.Achievement;
import net.mcreator.element.types.Block;
import net.mcreator.element.types.Fluid;
import net.mcreator.element.types.interfaces.LimitedOptions;
import net.mcreator.element.types.interfaces.Numeric;
import net.mcreator.element.util.GEValidator;
import net.mcreator.generator.Generator;
import net.mcreator.generator.GeneratorFile;
import net.mcreator.generator.template.TemplateGeneratorException;
import net.mcreator.generator.mapping.MappableElement;
import net.mcreator.minecraft.DataListLoader;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.workspace.resources.TextureType;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.ModElement;
import net.mcreator.workspace.references.TextureReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Base64;
import java.util.*;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

/**
 * Service that implements MCreator tools for the MCP server.
 * This replaces the old IPC-based communication with direct integration.
 */
public class MCPToolsService {

    private static final Logger LOG = LogManager.getLogger("MCP-Tools");
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Register all MCreator tools with the MCP server
     */
    public void registerTools(McpServer mcpServer, MCreator mcreator) {
        LOG.info("Registering MCreator tools with MCP server");

        // Workspace management
        mcpServer.registerTool("buildWorkspace", "Build the current MCreator workspace",
                objectSchema(), params -> executeBuildWorkspace(mcreator));
        mcpServer.registerTool("getWorkspaceInfo", "Get detailed workspace information",
                objectSchema(), params -> getWorkspaceInfo(mcreator));
        mcpServer.registerTool("getWorkspaceSettings", "Get all workspace settings",
                objectSchema(), params -> getWorkspaceSettings(mcreator));
        mcpServer.registerTool("updateWorkspaceSettings", "Update workspace settings",
                objectSchema(props("settings", objectPropSchema("Map of setting names to values")), "settings"),
                params -> updateWorkspaceSettings(mcreator, params));
        mcpServer.registerTool("regenerateCode", "Regenerate code without building",
                objectSchema(), params -> executeRegenerateCode(mcreator));

        // Element discovery
        mcpServer.registerTool("listModElements", "List mod elements with optional filtering",
                objectSchema(props("elementType", stringSchema("Filter by element type")),
                        "elementType"),
                params -> listModElements(mcreator, params));
        mcpServer.registerTool("listModElementTypes", "List all available element types",
                objectSchema(), params -> listModElementTypes(mcreator));
        mcpServer.registerTool("getElementProperties", "Get all properties of a mod element as JSON",
                objectSchema(props("elementName", stringSchema("Name of the element")), "elementName"),
                params -> getElementProperties(mcreator, params));
        mcpServer.registerTool("searchElements", "Search mod elements by name or type",
                objectSchema(props("query", stringSchema("Search string")), "query"),
                params -> searchElements(mcreator, params));
        mcpServer.registerTool("deleteElement", "Delete mod element",
                objectSchema(props("elementName", stringSchema("Name of element to delete")), "elementName"),
                params -> deleteElement(mcreator, params));
        mcpServer.registerTool("validateElement", "Validate a mod element for missing textures or references",
                objectSchema(props("elementName", stringSchema("Name of element to validate")), "elementName"),
                params -> validateElement(mcreator, params));
        mcpServer.registerTool("validateWorkspace", "Validate the entire workspace",
                objectSchema(), params -> validateWorkspace(mcreator));

        // Generic element creation
        mcpServer.registerTool("createElement", "Create a customized mod element",
                objectSchema(props(
                        "elementType", stringSchema("Type of element to create"),
                        "elementName", stringSchema("Name of the new element"),
                        "properties", objectPropSchema("Element-specific customization properties")
                ), "elementType", "elementName"),
                params -> createElement(mcreator, params));

        // Per-type creation shortcuts
        for (ModElementType type : ModElementTypeLoader.getAllModElementTypes()) {
            String typeName = type.getRegistryName();
            String toolName = "create" + capitalize(typeName);
            String displayName = type.getReadableName();
            mcpServer.registerTool(toolName, "Create a " + displayName + " mod element",
                    objectSchema(props(
                            "elementName", stringSchema("Name of the new " + displayName),
                            "properties", objectPropSchema("Customization properties for the " + displayName)
                    ), "elementName"),
                    params -> createTypedElement(mcreator, typeName, params));
        }

        // Bedrock alias tools
        mcpServer.registerTool("createBedrockItem", "Create a Bedrock item",
                objectSchema(props("elementName", stringSchema("Name of the Bedrock item"),
                        "properties", objectPropSchema("Customization properties")), "elementName"),
                params -> createTypedElement(mcreator, "beitem", params));
        mcpServer.registerTool("createBedrockBlock", "Create a Bedrock block",
                objectSchema(props("elementName", stringSchema("Name of the Bedrock block"),
                        "properties", objectPropSchema("Customization properties")), "elementName"),
                params -> createTypedElement(mcreator, "beblock", params));
        mcpServer.registerTool("createBedrockEntity", "Create a Bedrock entity",
                objectSchema(props("elementName", stringSchema("Name of the Bedrock entity"),
                        "properties", objectPropSchema("Customization properties")), "elementName"),
                params -> createTypedElement(mcreator, "beentity", params));

        // Data-pack style helpers
        mcpServer.registerTool("registerLootTable", "Create a loot table element",
                objectSchema(props("elementName", stringSchema("Name of the loot table"),
                        "properties", objectPropSchema("Loot table properties")), "elementName"),
                params -> createTypedElement(mcreator, "loottable", params));
        mcpServer.registerTool("registerAdvancement", "Create an advancement element",
                objectSchema(props("elementName", stringSchema("Name of the advancement"),
                        "properties", objectPropSchema("Advancement properties")), "elementName"),
                params -> createTypedElement(mcreator, "achievement", params));
        mcpServer.registerTool("registerFunction", "Create a function element",
                objectSchema(props("elementName", stringSchema("Name of the function"),
                        "properties", objectPropSchema("Function properties")), "elementName"),
                params -> createTypedElement(mcreator, "function", params));

        // Asset management
        mcpServer.registerTool("listTexturesByType", "List textures in the workspace",
                objectSchema(props("type", stringSchema("Texture type: BLOCK, ITEM, ENTITY, etc."))),
                params -> listTexturesByType(mcreator, params));
        mcpServer.registerTool("importTexture", "Import a texture into the workspace",
                objectSchema(props(
                        "textureName", stringSchema("Name for the texture"),
                        "sourcePath", stringSchema("Source file path or base64 data URI"),
                        "textureType", stringSchema("Texture type: BLOCK, ITEM, ENTITY, etc."),
                        "width", stringSchema("Optional target width"),
                        "height", stringSchema("Optional target height"),
                        "animation", stringSchema("Generate .mcmeta animation file (true/false)"),
                        "frameTime", stringSchema("Animation frame time in ticks")
                ), "textureName", "sourcePath", "textureType"),
                params -> importTexture(mcreator, params));
        mcpServer.registerTool("deleteTexture", "Delete a texture from the workspace",
                objectSchema(props(
                        "textureName", stringSchema("Texture name"),
                        "textureType", stringSchema("Texture type: BLOCK, ITEM, ENTITY, etc.")
                ), "textureName", "textureType"),
                params -> deleteTexture(mcreator, params));
        mcpServer.registerTool("listModels", "List custom models in the workspace",
                objectSchema(), params -> listModels(mcreator));
        mcpServer.registerTool("importModel", "Import a model into the workspace",
                objectSchema(props(
                        "modelName", stringSchema("Name for the model"),
                        "sourcePath", stringSchema("Source file path")
                ), "modelName", "sourcePath"),
                params -> importModel(mcreator, params));
        mcpServer.registerTool("deleteModel", "Delete a model from the workspace",
                objectSchema(props("modelName", stringSchema("Model name")), "modelName"),
                params -> deleteModel(mcreator, params));
        mcpServer.registerTool("getAssetMetadata", "Get metadata for an asset",
                objectSchema(props(
                        "assetName", stringSchema("Asset name"),
                        "assetType", stringSchema("Asset type: texture, model")
                ), "assetName", "assetType"),
                params -> getAssetMetadata(mcreator, params));

        // Workspace variables
        mcpServer.registerTool("listWorkspaceVariables", "List all mod variables in the workspace",
                objectSchema(), params -> listWorkspaceVariables(mcreator));
        mcpServer.registerTool("createVariable", "Create a workspace variable",
                objectSchema(props(
                        "variableName", stringSchema("Variable name"),
                        "variableType", stringSchema("Variable type"),
                        "scope", stringSchema("Variable scope"),
                        "defaultValue", objectPropSchema("Default value")
                ), "variableName", "variableType", "scope"),
                params -> createVariable(mcreator, params));
        mcpServer.registerTool("updateVariable", "Update an existing workspace variable",
                objectSchema(props(
                        "variableName", stringSchema("Variable name"),
                        "variableType", stringSchema("Variable type"),
                        "scope", stringSchema("Variable scope"),
                        "defaultValue", objectPropSchema("Default value")
                ), "variableName"),
                params -> updateVariable(mcreator, params));
        mcpServer.registerTool("deleteVariable", "Delete a workspace variable",
                objectSchema(props("variableName", stringSchema("Variable name")), "variableName"),
                params -> deleteVariable(mcreator, params));

        // Localization
        mcpServer.registerTool("getLocalizations", "Get localization strings for a language",
                objectSchema(props("language", stringSchema("Language code"))),
                params -> getLocalizations(mcreator, params));
        mcpServer.registerTool("setLocalization", "Set a localization string",
                objectSchema(props(
                        "key", stringSchema("Localization key"),
                        "language", stringSchema("Language code"),
                        "value", stringSchema("Localized value")
                ), "key", "language", "value"),
                params -> setLocalization(mcreator, params));
        mcpServer.registerTool("addLanguage", "Add a new language to the workspace",
                objectSchema(props("languageCode", stringSchema("Language code")), "languageCode"),
                params -> addLanguage(mcreator, params));

        // Build & export
        mcpServer.registerTool("buildForJavaEdition", "Build the workspace for Java Edition",
                objectSchema(props(
                        "includeClient", objectPropSchema("Build client artifacts"),
                        "includeServer", objectPropSchema("Build server artifacts")
                )),
                params -> buildForJavaEdition(mcreator, params));
        mcpServer.registerTool("exportResourcePack", "Export the generated resources as a resource pack",
                objectSchema(props("outputPath", stringSchema("Output directory path")), "outputPath"),
                params -> exportResourcePack(mcreator, params));
        mcpServer.registerTool("exportBehaviorPack", "Export behavior pack data",
                objectSchema(props("outputPath", stringSchema("Output directory path")), "outputPath"),
                params -> exportBehaviorPack(mcreator, params));
        mcpServer.registerTool("deployToGameFolder", "Deploy the mod to the Minecraft game folder",
                objectSchema(props(
                        "editionType", stringSchema("java or bedrock"),
                        "gameFolderPath", stringSchema("Target game folder path")
                ), "editionType", "gameFolderPath"),
                params -> deployToGameFolder(mcreator, params));

        // Testing
        mcpServer.registerTool("runClient", "Start Minecraft client",
                objectSchema(), params -> executeRunClient(mcreator));
        mcpServer.registerTool("runServer", "Start Minecraft server",
                objectSchema(), params -> executeRunServer(mcreator));

        // Advanced tools (events, workflows, Bedrock packs, test reporting)
        new McpAdvancedToolsService(this, mcpServer, mcreator).registerTools();

        // Extra tools (tags, creative tabs, backups, generator switching, procedure editing,
        // in-game verification, model validation/conversion)
        new McpExtraToolsService(this, mcpServer, mcreator).registerTools();

        LOG.info("Registered {} MCreator tools", mcpServer.getToolCount());
    }

    /**
     * Build workspace tool
     */
    private McpTypes.ToolResult executeBuildWorkspace(MCreator mcreator) {
        LOG.info("Executing buildWorkspace tool");

        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) {
                return createErrorResult("No workspace loaded");
            }

            McpTypes.ToolResult regenResult = regenerateWorkspaceCode(workspace);
            if (Boolean.TRUE.equals(regenResult.getIsError()))
                return regenResult;

            // Trigger the Gradle build task via the MCreator console
            mcreator.getGradleConsole().exec("build");

            return createSuccessResult("Workspace build initiated successfully");

        } catch (Exception e) {
            LOG.error("Error building workspace", e);
            return createErrorResult("Failed to build workspace: " + e.getMessage());
        }
    }

    /**
     * Get workspace information
     */
    private McpTypes.ToolResult getWorkspaceInfo(MCreator mcreator) {
        LOG.info("Executing getWorkspaceInfo tool");

        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) {
                return createErrorResult("No workspace loaded");
            }

            Map<String, Object> info = new HashMap<>();
            info.put("name", workspace.getWorkspaceSettings().getModName());
            info.put("version", workspace.getWorkspaceSettings().getVersion());
            info.put("author", workspace.getWorkspaceSettings().getAuthor());
            info.put("description", workspace.getWorkspaceSettings().getDescription());
            info.put("mcreatorVersion", String.valueOf(workspace.getMCreatorVersion()));
            info.put("elementCount", workspace.getModElements().size());
            info.put("workspaceFolder", workspace.getWorkspaceFolder().getAbsolutePath());

            String infoJson = objectMapper.writeValueAsString(info);
            return createSuccessResult("Workspace information retrieved:\n" + infoJson);

        } catch (Exception e) {
            LOG.error("Error getting workspace info", e);
            return createErrorResult("Failed to get workspace info: " + e.getMessage());
        }
    }

    /**
     * Regenerate code tool
     */
    private McpTypes.ToolResult executeRegenerateCode(MCreator mcreator) {
        LOG.info("Executing regenerateCode tool");

        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) {
                return createErrorResult("No workspace loaded");
            }

            return regenerateWorkspaceCode(workspace);

        } catch (Exception e) {
            LOG.error("Error regenerating code", e);
            return createErrorResult("Failed to regenerate code: " + e.getMessage());
        }
    }

    private McpTypes.ToolResult regenerateWorkspaceCode(Workspace workspace) {
        try {
            Generator generator = workspace.getGenerator();
            if (generator == null)
                return createErrorResult("No generator available for the workspace");

            generator.generateBase(true);

            int generated = 0;
            for (ModElement me : workspace.getModElements()) {
                GeneratableElement ge = me.getGeneratableElement();
                if (ge == null)
                    continue;

                List<GeneratorFile> files = generator.generateElement(ge, true, true);
                if (files != null && !files.isEmpty())
                    generated++;
            }

            generator.runResourceSetupTasks();
            workspace.markDirty();

            return createSuccessResult("Code regenerated for " + generated + " elements");
        } catch (TemplateGeneratorException e) {
            LOG.error("Template generation error", e);
            return createErrorResult("Template generation failed: " + e.getMessage());
        } catch (Exception e) {
            LOG.error("Error regenerating workspace code", e);
            return createErrorResult("Failed to regenerate code: " + e.getMessage());
        }
    }

    /**
     * List mod elements tool
     */
    private McpTypes.ToolResult listModElements(MCreator mcreator, Map<String, Object> params) {
        LOG.info("Executing listModElements tool");

        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) {
                return createErrorResult("No workspace loaded");
            }

            String elementType = (String) params.get("elementType");
            Collection<ModElement> elements = workspace.getModElements();

            // Filter by type if specified
            if (elementType != null && !elementType.trim().isEmpty()) {
                elements = elements.stream()
                    .filter(element -> element.getType().getRegistryName().equalsIgnoreCase(elementType.trim()))
                    .collect(Collectors.toList());
            }

            List<Map<String, Object>> elementList = elements.stream()
                .map(this::modElementToMap)
                .collect(Collectors.toList());

            Map<String, Object> result = new HashMap<>();
            result.put("elements", elementList);
            result.put("count", elementList.size());
            result.put("filteredBy", elementType);

            String resultJson = objectMapper.writeValueAsString(result);
            return createSuccessResult("Found " + elementList.size() + " mod elements:\n" + resultJson);

        } catch (Exception e) {
            LOG.error("Error listing mod elements", e);
            return createErrorResult("Failed to list mod elements: " + e.getMessage());
        }
    }

    /**
     * Create element tool
     */
    McpTypes.ToolResult createElement(MCreator mcreator, Map<String, Object> params) {
        String elementType = (String) params.get("elementType");
        String elementName = (String) params.get("elementName");
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) params.get("properties");

        LOG.info("Executing createElement tool: {} of type {}", elementName, elementType);

        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) {
                return createErrorResult("No workspace loaded");
            }

            if (elementName == null || elementName.trim().isEmpty()) {
                return createErrorResult("Element name is required");
            }

            if (elementType == null || elementType.trim().isEmpty()) {
                return createErrorResult("Element type is required");
            }

            // Find the ModElementType
            ModElementType type = null;
            for (ModElementType met : ModElementTypeLoader.getAllModElementTypes()) {
                if (met.getRegistryName().equalsIgnoreCase(elementType.trim())) {
                    type = met;
                    break;
                }
            }

            if (type == null) {
                return createErrorResult("Unknown element type: " + elementType);
            }

            // Check if element already exists
            if (workspace.getModElementByName(elementName.trim()) != null) {
                return createErrorResult("Element with name '" + elementName.trim() + "' already exists");
            }

            // Create the element on EDT
            final ModElementType finalType = type;
            final String finalName = elementName.trim();

            javax.swing.SwingUtilities.invokeAndWait(() -> {
                ModElement element = new ModElement(workspace, finalName, finalType);

                // Instantiate the type-specific GeneratableElement so the generator has data to work with.
                // Without this the element is only an empty ModElement wrapper and will not generate code.
                try {
                    Class<? extends GeneratableElement> storageClass = finalType.getModElementStorageClass();
                    GeneratableElement generatableElement = storageClass.getDeclaredConstructor(ModElement.class)
                            .newInstance(element);

                    applyGeneratableElementDefaults(generatableElement, workspace, finalName);

                    if (properties != null) {
                        new McpElementPropertyApplier(workspace, finalType.getRegistryName(), finalName)
                                .applyProperties(generatableElement, properties);
                    }

                    workspace.getModElementManager().storeModElement(generatableElement);
                } catch (Exception e) {
                    LOG.warn("Could not instantiate generatable element for type {}: {}", finalType.getRegistryName(),
                            e.getMessage());
                }

                workspace.addModElement(element);
                workspace.markDirty();
            });

            return createSuccessResult("Element '" + elementName + "' of type '" + elementType + "' created successfully");

        } catch (Exception e) {
            LOG.error("Error creating element", e);
            return createErrorResult("Failed to create element: " + e.getMessage());
        }
    }

    /**
     * Applies minimal safe defaults to a freshly instantiated GeneratableElement so the
     * generator and preview code do not fail on null fields. This is especially important
     * for texture holders, non-null strings, and numeric defaults.
     */
    void applyGeneratableElementDefaults(GeneratableElement generatableElement, Workspace workspace,
            String elementName) {
        List<Field> fields = new ArrayList<>();
        Class<?> clazz = generatableElement.getClass();
        while (clazz != null && clazz != Object.class) {
            fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
            clazz = clazz.getSuperclass();
        }

        for (Field field : fields) {
            field.setAccessible(true);
            try {
                if (field.getType() == String.class && field.get(generatableElement) == null) {
                    if ("name".equals(field.getName()) || "commandName".equals(field.getName())) {
                        field.set(generatableElement, elementName);
                    } else if ("customModelName".equals(field.getName())) {
                        field.set(generatableElement, "Normal");
                    } else if ("destroyTool".equals(field.getName())) {
                        field.set(generatableElement, "Not specified");
                    } else if (field.getAnnotation(javax.annotation.Nullable.class) != null) {
                        // Leave nullable string fields untouched; GEValidator will handle required ones
                    } else {
                        LimitedOptions limited = field.getAnnotation(LimitedOptions.class);
                        if (limited != null && limited.value().length > 0) {
                            field.set(generatableElement, limited.value()[0]);
                        } else if (field.getAnnotation(javax.annotation.Nonnull.class) != null) {
                            field.set(generatableElement, "");
                        }
                    }
                } else if (field.getType() == TextureHolder.class) {
                    TextureHolder current = (TextureHolder) field.get(generatableElement);
                    if (current == null && !"itemTexture".equals(field.getName())) {
                        TextureType textureType = TextureType.ITEM;
                        TextureReference ref = field.getAnnotation(TextureReference.class);
                        if (ref != null) {
                            textureType = ref.value();
                        }
                        String placeholder = createPlaceholderTexture(workspace, textureType, "mcp_placeholder");
                        field.set(generatableElement, new TextureHolder(workspace, placeholder));
                    }
                } else if (MappableElement.class.isAssignableFrom(field.getType())) {
                    MappableElement current = (MappableElement) field.get(generatableElement);
                    if (current == null) {
                        setDefaultMappableElement(field, generatableElement, workspace);
                    }
                } else if (net.mcreator.element.parts.procedure.Procedure.class.isAssignableFrom(field.getType())) {
                    Object current = field.get(generatableElement);
                    if (current == null) {
                        setDefaultProcedureElement(field, generatableElement, workspace);
                    }
                } else if (isPrimitiveNumber(field.getType())) {
                    setNumericDefault(field, generatableElement);
                }
            } catch (Exception e) {
                LOG.warn("Failed to set default value for field {}: {}", field.getName(), e.getMessage());
            }
        }

        // Required fields that GEValidator does not auto-fill
        if (generatableElement instanceof Fluid fluid) {
            if (fluid.type == null || fluid.type.isEmpty()) fluid.type = "water";
        }
        if (generatableElement instanceof Achievement achievement) {
            if (achievement.triggerxml == null || achievement.triggerxml.isEmpty()) {
                achievement.triggerxml = "<xml xmlns=\"https://developers.google.com/blockly/xml\"><block type=\"advancement_trigger\" deletable=\"false\" x=\"40\" y=\"80\"><next><shadow type=\"custom_trigger\"></shadow></next></block></xml>";
            }
        }

        // MCreator's UI defaults Block renderType to 10 (solid block); leaving it at 0 skips model generation
        if (generatableElement instanceof Block && ((Block) generatableElement).renderType == 0) {
            ((Block) generatableElement).renderType = 10;
        }

        // Run MCreator's own validator to fill in numeric/option defaults and ensure valid values
        try {
            GEValidator.validateAndTryToCorrect(generatableElement, null);
        } catch (Exception e) {
            LOG.warn("GE validation failed for element {}: {}", elementName, e.getMessage());
        }
    }

    private static boolean isPrimitiveNumber(Class<?> type) {
        return type == int.class || type == long.class || type == float.class || type == double.class
                || type == short.class || type == byte.class;
    }

    private void setNumericDefault(Field field, Object fieldHolder) throws IllegalAccessException {
        Numeric numeric = field.getAnnotation(Numeric.class);
        if (numeric == null || numeric.optional() || numeric.init() == 0)
            return;

        Object current = field.get(fieldHolder);
        if (current == null)
            return;

        boolean isZero = false;
        if (current instanceof Number number) {
            isZero = number.doubleValue() == 0;
        }
        if (!isZero)
            return;

        Class<?> type = field.getType();
        if (type == int.class || type == long.class || type == short.class || type == byte.class) {
            long value = (long) numeric.init();
            if (type == int.class) {
                field.set(fieldHolder, (int) value);
            } else if (type == long.class) {
                field.set(fieldHolder, value);
            } else if (type == short.class) {
                field.set(fieldHolder, (short) value);
            } else if (type == byte.class) {
                field.set(fieldHolder, (byte) value);
            }
        } else if (type == float.class) {
            field.set(fieldHolder, (float) numeric.init());
        } else if (type == double.class) {
            field.set(fieldHolder, numeric.init());
        }
    }

    private void setDefaultProcedureElement(Field field, Object fieldHolder, Workspace workspace) {
        try {
            Class<?> type = field.getType();
            java.lang.reflect.Constructor<?>[] constructors = type.getDeclaredConstructors();
            for (java.lang.reflect.Constructor<?> ctor : constructors) {
                ctor.setAccessible(true);
                Class<?>[] params = ctor.getParameterTypes();
                if (params.length == 1 && params[0] == String.class) {
                    field.set(fieldHolder, ctor.newInstance((String) null));
                    return;
                } else if (params.length == 2 && params[0] == String.class) {
                    Object secondArg = defaultProcedureReturnValue(params[1]);
                    if (secondArg != null) {
                        field.set(fieldHolder, ctor.newInstance((String) null, secondArg));
                        return;
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("Could not create default procedure element for field {}: {}", field.getName(), e.getMessage());
        }
    }

    private Object defaultProcedureReturnValue(Class<?> type) {
        if (type == double.class || type == Double.class) return 0.0;
        if (type == boolean.class || type == Boolean.class) return false;
        if (type == String.class) return "";
        if (type == List.class) return new ArrayList<String>();
        if (type == String[].class) return new String[0];
        return null;
    }

    /**
     * Instantiates a default MappableElement for fields that would otherwise remain null.
     * Some known types have safe defaults (e.g. StepSound), while optional types are left empty.
     */
    private void setDefaultMappableElement(Field field, Object fieldHolder, Workspace workspace) {
        try {
            Class<?> elementType = field.getType();
            java.lang.reflect.Constructor<?> ctor = elementType.getDeclaredConstructor(Workspace.class, String.class);
            ctor.setAccessible(true);

            String defaultValue = "";
            String typeName = elementType.getSimpleName();
            if ("StepSound".equals(typeName)) {
                defaultValue = "STONE";
            } else if ("AIPathNodeType".equals(typeName)) {
                defaultValue = "DEFAULT";
            } else if ("MItemBlock".equals(typeName) || "Sound".equals(typeName)) {
                defaultValue = "";
            } else {
                // For other mappable types, try to find the first valid data list entry
                MappableElement probe = (MappableElement) ctor.newInstance((Workspace) null, "");
                String mappingSource = probe.getNameMapper().getMappingSource();
                if (mappingSource != null) {
                    Map<String, ?> dataMap = DataListLoader.loadDataMap(mappingSource);
                    if (dataMap != null) {
                        for (String key : dataMap.keySet()) {
                            if (key != null && !key.isEmpty() && !key.startsWith("_")) {
                                defaultValue = key;
                                break;
                            }
                        }
                    }
                }
            }

            MappableElement value = (MappableElement) ctor.newInstance(workspace, defaultValue);
            field.set(fieldHolder, value);
        } catch (Exception e) {
            LOG.warn("Could not create default mappable element for field {}: {}", field.getName(), e.getMessage());
        }
    }

    /**
     * Creates a small placeholder texture PNG in the workspace if it does not yet exist
     * and returns the texture name (without extension) to use for model generation.
     */
    String createPlaceholderTexture(Workspace workspace, TextureType textureType, String name) {
        try {
            File texturesFolder = workspace.getFolderManager().getTexturesFolder(textureType);
            if (texturesFolder == null) {
                texturesFolder = new File(workspace.getFolderManager().getWorkspaceFolder(),
                        "src/main/resources/assets/" + workspace.getWorkspaceSettings().getModID() + "/textures/"
                                + textureType.getID() + "s");
            }
            texturesFolder.mkdirs();

            File textureFile = new File(texturesFolder, name + ".png");
            if (!textureFile.isFile()) {
                BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = image.createGraphics();
                g.setColor(new Color(0x2C9EEC));
                g.fillRect(0, 0, 16, 16);
                g.setColor(Color.WHITE);
                g.drawRect(0, 0, 15, 15);
                g.dispose();
                javax.imageio.ImageIO.write(image, "png", textureFile);
            }
        } catch (Exception e) {
            LOG.warn("Could not create placeholder texture for type {}: {}", textureType.getID(), e.getMessage());
        }
        return name;
    }

    /**
     * Delete element tool
     */
    private McpTypes.ToolResult deleteElement(MCreator mcreator, Map<String, Object> params) {
        String elementName = (String) params.get("elementName");

        LOG.info("Executing deleteElement tool: {}", elementName);

        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) {
                return createErrorResult("No workspace loaded");
            }

            if (elementName == null || elementName.trim().isEmpty()) {
                return createErrorResult("Element name is required");
            }

            ModElement element = workspace.getModElementByName(elementName.trim());
            if (element == null) {
                return createErrorResult("Element '" + elementName + "' not found");
            }

            // Delete the element on EDT
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                workspace.removeModElement(element);
                workspace.markDirty();
            });

            return createSuccessResult("Element '" + elementName + "' deleted successfully");

        } catch (Exception e) {
            LOG.error("Error deleting element", e);
            return createErrorResult("Failed to delete element: " + e.getMessage());
        }
    }

    /**
     * Get detailed workspace settings
     */
    private McpTypes.ToolResult getWorkspaceSettings(MCreator mcreator) {
        LOG.info("Executing getWorkspaceSettings tool");

        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) {
                return createErrorResult("No workspace loaded");
            }

            net.mcreator.workspace.settings.WorkspaceSettings settings = workspace.getWorkspaceSettings();
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("modName", settings.getModName());
            map.put("modId", settings.getModID());
            map.put("version", settings.getVersion());
            map.put("description", settings.getDescription());
            map.put("author", settings.getAuthor());
            map.put("websiteURL", settings.getWebsiteURL());
            map.put("license", settings.getLicense());
            map.put("modPicture", settings.getModPicture());
            map.put("credits", settings.getCredits());
            map.put("serverSideOnly", settings.isServerSideOnly());
            map.put("updateURL", settings.getUpdateURL());
            map.put("requiredMods", settings.getRequiredMods());
            map.put("dependencies", settings.getDependencies());
            map.put("dependants", settings.getDependants());
            map.put("mcreatorDependencies", settings.getMCreatorDependencies());
            map.put("currentGenerator", settings.getCurrentGenerator());

            String json = objectMapper.writeValueAsString(map);
            return createSuccessResult("Workspace settings retrieved:\n" + json);

        } catch (Exception e) {
            LOG.error("Error getting workspace settings", e);
            return createErrorResult("Failed to get workspace settings: " + e.getMessage());
        }
    }

    /**
     * Update workspace settings
     */
    private McpTypes.ToolResult updateWorkspaceSettings(MCreator mcreator, Map<String, Object> params) {
        LOG.info("Executing updateWorkspaceSettings tool");

        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) {
                return createErrorResult("No workspace loaded");
            }

            net.mcreator.workspace.settings.WorkspaceSettings settings = workspace.getWorkspaceSettings();
            List<String> updated = new ArrayList<>();

            for (Map.Entry<String, Object> entry : params.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if ("settings".equals(key) && value instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> settingsMap = (Map<String, Object>) value;
                    for (Map.Entry<String, Object> se : settingsMap.entrySet()) {
                        if (applyWorkspaceSetting(settings, se.getKey(), se.getValue()))
                            updated.add(se.getKey());
                    }
                } else if (!"workspace".equals(key)) {
                    if (applyWorkspaceSetting(settings, key, value))
                        updated.add(key);
                }
            }

            workspace.markDirty();

            return createSuccessResult("Updated workspace settings: " + updated);

        } catch (Exception e) {
            LOG.error("Error updating workspace settings", e);
            return createErrorResult("Failed to update workspace settings: " + e.getMessage());
        }
    }

    private boolean applyWorkspaceSetting(net.mcreator.workspace.settings.WorkspaceSettings settings, String key, Object value) {
        if (value == null)
            return false;
        try {
            switch (key.toLowerCase(Locale.ROOT)) {
            case "modname", "mod_name" -> {
                settings.setModName(String.valueOf(value));
                return true;
            }
            case "version" -> {
                settings.setVersion(String.valueOf(value));
                return true;
            }
            case "description" -> {
                settings.setDescription(String.valueOf(value));
                return true;
            }
            case "author" -> {
                settings.setAuthor(String.valueOf(value));
                return true;
            }
            case "websiteurl", "website" -> {
                settings.setWebsiteURL(String.valueOf(value));
                return true;
            }
            case "license" -> {
                settings.setLicense(String.valueOf(value));
                return true;
            }
            case "modpicture", "mod_picture" -> {
                settings.setModPicture(String.valueOf(value));
                return true;
            }
            case "credits" -> {
                settings.setCredits(String.valueOf(value));
                return true;
            }
            case "serversideonly" -> {
                settings.setServerSideOnly(toBoolean(value));
                return true;
            }
            case "updateurl", "update_url" -> {
                settings.setUpdateURL(String.valueOf(value));
                return true;
            }
            case "requiredmods", "required_mods" -> {
                settings.setRequiredMods(toStringSet(value));
                return true;
            }
            case "dependencies" -> {
                settings.setDependencies(toStringSet(value));
                return true;
            }
            case "dependants" -> {
                settings.setDependants(toStringSet(value));
                return true;
            }
            case "mcreatordependencies", "mcreator_dependencies" -> {
                settings.setMCreatorDependencies(toStringSet(value));
                return true;
            }
            case "modelementspackage", "mod_elements_package", "package" -> {
                settings.setModElementsPackage(String.valueOf(value));
                return true;
            }
            case "modid" -> {
                try {
                    java.lang.reflect.Field f = net.mcreator.workspace.settings.WorkspaceSettings.class.getDeclaredField("modid");
                    f.setAccessible(true);
                    f.set(settings, String.valueOf(value));
                    return true;
                } catch (Exception ex) {
                    LOG.warn("Could not set modid: {}", ex.getMessage());
                }
            }
            }
        } catch (Exception e) {
            LOG.warn("Could not set workspace setting {}: {}", key, e.getMessage());
        }
        return false;
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean b)
            return b;
        String s = String.valueOf(value).toLowerCase(Locale.ROOT);
        return "true".equals(s) || "yes".equals(s) || "1".equals(s) || "on".equals(s);
    }

    private Set<String> toStringSet(Object value) {
        Set<String> result = new HashSet<>();
        if (value instanceof String s) {
            if (!s.isEmpty())
                result.add(s);
        } else if (value instanceof Iterable<?> it) {
            for (Object o : it) {
                if (o != null && !String.valueOf(o).isEmpty())
                    result.add(String.valueOf(o));
            }
        } else if (value instanceof String[] arr) {
            for (String s : arr) {
                if (s != null && !s.isEmpty())
                    result.add(s);
            }
        }
        return result;
    }

    /**
     * List all available mod element types
     */
    private McpTypes.ToolResult listModElementTypes(MCreator mcreator) {
        LOG.info("Executing listModElementTypes tool");

        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) {
                return createErrorResult("No workspace loaded");
            }

            List<Map<String, Object>> types = new ArrayList<>();
            for (ModElementType<?> type : ModElementTypeLoader.getAllModElementTypes()) {
                Map<String, Object> map = new HashMap<>();
                map.put("registryName", type.getRegistryName());
                map.put("readableName", type.getReadableName());
                map.put("description", type.getDescription());
                map.put("storageClass", type.getModElementStorageClass().getSimpleName());
                types.add(map);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("types", types);
            result.put("count", types.size());

            String json = objectMapper.writeValueAsString(result);
            return createSuccessResult("Available element types:\n" + json);

        } catch (Exception e) {
            LOG.error("Error listing element types", e);
            return createErrorResult("Failed to list element types: " + e.getMessage());
        }
    }

    /**
     * Get all properties of a mod element as JSON
     */
    private McpTypes.ToolResult getElementProperties(MCreator mcreator, Map<String, Object> params) {
        String elementName = (String) params.get("elementName");
        LOG.info("Executing getElementProperties tool: {}", elementName);

        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) {
                return createErrorResult("No workspace loaded");
            }

            if (elementName == null || elementName.trim().isEmpty()) {
                return createErrorResult("Element name is required");
            }

            ModElement element = workspace.getModElementByName(elementName.trim());
            if (element == null) {
                return createErrorResult("Element '" + elementName + "' not found");
            }

            GeneratableElement ge = element.getGeneratableElement();
            if (ge == null) {
                return createErrorResult("Element has no generatable data");
            }

            String json = workspace.getModElementManager().generatableElementToJSON(ge);
            return createSuccessResult("Properties for '" + elementName + "':\n" + json);

        } catch (Exception e) {
            LOG.error("Error getting element properties", e);
            return createErrorResult("Failed to get element properties: " + e.getMessage());
        }
    }

    /**
     * Search elements by name or type
     */
    private McpTypes.ToolResult searchElements(MCreator mcreator, Map<String, Object> params) {
        String query = (String) params.get("query");
        LOG.info("Executing searchElements tool: {}", query);

        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) {
                return createErrorResult("No workspace loaded");
            }

            if (query == null || query.trim().isEmpty()) {
                return createErrorResult("Query is required");
            }

            String lower = query.toLowerCase(Locale.ROOT);
            List<Map<String, Object>> results = workspace.getModElements().stream()
                    .filter(e -> e.getName().toLowerCase(Locale.ROOT).contains(lower)
                            || e.getType().getRegistryName().toLowerCase(Locale.ROOT).contains(lower))
                    .map(this::modElementToMap)
                    .toList();

            Map<String, Object> result = new HashMap<>();
            result.put("elements", results);
            result.put("count", results.size());

            String json = objectMapper.writeValueAsString(result);
            return createSuccessResult("Found " + results.size() + " matching elements:\n" + json);

        } catch (Exception e) {
            LOG.error("Error searching elements", e);
            return createErrorResult("Failed to search elements: " + e.getMessage());
        }
    }

    /**
     * Validate a mod element for missing textures, broken references, etc.
     */
    private McpTypes.ToolResult validateElement(MCreator mcreator, Map<String, Object> params) {
        String elementName = (String) params.get("elementName");
        LOG.info("Executing validateElement tool: {}", elementName);

        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) {
                return createErrorResult("No workspace loaded");
            }

            if (elementName == null || elementName.trim().isEmpty()) {
                return createErrorResult("Element name is required");
            }

            ModElement element = workspace.getModElementByName(elementName.trim());
            if (element == null) {
                return createErrorResult("Element '" + elementName + "' not found");
            }

            GeneratableElement ge = element.getGeneratableElement();
            if (ge == null) {
                return createErrorResult("Element has no generatable data");
            }

            List<String> warnings = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            validateGeneratableElement(ge, warnings);

            Map<String, Object> result = new HashMap<>();
            result.put("elementName", elementName);
            result.put("valid", errors.isEmpty());
            result.put("warnings", warnings);
            result.put("errors", errors);

            String json = objectMapper.writeValueAsString(result);
            return createSuccessResult("Validation result for '" + elementName + "':\n" + json);

        } catch (Exception e) {
            LOG.error("Error validating element", e);
            return createErrorResult("Failed to validate element: " + e.getMessage());
        }
    }

    private void validateGeneratableElement(GeneratableElement ge, List<String> warnings) {
        List<Field> fields = new ArrayList<>();
        Class<?> clazz = ge.getClass();
        while (clazz != null && clazz != Object.class) {
            fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
            clazz = clazz.getSuperclass();
        }

        for (Field field : fields) {
            field.setAccessible(true);
            try {
                Object value = field.get(ge);
                if (value instanceof net.mcreator.element.parts.TextureHolder th) {
                    if (th.isEmpty()) {
                        warnings.add("Texture field '" + field.getName() + "' is empty");
                    }
                } else if (value instanceof net.mcreator.generator.mapping.MappableElement me) {
                    if (!me.isValidReference()) {
                        warnings.add("Reference field '" + field.getName() + "' has invalid value: " + me.getUnmappedValue());
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * List textures in the workspace, optionally filtered by type
     */
    private McpTypes.ToolResult listTexturesByType(MCreator mcreator, Map<String, Object> params) {
        String typeName = (String) params.get("type");
        LOG.info("Executing listTexturesByType tool: {}", typeName);

        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) {
                return createErrorResult("No workspace loaded");
            }

            List<Map<String, Object>> textures = new ArrayList<>();
            if (typeName != null && !typeName.trim().isEmpty()) {
                TextureType textureType;
                try {
                    textureType = TextureType.valueOf(typeName.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    return createErrorResult("Unknown texture type: " + typeName);
                }
                List<File> files = workspace.getFolderManager().getTexturesList(textureType);
                for (File file : files) {
                    if (file.getName().toLowerCase(Locale.ROOT).endsWith(".png")) {
                        Map<String, Object> map = new HashMap<>();
                        map.put("name", file.getName().replaceAll("\\.png$", ""));
                        map.put("type", textureType.name());
                        map.put("path", file.getAbsolutePath());
                        textures.add(map);
                    }
                }
            } else {
                for (TextureType textureType : TextureType.values()) {
                    for (File file : workspace.getFolderManager().getTexturesList(textureType)) {
                        if (file.getName().toLowerCase(Locale.ROOT).endsWith(".png")) {
                            Map<String, Object> map = new HashMap<>();
                            map.put("name", file.getName().replaceAll("\\.png$", ""));
                            map.put("type", textureType.name());
                            map.put("path", file.getAbsolutePath());
                            textures.add(map);
                        }
                    }
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("textures", textures);
            result.put("count", textures.size());

            String json = objectMapper.writeValueAsString(result);
            return createSuccessResult("Found " + textures.size() + " textures:\n" + json);

        } catch (Exception e) {
            LOG.error("Error listing textures", e);
            return createErrorResult("Failed to list textures: " + e.getMessage());
        }
    }

    /**
     * Run client tool
     */
    private McpTypes.ToolResult executeRunClient(MCreator mcreator) {
        LOG.info("Executing runClient tool");

        try {
            if (mcreator.getWorkspace() == null) {
                return createErrorResult("No workspace loaded");
            }

            // Execute run client on EDT without blocking the HTTP response
            javax.swing.SwingUtilities.invokeLater(() -> {
                mcreator.getActionRegistry().runClient.doAction();
            });

            return createSuccessResult("Minecraft client started successfully");

        } catch (Exception e) {
            LOG.error("Error running client", e);
            return createErrorResult("Failed to run client: " + e.getMessage());
        }
    }

    /**
     * Run server tool
     */
    private McpTypes.ToolResult executeRunServer(MCreator mcreator) {
        LOG.info("Executing runServer tool");

        try {
            if (mcreator.getWorkspace() == null) {
                return createErrorResult("No workspace loaded");
            }

            // Execute run server on EDT without blocking the HTTP response
            javax.swing.SwingUtilities.invokeLater(() -> {
                mcreator.getActionRegistry().runServer.doAction();
            });

            return createSuccessResult("Minecraft server started successfully");

        } catch (Exception e) {
            LOG.error("Error running server", e);
            return createErrorResult("Failed to run server: " + e.getMessage());
        }
    }

    /**
     * Helper method to convert ModElement to Map
     */
    private Map<String, Object> modElementToMap(ModElement element) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", element.getName());
        map.put("type", element.getType().getRegistryName());
        map.put("isLocked", element.isCodeLocked());
        map.put("sortIndex", element.getName());
        return map;
    }

    /**
     * Helper method to create success result
     */
    McpTypes.ToolResult createSuccessResult(String message) {
        List<McpTypes.ToolContent> content = List.of(
            new McpTypes.ToolContent("text", message)
        );
        return new McpTypes.ToolResult(content, false);
    }

    /**
     * Helper method to create error result
     */
    McpTypes.ToolResult createErrorResult(String message) {
        List<McpTypes.ToolContent> content = List.of(
            new McpTypes.ToolContent("text", "Error: " + message)
        );
        return new McpTypes.ToolResult(content, true);
    }

    // ---- Additional tool implementations ----

    /**
     * Helper for per-type creation shortcuts. Injects the elementType parameter and delegates to createElement.
     */
    McpTypes.ToolResult createTypedElement(MCreator mcreator, String elementType, Map<String, Object> params) {
        Map<String, Object> copy = new HashMap<>(params != null ? params : Map.of());
        copy.put("elementType", elementType);
        return createElement(mcreator, copy);
    }

    /**
     * Import a texture into the workspace. Supports file path, base64 data URI,
     * optional resize, and .mcmeta animation generation.
     */
    private McpTypes.ToolResult importTexture(MCreator mcreator, Map<String, Object> params) {
        String textureName = (String) params.get("textureName");
        String sourcePath = (String) params.get("sourcePath");
        String textureTypeName = (String) params.get("textureType");
        int width = toInt(params.get("width"), -1);
        int height = toInt(params.get("height"), -1);
        boolean animation = toBoolean(params.get("animation"), false);
        int frameTime = toInt(params.get("frameTime"), 1);

        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) return createErrorResult("No workspace loaded");

            TextureType textureType;
            try {
                textureType = TextureType.valueOf(textureTypeName.trim().toUpperCase(Locale.ROOT));
            } catch (Exception e) {
                return createErrorResult("Unknown texture type: " + textureTypeName);
            }

            BufferedImage image = null;
            if (sourcePath != null && sourcePath.startsWith("data:")) {
                int comma = sourcePath.indexOf(',');
                String base64 = comma > 0 ? sourcePath.substring(comma + 1) : sourcePath;
                byte[] bytes = Base64.getDecoder().decode(base64);
                try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
                    image = ImageIO.read(bais);
                }
            } else if (sourcePath != null) {
                File source = new File(sourcePath);
                if (!source.exists()) return createErrorResult("Source file not found: " + sourcePath);
                image = ImageIO.read(source);
            }

            if (image == null) return createErrorResult("Could not decode image from source");

            int targetWidth = width > 0 ? width : image.getWidth();
            int targetHeight = height > 0 ? height : image.getHeight();
            if (targetWidth != image.getWidth() || targetHeight != image.getHeight()) {
                BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = scaled.createGraphics();
                g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(image, 0, 0, targetWidth, targetHeight, null);
                g.dispose();
                image = scaled;
            }

            File target = workspace.getFolderManager().getTextureFile(textureName.replaceAll("\\.png$", ""), textureType);
            target.getParentFile().mkdirs();
            ImageIO.write(image, "png", target);

            if (animation) {
                File mcmetaFile = new File(target.getParentFile(), textureName.replaceAll("\\.png$", "") + ".png.mcmeta");
                String mcmetaJson = "{\"animation\":{\"frametime\":" + frameTime + "}}";
                java.nio.file.Files.writeString(mcmetaFile.toPath(), mcmetaJson);
            }

            return createSuccessResult("Texture imported to " + target.getAbsolutePath());
        } catch (Exception e) {
            LOG.error("Error importing texture", e);
            return createErrorResult("Failed to import texture: " + e.getMessage());
        }
    }

    /**
     * Delete a texture from the workspace.
     */
    private McpTypes.ToolResult deleteTexture(MCreator mcreator, Map<String, Object> params) {
        String textureName = (String) params.get("textureName");
        String textureTypeName = (String) params.get("textureType");

        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) return createErrorResult("No workspace loaded");

            TextureType textureType;
            try {
                textureType = TextureType.valueOf(textureTypeName.trim().toUpperCase(Locale.ROOT));
            } catch (Exception e) {
                return createErrorResult("Unknown texture type: " + textureTypeName);
            }

            File target = workspace.getFolderManager().getTextureFile(textureName.replaceAll("\\.png$", ""), textureType);
            if (!target.exists()) return createErrorResult("Texture not found: " + target.getAbsolutePath());

            target.delete();
            return createSuccessResult("Texture deleted: " + target.getAbsolutePath());
        } catch (Exception e) {
            LOG.error("Error deleting texture", e);
            return createErrorResult("Failed to delete texture: " + e.getMessage());
        }
    }

    /**
     * List custom models in the workspace models directory.
     */
    private McpTypes.ToolResult listModels(MCreator mcreator) {
        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) return createErrorResult("No workspace loaded");

            File modelsDir = workspace.getFolderManager().getModelsDir();
            List<Map<String, Object>> models = new ArrayList<>();
            if (modelsDir.exists() && modelsDir.isDirectory()) {
                for (File f : modelsDir.listFiles()) {
                    if (f.isFile()) {
                        Map<String, Object> map = new HashMap<>();
                        map.put("name", f.getName());
                        map.put("path", f.getAbsolutePath());
                        map.put("size", f.length());
                        models.add(map);
                    }
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("models", models);
            result.put("count", models.size());
            return createSuccessResult("Found " + models.size() + " models:\n" + objectMapper.writeValueAsString(result));
        } catch (Exception e) {
            LOG.error("Error listing models", e);
            return createErrorResult("Failed to list models: " + e.getMessage());
        }
    }

    /**
     * Import a model file into the workspace models directory.
     */
    private McpTypes.ToolResult importModel(MCreator mcreator, Map<String, Object> params) {
        String modelName = (String) params.get("modelName");
        String sourcePath = (String) params.get("sourcePath");

        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) return createErrorResult("No workspace loaded");

            File source = new File(sourcePath);
            if (!source.exists()) return createErrorResult("Source file not found: " + sourcePath);

            File modelsDir = workspace.getFolderManager().getModelsDir();
            modelsDir.mkdirs();
            File target = new File(modelsDir, modelName);
            java.nio.file.Files.copy(source.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return createSuccessResult("Model imported to " + target.getAbsolutePath());
        } catch (Exception e) {
            LOG.error("Error importing model", e);
            return createErrorResult("Failed to import model: " + e.getMessage());
        }
    }

    /**
     * Delete a model from the workspace.
     */
    private McpTypes.ToolResult deleteModel(MCreator mcreator, Map<String, Object> params) {
        String modelName = (String) params.get("modelName");

        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) return createErrorResult("No workspace loaded");

            File target = new File(workspace.getFolderManager().getModelsDir(), modelName);
            if (!target.exists()) return createErrorResult("Model not found: " + target.getAbsolutePath());

            target.delete();
            return createSuccessResult("Model deleted: " + target.getAbsolutePath());
        } catch (Exception e) {
            LOG.error("Error deleting model", e);
            return createErrorResult("Failed to delete model: " + e.getMessage());
        }
    }

    /**
     * Get metadata (size, dimensions for textures) for an asset.
     */
    private McpTypes.ToolResult getAssetMetadata(MCreator mcreator, Map<String, Object> params) {
        String assetName = (String) params.get("assetName");
        String assetType = (String) params.get("assetType");

        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) return createErrorResult("No workspace loaded");

            File file = null;
            int width = 0, height = 0;
            if ("texture".equalsIgnoreCase(assetType)) {
                for (TextureType tt : TextureType.values()) {
                    File candidate = workspace.getFolderManager().getTextureFile(assetName.replaceAll("\\.png$", ""), tt);
                    if (candidate.exists()) {
                        file = candidate;
                        break;
                    }
                }
                if (file != null) {
                    java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(file);
                    if (img != null) {
                        width = img.getWidth();
                        height = img.getHeight();
                    }
                }
            } else if ("model".equalsIgnoreCase(assetType)) {
                file = new File(workspace.getFolderManager().getModelsDir(), assetName);
            }

            if (file == null || !file.exists()) return createErrorResult("Asset not found: " + assetName);

            Map<String, Object> map = new HashMap<>();
            map.put("name", file.getName());
            map.put("path", file.getAbsolutePath());
            map.put("size", file.length());
            if (width > 0 && height > 0) {
                map.put("width", width);
                map.put("height", height);
            }
            return createSuccessResult("Asset metadata:\n" + objectMapper.writeValueAsString(map));
        } catch (Exception e) {
            LOG.error("Error getting asset metadata", e);
            return createErrorResult("Failed to get asset metadata: " + e.getMessage());
        }
    }

    /**
     * Validate the workspace by checking all mod elements for missing textures and invalid references.
     */
    private McpTypes.ToolResult validateWorkspace(MCreator mcreator) {
        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) return createErrorResult("No workspace loaded");

            List<String> warnings = new ArrayList<>();
            for (ModElement element : workspace.getModElements()) {
                GeneratableElement ge = element.getGeneratableElement();
                if (ge == null) {
                    warnings.add(element.getName() + ": missing generatable data");
                    continue;
                }

                try {
                    GEValidator.validateAndTryToCorrect(ge, null);
                } catch (Exception e) {
                    warnings.add(element.getName() + ": validation error " + e.getMessage());
                }

                for (Field field : getAllFields(ge.getClass())) {
                    field.setAccessible(true);
                    try {
                        Object value = field.get(ge);
                        if (value instanceof TextureHolder th) {
                            boolean found = false;
                            for (TextureType tt : TextureType.values()) {
                                if (th.toFile(tt).exists()) {
                                    found = true;
                                    break;
                                }
                            }
                            if (!found && th.toString() != null && !th.toString().isEmpty()) {
                                warnings.add(element.getName() + ": missing texture " + th.toString());
                            }
                        } else if (value instanceof MappableElement me) {
                            if (!me.isValidReference() && !me.isEmpty()) {
                                warnings.add(element.getName() + ": invalid reference " + me.getUnmappedValue() + " in " + field.getName());
                            }
                        } else if (value instanceof List<?> list) {
                            for (Object o : list) {
                                if (o instanceof MappableElement me && !me.isValidReference() && !me.isEmpty()) {
                                    warnings.add(element.getName() + ": invalid reference " + me.getUnmappedValue() + " in " + field.getName());
                                }
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }

            if (warnings.isEmpty()) return createSuccessResult("Workspace validation passed");

            Map<String, Object> result = new HashMap<>();
            result.put("warnings", warnings);
            return createSuccessResult("Workspace validation found " + warnings.size() + " warnings:\n" + objectMapper.writeValueAsString(result));
        } catch (Exception e) {
            LOG.error("Error validating workspace", e);
            return createErrorResult("Failed to validate workspace: " + e.getMessage());
        }
    }

    private List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        while (clazz != null && clazz != Object.class) {
            fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
            clazz = clazz.getSuperclass();
        }
        return fields;
    }

    /**
     * List workspace variables.
     */
    private McpTypes.ToolResult listWorkspaceVariables(MCreator mcreator) {
        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) return createErrorResult("No workspace loaded");

            List<Map<String, Object>> vars = new ArrayList<>();
            for (net.mcreator.workspace.elements.VariableElement v : workspace.getVariableElements()) {
                Map<String, Object> map = new HashMap<>();
                map.put("name", v.getName());
                map.put("type", v.getTypeString());
                map.put("scope", v.getScope() != null ? v.getScope().name() : null);
                map.put("value", v.getValue());
                vars.add(map);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("variables", vars);
            result.put("count", vars.size());
            return createSuccessResult("Found " + vars.size() + " variables:\n" + objectMapper.writeValueAsString(result));
        } catch (Exception e) {
            LOG.error("Error listing variables", e);
            return createErrorResult("Failed to list variables: " + e.getMessage());
        }
    }

    /**
     * Create a workspace variable.
     */
    private McpTypes.ToolResult createVariable(MCreator mcreator, Map<String, Object> params) {
        String variableName = (String) params.get("variableName");
        String variableType = (String) params.get("variableType");
        String scope = (String) params.get("scope");
        Object defaultValue = params.get("defaultValue");

        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) return createErrorResult("No workspace loaded");

            net.mcreator.workspace.elements.VariableElement var = new net.mcreator.workspace.elements.VariableElement(variableName);
            net.mcreator.workspace.elements.VariableType vt = new net.mcreator.workspace.elements.VariableType();
            vt.setName(variableType != null ? variableType : "Number");
            var.setType(vt);
            if (scope != null) {
                try {
                    var.setScope(net.mcreator.workspace.elements.VariableType.Scope.valueOf(scope.toUpperCase(Locale.ROOT)));
                } catch (Exception ignored) {
                    var.setScope(net.mcreator.workspace.elements.VariableType.Scope.GLOBAL_MAP);
                }
            }
            var.setValue(defaultValue);
            workspace.addVariableElement(var);
            workspace.markDirty();
            return createSuccessResult("Variable '" + variableName + "' created");
        } catch (Exception e) {
            LOG.error("Error creating variable", e);
            return createErrorResult("Failed to create variable: " + e.getMessage());
        }
    }

    /**
     * Update an existing workspace variable.
     */
    private McpTypes.ToolResult updateVariable(MCreator mcreator, Map<String, Object> params) {
        String variableName = (String) params.get("variableName");

        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) return createErrorResult("No workspace loaded");

            net.mcreator.workspace.elements.VariableElement var = workspace.getVariableElementByName(variableName);
            if (var == null) return createErrorResult("Variable not found: " + variableName);

            String variableType = (String) params.get("variableType");
            String scope = (String) params.get("scope");
            Object defaultValue = params.get("defaultValue");

            if (variableType != null) {
                net.mcreator.workspace.elements.VariableType vt = new net.mcreator.workspace.elements.VariableType();
                vt.setName(variableType);
                var.setType(vt);
            }
            if (scope != null) {
                try {
                    var.setScope(net.mcreator.workspace.elements.VariableType.Scope.valueOf(scope.toUpperCase(Locale.ROOT)));
                } catch (Exception ignored) {
                }
            }
            if (defaultValue != null) var.setValue(defaultValue);
            workspace.markDirty();
            return createSuccessResult("Variable '" + variableName + "' updated");
        } catch (Exception e) {
            LOG.error("Error updating variable", e);
            return createErrorResult("Failed to update variable: " + e.getMessage());
        }
    }

    /**
     * Delete a workspace variable.
     */
    private McpTypes.ToolResult deleteVariable(MCreator mcreator, Map<String, Object> params) {
        String variableName = (String) params.get("variableName");

        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) return createErrorResult("No workspace loaded");

            net.mcreator.workspace.elements.VariableElement var = workspace.getVariableElementByName(variableName);
            if (var == null) return createErrorResult("Variable not found: " + variableName);

            workspace.removeVariableElement(var);
            workspace.markDirty();
            return createSuccessResult("Variable '" + variableName + "' deleted");
        } catch (Exception e) {
            LOG.error("Error deleting variable", e);
            return createErrorResult("Failed to delete variable: " + e.getMessage());
        }
    }

    /**
     * Get localization strings for a language.
     */
    private McpTypes.ToolResult getLocalizations(MCreator mcreator, Map<String, Object> params) {
        String language = (String) params.get("language");

        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) return createErrorResult("No workspace loaded");

            Map<String, ? extends Map<String, String>> all = workspace.getLanguageMap();
            if (language == null || language.isEmpty()) {
                return createSuccessResult("All localizations:\n" + objectMapper.writeValueAsString(all));
            }
            Map<String, String> map = all.get(language);
            if (map == null) return createErrorResult("Language not found: " + language);
            return createSuccessResult("Localizations for " + language + ":\n" + objectMapper.writeValueAsString(map));
        } catch (Exception e) {
            LOG.error("Error getting localizations", e);
            return createErrorResult("Failed to get localizations: " + e.getMessage());
        }
    }

    /**
     * Set a localization string.
     */
    private McpTypes.ToolResult setLocalization(MCreator mcreator, Map<String, Object> params) {
        String key = (String) params.get("key");
        String language = (String) params.get("language");
        String value = (String) params.get("value");

        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) return createErrorResult("No workspace loaded");

            // MCreator's setLocalization does not take a language parameter, so update the language map directly
            Map<String, LinkedHashMap<String, String>> map = (Map<String, LinkedHashMap<String, String>>) (Map<?, ?>) workspace.getLanguageMap();
            if (!map.containsKey(language) || map.get(language) == null) {
                workspace.addLanguage(language, new LinkedHashMap<>());
            }
            LinkedHashMap<String, String> lang = map.get(language);
            if (lang == null) {
                lang = new LinkedHashMap<>();
                map.put(language, lang);
            }
            lang.put(key, value);
            workspace.markDirty();
            return createSuccessResult("Localization set: " + language + "." + key + " = " + value);
        } catch (Exception e) {
            LOG.error("Error setting localization", e);
            return createErrorResult("Failed to set localization: " + e.getMessage());
        }
    }

    /**
     * Add a new language to the workspace.
     */
    private McpTypes.ToolResult addLanguage(MCreator mcreator, Map<String, Object> params) {
        String languageCode = (String) params.get("languageCode");

        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) return createErrorResult("No workspace loaded");

            workspace.addLanguage(languageCode, new LinkedHashMap<>());
            workspace.markDirty();
            return createSuccessResult("Language '" + languageCode + "' added");
        } catch (Exception e) {
            LOG.error("Error adding language", e);
            return createErrorResult("Failed to add language: " + e.getMessage());
        }
    }

    /**
     * Build the workspace for Java Edition and return the resulting JAR path.
     */
    private McpTypes.ToolResult buildForJavaEdition(MCreator mcreator, Map<String, Object> params) {
        try {
            McpTypes.ToolResult start = executeBuildWorkspace(mcreator);
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) return createErrorResult("No workspace loaded");

            File libsDir = new File(workspace.getWorkspaceFolder(), "build/libs");
            File jar = null;
            if (libsDir.exists()) {
                File[] jars = libsDir.listFiles(f -> f.getName().endsWith(".jar"));
                if (jars != null && jars.length > 0) {
                    jar = jars[0];
                    for (File j : jars) {
                        if (j.lastModified() > jar.lastModified()) jar = j;
                    }
                }
            }

            String extra = jar != null ? "\nOutput JAR: " + jar.getAbsolutePath() + " (size: " + jar.length() + " bytes)" : "";
            return createSuccessResult("Java Edition build initiated" + extra + "\n" + start.getContent().get(0).getText());
        } catch (Exception e) {
            LOG.error("Error building for Java Edition", e);
            return createErrorResult("Failed to build for Java Edition: " + e.getMessage());
        }
    }

    /**
     * Export the generated resource pack by copying resources to the requested output path.
     */
    private McpTypes.ToolResult exportResourcePack(MCreator mcreator, Map<String, Object> params) {
        String outputPath = (String) params.get("outputPath");

        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) return createErrorResult("No workspace loaded");

            File resourcesDir = new File(workspace.getWorkspaceFolder(), "build/resources/main");
            if (!resourcesDir.exists()) return createErrorResult("No built resources found. Build the workspace first.");

            File target = new File(outputPath);
            target.mkdirs();
            copyDirectory(resourcesDir, target);
            return createSuccessResult("Resource pack exported to " + target.getAbsolutePath());
        } catch (Exception e) {
            LOG.error("Error exporting resource pack", e);
            return createErrorResult("Failed to export resource pack: " + e.getMessage());
        }
    }

    /**
     * Export behavior pack data. Not fully supported for Java Edition; copies data resources as a best effort.
     */
    private McpTypes.ToolResult exportBehaviorPack(MCreator mcreator, Map<String, Object> params) {
        String outputPath = (String) params.get("outputPath");

        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) return createErrorResult("No workspace loaded");

            File dataDir = new File(workspace.getWorkspaceFolder(), "build/resources/main/data");
            if (!dataDir.exists()) return createErrorResult("No built data resources found. Build the workspace first.");

            File target = new File(outputPath);
            target.mkdirs();
            copyDirectory(dataDir, target);
            return createSuccessResult("Behavior pack data exported to " + target.getAbsolutePath());
        } catch (Exception e) {
            LOG.error("Error exporting behavior pack", e);
            return createErrorResult("Failed to export behavior pack: " + e.getMessage());
        }
    }

    /**
     * Deploy the built mod to the Minecraft game folder.
     */
    private McpTypes.ToolResult deployToGameFolder(MCreator mcreator, Map<String, Object> params) {
        String editionType = (String) params.get("editionType");
        String gameFolderPath = (String) params.get("gameFolderPath");

        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) return createErrorResult("No workspace loaded");

            if (!"java".equalsIgnoreCase(editionType)) {
                return createErrorResult("Only java edition deployment is currently supported");
            }

            File libsDir = new File(workspace.getWorkspaceFolder(), "build/libs");
            File jar = null;
            if (libsDir.exists()) {
                File[] jars = libsDir.listFiles(f -> f.getName().endsWith(".jar"));
                if (jars != null && jars.length > 0) {
                    jar = jars[0];
                    for (File j : jars) {
                        if (j.lastModified() > jar.lastModified()) jar = j;
                    }
                }
            }

            if (jar == null || !jar.exists()) return createErrorResult("No built JAR found. Build the workspace first.");

            File modsDir = new File(gameFolderPath, "mods");
            modsDir.mkdirs();
            File target = new File(modsDir, jar.getName());
            java.nio.file.Files.copy(jar.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return createSuccessResult("Deployed " + jar.getName() + " to " + target.getAbsolutePath());
        } catch (Exception e) {
            LOG.error("Error deploying to game folder", e);
            return createErrorResult("Failed to deploy to game folder: " + e.getMessage());
        }
    }

    /**
     * Recursively copy a directory.
     */
    private void copyDirectory(File source, File target) throws IOException {
        if (source.isDirectory()) {
            target.mkdirs();
            for (File child : source.listFiles()) {
                copyDirectory(child, new File(target, child.getName()));
            }
        } else {
            java.nio.file.Files.copy(source.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // ---- JSON schema helpers ----

    Map<String, Object> objectSchema() {
        return Map.of("type", "object", "properties", Map.of());
    }

    Map<String, Object> objectSchema(Map<String, Object> properties) {
        return Map.of("type", "object", "properties", properties != null ? properties : Map.of());
    }

    Map<String, Object> objectSchema(Map<String, Object> properties, String... required) {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (required.length > 0)
            schema.put("required", Arrays.asList(required));
        return schema;
    }

    Map<String, Object> stringSchema(String description) {
        return Map.of("type", "string", "description", description);
    }

    private Map<String, Object> objectPropSchema(String description) {
        return Map.of("type", "object", "description", description);
    }

    private Map<String, Object> arraySchema(String description, String itemType) {
        return Map.of("type", "array", "description", description, "items", Map.of("type", itemType));
    }

    Map<String, Object> props(Object... keyValues) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static int toInt(Object value, int defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static boolean toBoolean(Object value, boolean defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Boolean b) return b;
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
package net.mcreator.MCreatorMCP;

import net.mcreator.MCreatorMCP.mcp.McpServer;
import net.mcreator.MCreatorMCP.mcp.McpTypes;
import net.mcreator.element.GeneratableElement;
import net.mcreator.element.ModElementType;
import net.mcreator.element.ModElementTypeLoader;
import net.mcreator.element.parts.TextureHolder;
import net.mcreator.element.types.Block;
import net.mcreator.element.types.interfaces.LimitedOptions;
import net.mcreator.element.types.interfaces.Numeric;
import net.mcreator.element.util.GEValidator;
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
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

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

        // Workspace management tools
        mcpServer.registerHandler("buildWorkspace", params -> executeBuildWorkspace(mcreator));
        mcpServer.registerHandler("getWorkspaceInfo", params -> getWorkspaceInfo(mcreator));
        mcpServer.registerHandler("regenerateCode", params -> executeRegenerateCode(mcreator));

        // Element operations
        mcpServer.registerHandler("listModElements", params -> listModElements(mcreator, params));
        mcpServer.registerHandler("createElement", params -> createElement(mcreator, params));
        mcpServer.registerHandler("deleteElement", params -> deleteElement(mcreator, params));

        // Testing tools
        mcpServer.registerHandler("runClient", params -> executeRunClient(mcreator));
        mcpServer.registerHandler("runServer", params -> executeRunServer(mcreator));

        LOG.info("Registered {} MCreator tools", 8);
    }

    /**
     * Build workspace tool
     */
    private McpTypes.ToolResult executeBuildWorkspace(MCreator mcreator) {
        LOG.info("Executing buildWorkspace tool");

        try {
            if (mcreator.getWorkspace() == null) {
                return createErrorResult("No workspace loaded");
            }

            // Execute build on EDT
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                mcreator.getActionRegistry().buildWorkspace.doAction();
            });

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
            if (mcreator.getWorkspace() == null) {
                return createErrorResult("No workspace loaded");
            }

            // Execute regenerate code on EDT
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                mcreator.getActionRegistry().regenerateCode.doAction();
            });

            return createSuccessResult("Code regeneration initiated successfully");

        } catch (Exception e) {
            LOG.error("Error regenerating code", e);
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
    private McpTypes.ToolResult createElement(MCreator mcreator, Map<String, Object> params) {
        String elementType = (String) params.get("elementType");
        String elementName = (String) params.get("elementName");

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
    private void applyGeneratableElementDefaults(GeneratableElement generatableElement, Workspace workspace,
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
                    if (current == null) {
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

        // Run MCreator's own validator to fill in numeric/option defaults and ensure valid values
        try {
            GEValidator.validateAndTryToCorrect(generatableElement, null);
        } catch (Exception e) {
            LOG.warn("GE validation failed for element {}: {}", elementName, e.getMessage());
        }

        // MCreator's UI defaults Block renderType to 10 (solid block); leaving it at 0 skips model generation
        if (generatableElement instanceof Block && ((Block) generatableElement).renderType == 0) {
            ((Block) generatableElement).renderType = 10;
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
    private String createPlaceholderTexture(Workspace workspace, TextureType textureType, String name) {
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
     * Run client tool
     */
    private McpTypes.ToolResult executeRunClient(MCreator mcreator) {
        LOG.info("Executing runClient tool");

        try {
            if (mcreator.getWorkspace() == null) {
                return createErrorResult("No workspace loaded");
            }

            // Execute run client on EDT
            javax.swing.SwingUtilities.invokeAndWait(() -> {
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

            // Execute run server on EDT
            javax.swing.SwingUtilities.invokeAndWait(() -> {
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
    private McpTypes.ToolResult createSuccessResult(String message) {
        List<McpTypes.ToolContent> content = List.of(
            new McpTypes.ToolContent("text", message)
        );
        return new McpTypes.ToolResult(content, false);
    }

    /**
     * Helper method to create error result
     */
    private McpTypes.ToolResult createErrorResult(String message) {
        List<McpTypes.ToolContent> content = List.of(
            new McpTypes.ToolContent("text", "Error: " + message)
        );
        return new McpTypes.ToolResult(content, true);
    }
}
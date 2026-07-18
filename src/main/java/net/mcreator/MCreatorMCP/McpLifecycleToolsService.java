/*
 * MCreatorMCP lifecycle, asset pipeline, and CI/automation tools.
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
import net.mcreator.element.types.LootTable;
import net.mcreator.element.types.Recipe;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.workspace.resources.TextureType;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.FolderElement;
import net.mcreator.workspace.elements.ModElement;
import net.mcreator.workspace.elements.ModElementManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import net.mcreator.plugin.Plugin;
import net.mcreator.plugin.PluginLoader;
import net.mcreator.plugin.modapis.ModAPI;
import net.mcreator.plugin.modapis.ModAPIImplementation;
import net.mcreator.plugin.modapis.ModAPIManager;

public class McpLifecycleToolsService {

	private static final Logger LOG = LogManager.getLogger("MCP-Lifecycle");

	private final MCPToolsService host;
	private final McpServer mcpServer;
	private final MCreator mcreator;
	private final ObjectMapper objectMapper;

	public McpLifecycleToolsService(MCPToolsService host, McpServer mcpServer, MCreator mcreator) {
		this.host = host;
		this.mcpServer = mcpServer;
		this.mcreator = mcreator;
		this.objectMapper = new ObjectMapper();
	}

	public void registerTools() {
		// Element lifecycle
		mcpServer.registerTool("cloneElement", "Clone an existing mod element",
				host.objectSchema(host.props(
						"sourceElementName", host.stringSchema("Name of the element to clone"),
						"newElementName", host.stringSchema("Name for the cloned element"),
						"properties", host.objectPropSchema("Optional property overrides")
				), "sourceElementName", "newElementName"),
				params -> cloneElement(params));
		mcpServer.registerTool("renameElement", "Rename an existing mod element",
				host.objectSchema(host.props(
						"elementName", host.stringSchema("Current element name"),
						"newName", host.stringSchema("New element name")
				), "elementName", "newName"),
				params -> renameElement(params));
		mcpServer.registerTool("moveElement", "Move an element to a workspace folder",
				host.objectSchema(host.props(
						"elementName", host.stringSchema("Element name"),
						"folderPath", host.stringSchema("Target folder path (e.g. \"blocks\")")
				), "elementName", "folderPath"),
				params -> moveElement(params));

		// Fine-grained element editing
		mcpServer.registerTool("editRecipe", "Edit an existing recipe element",
				host.objectSchema(host.props(
						"elementName", host.stringSchema("Name of the recipe element"),
						"properties", host.objectPropSchema("Recipe properties (recipeType, inputs, output, etc.)")
				), "elementName"),
				params -> editRecipe(params));
		mcpServer.registerTool("editAdvancement", "Edit an existing advancement element",
				host.objectSchema(host.props(
						"elementName", host.stringSchema("Name of the advancement element"),
						"properties", host.objectPropSchema("Advancement properties (displayName, description, triggerxml, rewards, etc.)")
				), "elementName"),
				params -> editAdvancement(params));
		mcpServer.registerTool("editLootTable", "Edit an existing loot table element",
				host.objectSchema(host.props(
						"elementName", host.stringSchema("Name of the loot table element"),
						"properties", host.objectPropSchema("Loot table properties (type, pools, etc.)")
				), "elementName"),
				params -> editLootTable(params));

		// Texture/model pipeline
		mcpServer.registerTool("processTexture", "Process a workspace texture (resize, pad, recolor, etc.)",
				host.objectSchema(host.props(
						"textureName", host.stringSchema("Texture name"),
						"textureType", host.stringSchema("Texture type: BLOCK, ITEM, ENTITY, etc."),
						"operations", host.objectPropSchema("Operations to apply (resize, pad, recolor)")
				), "textureName", "textureType", "operations"),
				params -> processTexture(params));
		mcpServer.registerTool("generateMcmeta", "Generate or update a .mcmeta animation file",
				host.objectSchema(host.props(
						"textureName", host.stringSchema("Texture name"),
						"textureType", host.stringSchema("Texture type"),
						"frameTime", host.stringSchema("Ticks per frame"),
						"interpolate", host.stringSchema("Enable interpolation (true/false)"),
						"width", host.stringSchema("Optional frame width"),
						"height", host.stringSchema("Optional frame height"),
						"frames", host.stringSchema("Optional frame indices (JSON array)")
				), "textureName", "textureType"),
				params -> generateMcmeta(params));
		mcpServer.registerTool("convertBlockbenchModel", "Convert a Blockbench JSON model to a Minecraft JSON model",
				host.objectSchema(host.props(
						"sourcePath", host.stringSchema("Path to the Blockbench JSON file"),
						"modelName", host.stringSchema("Output model name")
				), "sourcePath", "modelName"),
				params -> convertBlockbenchModel(params));
		mcpServer.registerTool("bindCustomModel", "Bind an imported/generated JSON/OBJ/Java model to a block or item",
				host.objectSchema(host.props(
							"elementName", host.stringSchema("Block or item element name"),
							"modelName", host.stringSchema("Model name (filename without extension)"),
							"modelType", host.stringSchema("Model type: json, obj, java"),
							"sourcePath", host.stringSchema("Optional source model file path to import"),
							"modelDefinition", host.objectPropSchema("Optional JSON model definition object (used if sourcePath is not given)"),
							"texture", host.stringSchema("Optional texture name to set as the main texture")
				), "elementName", "modelName", "modelType"),
				params -> bindCustomModel(params));

		// Element folders
		mcpServer.registerTool("listElementFolders", "List workspace folder tree for organizing elements",
				host.objectSchema(Map.of()),
				params -> listElementFolders(params));
		mcpServer.registerTool("createElementFolder", "Create a workspace folder for elements",
				host.objectSchema(host.props(
						"folderName", host.stringSchema("New folder name"),
						"parentPath", host.stringSchema("Parent folder path, empty for root")
				), "folderName"),
				params -> createElementFolder(params));
		mcpServer.registerTool("moveElementsToFolder", "Move multiple elements to a workspace folder",
				host.objectSchema(host.props(
						"elementNames", host.objectPropSchema("List of element names"),
						"folderPath", host.stringSchema("Target folder path, empty for root")
				), "elementNames", "folderPath"),
				params -> moveElementsToFolder(params));

		// Element export/import and bulk operations
		mcpServer.registerTool("exportElement", "Export a mod element as JSON to a file",
				host.objectSchema(host.props(
						"elementName", host.stringSchema("Element name"),
						"outputPath", host.stringSchema("Output JSON file path (optional)")
				), "elementName"),
				params -> exportElement(params));
		mcpServer.registerTool("importElement", "Import a mod element from a JSON file",
				host.objectSchema(host.props(
						"inputPath", host.stringSchema("Input JSON file path"),
						"newName", host.stringSchema("Optional new element name"),
						"properties", host.objectPropSchema("Optional property overrides")
				), "inputPath"),
				params -> importElement(params));
		mcpServer.registerTool("cloneElements", "Clone multiple mod elements",
				host.objectSchema(host.props(
						"mappings", host.objectPropSchema("Object mapping source element names to new names"),
						"properties", host.objectPropSchema("Optional property overrides applied to all clones")
				), "mappings"),
				params -> cloneElements(params));
		mcpServer.registerTool("renameElements", "Rename multiple mod elements",
				host.objectSchema(host.props(
						"mappings", host.objectPropSchema("Object mapping current names to new names")
				), "mappings"),
				params -> renameElements(params));
		mcpServer.registerTool("deleteElements", "Delete multiple mod elements",
				host.objectSchema(host.props(
						"elementNames", host.objectPropSchema("List of element names to delete")
				), "elementNames"),
				params -> deleteElements(params));
		mcpServer.registerTool("searchAndReplace", "Search and replace text across element properties and localizations",
				host.objectSchema(host.props(
						"search", host.stringSchema("Search string or regex"),
						"replace", host.stringSchema("Replacement string"),
						"elementNames", host.objectPropSchema("Optional list of element names to limit scope"),
						"useRegex", host.stringSchema("Treat search as regex (true/false, default false)"),
						"localizations", host.stringSchema("Also replace in localization entries (true/false, default false)")
				), "search", "replace"),
				params -> searchAndReplace(params));

		// Prompt-driven texture generation
		mcpServer.registerTool("generateTextureFromPrompt", "Generate a texture from a text prompt. Uses an external image URL if provided, otherwise a configured image-gen API, otherwise falls back to a placeholder.",
				host.objectSchema(host.props(
						"prompt", host.stringSchema("Text prompt describing the desired texture"),
						"textureName", host.stringSchema("Output texture name"),
						"textureType", host.stringSchema("Texture type: BLOCK, ITEM, ENTITY, etc."),
						"width", host.stringSchema("Width in pixels (default 64)"),
						"height", host.stringSchema("Height in pixels (default 64)"),
						"imageUrl", host.stringSchema("Direct image URL to download instead of generating (optional)"),
						"apiProvider", host.stringSchema("Image-gen API provider: url, pollinations, huggingface (default url/placeholder)"),
						"apiKey", host.stringSchema("API key for the selected provider (optional; falls back to env vars)"),
						"uvTemplatePath", host.stringSchema("Path to a UV template PNG to overlay/scale onto (optional)"),
						"seed", host.stringSchema("Seed for deterministic generation (optional)")
				), "prompt", "textureName", "textureType"),
				params -> generateTextureFromPrompt(params));

		// CI / automation
		mcpServer.registerTool("runGradleTask", "Run an arbitrary Gradle task in the workspace",
				host.objectSchema(host.props(
						"taskName", host.stringSchema("Gradle task name")
				), "taskName"),
				params -> runGradleTask(params));
		mcpServer.registerTool("executeServerCommand", "Send a command to a running Minecraft server via RCON",
				host.objectSchema(host.props(
						"command", host.stringSchema("Command to send (without leading /)"),
						"rconPassword", host.stringSchema("RCON password (default: mcp12345)"),
						"rconPort", host.stringSchema("RCON port (default: 25575)"),
						"timeoutSeconds", host.stringSchema("Connection timeout")
				), "command"),
				params -> executeServerCommand(params));
		mcpServer.registerTool("runTestScenario", "Run an automated in-game test scenario (server + commands + log check)",
				host.objectSchema(host.props(
						"scenarioName", host.stringSchema("Scenario name"),
						"commands", host.objectPropSchema("Array of commands to execute via RCON"),
						"timeoutSeconds", host.stringSchema("Timeout in seconds (default: 180)")
				), "scenarioName", "commands"),
				params -> runTestScenario(params));
		mcpServer.registerTool("verifyInWorld", "Run an in-world verification: start a server, execute place/break/inspect commands via RCON, and optionally capture a client screenshot",
				host.objectSchema(host.props(
						"commands", host.objectPropSchema("List of in-game commands to run via RCON"),
						"includeClientScreenshot", host.stringSchema("Also launch the client and capture a screenshot (true/false, default false)"),
						"timeoutSeconds", host.stringSchema("Timeout in seconds (default 180)"),
						"rconPassword", host.stringSchema("RCON password (default mcp12345)"),
						"outputPath", host.stringSchema("Client screenshot output path (default /tmp/mcp_inworld_screenshot.png)")
				), "commands"),
				params -> verifyInWorld(params));
		mcpServer.registerTool("exportModrinth", "Export the built mod as a Modrinth-compatible .mrpack",
				host.objectSchema(host.props(
						"outputPath", host.stringSchema("Output .mrpack file path"),
						"summary", host.stringSchema("Optional modpack summary")
				), "outputPath"),
				params -> exportModrinth(params));
		mcpServer.registerTool("runCIBuild", "Run a full CI build: regenerate, build, and verify server loads",
				host.objectSchema(host.props(
						"timeoutSeconds", host.stringSchema("Server verification timeout (default: 180)")
				)),
				params -> runCIBuild(params));

		// Build-system hooks
		mcpServer.registerTool("addGradleDependency", "Add or update a Gradle dependency in build.gradle",
				host.objectSchema(host.props(
						"configuration", host.stringSchema("Gradle configuration, e.g. implementation or modCompileOnly"),
						"dependency", host.stringSchema("Dependency string, e.g. com.example:lib:1.0"),
						"mcreatorDependency", host.stringSchema("Set as MCreator API dependency as well (true/false, default false)")
				), "configuration", "dependency"),
				params -> addGradleDependency(params));
		mcpServer.registerTool("editAccessTransformer", "Append or replace access transformer entries in src/main/resources/META-INF/accesstransformer.cfg",
				host.objectSchema(host.props(
						"entries", host.objectPropSchema("List of access transformer lines to add"),
						"replace", host.stringSchema("Replace the entire file (true/false, default false)")
				), "entries"),
				params -> editAccessTransformer(params));
		mcpServer.registerTool("editServerProperties", "Read or write server.properties for runServer",
				host.objectSchema(host.props(
						"properties", host.objectPropSchema("Map of server.properties keys to values"),
						"replace", host.stringSchema("Replace the entire file (true/false, default false)")
				)),
				params -> editServerProperties(params));

		// Workspace / session management
		mcpServer.registerTool("exportWorkspace", "Export the current workspace to a shareable .zip file",
				host.objectSchema(host.props(
						"outputPath", host.stringSchema("Output .zip file path"),
						"includeRunDir", host.stringSchema("Include the run directory (true/false, default: false)")
				), "outputPath"),
				params -> exportWorkspace(params));
		mcpServer.registerTool("importWorkspace", "Import a workspace from a .zip file (extract only; open it manually or restart MCreator)",
				host.objectSchema(host.props(
						"zipPath", host.stringSchema("Path to the workspace .zip"),
						"targetFolder", host.stringSchema("Optional folder to extract to")
				), "zipPath"),
				params -> importWorkspace(params));
		mcpServer.registerTool("listRecentWorkspaces", "List recently opened MCreator workspaces",
				host.objectSchema(Map.of()),
				params -> listRecentWorkspaces(params));

		// Addon / API integration helpers
		mcpServer.registerTool("listInstalledPlugins", "List installed MCreator plugins",
				host.objectSchema(Map.of()),
				params -> listInstalledPlugins(params));
		mcpServer.registerTool("listModAPIs", "List MCreator API plugins/addons available for the current generator",
				host.objectSchema(Map.of()),
				params -> listModAPIs(params));
		mcpServer.registerTool("enableModAPI", "Enable an API plugin/addon for the workspace",
				host.objectSchema(host.props(
						"apiId", host.stringSchema("API ID (e.g. geckolib)")
				), "apiId"),
				params -> enableModAPI(params));
		mcpServer.registerTool("disableModAPI", "Disable an API plugin/addon for the workspace",
				host.objectSchema(host.props(
						"apiId", host.stringSchema("API ID (e.g. geckolib)")
				), "apiId"),
				params -> disableModAPI(params));
	}

	private McpTypes.ToolResult cloneElement(Map<String, Object> params) {
		String sourceName = stringParam(params, "sourceElementName");
		String newName = stringParam(params, "newElementName");
		@SuppressWarnings("unchecked")
		Map<String, Object> properties = (Map<String, Object>) params.get("properties");

		if (sourceName == null || newName == null)
			return host.createErrorResult("sourceElementName and newElementName are required");

		try {
			Workspace workspace = mcreator.getWorkspace();
			if (workspace == null) return host.createErrorResult("No workspace loaded");

			ModElement source = workspace.getModElementByName(sourceName);
			if (source == null) return host.createErrorResult("Source element not found: " + sourceName);
			if (workspace.getModElementByName(newName) != null)
				return host.createErrorResult("Target element name already exists: " + newName);

			AtomicReference<McpTypes.ToolResult> ref = new AtomicReference<>();
			SwingUtilities.invokeAndWait(() -> {
				try {
					ModElementManager manager = workspace.getModElementManager();
					GeneratableElement sourceGe = source.getGeneratableElement();
					if (sourceGe == null) {
						ref.set(host.createErrorResult("Could not load generatable element for " + sourceName));
						return;
					}

					String json = manager.generatableElementToJSON(sourceGe);
					ModElement newMe = new ModElement(workspace, source, newName);
					GeneratableElement newGe = manager.fromJSONtoGeneratableElementOrNull(json, newMe);
					if (newGe == null) {
						ref.set(host.createErrorResult("Could not deserialize cloned element"));
						return;
					}

					if (properties != null && !properties.isEmpty()) {
						new McpElementPropertyApplier(workspace, newMe.getType().getRegistryName(), newName)
								.applyProperties(newGe, properties);
					}

					manager.storeModElement(newGe);
					workspace.addModElement(newMe);
					workspace.markDirty();
					ref.set(host.createSuccessResult("Cloned '" + sourceName + "' to '" + newName + "'"));
				} catch (Exception e) {
					LOG.error("Error cloning element", e);
					ref.set(host.createErrorResult("Failed to clone element: " + e.getMessage()));
				}
			});
			return ref.get();
		} catch (Exception e) {
			LOG.error("Error cloning element", e);
			return host.createErrorResult("Failed to clone element: " + e.getMessage());
		}
	}

	private McpTypes.ToolResult renameElement(Map<String, Object> params) {
		String elementName = stringParam(params, "elementName");
		String newName = stringParam(params, "newName");

		if (elementName == null || newName == null)
			return host.createErrorResult("elementName and newName are required");

		try {
			Workspace workspace = mcreator.getWorkspace();
			if (workspace == null) return host.createErrorResult("No workspace loaded");

			ModElement source = workspace.getModElementByName(elementName);
			if (source == null) return host.createErrorResult("Element not found: " + elementName);
			if (workspace.getModElementByName(newName) != null)
				return host.createErrorResult("Target name already exists: " + newName);

			AtomicReference<McpTypes.ToolResult> ref = new AtomicReference<>();
			SwingUtilities.invokeAndWait(() -> {
				try {
					ModElementManager manager = workspace.getModElementManager();
					GeneratableElement sourceGe = source.getGeneratableElement();
					String json = sourceGe != null ? manager.generatableElementToJSON(sourceGe) : null;
					ModElement newMe = new ModElement(workspace, source, newName);
					workspace.removeModElement(source);

					if (json != null) {
						GeneratableElement newGe = manager.fromJSONtoGeneratableElementOrNull(json, newMe);
						if (newGe != null) manager.storeModElement(newGe);
					}
					workspace.addModElement(newMe);
					workspace.markDirty();
					ref.set(host.createSuccessResult("Renamed '" + elementName + "' to '" + newName + "'"));
				} catch (Exception e) {
					LOG.error("Error renaming element", e);
					ref.set(host.createErrorResult("Failed to rename element: " + e.getMessage()));
				}
			});
			return ref.get();
		} catch (Exception e) {
			LOG.error("Error renaming element", e);
			return host.createErrorResult("Failed to rename element: " + e.getMessage());
		}
	}

	private McpTypes.ToolResult moveElement(Map<String, Object> params) {
		String elementName = stringParam(params, "elementName");
		String folderPath = stringParam(params, "folderPath");

		if (elementName == null || folderPath == null)
			return host.createErrorResult("elementName and folderPath are required");

		try {
			Workspace workspace = mcreator.getWorkspace();
			if (workspace == null) return host.createErrorResult("No workspace loaded");

			ModElement element = workspace.getModElementByName(elementName);
			if (element == null) return host.createErrorResult("Element not found: " + elementName);

			FolderElement folder;
			if (folderPath.isEmpty() || "/".equals(folderPath))
				folder = workspace.getFoldersRoot();
			else
				folder = FolderElement.findFolderByPath(workspace, folderPath);
			if (folder == null) return host.createErrorResult("Folder not found: " + folderPath);

			FolderElement finalFolder = folder;
			SwingUtilities.invokeAndWait(() -> {
				element.setParentFolder(finalFolder);
				workspace.markDirty();
			});
			return host.createSuccessResult("Moved '" + elementName + "' to folder '" + folderPath + "'");
		} catch (Exception e) {
			LOG.error("Error moving element", e);
			return host.createErrorResult("Failed to move element: " + e.getMessage());
		}
	}

	private McpTypes.ToolResult editRecipe(Map<String, Object> params) {
		String elementName = stringParam(params, "elementName");
		@SuppressWarnings("unchecked")
		Map<String, Object> properties = (Map<String, Object>) params.get("properties");
		if (elementName == null || properties == null)
			return host.createErrorResult("elementName and properties are required");

		try {
			Workspace workspace = mcreator.getWorkspace();
			if (workspace == null) return host.createErrorResult("No workspace loaded");

			ModElement me = workspace.getModElementByName(elementName);
			if (me == null) return host.createErrorResult("Element not found: " + elementName);

			AtomicReference<McpTypes.ToolResult> ref = new AtomicReference<>();
			SwingUtilities.invokeAndWait(() -> {
				try {
					GeneratableElement ge = me.getGeneratableElement();
					if (!(ge instanceof Recipe)) {
						ref.set(host.createErrorResult("Element is not a recipe: " + elementName));
						return;
					}
					new McpElementPropertyApplier(workspace, "recipe", elementName).applyProperties(ge, properties);
					workspace.getModElementManager().storeModElement(ge);
					workspace.markDirty();
					ref.set(host.createSuccessResult("Updated recipe '" + elementName + "'"));
				} catch (Exception e) {
					LOG.error("Error editing recipe", e);
					ref.set(host.createErrorResult("Failed to edit recipe: " + e.getMessage()));
				}
			});
			return ref.get();
		} catch (Exception e) {
			LOG.error("Error editing recipe", e);
			return host.createErrorResult("Failed to edit recipe: " + e.getMessage());
		}
	}

	private McpTypes.ToolResult editAdvancement(Map<String, Object> params) {
		String elementName = stringParam(params, "elementName");
		@SuppressWarnings("unchecked")
		Map<String, Object> properties = (Map<String, Object>) params.get("properties");
		if (elementName == null || properties == null)
			return host.createErrorResult("elementName and properties are required");

		try {
			Workspace workspace = mcreator.getWorkspace();
			if (workspace == null) return host.createErrorResult("No workspace loaded");

			ModElement me = workspace.getModElementByName(elementName);
			if (me == null) return host.createErrorResult("Element not found: " + elementName);

			AtomicReference<McpTypes.ToolResult> ref = new AtomicReference<>();
			SwingUtilities.invokeAndWait(() -> {
				try {
					GeneratableElement ge = me.getGeneratableElement();
					new McpElementPropertyApplier(workspace, me.getType().getRegistryName(), elementName)
							.applyProperties(ge, properties);
					workspace.getModElementManager().storeModElement(ge);
					workspace.markDirty();
					ref.set(host.createSuccessResult("Updated advancement '" + elementName + "'"));
				} catch (Exception e) {
					LOG.error("Error editing advancement", e);
					ref.set(host.createErrorResult("Failed to edit advancement: " + e.getMessage()));
				}
			});
			return ref.get();
		} catch (Exception e) {
			LOG.error("Error editing advancement", e);
			return host.createErrorResult("Failed to edit advancement: " + e.getMessage());
		}
	}

	private McpTypes.ToolResult editLootTable(Map<String, Object> params) {
		String elementName = stringParam(params, "elementName");
		@SuppressWarnings("unchecked")
		Map<String, Object> properties = (Map<String, Object>) params.get("properties");
		if (elementName == null || properties == null)
			return host.createErrorResult("elementName and properties are required");

		try {
			Workspace workspace = mcreator.getWorkspace();
			if (workspace == null) return host.createErrorResult("No workspace loaded");

			ModElement me = workspace.getModElementByName(elementName);
			if (me == null) return host.createErrorResult("Element not found: " + elementName);

			AtomicReference<McpTypes.ToolResult> ref = new AtomicReference<>();
			SwingUtilities.invokeAndWait(() -> {
				try {
					GeneratableElement ge = me.getGeneratableElement();
					if (!(ge instanceof LootTable)) {
						ref.set(host.createErrorResult("Element is not a loot table: " + elementName));
						return;
					}
					new McpElementPropertyApplier(workspace, "loottable", elementName).applyProperties(ge, properties);
					workspace.getModElementManager().storeModElement(ge);
					workspace.markDirty();
					ref.set(host.createSuccessResult("Updated loot table '" + elementName + "'"));
				} catch (Exception e) {
					LOG.error("Error editing loot table", e);
					ref.set(host.createErrorResult("Failed to edit loot table: " + e.getMessage()));
				}
			});
			return ref.get();
		} catch (Exception e) {
			LOG.error("Error editing loot table", e);
			return host.createErrorResult("Failed to edit loot table: " + e.getMessage());
		}
	}

	private McpTypes.ToolResult processTexture(Map<String, Object> params) {
		String textureName = stringParam(params, "textureName");
		String textureTypeName = stringParam(params, "textureType");
		@SuppressWarnings("unchecked")
		Map<String, Object> operations = (Map<String, Object>) params.get("operations");

		if (textureName == null || textureTypeName == null || operations == null)
			return host.createErrorResult("textureName, textureType, and operations are required");

		try {
			Workspace workspace = mcreator.getWorkspace();
			if (workspace == null) return host.createErrorResult("No workspace loaded");

			TextureType textureType;
			try {
				textureType = TextureType.valueOf(textureTypeName.trim().toUpperCase(Locale.ROOT));
			} catch (Exception e) {
				return host.createErrorResult("Unknown texture type: " + textureTypeName);
			}

			File textureFile = workspace.getFolderManager().getTextureFile(textureName.replaceAll("\\.png$", ""), textureType);
			if (!textureFile.exists()) return host.createErrorResult("Texture not found: " + textureFile.getAbsolutePath());

			BufferedImage image = ImageIO.read(textureFile);
			if (image == null) return host.createErrorResult("Could not read texture image");

			Object resize = operations.get("resize");
			if (resize instanceof Map<?, ?> rm) {
				int w = toInt(rm.get("width"), image.getWidth());
				int h = toInt(rm.get("height"), image.getHeight());
				BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
				Graphics2D g = scaled.createGraphics();
				g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
				g.drawImage(image, 0, 0, w, h, null);
				g.dispose();
				image = scaled;
			}

			Object pad = operations.get("pad");
			if (pad instanceof Map<?, ?> pm) {
				int top = toInt(pm.get("top"), 0);
				int bottom = toInt(pm.get("bottom"), 0);
				int left = toInt(pm.get("left"), 0);
				int right = toInt(pm.get("right"), 0);
				if (top > 0 || bottom > 0 || left > 0 || right > 0) {
					int w = image.getWidth() + left + right;
					int h = image.getHeight() + top + bottom;
					BufferedImage padded = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
					Graphics2D g = padded.createGraphics();
					g.setColor(new Color(0, 0, 0, 0));
					g.fillRect(0, 0, w, h);
					g.drawImage(image, left, top, null);
					g.dispose();
					image = padded;
				}
			}

			Object recolor = operations.get("recolor");
			if (recolor instanceof Map<?, ?> rm) {
				String color = stringParam(rm, "color", "#FFFFFF");
				float opacity = (float) toDouble(rm.get("opacity"), 0.5);
				Color tint = parseColor(color);
				BufferedImage tinted = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
				Graphics2D g = tinted.createGraphics();
				g.drawImage(image, 0, 0, null);
				g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.clamp(opacity, 0f, 1f)));
				g.setColor(tint);
				g.fillRect(0, 0, image.getWidth(), image.getHeight());
				g.dispose();
				image = tinted;
			}

			Object rotate = operations.get("rotate");
			if (rotate instanceof Number n) {
				double degrees = n.doubleValue();
				int w = image.getWidth();
				int h = image.getHeight();
				BufferedImage rotated = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
				Graphics2D g = rotated.createGraphics();
				g.setColor(new Color(0, 0, 0, 0));
				g.fillRect(0, 0, w, h);
				AffineTransform at = new AffineTransform();
				at.rotate(Math.toRadians(degrees), w / 2.0, h / 2.0);
				g.drawImage(image, at, null);
				g.dispose();
				image = rotated;
			}

			ImageIO.write(image, "png", textureFile);
			return host.createSuccessResult("Texture processed and saved to " + textureFile.getAbsolutePath());
		} catch (Exception e) {
			LOG.error("Error processing texture", e);
			return host.createErrorResult("Failed to process texture: " + e.getMessage());
		}
	}

	private McpTypes.ToolResult generateMcmeta(Map<String, Object> params) {
		String textureName = stringParam(params, "textureName");
		String textureTypeName = stringParam(params, "textureType");
		if (textureName == null || textureTypeName == null)
			return host.createErrorResult("textureName and textureType are required");

		try {
			Workspace workspace = mcreator.getWorkspace();
			if (workspace == null) return host.createErrorResult("No workspace loaded");

			TextureType textureType;
			try {
				textureType = TextureType.valueOf(textureTypeName.trim().toUpperCase(Locale.ROOT));
			} catch (Exception e) {
				return host.createErrorResult("Unknown texture type: " + textureTypeName);
			}

			File textureFile = workspace.getFolderManager().getTextureFile(textureName.replaceAll("\\.png$", ""), textureType);
			File mcmetaFile = new File(textureFile.getParentFile(), textureFile.getName() + ".mcmeta");

			ObjectNode root = objectMapper.createObjectNode();
			ObjectNode animation = root.putObject("animation");
			animation.put("frametime", toInt(params.get("frameTime"), 1));
			if (params.get("interpolate") != null)
				animation.put("interpolate", toBoolean(params.get("interpolate"), false));
			if (params.get("width") != null)
				animation.put("width", toInt(params.get("width"), 1));
			if (params.get("height") != null)
				animation.put("height", toInt(params.get("height"), 1));
			if (params.get("frames") != null) {
				ArrayNode frames = animation.putArray("frames");
				String framesJson = stringParam(params, "frames", "[]");
				JsonNode parsed = objectMapper.readTree(framesJson);
				if (parsed.isArray()) {
					for (JsonNode n : parsed) frames.add(n);
				}
			}

			objectMapper.writerWithDefaultPrettyPrinter().writeValue(mcmetaFile, root);
			return host.createSuccessResult("Generated " + mcmetaFile.getAbsolutePath());
		} catch (Exception e) {
			LOG.error("Error generating mcmeta", e);
			return host.createErrorResult("Failed to generate mcmeta: " + e.getMessage());
		}
	}

	private McpTypes.ToolResult convertBlockbenchModel(Map<String, Object> params) {
		String sourcePath = stringParam(params, "sourcePath");
		String modelName = stringParam(params, "modelName");
		if (sourcePath == null || modelName == null)
			return host.createErrorResult("sourcePath and modelName are required");

		try {
			Workspace workspace = mcreator.getWorkspace();
			if (workspace == null) return host.createErrorResult("No workspace loaded");

			File source = new File(sourcePath);
			if (!source.exists()) return host.createErrorResult("Source file not found: " + sourcePath);

			JsonNode root = objectMapper.readTree(source);
			JsonNode elements = root.path("model").path("elements");
			if (!elements.isArray() || elements.isEmpty()) {
				elements = root.path("elements");
			}
			if (!elements.isArray() || elements.isEmpty())
				return host.createErrorResult("Blockbench file does not contain a supported elements array");

			ObjectNode out = objectMapper.createObjectNode();
			if (root.has("textures")) {
				ObjectNode textures = out.putObject("textures");
				for (Iterator<String> it = root.path("textures").fieldNames(); it.hasNext(); ) {
					String key = it.next();
					textures.put(key, root.path("textures").path(key).asText());
				}
			}
			if (root.has("parent")) out.put("parent", root.path("parent").asText());

			ArrayNode outElements = out.putArray("elements");
			for (JsonNode el : elements) {
				ObjectNode oe = outElements.addObject();
				oe.set("from", el.path("from"));
				oe.set("to", el.path("to"));
				if (el.has("rotation")) {
					ObjectNode rot = oe.putObject("rotation");
					JsonNode r = el.path("rotation");
					rot.set("origin", r.path("origin"));
					rot.put("axis", r.path("axis").asText("y"));
					rot.put("angle", r.path("angle").asDouble(0));
					rot.put("rescale", r.path("rescale").asBoolean(false));
				}
				if (el.has("faces")) oe.set("faces", el.path("faces"));
			}

			File modelsDir = workspace.getFolderManager().getModelsDir();
			modelsDir.mkdirs();
			File outFile = new File(modelsDir, modelName.replaceAll("\\.json$", "") + ".json");
			objectMapper.writerWithDefaultPrettyPrinter().writeValue(outFile, out);
			return host.createSuccessResult("Converted Blockbench model to " + outFile.getAbsolutePath());
		} catch (Exception e) {
			LOG.error("Error converting Blockbench model", e);
			return host.createErrorResult("Failed to convert Blockbench model: " + e.getMessage());
		}
	}

	private McpTypes.ToolResult bindCustomModel(Map<String, Object> params) {
		String elementName = stringParam(params, "elementName");
		String modelName = stringParam(params, "modelName");
		String modelType = stringParam(params, "modelType", "json").toLowerCase(Locale.ROOT);
		String sourcePath = stringParam(params, "sourcePath");
		String textureName = stringParam(params, "texture");

		if (elementName == null || modelName == null)
			return host.createErrorResult("elementName and modelName are required");

		try {
			Workspace workspace = mcreator.getWorkspace();
			if (workspace == null) return host.createErrorResult("No workspace loaded");

			ModElement me = workspace.getModElementByName(elementName);
			if (me == null) return host.createErrorResult("Element not found: " + elementName);

			GeneratableElement ge = me.getGeneratableElement();
			if (ge == null) return host.createErrorResult("Element has no generatable data");

			File modelsDir = workspace.getFolderManager().getModelsDir();
			modelsDir.mkdirs();

			String extension = switch (modelType) {
				case "obj" -> ".obj";
				case "java" -> ".java";
				default -> ".json";
			};
			String safeModelName = modelName.replaceAll("\\.(json|obj|java)$", "");
			File modelFile = new File(modelsDir, safeModelName + extension);

			if (sourcePath != null && !sourcePath.isEmpty()) {
				File source = new File(sourcePath);
				if (!source.exists()) return host.createErrorResult("Source model file not found: " + sourcePath);
				Files.copy(source.toPath(), modelFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
			} else {
				Object definition = params.get("modelDefinition");
				if (definition instanceof Map<?, ?> defMap) {
					if ("json".equals(modelType) || "custom".equals(modelType)) {
						objectMapper.writerWithDefaultPrettyPrinter().writeValue(modelFile, defMap);
					} else {
						return host.createErrorResult("modelDefinition is only supported for json model type");
					}
				} else if (!modelFile.exists()) {
					return host.createErrorResult("No sourcePath or modelDefinition provided and model file does not exist");
				}
			}

			if (("json".equals(modelType) || "custom".equals(modelType)) && modelFile.exists()) {
				File texturesFile = new File(modelsDir, safeModelName + ".json.textures");
				if (!texturesFile.exists()) {
					ObjectNode texturesRoot = objectMapper.createObjectNode();
					ObjectNode mapping = texturesRoot.putObject("mappings").putObject("default");
					mapping.put("name", "default");
					ObjectNode map = mapping.putObject("map");
					if (textureName != null && !textureName.isEmpty()) {
						map.putObject("all").put("texture", textureName);
					}
					objectMapper.writerWithDefaultPrettyPrinter().writeValue(texturesFile, texturesRoot);
				}
			}

			Map<String, Object> props = new LinkedHashMap<>();
			props.put("customModelName", safeModelName);
			if (textureName != null && !textureName.isEmpty()) {
				props.put("texture", textureName);
			}

			if (ge instanceof net.mcreator.element.types.Block block) {
				int renderType = switch (modelType) {
					case "obj" -> 3;
					case "java" -> 4;
					default -> 2;
				};
				props.put("renderType", renderType);
			} else if (ge instanceof net.mcreator.element.types.Item item) {
				int renderType = switch (modelType) {
					case "obj" -> 2;
					case "java" -> 3;
					default -> 1;
				};
				props.put("renderType", renderType);
			} else {
				return host.createErrorResult("Element is not a block or item: " + elementName);
			}

			new McpElementPropertyApplier(workspace, me.getType().getRegistryName(), elementName).applyProperties(ge, props);
			workspace.getModElementManager().storeModElement(ge);
			workspace.markDirty();

			return host.createSuccessResult("Bound model '" + safeModelName + "' to '" + elementName + "' as " + modelType);
		} catch (Exception e) {
			LOG.error("Error binding custom model", e);
			return host.createErrorResult("Failed to bind custom model: " + e.getMessage());
		}
	}

	private McpTypes.ToolResult runGradleTask(Map<String, Object> params) {
		String taskName = stringParam(params, "taskName");
		if (taskName == null) return host.createErrorResult("taskName is required");
		try {
			mcreator.getGradleConsole().exec(taskName);
			return host.createSuccessResult("Started Gradle task: " + taskName);
		} catch (Exception e) {
			LOG.error("Error running Gradle task", e);
			return host.createErrorResult("Failed to run Gradle task: " + e.getMessage());
		}
	}

	private McpTypes.ToolResult executeServerCommand(Map<String, Object> params) {
		String command = stringParam(params, "command");
		String password = stringParam(params, "rconPassword", "mcp12345");
		int port = toInt(params.get("rconPort"), 25575);
		int timeout = toInt(params.get("timeoutSeconds"), 30);
		if (command == null) return host.createErrorResult("command is required");

		try (RConClient client = new RConClient("127.0.0.1", port, timeout * 1000)) {
			if (!client.login(password)) {
				return host.createErrorResult("RCON login failed");
			}
			String response = client.sendCommand(command);
			return host.createSuccessResult("Server response: " + response.trim());
		} catch (Exception e) {
			LOG.error("Error sending server command", e);
			return host.createErrorResult("Failed to send server command: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
		}
	}

	private McpTypes.ToolResult runTestScenario(Map<String, Object> params) {
		String scenarioName = stringParam(params, "scenarioName");
		Object commandsObj = params.get("commands");
		int timeout = toInt(params.get("timeoutSeconds"), 180);
		String password = stringParam(params, "rconPassword", "mcp12345");
		int port = toInt(params.get("rconPort"), 25575);

		if (scenarioName == null || commandsObj == null)
			return host.createErrorResult("scenarioName and commands are required");

		List<String> commands = new ArrayList<>();
		if (commandsObj instanceof List<?> list) {
			for (Object o : list) if (o != null) commands.add(String.valueOf(o));
		} else if (commandsObj instanceof String s) {
			commands.add(s);
		}
		if (commands.isEmpty()) return host.createErrorResult("No commands provided");

		Process serverProcess = null;
		try {
			Workspace workspace = mcreator.getWorkspace();
			if (workspace == null) return host.createErrorResult("No workspace loaded");

			File workspaceFolder = workspace.getFolderManager().getWorkspaceFolder();
			File runDir = new File(workspaceFolder, "run");
			runDir.mkdirs();
			ensureEula(runDir);
			File propsFile = new File(runDir, "server.properties");
			ensureServerRcon(propsFile, password, port);

			File gradlew = new File(workspaceFolder, "gradlew");
			if (!gradlew.exists()) return host.createErrorResult("Could not find gradlew in workspace");
			gradlew.setExecutable(true);

			File logsDir = new File(runDir, "logs");
			logsDir.mkdirs();

			// Remove stale log files so we don't mistake a previous run for this one
			File latestLog = new File(runDir, "logs/latest.log");
			if (latestLog.exists()) latestLog.delete();
			File debugLog = new File(runDir, "logs/debug.log");
			if (debugLog.exists()) debugLog.delete();

			ProcessBuilder pb = new ProcessBuilder(gradlew.getAbsolutePath(), "runServer");
			pb.directory(workspaceFolder);
			pb.redirectOutput(new File(logsDir, "gradle_runserver.log"));
			pb.redirectError(new File(logsDir, "gradle_runserver_error.log"));
			serverProcess = pb.start();
			long start = System.currentTimeMillis();
			boolean serverDone = false;
			boolean rconReady = false;
			while (System.currentTimeMillis() - start < timeout * 1000L) {
				if (latestLog.exists()) {
					List<String> lines = tailLog(latestLog, 50);
					for (String line : lines) {
						if (line.contains("Done (")) serverDone = true;
						if (line.contains("RCON running on")) rconReady = true;
					}
				}
				if (serverDone && rconReady) break;
				if (!serverProcess.isAlive()) {
					return host.createErrorResult("Server process exited before it started accepting connections");
				}
				Thread.sleep(3000);
			}
			if (!serverDone) return host.createErrorResult("Server did not start within timeout");
			if (!rconReady) return host.createErrorResult("Server started but RCON was not enabled within timeout");

			Thread.sleep(500);
			List<String> responses = new ArrayList<>();
			try (RConClient client = new RConClient("127.0.0.1", port, 15000)) {
				if (client.login(password)) {
					for (String cmd : commands) {
						responses.add(client.sendCommand(cmd).trim());
					}
					try {
						client.sendCommand("stop");
					} catch (Exception ignored) {
					}
				} else {
					return host.createErrorResult("RCON login failed after server started");
				}
			}

			// Wait for server to stop gracefully
			long stopDeadline = System.currentTimeMillis() + 30000;
			while (serverProcess.isAlive() && System.currentTimeMillis() < stopDeadline) {
				Thread.sleep(1000);
			}

			List<String> errors = new ArrayList<>();
			if (latestLog.exists()) {
				for (String line : tailLog(latestLog, 100)) {
					if (line.contains("/ERROR")) errors.add(line);
				}
			}

			Map<String, Object> result = new LinkedHashMap<>();
			result.put("scenario", scenarioName);
			result.put("serverUp", true);
			result.put("responses", responses);
			result.put("errorCount", errors.size());
			result.put("errors", errors);
			return host.createSuccessResult("Test scenario completed:\n" + objectMapper.writeValueAsString(result));
		} catch (Exception e) {
			LOG.error("Error running test scenario", e);
			return host.createErrorResult("Failed to run test scenario: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
		} finally {
			if (serverProcess != null && serverProcess.isAlive()) {
				serverProcess.destroyForcibly();
			}
		}
	}

	private void ensureEula(File runDir) throws IOException {
		File eula = new File(runDir, "eula.txt");
		try (FileWriter fw = new FileWriter(eula)) {
			fw.write("eula=true\n");
		}
	}

	private void ensureServerRcon(File propsFile, String password, int port) throws IOException {
		Properties props = new Properties();
		if (propsFile.exists()) {
			try (FileInputStream fis = new FileInputStream(propsFile)) {
				props.load(fis);
			}
		}
		props.setProperty("enable-rcon", "true");
		props.setProperty("rcon.password", password);
		props.setProperty("rcon.port", String.valueOf(port));
		props.setProperty("online-mode", "false");
		try (FileOutputStream fos = new FileOutputStream(propsFile)) {
			props.store(fos, "Updated by MCreatorMCP test scenario");
		}
	}

	private McpTypes.ToolResult exportModrinth(Map<String, Object> params) {
		String outputPath = stringParam(params, "outputPath");
		String summary = stringParam(params, "summary", "MCreatorMCP generated modpack");
		if (outputPath == null) return host.createErrorResult("outputPath is required");

		try {
			Workspace workspace = mcreator.getWorkspace();
			if (workspace == null) return host.createErrorResult("No workspace loaded");

			File libsDir = new File(workspace.getFolderManager().getWorkspaceFolder(), "build/libs");
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
			if (jar == null || !jar.exists()) return host.createErrorResult("No built JAR found. Build the workspace first.");

			File out = new File(outputPath);
			out.getParentFile().mkdirs();

			ObjectNode index = objectMapper.createObjectNode();
			index.put("formatVersion", 1);
			index.put("game", "minecraft");
			index.put("versionId", workspace.getWorkspaceSettings().getVersion());
			index.put("name", workspace.getWorkspaceSettings().getModName());
			index.put("summary", summary);
			index.putArray("files");
			ObjectNode deps = index.putObject("dependencies");
			String mcVersion = workspace.getGenerator() != null ? workspace.getGenerator().getGeneratorMinecraftVersion() : null;
			if (mcVersion != null) deps.put("minecraft", mcVersion);

			try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(out))) {
				ZipEntry indexEntry = new ZipEntry("modrinth.index.json");
				zos.putNextEntry(indexEntry);
				zos.write(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(index));
				zos.closeEntry();

				String jarEntry = "overrides/mods/" + jar.getName();
				ZipEntry modEntry = new ZipEntry(jarEntry);
				zos.putNextEntry(modEntry);
				Files.copy(jar.toPath(), zos);
				zos.closeEntry();
			}

			return host.createSuccessResult("Exported Modrinth pack to " + out.getAbsolutePath());
		} catch (Exception e) {
			LOG.error("Error exporting Modrinth pack", e);
			return host.createErrorResult("Failed to export Modrinth pack: " + e.getMessage());
		}
	}

	private McpTypes.ToolResult runCIBuild(Map<String, Object> params) {
		int timeout = toInt(params.get("timeoutSeconds"), 180);
		try {
			Workspace workspace = mcreator.getWorkspace();
			if (workspace == null) return host.createErrorResult("No workspace loaded");

			McpTypes.ToolResult regen = host.regenerateWorkspaceCode(workspace);
			if (Boolean.TRUE.equals(regen.getIsError())) return regen;

			mcreator.getGradleConsole().exec("build");

			File libsDir = new File(workspace.getFolderManager().getWorkspaceFolder(), "build/libs");
			File jar = null;
			long deadline = System.currentTimeMillis() + 300000;
			while (System.currentTimeMillis() < deadline) {
				if (libsDir.exists()) {
					File[] jars = libsDir.listFiles(f -> f.getName().endsWith(".jar"));
					if (jars != null && jars.length > 0) {
						jar = jars[0];
						for (File j : jars) {
							if (j.lastModified() > jar.lastModified()) jar = j;
						}
						break;
					}
				}
				Thread.sleep(3000);
			}
			if (jar == null) return host.createErrorResult("Build did not produce a JAR within 5 minutes");

			// Verify server loads
			return runTestScenario(Map.of(
					"scenarioName", "ci-build",
					"commands", List.of("say MCreatorMCP CI build verification started"),
					"timeoutSeconds", String.valueOf(timeout)
			));
		} catch (Exception e) {
			LOG.error("Error running CI build", e);
			return host.createErrorResult("Failed CI build: " + e.getMessage());
		}
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
			LOG.warn("Could not tail log", e);
		}
		return result;
	}

	private Color parseColor(String s) {
		s = s.trim();
		if (s.startsWith("#")) s = s.substring(1);
		if (s.length() == 6) {
			return new Color(Integer.parseInt(s, 16));
		} else if (s.length() == 8) {
			return new Color((int) Long.parseLong(s, 16), true);
		}
		return Color.WHITE;
	}

	private String stringParam(Map<?, ?> map, String key) {
		return stringParam(map, key, null);
	}

	private String stringParam(Map<?, ?> map, String key, String defaultValue) {
		Object v = map.get(key);
		return v != null ? String.valueOf(v) : defaultValue;
	}

	private int toInt(Object value, int defaultValue) {
		if (value == null) return defaultValue;
		if (value instanceof Number n) return n.intValue();
		try {
			return Integer.parseInt(String.valueOf(value));
		} catch (Exception e) {
			return defaultValue;
		}
	}

	private double toDouble(Object value, double defaultValue) {
		if (value == null) return defaultValue;
		if (value instanceof Number n) return n.doubleValue();
		try {
			return Double.parseDouble(String.valueOf(value));
		} catch (Exception e) {
			return defaultValue;
		}
	}

	private boolean toBoolean(Object value, boolean defaultValue) {
		if (value == null) return defaultValue;
		if (value instanceof Boolean b) return b;
		String s = String.valueOf(value).toLowerCase(Locale.ROOT);
		return s.equals("true") || s.equals("yes") || s.equals("1") || s.equals("on");
	}

	private String runCommand(List<String> command) throws IOException, InterruptedException {
		ProcessBuilder pb = new ProcessBuilder(command);
		pb.redirectErrorStream(true);
		Process p = pb.start();
		String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		p.waitFor();
		return output;
	}

	// ------------------------------------------------------------------
	// Workspace / session management
	// ------------------------------------------------------------------

	private McpTypes.ToolResult exportWorkspace(Map<String, Object> params) {
		Workspace ws = mcreator.getWorkspace();
		if (ws == null) return host.createErrorResult("No workspace loaded");

		String outputPath = stringParam(params, "outputPath");
		if (outputPath == null) return host.createErrorResult("outputPath is required");
		boolean includeRunDir = toBoolean(params.get("includeRunDir"), false);

		File workspaceFolder = ws.getFolderManager().getWorkspaceFolder();
		File output = new File(outputPath);
		output.getParentFile().mkdirs();

		Set<String> excludedDirs = new HashSet<>(Set.of("build", ".gradle", ".git", ".mcreator-backup"));
		if (!includeRunDir) excludedDirs.add("run");

		try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(output))) {
			Path root = workspaceFolder.toPath();
			Files.walk(root).forEach(path -> {
				try {
					String relative = root.relativize(path).toString().replace(File.separatorChar, '/');
					if (relative.isEmpty()) return;
					String[] parts = relative.split("/");
					if (parts.length > 0 && excludedDirs.contains(parts[0])) return;
					if (Files.isDirectory(path)) {
						if (!relative.endsWith("/")) relative += "/";
						zos.putNextEntry(new ZipEntry(relative));
						zos.closeEntry();
					} else {
						zos.putNextEntry(new ZipEntry(relative));
						Files.copy(path, zos);
						zos.closeEntry();
					}
				} catch (Exception e) {
					LOG.warn("Could not zip {}: {}", path, e.getMessage());
				}
			});
			return host.createSuccessResult("Exported workspace to " + output.getAbsolutePath());
		} catch (Exception e) {
			LOG.error("Failed to export workspace", e);
			return host.createErrorResult("Failed to export workspace: " + e.getMessage());
		}
	}

	private McpTypes.ToolResult importWorkspace(Map<String, Object> params) {
		String zipPath = stringParam(params, "zipPath");
		if (zipPath == null) return host.createErrorResult("zipPath is required");
		File zip = new File(zipPath);
		if (!zip.exists()) return host.createErrorResult("ZIP file not found: " + zipPath);

		String targetFolder = stringParam(params, "targetFolder");

		try (ZipFile zf = new ZipFile(zip)) {
			String mcreatorEntry = null;
			for (Enumeration<? extends ZipEntry> e = zf.entries(); e.hasMoreElements(); ) {
				ZipEntry entry = e.nextElement();
				if (!entry.isDirectory() && entry.getName().endsWith(".mcreator")) {
					mcreatorEntry = entry.getName();
					break;
				}
			}
			if (mcreatorEntry == null) return host.createErrorResult("ZIP does not contain a .mcreator workspace file");

			File target;
			if (targetFolder != null) {
				target = new File(targetFolder);
			} else {
				String top = mcreatorEntry.contains("/") ? mcreatorEntry.substring(0, mcreatorEntry.indexOf('/')) : "";
				if (top.isEmpty()) top = new File(mcreatorEntry).getName().replace(".mcreator", "");
				target = new File(zip.getParentFile(), top);
			}
			target.mkdirs();

			boolean hasCommonRoot = mcreatorEntry.contains("/");
			String commonRoot = hasCommonRoot ? mcreatorEntry.substring(0, mcreatorEntry.indexOf('/') + 1) : "";

			for (Enumeration<? extends ZipEntry> e = zf.entries(); e.hasMoreElements(); ) {
				ZipEntry entry = e.nextElement();
				String name = entry.getName();
				if (hasCommonRoot && name.startsWith(commonRoot))
					name = name.substring(commonRoot.length());
				if (name.isEmpty() || name.endsWith("/")) continue;
				File dest = new File(target, name);
				dest.getParentFile().mkdirs();
				try (InputStream is = zf.getInputStream(entry)) {
					Files.copy(is, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
				}
			}

			return host.createSuccessResult("Imported workspace to " + target.getAbsolutePath() + ". Restart MCreator to open it.");
		} catch (Exception e) {
			LOG.error("Failed to import workspace", e);
			return host.createErrorResult("Failed to import workspace: " + e.getMessage());
		}
	}

	private McpTypes.ToolResult listRecentWorkspaces(Map<String, Object> params) {
		File recent = new File(System.getProperty("user.home"), ".mcreator/recentworkspaces");
		if (!recent.exists()) return host.createSuccessResult("No recent workspaces found");
		try {
			JsonNode node = objectMapper.readTree(recent);
			return host.createSuccessResult(objectMapper.writeValueAsString(node));
		} catch (Exception e) {
			return host.createErrorResult("Could not read recent workspaces: " + e.getMessage());
		}
	}

	// ------------------------------------------------------------------
	// Addon / API integration helpers
	// ------------------------------------------------------------------

	private McpTypes.ToolResult listInstalledPlugins(Map<String, Object> params) {
		Collection<Plugin> plugins = PluginLoader.INSTANCE.getPlugins();
		List<Map<String, Object>> list = new ArrayList<>();
		for (Plugin plugin : plugins) {
			Map<String, Object> map = new LinkedHashMap<>();
			map.put("id", plugin.getID());
			map.put("builtin", plugin.isBuiltin());
			map.put("version", plugin.getPluginVersion());
			if (plugin.getInfo() != null) {
				map.put("name", plugin.getInfo().getName());
				map.put("author", plugin.getInfo().getAuthor());
				map.put("description", plugin.getInfo().getDescription());
			}
			list.add(map);
		}
		try {
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("plugins", list);
			return host.createSuccessResult(objectMapper.writeValueAsString(result));
		} catch (Exception e) {
			return host.createErrorResult("Failed to serialize plugins: " + e.getMessage());
		}
	}

	private McpTypes.ToolResult listModAPIs(Map<String, Object> params) {
		Workspace ws = mcreator.getWorkspace();
		if (ws == null) return host.createErrorResult("No workspace loaded");
		String generator = ws.getGenerator().getGeneratorName();
		List<ModAPIImplementation> apis = ModAPIManager.getModAPIsForGenerator(generator);
		List<Map<String, Object>> list = new ArrayList<>();
		for (ModAPIImplementation impl : apis) {
			Map<String, Object> map = new LinkedHashMap<>();
			map.put("id", impl.parent().id());
			map.put("name", impl.parent().name());
			map.put("gradle", impl.gradle());
			map.put("requiredWhenEnabled", impl.requiredWhenEnabled());
			map.put("versionRange", impl.versionRange());
			list.add(map);
		}
		try {
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("generator", generator);
			result.put("apis", list);
			return host.createSuccessResult(objectMapper.writeValueAsString(result));
		} catch (Exception e) {
			return host.createErrorResult("Failed to serialize APIs: " + e.getMessage());
		}
	}

	private McpTypes.ToolResult enableModAPI(Map<String, Object> params) {
		Workspace ws = mcreator.getWorkspace();
		if (ws == null) return host.createErrorResult("No workspace loaded");
		String apiId = stringParam(params, "apiId");
		if (apiId == null) return host.createErrorResult("apiId is required");

		String generator = ws.getGenerator().getGeneratorName();
		List<ModAPIImplementation> apis = ModAPIManager.getModAPIsForGenerator(generator);
		boolean found = apis.stream().anyMatch(i -> i.parent().id().equalsIgnoreCase(apiId));
		if (!found) return host.createErrorResult("API '" + apiId + "' is not available for generator " + generator);

		Set<String> deps = ws.getWorkspaceSettings().getMCreatorDependenciesRaw();
		if (deps == null) {
			deps = new HashSet<>();
			ws.getWorkspaceSettings().setMCreatorDependencies(deps);
		}
		deps.add(apiId);
		ws.markDirty();
		return host.createSuccessResult("Enabled API " + apiId + " for workspace");
	}

	private McpTypes.ToolResult disableModAPI(Map<String, Object> params) {
		Workspace ws = mcreator.getWorkspace();
		if (ws == null) return host.createErrorResult("No workspace loaded");
		String apiId = stringParam(params, "apiId");
		if (apiId == null) return host.createErrorResult("apiId is required");
		Set<String> deps = ws.getWorkspaceSettings().getMCreatorDependenciesRaw();
		if (deps != null) deps.remove(apiId);
		ws.markDirty();
		return host.createSuccessResult("Disabled API " + apiId + " for workspace");
	}

	private static class RConClient implements Closeable {
		private final Socket socket;
		private final InputStream in;
		private final OutputStream out;
		private int requestId = 1;

		RConClient(String host, int port, int timeout) throws IOException {
			socket = new Socket();
			socket.connect(new java.net.InetSocketAddress(host, port), timeout);
			socket.setSoTimeout(timeout);
			in = socket.getInputStream();
			out = socket.getOutputStream();
		}

		boolean login(String password) throws IOException {
			int id = sendPacket(3, password);
			Packet resp = readPacket();
			return resp != null && resp.requestId == id && resp.type == 2;
		}

		String sendCommand(String command) throws IOException {
			int id = sendPacket(2, command);
			Packet resp = readPacket();
			if (resp == null) return "";
			if (resp.requestId == id && resp.type == 0) {
				return resp.payload;
			}
			return "";
		}

		private int sendPacket(int type, String payload) throws IOException {
			int id = requestId++;
			byte[] payloadBytes = payload.getBytes(StandardCharsets.US_ASCII);
			ByteBuffer buf = ByteBuffer.allocate(12 + payloadBytes.length + 2).order(ByteOrder.LITTLE_ENDIAN);
			buf.putInt(payloadBytes.length + 10);
			buf.putInt(id);
			buf.putInt(type);
			buf.put(payloadBytes);
			buf.put((byte) 0);
			buf.put((byte) 0);
			out.write(buf.array());
			out.flush();
			return id;
		}

		private Packet readPacket() throws IOException {
			byte[] header = in.readNBytes(12);
			if (header.length < 12) return null;
			ByteBuffer buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
			int length = buf.getInt();
			if (length < 10) return null;
			int id = buf.getInt();
			int type = buf.getInt();
			int payloadLength = length - 10;
			byte[] payload = in.readNBytes(payloadLength);
			in.readNBytes(2); // null terminator + padding
			Packet p = new Packet();
			p.length = length;
			p.requestId = id;
			p.type = type;
			p.payload = new String(payload, StandardCharsets.US_ASCII);
			return p;
		}

		@Override
		public void close() throws IOException {
			in.close();
			out.close();
			socket.close();
		}

		private static class Packet {
			int length;
			int requestId;
			int type;
			String payload;
		}
	}

	private McpTypes.ToolResult listElementFolders(Map<String, Object> params) {
		try {
			Workspace workspace = mcreator.getWorkspace();
			if (workspace == null) return host.createErrorResult("No workspace loaded");
			FolderElement root = workspace.getFoldersRoot();
			return host.createSuccessResult(objectMapper.writeValueAsString(folderTree(root)));
		} catch (Exception e) {
			return host.createErrorResult("Failed to list folders: " + e.getMessage());
		}
	}

	private Map<String, Object> folderTree(FolderElement folder) {
		Map<String, Object> node = new LinkedHashMap<>();
		node.put("name", folder.getName());
		node.put("path", folder.getPath());
		node.put("isRoot", folder.isRoot());
		List<Map<String, Object>> children = new ArrayList<>();
		for (FolderElement child : folder.getDirectFolderChildren()) {
			children.add(folderTree(child));
		}
		node.put("children", children);
		return node;
	}

	private McpTypes.ToolResult createElementFolder(Map<String, Object> params) {
		String folderName = stringParam(params, "folderName");
		String parentPath = stringParam(params, "parentPath", "");
		if (folderName == null) return host.createErrorResult("folderName is required");
		try {
			Workspace workspace = mcreator.getWorkspace();
			if (workspace == null) return host.createErrorResult("No workspace loaded");
			FolderElement parent = parentPath.isEmpty() ? workspace.getFoldersRoot() :
					FolderElement.findFolderByPath(workspace, parentPath);
			if (parent == null) return host.createErrorResult("Parent folder not found: " + parentPath);
			FolderElement newFolder = new FolderElement(folderName, parent);
			SwingUtilities.invokeAndWait(() -> {
				parent.addChild(newFolder);
				workspace.markDirty();
			});
			return host.createSuccessResult("Created folder " + newFolder.getPath());
		} catch (Exception e) {
			return host.createErrorResult("Failed to create folder: " + e.getMessage());
		}
	}

	private McpTypes.ToolResult moveElementsToFolder(Map<String, Object> params) {
		Object elementNamesObj = params.get("elementNames");
		String folderPath = stringParam(params, "folderPath", "");
		if (elementNamesObj == null) return host.createErrorResult("elementNames is required");
		List<String> elementNames = new ArrayList<>();
		if (elementNamesObj instanceof List<?> list) {
			for (Object o : list) elementNames.add(String.valueOf(o));
		} else if (elementNamesObj instanceof String s) {
			elementNames.addAll(Arrays.asList(s.split("\\s*,\\s*")));
		}
		try {
			Workspace workspace = mcreator.getWorkspace();
			if (workspace == null) return host.createErrorResult("No workspace loaded");
			FolderElement folder = folderPath.isEmpty() ? workspace.getFoldersRoot() :
					FolderElement.findFolderByPath(workspace, folderPath);
			if (folder == null) return host.createErrorResult("Folder not found: " + folderPath);
			List<String> moved = new ArrayList<>();
			for (String elementName : elementNames) {
				ModElement element = workspace.getModElementByName(elementName);
				if (element == null) continue;
				SwingUtilities.invokeAndWait(() -> {
					element.setParentFolder(folder);
					workspace.markDirty();
				});
				moved.add(elementName);
			}
			return host.createSuccessResult("Moved " + moved.size() + " elements to " + (folderPath.isEmpty() ? "root" : folderPath));
		} catch (Exception e) {
			return host.createErrorResult("Failed to move elements: " + e.getMessage());
		}
	}

	private McpTypes.ToolResult exportElement(Map<String, Object> params) {
		String elementName = stringParam(params, "elementName");
		String outputPath = stringParam(params, "outputPath", "");
		if (elementName == null) return host.createErrorResult("elementName is required");
		try {
			Workspace workspace = mcreator.getWorkspace();
			if (workspace == null) return host.createErrorResult("No workspace loaded");
			ModElement element = workspace.getModElementByName(elementName);
			if (element == null) return host.createErrorResult("Element not found: " + elementName);
			GeneratableElement ge = element.getGeneratableElement();
			if (ge == null) return host.createErrorResult("Element has no generatable data: " + elementName);
			String json = workspace.getModElementManager().generatableElementToJSON(ge);
			File out = outputPath != null && !outputPath.isEmpty() ? new File(outputPath)
					: new File(System.getProperty("java.io.tmpdir"), elementName + ".mcelement.json");
			out.getParentFile().mkdirs();
			Files.writeString(out.toPath(), json, StandardCharsets.UTF_8);
			return host.createSuccessResult("Exported element '" + elementName + "' to " + out.getAbsolutePath());
		} catch (Exception e) {
			return host.createErrorResult("Failed to export element: " + e.getMessage());
		}
	}

	private McpTypes.ToolResult importElement(Map<String, Object> params) {
		String inputPath = stringParam(params, "inputPath");
		String newName = stringParam(params, "newName", null);
		@SuppressWarnings("unchecked")
		Map<String, Object> properties = (Map<String, Object>) params.get("properties");
		if (inputPath == null) return host.createErrorResult("inputPath is required");
		try {
			Workspace workspace = mcreator.getWorkspace();
			if (workspace == null) return host.createErrorResult("No workspace loaded");
			File in = new File(inputPath);
			if (!in.exists()) return host.createErrorResult("Input file not found: " + inputPath);
			String json = Files.readString(in.toPath(), StandardCharsets.UTF_8);
			JsonNode node = objectMapper.readTree(json);
			String typeName = null;
			if (properties != null && properties.containsKey("type")) {
				typeName = String.valueOf(properties.get("type"));
			} else if (node.has("_type") && !node.get("_type").isNull()) {
				typeName = node.get("_type").asText();
			}
			// Fall back to reading the legacy type field present in some exported JSON
			if (typeName == null && node.has("type") && !node.get("type").isNull())
				typeName = node.get("type").asText();
			if (typeName == null || typeName.isEmpty())
				return host.createErrorResult("Cannot determine element type from JSON. Provide properties.type");
			ModElementType<?> type = ModElementTypeLoader.getModElementType(typeName);
			if (type == null) return host.createErrorResult("Unknown element type: " + typeName);
			String importedName = newName != null && !newName.isEmpty() ? newName
					: (node.has("name") && !node.get("name").isNull() ? node.get("name").asText() : in.getName().replaceAll("\\.mcelement\\.json$", ""));
			if (workspace.getModElementByName(importedName) != null)
				return host.createErrorResult("Element name already exists: " + importedName);
			final String finalName = importedName;
			final String finalTypeName = typeName;
			AtomicReference<McpTypes.ToolResult> ref = new AtomicReference<>();
			SwingUtilities.invokeAndWait(() -> {
				try {
					ModElementManager manager = workspace.getModElementManager();
					ModElement me = new ModElement(workspace, finalName, type);
					GeneratableElement ge = manager.fromJSONtoGeneratableElementOrNull(json, me);
					if (ge == null) {
						ref.set(host.createErrorResult("Could not deserialize element JSON as type " + finalTypeName));
						return;
					}
					if (properties != null && !properties.isEmpty()) {
						new McpElementPropertyApplier(workspace, finalTypeName, finalName).applyProperties(ge, properties);
					}
					manager.storeModElement(ge);
					workspace.addModElement(me);
					workspace.markDirty();
					ref.set(host.createSuccessResult("Imported element '" + finalName + "' of type " + finalTypeName));
				} catch (Exception e) {
					ref.set(host.createErrorResult("Failed to import element: " + e.getMessage()));
				}
			});
			return ref.get();
		} catch (Exception e) {
			return host.createErrorResult("Failed to import element: " + e.getMessage());
		}
	}

	private McpTypes.ToolResult cloneElements(Map<String, Object> params) {
		@SuppressWarnings("unchecked")
		Map<String, Object> mappings = (Map<String, Object>) params.get("mappings");
		@SuppressWarnings("unchecked")
		Map<String, Object> properties = (Map<String, Object>) params.get("properties");
		if (mappings == null || mappings.isEmpty()) return host.createErrorResult("mappings is required");
		List<String> errors = new ArrayList<>();
		List<String> successes = new ArrayList<>();
		for (Map.Entry<String, Object> e : mappings.entrySet()) {
			String source = e.getKey();
			String target = String.valueOf(e.getValue());
			McpTypes.ToolResult r = cloneElement(new HashMap<>(Map.of("sourceElementName", source, "newElementName", target, "properties", properties != null ? properties : Map.of())));
			if (Boolean.TRUE.equals(r.getIsError())) errors.add(source + " -> " + target + ": " + r.getContent().get(0).getText());
			else successes.add(source + " -> " + target);
		}
		return host.createSuccessResult("Cloned " + successes.size() + " elements. Errors: " + errors);
	}

	private McpTypes.ToolResult renameElements(Map<String, Object> params) {
		@SuppressWarnings("unchecked")
		Map<String, Object> mappings = (Map<String, Object>) params.get("mappings");
		if (mappings == null || mappings.isEmpty()) return host.createErrorResult("mappings is required");
		List<String> errors = new ArrayList<>();
		List<String> successes = new ArrayList<>();
		for (Map.Entry<String, Object> e : mappings.entrySet()) {
			String current = e.getKey();
			String next = String.valueOf(e.getValue());
			McpTypes.ToolResult r = renameElement(new HashMap<>(Map.of("elementName", current, "newName", next)));
			if (Boolean.TRUE.equals(r.getIsError())) errors.add(current + " -> " + next + ": " + r.getContent().get(0).getText());
			else successes.add(current + " -> " + next);
		}
		return host.createSuccessResult("Renamed " + successes.size() + " elements. Errors: " + errors);
	}

	private McpTypes.ToolResult deleteElements(Map<String, Object> params) {
		Object namesObj = params.get("elementNames");
		if (namesObj == null) return host.createErrorResult("elementNames is required");
		List<String> names = new ArrayList<>();
		if (namesObj instanceof List<?> list) {
			for (Object o : list) names.add(String.valueOf(o));
		} else if (namesObj instanceof String s) {
			names.addAll(Arrays.asList(s.split("\\s*,\\s*")));
		}
		Workspace workspace = mcreator.getWorkspace();
		if (workspace == null) return host.createErrorResult("No workspace loaded");
		List<String> deleted = new ArrayList<>();
		List<String> errors = new ArrayList<>();
		for (String name : names) {
			ModElement element = workspace.getModElementByName(name);
			if (element == null) {
				errors.add(name + ": not found");
				continue;
			}
			try {
				SwingUtilities.invokeAndWait(() -> {
					workspace.getModElementManager().removeModElement(element);
					workspace.removeModElement(element);
					workspace.markDirty();
				});
				deleted.add(name);
			} catch (Exception e) {
				errors.add(name + ": " + e.getMessage());
			}
		}
		return host.createSuccessResult("Deleted " + deleted.size() + " elements. Errors: " + errors);
	}

	private McpTypes.ToolResult searchAndReplace(Map<String, Object> params) {
		String search = stringParam(params, "search");
		String replace = stringParam(params, "replace");
		if (search == null || replace == null) return host.createErrorResult("search and replace are required");
		boolean useRegex = toBoolean(params.get("useRegex"), false);
		boolean replaceLocs = toBoolean(params.get("localizations"), false);
		Object namesObj = params.get("elementNames");
		List<String> limit = new ArrayList<>();
		if (namesObj instanceof List<?> list) for (Object o : list) limit.add(String.valueOf(o));
		else if (namesObj instanceof String s) limit.addAll(Arrays.asList(s.split("\\s*,\\s*")));
		try {
			Workspace workspace = mcreator.getWorkspace();
			if (workspace == null) return host.createErrorResult("No workspace loaded");
			Pattern pattern = useRegex ? Pattern.compile(search) : Pattern.compile(Pattern.quote(search), Pattern.LITERAL);
			ModElementManager manager = workspace.getModElementManager();
			List<String> changed = new ArrayList<>();
			List<String> errors = new ArrayList<>();
			for (ModElement me : workspace.getModElements()) {
				if (!limit.isEmpty() && !limit.contains(me.getName())) continue;
				GeneratableElement ge = me.getGeneratableElement();
				if (ge == null) continue;
				try {
					String json = manager.generatableElementToJSON(ge);
					String updated = useRegex ? pattern.matcher(json).replaceAll(replace) : json.replace(search, replace);
					if (!updated.equals(json)) {
						ModElement target = new ModElement(workspace, me.getName(), me.getType());
						GeneratableElement newGe = manager.fromJSONtoGeneratableElementOrNull(updated, target);
						if (newGe == null) {
							errors.add(me.getName() + ": JSON deserialization failed after replace");
							continue;
						}
						manager.storeModElement(newGe);
						changed.add(me.getName());
					}
				} catch (Exception e) {
					errors.add(me.getName() + ": " + e.getMessage());
				}
			}
			if (replaceLocs) {
				Map<String, LinkedHashMap<String, String>> map = (Map<String, LinkedHashMap<String, String>>) (Map<?, ?>) workspace.getLanguageMap();
				for (Map.Entry<String, LinkedHashMap<String, String>> lang : map.entrySet()) {
					List<String> keys = new ArrayList<>(lang.getValue().keySet());
					for (String key : keys) {
						String value = lang.getValue().get(key);
						if (value == null) continue;
						String newValue = useRegex ? pattern.matcher(value).replaceAll(replace) : value.replace(search, replace);
						if (!newValue.equals(value)) lang.getValue().put(key, newValue);
					}
				}
				workspace.markDirty();
			}
			return host.createSuccessResult("Updated elements: " + changed + ". Errors: " + errors);
		} catch (Exception e) {
			return host.createErrorResult("Failed to search and replace: " + e.getMessage());
		}
	}

	private McpTypes.ToolResult addGradleDependency(Map<String, Object> params) {
		String configuration = stringParam(params, "configuration");
		String dependency = stringParam(params, "dependency");
		boolean mcreatorDep = toBoolean(params.get("mcreatorDependency"), false);
		if (configuration == null || dependency == null)
			return host.createErrorResult("configuration and dependency are required");
		try {
			Workspace workspace = mcreator.getWorkspace();
			if (workspace == null) return host.createErrorResult("No workspace loaded");
			File buildGradle = new File(workspace.getFolderManager().getWorkspaceFolder(), "build.gradle");
			if (!buildGradle.exists()) return host.createErrorResult("build.gradle not found");
			String content = Files.readString(buildGradle.toPath(), StandardCharsets.UTF_8);
			String line = configuration + " \"" + dependency + "\"";
			if (content.contains(line))
				return host.createSuccessResult("Dependency already present: " + line);
			// Insert before dependencies closing block or append
			if (content.contains("dependencies {")) {
				content = content.replaceFirst("(dependencies\\s*\\{)", "$1\n    " + line);
			} else {
				content += "\ndependencies {\n    " + line + "\n}\n";
			}
			Files.writeString(buildGradle.toPath(), content, StandardCharsets.UTF_8);
			if (mcreatorDep) {
				Set<String> deps = workspace.getWorkspaceSettings().getMCreatorDependenciesRaw();
				if (deps == null) {
					deps = new HashSet<>();
					workspace.getWorkspaceSettings().setMCreatorDependencies(deps);
				}
				String depId = dependency.split(":")[0];
				deps.add(depId);
				workspace.markDirty();
			}
			return host.createSuccessResult("Added dependency: " + line);
		} catch (Exception e) {
			return host.createErrorResult("Failed to add Gradle dependency: " + e.getMessage());
		}
	}

	private McpTypes.ToolResult editAccessTransformer(Map<String, Object> params) {
		Object entriesObj = params.get("entries");
		boolean replace = toBoolean(params.get("replace"), false);
		if (entriesObj == null) return host.createErrorResult("entries is required");
		List<String> entries = new ArrayList<>();
		if (entriesObj instanceof List<?> list) for (Object o : list) entries.add(String.valueOf(o));
		else if (entriesObj instanceof String s) entries.addAll(Arrays.asList(s.split("\\r?\\n")));
		try {
			Workspace workspace = mcreator.getWorkspace();
			if (workspace == null) return host.createErrorResult("No workspace loaded");
			File dir = new File(workspace.getFolderManager().getWorkspaceFolder(), "src/main/resources/META-INF");
			File at = new File(dir, "accesstransformer.cfg");
			dir.mkdirs();
			Set<String> existing = new LinkedHashSet<>();
			if (at.exists() && !replace) {
				for (String line : Files.readAllLines(at.toPath(), StandardCharsets.UTF_8)) {
					String t = line.trim();
					if (!t.isEmpty() && !t.startsWith("#")) existing.add(t);
				}
			}
			existing.addAll(entries);
			Files.writeString(at.toPath(), existing.stream().collect(Collectors.joining("\n")), StandardCharsets.UTF_8);
			return host.createSuccessResult("Wrote " + existing.size() + " access transformer entries to " + at.getAbsolutePath());
		} catch (Exception e) {
			return host.createErrorResult("Failed to edit access transformer: " + e.getMessage());
		}
	}

	private McpTypes.ToolResult editServerProperties(Map<String, Object> params) {
		@SuppressWarnings("unchecked")
		Map<String, Object> props = (Map<String, Object>) params.get("properties");
		boolean replace = toBoolean(params.get("replace"), false);
		if (props == null && !replace) return host.createErrorResult("properties is required");
		try {
			Workspace workspace = mcreator.getWorkspace();
			if (workspace == null) return host.createErrorResult("No workspace loaded");
			File serverProps = new File(workspace.getFolderManager().getWorkspaceFolder(), "run/server.properties");
			serverProps.getParentFile().mkdirs();
			Properties properties = new Properties();
			if (serverProps.exists() && !replace) {
				try (InputStream is = new FileInputStream(serverProps)) {
					properties.load(is);
				}
			}
			if (props != null) {
				for (Map.Entry<String, Object> e : props.entrySet()) {
					properties.setProperty(e.getKey(), String.valueOf(e.getValue()));
				}
			}
			try (OutputStream os = new FileOutputStream(serverProps)) {
				properties.store(os, "MCP generated server.properties");
			}
			return host.createSuccessResult("Wrote server.properties to " + serverProps.getAbsolutePath());
		} catch (Exception e) {
			return host.createErrorResult("Failed to edit server properties: " + e.getMessage());
		}
	}

	private McpTypes.ToolResult generateTextureFromPrompt(Map<String, Object> params) {
		String prompt = stringParam(params, "prompt");
		String textureName = stringParam(params, "textureName");
		String textureTypeName = stringParam(params, "textureType");
		String imageUrl = stringParam(params, "imageUrl", "");
		String apiProvider = stringParam(params, "apiProvider", "url");
		String apiKey = stringParam(params, "apiKey", "");
		String uvTemplatePath = stringParam(params, "uvTemplatePath", "");
		String seed = stringParam(params, "seed", "");
		if (prompt == null || textureName == null || textureTypeName == null)
			return host.createErrorResult("prompt, textureName, and textureType are required");
		try {
			Workspace workspace = mcreator.getWorkspace();
			if (workspace == null) return host.createErrorResult("No workspace loaded");
			TextureType textureType;
			try {
				textureType = TextureType.valueOf(textureTypeName.trim().toUpperCase(Locale.ROOT));
			} catch (Exception e) {
				return host.createErrorResult("Unknown texture type: " + textureTypeName);
			}
			int width = toInt(params.get("width"), 64);
			int height = toInt(params.get("height"), 64);

			File textureFile = workspace.getFolderManager().getTextureFile(textureName.replaceAll("\\.png$", ""), textureType);
			textureFile.getParentFile().mkdirs();

			BufferedImage image = null;
			String source = "fallback";
			if (imageUrl != null && !imageUrl.isEmpty()) {
				try {
					image = ImageIO.read(new URI(imageUrl).toURL());
					source = "imageUrl";
				} catch (Exception e) {
					return host.createErrorResult("Failed to download image from URL: " + e.getMessage());
				}
			} else if (!"url".equalsIgnoreCase(apiProvider)) {
				BufferedImage apiImage = fetchImageFromApi(prompt, apiProvider, apiKey, width, height, seed);
				if (apiImage != null) {
					image = apiImage;
					source = apiProvider;
				}
			}

			if (image == null) {
				image = generatePlaceholderTexture(prompt, width, height);
			} else if (width > 0 && height > 0 && (image.getWidth() != width || image.getHeight() != height)) {
				image = resizeAndPad(image, width, height);
			}

			if (uvTemplatePath != null && !uvTemplatePath.isEmpty()) {
				File uvFile = new File(uvTemplatePath);
				if (uvFile.exists()) {
					BufferedImage uv = ImageIO.read(uvFile);
					image = overlayUvTemplate(image, uv);
					source += "+uv";
				}
			}

			ImageIO.write(image, "png", textureFile);

			File promptFile = new File(textureFile.getParentFile(), textureFile.getName().replace(".png", "") + ".prompt.txt");
			Files.writeString(promptFile.toPath(), "source=" + source + "\nprompt=" + prompt, StandardCharsets.UTF_8);

			return host.createSuccessResult("Generated texture (source=" + source + ") at " + textureFile.getAbsolutePath());
		} catch (Exception e) {
			return host.createErrorResult("Failed to generate texture from prompt: " + e.getMessage());
		}
	}

	private BufferedImage fetchImageFromApi(String prompt, String provider, String apiKey, int width, int height, String seed) {
		try {
			if ("pollinations".equalsIgnoreCase(provider)) {
				String encoded = URLEncoder.encode(prompt, StandardCharsets.UTF_8);
				String seedParam = seed != null && !seed.isEmpty() ? "&seed=" + URLEncoder.encode(seed, StandardCharsets.UTF_8) : "";
				String url = "https://image.pollinations.ai/prompt/" + encoded + "?width=" + width + "&height=" + height + "&nologo=true" + seedParam;
				HttpURLConnection conn = (HttpURLConnection) new URI(url).toURL().openConnection();
				conn.setRequestMethod("GET");
				conn.setConnectTimeout(30000);
				conn.setReadTimeout(120000);
				if (conn.getResponseCode() == 200) {
					return ImageIO.read(conn.getInputStream());
				}
			} else if ("huggingface".equalsIgnoreCase(provider)) {
				if (apiKey == null || apiKey.isEmpty()) return null;
				String model = "black-forest-labs/FLUX.1-schnell";
				String body = objectMapper.writeValueAsString(Map.of("inputs", prompt));
				HttpURLConnection conn = (HttpURLConnection) new URI("https://api-inference.huggingface.co/models/" + model).toURL().openConnection();
				conn.setRequestMethod("POST");
				conn.setDoOutput(true);
				conn.setRequestProperty("Authorization", "Bearer " + apiKey);
				conn.setRequestProperty("Content-Type", "application/json");
				conn.setConnectTimeout(30000);
				conn.setReadTimeout(180000);
				try (OutputStream os = conn.getOutputStream()) { os.write(body.getBytes(StandardCharsets.UTF_8)); }
				if (conn.getResponseCode() == 200) return ImageIO.read(conn.getInputStream());
			}
		} catch (Exception e) {
			LOG.warn("Image API fetch failed for provider {}: {}", provider, e.getMessage());
		}
		return null;
	}

	private BufferedImage generatePlaceholderTexture(String prompt, int width, int height) {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		int hash = prompt.hashCode();
		Color bg = new Color((hash >> 16) & 0xFF, (hash >> 8) & 0xFF, hash & 0xFF);
		Color fg = new Color(255 - bg.getRed(), 255 - bg.getGreen(), 255 - bg.getBlue());

		Graphics2D g = image.createGraphics();
		g.setColor(bg);
		g.fillRect(0, 0, width, height);
		g.setColor(fg);
		for (int y = 0; y < height; y += 8) {
			for (int x = 0; x < width; x += 8) {
				if ((hash ^ (x * 31 + y)) % 7 == 0) g.fillRect(x, y, 4, 4);
			}
		}
		g.setColor(fg);
		String fontName = g.getFont().getName();
		int fontSize = Math.max(8, Math.min(width, height) / 12);
		g.setFont(new Font(fontName, Font.BOLD, fontSize));
		String shortPrompt = prompt.length() > 40 ? prompt.substring(0, 37) + "..." : prompt;
		g.drawString(shortPrompt, 4, height / 2);
		g.dispose();
		return image;
	}

	private BufferedImage resizeAndPad(BufferedImage src, int width, int height) {
		BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setColor(Color.BLACK);
		g.fillRect(0, 0, width, height);
		double scale = Math.min((double) width / src.getWidth(), (double) height / src.getHeight());
		int newW = (int) (src.getWidth() * scale);
		int newH = (int) (src.getHeight() * scale);
		int x = (width - newW) / 2;
		int y = (height - newH) / 2;
		g.drawImage(src, x, y, newW, newH, null);
		g.dispose();
		return out;
	}

	private BufferedImage overlayUvTemplate(BufferedImage image, BufferedImage uv) {
		int w = image.getWidth();
		int h = image.getHeight();
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.drawImage(image, 0, 0, null);
		g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
		g.drawImage(uv.getScaledInstance(w, h, Image.SCALE_SMOOTH), 0, 0, null);
		g.dispose();
		return out;
	}

	private McpTypes.ToolResult verifyInWorld(Map<String, Object> params) {
		Object commandsObj = params.get("commands");
		boolean includeClient = toBoolean(params.get("includeClientScreenshot"), false);
		int timeout = toInt(params.get("timeoutSeconds"), 180);
		String password = stringParam(params, "rconPassword", "mcp12345");
		int port = toInt(params.get("rconPort"), 25575);
		String outputPath = stringParam(params, "outputPath", "/tmp/mcp_inworld_screenshot.png");

		if (commandsObj == null)
			return host.createErrorResult("commands is required");
		List<String> commands = new ArrayList<>();
		if (commandsObj instanceof List<?> list) {
			for (Object o : list) commands.add(String.valueOf(o));
		}

		Process server = null;
		Process client = null;
		Process xvfb = null;
		Workspace workspace = mcreator.getWorkspace();
		if (workspace == null) return host.createErrorResult("No workspace loaded");
		File workspaceFolder = workspace.getFolderManager().getWorkspaceFolder();
		File gradlew = new File(workspaceFolder, "gradlew");
		if (!gradlew.exists()) return host.createErrorResult("Could not find gradlew");

		Map<String, Object> result = new LinkedHashMap<>();
		try {
			File logsDir = new File(workspaceFolder, "run/logs");
			logsDir.mkdirs();
			File latestLog = new File(logsDir, "latest.log");
			if (latestLog.exists()) latestLog.delete();
			File debugLog = new File(logsDir, "debug.log");
			if (debugLog.exists()) debugLog.delete();

			server = new ProcessBuilder(gradlew.getAbsolutePath(), "runServer", "--no-daemon")
					.directory(workspaceFolder)
					.redirectOutput(new File(logsDir, "server_run.log"))
					.redirectError(new File(logsDir, "server_run_error.log"))
					.start();

			long start = System.currentTimeMillis();
			boolean serverReady = false;
			while (System.currentTimeMillis() - start < timeout * 1000L) {
				if (latestLog.exists()) {
					List<String> lines = tailLog(latestLog, 20);
					for (String line : lines) {
						if (line.contains("RCON running on") && line.contains(String.valueOf(port))) {
							serverReady = true;
							break;
						}
					}
				}
				if (serverReady) break;
				if (!server.isAlive()) break;
				Thread.sleep(3000);
			}

			if (!serverReady) {
				if (server.isAlive()) server.destroyForcibly();
				return host.createErrorResult("Server did not start RCON within timeout");
			}

			List<String> responses = new ArrayList<>();
			try (RConClient rcon = new RConClient("127.0.0.1", port, 30000)) {
				if (rcon.login(password)) {
					for (String command : commands) {
						String resp = rcon.sendCommand(command);
						responses.add(command + " -> " + resp.trim());
					}
				} else {
					responses.add("RCON login failed");
				}
			}

			result.put("serverReady", true);
			result.put("commandsExecuted", commands.size());
			result.put("responses", responses);

			if (includeClient) {
				int display = 99;
				while (new File("/tmp/.X" + display + "-lock").exists() || new File("/tmp/.X11-unix/X" + display).exists()) {
					display++;
				}
				String displayStr = ":" + display;
				xvfb = new ProcessBuilder("Xvfb", displayStr, "-screen", "0", "1280x720x24", "-ac", "+extension", "GLX", "+render", "-noreset")
						.redirectOutput(new File(workspaceFolder, "run/logs/xvfb.log"))
						.redirectError(new File(workspaceFolder, "run/logs/xvfb_error.log"))
						.start();
				Thread.sleep(1000);

				File clientLog = new File(logsDir, "client_run.log");
				ProcessBuilder pb = new ProcessBuilder(gradlew.getAbsolutePath(), "runClient", "--no-daemon");
				pb.directory(workspaceFolder);
				pb.environment().put("DISPLAY", displayStr);
				client = pb.redirectOutput(clientLog)
						.redirectError(new File(logsDir, "client_run_error.log"))
						.start();

				File screenshot = new File(outputPath);
				screenshot.getParentFile().mkdirs();
				boolean clientLoaded = false;
				long clientStart = System.currentTimeMillis();
				while (System.currentTimeMillis() - clientStart < timeout * 1000L) {
					if (clientLog.exists()) {
						List<String> lines = tailLog(clientLog, 30);
						for (String line : lines) {
							if (line.contains("Created:") && line.contains("atlas")) {
								clientLoaded = true;
								break;
							}
						}
					}
					if (clientLoaded) break;
					if (!client.isAlive()) break;
					Thread.sleep(3000);
				}

				if (clientLoaded) {
					Thread.sleep(2000);
					runCommand(List.of("/usr/bin/import", "-display", displayStr, "-window", "root", screenshot.getAbsolutePath()));
					result.put("clientScreenshot", screenshot.getAbsolutePath());
					result.put("clientLoaded", true);
				} else {
					result.put("clientLoaded", false);
					result.put("clientScreenshot", null);
				}

				if (client.isAlive()) client.destroyForcibly();
				xvfb.destroyForcibly();
			}

			server.destroyForcibly();
			return host.createSuccessResult(objectMapper.writeValueAsString(result));
		} catch (Exception e) {
			if (server != null && server.isAlive()) server.destroyForcibly();
			if (client != null && client.isAlive()) client.destroyForcibly();
			if (xvfb != null && xvfb.isAlive()) xvfb.destroyForcibly();
			return host.createErrorResult("In-world verification failed: " + e.getMessage());
		}
	}
}

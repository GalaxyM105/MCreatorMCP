/*
 * MCreatorMCP publishing, log streaming, datapack/Bedrock helpers, and client verification.
 * SPDX-License-Identifier: GPL-2.0-only
 */
package net.mcreator.MCreatorMCP;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.mcreator.MCreatorMCP.mcp.McpServer;
import net.mcreator.MCreatorMCP.mcp.McpTypes;
import net.mcreator.ui.MCreator;
import net.mcreator.workspace.Workspace;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public class McpPublishingAndVerificationService {

	private static final Logger LOG = LogManager.getLogger("MCP-PubVerify");

	private final MCPToolsService host;
	private final McpServer mcpServer;
	private final MCreator mcreator;
	private final ObjectMapper objectMapper;

	public McpPublishingAndVerificationService(MCPToolsService host, McpServer mcpServer, MCreator mcreator) {
		this.host = host;
		this.mcpServer = mcpServer;
		this.mcreator = mcreator;
		this.objectMapper = new ObjectMapper();
	}

	public void registerTools() {
		// Log streaming / build progress
		mcpServer.registerTool("getLatestLog", "Tail the workspace run/logs/latest.log file",
				host.objectSchema(host.props(
						"lines", host.stringSchema("Number of lines to return (default 50)"),
						"logName", host.stringSchema("Log name: latest, debug (default latest)")
				)),
				params -> getLatestLog(params));
		mcpServer.registerTool("getGradleLog", "Tail the gradle runserver log",
				host.objectSchema(host.props(
						"lines", host.stringSchema("Number of lines to return (default 50)")
				)),
				params -> getGradleLog(params));
		mcpServer.registerTool("getBuildProgress", "Get the current Gradle console status and tail of the output",
				host.objectSchema(host.props(
						"maxChars", host.stringSchema("Maximum characters to return (default 4000)")
				)),
				params -> getBuildProgress(params));

		// Publishing
		mcpServer.registerTool("publishToModrinth", "Publish the built mod JAR to Modrinth",
				host.objectSchema(host.props(
						"apiToken", host.stringSchema("Modrinth personal access token"),
						"projectId", host.stringSchema("Modrinth project ID"),
						"versionNumber", host.stringSchema("Version number (e.g. 1.0.0)"),
						"versionName", host.stringSchema("Human-readable version name (defaults to versionNumber)"),
						"changelog", host.stringSchema("Changelog text"),
						"releaseType", host.stringSchema("release, beta, alpha (default release)"),
						"loaders", host.objectSchema(host.props(), new String[]{}),
						"gameVersions", host.objectSchema(host.props(), new String[]{}),
						"filePath", host.stringSchema("Optional path to JAR file")
				), "apiToken", "projectId", "versionNumber"),
				params -> publishToModrinth(params));
		mcpServer.registerTool("publishToCurseForge", "Publish the built mod JAR to CurseForge",
				host.objectSchema(host.props(
						"apiToken", host.stringSchema("CurseForge API token"),
						"projectId", host.stringSchema("CurseForge project ID"),
						"displayName", host.stringSchema("Display name for the file"),
						"releaseType", host.stringSchema("release, beta, alpha (default release)"),
						"changelog", host.stringSchema("Changelog text"),
						"gameVersionIds", host.objectSchema(host.props(), new String[]{}),
						"filePath", host.stringSchema("Optional path to JAR file")
				), "apiToken", "projectId", "displayName"),
				params -> publishToCurseForge(params));

		// Datapack / Bedrock file helpers
		mcpServer.registerTool("createDatapackFeature", "Write a datapack configured+placed feature JSON pair",
				host.objectSchema(host.props(
						"featureName", host.stringSchema("Feature name"),
						"featureType", host.stringSchema("Feature type: ore, simple_block, block_column (default simple_block)"),
						"target", host.stringSchema("Target block tag or block (e.g. minecraft:stone)"),
						"state", host.stringSchema("Block state to place (e.g. minecraft:diamond_ore)"),
						"count", host.stringSchema("Placement count (default 10)")
				), "featureName"),
				params -> createDatapackFeature(params));
		mcpServer.registerTool("createBedrockBehaviorJson", "Write a Bedrock behavior pack definition JSON file",
				host.objectSchema(host.props(
						"packName", host.stringSchema("Bedrock behavior pack name"),
						"elementType", host.stringSchema("Element type: item, block, entity (default item)"),
						"elementName", host.stringSchema("Element file name"),
						"properties", host.objectPropSchema("Bedrock component/properties object")
				), "packName", "elementName"),
				params -> createBedrockBehaviorJson(params));

		// Client verification
		mcpServer.registerTool("verifyClientInGame", "Launch the Minecraft client in a virtual display and capture a screenshot",
				host.objectSchema(host.props(
						"timeoutSeconds", host.stringSchema("Timeout in seconds (default 180)"),
						"outputPath", host.stringSchema("Screenshot output path (default /tmp/mcp_client_screenshot.png)"),
						"commands", host.objectSchema(host.props(), new String[]{}) // singleplayer commands not reliable headless
				)),
				params -> verifyClientInGame(params));
	}

	private McpTypes.ToolResult getLatestLog(Map<String, Object> params) {
		int lines = toInt(params.get("lines"), 50);
		String logName = stringParam(params, "logName", "latest");
		try {
			Workspace workspace = mcreator.getWorkspace();
			if (workspace == null) return host.createErrorResult("No workspace loaded");
			String fileName = "debug".equalsIgnoreCase(logName) ? "debug.log" : "latest.log";
			File logFile = new File(workspace.getFolderManager().getWorkspaceFolder(), "run/logs/" + fileName);
			if (!logFile.exists()) return host.createErrorResult("Log file not found: " + logFile);
			List<String> tail = tailLog(logFile, lines);
			return host.createSuccessResult(String.join("\n", tail));
		} catch (Exception e) {
			return host.createErrorResult("Failed to read log: " + e.getMessage());
		}
	}

	private McpTypes.ToolResult getGradleLog(Map<String, Object> params) {
		int lines = toInt(params.get("lines"), 50);
		try {
			Workspace workspace = mcreator.getWorkspace();
			if (workspace == null) return host.createErrorResult("No workspace loaded");
			File logFile = new File(workspace.getFolderManager().getWorkspaceFolder(), "run/logs/gradle_runserver.log");
			if (!logFile.exists()) logFile = new File(workspace.getFolderManager().getWorkspaceFolder(), "build/reports/problems/problems-report.html");
			if (!logFile.exists()) return host.createErrorResult("No Gradle log found");
			List<String> tail = tailLog(logFile, lines);
			return host.createSuccessResult(String.join("\n", tail));
		} catch (Exception e) {
			return host.createErrorResult("Failed to read Gradle log: " + e.getMessage());
		}
	}

	private McpTypes.ToolResult getBuildProgress(Map<String, Object> params) {
		int maxChars = toInt(params.get("maxChars"), 4000);
		try {
			String status = "unknown";
			String text = "";
			try {
				int s = mcreator.getGradleConsole().getStatus();
				status = switch (s) {
					case 0 -> "READY";
					case 1 -> "RUNNING";
					case 2 -> "ERROR";
					default -> "UNKNOWN(" + s + ")";
				};
				text = mcreator.getGradleConsole().getConsoleText();
			} catch (Exception ignored) {
			}
			String tail = text.length() > maxChars ? text.substring(text.length() - maxChars) : text;
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("status", status);
			result.put("consoleTail", tail);
			return host.createSuccessResult(objectMapper.writeValueAsString(result));
		} catch (Exception e) {
			return host.createErrorResult("Failed to get build progress: " + e.getMessage());
		}
	}

	private McpTypes.ToolResult publishToModrinth(Map<String, Object> params) {
		String token = stringParam(params, "apiToken");
		String projectId = stringParam(params, "projectId");
		String versionNumber = stringParam(params, "versionNumber");
		if (token == null || projectId == null || versionNumber == null)
			return host.createErrorResult("apiToken, projectId, and versionNumber are required");

		try {
			File jar = resolveBuiltJar(params.get("filePath"));
			Workspace workspace = mcreator.getWorkspace();
			String mcVersion = workspace.getGenerator() != null ? workspace.getGenerator().getGeneratorMinecraftVersion() : "1.21.1";

			String versionName = stringParam(params, "versionName", versionNumber);
			String releaseType = stringParam(params, "releaseType", "release").toLowerCase(Locale.ROOT);
			List<String> loaders = toStringList(params.get("loaders"), List.of("neoforge"));
			List<String> gameVersions = toStringList(params.get("gameVersions"), List.of(mcVersion));

			ObjectNode data = objectMapper.createObjectNode();
			data.put("project_id", projectId);
			data.put("version_number", versionNumber);
			data.put("name", versionName);
			data.put("changelog", stringParam(params, "changelog", ""));
			data.put("version_type", releaseType);
			ArrayNode loadersArr = data.putArray("loaders");
			loaders.forEach(loadersArr::add);
			ArrayNode gvArr = data.putArray("game_versions");
			gameVersions.forEach(gvArr::add);
			data.putArray("dependencies");
			data.put("featured", false);
			data.putArray("file_parts").add("file");

			File headerFile = writeTempFile("mcp_modrinth_header", "Authorization: " + token);
			File dataFile = writeTempFile("mcp_modrinth_data", objectMapper.writeValueAsString(data));

			List<String> command = List.of("curl", "-sS", "-w", "\\nHTTP %{http_code}\\n",
					"-H", "@" + headerFile.getAbsolutePath(),
					"-F", "data=@" + dataFile.getAbsolutePath() + ";type=application/json",
					"-F", "file=@" + jar.getAbsolutePath(),
					"https://api.modrinth.com/v2/version");
			String output = runCommand(command);
			return host.createSuccessResult("Modrinth response: " + output);
		} catch (Exception e) {
			LOG.error("Error publishing to Modrinth", e);
			return host.createErrorResult("Failed to publish to Modrinth: " + e.getMessage());
		}
	}

	private McpTypes.ToolResult publishToCurseForge(Map<String, Object> params) {
		String token = stringParam(params, "apiToken");
		String projectId = stringParam(params, "projectId");
		String displayName = stringParam(params, "displayName");
		if (token == null || projectId == null || displayName == null)
			return host.createErrorResult("apiToken, projectId, and displayName are required");

		try {
			File jar = resolveBuiltJar(params.get("filePath"));
			String releaseType = stringParam(params, "releaseType", "release").toLowerCase(Locale.ROOT);
			int releaseTypeId = switch (releaseType) {
				case "alpha" -> 1;
				case "beta" -> 2;
				default -> 3;
			};

			List<String> gameVersionIds = toStringList(params.get("gameVersionIds"), List.of());
			ObjectNode metadata = objectMapper.createObjectNode();
			metadata.put("displayName", displayName);
			metadata.put("releaseType", releaseTypeId);
			metadata.put("changelog", stringParam(params, "changelog", ""));
			metadata.put("changelogType", "markdown");
			ArrayNode gvArr = metadata.putArray("gameVersions");
			for (String id : gameVersionIds) {
				try {
					gvArr.add(Integer.parseInt(id));
				} catch (Exception ignored) {
					gvArr.add(id);
				}
			}

			File headerFile = writeTempFile("mcp_curseforge_header", "X-Api-Token: " + token);
			File metadataFile = writeTempFile("mcp_curseforge_metadata", objectMapper.writeValueAsString(metadata));

			List<String> command = List.of("curl", "-sS", "-w", "\\nHTTP %{http_code}\\n",
					"-H", "@" + headerFile.getAbsolutePath(),
					"-F", "metadata=@" + metadataFile.getAbsolutePath() + ";type=application/json",
					"-F", "file=@" + jar.getAbsolutePath(),
					"https://api.curseforge.com/v1/projects/" + projectId + "/files");
			String output = runCommand(command);
			return host.createSuccessResult("CurseForge response: " + output);
		} catch (Exception e) {
			LOG.error("Error publishing to CurseForge", e);
			return host.createErrorResult("Failed to publish to CurseForge: " + e.getMessage());
		}
	}

	private McpTypes.ToolResult createDatapackFeature(Map<String, Object> params) {
		String featureName = stringParam(params, "featureName");
		if (featureName == null) return host.createErrorResult("featureName is required");
		String safeFeatureName = featureName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
		try {
			Workspace workspace = mcreator.getWorkspace();
			if (workspace == null) return host.createErrorResult("No workspace loaded");
			String namespace = workspace.getWorkspaceSettings().getModName().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
			File dataDir = new File(workspace.getFolderManager().getWorkspaceFolder(),
					"src/main/resources/data/" + namespace + "/worldgen");

			String featureType = stringParam(params, "featureType", "simple_block").toLowerCase(Locale.ROOT);
			String state = stringParam(params, "state", "minecraft:stone");
			int count = toInt(params.get("count"), 10);

			ObjectNode configured = objectMapper.createObjectNode();
			ObjectNode config = configured.putObject("config");
			switch (featureType) {
			case "ore" -> {
				configured.put("type", "minecraft:ore");
				ArrayNode targets = config.putArray("targets");
				ObjectNode target = targets.addObject();
				target.set("target", toJsonNode(Map.of("predicate_type", "minecraft:tag_match", "tag", stringParam(params, "target", "minecraft:stone_ore_replaceables"))));
				ObjectNode stateObj = target.putObject("state");
				stateObj.put("Name", state);
				config.put("size", count);
				config.put("discard_chance_on_air_exposure", 0.0f);
			}
			case "block_column" -> {
				configured.put("type", "minecraft:block_column");
				ObjectNode layer = config.putArray("layers").addObject();
				layer.put("height", 1);
				ObjectNode provider = layer.putObject("provider");
				provider.put("type", "minecraft:simple_state_provider");
				ObjectNode pstate = provider.putObject("state");
				pstate.put("Name", state);
				config.put("direction", "up");
				config.set("allowed_placement", toJsonNode(Map.of("type", "minecraft:true")));
				config.put("prioritize_tip", false);
			}
			default -> {
				configured.put("type", "minecraft:simple_block");
				ObjectNode toPlace = config.putObject("to_place");
				toPlace.put("type", "minecraft:simple_state_provider");
				ObjectNode tpState = toPlace.putObject("state");
				tpState.put("Name", state);
			}
			}

			File configuredDir = new File(dataDir, "configured_feature");
			configuredDir.mkdirs();
			File configuredFile = new File(configuredDir, safeFeatureName + ".json");
			objectMapper.writerWithDefaultPrettyPrinter().writeValue(configuredFile, configured);

			ObjectNode placed = objectMapper.createObjectNode();
			placed.put("feature", namespace + ":" + safeFeatureName);
			ArrayNode placement = placed.putArray("placement");
			ObjectNode countPlacement = placement.addObject();
			countPlacement.put("type", "minecraft:count");
			countPlacement.putObject("count").put("type", "minecraft:uniform").put("min_inclusive", 1).put("max_inclusive", toInt(params.get("count"), 10));
			placement.addObject().put("type", "minecraft:in_square");
			ObjectNode heightRange = placement.addObject();
			heightRange.put("type", "minecraft:height_range");
			ObjectNode height = heightRange.putObject("height");
			height.put("type", "minecraft:uniform");
			height.set("min_inclusive", toJsonNode(Map.of("above_bottom", 0)));
			height.set("max_inclusive", toJsonNode(Map.of("below_top", 0)));
			placement.addObject().put("type", "minecraft:biome");

			File placedDir = new File(dataDir, "placed_feature");
			placedDir.mkdirs();
			File placedFile = new File(placedDir, safeFeatureName + ".json");
			objectMapper.writerWithDefaultPrettyPrinter().writeValue(placedFile, placed);

			return host.createSuccessResult("Wrote datapack feature files for " + featureName + " (" + configuredFile.getAbsolutePath() + ")");
		} catch (Exception e) {
			return host.createErrorResult("Failed to write datapack feature: " + e.getMessage());
		}
	}

	private McpTypes.ToolResult createBedrockBehaviorJson(Map<String, Object> params) {
		String packName = stringParam(params, "packName");
		String elementName = stringParam(params, "elementName");
		if (packName == null || elementName == null)
			return host.createErrorResult("packName and elementName are required");
		try {
			Workspace workspace = mcreator.getWorkspace();
			if (workspace == null) return host.createErrorResult("No workspace loaded");
			String type = stringParam(params, "elementType", "item").toLowerCase(Locale.ROOT);
			@SuppressWarnings("unchecked")
			Map<String, Object> properties = (Map<String, Object>) params.getOrDefault("properties", Map.of());

			File dir = new File(workspace.getFolderManager().getWorkspaceFolder(), "bedrock/behavior_packs/" + packName + "/" + (type + "s"));
			dir.mkdirs();
			ObjectNode json = objectMapper.createObjectNode();
			switch (type) {
			case "block" -> {
				json.put("format_version", "1.20.0");
				ObjectNode bp = json.putObject("minecraft:block");
				bp.set("description", toJsonNode(Map.of("identifier", elementName.toLowerCase(Locale.ROOT))));
				ObjectNode blockProps = (ObjectNode) toJsonNode(properties);
				bp.putObject("components").setAll(blockProps);
			}
			case "entity" -> {
				json.put("format_version", "1.20.0");
				ObjectNode ent = json.putObject("minecraft:entity");
				ent.set("description", toJsonNode(Map.of("identifier", elementName.toLowerCase(Locale.ROOT),
						"is_spawnable", true, "is_summonable", true)));
				ObjectNode entProps = (ObjectNode) toJsonNode(properties);
				ent.putObject("components").setAll(entProps);
			}
			default -> {
				json.put("format_version", "1.20.0");
				ObjectNode item = json.putObject("minecraft:item");
				item.set("description", toJsonNode(Map.of("identifier", elementName.toLowerCase(Locale.ROOT),
						"category", stringParam(properties, "category", "items"))));
				ObjectNode itemProps = (ObjectNode) toJsonNode(properties);
				item.putObject("components").setAll(itemProps);
			}
			}
			objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(dir, elementName.toLowerCase(Locale.ROOT) + ".json"), json);
			return host.createSuccessResult("Wrote Bedrock " + type + " JSON for " + elementName);
		} catch (Exception e) {
			return host.createErrorResult("Failed to write Bedrock behavior JSON: " + e.getMessage());
		}
	}

	private McpTypes.ToolResult verifyClientInGame(Map<String, Object> params) {
		int timeout = toInt(params.get("timeoutSeconds"), 180);
		String outputPath = stringParam(params, "outputPath", "/tmp/mcp_client_screenshot.png");
		try {
			Workspace workspace = mcreator.getWorkspace();
			if (workspace == null) return host.createErrorResult("No workspace loaded");

			File workspaceFolder = workspace.getFolderManager().getWorkspaceFolder();
			File gradlew = new File(workspaceFolder, "gradlew");
			if (!gradlew.exists()) return host.createErrorResult("Could not find gradlew");

			// Find a free display number and start Xvfb
			int display = 99;
			while (new File("/tmp/.X" + display + "-lock").exists() || new File("/tmp/.X11-unix/X" + display).exists()) {
				display++;
			}
			String displayStr = ":" + display;
			ProcessBuilder xvfbb = new ProcessBuilder("Xvfb", displayStr, "-screen", "0", "1280x720x24", "-ac", "+extension", "GLX", "+render", "-noreset");
			xvfbb.redirectOutput(new File(workspaceFolder, "run/logs/xvfb.log"));
			xvfbb.redirectError(new File(workspaceFolder, "run/logs/xvfb_error.log"));
			Process xvfb = xvfbb.start();

			Thread.sleep(1000);

			File logsDir = new File(workspaceFolder, "run/logs");
			logsDir.mkdirs();
			File clientLog = new File(logsDir, "client_run.log");
			File clientErr = new File(logsDir, "client_run_error.log");

			ProcessBuilder pb = new ProcessBuilder(gradlew.getAbsolutePath(), "runClient", "--no-daemon");
			pb.directory(workspaceFolder);
			pb.environment().put("DISPLAY", displayStr);
			pb.redirectOutput(clientLog);
			pb.redirectError(clientErr);
			Process client = pb.start();

			File latestLog = new File(workspaceFolder, "run/logs/latest.log");
			long start = System.currentTimeMillis();
			boolean loaded = false;
			while (System.currentTimeMillis() - start < timeout * 1000L) {
				if (latestLog.exists()) {
					List<String> lines = tailLog(latestLog, 30);
					for (String line : lines) {
						if (line.contains("Created:") && line.contains("atlas")) {
							loaded = true;
							break;
						}
					}
				}
				if (loaded) break;
				if (!client.isAlive()) break;
				Thread.sleep(3000);
			}
			if (!loaded) {
				client.destroyForcibly();
				xvfb.destroyForcibly();
				return host.createErrorResult("Client did not reach atlas creation within timeout");
			}

			Thread.sleep(2000);

			// Try to take a screenshot of the virtual display
			File screenshot = new File(outputPath);
			screenshot.getParentFile().mkdirs();
			List<String> shotCmd = List.of("/usr/bin/import", "-display", displayStr, "-window", "root", screenshot.getAbsolutePath());
			runCommand(shotCmd);

			// Send F2 to trigger Minecraft screenshot if window is present
			try {
				List<String> winCmd = List.of("/opt/.devin/package/custom_binaries/xdotool", "search", "--name", "Minecraft", "--sync", "--onlyvisible");
				String win = runCommand(winCmd).trim();
				if (!win.isEmpty()) {
					String[] ids = win.split("\s+");
					for (String id : ids) {
						if (!id.isEmpty()) {
							runCommand(List.of("/opt/.devin/package/custom_binaries/xdotool", "key", "--window", id, "F2"));
							break;
						}
					}
				}
			} catch (Exception ignored) {
			}

			client.destroyForcibly();
			xvfb.destroyForcibly();

			String result = screenshot.exists() ? "Screenshot saved to " + screenshot.getAbsolutePath() : "Screenshot capture may have failed";
			return host.createSuccessResult(result);
		} catch (Exception e) {
			return host.createErrorResult("Client verification failed: " + e.getMessage());
		}
	}

	// ---- helpers ----

	private File resolveBuiltJar(Object filePathObj) throws IOException {
		if (filePathObj != null && !String.valueOf(filePathObj).isEmpty()) {
			File jar = new File(String.valueOf(filePathObj));
			if (jar.exists()) return jar;
			throw new FileNotFoundException("JAR not found: " + jar);
		}
		Workspace workspace = mcreator.getWorkspace();
		if (workspace == null) throw new IOException("No workspace loaded");
		File libsDir = new File(workspace.getFolderManager().getWorkspaceFolder(), "build/libs");
		File[] jars = libsDir.listFiles(f -> f.getName().endsWith(".jar"));
		if (jars == null || jars.length == 0) throw new FileNotFoundException("No built JAR in build/libs");
		File newest = jars[0];
		for (File j : jars) if (j.lastModified() > newest.lastModified()) newest = j;
		return newest;
	}

	private File writeTempFile(String prefix, String content) throws IOException {
		File f = File.createTempFile(prefix, ".txt");
		f.deleteOnExit();
		Files.writeString(f.toPath(), content, StandardCharsets.UTF_8);
		return f;
	}

	private String runCommand(List<String> command) throws IOException, InterruptedException {
		ProcessBuilder pb = new ProcessBuilder(command);
		pb.redirectErrorStream(true);
		Process p = pb.start();
		StringBuilder out = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) out.append(line).append("\n");
		}
		p.waitFor();
		return out.toString();
	}

	private List<String> toStringList(Object value, List<String> defaultValue) {
		if (value == null) return defaultValue;
		if (value instanceof List<?> list) {
			List<String> result = new ArrayList<>();
			for (Object o : list) result.add(String.valueOf(o));
			return result;
		}
		if (value instanceof String s) {
			return new ArrayList<>(Arrays.asList(s.split("\\s*,\\s*")));
		}
		return defaultValue;
	}

	private JsonNode toJsonNode(Map<String, Object> map) {
		return objectMapper.valueToTree(map);
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
}

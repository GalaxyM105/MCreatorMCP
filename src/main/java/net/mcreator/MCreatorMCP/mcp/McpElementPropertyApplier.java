package net.mcreator.MCreatorMCP.mcp;

import net.mcreator.element.GeneratableElement;
import net.mcreator.element.parts.*;
import net.mcreator.element.parts.procedure.LogicProcedure;
import net.mcreator.element.parts.procedure.NumberProcedure;
import net.mcreator.element.parts.procedure.Procedure;
import net.mcreator.element.parts.procedure.RetvalProcedure;
import net.mcreator.element.parts.procedure.StringListProcedure;
import net.mcreator.element.types.*;
import net.mcreator.element.util.GEValidator;
import net.mcreator.generator.mapping.MappableElement;
import net.mcreator.minecraft.DataListEntry;
import net.mcreator.minecraft.DataListLoader;
import net.mcreator.plugin.Plugin;
import net.mcreator.plugin.PluginLoader;
import net.mcreator.ui.workspace.resources.TextureType;
import net.mcreator.util.yaml.YamlUtil;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.ModElement;
import net.mcreator.workspace.references.TextureReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Converts rich JSON property maps into MCreator {@link GeneratableElement} field values.
 * This is the core bridge that lets MCP clients create fully customized mod elements
 * instead of only default shells.
 */
public class McpElementPropertyApplier {

	private static final Logger LOG = LogManager.getLogger("MCP-PropertyApplier");

	private static final Map<String, Map<String, String>> ALIASES = new HashMap<>();
	private static final Map<String, Integer> BLOCK_RENDER_TYPES = new HashMap<>();
	private static final Map<String, Integer> ARMOR_PRESET_DURABILITY = new HashMap<>();
	private static final Map<String, int[]> ARMOR_PRESET_VALUES = new HashMap<>();
	private static final Map<String, Integer> ARMOR_PRESET_ENCHANTABILITY = new HashMap<>();
	private static final Map<String, Double> ARMOR_PRESET_TOUGHNESS = new HashMap<>();
	private static final Map<String, Double> ARMOR_PRESET_KNOCKBACK = new HashMap<>();

	static {
		Map<String, String> itemAliases = new HashMap<>();
		itemAliases.put("healamount", "nutritionalValue");
		itemAliases.put("foodhealamount", "nutritionalValue");
		itemAliases.put("foodsaturation", "saturation");
		itemAliases.put("canalwayseat", "isAlwaysEdible");
		itemAliases.put("wolffood", "isMeat");
		itemAliases.put("creativetab", "creativeTabs");
		itemAliases.put("attackdamage", "damageVsEntity");
		itemAliases.put("fire_resistance", "immuneToFire");
		itemAliases.put("fireresistance", "immuneToFire");
		itemAliases.put("isburnable", null); // no-op: not supported
		ALIASES.put("item", itemAliases);

		Map<String, String> blockAliases = new HashMap<>();
		blockAliases.put("soundtype", "soundOnStep");
		blockAliases.put("toolclass", "destroyTool");
		blockAliases.put("toollevel", "vanillaToolTier");
		blockAliases.put("creativetab", "creativeTabs");
		blockAliases.put("maxstacksize", "maxStackSize");
		ALIASES.put("block", blockAliases);

		Map<String, String> toolAliases = new HashMap<>();
		toolAliases.put("material", "blockDropsTier");
		toolAliases.put("toolclass", "toolType");
		toolAliases.put("attackdamage", "damageVsEntity");
		toolAliases.put("durability", "usageCount");
		toolAliases.put("repairmaterial", "repairItems");
		toolAliases.put("creativetab", "creativeTabs");
		toolAliases.put("isburnable", null);
		ALIASES.put("tool", toolAliases);

		Map<String, String> armorAliases = new HashMap<>();
		armorAliases.put("armormaterial", "armorTextureFile");
		armorAliases.put("durability", "maxDamage");
		armorAliases.put("creativetab", "creativeTabs");
		ALIASES.put("armor", armorAliases);

		Map<String, String> recipeAliases = new HashMap<>();
		recipeAliases.put("recipetype", "recipeType");
		recipeAliases.put("experience", "xpReward");
		recipeAliases.put("cooktime", "cookingTime");
		ALIASES.put("recipe", recipeAliases);

		Map<String, String> commandAliases = new HashMap<>();
		commandAliases.put("name", "commandName");
		commandAliases.put("command", "commandName");
		ALIASES.put("command", commandAliases);

		Map<String, String> potionEffectAliases = new HashMap<>();
		potionEffectAliases.put("name", "effectName");
		potionEffectAliases.put("displayname", "effectName");
		potionEffectAliases.put("sound", "onAddedSound");
		ALIASES.put("potioneffect", potionEffectAliases);

		Map<String, String> achievementAliases = new HashMap<>();
		achievementAliases.put("name", "achievementName");
		achievementAliases.put("displayname", "achievementName");
		achievementAliases.put("description", "achievementDescription");
		achievementAliases.put("icon", "achievementIcon");
		achievementAliases.put("xp", "rewardXP");
		achievementAliases.put("loot", "rewardLoot");
		achievementAliases.put("recipes", "rewardRecipes");
		achievementAliases.put("trigger", "triggerxml");
		ALIASES.put("achievement", achievementAliases);

		Map<String, String> villagerProfessionAliases = new HashMap<>();
		villagerProfessionAliases.put("name", "displayName");
		villagerProfessionAliases.put("poi", "pointOfInterest");
		villagerProfessionAliases.put("sound", "actionSound");
		ALIASES.put("villagerprofession", villagerProfessionAliases);

		Map<String, String> armorTrimAliases = new HashMap<>();
		armorTrimAliases.put("texture", "armorTextureFile");
		armorTrimAliases.put("trimTexture", "armorTextureFile");
		ALIASES.put("armortrim", armorTrimAliases);

		Map<String, String> itemExtensionAliases = new HashMap<>();
		itemExtensionAliases.put("targetItem", "item");
		itemExtensionAliases.put("target", "item");
		ALIASES.put("itemextension", itemExtensionAliases);

		Map<String, String> gameRuleAliases = new HashMap<>();
		gameRuleAliases.put("name", "displayName");
		gameRuleAliases.put("ruleType", "type");
		gameRuleAliases.put("defaultValue", "defaultValueLogic");
		ALIASES.put("gamerule", gameRuleAliases);

		Map<String, String> overlayAliases = new HashMap<>();
		overlayAliases.put("texture", "baseTexture");
		overlayAliases.put("target", "overlayTarget");
		overlayAliases.put("condition", "displayCondition");
		ALIASES.put("overlay", overlayAliases);

		Map<String, String> guiAliases = new HashMap<>();
		guiAliases.put("inventoryX", "inventoryOffsetX");
		guiAliases.put("inventoryY", "inventoryOffsetY");
		guiAliases.put("pauseGame", "doesPauseGame");
		ALIASES.put("gui", guiAliases);

		// CustomElement has no GeneratableElement fields; it uses the element name for custom code templates
		ALIASES.put("code", Map.of());
		ALIASES.put("customelement", Map.of());

		BLOCK_RENDER_TYPES.put("solid", 10);
		BLOCK_RENDER_TYPES.put("cutout", 11);
		BLOCK_RENDER_TYPES.put("translucent", 12);
		BLOCK_RENDER_TYPES.put("cutout_mipped", 14);

		ARMOR_PRESET_DURABILITY.put("leather", 80);
		ARMOR_PRESET_VALUES.put("leather", new int[] { 1, 3, 2, 1 });
		ARMOR_PRESET_ENCHANTABILITY.put("leather", 15);
		ARMOR_PRESET_TOUGHNESS.put("leather", 0.0);
		ARMOR_PRESET_KNOCKBACK.put("leather", 0.0);

		ARMOR_PRESET_DURABILITY.put("chainmail", 240);
		ARMOR_PRESET_VALUES.put("chainmail", new int[] { 1, 4, 3, 1 });
		ARMOR_PRESET_ENCHANTABILITY.put("chainmail", 12);
		ARMOR_PRESET_TOUGHNESS.put("chainmail", 0.0);
		ARMOR_PRESET_KNOCKBACK.put("chainmail", 0.0);

		ARMOR_PRESET_DURABILITY.put("iron", 240);
		ARMOR_PRESET_VALUES.put("iron", new int[] { 2, 5, 4, 1 });
		ARMOR_PRESET_ENCHANTABILITY.put("iron", 9);
		ARMOR_PRESET_TOUGHNESS.put("iron", 0.0);
		ARMOR_PRESET_KNOCKBACK.put("iron", 0.0);

		ARMOR_PRESET_DURABILITY.put("gold", 112);
		ARMOR_PRESET_VALUES.put("gold", new int[] { 2, 5, 3, 1 });
		ARMOR_PRESET_ENCHANTABILITY.put("gold", 25);
		ARMOR_PRESET_TOUGHNESS.put("gold", 0.0);
		ARMOR_PRESET_KNOCKBACK.put("gold", 0.0);

		ARMOR_PRESET_DURABILITY.put("diamond", 528);
		ARMOR_PRESET_VALUES.put("diamond", new int[] { 3, 6, 5, 2 });
		ARMOR_PRESET_ENCHANTABILITY.put("diamond", 10);
		ARMOR_PRESET_TOUGHNESS.put("diamond", 2.0);
		ARMOR_PRESET_KNOCKBACK.put("diamond", 0.0);

		ARMOR_PRESET_DURABILITY.put("netherite", 741);
		ARMOR_PRESET_VALUES.put("netherite", new int[] { 3, 6, 5, 2 });
		ARMOR_PRESET_ENCHANTABILITY.put("netherite", 15);
		ARMOR_PRESET_TOUGHNESS.put("netherite", 3.0);
		ARMOR_PRESET_KNOCKBACK.put("netherite", 0.1);
	}

	private final Workspace workspace;
	private final String elementName;
	private final String elementType;
	private Map<String, DataListEntry> blocksItemsCache;

	public McpElementPropertyApplier(Workspace workspace, String elementType, String elementName) {
		this.workspace = workspace;
		this.elementType = elementType;
		this.elementName = elementName;
	}

	private Map<String, DataListEntry> getBlocksItemsMap() {
		if (blocksItemsCache == null) {
			try {
				blocksItemsCache = DataListLoader.loadDataMap("blocksitems");
				if (blocksItemsCache == null || blocksItemsCache.isEmpty()) {
					blocksItemsCache = loadBlocksItemsFromCorePlugin();
				}
			} catch (Exception e) {
				LOG.warn("Could not load blocksitems datalist, falling back to core plugin: {}", e.getMessage());
				blocksItemsCache = loadBlocksItemsFromCorePlugin();
			}
			if (blocksItemsCache == null) {
				blocksItemsCache = Collections.emptyMap();
			}
		}
		return blocksItemsCache;
	}

	@SuppressWarnings("unchecked")
	private Map<String, DataListEntry> loadBlocksItemsFromCorePlugin() {
		try {
			if (PluginLoader.INSTANCE == null) return null;
			for (Plugin plugin : PluginLoader.INSTANCE.getPlugins()) {
				if ("mcreator-core".equals(plugin.getID()) && plugin.getFile() != null && plugin.getFile().exists()) {
					File file = plugin.getFile();
					if (file.getName().toLowerCase(Locale.ROOT).endsWith(".zip")) {
						try (ZipFile zip = new ZipFile(file)) {
							ZipEntry entry = zip.getEntry("datalists/blocksitems.yaml");
							if (entry != null) {
								try (InputStream is = zip.getInputStream(entry)) {
									String yaml = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
									org.snakeyaml.engine.v2.api.Load load = new org.snakeyaml.engine.v2.api.Load(YamlUtil.getSimpleLoadSettings());
									Object parsed = load.loadFromString(yaml);
									Map<String, DataListEntry> map = new LinkedHashMap<>();
									if (parsed instanceof List<?> list) {
										for (Object o : list) {
											if (o instanceof Map<?, ?> m) {
												for (Object keyObj : m.keySet()) {
													String key = String.valueOf(keyObj);
													map.put(key, createDataListEntry(key));
												}
											}
										}
									}
									LOG.info("Loaded {} blocksitems entries from mcreator-core plugin", map.size());
									return map;
								}
							}
						}
					} else {
						File datalist = new File(file, "datalists/blocksitems.yaml");
						if (datalist.exists()) {
							String yaml = Files.readString(datalist.toPath());
							org.snakeyaml.engine.v2.api.Load load = new org.snakeyaml.engine.v2.api.Load(YamlUtil.getSimpleLoadSettings());
							Object parsed = load.loadFromString(yaml);
							Map<String, DataListEntry> map = new LinkedHashMap<>();
							if (parsed instanceof List<?> list) {
								for (Object o : list) {
									if (o instanceof Map<?, ?> m) {
										for (Object keyObj : m.keySet()) {
											String key = String.valueOf(keyObj);
											map.put(key, createDataListEntry(key));
										}
									}
								}
							}
							LOG.info("Loaded {} blocksitems entries from mcreator-core plugin", map.size());
							return map;
						}
					}
				}
			}
		} catch (Exception e) {
			LOG.warn("Failed to load blocksitems from core plugin: {}", e.getMessage());
		}
		return null;
	}

	private DataListEntry createDataListEntry(String name) {
		try {
			java.lang.reflect.Constructor<DataListEntry> ctor = DataListEntry.class.getDeclaredConstructor(String.class);
			ctor.setAccessible(true);
			return ctor.newInstance(name);
		} catch (Exception e) {
			LOG.warn("Could not create DataListEntry for {}: {}", name, e.getMessage());
			return null;
		}
	}

	/**
	 * Apply all properties from the MCP JSON object to the given GeneratableElement.
	 */
	public void applyProperties(GeneratableElement ge, Map<String, Object> properties) {
		if (properties == null || properties.isEmpty())
			return;

		preProcessSpecials(ge, properties);

		for (Map.Entry<String, Object> entry : properties.entrySet()) {
			String key = entry.getKey();
			Object value = entry.getValue();
			try {
				if (applySpecialProperty(ge, key, value))
					continue;

				String fieldName = resolveAlias(elementType, key);
				if (fieldName == null)
					fieldName = key;

				Field field = findField(ge.getClass(), fieldName);
				if (field == null) {
					LOG.warn("Unknown property '{}' for element type '{}'", key, elementType);
					continue;
				}

				Object converted = convertValue(field, value);
				if (converted != null) {
					field.setAccessible(true);
					field.set(ge, converted);
				}
			} catch (Exception e) {
				LOG.warn("Failed to apply property {}={}: {}", key, value, e.getMessage());
			}
		}

		postProcess(ge);

		try {
			GEValidator.validateAndTryToCorrect(ge, null);
		} catch (Exception e) {
			LOG.warn("GE validation failed after applying properties: {}", e.getMessage());
		}

		applyPostValidationDefaults(ge, properties);
	}

	private void preProcessSpecials(GeneratableElement ge, Map<String, Object> properties) {
		if (ge instanceof Armor armor) {
			Object material = properties.get("armorMaterial");
			if (material instanceof String mat) {
				applyArmorPreset(armor, mat.toLowerCase(Locale.ROOT), properties);
			}
		}

		if (ge instanceof Recipe recipe) {
			Object recipeType = properties.remove("recipeType");
			if (recipeType != null) {
				normalizeAndSetRecipeType(recipe, recipeType);
			}
			Object inputs = properties.remove("inputs");
			Object output = properties.remove("output");
			Object outputCount = properties.remove("outputCount");
			if (output instanceof String && outputCount != null) {
				Map<String, Object> wrapped = new HashMap<>();
				wrapped.put("item", output);
				wrapped.put("count", outputCount);
				output = wrapped;
			}
			if (inputs != null || output != null) {
				applyRecipeInputsAndOutputs(recipe, inputs, output);
			}
		}
	}

	private void postProcess(GeneratableElement ge) {
		if (ge instanceof Block block) {
			if (block.renderType == 0) {
				block.renderType = block.hasTransparency ? 12 : 10;
			}
		}

		if (ge instanceof Fluid fluid) {
			if (fluid.type == null || fluid.type.isEmpty())
				fluid.type = "water";
		}

		if (ge instanceof Achievement achievement) {
			if (achievement.triggerxml == null || achievement.triggerxml.isEmpty()) {
				achievement.triggerxml = "<xml xmlns=\"https://developers.google.com/blockly/xml\"><block type=\"advancement_trigger\" deletable=\"false\" x=\"40\" y=\"80\"><next><shadow type=\"custom_trigger\"></shadow></next></block></xml>";
			}
		}

		if (ge instanceof Projectile projectile) {
			if (projectile.entityModel == null || projectile.entityModel.isEmpty())
				projectile.entityModel = "Default";
		}

		if (ge instanceof Plant plant) {
			if (plant.customModelName == null || plant.customModelName.isEmpty())
				plant.customModelName = "Cross model";
		}

		if (ge instanceof Feature feature) {
			if (feature.generationStep == null || "RAW_GENERATION".equals(feature.generationStep.getUnmappedValue()))
				feature.generationStep = new GenerationStep(workspace, "UNDERGROUND_ORES");
			if (feature.generateCondition == null)
				feature.generateCondition = new Procedure(null);
			if (feature.featurexml == null || feature.featurexml.isEmpty()) {
				feature.featurexml = "<xml xmlns=\"https://developers.google.com/blockly/xml\"><block type=\"feature_container\" deletable=\"false\" x=\"40\" y=\"40\"><value name=\"feature\"><block type=\"feature_simple_block\"><value name=\"block\"><block type=\"blockstate_selector\"><mutation inputs=\"0\"/><field name=\"block\">minecraft:stone</field></block></value><field name=\"schedule_tick\">FALSE</field></block></value></block></xml>";
			}
		}

		if (ge instanceof Structure structure) {
			if (structure.generationStep == null || "RAW_GENERATION".equals(structure.generationStep.getUnmappedValue()))
				structure.generationStep = new GenerationStep(workspace, "SURFACE_STRUCTURES");
			if (structure.restrictionBiomes == null)
				structure.restrictionBiomes = new ArrayList<>();
			if (structure.ignoredBlocks == null)
				structure.ignoredBlocks = new ArrayList<>();
			if (structure.terrainAdaptation == null)
				structure.terrainAdaptation = "beard_box";
			if (structure.surfaceDetectionType == null)
				structure.surfaceDetectionType = "WORLD_SURFACE_WG";
			if (structure.startHeightProviderType == null)
				structure.startHeightProviderType = "uniform";
			if (structure.projection == null)
				structure.projection = "rigid";
			if (structure.structure == null)
				structure.structure = "";
		}

		if (ge instanceof Command command) {
			if (command.commandName == null || command.commandName.isEmpty())
				command.commandName = elementName.toLowerCase(Locale.ROOT);
			else
				command.commandName = command.commandName.toLowerCase(Locale.ROOT);
			if (command.type == null || command.type.isEmpty())
				command.type = "STANDARD";
			if (command.permissionLevel == null || command.permissionLevel.isEmpty())
				command.permissionLevel = "4";
			if (command.argsxml == null || command.argsxml.isEmpty())
				command.argsxml = "<xml xmlns=\"https://developers.google.com/blockly/xml\"><block type=\"args_start\" deletable=\"false\" x=\"40\" y=\"40\"><next><block type=\"call_procedure\"><field name=\"procedure\"></field></block></next></block></xml>";
		}

		if (ge instanceof Painting painting) {
			if (painting.title == null || painting.title.isEmpty())
				painting.title = elementName;
			if (painting.author == null || painting.author.isEmpty())
				painting.author = workspace.getWorkspaceSettings().getAuthor();
			if (painting.width == 0)
				painting.width = 16;
			if (painting.height == 0)
				painting.height = 16;
		}

		if (ge instanceof BannerPattern banner) {
			if (banner.name == null || banner.name.isEmpty())
				banner.name = elementName.toLowerCase(Locale.ROOT);
		}

		if (ge instanceof DamageType damage) {
			if (damage.scaling == null || damage.scaling.isEmpty())
				damage.scaling = "never";
			if (damage.effects == null)
				damage.effects = "";
		}

		if (ge instanceof GameRule gameRule) {
			if (gameRule.type == null || gameRule.type.isEmpty())
				gameRule.type = "Boolean";
			if (gameRule.category == null)
				gameRule.category = "MISC";
			if (gameRule.displayName == null)
				gameRule.displayName = elementName;
			if (gameRule.description == null)
				gameRule.description = "";
		}

		if (ge instanceof Attribute attribute) {
			if (attribute.sentiment == null || attribute.sentiment.isEmpty())
				attribute.sentiment = "positive";
			if (attribute.entities == null)
				attribute.entities = new ArrayList<>();
		}

		if (ge instanceof KeyBinding key) {
			if (key.keyBindingName == null)
				key.keyBindingName = elementName;
			if (key.keyBindingCategoryKey == null)
				key.keyBindingCategoryKey = "key.categories.misc";
			if (key.triggerKey == null)
				key.triggerKey = new KeyButton(workspace, "UNKNOWN");
		}

		if (ge instanceof VillagerProfession prof) {
			if (prof.pointOfInterest == null || prof.pointOfInterest.getUnmappedValue().isEmpty())
				prof.pointOfInterest = new MItemBlock(workspace, "Blocks.CRAFTING_TABLE");
			if (prof.actionSound == null || prof.actionSound.getUnmappedValue().isEmpty())
				prof.actionSound = new Sound(workspace, "entity.villager.work_librarian");
			if (prof.professionTextureFile == null)
				prof.professionTextureFile = "";
			if (prof.zombifiedProfessionTextureFile == null)
				prof.zombifiedProfessionTextureFile = "";
			if (prof.hat == null)
				prof.hat = "None";
		}

		if (ge instanceof VillagerTrade trade) {
			if (trade.villagerProfession == null || trade.villagerProfession.getUnmappedValue().isEmpty())
				trade.villagerProfession = new ProfessionEntry(workspace, "ARMORER");
			if (trade.trades == null)
				trade.trades = new ArrayList<>();
		}

		if (ge instanceof PotionEffect effect) {
			if (effect.effectName == null || effect.effectName.isEmpty())
				effect.effectName = elementName;
			if (effect.mobEffectCategory == null)
				effect.mobEffectCategory = "NEUTRAL";
			if (effect.color == null)
				effect.color = new Color(0x6699ff);
			if (effect.particle == null || effect.particle.getUnmappedValue() == null
					|| effect.particle.getUnmappedValue().isEmpty())
				effect.particle = new ParticleEntry(workspace, "EXPLOSION_NORMAL");
			if (effect.onAddedSound == null || effect.onAddedSound.getUnmappedValue() == null
					|| effect.onAddedSound.getUnmappedValue().isEmpty())
				effect.onAddedSound = new Sound(workspace, "entity.villager.work_librarian");
			if (effect.modifiers == null)
				effect.modifiers = new ArrayList<>();
		}

		if (ge instanceof ArmorTrim trim) {
			if (trim.item == null || trim.item.getUnmappedValue() == null
					|| trim.item.getUnmappedValue().isEmpty())
				trim.item = new MItemBlock(workspace, "Items.IRON_INGOT");
			if (trim.name == null)
				trim.name = elementName.toLowerCase(Locale.ROOT);
			if (trim.armorTextureFile == null)
				trim.armorTextureFile = "";
		}

		if (ge instanceof ItemExtension ext) {
			if (ext.item == null)
				ext.item = new MItemBlock(workspace, "");
			if (ext.fuelPower == null)
				ext.fuelPower = new NumberProcedure(null, 0);
			if (ext.fuelSuccessCondition == null)
				ext.fuelSuccessCondition = new Procedure(null);
			if (ext.dispenseSuccessCondition == null)
				ext.dispenseSuccessCondition = new Procedure(null);
			if (ext.dispenseResultItemstack == null)
				ext.dispenseResultItemstack = new Procedure(null);
		}

		if (ge instanceof Overlay overlay) {
			if (overlay.components == null)
				overlay.components = new ArrayList<>();
			if (overlay.baseTexture == null)
				overlay.baseTexture = "";
			if (overlay.overlayTarget == null)
				overlay.overlayTarget = new ScreenEntry(workspace, "Ingame");
			if (overlay.displayCondition == null)
				overlay.displayCondition = new Procedure(null);
			if (overlay.gridSettings == null)
				overlay.gridSettings = new GridSettings();
		}

		if (ge instanceof GUI gui) {
			if (gui.components == null)
				gui.components = new ArrayList<>();
			if (gui.gridSettings == null)
				gui.gridSettings = new GridSettings();
			if (gui.onOpen == null)
				gui.onOpen = new Procedure(null);
			if (gui.onTick == null)
				gui.onTick = new Procedure(null);
			if (gui.onClosed == null)
				gui.onClosed = new Procedure(null);
		}

		if (ge instanceof SpecialEntity special) {
			if (special.name == null)
				special.name = elementName;
			if (special.rarity == null)
				special.rarity = "COMMON";
			if (special.creativeTabs == null)
				special.creativeTabs = new ArrayList<>();
			if (special.itemTexture == null)
				special.itemTexture = special.entityTexture;
		}
	}

	/**
	 * GEValidator sometimes resets mappable fields to the first datalist entry when no
	 * user value is supplied. Re-apply safe defaults for those fields unless the user
	 * explicitly provided them in the request.
	 */
	private void applyPostValidationDefaults(GeneratableElement ge, Map<String, Object> properties) {
		if (ge instanceof PotionEffect effect) {
			if (effect.particle == null || effect.particle.getUnmappedValue() == null
					|| effect.particle.getUnmappedValue().isEmpty())
				effect.particle = new ParticleEntry(workspace, "EXPLOSION_NORMAL");
			if (effect.onAddedSound == null || effect.onAddedSound.getUnmappedValue() == null
					|| effect.onAddedSound.getUnmappedValue().isEmpty())
				effect.onAddedSound = new Sound(workspace, "entity.villager.work_librarian");
		}
		if (ge instanceof Feature feature) {
			if (!properties.containsKey("generationStep")) {
				feature.generationStep = new GenerationStep(workspace, "UNDERGROUND_ORES");
			}
			if (!properties.containsKey("featurexml") && (feature.featurexml == null || feature.featurexml.isEmpty())) {
				feature.featurexml = "<xml xmlns=\"https://developers.google.com/blockly/xml\"><block type=\"feature_container\" deletable=\"false\" x=\"40\" y=\"40\"><value name=\"feature\"><block type=\"feature_simple_block\"><value name=\"block\"><block type=\"blockstate_selector\"><mutation inputs=\"0\"/><field name=\"block\">minecraft:stone</field></block></value><field name=\"schedule_tick\">FALSE</field></block></value></block></xml>";
			}
		}
		if (ge instanceof Structure structure) {
			if (!properties.containsKey("generationStep")) {
				structure.generationStep = new GenerationStep(workspace, "SURFACE_STRUCTURES");
			}
		}
	}

	private boolean applySpecialProperty(GeneratableElement ge, String key, Object value) {
		String lower = key.toLowerCase(Locale.ROOT);

		if ("texture".equals(lower)) {
			if (ge instanceof Block block)
				applyBlockSingleTexture(block, value);
			else if (ge instanceof Armor armor)
				applyArmorTextures(armor, value);
			else
				applyGenericTexture(ge, value);
			return true;
		}

		if ("textures".equals(lower) && ge instanceof Block block && value instanceof Map) {
			@SuppressWarnings("unchecked")
			Map<String, Object> map = (Map<String, Object>) value;
			applyBlockTextureMap(block, map);
			return true;
		}

		if ("glow".equals(lower) || "hasgloweffect".equals(lower) || "glowcondition".equals(lower)) {
			setGlowCondition(ge, value);
			return true;
		}

		if ("renderType".equalsIgnoreCase(key) && ge instanceof Block block) {
			block.renderType = parseRenderType(value);
			return true;
		}

		if ("luminance".equalsIgnoreCase(key) && ge instanceof Block block) {
			block.luminance = convertNumberProcedure(value);
			return true;
		}

		if ("isOpaque".equalsIgnoreCase(key) && ge instanceof Block block) {
			boolean opaque = toBoolean(value);
			block.hasTransparency = !opaque;
			block.lightOpacity = opaque ? 15 : 0;
			return true;
		}

		if ("isFlammable".equalsIgnoreCase(key) && ge instanceof Block block) {
			boolean flammable = toBoolean(value);
			block.flammability = flammable ? 5 : 0;
			block.fireSpreadSpeed = flammable ? 5 : 0;
			return true;
		}

		if ("isCollideable".equalsIgnoreCase(key) && ge instanceof Block block) {
			block.isNotColidable = !toBoolean(value);
			return true;
		}

		if ("drops".equalsIgnoreCase(key) && ge instanceof Block block) {
			applyBlockDrops(block, value);
			return true;
		}

		if ("creativeTab".equalsIgnoreCase(key) || "creativeTabs".equalsIgnoreCase(key)) {
			setCreativeTabs(ge, value);
			return true;
		}

		if ("toolClass".equalsIgnoreCase(key)) {
			if (ge instanceof Tool tool) {
				tool.toolType = normalizeToolType(toString(value));
				return true;
			}
			if (ge instanceof Block block) {
				block.destroyTool = normalizeDestroyTool(toString(value));
				return true;
			}
		}

		if ("toolLevel".equalsIgnoreCase(key) && ge instanceof Block block) {
			block.vanillaToolTier = toString(value).toUpperCase(Locale.ROOT);
			block.requiresCorrectTool = !block.vanillaToolTier.isEmpty();
			return true;
		}

		if ("material".equalsIgnoreCase(key) && ge instanceof Tool tool) {
			tool.blockDropsTier = toString(value).toUpperCase(Locale.ROOT);
			return true;
		}

		if ("repairMaterial".equalsIgnoreCase(key) && (ge instanceof Tool || ge instanceof net.mcreator.element.types.Item)) {
			setRepairItems(ge, value);
			return true;
		}

		if ("attackDamage".equalsIgnoreCase(key) || "damage".equalsIgnoreCase(key)) {
			if (ge instanceof Tool tool) {
				tool.damageVsEntity = toDouble(value);
				return true;
			}
			if (ge instanceof net.mcreator.element.types.Item item) {
				item.damageVsEntity = toDouble(value);
				return true;
			}
		}

		if ("attackSpeed".equalsIgnoreCase(key) && ge instanceof Tool tool) {
			tool.attackSpeed = toDouble(value);
			return true;
		}

		if ("durability".equalsIgnoreCase(key)) {
			if (ge instanceof Tool tool) {
				tool.usageCount = toInt(value);
				return true;
			}
		}

		if ("armorValues".equalsIgnoreCase(key) && ge instanceof Armor armor && value instanceof List) {
			@SuppressWarnings("unchecked")
			List<Object> values = (List<Object>) value;
			applyArmorValues(armor, values);
			return true;
		}

		if ("recipeType".equalsIgnoreCase(key) && ge instanceof Recipe recipe) {
			normalizeAndSetRecipeType(recipe, value);
			return true;
		}

		if (("inputs".equalsIgnoreCase(key) || "output".equalsIgnoreCase(key)) && ge instanceof Recipe recipe) {
			// handled in preProcess, but also allow standalone
			Object inputs = "inputs".equalsIgnoreCase(key) ? value : null;
			Object output = "output".equalsIgnoreCase(key) ? value : null;
			applyRecipeInputsAndOutputs(recipe, inputs, output);
			return true;
		}

		if ("pools".equalsIgnoreCase(key) && ge instanceof LootTable lootTable && value instanceof List<?>) {
			lootTable.pools = parseLootPools((List<?>) value);
			return true;
		}

		return false;
	}

	private void applyBlockSingleTexture(Block block, Object value) {
		TextureHolder holder = toTextureHolder(value, TextureType.BLOCK);
		setField(block, "texture", holder);
		setField(block, "textureTop", holder);
		setField(block, "textureLeft", holder);
		setField(block, "textureFront", holder);
		setField(block, "textureRight", holder);
		setField(block, "textureBack", holder);
		setField(block, "particleTexture", holder);
	}

	private void applyBlockTextureMap(Block block, Map<String, Object> map) {
		TextureHolder defaultHolder = null;
		if (map.containsKey("all") || map.containsKey("default")) {
			Object v = map.containsKey("all") ? map.get("all") : map.get("default");
			defaultHolder = toTextureHolder(v, TextureType.BLOCK);
			applyBlockSingleTexture(block, defaultHolder);
		}

		setIfPresent(block, map, "bottom", "texture", TextureType.BLOCK);
		setIfPresent(block, map, "top", "textureTop", TextureType.BLOCK);
		setIfPresent(block, map, "left", "textureLeft", TextureType.BLOCK);
		setIfPresent(block, map, "front", "textureFront", TextureType.BLOCK);
		setIfPresent(block, map, "right", "textureRight", TextureType.BLOCK);
		setIfPresent(block, map, "back", "textureBack", TextureType.BLOCK);
		if (map.containsKey("side")) {
			TextureHolder side = toTextureHolder(map.get("side"), TextureType.BLOCK);
			setField(block, "textureLeft", side);
			setField(block, "textureFront", side);
			setField(block, "textureRight", side);
			setField(block, "textureBack", side);
		}
		setIfPresent(block, map, "particle", "particleTexture", TextureType.BLOCK);
		setIfPresent(block, map, "item", "itemTexture", TextureType.ITEM);
	}

	private void setIfPresent(Block block, Map<String, Object> map, String key, String fieldName, TextureType type) {
		if (map.containsKey(key)) {
			setField(block, fieldName, toTextureHolder(map.get(key), type));
		}
	}

	private void applyGenericTexture(GeneratableElement ge, Object value) {
		TextureType type = getTextureTypeForField(ge.getClass(), "texture", TextureType.ITEM);
		setField(ge, "texture", toTextureHolder(value, type));
	}

	private void applyArmorTextures(Armor armor, Object value) {
		String textureName;
		if (value instanceof String s) {
			textureName = sanitizeTextureName(s);
		} else if (value instanceof Map<?, ?> map) {
			Object base = map.get("base");
			if (base == null)
				base = map.get("layer");
			if (base == null)
				base = elementName;
			textureName = sanitizeTextureName(String.valueOf(base));
			applyArmorPieceTextures(armor, map);
		} else {
			textureName = elementName;
		}

		armor.armorTextureFile = textureName;

		if (!(value instanceof Map<?, ?>)) {
			TextureHolder holder = toTextureHolder(value, TextureType.ITEM);
			setField(armor, "textureHelmet", holder);
			setField(armor, "textureBody", holder);
			setField(armor, "textureLeggings", holder);
			setField(armor, "textureBoots", holder);
			armor.enableHelmet = true;
			armor.enableBody = true;
			armor.enableLeggings = true;
			armor.enableBoots = true;
		}
	}

	private void applyArmorPieceTextures(Armor armor, Map<?, ?> map) {
		if (map.containsKey("helmet")) {
			setField(armor, "textureHelmet", toTextureHolder(map.get("helmet"), TextureType.ITEM));
			armor.enableHelmet = true;
		}
		if (map.containsKey("body") || map.containsKey("chestplate")) {
			Object v = map.containsKey("body") ? map.get("body") : map.get("chestplate");
			setField(armor, "textureBody", toTextureHolder(v, TextureType.ITEM));
			armor.enableBody = true;
		}
		if (map.containsKey("leggings")) {
			setField(armor, "textureLeggings", toTextureHolder(map.get("leggings"), TextureType.ITEM));
			armor.enableLeggings = true;
		}
		if (map.containsKey("boots")) {
			setField(armor, "textureBoots", toTextureHolder(map.get("boots"), TextureType.ITEM));
			armor.enableBoots = true;
		}
	}

	private void applyArmorPreset(Armor armor, String material, Map<String, Object> properties) {
		if (!ARMOR_PRESET_DURABILITY.containsKey(material))
			return;

		Set<String> lowerKeys = new HashSet<>();
		for (String k : properties.keySet())
			lowerKeys.add(k.toLowerCase(Locale.ROOT));

		if (!lowerKeys.contains("durability") && !lowerKeys.contains("maxdamage"))
			armor.maxDamage = ARMOR_PRESET_DURABILITY.get(material);
		if (!lowerKeys.contains("armordurability") && !lowerKeys.contains("damagevaluehelmet")) {
			int[] values = ARMOR_PRESET_VALUES.get(material);
			armor.damageValueHelmet = values[0];
			armor.damageValueBody = values[1];
			armor.damageValueLeggings = values[2];
			armor.damageValueBoots = values[3];
		}
		if (!lowerKeys.contains("enchantability"))
			armor.enchantability = ARMOR_PRESET_ENCHANTABILITY.get(material);
		if (!lowerKeys.contains("toughness"))
			armor.toughness = ARMOR_PRESET_TOUGHNESS.get(material);
		if (!lowerKeys.contains("knockbackresistance"))
			armor.knockbackResistance = ARMOR_PRESET_KNOCKBACK.get(material);
	}

	private void applyArmorValues(Armor armor, List<Object> values) {
		if (values.size() >= 1)
			armor.damageValueHelmet = toInt(values.get(0));
		if (values.size() >= 2)
			armor.damageValueBody = toInt(values.get(1));
		if (values.size() >= 3)
			armor.damageValueLeggings = toInt(values.get(2));
		if (values.size() >= 4)
			armor.damageValueBoots = toInt(values.get(3));
	}

	private void setGlowCondition(GeneratableElement ge, Object value) {
		LogicProcedure glow;
		if (value instanceof Boolean b) {
			glow = new LogicProcedure(null, b);
		} else if (value instanceof String s) {
			if (s.isEmpty())
				glow = new LogicProcedure(null, false);
			else
				glow = new LogicProcedure(s, false);
		} else if (value instanceof Map<?, ?> map) {
			String name = map.containsKey("procedure") ? String.valueOf(map.get("procedure")) : null;
			Object fixedValue = map.get("fixedValue");
			boolean fixed = fixedValue instanceof Boolean ? (Boolean) fixedValue : false;
			glow = new LogicProcedure(name, fixed);
		} else {
			glow = new LogicProcedure(null, toBoolean(value));
		}

		String[] possibleFields = { "glowCondition", "helmetGlowCondition", "bodyGlowCondition", "leggingsGlowCondition", "bootsGlowCondition" };
		for (String f : possibleFields) {
			Field field = findField(ge.getClass(), f);
			if (field != null && field.getType() == LogicProcedure.class) {
				try {
					field.setAccessible(true);
					field.set(ge, glow);
				} catch (IllegalAccessException ignored) {
				}
			}
		}
	}

	private void setCreativeTabs(GeneratableElement ge, Object value) {
		List<TabEntry> tabs = new ArrayList<>();
		if (value instanceof String s) {
			if (!s.isEmpty())
				tabs.add(new TabEntry(workspace, normalizeCreativeTab(s)));
		} else if (value instanceof List<?> list) {
			for (Object o : list) {
				if (o != null && !String.valueOf(o).isEmpty())
					tabs.add(new TabEntry(workspace, normalizeCreativeTab(String.valueOf(o))));
			}
		}
		setField(ge, "creativeTabs", tabs);
	}

	private String normalizeCreativeTab(String s) {
		// Custom tab references are CUSTOM:<Name> and must keep mixed case.
		if (s.startsWith("CUSTOM:") || s.startsWith("custom:"))
			return s;
		s = s.toUpperCase(Locale.ROOT);
		if (s.startsWith("TAB_"))
			s = s.substring(4);
		if (s.startsWith("ITEMGROUP."))
			s = s.substring(10);
		return s;
	}

	private void setRepairItems(GeneratableElement ge, Object value) {
		List<MItemBlock> items = new ArrayList<>();
		if (value instanceof String s) {
			if (!s.isEmpty())
				items.add(toMItemBlock(s));
		} else if (value instanceof List<?> list) {
			for (Object o : list) {
				if (o != null && !String.valueOf(o).isEmpty())
					items.add(toMItemBlock(o));
			}
		}
		setField(ge, "repairItems", items);
	}

	private void applyBlockDrops(Block block, Object value) {
		if (value instanceof String s) {
			block.customDrop = toMItemBlock(s);
			block.dropAmount = 1;
		} else if (value instanceof Map<?, ?> map) {
			Object item = map.get("item");
			if (item != null) {
				block.customDrop = toMItemBlock(item);
				Object count = map.get("count");
				block.dropAmount = count != null ? toInt(count) : 1;
			}
		}
	}

	private void normalizeAndSetRecipeType(Recipe recipe, Object value) {
		String rt = String.valueOf(value).toLowerCase(Locale.ROOT);
		switch (rt) {
			case "shaped" -> {
				recipe.recipeType = "Crafting";
				recipe.recipeShapeless = false;
			}
			case "shapeless" -> {
				recipe.recipeType = "Crafting";
				recipe.recipeShapeless = true;
			}
			case "smelting" -> recipe.recipeType = "Smelting";
			case "blasting" -> recipe.recipeType = "Blasting";
			case "smoking" -> recipe.recipeType = "Smoking";
			case "campfire", "campfire_cooking" -> recipe.recipeType = "Campfire cooking";
			case "stonecutting", "stone_cutting" -> recipe.recipeType = "Stone cutting";
			case "smithing" -> recipe.recipeType = "Smithing";
			case "brewing" -> recipe.recipeType = "Brewing";
			default -> recipe.recipeType = String.valueOf(value);
		}
	}

	private void applyRecipeInputsAndOutputs(Recipe recipe, Object inputs, Object output) {
		MItemBlock outItem = toMItemBlock(output);
		int outCount = 1;
		if (output instanceof Map<?, ?> map) {
			Object count = map.get("count");
			if (count != null)
				outCount = toInt(count);
		}

		String rt = recipe.recipeType == null ? "Crafting" : recipe.recipeType;

		switch (rt) {
			case "Crafting" -> {
				recipe.recipeReturnStack = outItem;
				recipe.recipeRetstackSize = outCount;
				if (inputs instanceof List<?> list) {
					MItemBlock[] slots = new MItemBlock[9];
					Arrays.fill(slots, new MItemBlock(workspace, ""));
					for (int i = 0; i < Math.min(list.size(), 9); i++) {
						slots[i] = toMItemBlock(list.get(i));
					}
					recipe.recipeSlots = slots;
				}
			}
			case "Smelting" -> {
				recipe.smeltingInputStack = toMItemBlock(inputs);
				recipe.smeltingReturnStack = outItem;
				recipe.recipeRetstackSize = outCount;
			}
			case "Blasting" -> {
				recipe.blastingInputStack = toMItemBlock(inputs);
				recipe.blastingReturnStack = outItem;
			}
			case "Smoking" -> {
				recipe.smokingInputStack = toMItemBlock(inputs);
				recipe.smokingReturnStack = outItem;
			}
			case "Campfire cooking" -> {
				recipe.campfireCookingInputStack = toMItemBlock(inputs);
				recipe.campfireCookingReturnStack = outItem;
			}
			case "Stone cutting" -> {
				recipe.stoneCuttingInputStack = toMItemBlock(inputs);
				recipe.stoneCuttingReturnStack = outItem;
			}
			case "Smithing" -> {
				if (inputs instanceof List<?> list && list.size() >= 2) {
					recipe.smithingInputStack = toMItemBlock(list.get(0));
					recipe.smithingInputAdditionStack = toMItemBlock(list.get(1));
					if (list.size() >= 3)
						recipe.smithingInputTemplateStack = toMItemBlock(list.get(2));
				}
				recipe.smithingReturnStack = outItem;
			}
			case "Brewing" -> {
				if (inputs instanceof List<?> list && list.size() >= 2) {
					recipe.brewingInputStack = toMItemBlock(list.get(0));
					recipe.brewingIngredientStack = toMItemBlock(list.get(1));
				}
				recipe.brewingReturnStack = outItem;
			}
		}
	}

	private List<LootTable.Pool> parseLootPools(List<?> source) {
		List<LootTable.Pool> pools = new ArrayList<>();
		for (Object o : source) {
			if (!(o instanceof Map<?, ?> map))
				continue;
			LootTable.Pool pool = new LootTable.Pool();
			Object rolls = map.get("rolls");
			if (rolls instanceof Number n) {
				pool.minrolls = n.intValue();
				pool.maxrolls = n.intValue();
			} else if (rolls instanceof Map<?, ?> rm) {
				pool.minrolls = toInt(rm.get("min"), 1);
				pool.maxrolls = toInt(rm.get("max"), 1);
			}
			Object bonus = map.get("bonusRolls");
			if (bonus instanceof Number n) {
				pool.minbonusrolls = n.intValue();
				pool.maxbonusrolls = n.intValue();
				pool.hasbonusrolls = true;
			} else if (bonus instanceof Map<?, ?> bm) {
				pool.minbonusrolls = toInt(bm.get("min"), 0);
				pool.maxbonusrolls = toInt(bm.get("max"), 0);
				pool.hasbonusrolls = true;
			}
			pool.entries = parseLootEntries(map.get("entries"));
			pools.add(pool);
		}
		return pools;
	}

	private List<LootTable.Pool.Entry> parseLootEntries(Object entriesObj) {
		List<LootTable.Pool.Entry> entries = new ArrayList<>();
		if (!(entriesObj instanceof List<?> list))
			return entries;
		for (Object o : list) {
			if (!(o instanceof Map<?, ?> map))
				continue;
			LootTable.Pool.Entry entry = new LootTable.Pool.Entry();
			if (map.containsKey("type"))
				entry.type = toString(map.get("type"));
			Object item = map.containsKey("item") ? map.get("item") : map.get("name");
			if (item != null)
				entry.item = toMItemBlock(item);
			if (map.containsKey("weight"))
				entry.weight = toInt(map.get("weight"), 1);
			if (map.containsKey("minCount"))
				entry.minCount = toInt(map.get("minCount"), 1);
			if (map.containsKey("maxCount"))
				entry.maxCount = toInt(map.get("maxCount"), 1);
			entries.add(entry);
		}
		return entries;
	}

	private String resolveAlias(String elementType, String key) {
		Map<String, String> map = ALIASES.get(elementType.toLowerCase(Locale.ROOT));
		if (map == null)
			return null;
		String alias = map.get(key.toLowerCase(Locale.ROOT));
		return alias;
	}

	private Field findField(Class<?> clazz, String name) {
		Class<?> current = clazz;
		while (current != null && current != Object.class) {
			for (Field f : current.getDeclaredFields()) {
				if (f.getName().equalsIgnoreCase(name))
					return f;
			}
			current = current.getSuperclass();
		}
		return null;
	}

	private void setField(GeneratableElement ge, String fieldName, Object value) {
		try {
			Field f = findField(ge.getClass(), fieldName);
			if (f != null) {
				f.setAccessible(true);
				f.set(ge, value);
			}
		} catch (Exception e) {
			LOG.warn("Could not set field {}: {}", fieldName, e.getMessage());
		}
	}

	private Object convertValue(Field field, Object value) throws Exception {
		if (value == null)
			return null;

		Class<?> type = field.getType();

		if (type == String.class) {
			return String.valueOf(value);
		}

		if (type == int.class || type == Integer.class)
			return toInt(value);
		if (type == long.class || type == Long.class)
			return (long) toDouble(value);
		if (type == short.class || type == Short.class)
			return (short) toInt(value);
		if (type == byte.class || type == Byte.class)
			return (byte) toInt(value);
		if (type == float.class || type == Float.class)
			return (float) toDouble(value);
		if (type == double.class || type == Double.class)
			return toDouble(value);
		if (type == boolean.class || type == Boolean.class)
			return toBoolean(value);

		if (Color.class.isAssignableFrom(type))
			return toColor(value);

		if (TextureHolder.class.isAssignableFrom(type)) {
			TextureType textureType = getTextureTypeForField(field);
			return toTextureHolder(value, textureType);
		}

		if (MappableElement.class.isAssignableFrom(type)) {
			return toMappableElement(type, value);
		}

		if (Procedure.class.isAssignableFrom(type) || RetvalProcedure.class.isAssignableFrom(type)) {
			return toProcedure(field.getType(), value);
		}

		if (type.isArray()) {
			return toArray(type.getComponentType(), value);
		}

		if (List.class.isAssignableFrom(type)) {
			return toList(field, value);
		}

		if (Map.class.isAssignableFrom(type)) {
			return toMap(field, value);
		}

		LOG.warn("Unsupported field type {} for {}", type.getName(), field.getName());
		return null;
	}

	private TextureHolder toTextureHolder(Object value, TextureType type) {
		if (value == null)
			return new TextureHolder(workspace, "mcp_placeholder");

		String textureName;
		if (value instanceof String s) {
			textureName = s;
		} else if (value instanceof Map<?, ?> map) {
			Object nameObj = map.get("name");
			if (nameObj == null)
				nameObj = map.get("path");
			if (nameObj == null)
				nameObj = "mcp_placeholder";
			textureName = String.valueOf(nameObj);
		} else {
			textureName = String.valueOf(value);
		}

		if (looksLikeFilePath(textureName)) {
			Path source = Path.of(textureName);
			if (Files.exists(source)) {
				String name = sanitizeTextureName(source.getFileName().toString());
				File targetDir = workspace.getFolderManager().getTexturesFolder(type);
				if (targetDir != null) {
					targetDir.mkdirs();
					Path target = targetDir.toPath().resolve(name + ".png");
					try {
						Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
						return new TextureHolder(workspace, name);
					} catch (Exception e) {
						LOG.warn("Failed to copy texture {}: {}", textureName, e.getMessage());
					}
				}
			}
		}

		textureName = sanitizeTextureName(textureName);
		ensurePlaceholderTexture(type, textureName);
		return new TextureHolder(workspace, textureName);
	}

	private MappableElement toMappableElement(Class<?> type, Object value) {
		String s = String.valueOf(value);
		try {
			if (type.getSimpleName().equals("MItemBlock"))
				return toMItemBlock(value);

			Constructor<?> ctor = type.getDeclaredConstructor(Workspace.class, String.class);
			ctor.setAccessible(true);
			String simple = type.getSimpleName();
			if ("StepSound".equals(simple) || "GenerationStep".equals(simple) || "ProfessionEntry".equals(simple)
					|| "AttributeEntry".equals(simple) || "EquipmentSlotEntry".equals(simple)
					|| "ParticleEntry".equals(simple) || "EffectEntry".equals(simple) || "KeyButton".equals(simple)) {
				s = s.toUpperCase(Locale.ROOT);
			}
			if ("TabEntry".equals(simple) && !s.contains(":")) {
				s = normalizeCreativeTab(s);
			}
			return (MappableElement) ctor.newInstance(workspace, s);
		} catch (Exception e) {
			LOG.warn("Could not create {} from '{}': {}", type.getSimpleName(), s, e.getMessage());
			return null;
		}
	}

	private Procedure toProcedure(Class<?> type, Object value) {
		try {
			if (value == null) {
				return createDefaultProcedure(type, null);
			}
			if (value instanceof String s) {
				if (s.isEmpty())
					return createDefaultProcedure(type, null);
				return createDefaultProcedure(type, s);
			}
			if (value instanceof Map<?, ?> map) {
				String name = map.containsKey("procedure") ? String.valueOf(map.get("procedure")) : null;
				return createDefaultProcedure(type, name, map.get("fixedValue"));
			}
			if (type == LogicProcedure.class) {
				return new LogicProcedure(null, toBoolean(value));
			}
			if (type == NumberProcedure.class) {
				return new NumberProcedure(null, toDouble(value));
			}
			if (type == StringListProcedure.class && value instanceof List) {
				@SuppressWarnings("unchecked")
				List<String> list = ((List<Object>) value).stream().map(String::valueOf).toList();
				return new StringListProcedure(null, list);
			}
			return createDefaultProcedure(type, String.valueOf(value));
		} catch (Exception e) {
			LOG.warn("Could not create procedure of type {}: {}", type.getSimpleName(), e.getMessage());
			return null;
		}
	}

	private Procedure createDefaultProcedure(Class<?> type, String name) {
		return createDefaultProcedure(type, name, null);
	}

	private Procedure createDefaultProcedure(Class<?> type, String name, Object fixedValue) {
		try {
			if (type == Procedure.class || type == net.mcreator.element.parts.procedure.Procedure.class) {
				return new Procedure(name);
			}
			if (type == LogicProcedure.class) {
				return new LogicProcedure(name, fixedValue instanceof Boolean ? (Boolean) fixedValue : false);
			}
			if (type == NumberProcedure.class) {
				return new NumberProcedure(name, fixedValue instanceof Number ? ((Number) fixedValue).doubleValue() : 0.0);
			}
			if (type == StringListProcedure.class) {
				return new StringListProcedure(name, Collections.emptyList());
			}
			// Fallback for any RetvalProcedure subclass with (String, T) constructor
			for (Constructor<?> ctor : type.getDeclaredConstructors()) {
				ctor.setAccessible(true);
				Class<?>[] params = ctor.getParameterTypes();
				if (params.length == 2 && params[0] == String.class) {
					Object second = defaultProcedureReturnValue(params[1]);
					return (Procedure) ctor.newInstance(name, second);
				}
			}
			return new Procedure(name);
		} catch (Exception e) {
			LOG.warn("Could not create default procedure for {}: {}", type.getSimpleName(), e.getMessage());
			return null;
		}
	}

	private Object defaultProcedureReturnValue(Class<?> type) {
		if (type == double.class || type == Double.class)
			return 0.0;
		if (type == boolean.class || type == Boolean.class)
			return false;
		if (type == String.class)
			return "";
		if (type == List.class)
			return new ArrayList<String>();
		if (type == String[].class)
			return new String[0];
		return null;
	}

	private NumberProcedure convertNumberProcedure(Object value) {
		if (value instanceof Number n)
			return new NumberProcedure(null, n.doubleValue());
		if (value instanceof String s) {
			if (s.isEmpty())
				return new NumberProcedure(null, 0.0);
			try {
				return new NumberProcedure(null, Double.parseDouble(s));
			} catch (NumberFormatException e) {
				return new NumberProcedure(s, 0.0);
			}
		}
		if (value instanceof Map<?, ?> map) {
			String name = map.containsKey("procedure") ? String.valueOf(map.get("procedure")) : null;
			Object fixed = map.get("fixedValue");
			double v = fixed instanceof Number ? ((Number) fixed).doubleValue() : 0.0;
			return new NumberProcedure(name, v);
		}
		return new NumberProcedure(null, 0.0);
	}

	private Object toArray(Class<?> componentType, Object value) {
		List<Object> source;
		if (value instanceof List<?> list) {
			source = new ArrayList<>(list);
		} else if (value.getClass().isArray()) {
			source = new ArrayList<>();
			int len = Array.getLength(value);
			for (int i = 0; i < len; i++)
				source.add(Array.get(value, i));
		} else {
			source = List.of(value);
		}

		Object array = Array.newInstance(componentType, source.size());
		for (int i = 0; i < source.size(); i++) {
			Array.set(array, i, convertSimpleValue(componentType, source.get(i), null));
		}
		return array;
	}

	@SuppressWarnings("unchecked")
	private List<Object> toList(Field field, Object value) throws Exception {
		java.lang.reflect.Type genericType = field.getGenericType();
		Class<?> componentType = Object.class;
		if (genericType instanceof ParameterizedType pt) {
			java.lang.reflect.Type[] args = pt.getActualTypeArguments();
			if (args.length == 1 && args[0] instanceof Class<?>) {
				componentType = (Class<?>) args[0];
			}
		}

		List<Object> source;
		if (value instanceof List<?> list) {
			source = new ArrayList<>(list);
		} else if (value instanceof String s) {
			source = new ArrayList<>();
			source.add(s);
		} else if (value.getClass().isArray()) {
			source = new ArrayList<>();
			int len = Array.getLength(value);
			for (int i = 0; i < len; i++)
				source.add(Array.get(value, i));
		} else {
			source = List.of(value);
		}

		List<Object> result = new ArrayList<>();
		for (Object o : source) {
			Object converted = convertSimpleValue(componentType, o, field);
			if (converted != null || componentType == String.class)
				result.add(converted);
		}
		return result;
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> toMap(Field field, Object value) {
		if (!(value instanceof Map<?, ?> map))
			return null;

		java.lang.reflect.Type genericType = field.getGenericType();
		Class<?> keyType = String.class;
		Class<?> valueType = Object.class;
		if (genericType instanceof ParameterizedType pt) {
			java.lang.reflect.Type[] args = pt.getActualTypeArguments();
			if (args.length == 2) {
				if (args[0] instanceof Class<?>)
					keyType = (Class<?>) args[0];
				if (args[1] instanceof Class<?>)
					valueType = (Class<?>) args[1];
			}
		}

		Map<String, Object> result = new HashMap<>();
		for (Map.Entry<?, ?> entry : map.entrySet()) {
			String k = String.valueOf(entry.getKey());
			Object v = entry.getValue();
			if (valueType == Procedure.class || valueType == net.mcreator.element.parts.procedure.Procedure.class) {
				result.put(k, createDefaultProcedure(valueType, String.valueOf(v)));
			} else {
				result.put(k, convertSimpleValue(valueType, v, field));
			}
		}
		return result;
	}

	private Object convertSimpleValue(Class<?> type, Object value, Field field) {
		try {
			if (String.class.isAssignableFrom(type))
				return String.valueOf(value);
			if (type == int.class || type == Integer.class)
				return toInt(value);
			if (type == long.class || type == Long.class)
				return (long) toDouble(value);
			if (type == short.class || type == Short.class)
				return (short) toInt(value);
			if (type == byte.class || type == Byte.class)
				return (byte) toInt(value);
			if (type == float.class || type == Float.class)
				return (float) toDouble(value);
			if (type == double.class || type == Double.class)
				return toDouble(value);
			if (type == boolean.class || type == Boolean.class)
				return toBoolean(value);
			if (Color.class.isAssignableFrom(type))
				return toColor(value);
			if (MappableElement.class.isAssignableFrom(type))
				return toMappableElement(type, value);
			if (Procedure.class.isAssignableFrom(type))
				return toProcedure(type, value);
			if (TextureHolder.class.isAssignableFrom(type)) {
				TextureType textureType = field != null ? getTextureTypeForField(field) : TextureType.ITEM;
				return toTextureHolder(value, textureType);
			}
			LOG.warn("Unsupported list/map component type {}", type.getName());
			return null;
		} catch (Exception e) {
			LOG.warn("Could not convert value {} to {}: {}", value, type.getName(), e.getMessage());
			return null;
		}
	}

	private MItemBlock toMItemBlock(Object value) {
		String raw = "";
		if (value instanceof String s) {
			raw = s;
		} else if (value instanceof Map<?, ?> map) {
			Object item = map.get("item");
			if (item != null)
				raw = String.valueOf(item);
		} else if (value != null) {
			raw = String.valueOf(value);
		}
		return new MItemBlock(workspace, normalizeItemBlockReference(raw));
	}

	private String normalizeItemBlockReference(String value) {
		if (value == null || value.isEmpty())
			return "";

		// Already a custom or tag reference
		if (value.startsWith("CUSTOM:") || value.startsWith("TAG:"))
			return value;

		Map<String, DataListEntry> blocksitems = getBlocksItemsMap();

		// Direct datalist key
		if (blocksitems.containsKey(value))
			return value;

		// Try to resolve a custom workspace element by name (with or without namespace)
		String nameOnly = value;
		if (value.contains(":"))
			nameOnly = value.substring(value.indexOf(':') + 1);

		if (nameOnly.equalsIgnoreCase("air"))
			return "";

		// Try workspace element (custom mod item/block)
		ModElement modElement = workspace.getModElementByName(nameOnly);
		if (modElement == null) {
			// try with stripped underscores converted to CamelCase
			String camel = toCamelCase(nameOnly);
			modElement = workspace.getModElementByName(camel);
			if (modElement == null) {
				camel = toCamelCase(value.replace("_", " "));
				modElement = workspace.getModElementByName(camel);
			}
		}
		if (modElement != null) {
			String custom = "CUSTOM:" + modElement.getName();
			if (blocksitems.containsKey(custom))
				return custom;
			return custom;
		}

		// Resolve vanilla or external mod references
		String path = value;
		String namespace = null;
		if (path.contains(":")) {
			namespace = path.substring(0, path.indexOf(':'));
			path = path.substring(path.indexOf(':') + 1);
		}

		String upper = path.toUpperCase(Locale.ROOT).replace(".", "_");

		// Prefer item over block
		String itemKey = "Items." + upper;
		if (blocksitems.containsKey(itemKey))
			return itemKey;

		String blockKey = "Blocks." + upper;
		if (blocksitems.containsKey(blockKey))
			return blockKey;

		// Some items use dotted names in datalist (e.g. Items.OAK_LOG vs oak_log)
		for (String prefix : List.of("Items.", "Blocks.")) {
			for (Map.Entry<String, DataListEntry> entry : blocksitems.entrySet()) {
				String key = entry.getKey();
				if (!key.startsWith(prefix))
					continue;
				DataListEntry e = entry.getValue();
				String readable = e.getReadableName() != null ? e.getReadableName().toLowerCase(Locale.ROOT).replace(" ", "_") : "";
				String name = e.getName();
				if (path.equalsIgnoreCase(name) || path.equalsIgnoreCase(readable)
						|| path.equalsIgnoreCase(key.substring(prefix.length())))
					return key;
			}
		}

		// External mod or already namespaced reference; keep as-is
		if (namespace != null)
			return value;

		return value;
	}

	private String toCamelCase(String s) {
		if (s == null || s.isEmpty())
			return s;
		StringBuilder sb = new StringBuilder();
		boolean capitalize = true;
		for (char c : s.toCharArray()) {
			if (c == '_' || c == ' ' || c == '-') {
				capitalize = true;
			} else {
				sb.append(capitalize ? Character.toUpperCase(c) : c);
				capitalize = false;
			}
		}
		return sb.toString();
	}

	private Color toColor(Object value) {
		if (value instanceof Color c)
			return c;
		if (value instanceof Number n)
			return new Color(n.intValue(), true);
		String s = String.valueOf(value).trim();
		if (s.startsWith("#")) {
			if (s.length() == 7)
				return new Color(Integer.parseInt(s.substring(1), 16));
			if (s.length() == 9)
				return new Color((int) Long.parseLong(s.substring(1), 16), true);
		}
		if (s.startsWith("0x")) {
			s = s.substring(2);
			if (s.length() <= 6)
				return new Color(Integer.parseInt(s, 16));
			return new Color((int) Long.parseLong(s, 16), true);
		}
		if (value instanceof List<?> list && list.size() >= 3) {
			return new Color(toInt(list.get(0)), toInt(list.get(1)), toInt(list.get(2)));
		}
		try {
			return new Color(Integer.parseInt(s));
		} catch (NumberFormatException ignored) {
		}
		return Color.WHITE;
	}

	private int parseRenderType(Object value) {
		if (value instanceof Number n)
			return n.intValue();
		String s = String.valueOf(value).toLowerCase(Locale.ROOT);
		return BLOCK_RENDER_TYPES.getOrDefault(s, 10);
	}

	private TextureType getTextureTypeForField(Field field) {
		TextureReference ref = field.getAnnotation(TextureReference.class);
		if (ref != null)
			return ref.value();
		return getTextureTypeForField(field.getDeclaringClass(), field.getName(), TextureType.ITEM);
	}

	private TextureType getTextureTypeForField(Class<?> clazz, String fieldName, TextureType fallback) {
		Field f = findField(clazz, fieldName);
		if (f != null) {
			TextureReference ref = f.getAnnotation(TextureReference.class);
			if (ref != null)
				return ref.value();
		}
		return fallback;
	}

	private void ensurePlaceholderTexture(TextureType textureType, String name) {
		try {
			File texturesFolder = workspace.getFolderManager().getTexturesFolder(textureType);
			if (texturesFolder == null)
				return;
			texturesFolder.mkdirs();
			File textureFile = new File(texturesFolder, name + ".png");
			if (textureFile.isFile())
				return;

			BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = image.createGraphics();
			g.setColor(new Color(0x2C9EEC));
			g.fillRect(0, 0, 16, 16);
			g.setColor(Color.WHITE);
			g.drawRect(0, 0, 15, 15);
			g.dispose();
			javax.imageio.ImageIO.write(image, "png", textureFile);
		} catch (Exception e) {
			LOG.warn("Could not create placeholder texture {}: {}", name, e.getMessage());
		}
	}

	private boolean looksLikeFilePath(String s) {
		if (s.contains("/") || s.contains("\\"))
			return true;
		String lower = s.toLowerCase(Locale.ROOT);
		return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif")
				|| lower.endsWith(".webp");
	}

	private String sanitizeTextureName(String s) {
		String name = s;
		int dot = name.lastIndexOf('.');
		if (dot > 0)
			name = name.substring(0, dot);
		return name.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase(Locale.ROOT);
	}

	private String normalizeToolType(String value) {
		String v = value.toLowerCase(Locale.ROOT);
		return switch (v) {
			case "sword" -> "Sword";
			case "pickaxe" -> "Pickaxe";
			case "axe" -> "Axe";
			case "shovel", "spade" -> "Spade";
			case "hoe" -> "Hoe";
			case "shears" -> "Shears";
			case "shield" -> "Shield";
			case "multitool" -> "MultiTool";
			case "special" -> "Special";
			default -> capitalizeFirst(value);
		};
	}

	private String normalizeDestroyTool(String value) {
		String v = value.toLowerCase(Locale.ROOT);
		return switch (v) {
			case "pickaxe" -> "pickaxe";
			case "axe" -> "axe";
			case "shovel", "spade" -> "shovel";
			case "hoe" -> "hoe";
			default -> value.toLowerCase(Locale.ROOT);
		};
	}

	private String capitalizeFirst(String s) {
		if (s == null || s.isEmpty())
			return s;
		return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(Locale.ROOT);
	}

	private int toInt(Object value) {
		if (value instanceof Number n)
			return n.intValue();
		return Integer.parseInt(String.valueOf(value));
	}

	private int toInt(Object value, int defaultValue) {
		if (value == null)
			return defaultValue;
		try {
			return toInt(value);
		} catch (Exception e) {
			return defaultValue;
		}
	}

	private double toDouble(Object value) {
		if (value instanceof Number n)
			return n.doubleValue();
		return Double.parseDouble(String.valueOf(value));
	}

	private boolean toBoolean(Object value) {
		if (value instanceof Boolean b)
			return b;
		String s = String.valueOf(value).toLowerCase(Locale.ROOT);
		return s.equals("true") || s.equals("yes") || s.equals("1") || s.equals("on");
	}

	private String toString(Object value) {
		return value == null ? "" : String.valueOf(value);
	}
}

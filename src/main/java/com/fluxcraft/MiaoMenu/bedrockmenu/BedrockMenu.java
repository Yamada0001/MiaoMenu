package com.fluxcraft.MiaoMenu.bedrockmenu;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Pattern;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.fluxcraft.MiaoMenu.menu.requirement.ConditionGroup;
import com.fluxcraft.MiaoMenu.menu.requirement.RequirementBlock;
import com.fluxcraft.MiaoMenu.menu.requirement.RequirementResult;
import com.fluxcraft.MiaoMenu.menu.requirement.RequirementService;
import com.fluxcraft.MiaoMenu.utils.Lang;
import com.fluxcraft.MiaoMenu.utils.PlaceholderUtils;

public class BedrockMenu {
    private static final Pattern STRIP_COLOR = Pattern.compile("§[0-9a-fk-or]");

    // Cached Cumulus reflection metadata (initialized lazily)
    private static volatile boolean cumulusInitialized = false;
    private static volatile Class<?> cumulusSimpleFormClass;
    private static volatile Class<?> cumulusFormImageClass;
    private static volatile Object cumulusPathType;
    private static volatile Object cumulusUrlType;

    private static void initCumulusReflection() {
        if (cumulusInitialized) return;
        cumulusInitialized = true;
        try {
            cumulusSimpleFormClass = Class.forName("org.geysermc.cumulus.form.SimpleForm");
            cumulusFormImageClass = Class.forName("org.geysermc.cumulus.util.FormImage");
            Class<?> typeEnum = Class.forName("org.geysermc.cumulus.util.FormImage$Type");
            for (Object constant : typeEnum.getEnumConstants()) {
                if ("PATH".equals(constant.toString())) cumulusPathType = constant;
                if ("URL".equals(constant.toString())) cumulusUrlType = constant;
            }
        } catch (ClassNotFoundException ignored) {
            // Cumulus not available
        }
    }

    private static class ConfigKeys {
        public static final String MENU_ITEMS = "menu.items";
        public static final String MENU_TITLE = "menu.title";
        public static final String DEFAULT_TITLE = "Menu";
        public static final String TEXT = "text";
        public static final String ICON = "icon";
        public static final String ICON_TYPE = "icon_type";
        public static final String COMMAND = "command";
        public static final String EXECUTE_AS = "execute_as";
        public static final String DEFAULT_ICON_TYPE = "path";
        public static final String ICON_TYPE_URL = "url";
    }

    private final String name;
    private final FileConfiguration config;
    private final List<BedrockMenuItem> menuItems = new ArrayList<>();
    private final MiaoMenu plugin;
    private final RequirementService requirementService;
    private final Map<String, RequirementBlock> requirementBlocks;
    private final List<Map<?, ?>> viewRequirements;
    private final String denyMessage;
    private final String fallbackMenu;

    public BedrockMenu(String name, FileConfiguration config, MiaoMenu plugin, RequirementService requirementService) {
        this.name = name;
        this.config = config;
        this.plugin = plugin;
        this.requirementService = requirementService;
        ConfigurationSection blocksSection = config.getConfigurationSection("requirement_blocks");
        requirementBlocks = requirementService.loadBlocks(name, blocksSection);
        viewRequirements = requirementService.readRequirementList(
                config.get("view_requirement.requirements"),
                name,
                "view_requirement.requirements"
        );
        denyMessage = config.getString("view_requirement.deny_message");
        fallbackMenu = config.getString("view_requirement.fallback_menu");
        requirementService.validateRequirementBlocks(name, requirementBlocks);
        requirementService.validateRequirements(name, "view_requirement.requirements", requirementBlocks, viewRequirements);
        loadMenuItems();
    }

    private String getDefaultText() {
        return Lang.get("default-item-name");
    }

    private void loadMenuItems() {
        menuItems.clear();
        if (!config.contains(ConfigKeys.MENU_ITEMS)) {
            return;
        }
        List<?> items = config.getList(ConfigKeys.MENU_ITEMS);
        if (items == null) {
            return;
        }
        String defaultText = getDefaultText();
        for (int index = 0; index < items.size(); index++) {
            Object itemObj = items.get(index);
            if (itemObj instanceof Map<?, ?> map) {
                String text = map.get(ConfigKeys.TEXT) != null ? map.get(ConfigKeys.TEXT).toString() : defaultText;
                String icon = map.get(ConfigKeys.ICON) != null ? map.get(ConfigKeys.ICON).toString() : "";
                String iconType = map.get(ConfigKeys.ICON_TYPE) != null ? map.get(ConfigKeys.ICON_TYPE).toString() : ConfigKeys.DEFAULT_ICON_TYPE;
                String command = map.get(ConfigKeys.COMMAND) != null ? map.get(ConfigKeys.COMMAND).toString() : "";
                String executeAs = map.get(ConfigKeys.EXECUTE_AS) != null ? map.get(ConfigKeys.EXECUTE_AS).toString() : "player";
                String lockMessage = map.get("lock_message") != null ? map.get("lock_message").toString() : null;
                ConditionGroup conditionGroup = loadConditionGroup(map, "menu.items[" + index + "]");
                requirementService.validateConditionGroup(name, "menu.items[" + index + "].conditions", requirementBlocks, conditionGroup);
                menuItems.add(new BedrockMenuItem(text, icon, iconType, command, executeAs, conditionGroup, lockMessage));
            }
        }
    }

    private ConditionGroup loadConditionGroup(Map<?, ?> map, String location) {
        if (map.containsKey("conditions")) {
            if (map.get("conditions") instanceof Map<?, ?> conditionsMap) {
                return ConditionGroup.fromYaml(conditionsMap);
            }
            throw new IllegalArgumentException(location + ".conditions must be a map");
        }
        List<Map<?, ?>> legacyConditions = requirementService.readRequirementList(
                map.get("item_conditions"),
                name,
                location + ".item_conditions"
        );
        return ConditionGroup.fromLegacyConditions(legacyConditions);
    }

    public List<BedrockMenuItem> getAllItems() {
        return new ArrayList<>(menuItems);
    }

    public Object buildForm(Player player) {
        initCumulusReflection();
        if (cumulusSimpleFormClass == null) {
            return null;
        }
        try {
            Object builder = cumulusSimpleFormClass.getMethod("builder").invoke(null);
            String title = PlaceholderUtils.parse(player, getMenuTitle(), plugin);
            builder.getClass().getMethod("title", String.class).invoke(builder, title);
            builder.getClass().getMethod("content", String.class).invoke(builder, "");
            for (BedrockMenuItem item : menuItems) {
                RequirementResult requirementResult = item.evaluateRequirement(player, requirementService, name, requirementBlocks);
                boolean locked = !requirementResult.allowed();
                String buttonText;
                if (locked) {
                    String originalText = PlaceholderUtils.parse(player, item.text(), plugin);
                    buttonText = Lang.get("message.bedrock-locked-prefix") + STRIP_COLOR.matcher(originalText).replaceAll("");
                } else {
                    buttonText = PlaceholderUtils.parse(player, item.text(), plugin);
                }
                if (!locked && item.hasIcon()) {
                    addIconButton(builder, cumulusFormImageClass, buttonText, item);
                } else {
                    builder.getClass().getMethod("button", String.class).invoke(builder, buttonText);
                }
            }
            return builder;
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().log(Level.WARNING, Lang.get("log.bedrock-menu.form-build-failed"), e);
            return null;
        }
    }

    private void addIconButton(Object builder, Class<?> formImageClass, String buttonText, BedrockMenuItem item) throws ReflectiveOperationException {
        Object imageType = parseImageType(item.iconType());
        Object formImage = formImageClass.getMethod("of", formImageClass.getDeclaredClasses()[0], String.class)
                .invoke(null, imageType, item.icon());
        builder.getClass().getMethod("button", String.class, formImageClass)
                .invoke(builder, buttonText, formImage);
    }

    private Object parseImageType(String typeString) {
        initCumulusReflection();
        if (cumulusUrlType != null && ConfigKeys.ICON_TYPE_URL.equalsIgnoreCase(typeString)) {
            return cumulusUrlType;
        }
        return cumulusPathType;
    }

    public RequirementResult checkViewRequirement(Player player) {
        return requirementService.checkViewRequirement(player, name, requirementBlocks, viewRequirements, denyMessage, fallbackMenu);
    }

    private String getMenuTitle() {
        return config.getString(ConfigKeys.MENU_TITLE, ConfigKeys.DEFAULT_TITLE);
    }

    public String getName() {
        return name;
    }

    public Map<String, RequirementBlock> getRequirementBlocks() {
        return requirementBlocks;
    }

    public record BedrockMenuItem(
            String text,
            String icon,
            String iconType,
            String command,
            String executeAs,
            ConditionGroup conditionGroup,
            String lockMessage
    ) {
        public BedrockMenuItem {
            if (text == null) {
                text = "";
            }
            if (icon == null) {
                icon = "";
            }
            if (iconType == null) {
                iconType = ConfigKeys.DEFAULT_ICON_TYPE;
            }
            if (command == null) {
                command = "";
            }
            if (executeAs == null) {
                executeAs = "player";
            }
        }

        public RequirementResult evaluateRequirement(
                Player player,
                RequirementService requirementService,
                String menuName,
                Map<String, RequirementBlock> requirementBlocks
        ) {
            if (conditionGroup == null) {
                return RequirementResult.allow();
            }
            if (conditionGroup.requirements().isEmpty() && conditionGroup.children().isEmpty()) {
                return RequirementResult.allow();
            }
            return requirementService.evaluateGroup(player, menuName, requirementBlocks, conditionGroup);
        }

        public String getLockMessage(Player player, MiaoMenu plugin, RequirementResult requirementResult) {
            String message = requirementResult.denyMessage();
            if (message == null || message.isBlank()) {
                message = lockMessage;
            }
            if (message == null || message.isBlank()) {
                message = Lang.get("message.item-locked");
            }
            return PlaceholderUtils.parse(player, message, plugin);
        }

        public String getCommand() {
            return command;
        }

        public String getExecuteAs() {
            return executeAs;
        }

        public boolean hasIcon() {
            return !icon.isEmpty();
        }
    }
}

package com.fluxcraft.MiaoMenu.dialogmenu.plus;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.jspecify.annotations.Nullable;

import com.fluxcraft.MiaoMenu.dialogmenu.plus.DialogFZ.Grid;
import com.fluxcraft.MiaoMenu.dialogmenu.plus.DialogFZ.GridStyle;
import com.fluxcraft.MiaoMenu.dialogmenu.plus.DialogFZ.LayoutType;
import com.fluxcraft.MiaoMenu.menu.requirement.ConditionGroup;
import com.fluxcraft.MiaoMenu.menu.requirement.RequirementBlock;
import com.fluxcraft.MiaoMenu.menu.requirement.RequirementService;

import io.papermc.paper.registry.data.dialog.DialogBase;

/**
 * 把 {@code dialog_menus/*.yml} 解析成 {@link DialogFZ}。
 *
 * <p>解析期只做结构与类型校验，不接触玩家；任何配置错误都以
 * {@link IllegalArgumentException} 抛出，由管理器按文件隔离并记录日志。</p>
 */
public final class DialogYaml {
    private final RequirementService requirementService;

    public DialogYaml(RequirementService requirementService) {
        this.requirementService = requirementService;
    }

    public DialogFZ parse(String name, FileConfiguration config) {
        // 只能读取 requirement_blocks 子节点：直接把整份菜单当条件块会
        // 把 title/body/buttons 等正常配置项误判成"缺少 requirements 的条件块"。
        Map<String, RequirementBlock> blocks = requirementService.loadBlocks(
                name, config.getConfigurationSection("requirement_blocks"));
        requirementService.validateRequirementBlocks(name, blocks);

        LayoutType layoutType = resolveLayoutType(config);
        ConfigurationSection layout = config.getConfigurationSection("layout");
        int columns = layout != null ? layout.getInt("columns", DialogFZ.DEFAULT_COLUMNS) : DialogFZ.DEFAULT_COLUMNS;
        Grid grid = parseGrid(layout != null ? layout.getConfigurationSection("grid") : null);
        Button exitButton = layout == null
                ? null
                : parseButton(layout.getConfigurationSection("exit_button"), name + ".layout.exit_button", blocks, name);

        List<Body> bodies = parseBodies(config);
        List<Input> inputs = parseInputs(config);
        List<Button> buttons = parseButtons(config, blocks, name);
        List<DynamicListDefinition> dynamicLists = parseDynamicLists(config);

        if (layoutType == LayoutType.CONFIRMATION && buttons.size() != 2) {
            throw new IllegalArgumentException(
                    "Dialog menu '" + name + "' uses layout confirmation and therefore needs exactly 2 buttons, found "
                            + buttons.size());
        }
        if (grid != null && grid.style() == GridStyle.ITEM_OVERLAY && !inputs.isEmpty()) {
            throw new IllegalArgumentException(
                    "Dialog menu '" + name + "' uses the item_overlay grid, which cannot carry inputs "
                            + "(body clicks have no response view)");
        }

        ConfigurationSection view = config.getConfigurationSection("view_requirement");
        List<Map<?, ?>> viewRequirements = view == null
                ? List.of()
                : requirementService.readRequirementList(
                        view.get("requirements"), name, name + ".view_requirement");
        if (view != null && !viewRequirements.isEmpty()) {
            requirementService.validateRequirements(
                    name, name + ".view_requirement.requirements", blocks, viewRequirements);
        }
        String denyMessage = view == null ? null : view.getString("deny_message");
        String fallbackMenu = view == null ? null : view.getString("fallback_menu");

        return new DialogFZ(
                name,
                config.getString("title", name),
                config.getString("external_title"),
                config.getBoolean("can_close_with_escape", true),
                config.getBoolean("pause", false),
                parseAfterAction(config.getString("after_action")),
                bodies,
                inputs,
                buttons,
                dynamicLists,
                grid,
                columns,
                exitButton,
                layoutType,
                blocks,
                viewRequirements,
                denyMessage,
                fallbackMenu
        );
    }

    private LayoutType resolveLayoutType(FileConfiguration config) {
        ConfigurationSection layout = config.getConfigurationSection("layout");
        String configured = layout != null && layout.getString("type") != null
                ? layout.getString("type")
                : config.getString("type");
        return LayoutType.fromConfig(configured);
    }

    private static DialogBase.DialogAfterAction parseAfterAction(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return DialogBase.DialogAfterAction.CLOSE;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "close" -> DialogBase.DialogAfterAction.CLOSE;
            case "none" -> DialogBase.DialogAfterAction.NONE;
            case "wait_for_response" -> DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE;
            default -> throw new IllegalArgumentException("Unknown after_action '" + raw + "'");
        };
    }

    private static @Nullable Grid parseGrid(@Nullable ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        int size = section.getInt("size", 0);
        if (size <= 0) {
            throw new IllegalArgumentException("layout.grid.size must be a positive integer");
        }
        return new Grid(
                size,
                section.getInt("button_width", Grid.DEFAULT_BUTTON_WIDTH),
                GridStyle.fromConfig(section.getString("style")),
                section.getInt("overlay_offset_y", 0)
        );
    }

    private List<Body> parseBodies(FileConfiguration config) {
        List<Map<?, ?>> raw = config.getMapList("body");
        List<Body> bodies = new ArrayList<>();
        for (Map<?, ?> entry : raw) {
            Object type = entry.get("type");
            String kindName = type == null ? "text" : String.valueOf(type);
            Body.Kind kind = switch (kindName.trim().toLowerCase(Locale.ROOT)) {
                case "text" -> Body.Kind.TEXT;
                case "item" -> Body.Kind.ITEM;
                default -> throw new IllegalArgumentException("Unknown dialog body type '" + kindName + "'");
            };
            bodies.add(new Body(
                    kind,
                    asString(entry.get("text")),
                    asString(entry.get("material")),
                    asString(entry.get("display_name")),
                    asStringList(entry.get("lore")),
                    asString(entry.get("description")),
                    asBoolean(entry.get("show_decorations"), true),
                    asBoolean(entry.get("show_tooltip"), true),
                    asInt(entry.get("width"), 0),
                    asInt(entry.get("height"), 0)
            ));
        }
        return bodies;
    }

    private static List<Input> parseInputs(FileConfiguration config) {
        List<Map<?, ?>> raw = config.getMapList("inputs");
        List<Input> inputs = new ArrayList<>();
        for (Map<?, ?> entry : raw) {
            String typeName = asString(entry.get("type"));
            if (typeName == null) {
                throw new IllegalArgumentException("Every dialog input needs a 'type'");
            }
            Input.Kind kind = switch (typeName.trim().toLowerCase(Locale.ROOT)) {
                case "text" -> Input.Kind.TEXT;
                case "boolean" -> Input.Kind.BOOLEAN;
                case "number_range" -> Input.Kind.NUMBER_RANGE;
                case "single_option" -> Input.Kind.SINGLE_OPTION;
                default -> throw new IllegalArgumentException("Unknown dialog input type '" + typeName + "'");
            };
            String key = asString(entry.get("key"));
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("Every dialog input needs a non-blank 'key'");
            }
            List<Input.Option> options = new ArrayList<>();
            Object rawOptions = entry.get("options");
            if (rawOptions instanceof List<?> optionList) {
                for (Object element : optionList) {
                    if (!(element instanceof Map<?, ?> optionMap)) {
                        throw new IllegalArgumentException("Dialog input options must be maps");
                    }
                    String id = asString(optionMap.get("id"));
                    if (id == null || id.isBlank()) {
                        throw new IllegalArgumentException("Dialog input option needs an 'id'");
                    }
                    options.add(new Input.Option(
                            id,
                            asString(optionMap.get("display")),
                            asBoolean(optionMap.get("initial"), false)
                    ));
                }
            }
            if (kind == Input.Kind.SINGLE_OPTION && options.isEmpty()) {
                throw new IllegalArgumentException("Dialog input '" + key + "' needs at least one option");
            }
            inputs.add(new Input(
                    key,
                    kind,
                    asStringOrEmpty(entry.get("label")),
                    asString(entry.get("initial")),
                    asInt(entry.get("max_length"), Input.DEFAULT_MAX_LENGTH),
                    asInt(entry.get("width"), 0),
                    asFloat(entry.get("start"), 0F),
                    asFloat(entry.get("end"), 0F),
                    entry.get("step") == null ? null : asFloat(entry.get("step"), 0F),
                    entry.get("initial") == null ? null : asFloat(entry.get("initial"), 0F),
                    asBoolean(entry.get("initial"), false),
                    asString(entry.get("on_true")),
                    asString(entry.get("on_false")),
                    options
            ));
        }
        return inputs;
    }

    private List<Button> parseButtons(FileConfiguration config, Map<String, RequirementBlock> blocks, String menuName) {
        List<Map<?, ?>> raw = config.getMapList("buttons");
        List<Button> buttons = new ArrayList<>();
        for (int index = 0; index < raw.size(); index++) {
            buttons.add(toButton(raw.get(index), menuName + ".buttons[" + index + "]", blocks, menuName));
        }
        return buttons;
    }

    private @Nullable Button parseButton(
            @Nullable ConfigurationSection section,
            String location,
            Map<String, RequirementBlock> blocks,
            String menuName
    ) {
        if (section == null) {
            return null;
        }
        return toButton(section.getValues(false), location, blocks, menuName);
    }

    private Button toButton(
            Map<?, ?> entry,
            String location,
            Map<String, RequirementBlock> blocks,
            String menuName
    ) {
        ConditionGroup conditions = parseConditions(entry.get("conditions"), location, blocks, menuName);
        Object slotRaw = entry.get("slot");
        Integer slot = slotRaw == null ? null : asInt(slotRaw, 0);
        return new Button(
                asStringOrEmpty(entry.get("label")),
                asString(entry.get("tooltip")),
                asInt(entry.get("width"), Button.DEFAULT_WIDTH),
                parseIcon(entry),
                asBoolean(entry.get("icon_only"), false),
                slot,
                asString(entry.get("locked_label")),
                asString(entry.get("lock_message")),
                asBoolean(entry.get("hide_when_locked"), false),
                conditions,
                asStringList(entry.get("actions"))
        );
    }

    private ConditionGroup parseConditions(
            @Nullable Object raw,
            String location,
            Map<String, RequirementBlock> blocks,
            String menuName
    ) {
        if (raw == null) {
            return new ConditionGroup(ConditionGroup.Operator.AND, List.of(), List.of());
        }
        if (!(raw instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException(location + ".conditions must be a map");
        }
        ConditionGroup group = ConditionGroup.fromYaml(map);
        requirementService.validateConditionGroup(menuName, location + ".conditions", blocks, group);
        return group;
    }

    private static Button.@Nullable Icon parseIcon(Map<?, ?> entry) {
        Object explicit = entry.get("icon");
        if (explicit instanceof Map<?, ?> iconMap) {
            String atlas = asString(iconMap.get("atlas"));
            String sprite = asString(iconMap.get("sprite"));
            if (sprite == null || sprite.isBlank()) {
                throw new IllegalArgumentException("button.icon needs a 'sprite'");
            }
            return new Button.Icon(atlas, sprite);
        }
        if (explicit instanceof String sprite && !sprite.isBlank()) {
            return new Button.Icon(null, sprite);
        }
        String item = asString(entry.get("item"));
        if (item == null || item.isBlank()) {
            return null;
        }
        return iconFromItem(item);
    }

    /**
     * 把物品材质名映射成资源包图标。
     *
     * <p>{@code NETHER_STAR} 与 {@code minecraft:nether_star} 都会得到
     * {@code minecraft:items} / {@code minecraft:item/nether_star}。</p>
     *
     * <p>这里只做字符串推导，不查询 {@link Material} 注册表——解析阶段
     * 可能没有服务端实例（单元测试），查询注册表会抛 {@code ExceptionInInitializerError}。</p>
     */
    static Button.@Nullable Icon iconFromItem(String item) {
        String normalized = item.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.startsWith("minecraft:")) {
            normalized = normalized.substring("minecraft:".length());
        }
        if (normalized.isEmpty() || normalized.indexOf(':') >= 0) {
            // 带命名空间的非原版物品无法映射到原版图集，交给调用方忽略。
            return null;
        }
        return new Button.Icon(Button.Icon.DEFAULT_ATLAS, "minecraft:item/" + normalized);
    }

    private static List<DynamicListDefinition> parseDynamicLists(FileConfiguration config) {
        List<Map<?, ?>> raw = config.getMapList("dynamic_lists");
        List<DynamicListDefinition> definitions = new ArrayList<>();
        for (Map<?, ?> entry : raw) {
            String source = asString(entry.get("source"));
            if (source == null || source.isBlank()) {
                throw new IllegalArgumentException("Every dynamic_lists entry needs a 'source'");
            }
            definitions.add(new DynamicListDefinition(
                    source,
                    asStringOrEmpty(entry.get("label")),
                    asString(entry.get("tooltip")),
                    asInt(entry.get("width"), Button.DEFAULT_WIDTH),
                    asInt(entry.get("limit"), DynamicListDefinition.DEFAULT_LIMIT),
                    asBoolean(entry.get("sort"), false),
                    asString(entry.get("empty_label")),
                    asStringList(entry.get("actions")),
                    asOptionMap(entry.get("options"))
            ));
        }
        return definitions;
    }

    private static @Nullable String asString(@Nullable Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String asStringOrEmpty(@Nullable Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static Map<String, Object> asOptionMap(@Nullable Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> options = new java.util.LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                options.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return Map.copyOf(options);
    }

    private static List<String> asStringList(@Nullable Object value) {        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>(list.size());
        for (Object element : list) {
            if (element != null) {
                result.add(String.valueOf(element));
            }
        }
        return result;
    }

    private static boolean asBoolean(@Nullable Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return switch (text.trim().toLowerCase(Locale.ROOT)) {
                case "true", "yes", "on" -> true;
                case "false", "no", "off" -> false;
                default -> fallback;
            };
        }
        return fallback;
    }

    private static int asInt(@Nullable Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static float asFloat(@Nullable Object value, float fallback) {
        if (value instanceof Number number) {
            return number.floatValue();
        }
        if (value instanceof String text) {
            try {
                return Float.parseFloat(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}

package com.fluxcraft.MiaoMenu.dialogmenu.plus;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.fluxcraft.MiaoMenu.menu.requirement.RequirementBlock;

import io.papermc.paper.registry.data.dialog.DialogBase;

/**
 * 一个已解析完成的对话框菜单定义（{@code dialog_menus/*.yml}）。
 */
public record DialogFZ(
        String name,
        String title,
        @Nullable String externalTitle,
        boolean canCloseWithEscape,
        boolean pause,
        DialogBase.DialogAfterAction afterAction,
        List<Body> bodies,
        List<Input> inputs,
        List<Button> buttons,
        List<DynamicListDefinition> dynamicLists,
        @Nullable Grid grid,
        int columns,
        @Nullable Button exitButton,
        LayoutType layoutType,
        Map<String, RequirementBlock> requirementBlocks,
        List<Map<?, ?>> viewRequirements,
        @Nullable String denyMessage,
        @Nullable String fallbackMenu
) {
    public static final int DEFAULT_COLUMNS = 2;

    public DialogFZ {
        name = name == null ? "" : name;
        title = title == null ? "" : title;
        afterAction = afterAction == null ? DialogBase.DialogAfterAction.CLOSE : afterAction;
        bodies = bodies == null ? List.of() : List.copyOf(bodies);
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        buttons = buttons == null ? List.of() : List.copyOf(buttons);
        dynamicLists = dynamicLists == null ? List.of() : List.copyOf(dynamicLists);
        columns = columns <= 0 ? DEFAULT_COLUMNS : columns;
        requirementBlocks = requirementBlocks == null ? Map.of() : Map.copyOf(requirementBlocks);
        viewRequirements = viewRequirements == null ? List.of() : List.copyOf(viewRequirements);
    }

    /** 布局类型。 */
    public enum LayoutType {
        MULTI_ACTION("multi_action"),
        NOTICE("notice"),
        CONFIRMATION("confirmation");

        private final String configName;

        LayoutType(String configName) {
            this.configName = configName;
        }

        public String configName() {
            return configName;
        }

        public static LayoutType fromConfig(@Nullable String raw) {
            if (raw == null || raw.isBlank()) {
                return MULTI_ACTION;
            }
            String normalized = raw.trim().toLowerCase(java.util.Locale.ROOT);
            for (LayoutType type : values()) {
                if (type.configName.equals(normalized)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown dialog layout type '" + raw + "'");
        }
    }

    /** 网格样式。 */
    public enum GridStyle {
        BUTTONS("buttons"),
        ITEM_OVERLAY("item_overlay");

        private final String configName;

        GridStyle(String configName) {
            this.configName = configName;
        }

        public String configName() {
            return configName;
        }

        public static GridStyle fromConfig(@Nullable String raw) {
            if (raw == null || raw.isBlank()) {
                return BUTTONS;
            }
            String normalized = raw.trim().toLowerCase(java.util.Locale.ROOT);
            for (GridStyle style : values()) {
                if (style.configName.equals(normalized)) {
                    return style;
                }
            }
            throw new IllegalArgumentException("Unknown dialog grid style '" + raw + "'");
        }
    }

    /** 按钮网格布局。 */
    public record Grid(int size, int buttonWidth, GridStyle style, int overlayOffsetY) {
        public static final int DEFAULT_BUTTON_WIDTH = 20;
        public static final int MIN_SIZE = 1;
        public static final int MAX_SIZE = 1024;

        public Grid {
            size = Math.clamp(size, MIN_SIZE, MAX_SIZE);
            buttonWidth = buttonWidth <= 0 ? DEFAULT_BUTTON_WIDTH : buttonWidth;
            style = style == null ? GridStyle.BUTTONS : style;
        }
    }
}

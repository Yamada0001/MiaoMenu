package com.fluxcraft.MiaoMenu.dialogmenu.plus;

import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import com.fluxcraft.MiaoMenu.menu.requirement.ConditionGroup;
import com.fluxcraft.MiaoMenu.menu.requirement.RequirementBlock;
import com.fluxcraft.MiaoMenu.menu.requirement.RequirementResult;
import com.fluxcraft.MiaoMenu.menu.requirement.RequirementService;

/**
 * 一个对话框按钮的定义（来自 YAML）。
 *
 * <p>按钮本身不持有任何玩家状态，可在多个玩家之间安全共享；所有与玩家相关的
 * 内容（条件求值、占位符解析）都在渲染或点击时按需计算。</p>
 */
public record Button(
        String label,
        @Nullable String tooltip,
        int width,
        @Nullable Icon icon,
        boolean iconOnly,
        @Nullable Integer slot,
        @Nullable String lockedLabel,
        @Nullable String lockMessage,
        boolean hideWhenLocked,
        ConditionGroup conditions,
        List<String> actions
) {
    public static final int DEFAULT_WIDTH = 150;

    public Button {
        label = label == null ? "" : label;
        width = width <= 0 ? DEFAULT_WIDTH : width;
        conditions = conditions == null
                ? new ConditionGroup(ConditionGroup.Operator.AND, List.of(), List.of())
                : conditions;
        actions = actions == null ? List.of() : List.copyOf(actions);
    }

    /**
     * 按玩家求值本按钮的显示条件。
     *
     * <p>条件组为空时视为无条件限制（放行）。</p>
     */
    public RequirementResult evaluate(
            Player player,
            RequirementService requirementService,
            String menuName,
            Map<String, RequirementBlock> blocks
    ) {
        return requirementService.evaluateGroup(player, menuName, blocks, conditions);
    }

    /** 按钮图标：资源包图集与精灵名。 */
    public record Icon(String atlas, String sprite) {
        public static final String DEFAULT_ATLAS = "minecraft:items";

        public Icon {
            atlas = atlas == null || atlas.isBlank() ? DEFAULT_ATLAS : atlas;
            sprite = sprite == null ? "" : sprite;
        }
    }
}

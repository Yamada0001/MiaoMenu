package com.fluxcraft.MiaoMenu.menu.requirement;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record RequirementResult(boolean allowed, boolean locked, String denyMessage, String fallbackMenu) {
    public static RequirementResult allow() {
        return new RequirementResult(true, false, null, null);
    }

    public static RequirementResult deny(String denyMessage, String fallbackMenu) {
        return new RequirementResult(false, false, denyMessage, fallbackMenu);
    }

    public static RequirementResult locked(String denyMessage) {
        return new RequirementResult(false, true, denyMessage, null);
    }

    public static RequirementResult locked(String denyMessage, String fallbackMenu) {
        return new RequirementResult(false, true, denyMessage, fallbackMenu);
    }

    public record RequirementContext(
            String menuName,
            Map<String, RequirementBlock> blocks,
            Set<String> visitedBlocks,
            List<Map<?, ?>> requirements
    ) {
    }
}

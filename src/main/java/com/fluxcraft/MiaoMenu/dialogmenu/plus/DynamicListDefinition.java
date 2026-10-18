package com.fluxcraft.MiaoMenu.dialogmenu.plus;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * 一条动态按钮列表的定义（{@code dynamic_lists}）。
 *
 * <p>展开时按 {@link #source()} 从 {@link com.fluxcraft.MiaoMenu.dialogmenu.DynamicLR}
 * 取得条目，再套用 label/tooltip/actions 模板生成按钮。</p>
 */
public record DynamicListDefinition(
        String source,
        String label,
        @Nullable String tooltip,
        int width,
        int limit,
        boolean sort,
        @Nullable String emptyLabel,
        List<String> actions,
        Map<String, Object> options
) {
    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 200;

    public DynamicListDefinition {
        source = source == null ? "" : source;
        label = label == null ? "" : label;
        width = width <= 0 ? Button.DEFAULT_WIDTH : width;
        limit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        actions = actions == null ? List.of() : List.copyOf(actions);
        options = options == null ? Map.of() : Map.copyOf(options);
    }
}

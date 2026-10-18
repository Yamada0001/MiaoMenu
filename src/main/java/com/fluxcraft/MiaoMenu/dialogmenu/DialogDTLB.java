package com.fluxcraft.MiaoMenu.dialogmenu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.bukkit.entity.Player;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.fluxcraft.MiaoMenu.api.DynamicListEntry;
import com.fluxcraft.MiaoMenu.dialogmenu.plus.Button;
import com.fluxcraft.MiaoMenu.dialogmenu.plus.DynamicListDefinition;
import com.fluxcraft.MiaoMenu.menu.requirement.ConditionGroup;

/**
 * 把 {@code dynamic_lists} 展开成一批 {@link Button}。
 *
 * <p>展开过程完全按玩家进行：provider 抛出的任何异常都会降级为空列表并记录日志，
 * 不会中断整张对话框的构建。</p>
 */
public final class DialogDTLB {
    private static final ConditionGroup NO_CONDITIONS =
            new ConditionGroup(ConditionGroup.Operator.AND, List.of(), List.of());

    private final MiaoMenu plugin;

    public DialogDTLB(MiaoMenu plugin) {
        this.plugin = plugin;
    }

    public List<Button> expand(Player player, List<DynamicListDefinition> definitions) {
        if (definitions.isEmpty()) {
            return List.of();
        }
        List<Button> buttons = new ArrayList<>();
        DynamicLR dynamicLR = plugin.getDynamicLR();
        for (DynamicListDefinition definition : definitions) {
            List<DynamicListEntry> entries = resolveEntries(dynamicLR, player, definition);
            if (entries.isEmpty()) {
                Button empty = emptyButton(definition);
                if (empty != null) {
                    buttons.add(empty);
                }
                continue;
            }
            for (DynamicListEntry entry : entries) {
                buttons.add(toButton(definition, entry));
            }
        }
        return buttons;
    }

    private List<DynamicListEntry> resolveEntries(
            DynamicLR dynamicLR,
            Player player,
            DynamicListDefinition definition
    ) {
        List<DynamicListEntry> entries;
        try {
            entries = dynamicLR.resolve(definition.source(), player, definition.options());
        } catch (RuntimeException e) {
            plugin.getLogger().log(
                    Level.WARNING,
                    "Could not resolve dynamic list source '" + definition.source() + "'",
                    e
            );
            return List.of();
        }
        if (entries.isEmpty()) {
            return entries;
        }
        if (definition.sort()) {
            entries = entries.stream()
                    .sorted(Comparator.comparing(DynamicListEntry::display, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        }
        return entries.size() > definition.limit()
                ? entries.subList(0, definition.limit())
                : entries;
    }

    private static @org.jspecify.annotations.Nullable Button emptyButton(DynamicListDefinition definition) {
        String emptyLabel = definition.emptyLabel();
        if (emptyLabel == null || emptyLabel.isBlank()) {
            return null;
        }
        return new Button(
                emptyLabel,
                definition.tooltip(),
                definition.width(),
                null,
                false,
                null,
                null,
                null,
                false,
                NO_CONDITIONS,
                List.of()
        );
    }

    private static Button toButton(DynamicListDefinition definition, DynamicListEntry entry) {
        Map<String, String> variables = Map.of(
                "value", entry.value(),
                "display", entry.display()
        );
        List<String> actions = definition.actions().stream()
                .map(action -> substitute(action, variables))
                .toList();
        return new Button(
                substitute(definition.label(), variables),
                definition.tooltip() == null ? null : substitute(definition.tooltip(), variables),
                definition.width(),
                null,
                false,
                null,
                null,
                null,
                false,
                NO_CONDITIONS,
                actions
        );
    }

    /** 替换 {@code $(value)} / {@code $(display)} 形式的内联变量。 */
    static String substitute(String template, Map<String, String> variables) {
        if (template == null || template.isEmpty()) {
            return template;
        }
        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("$(" + entry.getKey() + ")", entry.getValue());
        }
        return result;
    }
}

package com.fluxcraft.MiaoMenu.dialogmenu.plus;

import java.util.List;

import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.fluxcraft.MiaoMenu.utils.PlaceholderUtils;

import io.papermc.paper.registry.data.dialog.input.BooleanDialogInput;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.NumberRangeDialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import net.kyori.adventure.text.Component;

/**
 * 对话框输入组件的定义。
 *
 * <p>{@link #key()} 是后续在动作串中用 {@code $(key)} 或 {@code %input_key%}
 * 引用该输入值的标识。</p>
 */
public record Input(
        String key,
        Kind kind,
        String label,
        @Nullable String initial,
        int maxLength,
        int width,
        float start,
        float end,
        @Nullable Float step,
        @Nullable Float numberInitial,
        boolean booleanInitial,
        String onTrue,
        String onFalse,
        List<Option> options
) {
    /** 输入组件类型。 */
    public enum Kind {
        TEXT,
        BOOLEAN,
        NUMBER_RANGE,
        SINGLE_OPTION
    }

    public static final int DEFAULT_MAX_LENGTH = 128;
    public static final String DEFAULT_ON_TRUE = "true";
    public static final String DEFAULT_ON_FALSE = "false";

    public Input {
        label = label == null ? "" : label;
        maxLength = maxLength <= 0 ? DEFAULT_MAX_LENGTH : maxLength;
        onTrue = onTrue == null || onTrue.isEmpty() ? DEFAULT_ON_TRUE : onTrue;
        onFalse = onFalse == null || onFalse.isEmpty() ? DEFAULT_ON_FALSE : onFalse;
        options = options == null ? List.of() : List.copyOf(options);
    }

    public DialogInput build(Player player, MiaoMenu plugin) {
        Component labelComponent = plugin.getMenuTextParser().parse(player, label);
        return switch (kind) {
            case TEXT -> buildText(player, plugin, labelComponent);
            case BOOLEAN -> buildBoolean(labelComponent);
            case NUMBER_RANGE -> buildNumberRange(labelComponent);
            case SINGLE_OPTION -> buildSingleOption(player, plugin, labelComponent);
        };
    }

    private DialogInput buildText(Player player, MiaoMenu plugin, Component labelComponent) {
        TextDialogInput.Builder builder = DialogInput.text(key, labelComponent)
                .maxLength(maxLength);
        if (width > 0) {
            builder.width(width);
        }
        if (initial != null && !initial.isEmpty()) {
            builder.initial(plainText(player, plugin, initial));
        }
        return builder.build();
    }

    private DialogInput buildBoolean(Component labelComponent) {
        BooleanDialogInput.Builder builder = DialogInput.bool(key, labelComponent)
                .initial(booleanInitial)
                .onTrue(onTrue)
                .onFalse(onFalse);
        return builder.build();
    }

    private DialogInput buildNumberRange(Component labelComponent) {
        NumberRangeDialogInput.Builder builder = DialogInput.numberRange(key, labelComponent, start, end);
        if (width > 0) {
            builder.width(width);
        }
        if (step != null) {
            builder.step(step);
        }
        if (numberInitial != null) {
            builder.initial(numberInitial);
        }
        return builder.build();
    }

    private DialogInput buildSingleOption(Player player, MiaoMenu plugin, Component labelComponent) {
        List<SingleOptionDialogInput.OptionEntry> entries = options.stream()
                .map(option -> SingleOptionDialogInput.OptionEntry.create(
                        option.id(),
                        option.display() == null || option.display().isBlank()
                                ? Component.text(option.id())
                                : plugin.getMenuTextParser().parse(player, option.display()),
                        option.initial()
                ))
                .toList();
        SingleOptionDialogInput.Builder builder = DialogInput.singleOption(key, labelComponent, entries);
        if (width > 0) {
            builder.width(width);
        }
        return builder.build();
    }

    private static String plainText(Player player, MiaoMenu plugin, String raw) {
        String parsed = PlaceholderUtils.parse(player, raw, plugin);
        return LEGACY_FORMAT.matcher(parsed).replaceAll("");
    }

    private static final java.util.regex.Pattern LEGACY_FORMAT =
            java.util.regex.Pattern.compile("(?i)\\u00a7[0-9a-fk-orx]");

    /** 单选项。 */
    public record Option(String id, @Nullable String display, boolean initial) {
    }
}

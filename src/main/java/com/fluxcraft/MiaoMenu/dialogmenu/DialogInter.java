package com.fluxcraft.MiaoMenu.dialogmenu;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.fluxcraft.MiaoMenu.dialogmenu.plus.Button;
import com.fluxcraft.MiaoMenu.dialogmenu.plus.DialogFZ;
import com.fluxcraft.MiaoMenu.dialogmenu.plus.Input;
import com.fluxcraft.MiaoMenu.foliacall.FoliaFactory;
import com.fluxcraft.MiaoMenu.menu.action.ActionRegistry;
import com.fluxcraft.MiaoMenu.utils.Lang;
import com.fluxcraft.MiaoMenu.utils.PlaceholderUtils;

import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;

/**
 * 把对话框按钮的点击转换成动作执行。
 *
 * <p>所有执行体都会先切回玩家所属的区域线程再触碰玩家状态，
 * 因此可以安全地在 Floodgate/Paper 的回调线程上被调用。</p>
 */
public final class DialogInter {
    private static final int MAX_INPUT_LENGTH = 256;

    private final MiaoMenu plugin;
    private final ActionRegistry actionRegistry;
    private final DialogFZ definition;

    public DialogInter(
            MiaoMenu plugin,
            ActionRegistry actionRegistry,
            DialogFZ definition
    ) {
        this.plugin = plugin;
        this.actionRegistry = actionRegistry;
        this.definition = definition;
    }

    /**
     * 原生按钮的点击动作。
     *
     * @return 没有动作可执行时返回 {@code null}（按钮仅作展示）
     */
    public @Nullable DialogAction createAction(Player player, Button button, boolean locked) {
        if (!locked && button.actions().isEmpty()) {
            return null;
        }
        return DialogAction.customClick(
                (response, audience) -> execute(player, button, locked, response),
                ClickCallback.Options.builder().uses(1).build()
        );
    }

    /**
     * 物品覆盖层（{@code item_overlay}）的点击动作。
     *
     * <p>正文点击没有响应视图，因此该路径不支持输入组件——{@code DialogYaml}
     * 已在解析期拒绝这种组合。</p>
     */
    public @Nullable ClickEvent<?> createOverlayClick(Player player, Button button, boolean locked) {
        if (!locked && button.actions().isEmpty()) {
            return null;
        }
        return ClickEvent.callback(
                audience -> execute(player, button, locked, null),
                ClickCallback.Options.builder().uses(1).build()
        );
    }

    private void execute(Player player, Button button, boolean locked, @Nullable DialogResponseView response) {
        if (player == null || !player.isOnline()) {
            return;
        }
        Map<String, String> values = collectInputValues(response);
        FoliaFactory.getAdapter().runTaskLaterForEntity(
                plugin,
                player,
                () -> runNow(player, button, locked, values),
                0L
        );
    }

    private void runNow(Player player, Button button, boolean locked, Map<String, String> values) {
        if (!player.isOnline()) {
            return;
        }
        if (locked) {
            String message = lockMessage(player, button);
            if (!message.isBlank()) {
                player.sendMessage(plugin.getMenuTextParser().parse(player, message));
            }
            return;
        }
        for (String action : button.actions()) {
            String withInputs = applyInputValues(action, values);
            String parsed = PlaceholderUtils.parse(player, withInputs, plugin);
            try {
                actionRegistry.dispatch(player, parsed);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Dialog action failed: " + parsed, e);
            }
        }
    }

    private String lockMessage(Player player, Button button) {
        String configured = button.lockMessage();
        if (configured == null || configured.isBlank()) {
            configured = Lang.get("message.item-locked");
        }
        return configured;
    }

    private Map<String, String> collectInputValues(@Nullable DialogResponseView response) {
        if (response == null || definition.inputs().isEmpty()) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (Input input : definition.inputs()) {
            String value = readInput(response, input);
            if (value != null) {
                values.put(input.key(), sanitize(value));
            }
        }
        return values;
    }

    private static @Nullable String readInput(DialogResponseView response, Input input) {
        return switch (input.kind()) {
            case TEXT, SINGLE_OPTION -> response.getText(input.key());
            case BOOLEAN -> {
                Boolean value = response.getBoolean(input.key());
                if (value == null) {
                    yield null;
                }
                yield value ? input.onTrue() : input.onFalse();
            }
            case NUMBER_RANGE -> {
                Float value = response.getFloat(input.key());
                yield value == null ? null : formatNumber(value);
            }
        };
    }

    private static String formatNumber(float value) {
        if (value == Math.rint(value) && !Float.isInfinite(value)) {
            return Long.toString((long) value);
        }
        return Float.toString(value);
    }

    /**
     * 限制玩家可控文本的破坏面：去掉换行、颜色码与多余控制字符，并截断长度。
     */
    private static String sanitize(String raw) {
        String cleaned = raw.replace('\n', ' ').replace('\r', ' ').replace('\u00a7', ' ');
        cleaned = cleaned.trim();
        return cleaned.length() > MAX_INPUT_LENGTH ? cleaned.substring(0, MAX_INPUT_LENGTH) : cleaned;
    }

    /**
     * 把 {@code $(key)} 与 {@code %input_key%} 两种写法替换为实际输入值。
     */
    public static String applyInputValues(String template, Map<String, String> values) {
        if (template == null || template.isEmpty() || values.isEmpty()) {
            return template;
        }
        String result = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue() == null ? "" : entry.getValue();
            result = result.replace("$(" + key + ")", value);
            result = result.replace("%input_" + key.toLowerCase(Locale.ROOT) + "%", value);
            result = result.replace("%input_" + key + "%", value);
        }
        return result;
    }
}

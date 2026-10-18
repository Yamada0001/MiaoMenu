package com.fluxcraft.MiaoMenu.menu.action;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.fluxcraft.MiaoMenu.menu.action.impl.CloseAction;
import com.fluxcraft.MiaoMenu.menu.action.impl.CmdAction;
import com.fluxcraft.MiaoMenu.menu.action.impl.DefaultAction;
import com.fluxcraft.MiaoMenu.menu.action.impl.MessageAction;
import com.fluxcraft.MiaoMenu.menu.action.impl.PlayerAction;
import com.fluxcraft.MiaoMenu.menu.action.impl.VariableAddAction;
import com.fluxcraft.MiaoMenu.menu.action.impl.VariableRemoveAction;
import com.fluxcraft.MiaoMenu.menu.action.impl.VariableSetAction;
import com.fluxcraft.MiaoMenu.security.InputValidator;
import com.fluxcraft.MiaoMenu.utils.Lang;

public class ActionRegistry {
    /** 这些前缀的内容会被当作命令执行，需要经过内容校验。 */
    private static final Set<String> COMMAND_PREFIXES = Set.of("player", "cmd", "");

    private final Map<String, MenuAction> actions = new ConcurrentHashMap<>();
    private final Plugin plugin;
    private final MenuAction defaultAction;

    public ActionRegistry(Plugin plugin) {
        this.plugin = plugin;
        this.defaultAction = new DefaultAction();
        registerDefaults();
    }

    private void registerDefaults() {
        register("player", new PlayerAction());
        register("message", new MessageAction());
        register("close", new CloseAction());
        register("cmd", new CmdAction());
        register("menu", new com.fluxcraft.MiaoMenu.menu.action.impl.MenuAction());
        register("var_set", new VariableSetAction());
        register("var_add", new VariableAddAction());
        register("var_remove", new VariableRemoveAction());
    }

    /**
     * 注册或替换一个动作前缀。
     *
     * @throws IllegalArgumentException 前缀或实现为空
     */
    public void register(String prefix, MenuAction action) {
        if (prefix == null || prefix.isBlank() || action == null) {
            throw new IllegalArgumentException("Action prefix and implementation are required");
        }
        actions.put(prefix.trim().toLowerCase(Locale.ROOT), action);
    }

    public boolean isRegistered(String prefix) {
        return prefix != null && actions.containsKey(prefix.trim().toLowerCase(Locale.ROOT));
    }

    public void dispatch(Player player, String rawCommand) {
        if (rawCommand == null || rawCommand.trim().isEmpty()) {
            return;
        }
        String trimmedCommand = rawCommand.trim();
        String prefix = "";
        String content = trimmedCommand;
        int bracketStart = trimmedCommand.indexOf('[');
        int bracketEnd = trimmedCommand.indexOf(']');
        if (bracketStart == 0 && bracketEnd > bracketStart) {
            prefix = trimmedCommand.substring(1, bracketEnd).trim().toLowerCase(Locale.ROOT);
            content = trimmedCommand.substring(bracketEnd + 1).trim();
        }

        MenuAction action;
        if (prefix.isEmpty()) {
            action = defaultAction;
        } else {
            action = actions.get(prefix);
            if (action == null) {
                // 前缀写错时不再静默降级为"以玩家身份执行命令"，否则会把配置错误
                // 变成一条意料之外的真实命令。
                plugin.getLogger().warning(Lang.get("log.action.unknown-prefix")
                        .replace("{0}", prefix)
                        .replace("{1}", trimmedCommand)
                        .replace("{2}", player.getName()));
                return;
            }
        }

        try {
            if (COMMAND_PREFIXES.contains(prefix)
                    && plugin.getConfig().getBoolean("settings.validate-commands", false)
                    && !InputValidator.isSafeCommandContent(content)) {
                plugin.getLogger().warning(Lang.get("log.action.unsafe-content")
                        .replace("{0}", InputValidator.describeUnsafe(content))
                        .replace("{1}", player.getName()));
                return;
            }
            action.execute(player, content, plugin);
        } catch (RuntimeException | LinkageError e) {
            plugin.getLogger().log(Level.SEVERE, Lang.get("log.action.dispatch-failed")
                    .replace("{0}", trimmedCommand)
                    .replace("{1}", player.getName()), e);
        }
    }
}

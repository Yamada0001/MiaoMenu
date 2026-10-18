package com.fluxcraft.MiaoMenu.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.fluxcraft.MiaoMenu.commands.impl.HelpCommand;
import com.fluxcraft.MiaoMenu.commands.impl.OpenCommand;
import com.fluxcraft.MiaoMenu.commands.impl.ReloadCommand;
import com.fluxcraft.MiaoMenu.utils.Lang;

public class CommandManager implements CommandExecutor, TabCompleter {
    private static final String CMD_HELP = "help";

    private final MiaoMenu plugin;
    private final Map<String, PluginCommand> commands = new LinkedHashMap<>();

    public CommandManager(@NotNull MiaoMenu plugin) {
        this.plugin = plugin;
        registerCommands();
    }

    private void registerCommands() {
        Map<String, String> helpDescriptions = loadHelpDescriptions();
        register("open", new OpenCommand(plugin));
        register("reload", new ReloadCommand(plugin));
        register(CMD_HELP, new HelpCommand(helpDescriptions, commands));
    }

    @NotNull
    private Map<String, String> loadHelpDescriptions() {
        Map<String, String> descriptions = Lang.getStringSection("descriptions");
        if (descriptions.isEmpty()) {
            plugin.getLogger().warning(Lang.get("log.command.descriptions-missing"));
        }
        return descriptions;
    }

    private void register(@NotNull String name, @NotNull PluginCommand command) {
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Command name cannot be empty");
        }
        commands.put(name.toLowerCase(Locale.ROOT), command);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             @NotNull String @NonNull [] args) {
        if (args.length == 0) {
            dispatchCommand(sender, CMD_HELP, new String[0]);
            return true;
        }
        String subCommandName = args[0].toLowerCase(Locale.ROOT);
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        dispatchCommand(sender, subCommandName, subArgs);
        return true;
    }

    private void dispatchCommand(@NotNull CommandSender sender, @NotNull String commandName, @NotNull String[] args) {
        PluginCommand target = commands.get(commandName);
        if (target == null) {
            sender.sendMessage(Lang.get("command.unknown").replace("{0}", alias()));
            dispatchCommand(sender, CMD_HELP, new String[0]);
            return;
        }
        if (!target.hasAccess(sender)) {
            sender.sendMessage(Lang.get("message.no-permission"));
            return;
        }
        try {
            target.execute(sender, args);
        } catch (RuntimeException | LinkageError e) {
            plugin.getLogger().log(Level.SEVERE, Lang.get("log.command.execute-failed")
                    .replace("{0}", commandName)
                    .replace("{1}", sender.getName()), e);
            sender.sendMessage(Lang.get(target.errorMessageKey()));
        }
    }

    private static String alias() {
        return Lang.get("command.main-alias");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String @NonNull [] args) {
        if (args.length == 1) {
            String typed = args[0].toLowerCase(Locale.ROOT);
            return commands.entrySet().stream()
                    .filter(entry -> entry.getValue().hasAccess(sender))
                    .map(Map.Entry::getKey)
                    .filter(key -> key.startsWith(typed))
                    .sorted()
                    .collect(Collectors.toList());
        }
        if (args.length > 1) {
            PluginCommand target = commands.get(args[0].toLowerCase(Locale.ROOT));
            if (target != null && target.hasAccess(sender)) {
                String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
                List<String> completion = target.tabComplete(sender, subArgs);
                return completion != null ? completion : Collections.emptyList();
            }
        }
        return Collections.emptyList();
    }

    /** 当前已注册的子命令（只读快照）。 */
    public List<String> registeredCommands() {
        return new ArrayList<>(commands.keySet());
    }
}

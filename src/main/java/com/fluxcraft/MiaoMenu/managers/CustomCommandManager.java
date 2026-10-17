package com.fluxcraft.MiaoMenu.managers;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.fluxcraft.MiaoMenu.menu.action.ActionRegistry;
import com.fluxcraft.MiaoMenu.utils.Lang;
import com.fluxcraft.MiaoMenu.utils.PlaceholderUtils;

public final class CustomCommandManager {
    private static final Pattern COMMAND_NAME = Pattern.compile("[a-z0-9_-]{1,32}");

    private final MiaoMenu plugin;
    private final ActionRegistry actionRegistry;
    private final CommandMap commandMap;
    private final List<Command> registeredCommands = new ArrayList<>();

    public CustomCommandManager(MiaoMenu plugin, ActionRegistry actionRegistry) {
        this.plugin = plugin;
        this.actionRegistry = actionRegistry;
        this.commandMap = plugin.getServer().getCommandMap();
    }

    public synchronized void reload() {
        unregisterAll();
        File file = new File(plugin.getDataFolder(), "commands.yml");
        if (!file.exists()) {
            return;
        }
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection commands = configuration.getConfigurationSection("commands");
        if (commands == null) {
            return;
        }
        for (String configuredName : commands.getKeys(false)) {
            ConfigurationSection section = commands.getConfigurationSection(configuredName);
            if (section == null || !section.getBoolean("enabled", true)) {
                continue;
            }
            String name = configuredName.toLowerCase(Locale.ROOT);
            if (!COMMAND_NAME.matcher(name).matches()) {
                plugin.getLogger().warning("Ignoring invalid custom command name: " + configuredName);
                continue;
            }
            List<String> actions = section.getStringList("actions");
            if (actions.isEmpty()) {
                plugin.getLogger().warning("Ignoring custom command without actions: " + configuredName);
                continue;
            }
            YamlCommand command = new YamlCommand(
                    name,
                    section.getString("description", "MiaoMenu custom command"),
                    section.getString("usage", "/" + name),
                    section.getStringList("aliases"),
                    section.getString("permission"),
                    section.getString("permission_message"),
                    actions,
                    section.getStringList("tab_complete")
            );
            boolean primaryNameRegistered = commandMap.register(name, plugin.getName().toLowerCase(Locale.ROOT), command);
            if (!primaryNameRegistered) {
                plugin.getLogger().warning("Custom command /" + name
                        + " conflicted with another plugin; registered under /miaomenu:" + name);
            }
            registeredCommands.add(command);
        }
    }

    public synchronized void shutdown() {
        unregisterAll();
    }

    private void unregisterAll() {
        for (Command command : registeredCommands) {
            command.unregister(commandMap);
            commandMap.getKnownCommands().entrySet().removeIf(entry -> entry.getValue() == command);
        }
        registeredCommands.clear();
    }

    private final class YamlCommand extends Command {
        private final String permission;
        private final String permissionMessage;
        private final List<String> actions;
        private final List<String> suggestions;

        private YamlCommand(
                String name,
                String description,
                String usage,
                List<String> aliases,
                String permission,
                String permissionMessage,
                List<String> actions,
                List<String> suggestions
        ) {
            super(name, description, usage, aliases);
            this.permission = permission;
            this.permissionMessage = permissionMessage;
            this.actions = List.copyOf(actions);
            this.suggestions = List.copyOf(suggestions);
        }

        @Override
        public boolean execute(
                @NotNull CommandSender sender,
                @NotNull String commandLabel,
                @NotNull String @NotNull [] args
        ) {
            if (permission != null && !permission.isBlank() && !sender.hasPermission(permission)) {
                sender.sendMessage(permissionMessage != null && !permissionMessage.isBlank()
                        ? permissionMessage
                        : Lang.get("message.no-permission"));
                return true;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Lang.get("message.players-only"));
                return true;
            }
            for (String action : actions) {
                String withArguments = applyArguments(action, args);
                actionRegistry.dispatch(player, PlaceholderUtils.parse(player, withArguments, plugin));
            }
            return true;
        }

        @Override
        public @NotNull List<String> tabComplete(
                @NotNull CommandSender sender,
                @NotNull String alias,
                @NotNull String @NotNull [] args
        ) {
            if ((permission != null && !permission.isBlank() && !sender.hasPermission(permission))
                    || !(sender instanceof Player player)
                    || suggestions.isEmpty()) {
                return Collections.emptyList();
            }
            String current = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
            return suggestions.stream()
                    .map(suggestion -> PlaceholderUtils.parse(player, suggestion, plugin))
                    .filter(suggestion -> suggestion.toLowerCase(Locale.ROOT).startsWith(current))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }
    }

    static String applyArguments(String action, String[] args) {
        String result = action.replace("%args%", String.join(" ", args));
        for (int index = 0; index < args.length; index++) {
            String number = Integer.toString(index + 1);
            result = result.replace("%arg_" + number + "%", args[index]);
            result = result.replace("{arg:" + number + "}", args[index]);
        }
        return result;
    }
}

package com.fluxcraft.MiaoMenu.commands.impl;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.fluxcraft.MiaoMenu.commands.PluginCommand;
import com.fluxcraft.MiaoMenu.utils.Lang;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class OpenCommand implements PluginCommand {
    private static final String PERMISSION = "dgeysermenu.use";

    private final MiaoMenu plugin;

    public OpenCommand(MiaoMenu plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(Lang.get("message.no-permission"));
            return;
        }
        if (args.length < 1) {
            sender.sendMessage(Lang.get("message.usage-open")
                    .replace("{0}", Lang.get("command.main-alias")));
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Lang.get("message.players-only"));
            return;
        }
        plugin.openSmartMenu(player, args[0]);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length != 1 || !sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        String typed = args[0].toLowerCase(Locale.ROOT);
        TreeSet<String> names = new TreeSet<>();
        names.addAll(plugin.getJavaMenuManager().getMenus().keySet());
        names.addAll(plugin.getBedrockMenuManager().getMenus().keySet());
        if (plugin.getDialogMenuManager() != null) {
            names.addAll(plugin.getDialogMenuManager().getMenus().keySet());
        }
        return names.stream()
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(typed))
                .collect(Collectors.toList());
    }

    @Override
    public boolean hasAccess(CommandSender sender) {
        return sender.hasPermission(PERMISSION);
    }

    @Override
    public String errorMessageKey() {
        return "open.error";
    }
}

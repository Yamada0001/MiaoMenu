package com.fluxcraft.MiaoMenu.commands.impl;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.fluxcraft.MiaoMenu.commands.PluginCommand;
import com.fluxcraft.MiaoMenu.utils.Lang;
import org.bukkit.command.CommandSender;

public class ReloadCommand implements PluginCommand {
    private final MiaoMenu plugin;

    public ReloadCommand(MiaoMenu plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dgeysermenu.reload")) {
            sender.sendMessage(Lang.get("message.no-permission"));
            return;
        }
        plugin.getConfigManager().loadConfig();
        Lang.reload();
        plugin.getJavaMenuManager().loadAllMenus();
        plugin.getBedrockMenuManager().loadAllMenus();
        plugin.getProxyManager().reload();

        sender.sendMessage(Lang.get("message.reloaded"));
    }
}

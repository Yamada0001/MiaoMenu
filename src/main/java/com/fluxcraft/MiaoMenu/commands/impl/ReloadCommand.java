package com.fluxcraft.MiaoMenu.commands.impl;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.fluxcraft.MiaoMenu.commands.PluginCommand;
import com.fluxcraft.MiaoMenu.utils.Lang;
import org.bukkit.command.CommandSender;

public class ReloadCommand implements PluginCommand {
    private static final String PERMISSION = "dgeysermenu.reload";

    private final MiaoMenu plugin;

    public ReloadCommand(MiaoMenu plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(Lang.get("message.no-permission"));
            return;
        }
        // 与文件热重载共用同一条重载链路，避免两条路径行为漂移。
        plugin.reloadAll();
        sender.sendMessage(Lang.get("message.reloaded"));
    }

    @Override
    public boolean hasAccess(CommandSender sender) {
        return sender.hasPermission(PERMISSION);
    }

    @Override
    public String errorMessageKey() {
        return "message.reload-failed";
    }
}

package com.fluxcraft.MiaoMenu.commands.impl;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.fluxcraft.MiaoMenu.utils.Lang;

public class GetMenuClockCommand implements CommandExecutor {
    private static final String PERMISSION = "dgeysermenu.admin";

    private final MiaoMenu plugin;

    public GetMenuClockCommand(MiaoMenu plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String @NotNull [] args
    ) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Lang.get("message.players-only"));
            return true;
        }
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(Lang.get("message.no-permission"));
            return true;
        }
        if (!plugin.getMenuClockManager().isEnabled()) {
            sender.sendMessage(Lang.get("message.requirement-denied"));
            return true;
        }
        plugin.getMenuClockManager().giveClockToPlayer(player);
        return true;
    }
}

package com.fluxcraft.MiaoMenu.commands;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface PluginCommand {
    void execute(CommandSender sender, String[] args);

    default @Nullable List<String> tabComplete(CommandSender sender, String[] args) {
        return null;
    }

    /** 该子命令是否对指定发送者可见（用于 tab 补全与帮助列表过滤）。 */
    default boolean hasAccess(CommandSender sender) {
        return true;
    }

    /** 该子命令失败时使用的错误提示键。 */
    default String errorMessageKey() {
        return "open.error";
    }
}

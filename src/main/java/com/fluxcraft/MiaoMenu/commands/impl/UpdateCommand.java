package com.fluxcraft.MiaoMenu.commands.impl;

import java.util.List;

import org.bukkit.command.CommandSender;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.fluxcraft.MiaoMenu.commands.PluginCommand;
import com.fluxcraft.MiaoMenu.update.UpdateChecker;
import com.fluxcraft.MiaoMenu.utils.Lang;

/**
 * {@code /<main> update}：手动触发一次更新检查。
 *
 * <p>检查在异步线程完成，本命令只负责把结果回给发送者；
 * 不会下载或替换任何文件。</p>
 */
public class UpdateCommand implements PluginCommand {
    private static final String PERMISSION = UpdateChecker.PERMISSION;

    private final MiaoMenu plugin;

    public UpdateCommand(MiaoMenu plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(Lang.get("message.no-permission"));
            return;
        }
        UpdateChecker checker = plugin.getUpdateChecker();
        if (checker == null) {
            sender.sendMessage(Lang.get("update.disabled"));
            return;
        }
        checker.checkNow(sender);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return List.of();
    }

    @Override
    public boolean hasAccess(CommandSender sender) {
        return sender.hasPermission(PERMISSION);
    }

    @Override
    public String errorMessageKey() {
        return "update.error";
    }
}

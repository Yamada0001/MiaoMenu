package com.fluxcraft.MiaoMenu.menu.action.impl;

import java.util.regex.Pattern;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.fluxcraft.MiaoMenu.constants.Constants;
import com.fluxcraft.MiaoMenu.proxy.ProxyManager;
import com.fluxcraft.MiaoMenu.utils.Lang;

public class PlayerAction implements com.fluxcraft.MiaoMenu.menu.action.MenuAction {
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern SERVER_NAME = Pattern.compile("[A-Za-z0-9_.-]{1,64}");

    @Override
    public void execute(Player player, String content, Plugin plugin) {
        if (content == null || content.isEmpty()) {
            return;
        }
        String cmd = Constants.stripLeadingSlash(content);

        if (plugin instanceof MiaoMenu miaoMenu) {
            String[] parts = WHITESPACE.split(cmd, 2);
            if (parts.length > 1 && parts[0].equalsIgnoreCase("server")) {
                handleServerSwitch(player, miaoMenu, parts[1].trim());
                return;
            }
        }

        player.performCommand(cmd);
    }

    /**
     * 处理 {@code [player] server <name>}：跨服失败时必须给出明确反馈，
     * 而不是回落到本地命令产生一条"未知命令"。
     */
    private void handleServerSwitch(Player player, MiaoMenu miaoMenu, String serverName) {
        if (serverName.isEmpty() || !SERVER_NAME.matcher(serverName).matches()) {
            player.sendMessage(Lang.get("message.proxy-invalid-server").replace("{0}", serverName));
            return;
        }
        ProxyManager proxyManager = miaoMenu.getProxyManager();
        if (proxyManager == null || !proxyManager.isProxyConnected()) {
            player.sendMessage(Lang.get("message.proxy-unavailable"));
            return;
        }
        if (proxyManager.sendServerCommand(player, serverName)) {
            player.sendMessage(Lang.get("message.proxy-connecting").replace("{0}", serverName));
            return;
        }
        player.sendMessage(Lang.get("message.proxy-failed").replace("{0}", serverName));
    }
}

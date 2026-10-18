package com.fluxcraft.MiaoMenu.menu.action.impl;

import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.fluxcraft.MiaoMenu.constants.Constants;
import com.fluxcraft.MiaoMenu.foliacall.FoliaFactory;

/**
 * 以控制台身份执行命令。
 *
 * <p>Folia 下命令必须投递到全局区域线程执行（Bukkit 的命令分发本身不是
 * 区域化的），因此该动作会比同一动作链中的其它动作延后一个 tick。
 * 这个时序差异已在 README 中说明。</p>
 */
public class CmdAction implements com.fluxcraft.MiaoMenu.menu.action.MenuAction {
    @Override
    public void execute(Player player, String content, Plugin plugin) {
        if (content == null || content.isEmpty()) {
            return;
        }
        String cmd = Constants.stripLeadingSlash(content);
        if (cmd.isBlank()) {
            return;
        }
        if (FoliaFactory.isFolia()) {
            Bukkit.getGlobalRegionScheduler().execute(plugin, () -> run(cmd, player, plugin));
            return;
        }
        run(cmd, player, plugin);
    }

    private void run(String cmd, Player player, Plugin plugin) {
        try {
            boolean handled = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            if (!handled) {
                plugin.getLogger().warning("Console command was not handled: /" + cmd
                        + " (triggered by " + player.getName() + ")");
            }
        } catch (RuntimeException | LinkageError e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Console command failed: /" + cmd + " (triggered by " + player.getName() + ")", e);
        }
    }
}

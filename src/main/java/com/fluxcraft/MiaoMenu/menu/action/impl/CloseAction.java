package com.fluxcraft.MiaoMenu.menu.action.impl;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * 关闭当前界面。
 *
 * <p>Java 版玩家关闭背包即可；基岩版玩家面前是 Cumulus 表单，
 * 需要走 Floodgate 的 {@code closeForm(UUID)} 才能真正关掉。</p>
 */
public class CloseAction implements com.fluxcraft.MiaoMenu.menu.action.MenuAction {
    private static volatile Method closeFormMethod;
    private static volatile Object floodgateApi;
    private static volatile boolean floodgateUnavailable;

    @Override
    public void execute(Player player, String content, Plugin plugin) {
        player.closeInventory();
        closeBedrockForm(player, plugin);
    }

    private void closeBedrockForm(Player player, Plugin plugin) {
        if (floodgateUnavailable) {
            return;
        }
        try {
            if (closeFormMethod == null) {
                Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                floodgateApi = apiClass.getMethod("getInstance").invoke(null);
                closeFormMethod = apiClass.getMethod("closeForm", UUID.class);
            }
            closeFormMethod.invoke(floodgateApi, player.getUniqueId());
        } catch (ClassNotFoundException e) {
            // 未安装 Floodgate：基岩版玩家不存在，属正常情况。
            floodgateUnavailable = true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            floodgateUnavailable = true;
            if (plugin != null) {
                plugin.getLogger().log(Level.FINE, "Could not close the Floodgate form", e);
            }
        }
    }
}

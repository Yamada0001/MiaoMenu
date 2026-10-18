package com.fluxcraft.MiaoMenu.update;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * 玩家进服时补发一次更新提示。
 *
 * <p>只在"已经检查到新版本"且玩家拥有 {@link UpdateChecker#PERMISSION} 时提示，
 * 同一玩家每次运行最多提示一次。</p>
 */
public final class UpdateNotifyListener implements Listener {
    private final UpdateChecker updateChecker;

    public UpdateNotifyListener(UpdateChecker updateChecker) {
        this.updateChecker = updateChecker;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        updateChecker.notifyOnJoin(event.getPlayer());
    }
}

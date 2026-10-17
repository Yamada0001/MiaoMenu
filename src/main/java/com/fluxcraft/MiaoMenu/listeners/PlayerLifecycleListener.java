package com.fluxcraft.MiaoMenu.listeners;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.fluxcraft.MiaoMenu.foliacall.FoliaFactory;
import com.fluxcraft.MiaoMenu.managers.MenuClockManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class PlayerLifecycleListener implements Listener {
    private final MiaoMenu plugin;
    private final MenuClockManager clockManager;
    public PlayerLifecycleListener(MiaoMenu plugin, MenuClockManager clockManager) {
        this.plugin = plugin;
        this.clockManager = clockManager;
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (clockManager.isEnabled() && plugin.getConfig().getBoolean("settings.menu-clock.give-on-join", true)) {
            Player player = event.getPlayer();
            FoliaFactory.getAdapter().runTaskLaterForEntity(plugin, player, () -> {
                if (player.isOnline()) {
                    clockManager.giveClockToPlayer(player);
                }
            }, MiaoMenu.JOIN_DELAY_TICKS);
        }
    }
    // 玩家离线后立刻回收限流窗口，避免离线玩家记录常驻内存
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.getInteractionRateLimiter().clear(event.getPlayer().getUniqueId());
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onKick(PlayerKickEvent event) {
        plugin.getInteractionRateLimiter().clear(event.getPlayer().getUniqueId());
    }
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        clockManager.removeClockFromDrops(event.getDrops());
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (clockManager.isEnabled()) {
            FoliaFactory.getAdapter().runTaskLaterForEntity(plugin, player, () -> {
                if (player.isOnline()) {
                    clockManager.ensureClock(player);
                }
            }, 1L);
        }
    }
}

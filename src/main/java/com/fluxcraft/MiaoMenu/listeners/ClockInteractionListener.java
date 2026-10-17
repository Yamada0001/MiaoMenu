package com.fluxcraft.MiaoMenu.listeners;

import com.fluxcraft.MiaoMenu.managers.MenuClockManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class ClockInteractionListener implements Listener {
    private final MenuClockManager clockManager;

    public ClockInteractionListener(MenuClockManager clockManager) {
        this.clockManager = clockManager;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (item == null) return;

        if (clockManager.isEnabled() && clockManager.isMenuClock(item)) {
            event.setCancelled(true);
            clockManager.openMenuWithClock(event.getPlayer());
        }
    }
}

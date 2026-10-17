package com.fluxcraft.MiaoMenu.javamenu;

import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.fluxcraft.MiaoMenu.menu.action.ActionRegistry;
import com.fluxcraft.MiaoMenu.utils.PlaceholderUtils;

public class JavaMenuListener implements Listener {
    private final MiaoMenu plugin;
    private final ActionRegistry actionRegistry;

    public JavaMenuListener(MiaoMenu plugin, ActionRegistry actionRegistry) {
        this.plugin = plugin;
        this.actionRegistry = actionRegistry;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!(topInventory.getHolder() instanceof JavaMenu.MenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= topInventory.getSize()) {
            return;
        }
        if (!plugin.getInteractionRateLimiter().allow(player.getUniqueId())) {
            return;
        }
        JavaMenu.MenuItem item = holder.getMenu().getItem(rawSlot);
        if (item == null) {
            return;
        }
        if (item.isLocked(player, plugin.getRequirementService(), holder.getMenu().getName(), holder.getMenu().getRequirementBlocks())) {
            String lockMsg = item.getLockMessage(player, plugin.getRequirementService(), holder.getMenu().getName(), holder.getMenu().getRequirementBlocks());
            if (lockMsg != null && !lockMsg.isBlank()) {
                player.sendMessage(lockMsg);
            }
            return;
        }
        if (event.isLeftClick()) {
            List<String> commands = item.getLeftClickCommands();
            if (!commands.isEmpty()) {
                executeActions(player, commands);
            }
        } else if (event.isRightClick()) {
            List<String> commands = item.getRightClickCommands();
            if (!commands.isEmpty()) {
                executeActions(player, commands);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof JavaMenu.MenuHolder) {
            event.setCancelled(true);
        }
    }

    private void executeActions(Player player, List<String> rawCommands) {
        for (String rawCmd : rawCommands) {
            if (rawCmd == null || rawCmd.trim().isEmpty()) {
                continue;
            }
            String parsed = PlaceholderUtils.parse(player, rawCmd, plugin);
            actionRegistry.dispatch(player, parsed);
        }
    }
}

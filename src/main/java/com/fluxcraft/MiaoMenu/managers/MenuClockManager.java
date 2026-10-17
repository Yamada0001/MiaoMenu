package com.fluxcraft.MiaoMenu.managers;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.fluxcraft.MiaoMenu.utils.Lang;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import java.util.Map;

public class MenuClockManager {
    private final MiaoMenu plugin;
    private final NamespacedKey clockKey;
    public MenuClockManager(MiaoMenu plugin, NamespacedKey clockKey) {
        this.plugin = plugin;
        this.clockKey = clockKey;
    }
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("settings.menu-clock.enabled", true);
    }
    public ItemStack createClock() {
        ItemStack clock = new ItemStack(Material.CLOCK);
        ItemMeta meta = clock.getItemMeta();
        if (meta != null) {
            String rawName = Lang.get("menu.clock.name");
            Component nameComponent = Component.text(rawName)
                    .decoration(TextDecoration.ITALIC, false);
            meta.displayName(nameComponent);
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(clockKey, PersistentDataType.BYTE, (byte) 1);
            clock.setItemMeta(meta);
        }
        return clock;
    }
    public boolean isMenuClock(ItemStack item) {
        if (item == null || item.getType() != Material.CLOCK) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(clockKey, PersistentDataType.BYTE);
    }
    public boolean playerHasClock(Player player) {
        return containsMenuClock(player.getInventory().getStorageContents())
                || containsMenuClock(player.getInventory().getArmorContents())
                || containsMenuClock(player.getInventory().getExtraContents())
                || isMenuClock(player.getInventory().getItemInOffHand());
    }
    private boolean containsMenuClock(ItemStack[] items) {
        for (ItemStack item : items) {
            if (isMenuClock(item)) return true;
        }
        return false;
    }
    public boolean playerHasNoClock(Player player) {
        return !playerHasClock(player);
    }
    public void giveClockToPlayer(Player player) {
        if (isEnabled() && playerHasNoClock(player)) {
            ItemStack clock = createClock();
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(clock);
            if (!leftovers.isEmpty()) {
                Location loc = player.getLocation();
                for (ItemStack leftover : leftovers.values()) {
                    player.getWorld().dropItemNaturally(loc, leftover);
                }
            }
            player.sendMessage(Lang.get("menu.clock.given"));
        }
    }
    public void openMenuWithClock(Player player) {
        if (!isEnabled()) {
            return;
        }
        String defaultMenu = plugin.getConfig().getString("settings.default-menu", "test");
        plugin.openSmartMenu(player, defaultMenu);
    }
    public void removeClockFromDrops(java.util.List<ItemStack> drops) {
        drops.removeIf(this::isMenuClock);
    }
    public void ensureClock(Player player) {
        giveClockToPlayer(player);
    }
}

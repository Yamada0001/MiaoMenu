package com.fluxcraft.MiaoMenu.javamenu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.fluxcraft.MiaoMenu.constants.Constants;
import com.fluxcraft.MiaoMenu.integration.ItemResolver;
import com.fluxcraft.MiaoMenu.menu.requirement.ConditionGroup;
import com.fluxcraft.MiaoMenu.menu.requirement.RequirementBlock;
import com.fluxcraft.MiaoMenu.menu.requirement.RequirementResult;
import com.fluxcraft.MiaoMenu.menu.requirement.RequirementService;
import com.fluxcraft.MiaoMenu.utils.Lang;
import com.fluxcraft.MiaoMenu.utils.PlaceholderUtils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class JavaMenu {
    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.legacySection();
    private final String name;
    private final String title;
    private final int size;
    private final MiaoMenu plugin;
    private final ItemResolver itemResolver;
    private final RequirementService requirementService;
    private final List<MenuItem> items;
    private final Map<Integer, MenuItem> itemsBySlot;
    private final Map<String, RequirementBlock> requirementBlocks;
    private final List<Map<?, ?>> viewRequirements;
    private final String denyMessage;
    private final String fallbackMenu;

    public JavaMenu(
            String name,
            FileConfiguration config,
            MiaoMenu plugin,
            ItemResolver itemResolver,
            RequirementService requirementService
    ) {
        this.name = name;
        this.plugin = plugin;
        this.itemResolver = itemResolver;
        this.requirementService = requirementService;
        this.title = config.getString("menu_title", config.getString("title", "&6Menu"));
        this.size = Math.clamp(config.getInt("rows", Constants.Config.DEFAULT_MENU_ROWS), Constants.Config.INVENTORY_MIN_ROWS,
                Constants.Config.INVENTORY_MAX_ROWS
        ) * Constants.Config.INVENTORY_ROW_SIZE;
        this.requirementBlocks = requirementService.loadBlocks(name, config.getConfigurationSection("requirement_blocks"));
        this.viewRequirements = requirementService.readRequirementList(
                config.get("view_requirement.requirements"),
                name,
                "view_requirement.requirements"
        );
        this.denyMessage = config.getString("view_requirement.deny_message");
        this.fallbackMenu = config.getString("view_requirement.fallback_menu");
        requirementService.validateRequirementBlocks(name, requirementBlocks);
        requirementService.validateRequirements(name, "view_requirement.requirements", requirementBlocks, viewRequirements);
        this.items = new ArrayList<>();
        this.itemsBySlot = new HashMap<>();
        loadItems(config);
    }

    private void loadItems(FileConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("items");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            String path = "items." + key;
            List<String> leftCmds = config.getStringList(path + ".left_click_commands");
            List<String> rightCmds = config.getStringList(path + ".right_click_commands");
            ConditionGroup conditionGroup = loadConditionGroup(config, path);
            requirementService.validateConditionGroup(name, path + ".conditions", requirementBlocks, conditionGroup);
            String lockMessage = config.getString(path + ".lock_message");
            MenuItem item = new MenuItem(
                    config.getInt(path + ".slot", 0),
                    config.getString(path + ".material", "STONE"),
                    config.getInt(path + ".custom_model_data", 0),
                    config.getString(path + ".display_name", "&fItem"),
                    config.getStringList(path + ".lore"),
                    leftCmds,
                    rightCmds,
                    conditionGroup,
                    lockMessage
            );
            items.add(item);
            itemsBySlot.put(item.getSlot(), item);
        }
    }

    private ConditionGroup loadConditionGroup(FileConfiguration config, String path) {
        if (config.contains(path + ".conditions")) {
            Object conditionsObj = config.get(path + ".conditions");
            if (conditionsObj instanceof Map<?, ?> map) {
                return ConditionGroup.fromYaml(map);
            }
            if (conditionsObj instanceof ConfigurationSection section) {
                return ConditionGroup.fromYaml(section.getValues(false));
            }
            throw new IllegalArgumentException(path + ".conditions must be a map");
        }
        if (config.contains(path + ".requirements") || config.contains(path + ".operator") || config.contains(path + ".children")) {
            ConfigurationSection itemSection = config.getConfigurationSection(path);
            if (itemSection != null) {
                return ConditionGroup.fromYaml(itemSection.getValues(false));
            }
        }
        List<Map<?, ?>> legacyConditions = requirementService.readRequirementList(
                config.get(path + ".item_conditions"),
                name,
                path + ".item_conditions"
        );
        return ConditionGroup.fromLegacyConditions(legacyConditions);
    }

    public void open(Player player) {
        String parsedTitleString = PlaceholderUtils.parse(player, title, plugin);
        if (parsedTitleString.length() > Constants.Config.TITLE_MAX_LENGTH) {
            parsedTitleString = parsedTitleString.substring(0, Constants.Config.TITLE_MAX_LENGTH);
        }
        Component titleComponent = LEGACY_SECTION.deserialize(parsedTitleString);
        MenuHolder holder = new MenuHolder(this);
        Inventory inventory = Bukkit.createInventory(holder, size, titleComponent);
        holder.setInventory(inventory);
        items.forEach(item -> {
            ItemStack stack = item.createItemStack(player, plugin, itemResolver, requirementService, name, requirementBlocks);
            if (stack != null) {
                inventory.setItem(item.getSlot(), stack);
            }
        });
        player.openInventory(inventory);
    }

    public RequirementResult checkViewRequirement(Player player) {
        return requirementService.checkViewRequirement(player, name, requirementBlocks, viewRequirements, denyMessage, fallbackMenu);
    }

    public MenuItem getItem(int slot) {
        return itemsBySlot.get(slot);
    }

    public String getName() {
        return name;
    }

    public Map<String, RequirementBlock> getRequirementBlocks() {
        return requirementBlocks;
    }

    public static class MenuHolder implements org.bukkit.inventory.InventoryHolder {
        private Inventory inventory;
        private final JavaMenu menu;

        public MenuHolder(JavaMenu menu) {
            this.menu = menu;
        }

        @Override
        @NotNull
        public Inventory getInventory() {
            return inventory;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        public JavaMenu getMenu() {
            return menu;
        }
    }

    public static class MenuItem {
        private static final Material LOCKED_MATERIAL = Material.GRAY_STAINED_GLASS_PANE;

        private final int slot;
        private final String name;
        private final String material;
        private final int customModelData;
        private final List<String> lore;
        private final List<String> leftClickCommands;
        private final List<String> rightClickCommands;
        private final ConditionGroup conditionGroup;
        private final String lockMessage;

        public MenuItem(
                int slot,
                String material,
                int customModelData,
                String name,
                List<String> lore,
                List<String> leftClickCommands,
                List<String> rightClickCommands,
                ConditionGroup conditionGroup,
                String lockMessage
        ) {
            this.slot = slot;
            this.material = material;
            this.customModelData = customModelData;
            this.name = name;
            this.lore = lore;
            this.leftClickCommands = leftClickCommands != null ? leftClickCommands : new ArrayList<>();
            this.rightClickCommands = rightClickCommands != null ? rightClickCommands : new ArrayList<>();
            this.conditionGroup = conditionGroup != null ? conditionGroup : ConditionGroup.fromLegacyConditions(null);
            this.lockMessage = lockMessage;
        }

        public boolean isLocked(Player player, RequirementService requirementService, String menuName, Map<String, RequirementBlock> requirementBlocks) {
            RequirementResult requirementResult = evaluateRequirement(player, requirementService, menuName, requirementBlocks);
            return requirementResult != null && !requirementResult.allowed();
        }

        public String getLockMessage(Player player, RequirementService requirementService, String menuName, Map<String, RequirementBlock> requirementBlocks) {
            RequirementResult requirementResult = evaluateRequirement(player, requirementService, menuName, requirementBlocks);
            return resolveLockMessage(player, requirementResult);
        }

        public ItemStack createItemStack(
                Player player,
                MiaoMenu plugin,
                @Nullable ItemResolver itemResolver,
                RequirementService requirementService,
                String menuName,
                Map<String, RequirementBlock> requirementBlocks
        ) {
            RequirementResult requirementResult = evaluateRequirement(player, requirementService, menuName, requirementBlocks);
            if (requirementResult != null && !requirementResult.allowed()) {
                return createLockedItemStack(player, plugin, resolveLockMessage(player, requirementResult));
            }
            return createUnlockedItemStack(player, plugin, itemResolver);
        }

        private RequirementResult evaluateRequirement(
                Player player,
                RequirementService requirementService,
                String menuName,
                Map<String, RequirementBlock> requirementBlocks
        ) {
            if (conditionGroup.requirements().isEmpty() && conditionGroup.children().isEmpty()) {
                return null;
            }
            return requirementService.evaluateGroup(player, menuName, requirementBlocks, conditionGroup);
        }

        private String resolveLockMessage(Player player, @Nullable RequirementResult requirementResult) {
            if (requirementResult == null || requirementResult.allowed()) {
                return null;
            }
            if (requirementResult.denyMessage() != null && !requirementResult.denyMessage().isBlank()) {
                return PlaceholderUtils.parse(player, requirementResult.denyMessage(), null);
            }
            if (lockMessage != null && !lockMessage.isBlank()) {
                return PlaceholderUtils.parse(player, lockMessage, null);
            }
            return Lang.get("message.item-locked");
        }

        private ItemStack createLockedItemStack(Player player, MiaoMenu plugin, String resolvedLockMessage) {
            ItemStack item = new ItemStack(LOCKED_MATERIAL);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                String lockedName = "&7" + PlaceholderUtils.parse(player, name, plugin) + Lang.get("message.item-locked-suffix");
                meta.displayName(LEGACY_SECTION.deserialize(lockedName));
                List<Component> loreComponents = new ArrayList<>();
                lore.forEach(line -> loreComponents.add(
                        LEGACY_SECTION.deserialize("&8" + PlaceholderUtils.parse(player, line, plugin))
                ));
                loreComponents.add(Component.empty());
                String lockLore = resolvedLockMessage != null ? resolvedLockMessage : Lang.get("message.item-locked");
                loreComponents.add(LEGACY_SECTION.deserialize("&c" + lockLore));
                meta.lore(loreComponents);
                item.setItemMeta(meta);
            }
            return item;
        }

        @SuppressWarnings("deprecation")
        private ItemStack createUnlockedItemStack(Player player, MiaoMenu plugin, @Nullable ItemResolver itemResolver) {
            ItemStack item;
            if (itemResolver != null) {
                item = itemResolver.resolve(material, customModelData);
            } else {
                Material resolvedMaterial = Material.matchMaterial(material);
                item = new ItemStack(resolvedMaterial != null ? resolvedMaterial : Material.STONE);
            }
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(LEGACY_SECTION.deserialize(PlaceholderUtils.parse(player, name, plugin)));
                List<Component> loreComponents = new ArrayList<>();
                lore.forEach(line -> loreComponents.add(
                        LEGACY_SECTION.deserialize(PlaceholderUtils.parse(player, line, plugin))
                ));
                meta.lore(loreComponents);
                if (customModelData > 0) {
                    meta.setCustomModelData(customModelData);
                }
                item.setItemMeta(meta);
            }
            return item;
        }

        public int getSlot() {
            return slot;
        }

        public List<String> getLeftClickCommands() {
            return leftClickCommands;
        }

        public List<String> getRightClickCommands() {
            return rightClickCommands;
        }
    }
}

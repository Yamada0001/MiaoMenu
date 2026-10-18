package com.fluxcraft.MiaoMenu.dialogmenu;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.fluxcraft.MiaoMenu.dialogmenu.plus.DialogYaml;
import com.fluxcraft.MiaoMenu.integration.ItemResolver;
import com.fluxcraft.MiaoMenu.managers.SoundsClock;
import com.fluxcraft.MiaoMenu.menu.action.ActionRegistry;
import com.fluxcraft.MiaoMenu.menu.requirement.RequirementFeedbackHandler;
import com.fluxcraft.MiaoMenu.menu.requirement.RequirementResult;
import com.fluxcraft.MiaoMenu.menu.requirement.RequirementService;
import com.fluxcraft.MiaoMenu.utils.Lang;
import com.fluxcraft.MiaoMenu.utils.MenuUtils;

public final class DialogMenuManager {
    private final MiaoMenu plugin;
    private final ItemResolver itemResolver;
    private final ActionRegistry actionRegistry;
    private final SoundsClock soundsClock;
    private final RequirementService requirementService;
    private final RequirementFeedbackHandler requirementFeedbackHandler;
    private final DialogYaml dialogYaml;
    private volatile Map<String, DialogMenu> menus = Collections.emptyMap();

    public DialogMenuManager(
            MiaoMenu plugin,
            ItemResolver itemResolver,
            ActionRegistry actionRegistry,
            SoundsClock soundsClock,
            RequirementService requirementService,
            RequirementFeedbackHandler requirementFeedbackHandler
    ) {
        this.plugin = plugin;
        this.itemResolver = itemResolver;
        this.actionRegistry = actionRegistry;
        this.soundsClock = soundsClock;
        this.requirementService = requirementService;
        this.requirementFeedbackHandler = requirementFeedbackHandler;
        this.dialogYaml = new DialogYaml(requirementService);
    }

    public void loadAllMenus() {
        Map<String, DialogMenu> previous = this.menus;
        Map<String, DialogMenu> newMenus = new ConcurrentHashMap<>();
        File directory = new File(plugin.getDataFolder(), "dialog_menus");
        if (!directory.exists() && !directory.mkdirs()) {
            plugin.getLogger().warning("Could not create the dialog_menus directory.");
            return;
        }
        File[] files = directory.listFiles(file -> file.isFile()
                && file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".yml"));
        if (files == null) {
            plugin.getLogger().warning("Could not read the dialog_menus directory.");
            return;
        }
        if (files.length == 0) {
            if (previous.isEmpty()) {
                return;
            }
            plugin.getLogger().warning("The dialog_menus directory is empty; keeping the previously loaded dialogs.");
            return;
        }
        for (File file : files) {
            String fileName = file.getName();
            String name = fileName.substring(0, fileName.length() - 4).toLowerCase(java.util.Locale.ROOT);
            try {
                FileConfiguration config = loadStrictly(file);
                newMenus.put(name, new DialogMenu(
                        dialogYaml.parse(name, config),
                        plugin,
                        itemResolver,
                        actionRegistry,
                        requirementService
                ));
            } catch (IOException | InvalidConfigurationException | RuntimeException e) {
                DialogMenu fallback = previous.get(name);
                if (fallback != null) {
                    newMenus.put(name, fallback);
                }
                plugin.getLogger().log(Level.WARNING, "Could not load dialog menu " + fileName, e);
            }
        }
        if (newMenus.isEmpty()) {
            plugin.getLogger().severe("Every dialog_menus file failed to load; keeping the previously loaded dialogs.");
            return;
        }
        menus = newMenus;
    }

    private static FileConfiguration loadStrictly(File file) throws IOException, InvalidConfigurationException {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.load(file);
        return configuration;
    }

    public void openMenu(Player player, String menuName) {
        if (menuName == null) {
            player.sendMessage(Lang.get("message.menu-name-required"));
            return;
        }
        DialogMenu menu = menus.get(menuName.toLowerCase(java.util.Locale.ROOT));
        if (MenuUtils.handleMenuNotFound(plugin.getLogger(), player, menu, menuName)) {
            return;
        }
        RequirementResult requirementResult = menu.checkViewRequirement(player);
        if (MenuUtils.handleRequirementDenied(requirementFeedbackHandler, player, requirementResult)) {
            return;
        }
        soundsClock.playMenuOpenSound(player);
        try {
            menu.open(player);
        } catch (RuntimeException | LinkageError e) {
            player.sendMessage(Lang.get("open.error"));
            plugin.getLogger().log(Level.SEVERE, "Could not open dialog menu " + menuName, e);
        }
    }

    public boolean containsMenu(String menuName) {
        return menuName != null && menus.containsKey(menuName.toLowerCase(java.util.Locale.ROOT));
    }

    public Map<String, DialogMenu> getMenus() {
        return Collections.unmodifiableMap(menus);
    }
}

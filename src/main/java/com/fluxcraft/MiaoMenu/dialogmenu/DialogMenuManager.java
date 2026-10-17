package com.fluxcraft.MiaoMenu.dialogmenu;

import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import com.fluxcraft.MiaoMenu.MiaoMenu;
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
        Map<String, DialogMenu> newMenus = new ConcurrentHashMap<>();
        File directory = new File(plugin.getDataFolder(), "dialog_menus");
        if (!directory.exists() && !directory.mkdirs()) {
            plugin.getLogger().warning("Could not create the dialog_menus directory.");
            return;
        }
        File[] files = directory.listFiles((_, name) -> name.endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            try {
                String name = file.getName().substring(0, file.getName().length() - 4);
                FileConfiguration config = YamlConfiguration.loadConfiguration(file);
                newMenus.put(name, new DialogMenu(
                        dialogYaml.parse(name, config),
                        plugin,
                        itemResolver,
                        actionRegistry,
                        requirementService
                ));
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Could not load dialog menu " + file.getName(), e);
            }
        }
        menus = newMenus;
    }

    public void openMenu(Player player, String menuName) {
        DialogMenu menu = menus.get(menuName);
        if (MenuUtils.handleMenuNotFound(player, menu, menuName)) {
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
        return menus.containsKey(menuName);
    }

    public Map<String, DialogMenu> getMenus() {
        return Collections.unmodifiableMap(menus);
    }
}

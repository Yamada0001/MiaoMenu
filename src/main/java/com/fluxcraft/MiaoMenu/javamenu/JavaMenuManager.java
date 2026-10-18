package com.fluxcraft.MiaoMenu.javamenu;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.fluxcraft.MiaoMenu.integration.ItemResolver;
import com.fluxcraft.MiaoMenu.managers.SoundsClock;
import com.fluxcraft.MiaoMenu.menu.requirement.RequirementFeedbackHandler;
import com.fluxcraft.MiaoMenu.menu.requirement.RequirementResult;
import com.fluxcraft.MiaoMenu.menu.requirement.RequirementService;
import com.fluxcraft.MiaoMenu.utils.Lang;
import com.fluxcraft.MiaoMenu.utils.MenuUtils;

public class JavaMenuManager {
    private final MiaoMenu plugin;
    private final ItemResolver itemResolver;
    private final SoundsClock soundsClock;
    private final RequirementService requirementService;
    private final RequirementFeedbackHandler requirementFeedbackHandler;
    private volatile Map<String, JavaMenu> menus = Collections.emptyMap();

    public JavaMenuManager(
            MiaoMenu plugin,
            ItemResolver itemResolver,
            SoundsClock soundsClock,
            RequirementService requirementService,
            RequirementFeedbackHandler requirementFeedbackHandler
    ) {
        this.plugin = plugin;
        this.itemResolver = itemResolver;
        this.soundsClock = soundsClock;
        this.requirementService = requirementService;
        this.requirementFeedbackHandler = requirementFeedbackHandler;
    }

    /**
     * 重新加载 java_menus 目录。
     *
     * <p>容错策略：单个文件失败时保留旧实例；目录整体为空时不清空现有注册表，
     * 避免"保存一次坏文件"或"部署脚本短暂清空目录"导致全服菜单消失。</p>
     */
    public void loadAllMenus() {
        Map<String, JavaMenu> previous = this.menus;
        Map<String, JavaMenu> newMenus = new ConcurrentHashMap<>();
        File dir = new File(plugin.getDataFolder(), "java_menus");
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning(Lang.get("log.java-menu.directory-create-failed"));
            return;
        }
        File[] files = dir.listFiles(file -> file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) {
            plugin.getLogger().warning(Lang.get("log.java-menu.directory-read-failed"));
            return;
        }
        if (files.length == 0) {
            if (previous.isEmpty()) {
                return;
            }
            plugin.getLogger().warning(Lang.get("log.java-menu.directory-empty"));
            return;
        }

        for (File file : files) {
            String fileName = file.getName();
            String name = fileName.substring(0, fileName.length() - 4).toLowerCase(Locale.ROOT);
            try {
                FileConfiguration config = loadStrictly(file);
                newMenus.put(name, new JavaMenu(name, config, plugin, itemResolver, requirementService));
            } catch (IOException | InvalidConfigurationException | RuntimeException e) {
                JavaMenu fallback = previous.get(name);
                if (fallback != null) {
                    newMenus.put(name, fallback);
                }
                plugin.getLogger().log(
                        Level.WARNING,
                        Lang.get("log.java-menu.load-failed").replace("{0}", fileName),
                        e
                );
            }
        }

        if (newMenus.isEmpty()) {
            plugin.getLogger().severe(Lang.get("log.java-menu.all-failed"));
            return;
        }
        this.menus = newMenus;
    }

    /**
     * 显式加载 YAML，让语法错误抛出异常而不是静默返回空配置。
     */
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
        JavaMenu menu = menus.get(menuName.toLowerCase(Locale.ROOT));
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
            plugin.getLogger().log(Level.SEVERE, Lang.get("log.java-menu.open-failed").replace("{0}", menuName), e);
        }
    }

    public boolean containsMenu(String menuName) {
        return menuName != null && menus.containsKey(menuName.toLowerCase(Locale.ROOT));
    }

    public Map<String, JavaMenu> getMenus() {
        return Collections.unmodifiableMap(menus);
    }
}

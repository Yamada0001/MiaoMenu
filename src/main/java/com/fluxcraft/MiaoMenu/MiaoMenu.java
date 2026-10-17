package com.fluxcraft.MiaoMenu;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Stream;

import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.fluxcraft.MiaoMenu.bedrockmenu.BedrockMenuManager;
import com.fluxcraft.MiaoMenu.commands.CommandManager;
import com.fluxcraft.MiaoMenu.commands.impl.GetMenuClockCommand;
import com.fluxcraft.MiaoMenu.config.ConfigManager;
import com.fluxcraft.MiaoMenu.foliacall.FoliaFactory;
import com.fluxcraft.MiaoMenu.integration.ItemResolver;
import com.fluxcraft.MiaoMenu.javamenu.JavaMenuListener;
import com.fluxcraft.MiaoMenu.javamenu.JavaMenuManager;
import com.fluxcraft.MiaoMenu.listeners.ClockInteractionListener;
import com.fluxcraft.MiaoMenu.listeners.PlayerLifecycleListener;
import com.fluxcraft.MiaoMenu.listeners.PlayerLifecycleListener_Folia;
import com.fluxcraft.MiaoMenu.managers.HotReloadManager;
import com.fluxcraft.MiaoMenu.managers.MenuClockManager;
import com.fluxcraft.MiaoMenu.managers.SoundsClock;
import com.fluxcraft.MiaoMenu.menu.action.ActionRegistry;
import com.fluxcraft.MiaoMenu.menu.requirement.RequirementFeedbackHandler;
import com.fluxcraft.MiaoMenu.menu.requirement.RequirementService;
import com.fluxcraft.MiaoMenu.proxy.ProxyManager;
import com.fluxcraft.MiaoMenu.security.RateLimiter;
import com.fluxcraft.MiaoMenu.utils.Lang;

import cn.handyplus.lib.adapter.HandySchedulerUtil;

public final class MiaoMenu extends JavaPlugin {
    private static final int BSTATS_ID = 28979;
    public static final int JOIN_DELAY_TICKS = 20;
    private static final String MAIN_COMMAND = "dgeysermenu";
    private static final String CLOCK_COMMAND = "getmenuclock";

    private ConfigManager configManager;
    private JavaMenuManager javaMenuManager;
    private BedrockMenuManager bedrockMenuManager;
    private ActionRegistry actionRegistry;
    private CommandManager commandManager;
    private HotReloadManager hotReloadManager;
    private ProxyManager proxyManager;
    private RequirementService requirementService;
    private RateLimiter interactionRateLimiter;
    private MenuClockManager menuClockManager;
    private final ThreadLocal<Set<String>> menuOpenChain = ThreadLocal.withInitial(LinkedHashSet::new);
    private volatile Class<?> floodgateApiClass;
    private volatile Object floodgateApiInstance;
    private volatile Method floodgateIsPlayerMethod;
    private volatile boolean floodgateAvailable;

    @Override
    public void onEnable() {
        try {
            initializeCoreServices();
            initializeMenuSystems();
            registerListeners();
            registerCommands();
            initializeOptionalFeatures();
            getLogger().info(Lang.get("log.plugin.enabled").replace("{0}", getPluginMeta().getVersion()));
        } catch (RuntimeException e) {
            getLogger().log(Level.SEVERE, Lang.get("log.plugin.enable-failed"), e);
            throw e;
        }
    }

    @Override
    public void onDisable() {
        if (hotReloadManager != null) {
            hotReloadManager.shutdown();
        }
        if (interactionRateLimiter != null) {
            interactionRateLimiter.clearAll();
        }
        if (proxyManager != null) {
            proxyManager.shutdown();
        }
    }

    private void initializeCoreServices() {
        saveDefaultConfig();
        Lang.init(this);
        HandySchedulerUtil.init(this);
        floodgateAvailable = Bukkit.getPluginManager().isPluginEnabled("floodgate");
        configManager = new ConfigManager(this);
        configManager.loadConfig();
        Lang.reload();
        configManager.checkAndRefreshMenus();
        requirementService = new RequirementService(this);
        interactionRateLimiter = new RateLimiter(java.time.Duration.ofSeconds(2), 10);
    }

    private void initializeMenuSystems() {
        NamespacedKey clockKey = new NamespacedKey(this, "menu_clock");
        RequirementFeedbackHandler requirementFeedbackHandler = new RequirementFeedbackHandler(this);
        ItemResolver itemResolver = new ItemResolver(this, configManager.getCraftEngineFallbackMaterial());
        SoundsClock soundsClock = new SoundsClock(this);
        actionRegistry = new ActionRegistry(this);
        javaMenuManager = new JavaMenuManager(this, itemResolver, soundsClock, requirementService, requirementFeedbackHandler);
        bedrockMenuManager = new BedrockMenuManager(
                this,
                actionRegistry,
                soundsClock,
                requirementService,
                requirementFeedbackHandler,
                floodgateAvailable
        );
        boolean bedrockMenusEnabled = floodgateAvailable && bedrockMenuManager.isEnabled();
        floodgateAvailable = bedrockMenusEnabled;
        commandManager = new CommandManager(this);
        menuClockManager = new MenuClockManager(this, clockKey);
        hotReloadManager = new HotReloadManager(this);
        proxyManager = new ProxyManager(this);
        proxyManager.initialize();
        javaMenuManager.loadAllMenus();
        bedrockMenuManager.loadAllMenus();
        registerClockListeners(menuClockManager);
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new JavaMenuListener(this, actionRegistry), this);
    }

    private void registerClockListeners(MenuClockManager clockManager) {
        getServer().getPluginManager().registerEvents(new ClockInteractionListener(clockManager), this);
        if (FoliaFactory.isFolia()) {
            getServer().getPluginManager().registerEvents(new PlayerLifecycleListener_Folia(this, clockManager), this);
            return;
        }
        getServer().getPluginManager().registerEvents(new PlayerLifecycleListener(this, clockManager), this);
    }

    private void initializeOptionalFeatures() {
        if (configManager.getConfig().getBoolean("settings.hot-reload.enabled", true)) {
            initializeHotReload();
        }
        initializeBStats();
    }

    private void initializeHotReload() {
        try {
            hotReloadManager.initialize();
        } catch (java.io.IOException | IllegalStateException | SecurityException e) {
            getLogger().log(Level.SEVERE, Lang.get("log.hot-reload.initialize-failed"), e);
        }
    }

    private void registerCommands() {
        PluginCommand command = getCommand(MAIN_COMMAND);
        if (command != null) {
            command.setExecutor(commandManager);
            command.setTabCompleter(commandManager);
        } else {
            getLogger().severe(Lang.get("log.command.register-failed").replace("{0}", MAIN_COMMAND));
        }

        PluginCommand clockCommand = getCommand(CLOCK_COMMAND);
        if (clockCommand != null) {
            clockCommand.setExecutor(new GetMenuClockCommand(this));
        } else {
            getLogger().severe(Lang.get("log.command.register-failed").replace("{0}", CLOCK_COMMAND));
        }
    }

    public void openSmartMenu(Player player, String menuName) {
        if (menuName == null) {
            player.sendMessage(Lang.get("message.menu-not-found").replace("{0}", ""));
            return;
        }
        Set<String> openingMenus = menuOpenChain.get();
        String normalizedMenuName = menuName.toLowerCase(Locale.ROOT);
        String openingMenuKey = player.getUniqueId() + ":" + normalizedMenuName;
        if (!openingMenus.add(openingMenuKey)) {
            getLogger().warning("Blocked recursive menu fallback for " + player.getName() + ": " + menuName);
            return;
        }

        try {
            if (isBedrockPlayer(player)) {
                bedrockMenuManager.openMenu(player, menuName);
                return;
            }
            javaMenuManager.openMenu(player, menuName);
        } finally {
            openingMenus.remove(openingMenuKey);
            if (openingMenus.isEmpty()) {
                menuOpenChain.remove();
            }
        }
    }

    private boolean isBedrockPlayer(Player player) {
        if (!floodgateAvailable || bedrockMenuManager == null || !bedrockMenuManager.isEnabled()) {
            return false;
        }
        try {
            if (floodgateApiClass == null) {
                floodgateApiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            }
            if (floodgateApiInstance == null) {
                floodgateApiInstance = floodgateApiClass.getMethod("getInstance").invoke(null);
            }
            if (floodgateIsPlayerMethod == null) {
                floodgateIsPlayerMethod = floodgateApiClass.getMethod("isFloodgatePlayer", UUID.class);
            }
            return (Boolean) floodgateIsPlayerMethod.invoke(floodgateApiInstance, player.getUniqueId());
        } catch (ReflectiveOperationException e) {
            getLogger().log(Level.WARNING, Lang.get("log.floodgate.player-check-failed").replace("{0}", player.getName()), e);
            return false;
        }
    }

    private void initializeBStats() {
        try {
            Metrics metrics = new Metrics(this, BSTATS_ID);
            metrics.addCustomChart(new SimplePie("server_software", this::detectServerSoftware));
            metrics.addCustomChart(new SimplePie("minecraft_version", this::detectMinecraftVersion));
        } catch (RuntimeException e) {
            getLogger().log(Level.WARNING, Lang.get("log.bstats.initialize-failed"), e);
        }
    }

    private String detectServerSoftware() {
        String version = Bukkit.getVersion();
        return Stream.of("Paper", "Spigot", "Purpur", "Mint", "Leaves", "Leaf", "Luminol", "Folia")
                .filter(version::contains)
                .findFirst()
                .orElse("Other");
    }

    private String detectMinecraftVersion() {
        String version = Bukkit.getBukkitVersion().split("-")[0];
        int maxLength = Math.min(4, version.length());
        return version.substring(0, maxLength);
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public JavaMenuManager getJavaMenuManager() {
        return javaMenuManager;
    }

    public BedrockMenuManager getBedrockMenuManager() {
        return bedrockMenuManager;
    }

    public ProxyManager getProxyManager() {
        return proxyManager;
    }

    public RequirementService getRequirementService() {
        return requirementService;
    }

    public RateLimiter getInteractionRateLimiter() {
        return interactionRateLimiter;
    }

    public MenuClockManager getMenuClockManager() {
        return menuClockManager;
    }
}

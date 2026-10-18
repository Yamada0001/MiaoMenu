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

import com.fluxcraft.MiaoMenu.api.MiaoMenuApi;
import com.fluxcraft.MiaoMenu.api.MiaoMenuApiImpl;
import com.fluxcraft.MiaoMenu.bedrockmenu.BedrockMenu;
import com.fluxcraft.MiaoMenu.bedrockmenu.BedrockMenuManager;
import com.fluxcraft.MiaoMenu.commands.CommandManager;
import com.fluxcraft.MiaoMenu.commands.impl.GetMenuClockCommand;
import com.fluxcraft.MiaoMenu.config.ConfigManager;
import com.fluxcraft.MiaoMenu.dialogmenu.DialogMenuManager;
import com.fluxcraft.MiaoMenu.dialogmenu.DynamicLR;
import com.fluxcraft.MiaoMenu.foliacall.FoliaFactory;
import com.fluxcraft.MiaoMenu.integration.ItemResolver;
import com.fluxcraft.MiaoMenu.integration.PlaceholderHook;
import com.fluxcraft.MiaoMenu.integration.text.MenuTextParser;
import com.fluxcraft.MiaoMenu.javamenu.JavaMenuListener;
import com.fluxcraft.MiaoMenu.javamenu.JavaMenuManager;
import com.fluxcraft.MiaoMenu.listeners.ClockInteractionListener;
import com.fluxcraft.MiaoMenu.listeners.PlayerLifecycleListener;
import com.fluxcraft.MiaoMenu.listeners.PlayerLifecycleListener_Folia;
import com.fluxcraft.MiaoMenu.managers.HotReloadManager;
import com.fluxcraft.MiaoMenu.managers.MenuClockManager;
import com.fluxcraft.MiaoMenu.managers.PeriodicTaskManager;
import com.fluxcraft.MiaoMenu.managers.ResourcePackManager;
import com.fluxcraft.MiaoMenu.managers.SoundsClock;
import com.fluxcraft.MiaoMenu.managers.CustomCommandManager;
import com.fluxcraft.MiaoMenu.menu.action.ActionRegistry;
import com.fluxcraft.MiaoMenu.menu.action.impl.CloseAction;
import com.fluxcraft.MiaoMenu.menu.requirement.RequirementFeedbackHandler;
import com.fluxcraft.MiaoMenu.menu.requirement.RequirementService;
import com.fluxcraft.MiaoMenu.proxy.ProxyManager;
import com.fluxcraft.MiaoMenu.security.RateLimiter;
import com.fluxcraft.MiaoMenu.storage.VariableStore;
import com.fluxcraft.MiaoMenu.theme.ThemeAssets;
import com.fluxcraft.MiaoMenu.theme.ThemePackExporter;
import com.fluxcraft.MiaoMenu.theme.ThemeRepository;
import com.fluxcraft.MiaoMenu.update.UpdateChecker;
import com.fluxcraft.MiaoMenu.update.UpdateNotifyListener;
import com.fluxcraft.MiaoMenu.utils.Lang;
import com.fluxcraft.MiaoMenu.utils.MenuUtils;
import com.fluxcraft.MiaoMenu.utils.PlaceholderUtils;

import cn.handyplus.lib.adapter.HandySchedulerUtil;

public final class MiaoMenu extends JavaPlugin {
    private static final int BSTATS_ID = 28979;
    public static final int JOIN_DELAY_TICKS = 20;
    private static final String MAIN_COMMAND = "dgeysermenu";
    private static final String CLOCK_COMMAND = "getmenuclock";

    private ConfigManager configManager;
    private JavaMenuManager javaMenuManager;
    private BedrockMenuManager bedrockMenuManager;
    private DialogMenuManager dialogMenuManager;
    private ActionRegistry actionRegistry;
    private CommandManager commandManager;
    private HotReloadManager hotReloadManager;
    private ProxyManager proxyManager;
    private RequirementService requirementService;
    private RateLimiter interactionRateLimiter;
    private MenuClockManager menuClockManager;
    private VariableStore variableStore;
    private DynamicLR dynamicLR;
    private MenuTextParser menuTextParser;
    private CustomCommandManager customCommandManager;
    private PeriodicTaskManager periodicTaskManager;
    private UpdateChecker updateChecker;
    private ThemeRepository themeRepository;
    private ResourcePackManager resourcePackManager;
    private ThemePackExporter themePackExporter;
    private Metrics metrics;
    private final ThreadLocal<Set<String>> menuOpenChain = ThreadLocal.withInitial(LinkedHashSet::new);
    private volatile Class<?> floodgateApiClass;
    private volatile Object floodgateApiInstance;
    private volatile Method floodgateIsPlayerMethod;
    private volatile boolean floodgateReflectionBroken;
    /** Floodgate 插件是否存在（与"基岩菜单链路是否就绪"是两个独立概念）。 */
    private volatile boolean floodgatePresent;

    @Override
    public void onEnable() {
        try {
            initializeCoreServices();
            initializeMenuSystems();
            registerListeners();
            registerCommands();
            registerApi();
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
        if (customCommandManager != null) {
            customCommandManager.shutdown();
        }
        if (periodicTaskManager != null) {
            periodicTaskManager.shutdown();
        }
        if (themeRepository != null) {
            // 先停掉主题下载，避免异步任务在禁用后回调 reloadAllMenus。
            themeRepository.shutdown();
        }
        if (variableStore != null) {
            // 停止后台落盘并做最后一次同步保存。
            variableStore.shutdown();
        }
        if (metrics != null) {
            metrics.shutdown();
        }
        if (updateChecker != null) {
            updateChecker.shutdown();
        }
        try {
            releaseStaticReferences();
        } catch (RuntimeException | LinkageError e) {
            getLogger().log(Level.FINE, "Could not release all static references", e);
        } finally {
            // 无论如何都要断开对插件实例的静态引用，否则热重载会钉住旧类加载器。
            Lang.shutdown();
        }
    }

    /**
     * 释放静态字段对插件实例与外部类加载器的引用。
     *
     * <p>正常启停不需要这步，但用 PlugMan 之类热重载插件时，静态字段会钉住
     * 旧的类加载器，导致整份旧类无法回收。</p>
     */
    private void releaseStaticReferences() {
        BedrockMenu.resetReflectionCache();
        ItemResolver.resetReflectionCache();
        CloseAction.resetReflectionCache();
        floodgateApiClass = null;
        floodgateApiInstance = null;
        floodgateIsPlayerMethod = null;
        floodgateReflectionBroken = false;
        floodgatePresent = false;
    }

    private void initializeCoreServices() {
        saveDefaultConfig();
        Lang.init(this);
        HandySchedulerUtil.init(this);
        floodgatePresent = detectFloodgate();
        configManager = new ConfigManager(this);
        configManager.loadConfig();
        Lang.reload();
        configManager.checkAndRefreshMenus();
        requirementService = new RequirementService(this);
        interactionRateLimiter = new RateLimiter(
                java.time.Duration.ofMillis(configManager.getRateLimitWindowMillis()),
                configManager.getRateLimitMaxEvents()
        );
        variableStore = new VariableStore(this);
        variableStore.load();
        menuTextParser = new MenuTextParser(this);
        dynamicLR = new DynamicLR(this);
        themeRepository = new ThemeRepository(this);
        resourcePackManager = new ResourcePackManager(this);
        themePackExporter = new ThemePackExporter(this);
        PlaceholderHook.register(this);
    }

    private void initializeMenuSystems() {
        NamespacedKey clockKey = new NamespacedKey(this, "menu_clock");
        RequirementFeedbackHandler requirementFeedbackHandler = new RequirementFeedbackHandler(this);
        ItemResolver itemResolver = new ItemResolver(this, configManager.getCraftEngineFallbackMaterial());
        SoundsClock soundsClock = new SoundsClock(this);
        actionRegistry = new ActionRegistry(this);        javaMenuManager = new JavaMenuManager(this, itemResolver, soundsClock, requirementService, requirementFeedbackHandler);
        bedrockMenuManager = new BedrockMenuManager(
                this,
                actionRegistry,
                soundsClock,
                requirementService,
                requirementFeedbackHandler,
                floodgatePresent
        );
        dialogMenuManager = new DialogMenuManager(
                this,
                itemResolver,
                actionRegistry,
                soundsClock,
                requirementService,
                requirementFeedbackHandler
        );
        commandManager = new CommandManager(this);
        menuClockManager = new MenuClockManager(this, clockKey);
        hotReloadManager = new HotReloadManager(this);
        proxyManager = new ProxyManager(this);
        proxyManager.initialize();
        customCommandManager = new CustomCommandManager(this, actionRegistry);
        periodicTaskManager = new PeriodicTaskManager(this, actionRegistry, requirementService);
        reloadAllMenus();
        registerClockListeners(menuClockManager);
    }

    /**
     * 重新加载全部菜单与配置，供 {@code /dgm reload} 与文件热重载共用。
     */
    public void reloadAll() {
        configManager.loadConfig();
        Lang.reload();
        PlaceholderUtils.reset();
        configManager.checkAndRefreshMenus();
        reloadAllMenus();
        proxyManager.reload();
        if (updateChecker != null) {
            updateChecker.reload();
        }
    }

    /**
     * 只重新加载菜单注册表与依赖菜单的子系统。
     */
    public void reloadAllMenus() {
        requirementService.resetReportedErrors();
        if (javaMenuManager != null) {
            javaMenuManager.loadAllMenus();
        }
        if (bedrockMenuManager != null) {
            bedrockMenuManager.loadAllMenus();
        }
        if (dialogMenuManager != null) {
            dialogMenuManager.loadAllMenus();
        }
        if (customCommandManager != null) {
            customCommandManager.reload();
        }
        if (periodicTaskManager != null) {
            periodicTaskManager.reload();
        }
    }

    private void registerApi() {
        try {
            getServer().getServicesManager().register(
                    MiaoMenuApi.class,
                    new MiaoMenuApiImpl(this),
                    this,
                    org.bukkit.plugin.ServicePriority.Normal
            );
        } catch (RuntimeException e) {
            getLogger().log(Level.WARNING, "Could not register the MiaoMenu API service", e);
        }
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
        initializeUpdateChecker();
        initializeThemePrefetch();
        initializeBedrockRetry();
        initializeBStats();
    }

    /**
     * 启动后再探测一次 Floodgate。
     *
     * <p>插件启用顺序由服务端决定，Floodgate 可能在本插件之后才启用；启动瞬间
     * 探测不到就会把基岩菜单永久关掉。这里延迟重试一次，探测到就补加载。</p>
     */
    private void initializeBedrockRetry() {
        if (bedrockMenuManager == null) {
            return;
        }
        HandySchedulerUtil.runTaskLater(() -> {
            if (bedrockMenuManager.isEnabled()) {
                return;
            }
            if (!isFloodgatePresent()) {
                return;
            }
            getLogger().info(Lang.get("log.bedrock-menu.late-detected"));
            bedrockMenuManager.loadAllMenus();
        }, 100L);
    }

    /**
     * 后台预取主题清单。
     *
     * <p>{@code download} 的 Tab 补全依赖已缓存的清单；不预取的话第一次按 Tab
     * 只能返回空列表（请求是异步的），玩家会以为插件没连上远端仓库。</p>
     */
    private void initializeThemePrefetch() {
        if (themeRepository == null) {
            return;
        }
        // 已安装的主题可能还是旧结构（贴图在 gui/sprites 下），先补齐标准物品模型，
        // 这样升级插件后不用重新下载主题也能显示图片。
        HandySchedulerUtil.runTaskAsynchronously(() -> {
            try {
                int generated = ThemeAssets.ensure(getDataFolder().toPath().resolve("themes"));
                if (generated > 0) {
                    getLogger().info("Prepared " + generated + " theme asset file(s) for item models.");
                }
                // 已安装的主题也补一份"54 格铺满背景"的 Java 菜单（存在则跳过）。
                if (getConfig().getBoolean("settings.theme-pack.generate-background-menus", true)) {
                    int menus = com.fluxcraft.MiaoMenu.theme.JavaBackgroundMenuWriter.writeAll(
                            getDataFolder().toPath().resolve("java_menus"),
                            getDataFolder().toPath().resolve("themes/assets/miaomenu/textures"),
                            themeRepository == null ? null : themeRepository.cachedManifest()
                    );
                    if (menus > 0) {
                        getLogger().info("Generated " + menus + " theme background menu(s).");
                        HandySchedulerUtil.runTask(this::reloadAllMenus);
                    }
                }
            } catch (java.io.IOException e) {
                getLogger().log(Level.WARNING, "Could not prepare theme assets", e);
            }
        });
        if (!getConfig().getBoolean("settings.theme-repository.prefetch", true)) {
            return;
        }
        themeRepository.loadManifestAsync();
    }

    /**
     * 更新检查完全异步，失败只会留下一条日志，不影响插件启用。
     */
    private void initializeUpdateChecker() {
        updateChecker = new UpdateChecker(this);
        getServer().getPluginManager().registerEvents(new UpdateNotifyListener(updateChecker), this);
        updateChecker.start();
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

    /**
     * 打开菜单的统一入口。
     *
     * <p>菜单名支持 {@code java:} / {@code bedrock:} / {@code dialog:} 前缀来显式指定渲染方式；
     * 不带前缀时按玩家类型选择首选渲染方式，并在未命中时回退到另一种可用形态。</p>
     */
    public void openSmartMenu(Player player, String menuName) {
        if (player == null) {
            return;
        }
        if (menuName == null || menuName.isBlank()) {
            player.sendMessage(Lang.get("message.menu-name-required"));
            return;
        }

        String requested = menuName.trim();
        String target = null;
        String bare = requested;
        int colon = requested.indexOf(':');
        if (colon > 0) {
            String prefix = requested.substring(0, colon).toLowerCase(Locale.ROOT);
            if (prefix.equals("java") || prefix.equals("bedrock") || prefix.equals("dialog")) {
                target = prefix;
                bare = requested.substring(colon + 1).trim();
            }
        }
        if (bare.isEmpty()) {
            player.sendMessage(Lang.get("message.menu-name-required"));
            return;
        }

        Set<String> openingMenus = menuOpenChain.get();
        String openingMenuKey = player.getUniqueId() + ":" + bare.toLowerCase(Locale.ROOT);
        if (!openingMenus.add(openingMenuKey)) {
            getLogger().warning("Blocked recursive menu fallback for " + player.getName() + ": " + requested);
            player.sendMessage(Lang.get("message.menu-recursion-blocked"));
            return;
        }

        try {
            if (!dispatchMenu(player, target, bare)) {
                MenuUtils.handleMenuNotFound(player, null, requested);
            }
        } finally {
            openingMenus.remove(openingMenuKey);
            if (openingMenus.isEmpty()) {
                menuOpenChain.remove();
            }
        }
    }

    /**
     * @return 是否找到并尝试打开了菜单
     */
    private boolean dispatchMenu(Player player, String target, String menuName) {
        if (target != null) {
            return switch (target) {
                case "java" -> openJavaMenu(player, menuName);
                case "bedrock" -> openBedrockMenu(player, menuName);
                case "dialog" -> openDialogMenu(player, menuName);
                default -> false;
            };
        }
        if (isBedrockPlayer(player)) {
            if (openBedrockMenu(player, menuName)) {
                return true;
            }
            // Geyser 能渲染原版容器，因此基岩玩家仍可使用 Java 菜单
            return openJavaMenu(player, menuName);
        }
        if (openJavaMenu(player, menuName)) {
            return true;
        }
        return openDialogMenu(player, menuName);
    }

    private boolean openJavaMenu(Player player, String menuName) {
        if (javaMenuManager == null || !javaMenuManager.getMenus().containsKey(menuName)) {
            return false;
        }
        javaMenuManager.openMenu(player, menuName);
        return true;
    }

    private boolean openBedrockMenu(Player player, String menuName) {
        if (bedrockMenuManager == null || !bedrockMenuManager.getMenus().containsKey(menuName)) {
            return false;
        }
        bedrockMenuManager.openMenu(player, menuName);
        return true;
    }

    private boolean openDialogMenu(Player player, String menuName) {
        if (dialogMenuManager == null || !dialogMenuManager.containsMenu(menuName)) {
            return false;
        }
        dialogMenuManager.openMenu(player, menuName);
        return true;
    }

    /**
     * 判断玩家是否为 Floodgate（基岩版）玩家。
     *
     * <p>该判定只依赖 Floodgate 插件本身是否存在，与基岩菜单链路是否搭建成功无关——
     * 后者失败时仍然要能正确识别基岩玩家并给出提示。</p>
     */
    private boolean isBedrockPlayer(Player player) {
        if (!isFloodgatePresent() || floodgateReflectionBroken) {
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
            Object result = floodgateIsPlayerMethod.invoke(floodgateApiInstance, player.getUniqueId());
            return result instanceof Boolean value && value;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            floodgateReflectionBroken = true;
            getLogger().log(
                    Level.WARNING,
                    Lang.get("log.floodgate.player-check-failed").replace("{0}", player.getName()),
                    e
            );
            return false;
        }
    }

    /** 基岩版菜单链路是否可用（Floodgate + Cumulus 反射桥都就绪）。 */
    public boolean isBedrockMenuEnabled() {
        return isFloodgatePresent() && bedrockMenuManager != null && bedrockMenuManager.isEnabled();
    }

    /**
     * Floodgate 插件是否可用。
     *
     * <p>启动期只探测一次并缓存结果；此前探测失败时会重新检查，
     * 这样 Floodgate 晚于本插件启用（或插件名大小写与预期不同）也能自动恢复。</p>
     */
    public boolean isFloodgatePresent() {
        if (floodgatePresent) {
            return true;
        }
        boolean detected = detectFloodgate();
        if (detected) {
            floodgatePresent = true;
        }
        return detected;
    }

    /**
     * 大小写不敏感地查找 Floodgate。
     *
     * <p>{@code PluginManager#isPluginEnabled(String)} 依赖插件注册表里的名字，
     * 一旦插件名大小写或加载顺序与预期不同就会返回 {@code false}，因此再扫一遍
     * 已加载插件列表兜底，避免"明明装了 Floodgate 却提示不可用"。</p>
     */
    private boolean detectFloodgate() {
        org.bukkit.Server server = getServer();
        if (server == null) {
            return false;
        }
        org.bukkit.plugin.PluginManager pluginManager = server.getPluginManager();
        if (pluginManager == null) {
            return false;
        }
        try {
            if (pluginManager.isPluginEnabled("floodgate")) {
                return true;
            }
        } catch (RuntimeException ignored) {
            // 插件管理器异常时继续走下面的兜底扫描
        }
        for (org.bukkit.plugin.Plugin plugin : pluginManager.getPlugins()) {
            if (plugin != null && "floodgate".equalsIgnoreCase(plugin.getName())) {
                return plugin.isEnabled();
            }
        }
        return false;
    }

    private void initializeBStats() {
        try {
            metrics = new Metrics(this, BSTATS_ID);
            metrics.addCustomChart(new SimplePie("server_software", this::detectServerSoftware));
            metrics.addCustomChart(new SimplePie("minecraft_version", this::detectMinecraftVersion));
        } catch (RuntimeException e) {
            getLogger().log(Level.WARNING, Lang.get("log.bstats.initialize-failed"), e);
        }
    }

    private String detectServerSoftware() {
        if (FoliaFactory.isFolia()) {
            return "Folia";
        }
        String version = Bukkit.getVersion();
        return Stream.of("Paper", "Purpur", "Mint", "Leaves", "Leaf", "Luminol", "Spigot")
                .filter(version::contains)
                .findFirst()
                .orElse("Other");
    }

    private String detectMinecraftVersion() {
        String version = Bukkit.getBukkitVersion().split("-")[0];
        java.util.regex.Matcher matcher = MINECRAFT_VERSION.matcher(version);
        return matcher.find() ? matcher.group() : version;
    }

    private static final java.util.regex.Pattern MINECRAFT_VERSION =
            java.util.regex.Pattern.compile("\\d+\\.\\d+(\\.\\d+)?");

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

    public DialogMenuManager getDialogMenuManager() {
        return dialogMenuManager;
    }

    public ActionRegistry getActionRegistry() {
        return actionRegistry;
    }

    public VariableStore getVariableStore() {
        return variableStore;
    }

    public DynamicLR getDynamicLR() {
        return dynamicLR;
    }

    public MenuTextParser getMenuTextParser() {
        return menuTextParser;
    }

    public HotReloadManager getHotReloadManager() {
        return hotReloadManager;
    }

    public CommandManager getCommandManager() {
        return commandManager;
    }

    public CustomCommandManager getCustomCommandManager() {
        return customCommandManager;
    }

    public PeriodicTaskManager getPeriodicTaskManager() {
        return periodicTaskManager;
    }

    public UpdateChecker getUpdateChecker() {
        return updateChecker;
    }

    public ThemeRepository getThemeRepository() {
        return themeRepository;
    }

    public ResourcePackManager getResourcePackManager() {
        return resourcePackManager;
    }

    /** 主题贴图导出器：把主题资源交给 CraftEngine / ItemsAdder / Oraxen 打包。 */
    public ThemePackExporter getThemePackExporter() {
        return themePackExporter;
    }

    /**
     * Dialog 里的 {@code image:<id>} 是否应渲染成 CraftEngine 的
     * {@code <image:...>} 大图标签。
     *
     * <p>CE 会拦截 Dialog 数据包（{@code intercept-packets.dialog}），所以正文里
     * 的标签能显示成大图；没有 CE 时只能退回普通物品图标。</p>
     */
    public boolean useCraftEngineDialogImage() {
        if (themePackExporter == null) {
            return false;
        }
        if (!"tag".equalsIgnoreCase(getConfig()
                .getString("settings.theme-pack.dialog-image", "tag"))) {
            return false;
        }
        try {
            return getServer() != null
                    && getServer().getPluginManager().isPluginEnabled("CraftEngine");
        } catch (RuntimeException e) {
            return false;
        }
    }
}

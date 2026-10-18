package com.fluxcraft.MiaoMenu.bedrockmenu;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.function.Consumer;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.fluxcraft.MiaoMenu.foliacall.FoliaFactory;
import com.fluxcraft.MiaoMenu.managers.SoundsClock;
import com.fluxcraft.MiaoMenu.menu.action.ActionRegistry;
import com.fluxcraft.MiaoMenu.menu.requirement.RequirementFeedbackHandler;
import com.fluxcraft.MiaoMenu.menu.requirement.RequirementResult;
import com.fluxcraft.MiaoMenu.menu.requirement.RequirementService;
import com.fluxcraft.MiaoMenu.utils.Lang;
import com.fluxcraft.MiaoMenu.utils.MenuUtils;
import com.fluxcraft.MiaoMenu.utils.PlaceholderUtils;

public class BedrockMenuManager {
    private final MiaoMenu plugin;
    private final ActionRegistry actionRegistry;
    private final SoundsClock soundsClock;
    private final RequirementService requirementService;
    private final RequirementFeedbackHandler requirementFeedbackHandler;
    private volatile @Nullable FloodgateReflectionAccess reflectionAccess;
    private volatile Map<String, BedrockMenu> menus = Collections.emptyMap();

    public BedrockMenuManager(
            MiaoMenu plugin,
            ActionRegistry actionRegistry,
            SoundsClock soundsClock,
            RequirementService requirementService,
            RequirementFeedbackHandler requirementFeedbackHandler,
            boolean floodgatePresent
    ) {
        this.plugin = plugin;
        this.actionRegistry = actionRegistry;
        this.soundsClock = soundsClock;
        this.requirementService = requirementService;
        this.requirementFeedbackHandler = requirementFeedbackHandler;
        this.reflectionAccess = floodgatePresent ? createReflectionAccess() : null;
    }

    private @Nullable FloodgateReflectionAccess createReflectionAccess() {
        try {
            return new FloodgateReflectionAccess();
        } catch (RuntimeException | LinkageError e) {
            plugin.getLogger().log(Level.WARNING, Lang.get("log.bedrock-menu.reflection-setup-failed"), e);
            return null;
        }
    }

    /**
     * 取得反射桥，必要时重试。
     *
     * <p>启动期 Floodgate 可能还没启用（或探测时机不对），这里在每次加载菜单时
     * 复核一次，避免整台服务器因为一次误判而永久失去基岩版菜单。</p>
     */
    private @Nullable FloodgateReflectionAccess ensureReflectionAccess() {
        FloodgateReflectionAccess current = reflectionAccess;
        if (current != null) {
            return current;
        }
        if (!plugin.isFloodgatePresent()) {
            return null;
        }
        synchronized (this) {
            if (reflectionAccess == null) {
                reflectionAccess = createReflectionAccess();
            }
            return reflectionAccess;
        }
    }

    public boolean isEnabled() {
        return reflectionAccess != null;
    }

    public void loadAllMenus() {
        if (ensureReflectionAccess() == null) {
            if (!menus.isEmpty()) {
                menus = Collections.emptyMap();
            }
            plugin.getLogger().warning(disabledReason());
            return;
        }
        Map<String, BedrockMenu> previous = this.menus;
        Map<String, BedrockMenu> newMenus = new ConcurrentHashMap<>();
        File dir = new File(plugin.getDataFolder(), "bedrock_menus");
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().severe(Lang.get("log.bedrock-menu.directory-create-failed"));
            return;
        }
        File[] files = dir.listFiles(file -> file.isFile()
                && file.getName().toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) {
            plugin.getLogger().warning(Lang.get("log.bedrock-menu.directory-read-failed"));
            return;
        }
        if (files.length == 0) {
            if (previous.isEmpty()) {
                return;
            }
            plugin.getLogger().warning(Lang.get("log.bedrock-menu.directory-empty"));
            return;
        }
        for (File file : files) {
            String fileName = file.getName();
            String name = fileName.substring(0, fileName.length() - 4).toLowerCase(Locale.ROOT);
            try {
                FileConfiguration config = loadStrictly(file);
                BedrockMenu menu = new BedrockMenu(name, config, plugin, requirementService);
                warnAboutUnknownExecuteAs(menu);
                newMenus.put(name, menu);
            } catch (IOException | InvalidConfigurationException | RuntimeException e) {
                BedrockMenu fallback = previous.get(name);
                if (fallback != null) {
                    newMenus.put(name, fallback);
                }
                plugin.getLogger().log(Level.WARNING,
                        Lang.get("log.bedrock-menu.load-failed").replace("{0}", fileName), e);
            }
        }
        if (newMenus.isEmpty()) {
            plugin.getLogger().severe(Lang.get("log.bedrock-menu.all-failed"));
            return;
        }
        menus = newMenus;
    }

    private static FileConfiguration loadStrictly(File file) throws IOException, InvalidConfigurationException {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.load(file);
        return configuration;
    }

    /**
     * 把"基岩版菜单不可用"细分成可定位的原因，避免服主只能看到笼统提示。
     */
    private String disabledReason() {
        if (!plugin.isFloodgatePresent()) {
            return Lang.get("log.bedrock-menu.floodgate-missing");
        }
        if (!BedrockMenu.cumulusAvailable()) {
            return Lang.get("log.bedrock-menu.cumulus-missing");
        }
        return Lang.get("log.bedrock-menu.disabled");
    }

    /**
     * 加载期校验 {@code execute_as}，避免写错时静默以玩家身份执行。
     */
    private void warnAboutUnknownExecuteAs(BedrockMenu menu) {
        for (BedrockMenu.BedrockMenuItem item : menu.getAllItems()) {
            String executeAs = item.getExecuteAs();
            if (executeAs == null || executeAs.isBlank()) {
                continue;
            }
            if (!actionRegistry.isRegistered(executeAs)) {
                plugin.getLogger().warning(Lang.get("log.bedrock-menu.unknown-execute-as")
                        .replace("{0}", executeAs)
                        .replace("{1}", menu.getName())
                        .replace("{2}", item.getCommand()));
            }
        }
    }

    public void openMenu(Player player, String menuName) {
        if (!isEnabled()) {
            player.sendMessage(Lang.get("message.bedrock-disabled"));
            return;
        }
        if (menuName == null) {
            player.sendMessage(Lang.get("message.menu-name-required"));
            return;
        }
        BedrockMenu menu = menus.get(menuName.toLowerCase(Locale.ROOT));
        if (MenuUtils.handleMenuNotFound(plugin.getLogger(), player, menu, menuName)) {
            return;
        }
        RequirementResult requirementResult = menu.checkViewRequirement(player);
        if (MenuUtils.handleRequirementDenied(requirementFeedbackHandler, player, requirementResult)) {
            return;
        }
        soundsClock.playMenuOpenSound(player);
        try {
            sendFloodgateForm(player, menu);
        } catch (RuntimeException | LinkageError e) {
            plugin.getLogger().log(Level.SEVERE, Lang.get("log.bedrock-menu.send-failed")
                    .replace("{0}", menuName)
                    .replace("{1}", player.getName()), e);
            player.sendMessage(Lang.get("open.error"));
        }
    }

    private void sendFloodgateForm(Player player, BedrockMenu menu) {
        FloodgateReflectionAccess access = ensureReflectionAccess();
        if (access == null) {
            throw new IllegalStateException(Lang.get("log.bedrock-menu.reflection-setup-failed"));
        }
        Object formBuilder = menu.buildForm(player);
        if (formBuilder == null) {
            plugin.getLogger().warning(Lang.get("log.bedrock-menu.form-build-returned-null")
                    .replace("{0}", player.getName()));
            player.sendMessage(Lang.get("open.error"));
            return;
        }
        try {
            Object builtForm = access.buildForm(formBuilder, createFormResponseHandler(menu.getAllItems(), player, menu));
            if (!access.sendForm(player.getUniqueId(), builtForm)) {
                plugin.getLogger().warning(Lang.get("log.bedrock-menu.send-returned-false")
                        .replace("{0}", player.getName())
                        .replace("{1}", menu.getName()));
                player.sendMessage(Lang.get("open.error"));
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(Lang.get("log.bedrock-menu.reflection-send-failed")
                    .replace("{0}", menu.getName())
                    .replace("{1}", player.getName()), e);
        }
    }

    private Consumer<Object> createFormResponseHandler(
            List<BedrockMenu.BedrockMenuItem> allItems,
            Player player,
            BedrockMenu menu
    ) {
        return response -> {
            try {
                // Floodgate 的回调线程不保证是玩家所属区域线程，必须切回去。
                FoliaFactory.getAdapter().runTaskLaterForEntity(
                        plugin,
                        player,
                        () -> handleFormResponseSafely(response, allItems, player, menu),
                        0L
                );
            } catch (RuntimeException | LinkageError e) {
                plugin.getLogger().log(Level.WARNING,
                        Lang.get("log.bedrock-menu.response-schedule-failed")
                                .replace("{0}", player.getName())
                                .replace("{1}", menu.getName()), e);
            }
        };
    }

    private void handleFormResponseSafely(
            Object response,
            List<BedrockMenu.BedrockMenuItem> allItems,
            Player player,
            BedrockMenu menu
    ) {
        if (player == null || !player.isOnline()) {
            return;
        }
        try {
            handleFormResponse(response, allItems, player, menu);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            plugin.getLogger().log(Level.WARNING,
                    Lang.get("log.bedrock-menu.response-handle-failed")
                            .replace("{0}", player.getName())
                            .replace("{1}", menu.getName()), e);
        }
    }

    private void handleFormResponse(
            Object response,
            List<BedrockMenu.BedrockMenuItem> allItems,
            Player player,
            BedrockMenu menu
    ) throws ReflectiveOperationException {
        Object rawIndex = response.getClass().getMethod("clickedButtonId").invoke(response);
        if (!(rawIndex instanceof Number number)) {
            return;
        }
        int clickedIndex = number.intValue();
        if (clickedIndex < 0 || clickedIndex >= allItems.size()) {
            return;
        }
        if (!plugin.getInteractionRateLimiter().allow(player.getUniqueId())) {
            return;
        }
        BedrockMenu.BedrockMenuItem item = allItems.get(clickedIndex);
        RequirementResult requirementResult = item.evaluateRequirement(
                player,
                requirementService,
                menu.getName(),
                menu.getRequirementBlocks()
        );
        if (!requirementResult.allowed()) {
            player.sendMessage(item.getLockMessage(player, plugin, requirementResult));
            return;
        }
        String cmd = item.getCommand();
        if (cmd == null || cmd.isBlank()) {
            player.sendMessage(Lang.get("message.button-no-action"));
            return;
        }
        String parsed = PlaceholderUtils.parse(player, cmd, plugin);
        String executeAs = item.getExecuteAs();
        if (executeAs == null || executeAs.isBlank() || parsed.startsWith("[")) {
            actionRegistry.dispatch(player, parsed);
            return;
        }
        actionRegistry.dispatch(player, "[" + executeAs + "] " + parsed);
    }

    public boolean containsMenu(String menuName) {
        return menuName != null && menus.containsKey(menuName.toLowerCase(Locale.ROOT));
    }

    public Map<String, BedrockMenu> getMenus() {
        return Collections.unmodifiableMap(menus);
    }

    public ActionRegistry getActionRegistry() {
        return actionRegistry;
    }

    private static final class FloodgateReflectionAccess {
        private final Object floodgateApi;
        private final Method sendFormMethod;

        private FloodgateReflectionAccess() {
            try {
                Class<?> floodgateApiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                floodgateApi = floodgateApiClass.getMethod("getInstance").invoke(null);
                sendFormMethod = findSendFormMethod(floodgateApiClass);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(Lang.get("log.bedrock-menu.reflection-setup-failed"), e);
            }
        }

        private Object buildForm(Object formBuilder, Consumer<Object> handler) throws ReflectiveOperationException {
            Method validResultHandlerMethod = formBuilder.getClass().getMethod("validResultHandler", Consumer.class);
            Method buildMethod = formBuilder.getClass().getMethod("build");
            validResultHandlerMethod.invoke(formBuilder, handler);
            return buildMethod.invoke(formBuilder);
        }

        private boolean sendForm(UUID uuid, Object form) throws ReflectiveOperationException {
            Object result = sendFormMethod.invoke(floodgateApi, uuid, form);
            return !(result instanceof Boolean value) || value;
        }

        /**
         * 依次尝试 legacy 与新包的 {@code sendForm(UUID, Form)} 重载，
         * 避免 Floodgate 升级后单点精确匹配直接失效。
         */
        private Method findSendFormMethod(Class<?> floodgateApiClass) {
            for (String formClassName : List.of(
                    "org.geysermc.cumulus.form.Form",
                    "org.geysermc.cumulus.Form"
            )) {
                Method candidate = findSendForm(floodgateApiClass, formClassName);
                if (candidate != null) {
                    return candidate;
                }
            }
            throw new IllegalStateException(Lang.get("log.bedrock-menu.reflection-setup-failed"));
        }

        private Method findSendForm(Class<?> floodgateApiClass, String formClassName) {
            Class<?> formClass;
            try {
                formClass = Class.forName(formClassName);
            } catch (ClassNotFoundException e) {
                return null;
            }
            for (Method method : floodgateApiClass.getMethods()) {
                if (method.getName().equals("sendForm")
                        && method.getParameterCount() == 2
                        && method.getParameterTypes()[0] == UUID.class
                        && method.getParameterTypes()[1].isAssignableFrom(formClass)) {
                    return method;
                }
            }
            return null;
        }
    }
}

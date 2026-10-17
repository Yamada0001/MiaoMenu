package com.fluxcraft.MiaoMenu.bedrockmenu;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.function.Consumer;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import com.fluxcraft.MiaoMenu.MiaoMenu;
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
    private final FloodgateReflectionAccess reflectionAccess;
    private volatile Map<String, BedrockMenu> menus = Collections.emptyMap();

    public BedrockMenuManager(
            MiaoMenu plugin,
            ActionRegistry actionRegistry,
            SoundsClock soundsClock,
            RequirementService requirementService,
            RequirementFeedbackHandler requirementFeedbackHandler,
            boolean floodgateAvailable
    ) {
        this.plugin = plugin;
        this.actionRegistry = actionRegistry;
        this.soundsClock = soundsClock;
        this.requirementService = requirementService;
        this.requirementFeedbackHandler = requirementFeedbackHandler;
        this.reflectionAccess = floodgateAvailable ? createReflectionAccess() : null;
    }

    private FloodgateReflectionAccess createReflectionAccess() {
        try {
            return new FloodgateReflectionAccess();
        } catch (RuntimeException | LinkageError e) {
            plugin.getLogger().log(Level.WARNING, Lang.get("log.bedrock-menu.reflection-setup-failed"), e);
            return null;
        }
    }

    public boolean isEnabled() {
        return reflectionAccess != null;
    }

    public void loadAllMenus() {
        if (!isEnabled()) {
            menus = Collections.emptyMap();
            return;
        }
        Map<String, BedrockMenu> newMenus = new ConcurrentHashMap<>();
        File dir = new File(plugin.getDataFolder(), "bedrock_menus");
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().severe(Lang.get("log.bedrock-menu.directory-create-failed"));
            return;
        }
        File[] files = dir.listFiles((_, n) -> n.endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            try {
                String name = file.getName().replace(".yml", "");
                FileConfiguration config = YamlConfiguration.loadConfiguration(file);
                newMenus.put(name, new BedrockMenu(name, config, plugin, requirementService));
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, Lang.get("log.bedrock-menu.load-failed").replace("{0}", file.getName()), e);
            }
        }
        menus = newMenus;
    }

    public void openMenu(Player player, String menuName) {
        if (!isEnabled()) {
            player.sendMessage(Lang.get("open.error"));
            return;
        }
        BedrockMenu menu = menus.get(menuName);
        if (MenuUtils.handleMenuNotFound(player, menu, menuName)) {
            return;
        }
        RequirementResult requirementResult = menu.checkViewRequirement(player);
        if (MenuUtils.handleRequirementDenied(requirementFeedbackHandler, player, requirementResult)) {
            return;
        }
        soundsClock.playMenuOpenSound(player);
        try {
            sendFloodgateForm(player, menu);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, Lang.get("log.bedrock-menu.send-failed")
                    .replace("{0}", menuName)
                    .replace("{1}", player.getName()), e);
            player.sendMessage(Lang.get("open.error"));
        }
    }

    private void sendFloodgateForm(Player player, BedrockMenu menu) {
        FloodgateReflectionAccess access = reflectionAccess;
        if (access == null) {
            throw new IllegalStateException(Lang.get("log.bedrock-menu.reflection-setup-failed"));
        }
        Object formBuilder = menu.buildForm(player);
        if (formBuilder == null) {
            plugin.getLogger().warning(Lang.get("log.bedrock-menu.form-build-returned-null").replace("{0}", player.getName()));
            return;
        }
        try {
            Object builtForm = access.buildForm(formBuilder, createFormResponseHandler(menu.getAllItems(), player, menu));
            access.sendForm(player.getUniqueId(), builtForm);
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
                handleFormResponse(response, allItems, player, menu);
            } catch (ReflectiveOperationException e) {
                plugin.getLogger().log(Level.WARNING, Lang.get("log.bedrock-menu.reflection-send-failed")
                        .replace("{0}", menu.getName())
                        .replace("{1}", player.getName()), e);
            }
        };
    }

    private void handleFormResponse(
            Object response,
            List<BedrockMenu.BedrockMenuItem> allItems,
            Player player,
            BedrockMenu menu
    ) throws ReflectiveOperationException {
        int clickedIndex = (int) response.getClass().getMethod("clickedButtonId").invoke(response);
        if (clickedIndex < 0 || clickedIndex >= allItems.size()) {
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
        if (cmd == null || cmd.isEmpty()) {
            return;
        }
        String parsed = PlaceholderUtils.parse(player, cmd, plugin);
        String executeAs = item.getExecuteAs();
        if (executeAs == null || executeAs.isBlank()) {
            actionRegistry.dispatch(player, parsed);
            return;
        }
        actionRegistry.dispatch(player, "[" + executeAs + "] " + parsed);
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

        private void sendForm(UUID uuid, Object form) throws ReflectiveOperationException {
            sendFormMethod.invoke(floodgateApi, uuid, form);
        }

        private Method findSendFormMethod(Class<?> floodgateApiClass) throws ClassNotFoundException {
    Class<?> formClass = Class.forName("org.geysermc.cumulus.form.Form");
    for (Method method : floodgateApiClass.getMethods()) {
        if (method.getName().equals("sendForm")
                && method.getParameterCount() == 2
                && method.getParameterTypes()[0] == UUID.class
                && formClass.equals(method.getParameterTypes()[1])) {
            return method;
        }
    }
    throw new IllegalStateException(Lang.get("log.bedrock-menu.reflection-setup-failed"));
}
    }
}

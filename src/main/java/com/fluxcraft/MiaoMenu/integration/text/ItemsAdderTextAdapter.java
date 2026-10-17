package com.fluxcraft.MiaoMenu.integration.text;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.bukkit.entity.Player;
import org.bukkit.permissions.Permissible;
import org.bukkit.plugin.Plugin;

final class ItemsAdderTextAdapter {
    private static final String PLUGIN_NAME = "ItemsAdder";
    private static final String WRAPPER_CLASS = "dev.lone.itemsadder.api.FontImages.FontImageWrapper";
    private static final Pattern TAG_PATTERN = Pattern.compile(
            "(?i)(?<![a-z0-9_]):(?:offset_-?\\d+|[a-z0-9_./-]+):(?![a-z0-9_])"
    );

    private final PluginLookup pluginLookup;
    private final Logger logger;
    private volatile ClassLoader cachedClassLoader;
    private volatile Method replaceMethod;
    private volatile Method replaceWithPermissibleMethod;
    private final AtomicBoolean apiWarningLogged = new AtomicBoolean();

    ItemsAdderTextAdapter(PluginLookup pluginLookup, Logger logger) {
        this.pluginLookup = pluginLookup;
        this.logger = logger;
    }

    boolean containsTag(String text) {
        return TAG_PATTERN.matcher(text).find();
    }

    boolean isAvailable() {
        Plugin plugin = pluginLookup.find(PLUGIN_NAME);
        return plugin != null && plugin.isEnabled();
    }

    String replace(String text, Player player) {
        if (!containsTag(text)) {
            return text;
        }
        Plugin itemsAdder = pluginLookup.find(PLUGIN_NAME);
        if (itemsAdder == null || !itemsAdder.isEnabled()) {
            return text;
        }

        try {
            Method fallback = resolveMethods(itemsAdder.getClass().getClassLoader());
            if (fallback == null) {
                warnApiFailure(null);
                return text;
            }
            Object result;
            Method permissionAware = replaceWithPermissibleMethod;
            if (player != null && permissionAware != null) {
                result = permissionAware.invoke(null, player, text);
            } else {
                result = fallback.invoke(null, text);
            }
            return result instanceof String replaced ? replaced : text;
        } catch (ReflectiveOperationException | LinkageError e) {
            warnApiFailure(e);
            return text;
        }
    }

    private synchronized Method resolveMethods(ClassLoader classLoader) {
        if (cachedClassLoader == classLoader) {
            return replaceMethod;
        }
        try {
            Class<?> wrapper = classLoader.loadClass(WRAPPER_CLASS);
            replaceMethod = wrapper.getMethod("replaceFontImages", String.class);
            try {
                replaceWithPermissibleMethod = wrapper.getMethod(
                        "replaceFontImages",
                        Permissible.class,
                        String.class
                );
            } catch (NoSuchMethodException ignored) {
                replaceWithPermissibleMethod = null;
            }
        } catch (ReflectiveOperationException | LinkageError e) {
            replaceMethod = null;
            replaceWithPermissibleMethod = null;
        }
        cachedClassLoader = classLoader;
        return replaceMethod;
    }

    private void warnApiFailure(Throwable cause) {
        if (!apiWarningLogged.compareAndSet(false, true)) {
            return;
        }
        String message = "ItemsAdder is enabled, but its font image API is unavailable; glyph tags will be kept as text.";
        if (cause == null) {
            logger.warning(message);
        } else {
            logger.log(Level.WARNING, message, cause);
        }
    }
}

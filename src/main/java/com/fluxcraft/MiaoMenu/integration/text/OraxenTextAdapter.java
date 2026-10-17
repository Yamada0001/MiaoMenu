package com.fluxcraft.MiaoMenu.integration.text;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

final class OraxenTextAdapter {
    private static final String PLUGIN_NAME = "Oraxen";
    private static final String GLYPH_TAG_CLASS = "io.th0rgal.oraxen.glyphs.GlyphTag";
    private static final String SHIFT_TAG_CLASS = "io.th0rgal.oraxen.glyphs.ShiftTag";
    private static final Pattern GLYPH_PATTERN = Pattern.compile("(?i)<(?:glyph|g):");

    private final PluginLookup pluginLookup;
    private final Logger logger;
    private volatile ClassLoader cachedClassLoader;
    private volatile Field glyphResolverField;
    private volatile Method playerResolverMethod;
    private volatile Field shiftResolverField;
    private final AtomicBoolean apiWarningLogged = new AtomicBoolean();

    OraxenTextAdapter(PluginLookup pluginLookup, Logger logger) {
        this.pluginLookup = pluginLookup;
        this.logger = logger;
    }

    boolean containsTag(String text) {
        return GLYPH_PATTERN.matcher(text).find();
    }

    boolean isAvailable() {
        Plugin plugin = pluginLookup.find(PLUGIN_NAME);
        return plugin != null && plugin.isEnabled();
    }

    @Nullable Component parse(String text, Player player) {
        if (!containsTag(text)) {
            return null;
        }
        Plugin oraxen = pluginLookup.find(PLUGIN_NAME);
        if (oraxen == null || !oraxen.isEnabled()) {
            return null;
        }

        try {
            if (!resolve(oraxen.getClass().getClassLoader())) {
                warnApiFailure(null);
                return null;
            }
            Object glyphValue = player == null
                    ? glyphResolverField.get(null)
                    : playerResolverMethod.invoke(null, player);
            Object shiftValue = shiftResolverField.get(null);
            if (!(glyphValue instanceof TagResolver glyphResolver)
                    || !(shiftValue instanceof TagResolver shiftResolver)) {
                warnApiFailure(null);
                return null;
            }
            TagResolver resolver = TagResolver.resolver(TagResolver.standard(), glyphResolver, shiftResolver);
            return MiniMessage.builder().tags(resolver).build().deserialize(text);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            warnApiFailure(e);
            return null;
        }
    }

    private synchronized boolean resolve(ClassLoader classLoader) {
        if (cachedClassLoader == classLoader) {
            return glyphResolverField != null && playerResolverMethod != null && shiftResolverField != null;
        }
        try {
            Class<?> glyphClass = classLoader.loadClass(GLYPH_TAG_CLASS);
            Class<?> shiftClass = classLoader.loadClass(SHIFT_TAG_CLASS);
            glyphResolverField = glyphClass.getField("RESOLVER");
            playerResolverMethod = glyphClass.getMethod("getResolverForPlayer", Player.class);
            shiftResolverField = shiftClass.getField("RESOLVER");
        } catch (ReflectiveOperationException | LinkageError e) {
            glyphResolverField = null;
            playerResolverMethod = null;
            shiftResolverField = null;
        }
        cachedClassLoader = classLoader;
        return glyphResolverField != null && playerResolverMethod != null && shiftResolverField != null;
    }

    private void warnApiFailure(Throwable cause) {
        if (!apiWarningLogged.compareAndSet(false, true)) {
            return;
        }
        String message = "Oraxen is enabled, but its glyph API is unavailable; glyph tags will be kept as text.";
        if (cause == null) {
            logger.warning(message);
        } else {
            logger.log(Level.WARNING, message, cause);
        }
    }
}

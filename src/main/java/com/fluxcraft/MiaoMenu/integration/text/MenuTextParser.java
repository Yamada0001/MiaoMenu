package com.fluxcraft.MiaoMenu.integration.text;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.fluxcraft.MiaoMenu.utils.PlaceholderUtils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class MenuTextParser {
    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.legacySection();

    private final MiaoMenu plugin;
    private final ItemsAdderTextAdapter itemsAdder;
    private final OraxenTextAdapter oraxen;
    private final CraftEngineTextAdapter craftEngine;
    private final Set<String> missingProviderWarnings = ConcurrentHashMap.newKeySet();

    public MenuTextParser(MiaoMenu plugin) {
        this(plugin, PluginLookup.bukkit(), plugin.getLogger());
    }

    MenuTextParser(MiaoMenu plugin, PluginLookup pluginLookup, Logger logger) {
        this.plugin = plugin;
        this.itemsAdder = new ItemsAdderTextAdapter(pluginLookup, logger);
        this.oraxen = new OraxenTextAdapter(pluginLookup, logger);
        this.craftEngine = new CraftEngineTextAdapter(pluginLookup);
    }

    public Component parse(@Nullable Player player, @Nullable String text) {
        return parseResolved(player, resolve(player, text));
    }

    public boolean containsExternalGlyphTag(@Nullable String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return itemsAdder.containsTag(text) || oraxen.containsTag(text) || craftEngine.containsTag(text);
    }

    public String resolve(@Nullable Player player, @Nullable String text) {
        String resolved = PlaceholderUtils.parse(player, text, plugin);
        if (itemsAdder.containsTag(resolved)) {
            if (!itemsAdder.isAvailable()) {
                warnMissingProvider("ItemsAdder", ":font_image:");
            } else {
                resolved = itemsAdder.replace(resolved, player);
            }
        }
        return resolved;
    }

    public Component parseResolved(@Nullable Player player, @Nullable String resolvedText) {
        String resolved = resolvedText == null ? "" : resolvedText;
        if (oraxen.containsTag(resolved)) {
            if (!oraxen.isAvailable()) {
                warnMissingProvider("Oraxen", "<glyph:id>");
            } else {
                Component component = oraxen.parse(convertLegacyToMiniMessage(resolved), player);
                if (component != null) {
                    return component;
                }
            }
        }
        if (craftEngine.containsTag(resolved)) {
            if (!craftEngine.isAvailable()) {
                warnMissingProvider("CraftEngine", "<image:namespace:id>");
            } else {
                resolved = craftEngine.prepare(resolved);
            }
        }
        return LEGACY_SECTION.deserialize(resolved);
    }

    static String convertLegacyToMiniMessage(String text) {
        StringBuilder converted = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current != '\u00a7' || index + 1 >= text.length()) {
                converted.append(current);
                continue;
            }
            String tag = legacyTag(text.charAt(index + 1));
            if (tag == null) {
                converted.append(current);
                continue;
            }
            converted.append(tag);
            index++;
        }
        return converted.toString();
    }

    private static @Nullable String legacyTag(char code) {
        return switch (Character.toLowerCase(code)) {
            case '0' -> "<black>";
            case '1' -> "<dark_blue>";
            case '2' -> "<dark_green>";
            case '3' -> "<dark_aqua>";
            case '4' -> "<dark_red>";
            case '5' -> "<dark_purple>";
            case '6' -> "<gold>";
            case '7' -> "<gray>";
            case '8' -> "<dark_gray>";
            case '9' -> "<blue>";
            case 'a' -> "<green>";
            case 'b' -> "<aqua>";
            case 'c' -> "<red>";
            case 'd' -> "<light_purple>";
            case 'e' -> "<yellow>";
            case 'f' -> "<white>";
            case 'k' -> "<obfuscated>";
            case 'l' -> "<bold>";
            case 'm' -> "<strikethrough>";
            case 'n' -> "<underlined>";
            case 'o' -> "<italic>";
            case 'r' -> "<reset>";
            default -> null;
        };
    }

    private void warnMissingProvider(String provider, String example) {
        if (missingProviderWarnings.add(provider)) {
            plugin.getLogger().warning(
                    provider + " glyph tag " + example + " was found, but " + provider
                            + " is not installed or enabled; the original tag will be displayed."
            );
        }
    }
}

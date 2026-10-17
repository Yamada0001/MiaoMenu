package com.fluxcraft.MiaoMenu.integration.text;

import java.util.regex.Pattern;

import org.bukkit.plugin.Plugin;

final class CraftEngineTextAdapter {
    private static final String PLUGIN_NAME = "CraftEngine";
    private static final Pattern IMAGE_PATTERN = Pattern.compile("(?i)<(?:image|shift):");

    private final PluginLookup pluginLookup;

    CraftEngineTextAdapter(PluginLookup pluginLookup) {
        this.pluginLookup = pluginLookup;
    }

    boolean containsTag(String text) {
        return IMAGE_PATTERN.matcher(text).find();
    }

    boolean isAvailable() {
        Plugin plugin = pluginLookup.find(PLUGIN_NAME);
        return plugin != null && plugin.isEnabled();
    }

    String prepare(String text) {
        // CraftEngine resolves <image:...> and <shift:...> in outgoing menu packets.
        return text;
    }
}

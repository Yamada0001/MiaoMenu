package com.fluxcraft.MiaoMenu.integration.text;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

@FunctionalInterface
interface PluginLookup {
    @Nullable Plugin find(String name);

    static PluginLookup bukkit() {
        return name -> Bukkit.getPluginManager().getPlugin(name);
    }
}

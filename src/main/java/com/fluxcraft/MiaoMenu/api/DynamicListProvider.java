package com.fluxcraft.MiaoMenu.api;

import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;

@FunctionalInterface
public interface DynamicListProvider {
    List<DynamicListEntry> provide(Player player, Map<String, Object> options);
}

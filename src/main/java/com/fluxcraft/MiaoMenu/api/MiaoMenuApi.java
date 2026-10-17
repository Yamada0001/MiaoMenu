package com.fluxcraft.MiaoMenu.api;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Player;

import com.fluxcraft.MiaoMenu.menu.action.MenuAction;

public interface MiaoMenuApi {
    void openMenu(Player player, String menuName);

    Set<String> getJavaMenuNames();

    Set<String> getDialogMenuNames();

    Set<String> getBedrockMenuNames();

    Optional<String> getGlobalVariable(String key);

    Optional<String> getPlayerVariable(UUID playerId, String key);

    Map<String, String> getGlobalVariables();

    Map<String, String> getPlayerVariables(UUID playerId);

    void setGlobalVariable(String key, String value);

    void setPlayerVariable(UUID playerId, String key, String value);

    boolean removeGlobalVariable(String key);

    boolean removePlayerVariable(UUID playerId, String key);

    void registerAction(String prefix, MenuAction action);

    void registerDynamicListProvider(String source, DynamicListProvider provider);
}

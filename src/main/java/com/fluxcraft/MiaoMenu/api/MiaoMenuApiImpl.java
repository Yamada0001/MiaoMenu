package com.fluxcraft.MiaoMenu.api;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Player;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.fluxcraft.MiaoMenu.menu.action.MenuAction;

public final class MiaoMenuApiImpl implements MiaoMenuApi {
    private final MiaoMenu plugin;

    public MiaoMenuApiImpl(MiaoMenu plugin) {
        this.plugin = plugin;
    }

    @Override
    public void openMenu(Player player, String menuName) {
        plugin.openSmartMenu(player, menuName);
    }

    @Override
    public Set<String> getJavaMenuNames() {
        return Set.copyOf(plugin.getJavaMenuManager().getMenus().keySet());
    }

    @Override
    public Set<String> getDialogMenuNames() {
        return Set.copyOf(plugin.getDialogMenuManager().getMenus().keySet());
    }

    @Override
    public Set<String> getBedrockMenuNames() {
        return Set.copyOf(plugin.getBedrockMenuManager().getMenus().keySet());
    }

    @Override
    public Optional<String> getGlobalVariable(String key) {
        return plugin.getVariableStore().getGlobal(key);
    }

    @Override
    public Optional<String> getPlayerVariable(UUID playerId, String key) {
        return plugin.getVariableStore().getPlayer(playerId, key);
    }

    @Override
    public Map<String, String> getGlobalVariables() {
        return plugin.getVariableStore().getGlobalVariables();
    }

    @Override
    public Map<String, String> getPlayerVariables(UUID playerId) {
        return plugin.getVariableStore().getPlayerVariables(playerId);
    }

    @Override
    public void setGlobalVariable(String key, String value) {
        plugin.getVariableStore().setGlobal(key, value);
    }

    @Override
    public void setPlayerVariable(UUID playerId, String key, String value) {
        plugin.getVariableStore().setPlayer(playerId, key, value);
    }

    @Override
    public boolean removeGlobalVariable(String key) {
        return plugin.getVariableStore().removeGlobal(key);
    }

    @Override
    public boolean removePlayerVariable(UUID playerId, String key) {
        return plugin.getVariableStore().removePlayer(playerId, key);
    }

    @Override
    public void registerAction(String prefix, MenuAction action) {
        plugin.getActionRegistry().register(prefix, action);
    }

    @Override
    public void registerDynamicListProvider(String source, DynamicListProvider provider) {
        plugin.getDynamicLR().register(source, provider);
    }
}

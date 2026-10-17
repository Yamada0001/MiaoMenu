package com.fluxcraft.MiaoMenu.menu.action.impl;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.fluxcraft.MiaoMenu.menu.action.MenuAction;

public final class VariableRemoveAction implements MenuAction {
    @Override
    public void execute(Player player, String content, Plugin plugin) {
        VariableActionSupport.VariableReference reference = VariableActionSupport.parseRemove(player, content, plugin);
        if (reference.scope() == VariableActionSupport.Scope.GLOBAL) {
            reference.store().removeGlobal(reference.key());
        } else {
            reference.store().removePlayer(player.getUniqueId(), reference.key());
        }
    }
}

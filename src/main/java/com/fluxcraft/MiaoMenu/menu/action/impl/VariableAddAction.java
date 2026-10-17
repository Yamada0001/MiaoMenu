package com.fluxcraft.MiaoMenu.menu.action.impl;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.fluxcraft.MiaoMenu.menu.action.MenuAction;

public final class VariableAddAction implements MenuAction {
    @Override
    public void execute(Player player, String content, Plugin plugin) {
        VariableActionSupport.ParsedAdd parsed = VariableActionSupport.parseAdd(player, content, plugin);
        VariableActionSupport.VariableReference reference = parsed.reference();
        if (reference.scope() == VariableActionSupport.Scope.GLOBAL) {
            reference.store().addGlobal(reference.key(), parsed.amount());
        } else {
            reference.store().addPlayer(player.getUniqueId(), reference.key(), parsed.amount());
        }
    }
}

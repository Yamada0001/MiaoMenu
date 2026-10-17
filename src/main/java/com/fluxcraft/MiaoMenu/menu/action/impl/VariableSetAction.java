package com.fluxcraft.MiaoMenu.menu.action.impl;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.fluxcraft.MiaoMenu.menu.action.MenuAction;

public final class VariableSetAction implements MenuAction {
    @Override
    public void execute(Player player, String content, Plugin plugin) {
        VariableActionSupport.ParsedSet parsed = VariableActionSupport.parseSet(player, content, plugin);
        VariableActionSupport.VariableReference reference = parsed.reference();
        if (reference.scope() == VariableActionSupport.Scope.GLOBAL) {
            reference.store().setGlobal(reference.key(), parsed.value());
        } else {
            reference.store().setPlayer(player.getUniqueId(), reference.key(), parsed.value());
        }
    }
}

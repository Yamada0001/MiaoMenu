package com.fluxcraft.MiaoMenu.menu.action.impl;

import java.util.Locale;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.fluxcraft.MiaoMenu.storage.VariableStore;

final class VariableActionSupport {
    private VariableActionSupport() {
    }

    static ParsedSet parseSet(Player player, String content, Plugin plugin) {
        String[] parts = split(content, 2);
        return new ParsedSet(parseReference(player, parts[0], plugin), parts[1]);
    }

    static ParsedAdd parseAdd(Player player, String content, Plugin plugin) {
        String[] parts = split(content, 2);
        double amount;
        try {
            amount = Double.parseDouble(parts[1]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Variable amount must be numeric", e);
        }
        if (!Double.isFinite(amount)) {
            throw new IllegalArgumentException("Variable amount must be finite");
        }
        return new ParsedAdd(parseReference(player, parts[0], plugin), amount);
    }

    static VariableReference parseRemove(Player player, String content, Plugin plugin) {
        if (content == null || content.isBlank() || content.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Variable action requires one scope:key reference");
        }
        return parseReference(player, content, plugin);
    }

    private static String[] split(String content, int expectedParts) {
        if (content == null) {
            throw new IllegalArgumentException("Variable action content is required");
        }
        String[] parts = content.trim().split("\\s+", expectedParts);
        if (parts.length != expectedParts || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException("Variable action requires scope:key and value");
        }
        return parts;
    }

    private static VariableReference parseReference(Player player, String configured, Plugin plugin) {
        if (!(plugin instanceof MiaoMenu miaoMenu)) {
            throw new IllegalStateException("Variable actions require MiaoMenu");
        }
        String[] parts = configured.split(":", 2);
        if (parts.length != 2 || parts[1].isBlank()) {
            throw new IllegalArgumentException("Variable reference must use player:key or global:key");
        }
        Scope scope = switch (parts[0].toLowerCase(Locale.ROOT)) {
            case "player" -> Scope.PLAYER;
            case "global" -> Scope.GLOBAL;
            default -> throw new IllegalArgumentException("Variable scope must be player or global");
        };
        return new VariableReference(miaoMenu.getVariableStore(), player, scope, parts[1]);
    }

    enum Scope {
        PLAYER,
        GLOBAL
    }

    record VariableReference(VariableStore store, Player player, Scope scope, String key) {
    }

    record ParsedSet(VariableReference reference, String value) {
    }

    record ParsedAdd(VariableReference reference, double amount) {
    }
}

package com.fluxcraft.MiaoMenu.utils;

import java.util.Objects;
import java.util.logging.Level;
import java.util.regex.Pattern;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class PlaceholderUtils {
    /** 仅把合法的旧式颜色码 {@code &a}… 转成 {@code §a}，保留普通 {@code &}。 */
    private static final Pattern AMPERSAND_COLOR = Pattern.compile("(?i)&([0-9a-fk-or])");

    private static volatile boolean placeholderApiChecked = false;
    private static volatile boolean placeholderApiAvailable = false;

    private PlaceholderUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** 供插件重载时重新探测 PlaceholderAPI。 */
    public static void reset() {
        placeholderApiChecked = false;
        placeholderApiAvailable = false;
    }

    private static boolean isPlaceholderApiAvailable() {
        if (!placeholderApiChecked) {
            placeholderApiAvailable = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
            placeholderApiChecked = true;
        }
        return placeholderApiAvailable;
    }

    public static String parse(Player player, String text, Plugin plugin) {
        if (text == null) {
            return "";
        }
        String result = text;
        if (player != null && result.contains("%player")) {
            String playerName = player.getName();
            result = result.replace("%player_name%", playerName);
            result = result.replace("%player%", playerName);
        }
        if (player != null && isPlaceholderApiAvailable()) {
            try {
                result = Objects.requireNonNullElse(
                        PlaceholderAPI.setPlaceholders(player, result), result);
            } catch (RuntimeException | LinkageError e) {
                log(plugin, Level.WARNING, "PlaceholderAPI expansion failed; keeping the raw text.", e);
            }
        }
        // 颜色码转换放在占位符解析之后，这样 PAPI 返回的 &a 也能被正确上色。
        return AMPERSAND_COLOR.matcher(result).replaceAll("\u00a7$1");
    }

    private static void log(Plugin plugin, Level level, String message, Throwable error) {
        if (plugin != null) {
            plugin.getLogger().log(level, message, error);
            return;
        }
        Bukkit.getLogger().log(level, message, error);
    }
}

package com.fluxcraft.MiaoMenu.integration;

import java.util.UUID;

import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.fluxcraft.MiaoMenu.MiaoMenu;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;

/**
 * 把插件变量暴露给 PlaceholderAPI。
 *
 * <p>支持的占位符：</p>
 * <ul>
 *     <li>{@code %miao_player_<key>%} —— 当前玩家的变量</li>
 *     <li>{@code %miao_global_<key>%} —— 全局变量</li>
 * </ul>
 *
 * <p>插件变量此前只能被 API 与动态列表读取，菜单文本里写不出来；
 * 注册这个扩展后，任意支持 PAPI 的地方都能显示变量值。</p>
 */
public final class PlaceholderHook extends PlaceholderExpansion {
    private static final String PLAYER_PREFIX = "player_";
    private static final String GLOBAL_PREFIX = "global_";

    private final MiaoMenu plugin;

    public PlaceholderHook(MiaoMenu plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "miao";
    }

    @Override
    public @NotNull String getAuthor() {
        return "MingLi";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        String key = params.toLowerCase(java.util.Locale.ROOT);
        if (key.startsWith(GLOBAL_PREFIX)) {
            return plugin.getVariableStore()
                    .getGlobal(key.substring(GLOBAL_PREFIX.length()))
                    .orElse("");
        }
        if (!key.startsWith(PLAYER_PREFIX)) {
            return null;
        }
        if (player == null) {
            return "";
        }
        UUID uuid = player.getUniqueId();
        return plugin.getVariableStore()
                .getPlayer(uuid, key.substring(PLAYER_PREFIX.length()))
                .orElse("");
    }

    /** 在 PlaceholderAPI 已安装时注册，失败只记录一次日志。 */
    public static void register(MiaoMenu plugin) {
        Plugin placeholderApi = plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI");
        if (placeholderApi == null || !placeholderApi.isEnabled()) {
            return;
        }
        try {
            new PlaceholderHook(plugin).register();
        } catch (RuntimeException | LinkageError e) {
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "Could not register the MiaoMenu PlaceholderAPI expansion", e);
        }
    }
}

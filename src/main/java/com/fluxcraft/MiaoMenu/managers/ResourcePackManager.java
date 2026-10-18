package com.fluxcraft.MiaoMenu.managers;

import java.util.HexFormat;
import java.util.Locale;

import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.fluxcraft.MiaoMenu.foliacall.FoliaFactory;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * 进服下发资源包（主题图片依赖它）。
 *
 * <p>只在玩家进服后延迟下发一次，避免和登录阶段抢带宽；配置不完整时
 * 只告警一次并跳过，不影响菜单功能。</p>
 */
public final class ResourcePackManager {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final MiaoMenu plugin;
    private volatile boolean warned;

    public ResourcePackManager(MiaoMenu plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("settings.resource-pack.enabled", false)
                && !url().isBlank();
    }

    /** 玩家进服后调用；延迟与是否强制由配置决定。 */
    public void sendOnJoin(Player player) {
        if (player == null || !isEnabled()) {
            return;
        }
        long delay = Math.max(0, plugin.getConfig().getInt("settings.resource-pack.delay-ticks", 20));
        FoliaFactory.getAdapter().runTaskLaterForEntity(plugin, player, () -> {
            if (player.isOnline()) {
                send(player);
            }
        }, delay);
    }

    /** 立即给单个玩家下发（配置错误时只告警一次）。 */
    public void send(Player player) {
        if (player == null || !plugin.getConfig().getBoolean("settings.resource-pack.enabled", false)) {
            return;
        }
        String url = url();
        if (url.isBlank()) {
            warnOnce("Resource pack URL is empty; skipping (settings.resource-pack.url).");
            return;
        }
        byte[] hash = parseSha1(plugin.getConfig().getString("settings.resource-pack.sha1"));
        if (hash == null && !plugin.getConfig().getString("settings.resource-pack.sha1", "").isBlank()) {
            warnOnce("settings.resource-pack.sha1 is not a valid SHA-1 hash; sending without hash check.");
        }
        boolean force = plugin.getConfig().getBoolean("settings.resource-pack.force", false);
        String prompt = plugin.getConfig().getString("settings.resource-pack.prompt", "");
        try {
            if (prompt == null || prompt.isBlank()) {
                player.setResourcePack(url, hash, force);
            } else {
                player.setResourcePack(url, hash, LEGACY.deserialize(prompt), force);
            }
        } catch (RuntimeException | LinkageError e) {
            warnOnce("Failed to send the resource pack: " + e);
        }
    }

    private String url() {
        String configured = plugin.getConfig().getString("settings.resource-pack.url", "");
        return configured == null ? "" : configured.trim();
    }

    private void warnOnce(String message) {
        if (!warned) {
            warned = true;
            plugin.getLogger().warning(message);
        }
    }

    /**
     * 解析 40 位十六进制 SHA-1；空值或非法值返回 {@code null}。
     */
    public static byte @Nullable [] parseSha1(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        if (!normalized.matches("[0-9a-f]{40}")) {
            return null;
        }
        return HexFormat.of().parseHex(normalized);
    }
}

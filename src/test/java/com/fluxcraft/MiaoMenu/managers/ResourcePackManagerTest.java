package com.fluxcraft.MiaoMenu.managers;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HexFormat;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import com.fluxcraft.MiaoMenu.MiaoMenu;

import net.kyori.adventure.text.Component;

class ResourcePackManagerTest {
    private static final String URL = "https://example.com/pack.zip";
    private static final String SHA1 = "a".repeat(40);

    @Test
    void parsesValidSha1() {
        byte[] expected = HexFormat.of().parseHex(SHA1);

        assertArrayEquals(expected, ResourcePackManager.parseSha1(SHA1));
        assertArrayEquals(expected, ResourcePackManager.parseSha1(SHA1.toUpperCase()));
        assertArrayEquals(expected, ResourcePackManager.parseSha1("  " + SHA1 + "  "));
    }

    @Test
    void rejectsInvalidSha1() {
        assertNull(ResourcePackManager.parseSha1(null));
        assertNull(ResourcePackManager.parseSha1(""));
        assertNull(ResourcePackManager.parseSha1("abc"));
        assertNull(ResourcePackManager.parseSha1("z".repeat(40)));
        assertNull(ResourcePackManager.parseSha1("a".repeat(41)));
    }

    @Test
    void sendsPackWithHashAndPrompt() {
        MiaoMenu plugin = pluginWith(true, URL, SHA1, false, "&d加载中");
        ResourcePackManager manager = new ResourcePackManager(plugin);
        Player player = mock(Player.class);

        manager.send(player);

        verify(player).setResourcePack(eq(URL), eq(HexFormat.of().parseHex(SHA1)),
                any(Component.class), eq(false));
    }

    @Test
    void sendsPackWithoutPromptWhenBlank() {
        MiaoMenu plugin = pluginWith(true, URL, "", false, "");
        ResourcePackManager manager = new ResourcePackManager(plugin);
        Player player = mock(Player.class);

        manager.send(player);

        verify(player).setResourcePack(eq(URL), (byte[]) eq(null), eq(false));
    }

    @Test
    void sendsWithoutHashWhenSha1Invalid() {
        MiaoMenu plugin = pluginWith(true, URL, "not-a-hash", true, "");
        ResourcePackManager manager = new ResourcePackManager(plugin);
        Player player = mock(Player.class);

        manager.send(player);

        verify(player).setResourcePack(eq(URL), (byte[]) eq(null), eq(true));
    }

    @Test
    void skipsWhenDisabledOrUrlMissing() {
        Player player = mock(Player.class);

        ResourcePackManager disabled = new ResourcePackManager(pluginWith(false, URL, SHA1, false, ""));
        assertFalse(disabled.isEnabled());
        disabled.send(player);

        ResourcePackManager noUrl = new ResourcePackManager(pluginWith(true, "  ", SHA1, false, ""));
        assertFalse(noUrl.isEnabled());
        noUrl.send(player);

        verify(player, never()).setResourcePack(any(), any(byte[].class), anyBoolean());
        verify(player, never()).setResourcePack(any(), any(byte[].class), any(Component.class), anyBoolean());
    }

    @Test
    void enabledWhenUrlConfigured() {
        assertTrue(new ResourcePackManager(pluginWith(true, URL, "", false, "")).isEnabled());
    }

    private static MiaoMenu pluginWith(boolean enabled, String url, String sha1, boolean force,
                                       String prompt) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("settings.resource-pack.enabled", enabled);
        config.set("settings.resource-pack.url", url);
        config.set("settings.resource-pack.sha1", sha1);
        config.set("settings.resource-pack.force", force);
        config.set("settings.resource-pack.prompt", prompt);
        config.set("settings.resource-pack.delay-ticks", 0);

        MiaoMenu plugin = mock(MiaoMenu.class);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("MiaoMenuTest"));
        return plugin;
    }

    @Test
    void delayDefaultsToConfiguredValue() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("settings.resource-pack.enabled", true);
        config.set("settings.resource-pack.url", URL);
        MiaoMenu plugin = mock(MiaoMenu.class);
        when(plugin.getConfig()).thenReturn(config);

        assertEquals(20, config.getInt("settings.resource-pack.delay-ticks", 20));
        assertTrue(new ResourcePackManager(plugin).isEnabled());
    }
}

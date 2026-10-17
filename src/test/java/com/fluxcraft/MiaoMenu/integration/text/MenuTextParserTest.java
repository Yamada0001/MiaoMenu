package com.fluxcraft.MiaoMenu.integration.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.logging.Logger;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.lone.itemsadder.api.FontImages.FontImageWrapper;
import io.th0rgal.oraxen.glyphs.GlyphTag;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

class MenuTextParserTest {
    private static final Logger LOGGER = Logger.getLogger(MenuTextParserTest.class.getName());

    @BeforeEach
    void resetProviderStubs() {
        FontImageWrapper.reset();
        GlyphTag.reset();
    }

    @Test
    void itemsAdderUsesPlayerPermissionAwareApi() {
        Plugin provider = enabledPlugin();
        Player player = mock(Player.class);
        ItemsAdderTextAdapter adapter = new ItemsAdderTextAdapter(name -> provider, LOGGER);

        assertEquals("IA_PLAYER", adapter.replace(":logo:", player));
        assertSame(player, FontImageWrapper.lastPermissible());
    }

    @Test
    void itemsAdderKeepsTagWhenPluginIsMissing() {
        ItemsAdderTextAdapter adapter = new ItemsAdderTextAdapter(name -> null, LOGGER);

        assertEquals(":logo:", adapter.replace(":logo:", mock(Player.class)));
    }

    @Test
    void oraxenUsesPlayerAwareResolver() {
        Plugin provider = enabledPlugin();
        Player player = mock(Player.class);
        OraxenTextAdapter adapter = new OraxenTextAdapter(name -> provider, LOGGER);

        Component result = adapter.parse("<glyph:logo>", player);

        assertEquals("ORAXEN", PlainTextComponentSerializer.plainText().serialize(result));
        assertSame(player, GlyphTag.lastPlayer());
    }

    @Test
    void convertsLegacyFormattingBeforeOraxenMiniMessageParsing() {
        assertEquals(
                "<green>Hello <bold>world",
                MenuTextParser.convertLegacyToMiniMessage("\u00a7aHello \u00a7lworld")
        );
    }

    @Test
    void detectsProviderTagsWithoutTreatingNormalTextAsGlyphs() {
        Plugin provider = enabledPlugin();
        MenuTextParser parser = new MenuTextParser(mock(com.fluxcraft.MiaoMenu.MiaoMenu.class), name -> provider, LOGGER);

        assertTrue(parser.containsExternalGlyphTag("<image:miaomenu:logo>"));
        assertTrue(parser.containsExternalGlyphTag("<glyph:logo>"));
        assertTrue(parser.containsExternalGlyphTag(":logo:"));
        assertFalse(parser.containsExternalGlyphTag("Server: online"));
    }

    private static Plugin enabledPlugin() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.isEnabled()).thenReturn(true);
        return plugin;
    }
}

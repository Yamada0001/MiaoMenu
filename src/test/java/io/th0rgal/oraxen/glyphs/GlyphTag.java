package io.th0rgal.oraxen.glyphs;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public final class GlyphTag {
    public static final TagResolver RESOLVER = TagResolver.resolver(
            "glyph",
            (arguments, context) -> Tag.inserting(Component.text("ORAXEN"))
    );
    private static Player lastPlayer;

    private GlyphTag() {
    }

    public static TagResolver getResolverForPlayer(Player player) {
        lastPlayer = player;
        return RESOLVER;
    }

    public static Player lastPlayer() {
        return lastPlayer;
    }

    public static void reset() {
        lastPlayer = null;
    }
}

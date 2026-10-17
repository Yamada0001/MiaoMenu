package io.th0rgal.oraxen.glyphs;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public final class ShiftTag {
    public static final TagResolver RESOLVER = TagResolver.resolver(
            "shift",
            (arguments, context) -> Tag.inserting(Component.empty())
    );

    private ShiftTag() {
    }
}

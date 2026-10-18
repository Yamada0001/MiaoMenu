package com.fluxcraft.MiaoMenu.dialogmenu.plus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * 校验 {@code item:} 到资源包图标的映射。
 *
 * <p>{@code image:<id>} 曾经因为含冒号被当成"未知命名空间"而静默丢弃，导致
 * 主题按钮在 Dialog 里没有图标，这里固定该行为。</p>
 */
class DialogIconMappingTest {
    @Test
    void mapsThemeImageItemToCustomAtlasSprite() {
        Button.Icon icon = DialogYaml.iconFromItem("image:spring_sakura");

        assertNotNull(icon, "image: 图标不应被忽略");
        assertEquals("miaomenu:menu", icon.atlas());
        assertEquals("miaomenu:menu/spring_sakura", icon.sprite());
    }

    @Test
    void mapsNamespacedThemeImageWithoutDoubleNamespace() {
        Button.Icon icon = DialogYaml.iconFromItem("image:miaomenu:spring_sakura");

        assertNotNull(icon);
        assertEquals("miaomenu:menu", icon.atlas());
        assertEquals("miaomenu:menu/spring_sakura", icon.sprite());
    }

    @Test
    void mapsVanillaItemToDefaultAtlas() {
        Button.Icon icon = DialogYaml.iconFromItem("NETHER_STAR");

        assertNotNull(icon);
        assertEquals(Button.Icon.DEFAULT_ATLAS, icon.atlas());
        assertEquals("minecraft:item/nether_star", icon.sprite());
    }

    @Test
    void rejectsUnknownNamespacedItemInsteadOfGuessing() {
        assertNull(DialogYaml.iconFromItem("itemsadder:my_item"));
        assertNull(DialogYaml.iconFromItem("image:"));
        assertNull(DialogYaml.iconFromItem("image:../evil"));
    }
}

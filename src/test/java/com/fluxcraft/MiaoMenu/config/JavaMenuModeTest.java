package com.fluxcraft.MiaoMenu.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class JavaMenuModeTest {
    @Test
    void defaultsToInventoryRenderer() {
        assertEquals(JavaMenuMode.DEFAULT, JavaMenuMode.parse(null));
        assertEquals(JavaMenuMode.DEFAULT, JavaMenuMode.parse("unknown"));
        assertEquals(JavaMenuMode.DEFAULT, JavaMenuMode.parse("inventory"));
    }

    @Test
    void acceptsDialogAliases() {
        assertEquals(JavaMenuMode.DIALOG, JavaMenuMode.parse("dialog"));
        assertEquals(JavaMenuMode.DIALOG, JavaMenuMode.parse("native_dialog"));
    }
}

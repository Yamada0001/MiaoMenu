package com.fluxcraft.MiaoMenu.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CustomCommandManagerTest {
    @Test
    void substitutesArgumentsInYamlActions() {
        assertEquals(
                "[message] first second / first / second",
                CustomCommandManager.applyArguments(
                        "[message] %args% / %arg_1% / {arg:2}",
                        new String[] {"first", "second"}
                )
        );
    }
}

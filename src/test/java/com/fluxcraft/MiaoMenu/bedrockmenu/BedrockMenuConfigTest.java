package com.fluxcraft.MiaoMenu.bedrockmenu;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.fluxcraft.MiaoMenu.menu.requirement.RequirementService;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class BedrockMenuConfigTest {
    @Test
    void bundledBedrockExampleUsesSupportedMenuItemsFormat() throws Exception {
        try (InputStream resource = getClass().getResourceAsStream("/bedrock_menus/test.yml")) {
            assertNotNull(resource);

            BedrockMenu menu = parse(YamlConfiguration.loadConfiguration(
                    new InputStreamReader(resource, StandardCharsets.UTF_8)
            ));

            assertFalse(menu.getAllItems().isEmpty());
        }
    }

    @Test
    void readmeBedrockExampleUsesSupportedMenuItemsFormat() {
        String yaml = """
                menu:
                  title: "My Menu"
                  items:
                    - text: "Spawn"
                      icon: "textures/blocks/grass.png"
                      icon_type: path
                      command: "spawn"
                      execute_as: player
                    - text: "Close"
                      command: "close"
                      execute_as: close
                """;

        BedrockMenu menu = parse(YamlConfiguration.loadConfiguration(new StringReader(yaml)));

        assertEquals(2, menu.getAllItems().size());
        assertEquals("close", menu.getAllItems().get(1).getExecuteAs());
    }

    private BedrockMenu parse(YamlConfiguration config) {
        MiaoMenu plugin = mock(MiaoMenu.class);
        RequirementService requirementService = new RequirementService(plugin);
        return new BedrockMenu("test", config, plugin, requirementService);
    }
}

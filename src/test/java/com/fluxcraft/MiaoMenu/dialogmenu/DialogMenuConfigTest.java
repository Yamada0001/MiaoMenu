package com.fluxcraft.MiaoMenu.dialogmenu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.fluxcraft.MiaoMenu.dialogmenu.plus.DialogFZ;
import com.fluxcraft.MiaoMenu.dialogmenu.plus.DialogFZ.GridStyle;
import com.fluxcraft.MiaoMenu.dialogmenu.plus.DialogYaml;
import com.fluxcraft.MiaoMenu.menu.requirement.RequirementService;

class DialogMenuConfigTest {
    @Test
    void bundledDialogExampleUsesSupportedFormat() throws Exception {
        try (InputStream resource = getClass().getResourceAsStream("/dialog_menus/test.yml")) {
            org.junit.jupiter.api.Assertions.assertNotNull(resource);
            MiaoMenu plugin = mock(MiaoMenu.class);
            RequirementService requirementService = new RequirementService(plugin);
            DialogFZ definition = new DialogYaml(requirementService).parse(
                    "test",
                    YamlConfiguration.loadConfiguration(new InputStreamReader(resource, StandardCharsets.UTF_8))
            );

            assertEquals(2, definition.bodies().size());
            assertEquals(4, definition.inputs().size());
            assertEquals(2, definition.buttons().size());
            assertEquals(1, definition.dynamicLists().size());
        }
    }

    @Test
    void parsesBodyInputsButtonsAndLayout() {
        String yaml = """
                title: "&6Test"
                layout:
                  type: multi_action
                  columns: 2
                  grid:
                    size: 18
                    button_width: 18
                  exit_button:
                    label: Close
                body:
                  - type: text
                    text: Hello
                  - type: item
                    material: DIAMOND
                inputs:
                  - type: text
                    key: message
                    label: Message
                  - type: boolean
                    key: enabled
                    label: Enabled
                  - type: number_range
                    key: amount
                    label: Amount
                    start: 1
                    end: 10
                  - type: single_option
                    key: target
                    label: Target
                    options:
                      - id: spawn
                        initial: true
                      - id: shop
                buttons:
                  - label: Submit
                    slot: 10
                    item: NETHER_STAR
                    icon_only: true
                    actions:
                      - "[message] $(message)"
                """;

        DialogFZ definition = parse(yaml);

        assertEquals(2, definition.bodies().size());
        assertEquals(4, definition.inputs().size());
        assertEquals(1, definition.buttons().size());
        assertNotNull(definition.buttons().getFirst().icon());
        assertEquals("minecraft:items", definition.buttons().getFirst().icon().atlas());
        assertEquals("minecraft:item/nether_star", definition.buttons().getFirst().icon().sprite());
        assertEquals(10, definition.buttons().getFirst().slot());
        assertEquals(18, definition.grid().size());
        assertEquals(18, definition.grid().buttonWidth());
        assertEquals("multi_action", definition.layoutType().configName());
        assertEquals(
                java.util.List.of("message", "enabled", "amount", "target"),
                definition.inputs().stream().map(com.fluxcraft.MiaoMenu.dialogmenu.plus.Input::key).toList()
        );
    }

    @Test
    void confirmationRequiresExactlyTwoButtons() {
        String yaml = """
                title: Test
                type: confirmation
                buttons:
                  - label: Only one
                """;

        assertThrows(IllegalArgumentException.class, () -> parse(yaml));
    }

    @Test
    void parsesItemOverlayGridWithoutNativeButtons() {
        String yaml = """
                title: "<image:miaomenu:logo>"
                layout:
                  type: multi_action
                  columns: 9
                  grid:
                    size: 72
                    button_width: 20
                    style: item_overlay
                    overlay_offset_y: 29
                buttons:
                  - label: Server
                    slot: 10
                    item: NETHER_STAR
                    actions:
                      - "[message] Server"
                """;

        DialogFZ definition = parse(yaml);

        assertEquals(GridStyle.ITEM_OVERLAY, definition.grid().style());
        assertEquals(29, definition.grid().overlayOffsetY());
    }

    @Test
    void itemOverlayRejectsInputsBecauseBodyClicksHaveNoResponseView() {
        String yaml = """
                title: Test
                layout:
                  type: multi_action
                  grid:
                    size: 9
                    button_width: 20
                    style: item_overlay
                inputs:
                  - type: text
                    key: message
                    label: Message
                buttons:
                  - slot: 0
                    item: STONE
                """;

        assertThrows(IllegalArgumentException.class, () -> parse(yaml));
    }

    @Test
    void substitutesBothSupportedInputVariableFormats() {
        String value = DialogInter.applyInputValues(
                "[message] $(name) has %input_amount% items",
                Map.of("name", "Alex", "amount", "3")
        );

        assertEquals("[message] Alex has 3 items", value);
    }

    private DialogFZ parse(String yaml) {
        MiaoMenu plugin = mock(MiaoMenu.class);
        RequirementService requirementService = new RequirementService(plugin);
        return new DialogYaml(requirementService).parse(
                "test",
                YamlConfiguration.loadConfiguration(new StringReader(yaml))
        );
    }
}

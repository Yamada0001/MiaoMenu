package com.fluxcraft.MiaoMenu.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.fluxcraft.MiaoMenu.dialogmenu.plus.DialogFZ;
import com.fluxcraft.MiaoMenu.dialogmenu.plus.DialogYaml;
import com.fluxcraft.MiaoMenu.menu.requirement.RequirementService;

/**
 * 内置主题模板必须能被真实解析器加载，避免发布出"示例菜单打不开"的版本。
 */
class BundledThemeTemplateTest {
    @Test
    void bundledSpringDialogTemplateParses() throws Exception {
        DialogFZ definition = parseDialog("/dialog_menus/theme-spring.yml");

        assertEquals(2, definition.buttons().size());
        assertEquals(2, definition.bodies().size());
        assertEquals(1, definition.dynamicLists().size());
        assertEquals("theme-spring", definition.name());
    }

    @Test
    void bundledSpringJavaTemplateDeclaresThemeItems() throws Exception {
        try (InputStream resource = getClass().getResourceAsStream("/java_menus/theme-spring.yml")) {
            assertNotNull(resource);
            YamlConfiguration config = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(resource, StandardCharsets.UTF_8)
            );

            assertEquals(3, config.getInt("rows"));
            assertEquals("image:spring_sakura", config.getString("items.theme_art.material"));
            assertEquals(4, config.getInt("items.theme_art.slot"));
            // 与 dialog 模板互相跳转，形成 java/dialog 双形态
            assertEquals("[menu] dialog:theme-spring",
                    config.getStringList("items.open_dialog.left_click_commands").getFirst());
        }
    }

    private static DialogFZ parseDialog(String path) throws Exception {
        try (InputStream resource = BundledThemeTemplateTest.class.getResourceAsStream(path)) {
            assertNotNull(resource, "缺少内置模板：" + path);
            MiaoMenu plugin = mock(MiaoMenu.class);
            RequirementService requirementService = new RequirementService(plugin);
            return new DialogYaml(requirementService).parse(
                    "theme-spring",
                    YamlConfiguration.loadConfiguration(new InputStreamReader(resource, StandardCharsets.UTF_8))
            );
        }
    }
}

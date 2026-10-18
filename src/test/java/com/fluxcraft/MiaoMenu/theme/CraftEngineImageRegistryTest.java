package com.fluxcraft.MiaoMenu.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * CE 的 {@code <image:...>} 标签依赖 images.yml 注册，这里验证合并写入。
 */
class CraftEngineImageRegistryTest {
    @TempDir
    Path root;

    @Test
    void registersEveryThemeImage() throws Exception {
        Path textures = root.resolve("assets/miaomenu/textures");
        Path png = textures.resolve("gui/sprites/menu/autumn_forest.png");
        Files.createDirectories(png.getParent());
        Files.write(png, new byte[]{1});
        Path images = root.resolve("CraftEngine/configuration/images.yml");

        int registered = new CraftEngineImageRegistry(images, 200, 6).register(textures);

        assertEquals(1, registered);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(images.toFile());
        assertEquals(200, config.getInt("images.miaomenu:autumn_forest.height"));
        assertEquals(6, config.getInt("images.miaomenu:autumn_forest.ascent"));
        assertEquals("miaomenu:theme", config.getString("images.miaomenu:autumn_forest.font"));
        assertEquals("miaomenu:item/menu/autumn_forest.png",
                config.getString("images.miaomenu:autumn_forest.file"));
    }

    @Test
    void preservesExistingEntriesWrittenByHand() throws Exception {
        Path images = root.resolve("CraftEngine/configuration/images.yml");
        Files.createDirectories(images.getParent());
        Files.writeString(images, """
                images:
                  miaomenu:logo:
                    height: 282
                    ascent: 5
                    font: miaomenu:ui
                    file: miaomenu:font/ui/logo.png
                """, StandardCharsets.UTF_8);
        Path textures = root.resolve("assets/miaomenu/textures");
        Path png = textures.resolve("gui/sprites/menu/spring_sakura.png");
        Files.createDirectories(png.getParent());
        Files.write(png, new byte[]{1});

        new CraftEngineImageRegistry(images, 200, 6).register(textures);

        YamlConfiguration config = YamlConfiguration.loadConfiguration(images.toFile());
        // 手写条目必须原样保留
        assertEquals(282, config.getInt("images.miaomenu:logo.height"));
        assertEquals("miaomenu:ui", config.getString("images.miaomenu:logo.font"));
        assertEquals("miaomenu:font/ui/logo.png", config.getString("images.miaomenu:logo.file"));
        // 新条目同时存在
        assertEquals(200, config.getInt("images.miaomenu:spring_sakura.height"));
    }

    @Test
    void isIdempotentWhenNothingChanged() throws Exception {
        Path textures = root.resolve("assets/miaomenu/textures");
        Path png = textures.resolve("gui/sprites/menu/winter_snow.png");
        Files.createDirectories(png.getParent());
        Files.write(png, new byte[]{1});
        Path images = root.resolve("CraftEngine/configuration/images.yml");

        assertEquals(1, new CraftEngineImageRegistry(images, 200, 6).register(textures));
        assertEquals(0, new CraftEngineImageRegistry(images, 200, 6).register(textures));
    }

    @Test
    void clampsAscentToHeight() throws Exception {
        Path textures = root.resolve("assets/miaomenu/textures");
        Path png = textures.resolve("gui/sprites/menu/summer_beach.png");
        Files.createDirectories(png.getParent());
        Files.write(png, new byte[]{1});
        Path images = root.resolve("CraftEngine/configuration/images.yml");

        new CraftEngineImageRegistry(images, 10, 999).register(textures);

        YamlConfiguration config = YamlConfiguration.loadConfiguration(images.toFile());
        assertEquals(10, config.getInt("images.miaomenu:summer_beach.ascent"),
                "ascent 不能超过 height");
    }

    @Test
    void ignoresNonThemeDirectories() throws Exception {
        Path textures = root.resolve("assets/miaomenu/textures");
        Path png = textures.resolve("font/ui/logo.png");
        Files.createDirectories(png.getParent());
        Files.write(png, new byte[]{1});

        assertEquals(0, new CraftEngineImageRegistry(
                root.resolve("CraftEngine/configuration/images.yml"), 200, 6).register(textures));
    }

    @Test
    void backgroundMenuFillsEverySlotExceptButtons() throws Exception {
        Path menus = root.resolve("java_menus");

        assertTrue(JavaBackgroundMenuWriter.write(menus, "autumn_forest", "秋日森林", "theme-autumn"));

        Path file = menus.resolve("theme-autumn_forest-bg.yml");
        assertTrue(Files.isRegularFile(file));
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file.toFile());
        assertEquals(6, config.getInt("rows"), "必须是 6 行 54 格");
        assertEquals("秋日森林", config.getString("menu_title"), "标题应使用主题展示名");

        Set<Integer> occupied = new HashSet<>();
        int backgroundSlots = 0;
        for (String key : config.getConfigurationSection("items").getKeys(false)) {
            int slot = config.getInt("items." + key + ".slot");
            assertTrue(occupied.add(slot), "槽位重复：" + key + " -> " + slot);
            if (key.startsWith("bg_")) {
                backgroundSlots++;
                assertEquals("image:autumn_forest", config.getString("items." + key + ".material"));
            }
        }
        assertEquals(51, backgroundSlots, "除 3 个按钮格外其余 51 格都铺背景");
    }

    @Test
    void dialogButtonPointsAtTheRealMenuName() throws Exception {
        Path menus = root.resolve("java_menus");
        // 主题 ID 是 autumn_forest，但菜单名是 theme-autumn，两者不同
        JavaBackgroundMenuWriter.write(menus, "autumn_forest", "秋日森林", "theme-autumn");

        YamlConfiguration config = YamlConfiguration.loadConfiguration(
                menus.resolve("theme-autumn_forest-bg.yml").toFile());

        assertEquals(List.of("[menu] dialog:theme-autumn"),
                config.getStringList("items.open_dialog.left_click_commands"),
                "跳转按钮必须指向真实存在的菜单名");
    }

    @Test
    void omitsDialogButtonWhenMenuNameIsUnknown() throws Exception {
        Path menus = root.resolve("java_menus");

        JavaBackgroundMenuWriter.write(menus, "spring_sakura", null, null);

        YamlConfiguration config = YamlConfiguration.loadConfiguration(
                menus.resolve("theme-spring_sakura-bg.yml").toFile());
        assertNull(config.getConfigurationSection("items.open_dialog"),
                "不知道菜单名时不应生成一个点不开的按钮");
        assertEquals("spring_sakura", config.getString("menu_title"), "没有展示名时退回主题 ID");
    }

    @Test
    void backgroundMenuIsNotOverwritten() throws Exception {
        Path menus = root.resolve("java_menus");
        assertTrue(JavaBackgroundMenuWriter.write(menus, "winter_snow", null, null));
        Path file = menus.resolve("theme-winter_snow-bg.yml");
        Files.writeString(file, "menu_title: custom\n", StandardCharsets.UTF_8);

        assertFalse(JavaBackgroundMenuWriter.write(menus, "winter_snow", null, null),
                "已存在的菜单不应被覆盖");
        assertEquals("menu_title: custom\n", Files.readString(file));
    }
}

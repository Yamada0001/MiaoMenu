package com.fluxcraft.MiaoMenu.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 验证"把主题贴图直接放进 CE/IA/Oraxen 的资源目录"这条路径。
 */
class ThemePackExporterTest {
    @TempDir
    Path pluginsFolder;

    @Test
    void exportsThemeAssetsIntoProviderDirectory() throws Exception {
        Path themeAssets = pluginsFolder.resolve("MiaoMenu/themes/assets");
        Path model = themeAssets.resolve("miaomenu/models/menu/spring_sakura.json");
        Path texture = themeAssets.resolve("miaomenu/textures/gui/sprites/menu/spring_sakura.png");
        Files.createDirectories(model.getParent());
        Files.createDirectories(texture.getParent());
        Files.writeString(model, "{\"parent\":\"minecraft:item/generated\"}", StandardCharsets.UTF_8);
        Files.write(texture, new byte[]{1, 2, 3});

        ThemePackExporter exporter = new ThemePackExporter(
                pluginsFolder, ThemePackExporter.Provider.ORAXEN);

        ThemePackExporter.ExportResult result = exporter.export(themeAssets);

        assertTrue(result.exported(), () -> "导出失败：" + result.error());
        assertEquals(ThemePackExporter.Provider.ORAXEN, result.provider());
        assertEquals(2, result.files());

        Path target = pluginsFolder.resolve("Oraxen/pack/assets");
        assertTrue(Files.isRegularFile(target.resolve("miaomenu/models/menu/spring_sakura.json")));
        assertTrue(Files.isRegularFile(target.resolve("miaomenu/textures/gui/sprites/menu/spring_sakura.png")));
        // 内容必须原样复制，不能改写
        assertEquals("{\"parent\":\"minecraft:item/generated\"}",
                Files.readString(target.resolve("miaomenu/models/menu/spring_sakura.json")));
    }

    @Test
    void reExportOverwritesOldTextures() throws Exception {
        Path themeAssets = pluginsFolder.resolve("MiaoMenu/themes/assets");
        Path texture = themeAssets.resolve("miaomenu/textures/gui/sprites/menu/winter_snow.png");
        Files.createDirectories(texture.getParent());
        Files.write(texture, new byte[]{1});
        ThemePackExporter exporter = new ThemePackExporter(
                pluginsFolder, ThemePackExporter.Provider.ITEMSADDER);
        exporter.export(themeAssets);

        Files.write(texture, new byte[]{9, 9});
        ThemePackExporter.ExportResult second = exporter.export(themeAssets);

        assertTrue(second.exported());
        Path target = pluginsFolder.resolve("ItemsAdder/data/resource_pack/assets")
                .resolve("miaomenu/textures/gui/sprites/menu/winter_snow.png");
        assertEquals(2, Files.readAllBytes(target).length, "主题更新应覆盖旧贴图");
    }

    @Test
    void skipsWhenNoProviderIsAvailable() {
        ThemePackExporter exporter = new ThemePackExporter(
                pluginsFolder, ThemePackExporter.Provider.NONE);

        ThemePackExporter.ExportResult result = exporter.export(pluginsFolder);

        assertFalse(result.exported());
        assertEquals(ThemePackExporter.Provider.NONE, result.provider());
        assertNotNull(result);
    }

    @Test
    void reportsMissingAssetsInsteadOfThrowing() {
        ThemePackExporter exporter = new ThemePackExporter(
                pluginsFolder, ThemePackExporter.Provider.CRAFTENGINE);

        ThemePackExporter.ExportResult result = exporter.export(pluginsFolder.resolve("does-not-exist"));

        assertFalse(result.exported());
        assertNotNull(result.error());
    }

    @Test
    void hasExportedPackOnlyAfterARealExport() throws Exception {
        Path themeAssets = pluginsFolder.resolve("MiaoMenu/themes/assets");
        Files.createDirectories(themeAssets.resolve("miaomenu/models/menu"));
        Files.writeString(themeAssets.resolve("miaomenu/models/menu/x.json"), "{}");
        ThemePackExporter exporter = new ThemePackExporter(
                pluginsFolder, ThemePackExporter.Provider.CRAFTENGINE);

        assertFalse(exporter.hasExportedPack(), "导出前不应报告已就绪");

        exporter.export(themeAssets);

        assertTrue(exporter.hasExportedPack(), "导出后应报告已就绪");
    }
}

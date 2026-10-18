package com.fluxcraft.MiaoMenu.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 验证主题贴图会被整理成"物品模型能采样到"的标准结构。
 *
 * <p>主题仓库把 png 放在 {@code textures/gui/sprites/menu/}，那不在
 * {@code minecraft:atlases/blocks.json} 的范围内，item_model 取不到纹理；
 * 必须补一份 {@code textures/item/menu/} 并生成 {@code models/item/menu/}。</p>
 */
class ThemeAssetsTest {
    @TempDir
    Path themesRoot;

    @Test
    void generatesItemModelAndCopiesTexture() throws Exception {
        Path source = themesRoot.resolve(
                "assets/miaomenu/textures/gui/sprites/menu/spring_sakura.png");
        Files.createDirectories(source.getParent());
        Files.write(source, new byte[]{1, 2, 3});

        int generated = ThemeAssets.ensure(themesRoot);

        Path texture = themesRoot.resolve("assets/miaomenu/textures/item/menu/spring_sakura.png");
        Path model = themesRoot.resolve("assets/miaomenu/models/item/menu/spring_sakura.json");
        assertTrue(Files.isRegularFile(texture), "应复制出 textures/item 下的标准副本");
        assertTrue(Files.isRegularFile(model), "应生成 models/item 下的模型");
        assertTrue(generated >= 2);

        String json = Files.readString(model, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"layer0\": \"miaomenu:item/menu/spring_sakura\""),
                () -> "模型必须引用 item/ 下的纹理：" + json);
        assertTrue(json.contains("minecraft:item/generated"));
    }

    @Test
    void keepsOriginalSpriteTextureForDialogIcons() throws Exception {
        Path source = themesRoot.resolve(
                "assets/miaomenu/textures/gui/sprites/menu/winter_snow.png");
        Files.createDirectories(source.getParent());
        Files.write(source, new byte[]{9});

        ThemeAssets.ensure(themesRoot);

        assertTrue(Files.isRegularFile(source), "原图不能删，Dialog sprite 图标还依赖它");
        assertTrue(Files.isRegularFile(
                themesRoot.resolve("assets/miaomenu/atlases/menu.json")), "应补回图集");
    }

    @Test
    void isIdempotentAndOnlyRewritesChangedFiles() throws Exception {
        Path source = themesRoot.resolve(
                "assets/miaomenu/textures/gui/sprites/menu/summer_beach.png");
        Files.createDirectories(source.getParent());
        Files.write(source, new byte[]{1});

        assertTrue(ThemeAssets.ensure(themesRoot) > 0);
        assertEquals(0, ThemeAssets.ensure(themesRoot), "内容未变时不应重复写入");
    }

    @Test
    void updatesModelWhenTextureChanges() throws Exception {
        Path source = themesRoot.resolve(
                "assets/miaomenu/textures/gui/sprites/menu/autumn_forest.png");
        Files.createDirectories(source.getParent());
        Files.write(source, new byte[]{1});
        ThemeAssets.ensure(themesRoot);

        Files.write(source, new byte[]{2, 2});
        assertTrue(ThemeAssets.ensure(themesRoot) > 0, "贴图更新后应重新复制");

        Path texture = themesRoot.resolve("assets/miaomenu/textures/item/menu/autumn_forest.png");
        assertEquals(2, Files.readAllBytes(texture).length);
    }

    @Test
    void ignoresFilesOutsideMenuDirectory() throws Exception {
        Path other = themesRoot.resolve("assets/miaomenu/textures/gui/sprites/other/logo.png");
        Files.createDirectories(other.getParent());
        Files.write(other, new byte[]{1});

        assertEquals(0, ThemeAssets.ensure(themesRoot));
        assertTrue(Files.notExists(
                themesRoot.resolve("assets/miaomenu/models/item/menu/logo.json")));
    }

    @Test
    void modelIdMatchesGeneratedModelPath() {
        assertEquals("miaomenu:item/menu/spring_sakura",
                ThemeAssets.modelId("spring_sakura"));
    }
}

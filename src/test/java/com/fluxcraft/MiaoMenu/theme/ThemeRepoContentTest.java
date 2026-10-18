package com.fluxcraft.MiaoMenu.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.fluxcraft.MiaoMenu.dialogmenu.plus.DialogFZ;
import com.fluxcraft.MiaoMenu.dialogmenu.plus.DialogYaml;
import com.fluxcraft.MiaoMenu.menu.requirement.RequirementService;
import com.google.gson.JsonParser;

/**
 * 校验 {@code themes-repo/}（推送到主题仓库的内容）与插件解析逻辑一致。
 *
 * <p>该目录不存在时（例如只拿到 jar 的构建环境）自动跳过。</p>
 */
class ThemeRepoContentTest {
    private static final Path REPO = Path.of("themes-repo");

    @Test
    void manifestCoversAllThemeDirectories() throws Exception {
        Assumptions.assumeTrue(Files.isDirectory(REPO), "themes-repo 不存在，跳过");
        ThemeManifest manifest = ThemeManifest.parse(
                new JsonParser().parse(Files.readString(REPO.resolve("themes.json")))
        );

        assertEquals(4, manifest.themes().size());
        for (ThemeManifest.Theme theme : manifest.themes()) {
            for (ThemeManifest.FileEntry entry : theme.files()) {
                Path source = REPO.resolve(entry.source());
                assertTrue(Files.isRegularFile(source), "清单引用了不存在的文件：" + entry.source());
                if (entry.sha256() != null) {
                    assertTrue(
                            ThemeRepository.matchesSha256(Files.readAllBytes(source), entry.sha256()),
                            "校验和不匹配：" + entry.source()
                    );
                }
            }
        }
    }

    @Test
    void everyThemeDialogMenuParses() throws Exception {
        Assumptions.assumeTrue(Files.isDirectory(REPO), "themes-repo 不存在，跳过");
        List<Path> dialogs = new ArrayList<>();
        try (var stream = Files.walk(REPO.resolve("themes"))) {
            stream.filter(path -> path.toString().replace('\\', '/').contains("/dialog_menus/"))
                    .filter(path -> path.toString().endsWith(".yml"))
                    .forEach(dialogs::add);
        }

        assertEquals(4, dialogs.size(), "四季主题各应提供一个 dialog 模板");
        MiaoMenu plugin = mock(MiaoMenu.class);
        RequirementService requirementService = new RequirementService(plugin);
        for (Path dialog : dialogs) {
            String name = dialog.getFileName().toString().replace(".yml", "");
            DialogFZ definition = new DialogYaml(requirementService).parse(
                    name,
                    YamlConfiguration.loadConfiguration(
                            new InputStreamReader(Files.newInputStream(dialog), StandardCharsets.UTF_8)
                    )
            );
            assertTrue(definition.buttons().size() >= 2, name + " 至少需要两个按钮");
        }
    }

    @Test
    void everyThemeJavaMenuIsValidYamlWithImageItem() throws Exception {
        Assumptions.assumeTrue(Files.isDirectory(REPO), "themes-repo 不存在，跳过");
        List<Path> menus = new ArrayList<>();
        try (var stream = Files.walk(REPO.resolve("themes"))) {
            stream.filter(path -> path.toString().replace('\\', '/').contains("/java_menus/"))
                    .filter(path -> path.toString().endsWith(".yml"))
                    .forEach(menus::add);
        }

        assertEquals(4, menus.size(), "四季主题各应提供一个 java 模板");
        for (Path menu : menus) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(Files.newInputStream(menu), StandardCharsets.UTF_8)
            );
            assertTrue(config.getInt("rows") > 0, menu + " 缺少 rows");
            assertTrue(config.getString("items.theme_art.material", "").startsWith("image:"),
                    menu + " 的 theme_art 应使用 image: 前缀");
        }
    }
}

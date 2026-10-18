package com.fluxcraft.MiaoMenu.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.google.gson.JsonParser;

/**
 * 用本地文件系统模拟远端仓库，验证清单里的路径约束与校验和逻辑。
 *
 * <p>真正的 HTTP 下载需要联网，这里只覆盖"清单 → 目标路径 → 内容校验"这段纯逻辑，
 * 网络失败路径由 {@code UpdateCheckerTest} 的解析测试与手工验证覆盖。</p>
 */
class ThemeInstallLogicTest {
    private static final Path REPO = Path.of("themes-repo");

    @Test
    void manifestTargetsStayInsideDataFolder(@TempDir Path dataFolder) throws Exception {
        Assumptions.assumeTrue(Files.isDirectory(REPO), "themes-repo 不存在，跳过");
        ThemeManifest manifest = ThemeManifest.parse(
                new JsonParser().parse(Files.readString(REPO.resolve("themes.json")))
        );

        List<String> targets = new ArrayList<>();
        for (ThemeManifest.Theme theme : manifest.themes()) {
            for (ThemeManifest.FileEntry entry : theme.files()) {
                Path resolved = dataFolder.resolve(entry.target()).normalize();
                assertTrue(resolved.startsWith(dataFolder),
                        "目标路径逃逸出数据目录：" + entry.target());
                targets.add(entry.target());
            }
        }
        assertFalse(targets.isEmpty());
        assertEquals(targets.size(), targets.stream().distinct().count(), "目标路径不应重复");
    }

    @Test
    void checksumMatchesGeneratedFiles() throws Exception {
        Assumptions.assumeTrue(Files.isDirectory(REPO), "themes-repo 不存在，跳过");
        ThemeManifest manifest = ThemeManifest.parse(
                new JsonParser().parse(Files.readString(REPO.resolve("themes.json")))
        );
        ThemeManifest.Theme theme = manifest.find("spring_sakura");

        for (ThemeManifest.FileEntry entry : theme.files()) {
            byte[] content = Files.readAllBytes(REPO.resolve(entry.source()));
            assertTrue(ThemeRepository.matchesSha256(content, entry.sha256()),
                    "校验和不匹配：" + entry.source());
            assertFalse(ThemeRepository.matchesSha256(content, "0".repeat(64)));
        }
    }

    @Test
    void downloadedDialogMenuLoadsAsMenu() throws Exception {
        Assumptions.assumeTrue(Files.isDirectory(REPO), "themes-repo 不存在，跳过");
        Path dialog = REPO.resolve("themes/winter_snow/dialog_menus/theme-winter.yml");

        YamlConfiguration config = YamlConfiguration.loadConfiguration(
                new InputStreamReader(Files.newInputStream(dialog), StandardCharsets.UTF_8)
        );
        assertEquals("&f&l冬季飘雪", config.getString("title"));
        assertEquals(2, config.getMapList("buttons").size());
    }

    @Test
    void repositoryFallsBackToDefaultWhenConfiguredBadly() {
        MiaoMenu plugin = mock(MiaoMenu.class);
        org.bukkit.configuration.file.YamlConfiguration config =
                new org.bukkit.configuration.file.YamlConfiguration();
        when(plugin.getConfig()).thenReturn(config);
        config.set("settings.theme-repository.repository", "not a repo!!");
        config.set("settings.theme-repository.branch", "!!!");

        // 构造过程不应抛异常；非法配置会退回默认仓库
        ThemeRepository repository = new ThemeRepository(plugin);
        assertTrue(repository.cachedThemeIds().isEmpty());
    }
}

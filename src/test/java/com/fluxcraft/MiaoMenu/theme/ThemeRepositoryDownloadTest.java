package com.fluxcraft.MiaoMenu.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.sun.net.httpserver.HttpServer;

/**
 * 端到端验证主题下载：本地 HTTP 服务模拟 GitHub raw，真实走完
 * "清单 → 下载 → sha256 校验 → 落盘 → 已存在则跳过 → --force 覆盖" 全流程。
 */
class ThemeRepositoryDownloadTest {
    private static final Path REPO = Path.of("themes-repo");

    @TempDir
    Path dataFolder;

    private HttpServer server;
    private final List<String> requested = new CopyOnWriteArrayList<>();

    @BeforeEach
    void startServer() throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(REPO), "themes-repo 不存在，跳过");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath().substring(1);
            requested.add(path);
            Path file = REPO.resolve(path).normalize();
            if (!file.startsWith(REPO) || !Files.isRegularFile(file)) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            byte[] body = Files.readAllBytes(file);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void installsThemeFilesAndReloadsMenus() throws Exception {
        MiaoMenu plugin = pluginMock();
        ThemeRepository repository = new ThemeRepository(plugin, baseUrl());

        ThemeRepository.InstallReport report = install(repository, "winter_snow", false);

        assertTrue(report.success(), () -> "安装失败：" + report.error());
        assertNotNull(report.theme());
        assertEquals("winter_snow", report.theme().id());
        assertEquals(4, report.written().size());
        assertTrue(report.skipped().isEmpty());

        assertTrue(Files.isRegularFile(dataFolder.resolve("java_menus/theme-winter.yml")));
        assertTrue(Files.isRegularFile(dataFolder.resolve("dialog_menus/theme-winter.yml")));
        assertTrue(Files.isRegularFile(dataFolder.resolve(
                "themes/assets/miaomenu/textures/gui/sprites/menu/winter_snow.png")));
        // 下载到的 YAML 必须仍是可解析的合法配置
        YamlConfiguration config = YamlConfiguration.loadConfiguration(
                dataFolder.resolve("java_menus/theme-winter.yml").toFile()
        );
        assertEquals("image:winter_snow", config.getString("items.theme_art.material"));
    }

    @Test
    void skipsExistingFilesUnlessForced() throws Exception {
        MiaoMenu plugin = pluginMock();
        ThemeRepository repository = new ThemeRepository(plugin, baseUrl());

        assertTrue(install(repository, "spring_sakura", false).success());
        Path menu = dataFolder.resolve("java_menus/theme-spring.yml");
        Files.writeString(menu, "# 玩家手改过的菜单\n", StandardCharsets.UTF_8);

        ThemeRepository.InstallReport second = install(repository, "spring_sakura", false);
        assertTrue(second.success());
        assertEquals(0, second.written().size());
        assertEquals(4, second.skipped().size());
        assertEquals("# 玩家手改过的菜单\n", Files.readString(menu), "默认不应覆盖玩家文件");

        ThemeRepository.InstallReport forced = install(repository, "spring_sakura", true);
        assertTrue(forced.success());
        assertEquals(4, forced.written().size());
        assertFalse(Files.readString(menu).startsWith("# 玩家手改过"), "--force 应覆盖");
    }

    @Test
    void unknownThemeReportsFailureWithoutWriting() throws Exception {
        MiaoMenu plugin = pluginMock();
        ThemeRepository repository = new ThemeRepository(plugin, baseUrl());

        ThemeRepository.InstallReport report = install(repository, "does_not_exist", false);

        assertFalse(report.success());
        assertNotNull(report.error());
        try (var stream = Files.list(dataFolder)) {
            assertEquals(0, stream.count(), "失败时不应写入任何文件");
        }
    }

    private ThemeRepository.InstallReport install(ThemeRepository repository, String id, boolean force)
            throws Exception {
        return repository.install(id, force);
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
    }

    private MiaoMenu pluginMock() {
        MiaoMenu plugin = mock(MiaoMenu.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        doNothing().when(plugin).reloadAllMenus();
        return plugin;
    }
}

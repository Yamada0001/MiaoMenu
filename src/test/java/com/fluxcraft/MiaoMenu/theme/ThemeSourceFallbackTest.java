package com.fluxcraft.MiaoMenu.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.fluxcraft.MiaoMenu.net.HttpFetcher;
import com.sun.net.httpserver.HttpServer;

/**
 * 验证多下载源回退：第一个源不可用时自动换下一个，并把成功源记住。
 *
 * <p>对应线上场景是 raw.githubusercontent.com 被重置（TLS handshake 失败）
 * 后自动改用 CDN 后备源。</p>
 */
class ThemeSourceFallbackTest {
    private static final String MANIFEST = """
            {
              "themes": [
                {
                  "id": "demo",
                  "display-name": "Demo",
                  "files": [{"source": "themes/demo/menu.yml", "target": "java_menus/demo.yml"}]
                }
              ]
            }
            """;

    private HttpServer server;
    private final List<String> requested = new CopyOnWriteArrayList<>();

    @TempDir
    Path dataFolder;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath().substring(1);
            requested.add(path);
            String body = switch (path) {
                case "themes.json" -> MANIFEST;
                case "themes/demo/menu.yml" -> "title: Demo\n";
                default -> null;
            };
            if (body == null) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
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
    void fallsBackToTheNextSourceWhenTheFirstOneIsUnreachable() throws Exception {
        ThemeRepository repository = new ThemeRepository(pluginWithSources(List.of(
                "http://127.0.0.1:" + closedPort() + "/{path}",
                baseUrl() + "{path}"
        )));

        ThemeManifest manifest = repository.loadManifestAsync().get();

        assertNotNull(manifest, () -> "回退失败：" + repository.lastManifestError());
        assertEquals("demo", manifest.themes().getFirst().id());
        assertTrue(requested.contains("themes.json"), "后备源应当收到清单请求");
    }

    @Test
    void keepsUsingTheSourceThatWorked() throws Exception {
        ThemeRepository repository = new ThemeRepository(pluginWithSources(List.of(
                "http://127.0.0.1:" + closedPort() + "/{path}",
                baseUrl() + "{path}"
        )));

        assertNotNull(repository.loadManifestAsync().get());
        requested.clear();
        assertNotNull(repository.loadManifestAsync().get());

        assertEquals(List.of("themes.json"), List.copyOf(requested),
                "记住可用源后不应再回到失败的源重试");
    }

    @Test
    void treatsBarePrefixAsADirectoryAndRejectsNonHttpSources() {
        ThemeRepository repository = new ThemeRepository(pluginWithSources(List.of(
                "https://mirror.example.com/themes/",
                "file:///etc/passwd",
                "https://cdn.example.com/{repository}/{branch}/{path}"
        )));

        assertEquals(List.of(
                "https://mirror.example.com/themes/{path}",
                "https://cdn.example.com/Yamada0001/MiaoMenu-theme/main/{path}"
        ), repository.sourceTemplates());
    }

    @Test
    void afterShutdownNoFurtherRequestsAreMade() throws Exception {
        ThemeRepository repository = new ThemeRepository(pluginWithSources(List.of(baseUrl() + "{path}")));
        repository.shutdown();
        requested.clear();

        ThemeManifest manifest = repository.loadManifestAsync().get();

        assertNull(manifest, "禁用后不应再返回清单");
        assertTrue(requested.isEmpty(), "禁用后不应再发起网络请求");
        assertEquals(0, repository.cachedThemeIds().size());
    }

    @Test
    void defaultSourceUsesGhProxy() {
        // 未配置 sources 时必须回落到 gh-proxy，直连 raw/jsDelivr 在受限网络下会握手失败。
        ThemeRepository repository = new ThemeRepository(pluginWithSources(List.of()));

        assertEquals(
                List.of("https://gh-proxy.com/https://raw.githubusercontent.com/"
                        + "Yamada0001/MiaoMenu-theme/main/{path}"),
                repository.sourceTemplates()
        );
    }

    @Test
    void reportsDownloadProgressAndCompletes() throws Exception {
        ThemeRepository repository = new ThemeRepository(pluginWithSources(List.of(baseUrl() + "{path}")));
        repository.loadManifestAsync().get();

        // 通过清单拿到文件条目后直接驱动一次安装，验证进度回调不会中断下载。
        ThemeManifest manifest = repository.cachedManifest();
        assertNotNull(manifest);
        String source = manifest.themes().getFirst().files().getFirst().source();

        List<String> progress = new CopyOnWriteArrayList<>();
        byte[] body = new HttpFetcher(List.of(baseUrl() + "{path}"), "", "", 5000)
                .fetch(source, (done, total) -> progress.add(done + "/" + total), () -> false);

        assertTrue(body.length > 0);
        assertFalse(progress.isEmpty(), "应当至少回调一次进度");
        assertTrue(progress.getLast().endsWith("/" + body.length) || progress.getLast().startsWith(String.valueOf(body.length)),
                () -> "最后一次进度应等于文件长度：" + progress.getLast());
    }

    @Test
    void cancellationStopsTheDownload() {
        HttpFetcher fetcher = new HttpFetcher(List.of(baseUrl() + "{path}"), "", "", 5000);

        assertThrows(HttpFetcher.CancelledException.class,
                () -> fetcher.fetch("themes.json", null, () -> true));
    }

    @Test
    void installsThemeFilesThroughTheConfiguredSource() throws Exception {
        ThemeRepository repository = new ThemeRepository(pluginWithSources(List.of(baseUrl() + "{path}")));

        ThemeRepository.InstallReport report = repository.install("demo", false);

        assertTrue(report.success(), () -> "安装失败：" + report.error());
        assertNotNull(report.theme());
        assertEquals(List.of("java_menus/demo.yml"), report.written());
        Path installed = dataFolder.resolve("java_menus/demo.yml");
        assertTrue(Files.isRegularFile(installed), "主题文件应写入数据目录");
        assertEquals("title: Demo\n", Files.readString(installed));
    }

    @Test
    void secondInstallSkipsExistingFilesUnlessForced() throws Exception {
        ThemeRepository repository = new ThemeRepository(pluginWithSources(List.of(baseUrl() + "{path}")));
        assertTrue(repository.install("demo", false).success());

        ThemeRepository.InstallReport second = repository.install("demo", false);

        assertTrue(second.success());
        assertTrue(second.written().isEmpty(), "默认不应覆盖已存在文件");
        assertEquals(List.of("java_menus/demo.yml"), second.skipped());
    }

    @Test
    void cancelInstallIsIgnoredWhenNothingIsRunning() {
        ThemeRepository repository = new ThemeRepository(pluginWithSources(List.of(baseUrl() + "{path}")));

        assertNull(repository.installingThemeId());
        assertFalse(repository.cancelInstall(), "空闲时不应报告取消成功");
    }

    private static int closedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
    }

    private MiaoMenu pluginWithSources(List<String> sources) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("settings.theme-repository.sources", sources);
        MiaoMenu plugin = mock(MiaoMenu.class);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("MiaoMenuTest"));
        return plugin;
    }
}

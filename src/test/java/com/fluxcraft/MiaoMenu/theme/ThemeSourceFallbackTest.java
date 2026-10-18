package com.fluxcraft.MiaoMenu.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fluxcraft.MiaoMenu.MiaoMenu;
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

    private static int closedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
    }

    private static MiaoMenu pluginWithSources(List<String> sources) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("settings.theme-repository.sources", sources);
        MiaoMenu plugin = mock(MiaoMenu.class);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("MiaoMenuTest"));
        return plugin;
    }
}

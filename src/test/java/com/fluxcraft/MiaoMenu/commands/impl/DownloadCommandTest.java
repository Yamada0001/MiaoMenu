package com.fluxcraft.MiaoMenu.commands.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.fluxcraft.MiaoMenu.theme.ThemeRepository;
import com.sun.net.httpserver.HttpServer;

/**
 * 补全行为测试：清单通过本地 HTTP 服务提供，走真实的加载路径。
 */
class DownloadCommandTest {
    private HttpServer server;
    private MiaoMenu plugin;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = MANIFEST.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        plugin = pluginWithRepository();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void completesThemeIdsFromManifest() {
        DownloadCommand command = new DownloadCommand(plugin);
        CommandSender sender = adminSender(true);

        assertEquals(
                List.of("spring_sakura", "summer_beach"),
                command.tabComplete(sender, new String[]{"s"})
        );
        assertEquals(
                List.of("winter_snow"),
                command.tabComplete(sender, new String[]{"winter"})
        );
    }

    @Test
    void completesForceFlag() {
        DownloadCommand command = new DownloadCommand(plugin);
        CommandSender sender = adminSender(true);

        assertTrue(command.tabComplete(sender, new String[]{"--"}).contains("--force"));
        assertEquals(List.of("--force"), command.tabComplete(sender, new String[]{"spring_sakura", "--"}));
    }

    @Test
    void hidesCompletionWithoutPermission() {
        DownloadCommand command = new DownloadCommand(plugin);
        CommandSender sender = adminSender(false);

        assertEquals(List.of(), command.tabComplete(sender, new String[]{""}));
        assertFalse(command.hasAccess(sender));
    }

    private MiaoMenu pluginWithRepository() {
        MiaoMenu mockedPlugin = mock(MiaoMenu.class);
        when(mockedPlugin.getDataFolder()).thenReturn(new java.io.File("."));
        when(mockedPlugin.getConfig()).thenReturn(new YamlConfiguration());
        ThemeRepository repository = new ThemeRepository(
                mockedPlugin,
                "http://127.0.0.1:" + server.getAddress().getPort() + "/"
        );
        // 预热清单，避免补全测试依赖异步时序
        repository.loadManifestAsync().join();
        when(mockedPlugin.getThemeRepository()).thenReturn(repository);
        return mockedPlugin;
    }

    private static CommandSender adminSender(boolean permission) {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission(ThemeRepository.PERMISSION)).thenReturn(permission);
        return sender;
    }

    private static final String MANIFEST = """
            {
              "schema": 1,
              "themes": [
                {"id":"spring_sakura","files":[{"source":"a.yml","target":"java_menus/theme-spring.yml"}]},
                {"id":"summer_beach","files":[{"source":"b.yml","target":"java_menus/theme-summer.yml"}]},
                {"id":"winter_snow","files":[{"source":"c.yml","target":"java_menus/theme-winter.yml"}]}
              ]
            }
            """;
}

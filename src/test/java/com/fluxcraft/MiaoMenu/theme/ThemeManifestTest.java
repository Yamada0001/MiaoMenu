package com.fluxcraft.MiaoMenu.theme;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThemeManifestTest {
    private static final String MANIFEST = """
            {
              "schema": 1,
              "themes": [
                {
                  "id": "spring_sakura",
                  "name": "春日樱花",
                  "description": "樱花飘落的春季主题",
                  "version": "1.0.0",
                  "menus": { "java": "theme-spring", "dialog": "theme-spring" },
                  "files": [
                    { "source": "themes/spring_sakura/java_menus/theme-spring.yml",
                      "target": "java_menus/theme-spring.yml",
                      "sha256": "abc" },
                    { "source": "themes/spring_sakura/pack/x.png",
                      "target": "themes/assets/miaomenu/x.png" }
                  ]
                }
              ]
            }
            """;

    @Test
    void parsesThemeWithFiles() {
        ThemeManifest manifest = ThemeManifest.parse(new JsonParser().parse(MANIFEST));

        assertNotNull(manifest);
        assertEquals(1, manifest.themes().size());
        ThemeManifest.Theme theme = manifest.themes().getFirst();
        assertEquals("spring_sakura", theme.id());
        assertEquals("春日樱花", theme.displayName());
        assertEquals("theme-spring", theme.javaMenu());
        assertEquals("theme-spring", theme.dialogMenu());
        assertEquals(2, theme.files().size());
    }

    @Test
    void findsThemeCaseInsensitively() {
        ThemeManifest manifest = ThemeManifest.parse(new JsonParser().parse(MANIFEST));

        assertNotNull(manifest);
        assertNotNull(manifest.find("SPRING_SAKURA"));
        assertNull(manifest.find("winter_snow"));
        assertNull(manifest.find(null));
    }

    @Test
    void rejectsTraversalAndAbsoluteTargets() {
        String payload = """
                {
                  "themes": [
                    { "id": "evil", "files": [
                        { "source": "a.yml", "target": "../../config.yml" }
                    ]}
                  ]
                }
                """;
        ThemeManifest manifest = ThemeManifest.parse(new JsonParser().parse(payload));

        assertNotNull(manifest);
        assertTrue(manifest.themes().isEmpty(), "主题因为没有任何合法文件而被丢弃");
    }

    @Test
    void rejectsTargetsOutsideAllowedDirectories() {
        String payload = """
                {
                  "themes": [
                    { "id": "evil", "files": [
                        { "source": "a.yml", "target": "config.yml" }
                    ]}
                  ]
                }
                """;
        ThemeManifest manifest = ThemeManifest.parse(new JsonParser().parse(payload));

        assertNotNull(manifest);
        assertTrue(manifest.themes().isEmpty());
    }

    @Test
    void rejectsUnsafeThemeId() {
        String payload = """
                {
                  "themes": [
                    { "id": "../evil", "files": [
                        { "source": "a.yml", "target": "java_menus/a.yml" }
                    ]}
                  ]
                }
                """;
        ThemeManifest manifest = ThemeManifest.parse(new JsonParser().parse(payload));

        assertNotNull(manifest);
        assertTrue(manifest.themes().isEmpty());
    }

    @Test
    void rejectsFutureSchema() {
        assertNull(ThemeManifest.parse(new JsonParser().parse("{\"schema\": 99, \"themes\": []}")));
    }

    @Test
    void rejectsMalformedPayload() {
        assertNull(ThemeManifest.parse(null));
        assertNull(ThemeManifest.parse(new JsonParser().parse("[]")));
        assertNull(ThemeManifest.parse(new JsonParser().parse("{\"themes\": {}}")));
    }

    @Test
    void pathHelpersRejectUnsafeInput() {
        assertFalse(ThemeManifest.isSafeRelativePath(null));
        assertFalse(ThemeManifest.isSafeRelativePath("/etc/passwd"));
        assertFalse(ThemeManifest.isSafeRelativePath("C:/windows"));
        assertFalse(ThemeManifest.isSafeRelativePath("a/../../b"));
        assertTrue(ThemeManifest.isSafeRelativePath("java_menus/theme.yml"));

        assertTrue(ThemeManifest.isAllowedTarget("themes/assets/x.png"));
        assertFalse(ThemeManifest.isAllowedTarget("plugins/MiaoMenu.jar"));
    }
}

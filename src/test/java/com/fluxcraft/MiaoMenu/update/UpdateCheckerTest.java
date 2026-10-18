package com.fluxcraft.MiaoMenu.update;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateCheckerTest {
    private static final String CURRENT = "V3.1-build.15";

    @Test
    void readsLatestReleasePayload() {
        String payload = """
                {
                  "tag_name": "V3.1-build.16",
                  "name": "V3.1 build #16",
                  "html_url": "https://github.com/Yamada0001/MiaoMenu/releases/tag/V3.1-build.16",
                  "draft": false,
                  "prerelease": false
                }
                """;
        UpdateInfo info = UpdateChecker.fromSingleRelease(parse(payload), CURRENT);

        assertEquals("V3.1-build.16", info.latestVersion());
        assertEquals("V3.1 build #16", info.displayVersion());
        assertTrue(info.updateAvailable());
    }

    @Test
    void reportsUpToDateWhenTagMatches() {
        String payload = """
                {"tag_name": "V3.1-build.15", "name": "current", "html_url": "https://example.invalid"}
                """;
        UpdateInfo info = UpdateChecker.fromSingleRelease(parse(payload), CURRENT);

        assertFalse(info.updateAvailable());
    }

    @Test
    void draftReleaseIsNotAnUpdate() {
        String payload = """
                {"tag_name": "V3.1-build.99", "draft": true, "html_url": "https://example.invalid"}
                """;
        UpdateInfo info = UpdateChecker.fromSingleRelease(parse(payload), CURRENT);

        assertNull(info.latestVersion());
        assertFalse(info.updateAvailable());
    }

    @Test
    void picksNewestNonPrereleaseFromList() {
        String payload = """
                [
                  {"tag_name": "V3.2-build.20", "prerelease": true, "draft": false},
                  {"tag_name": "V3.1-build.17", "prerelease": false, "draft": false,
                   "name": "V3.1 build #17", "html_url": "https://example.invalid/17"},
                  {"tag_name": "V3.1-build.16", "prerelease": false, "draft": false}
                ]
                """;
        UpdateInfo info = UpdateChecker.fromReleaseList(parse(payload), CURRENT, false);

        assertEquals("V3.1-build.17", info.latestVersion());
        assertTrue(info.updateAvailable());
    }

    @Test
    void listMayIncludePrereleaseWhenEnabled() {
        String payload = """
                [
                  {"tag_name": "V3.2-build.20", "prerelease": true, "draft": false},
                  {"tag_name": "V3.1-build.17", "prerelease": false, "draft": false}
                ]
                """;
        UpdateInfo info = UpdateChecker.fromReleaseList(parse(payload), CURRENT, true);

        assertEquals("V3.2-build.20", info.latestVersion());
    }

    @Test
    void emptyListMeansNoRelease() {
        UpdateInfo info = UpdateChecker.fromReleaseList(parse("[]"), CURRENT, false);

        assertNull(info.latestVersion());
        assertFalse(info.updateAvailable());
    }

    @Test
    void malformedPayloadIsTreatedAsNoRelease() {
        assertNull(UpdateChecker.fromSingleRelease(parse("\"text\""), CURRENT).latestVersion());
        assertNull(UpdateChecker.fromReleaseList(parse("{}"), CURRENT, false).latestVersion());
        assertNull(UpdateChecker.fromSingleRelease(null, CURRENT).latestVersion());
    }

    private static JsonElement parse(String json) {
        return new JsonParser().parse(json);
    }
}

package com.fluxcraft.MiaoMenu.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginVersionTest {
    @Test
    void comparesBuildNumbersNumerically() {
        assertTrue(PluginVersion.isNewer("V3.1-build.16", "V3.1-build.9"));
        assertFalse(PluginVersion.isNewer("V3.1-build.9", "V3.1-build.16"));
    }

    @Test
    void treatsBuildSuffixAsNewerThanBaseVersion() {
        assertTrue(PluginVersion.isNewer("V3.1-build.16", "V3.1"));
    }

    @Test
    void identicalVersionsAreNotNewer() {
        assertFalse(PluginVersion.isNewer("V3.1-build.16", "V3.1-build.16"));
        assertEquals(0, PluginVersion.parse("V3.1-build.16").compareTo(PluginVersion.parse("3.1.build.16")));
    }

    @Test
    void comparesMinorVersions() {
        assertTrue(PluginVersion.isNewer("V3.2", "V3.1-build.16"));
        assertFalse(PluginVersion.isNewer("V3.1", "V3.2"));
    }

    @Test
    void releaseIsNewerThanPrerelease() {
        assertTrue(PluginVersion.isNewer("V3.1", "V3.1-beta"));
    }

    @Test
    void ignoresLeadingZeros() {
        assertEquals(0, PluginVersion.parse("V3.01").compareTo(PluginVersion.parse("3.1")));
    }

    @Test
    void rejectsUnparsableInput() {
        assertNull(PluginVersion.parse(null));
        assertNull(PluginVersion.parse("   "));
        assertNull(PluginVersion.parse("---"));
        assertFalse(PluginVersion.isNewer(null, "V3.1"));
        assertFalse(PluginVersion.isNewer("V3.1", ""));
    }
}

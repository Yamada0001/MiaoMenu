package com.fluxcraft.MiaoMenu.integration;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ItemResolverTest {
    private static final String TEXTURE_HASH = "a".repeat(64);
    private static final URI EXPECTED_URI = URI.create("https://textures.minecraft.net/texture/" + TEXTURE_HASH);

    @Test
    void decodesMojangTexturePayload() {
        String payload = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/"
                + TEXTURE_HASH + "\"}}}";
        String encoded = Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        assertEquals(EXPECTED_URI, ItemResolver.resolveSkinTextureUri(encoded));
    }

    @Test
    void rejectsNonMojangTextureUrl() {
        String payload = "{\"textures\":{\"SKIN\":{\"url\":\"https://example.invalid/texture/"
                + TEXTURE_HASH + "\"}}}";
        String encoded = Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        assertNull(ItemResolver.resolveSkinTextureUri(encoded));
    }

    @Test
    void acceptsLegacyTextureHash() {
        assertEquals(EXPECTED_URI, ItemResolver.resolveSkinTextureUri(TEXTURE_HASH));
    }
}

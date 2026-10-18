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

    @Test
    void acceptsUrlSafeAndUnpaddedPayload() {
        String payload = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/"
                + TEXTURE_HASH + "\"}}}";
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        assertEquals(EXPECTED_URI, ItemResolver.resolveSkinTextureUri(encoded));
    }

    @Test
    void ignoresWhitespaceAndNewlinesInPayload() {
        String payload = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/"
                + TEXTURE_HASH + "\"}}}";
        String encoded = Base64.getMimeEncoder(24, "\n".getBytes(StandardCharsets.UTF_8))
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        assertEquals(EXPECTED_URI, ItemResolver.resolveSkinTextureUri(encoded));
    }

    @Test
    void normalizesUppercaseHash() {
        assertEquals(EXPECTED_URI, ItemResolver.resolveSkinTextureUri(TEXTURE_HASH.toUpperCase(java.util.Locale.ROOT)));
    }

    @Test
    void rejectsPayloadWithoutSkinTexture() {
        String payload = "{\"textures\":{\"CAPE\":{\"url\":\"http://textures.minecraft.net/texture/"
                + TEXTURE_HASH + "\"}}}";
        String encoded = Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        assertNull(ItemResolver.resolveSkinTextureUri(encoded));
    }

    @Test
    void rejectsGarbageInsteadOfThrowing() {
        assertNull(ItemResolver.resolveSkinTextureUri("not-a-base64-payload"));
        assertNull(ItemResolver.resolveSkinTextureUri(""));
        assertNull(ItemResolver.resolveSkinTextureUri(null));
        assertNull(ItemResolver.resolveSkinTextureUri("eyJ0ZXh0dXJlcyI6e30="));
    }

    @Test
    void headUuidIsDeterministicPerTexture() {
        String otherHash = "b".repeat(64);

        assertEquals(ItemResolver.headUuid(TEXTURE_HASH), ItemResolver.headUuid(TEXTURE_HASH));
        assertEquals(ItemResolver.headUuid(TEXTURE_HASH), ItemResolver.headUuid(TEXTURE_HASH.toUpperCase(java.util.Locale.ROOT)));
        org.junit.jupiter.api.Assertions.assertNotEquals(
                ItemResolver.headUuid(TEXTURE_HASH),
                ItemResolver.headUuid(otherHash)
        );
    }

    @Test
    void textureUriIsAlwaysHttpsMojangUrl() {
        assertEquals(EXPECTED_URI, ItemResolver.textureUri(TEXTURE_HASH));
    }
}

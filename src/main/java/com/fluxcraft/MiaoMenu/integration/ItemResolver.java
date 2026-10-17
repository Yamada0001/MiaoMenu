package com.fluxcraft.MiaoMenu.integration;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class ItemResolver {
    private static final String TEXTURE_HOST = "textures.minecraft.net";
    private static final Pattern TEXTURE_HASH = Pattern.compile("[0-9a-fA-F]{64}");

    private final MiaoMenu plugin;
    private final Material fallbackMaterial;
    private volatile Boolean craftEngineAvailable;
    private volatile Boolean itemsAdderAvailable;
    private volatile Boolean mmoItemsAvailable;
    private volatile Boolean headDatabaseAvailable;

    // Cached reflection classes (initialized lazily)
    private static volatile boolean ceClassesInit = false;
    private static volatile Class<?> ceKeyClass;
    private static volatile Class<?> ceItemsClass;
    private static volatile Class<?> iaCustomStackClass;
    private static volatile Class<?> mmoItemsClass;
    private static volatile Class<?> headDbMainClass;

    public ItemResolver(MiaoMenu plugin, Material fallbackMaterial) {
        this.plugin = plugin;
        this.fallbackMaterial = fallbackMaterial;
    }

    @NotNull
    public ItemStack resolve(String materialString, int customModelData) {
        if (materialString == null || materialString.isBlank()) {
            return new ItemStack(fallbackMaterial);
        }

        String lower = materialString.toLowerCase();

        if (lower.startsWith("craftengine:")) {
            ItemStack item = resolveCraftEngine(materialString.substring(12));
            if (item != null) return item;
        } else if (lower.startsWith("itemsadder:")) {
            ItemStack item = resolveItemsAdder(materialString.substring(11));
            if (item != null) return item;
        } else if (lower.startsWith("mmoitems:")) {
            ItemStack item = resolveMMOItems(materialString.substring(9));
            if (item != null) return item;
        } else if (lower.startsWith("headdb:")) {
            ItemStack item = resolveHeadDatabase(materialString.substring(7));
            if (item != null) return item;
        } else if (lower.startsWith("base64head:")) {
            ItemStack item = resolveBase64Head(materialString.substring(11));
            if (item != null) return item;
        }

        ItemStack vanillaItem = resolveVanilla(materialString);
        applyCustomModelData(vanillaItem, customModelData);
        return vanillaItem;
    }

    @SuppressWarnings("deprecation")
    public static void applyCustomModelData(ItemStack item, int customModelData) {
        if (customModelData <= 0 || item.getType() == Material.AIR) {
            return;
        }
        var meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.setCustomModelData(customModelData);
        item.setItemMeta(meta);
    }

    @NotNull
    private ItemStack resolveVanilla(String materialString) {
        Material mat = Material.matchMaterial(materialString);
        return new ItemStack(mat != null ? mat : fallbackMaterial);
    }

    private ItemStack resolveCraftEngine(String id) {
        if (isPluginUnavailable("CraftEngine", ref -> craftEngineAvailable = ref)) {
            return null;
        }
        try {
            initCraftEngineClasses();
            Object key = ceKeyClass.getMethod("of", String.class).invoke(null, id);
            Object customItem = invokeById(ceItemsClass, ceKeyClass, key);
            if (customItem != null) {
                return (ItemStack) customItem.getClass().getMethod("buildItemStack").invoke(customItem);
            }
        } catch (Exception e) {
            plugin.getLogger().fine("CraftEngine item not found: " + id);
        }
        return null;
    }

    private static void initCraftEngineClasses() throws ClassNotFoundException {
        if (ceClassesInit) return;
        ceClassesInit = true;
        ceKeyClass = Class.forName("net.momirealms.craftengine.core.util.Key");
        ceItemsClass = Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineItems");
    }

    private static Object invokeById(Class<?> itemsClass, Class<?> keyClass, Object key) throws ReflectiveOperationException {
        return itemsClass.getMethod("byId", keyClass).invoke(null, key);
    }

    private ItemStack resolveItemsAdder(String id) {
        if (isPluginUnavailable("ItemsAdder", ref -> itemsAdderAvailable = ref)) {
            return null;
        }
        try {
            if (iaCustomStackClass == null) {
                iaCustomStackClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
            }
            var customStack = iaCustomStackClass
                    .getMethod("getInstance", String.class).invoke(null, id);
            if (customStack != null) {
                return (ItemStack) customStack.getClass().getMethod("getItemStack").invoke(customStack);
            }
        } catch (Exception e) {
            plugin.getLogger().fine("ItemsAdder item not found: " + id);
        }
        return null;
    }

    private ItemStack resolveMMOItems(String id) {
        if (isPluginUnavailable("MMOItems", ref -> mmoItemsAvailable = ref)) {
            return null;
        }
        try {
            String[] parts = id.split(":", 2);
            if (parts.length != 2) return null;
            var pluginObj = org.bukkit.Bukkit.getPluginManager().getPlugin("MMOItems");
            if (mmoItemsClass == null) {
                mmoItemsClass = Class.forName("net.Indyuce.mmoitems.MMOItems");
            }
            var getItemMethod = mmoItemsClass.getMethod("getItem", String.class, String.class);
            Object itemStack = getItemMethod.invoke(pluginObj, parts[0], parts[1]);
            return (ItemStack) itemStack;
        } catch (Exception e) {
            plugin.getLogger().fine("MMOItems item not found: " + id);
        }
        return null;
    }

    private ItemStack resolveHeadDatabase(String id) {
        if (isPluginUnavailable("HeadDatabase", ref -> headDatabaseAvailable = ref)) {
            return null;
        }
        try {
            if (headDbMainClass == null) {
                headDbMainClass = Class.forName("com.arcaniax.headdatabase.Main");
            }
            var apiMethod = headDbMainClass.getMethod("getHead", String.class);
            var pluginObj = org.bukkit.Bukkit.getPluginManager().getPlugin("HeadDatabase");
            return (ItemStack) apiMethod.invoke(pluginObj, id);
        } catch (Exception e) {
            plugin.getLogger().fine("HeadDatabase head not found: " + id);
        }
        return null;
    }

    private ItemStack resolveBase64Head(String base64) {
        try {
            URI skinTexture = resolveSkinTextureUri(base64);
            if (skinTexture == null) {
                return null;
            }
            var urlClass = Class.forName("org.bukkit.profile.PlayerProfile");
            var server = plugin.getServer();
            var profile = server.createProfile(UUID.randomUUID());
            var textures = profile.getTextures();
            var url = skinTexture.toURL();
            textures.setSkin(url);
            profile.setTextures(textures);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            var meta = head.getItemMeta();
            if (meta != null) {
                var skullMetaClass = meta.getClass();
                skullMetaClass.getMethod("setOwnerProfile", urlClass).invoke(meta, profile);
                head.setItemMeta(meta);
            }
            return head;
        } catch (Exception e) {
            plugin.getLogger().fine("Failed to create base64 head: " + base64.substring(0, Math.min(20, base64.length())) + "...");
        }
        return null;
    }

    static URI resolveSkinTextureUri(String encodedTexture) {
        if (encodedTexture == null || encodedTexture.isBlank()) {
            return null;
        }
        String value = encodedTexture.trim();
        if (TEXTURE_HASH.matcher(value).matches()) {
            return URI.create("https://" + TEXTURE_HOST + "/texture/" + value);
        }
        try {
            byte[] decoded = decodeBase64(value);
            JsonObject root = new JsonParser().parse(new String(decoded, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject textures = root.getAsJsonObject("textures");
            JsonObject skin = textures != null ? textures.getAsJsonObject("SKIN") : null;
            if (skin == null || !skin.has("url")) {
                return null;
            }
            return validateTextureUrl(skin.get("url").getAsString());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static byte[] decodeBase64(String value) {
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException ignored) {
            return Base64.getUrlDecoder().decode(value);
        }
    }

    private static URI validateTextureUrl(String value) {
        URI source = URI.create(value);
        String scheme = source.getScheme();
        if ((scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")))
                || !TEXTURE_HOST.equalsIgnoreCase(source.getHost())
                || source.getPort() != -1
                || source.getUserInfo() != null
                || source.getQuery() != null
                || source.getFragment() != null) {
            return null;
        }
        String path = source.getPath();
        if (path == null || !path.startsWith("/texture/")) {
            return null;
        }
        String textureHash = path.substring("/texture/".length());
        if (!TEXTURE_HASH.matcher(textureHash).matches()) {
            return null;
        }
        return URI.create("https://" + TEXTURE_HOST + "/texture/" + textureHash);
    }

    @FunctionalInterface
    private interface AvailabilitySetter {
        void set(Boolean value);
    }

    private boolean isPluginUnavailable(String pluginName, AvailabilitySetter setter) {
        try {
            Boolean cached = switch (pluginName) {
                case "CraftEngine" -> craftEngineAvailable;
                case "ItemsAdder" -> itemsAdderAvailable;
                case "MMOItems" -> mmoItemsAvailable;
                case "HeadDatabase" -> headDatabaseAvailable;
                default -> null;
            };
            if (cached == null) {
                cached = plugin.getServer().getPluginManager().isPluginEnabled(pluginName);
                setter.set(cached);
            }
            return !cached;
        } catch (Exception e) {
            return true;
        }
    }
}

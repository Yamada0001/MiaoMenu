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
    private final java.util.Set<String> warnedProblems = java.util.concurrent.ConcurrentHashMap.newKeySet();
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

        String lower = materialString.toLowerCase(java.util.Locale.ROOT);

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
        } else if (lower.indexOf(':') > 0) {
            // 带命名空间但不是已知前缀：明确告警，避免静默变成石头。
            warnOnce("unknown-prefix:" + lower, "Unknown item prefix in '" + materialString
                    + "'; falling back to " + fallbackMaterial + ".");
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
        try {
            Material mat = Material.matchMaterial(materialString);
            return new ItemStack(mat != null ? mat : fallbackMaterial);
        } catch (RuntimeException e) {
            warnOnce("vanilla:" + materialString, "Invalid material '" + materialString + "': " + e.getMessage());
            return new ItemStack(fallbackMaterial);
        }
    }

    /** 同一类问题只记录一次，避免每格物品刷屏。 */
    private void warnOnce(String key, String message) {
        if (warnedProblems.add(key)) {
            plugin.getLogger().warning(message);
        }
    }

    private ItemStack resolveCraftEngine(String id) {
        if (isPluginUnavailable("CraftEngine", ref -> craftEngineAvailable = ref)) {
            return null;
        }
        try {
            initCraftEngineClasses();
            if (ceKeyClass == null || ceItemsClass == null) {
                return null;
            }
            Object key = buildCraftEngineKey(id);
            if (key == null) {
                return null;
            }
            Object customItem = invokeById(ceItemsClass, ceKeyClass, key);
            if (customItem != null) {
                ItemStack built = buildCraftEngineItem(customItem);
                if (built != null) {
                    return built.clone();
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            warnOnce("craftengine-api", "CraftEngine integration failed; "
                    + "craftengine: items will fall back to " + fallbackMaterial + ": " + e);
        }
        return null;
    }

    /**
     * 兼容 CraftEngine 的命名空间习惯：不带冒号的 ID 默认属于 {@code craftengine}。
     */
    private static Object buildCraftEngineKey(String id) throws ReflectiveOperationException {
        if (id.indexOf(':') > 0) {
            return ceKeyClass.getMethod("of", String.class).invoke(null, id);
        }
        for (String factory : new String[]{"ce", "withDefaultNamespace"}) {
            try {
                return ceKeyClass.getMethod(factory, String.class).invoke(null, id);
            } catch (NoSuchMethodException ignored) {
                // 该版本没有这个工厂方法，继续尝试下一个。
            }
        }
        return ceKeyClass.getMethod("of", String.class).invoke(null, id);
    }

    /**
     * CraftEngine 26.x 把 {@code buildItemStack()} 改名为 {@code buildBukkitItem()}，
     * 这里按顺序探测，避免版本升级后整类物品静默失效。
     */
    private static ItemStack buildCraftEngineItem(Object customItem) {
        for (String methodName : new String[]{"buildItemStack", "buildBukkitItem"}) {
            try {
                Object result = customItem.getClass().getMethod(methodName).invoke(customItem);
                if (result instanceof ItemStack stack) {
                    return stack;
                }
            } catch (NoSuchMethodException ignored) {
                // 换下一个候选方法名。
            } catch (ReflectiveOperationException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 只有全部类都加载成功后才置位，失败时回滚标志以便下次重试。
     */
    private static synchronized void initCraftEngineClasses() {
        if (ceClassesInit) {
            return;
        }
        try {
            Class<?> keyClass = Class.forName("net.momirealms.craftengine.core.util.Key");
            Class<?> itemsClass = Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineItems");
            ceKeyClass = keyClass;
            ceItemsClass = itemsClass;
            ceClassesInit = true;
        } catch (ClassNotFoundException | LinkageError e) {
            ceKeyClass = null;
            ceItemsClass = null;
            ceClassesInit = false;
        }
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
                Object raw = customStack.getClass().getMethod("getItemStack").invoke(customStack);
                if (raw instanceof ItemStack stack) {
                    // 第三方可能返回内部模板实例，clone 后再交给菜单改写名称/lore。
                    return stack.clone();
                }
            }
        } catch (ClassNotFoundException e) {
            warnOnce("itemsadder-api", "ItemsAdder API class not found; "
                    + "itemsadder: items will fall back to " + fallbackMaterial + ".");
        } catch (ReflectiveOperationException | RuntimeException e) {
            warnOnce("itemsadder-api", "ItemsAdder integration failed: " + e);
        }
        return null;
    }

    private ItemStack resolveMMOItems(String id) {
        if (isPluginUnavailable("MMOItems", ref -> mmoItemsAvailable = ref)) {
            return null;
        }
        String[] parts = id.split(":", 2);
        if (parts.length != 2) {
            warnOnce("mmoitems-format:" + id,
                    "MMOItems reference '" + id + "' must be mmoitems:<type>:<id>.");
            return null;
        }
        try {
            var pluginObj = org.bukkit.Bukkit.getPluginManager().getPlugin("MMOItems");
            if (mmoItemsClass == null) {
                mmoItemsClass = Class.forName("net.Indyuce.mmoitems.MMOItems");
            }
            var getItemMethod = mmoItemsClass.getMethod("getItem", String.class, String.class);
            Object itemStack = getItemMethod.invoke(pluginObj, parts[0], parts[1]);
            if (itemStack instanceof ItemStack stack) {
                return stack.clone();
            }
        } catch (ClassNotFoundException e) {
            warnOnce("mmoitems-api", "MMOItems API class not found; "
                    + "mmoitems: items will fall back to " + fallbackMaterial + ".");
        } catch (ReflectiveOperationException | RuntimeException e) {
            warnOnce("mmoitems-api", "MMOItems integration failed: " + e);
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
            Object head = apiMethod.invoke(pluginObj, id);
            if (head instanceof ItemStack stack) {
                return stack.clone();
            }
        } catch (ClassNotFoundException e) {
            warnOnce("headdb-api", "HeadDatabase API class not found; "
                    + "headdb: items will fall back to " + fallbackMaterial + ".");
        } catch (ReflectiveOperationException | RuntimeException e) {
            warnOnce("headdb-api", "HeadDatabase integration failed: " + e);
        }
        return null;
    }

    private ItemStack resolveBase64Head(String base64) {
        try {
            URI skinTexture = resolveSkinTextureUri(base64);
            if (skinTexture == null) {
                return null;
            }
            org.bukkit.profile.PlayerProfile profile = plugin.getServer().createProfile(UUID.randomUUID());
            org.bukkit.profile.PlayerTextures textures = profile.getTextures();
            textures.setSkin(skinTexture.toURL());
            profile.setTextures(textures);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            if (head.getItemMeta() instanceof org.bukkit.inventory.meta.SkullMeta skullMeta) {
                skullMeta.setOwnerProfile(profile);
                head.setItemMeta(skullMeta);
            }
            return head;
        } catch (RuntimeException | java.net.MalformedURLException e) {
            warnOnce("base64head:" + base64.substring(0, Math.min(24, base64.length())),
                    "Failed to build a base64 head: " + e.getMessage());
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

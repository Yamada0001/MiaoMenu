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
    /** 资源包物品模型 ID：{@code [命名空间:]路径}。 */
    private static final Pattern IMAGE_ID = Pattern.compile("[a-z0-9_.-]+(:[a-z0-9_./-]+)?");
    /** 头像 profile 的占位名字：只用于让 profile 完整，不参与渲染。 */
    private static final String HEAD_PROFILE_NAME = "MiaoMenuHead";

    private final MiaoMenu plugin;
    private final Material fallbackMaterial;
    private final java.util.Set<String> warnedProblems = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** 纹理 hash → 已构建好的头颅模板，重复取值只做 clone。 */
    private final java.util.Map<String, ItemStack> headCache = new java.util.concurrent.ConcurrentHashMap<>();
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
        } else if (lower.startsWith("image:")) {
            return resolveThemeImage(materialString.substring(6), customModelData);
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

    /**
     * 释放缓存的第三方插件类引用（插件禁用时调用）。
     *
     * <p>这些静态字段指向 CraftEngine / ItemsAdder / MMOItems / HeadDatabase
     * 类加载器里的类，热重载场景下不清理会阻止它们被回收。</p>
     */
    public static synchronized void resetReflectionCache() {
        ceClassesInit = false;
        ceKeyClass = null;
        ceItemsClass = null;
        iaCustomStackClass = null;
        mmoItemsClass = null;
        headDbMainClass = null;
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

    /**
     * {@code image:<模型ID>}：引用随插件发布的四季主题图（或任意资源包物品模型）。
     *
     * <p>图片本体在 {@code themes/} 资源包里，模型路径是
     * {@code assets/miaomenu/models/menu/<id>.json}，因此 item_model 必须是
     * {@code miaomenu:menu/<id>}——少了 {@code menu/} 这一层客户端就找不到模型，
     * 物品会退化成普通纸张。</p>
     */
    private ItemStack resolveThemeImage(String id, int customModelData) {
        String key = id == null ? "" : id.trim().toLowerCase(java.util.Locale.ROOT);
        if (!IMAGE_ID.matcher(key).matches()) {
            warnOnce("image-id:" + key,
                    "Invalid image id '" + key + "'; expected something like image:spring_sakura.");
            return placeholder("?", "invalid-id");
        }
        String modelId = themeImageModelId(key);
        if (modelId == null) {
            warnOnce("image-id:" + key, "Invalid image id '" + key + "'.");
            return placeholder(key, "invalid-id");
        }
        org.bukkit.NamespacedKey modelKey = org.bukkit.NamespacedKey.fromString(modelId);
        ItemStack item = new ItemStack(Material.PAPER);
        if (modelKey == null) {
            warnOnce("image-key:" + key, "Invalid image model key '" + modelId + "'.");
            return placeholder(key, "invalid-key");
        }
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            try {
                meta.setItemModel(modelKey);
                item.setItemMeta(meta);
            } catch (RuntimeException | LinkageError e) {
                // 旧服务端没有 item_model 组件时保持普通纸张，不影响菜单打开
                warnOnce("image-model:" + key,
                        "Server does not support item_model, '" + key + "' renders as plain paper: " + e);
                return placeholder(key, "no-item-model");
            }
        }
        // 资源包没启用时贴图不会出现，给一个可读的占位名，避免"空白纸片"让人困惑。
        if (!isThemePackActive()) {
            warnOnce("image-pack:" + key,
                    "Theme image '" + key + "' needs the resource pack; configure "
                            + "settings.resource-pack.url and reload to show it.");
            ItemStack placeholder = placeholder(key, "no-pack");
            applyCustomModelData(placeholder, customModelData);
            return placeholder;
        }
        applyCustomModelData(item, customModelData);
        return item;
    }

    /**
     * 主题贴图不可用时的占位物品。
     *
     * <p>用纸张 + 可读名字，而不是静默显示一张空白纸；名字里的 id 便于服主
     * 对照资源包排查缺哪张贴图。</p>
     */
    private ItemStack placeholder(String id, String reason) {
        ItemStack item = new ItemStack(Material.PAPER);
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String label = com.fluxcraft.MiaoMenu.utils.Lang.get("menu.image-placeholder")
                    .replace("{0}", id == null || id.isBlank() ? "?" : id);
            meta.displayName(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacyAmpersand()
                    .deserialize(label)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 把 {@code image:} 后面的写法归一成资源包里的模型 ID。
     *
     * <p>模型位于 {@code assets/miaomenu/models/item/menu/<id>.json}，也就是
     * {@code miaomenu:item/menu/<id>}——贴图必须放在 {@code textures/item/} 下，
     * 否则物品模型采样不到（{@code gui/sprites} 只服务于 Dialog 的 sprite 图标）。</p>
     *
     * <ul>
     *   <li>{@code spring_sakura} → {@code miaomenu:item/menu/spring_sakura}</li>
     *   <li>{@code miaomenu:spring_sakura} → {@code miaomenu:item/menu/spring_sakura}</li>
     *   <li>{@code miaomenu:item/menu/spring_sakura} → 原样保留（已写全路径）</li>
     * </ul>
     */
    static @org.jspecify.annotations.Nullable String themeImageModelId(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String namespace = com.fluxcraft.MiaoMenu.theme.ThemeAssets.NAMESPACE;
        String path = key.trim();
        int colon = path.indexOf(':');
        if (colon > 0) {
            namespace = path.substring(0, colon);
            path = path.substring(colon + 1);
        }
        if (path.isBlank()) {
            return null;
        }
        // 已经写了完整路径（如 item/menu/spring_sakura）就不要再加前缀。
        return path.indexOf('/') >= 0
                ? namespace + ":" + path
                : namespace + ":" + com.fluxcraft.MiaoMenu.theme.ThemeAssets.MODEL_PATH_PREFIX + path;
    }

    /** 资源包是否真的在给客户端发主题贴图。 */
    private boolean isThemePackActive() {
        try {
            if (plugin.getResourcePackManager() != null
                    && plugin.getResourcePackManager().isEnabled()) {
                return true;
            }
            // 已把贴图导出给 CraftEngine / ItemsAdder / Oraxen 时，由它们负责下发。
            return plugin.getThemePackExporter() != null
                    && plugin.getThemePackExporter().hasExportedPack();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private ItemStack resolveBase64Head(String base64) {
        String textureHash = extractTextureHash(base64);
        if (textureHash == null) {
            warnOnce("base64head:" + preview(base64),
                    "Ignoring an invalid base64 head payload; a plain player head is used instead.");
            return new ItemStack(Material.PLAYER_HEAD);
        }
        return buildTexturedHead(textureHash);
    }

    /**
     * 用固定的纹理 hash 构建头颅。
     *
     * <p>profile 的 UUID 由纹理 hash 派生，因此同一份 Base64 数据每次都会生成
     * 完全相同的物品（物品相等性、堆叠与测试断言都依赖这一点）。
     * 构建结果按 hash 缓存，避免每个格子重复构造 profile。</p>
     */
    private ItemStack buildTexturedHead(String textureHash) {
        ItemStack cached = headCache.get(textureHash);
        if (cached != null) {
            return cached.clone();
        }
        try {
            org.bukkit.profile.PlayerProfile profile = plugin.getServer().createProfileExact(
                    headUuid(textureHash),
                    HEAD_PROFILE_NAME
            );
            org.bukkit.profile.PlayerTextures textures = profile.getTextures();
            textures.setSkin(textureUri(textureHash).toURL());
            profile.setTextures(textures);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            if (head.getItemMeta() instanceof org.bukkit.inventory.meta.SkullMeta skullMeta) {
                skullMeta.setOwnerProfile(profile);
                head.setItemMeta(skullMeta);
            }
            headCache.put(textureHash, head);
            return head.clone();
        } catch (RuntimeException | java.net.MalformedURLException | LinkageError e) {
            // LinkageError：服务端缺少 profile API 或模块访问受限时也要降级，不能让菜单整页失败
            warnOnce("base64head:" + textureHash,
                    "Failed to build a base64 head, a plain player head is used instead: " + e);
            return new ItemStack(Material.PLAYER_HEAD);
        }
    }

    /** 纹理 hash → 稳定的离线 UUID。 */
    static UUID headUuid(String textureHash) {
        return UUID.nameUUIDFromBytes(("MiaoMenuHead:" + normalizeHash(textureHash)).getBytes(StandardCharsets.UTF_8));
    }

    static URI textureUri(String textureHash) {
        return URI.create("https://" + TEXTURE_HOST + "/texture/" + normalizeHash(textureHash));
    }

    /** 纹理 hash 统一小写，避免同一张皮肤因大小写产生两份缓存与两个 UUID。 */
    private static String normalizeHash(String textureHash) {
        return textureHash == null ? "" : textureHash.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String preview(String base64) {
        String value = base64 == null ? "" : base64.trim();
        return value.substring(0, Math.min(24, value.length()));
    }

    static URI resolveSkinTextureUri(String encodedTexture) {
        String textureHash = extractTextureHash(encodedTexture);
        return textureHash == null ? null : textureUri(textureHash);
    }

    /**
     * 从"纹理 hash"或 Mojang 的 Base64 皮肤数据里取出 64 位纹理 hash。
     *
     * <p>容错范围：允许直接写 hash、允许标准 / URL-safe 两种 Base64、允许缺少
     * {@code =} 填充、允许数据中夹带换行或空格。任何无法识别的输入都返回
     * {@code null}，由调用方决定降级方式。</p>
     */
    static @org.jetbrains.annotations.Nullable String extractTextureHash(String encodedTexture) {
        if (encodedTexture == null) {
            return null;
        }
        String value = encodedTexture.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (TEXTURE_HASH.matcher(value).matches()) {
            return value.toLowerCase(java.util.Locale.ROOT);
        }
        byte[] decoded = decodeBase64(value);
        if (decoded == null) {
            return null;
        }
        try {
            String json = new String(decoded, StandardCharsets.UTF_8);
            JsonObject root = new JsonParser().parse(json).getAsJsonObject();
            JsonObject textures = root.getAsJsonObject("textures");
            JsonObject skin = textures != null ? textures.getAsJsonObject("SKIN") : null;
            if (skin == null || !skin.has("url")) {
                return null;
            }
            String url = skin.get("url").getAsString();
            return textureHashFromUrl(url);
        } catch (RuntimeException e) {
            // 非 JSON、结构不符或类型不符都按无效数据处理
            return null;
        }
    }

    /**
     * 只接受 Mojang 官方纹理地址，且只保留 {@code /texture/<hash>} 部分。
     */
    private static @org.jetbrains.annotations.Nullable String textureHashFromUrl(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        URI source;
        try {
            source = URI.create(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
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
        return TEXTURE_HASH.matcher(textureHash).matches()
                ? textureHash.toLowerCase(java.util.Locale.ROOT)
                : null;
    }

    /**
     * 兼容标准 / URL-safe、带或不带填充的 Base64；无法解码时返回 {@code null}。
     */
    private static byte @org.jetbrains.annotations.Nullable [] decodeBase64(String value) {
        String compact = value.replaceAll("\\s+", "");
        if (compact.isEmpty()) {
            return null;
        }
        for (Base64.Decoder decoder : DECODERS) {
            try {
                return decoder.decode(compact);
            } catch (IllegalArgumentException ignored) {
                // 换下一种编码变体
            }
        }
        return null;
    }

    private static final java.util.List<Base64.Decoder> DECODERS = java.util.List.of(
            Base64.getDecoder(),
            Base64.getUrlDecoder(),
            Base64.getMimeDecoder()
    );

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

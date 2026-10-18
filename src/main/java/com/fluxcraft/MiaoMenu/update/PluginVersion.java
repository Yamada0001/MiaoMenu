package com.fluxcraft.MiaoMenu.update;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * 可比较的版本号。
 *
 * <p>插件自身版本形如 {@code V3.1}，而 GitHub Release 的标签形如 {@code V3.1-build.16}，
 * 因此不能直接用字符串比较。这里把版本拆成"数字段 / 字母段"交替的序列：</p>
 *
 * <ul>
 *   <li>数字段按数值比较，{@code build.16} 因此大于 {@code build.9}；</li>
 *   <li>字母段按字典序比较，且数字段大于同位置的字母段，
 *       所以 {@code 3.1} 大于 {@code 3.1-beta}；</li>
 *   <li>前缀相同时，段数更多的一方更新，{@code V3.1-build.16} 因此大于 {@code V3.1}。</li>
 * </ul>
 */
public final class PluginVersion implements Comparable<PluginVersion> {
    /** 预发布限定词：带这些词的版本比同前缀的正式版更旧。 */
    private static final java.util.Set<String> PRERELEASE_MARKERS = java.util.Set.of(
            "alpha", "beta", "rc", "pre", "preview", "snapshot", "dev", "nightly", "canary"
    );

    private final String raw;
    private final List<String> segments;

    private PluginVersion(String raw, List<String> segments) {
        this.raw = raw;
        this.segments = segments;
    }

    /**
     * 解析版本号；无法解析时返回 {@code null}，由调用方决定降级方式。
     */
    public static @Nullable PluginVersion parse(@Nullable String version) {
        if (version == null) {
            return null;
        }
        String trimmed = version.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        List<String> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean currentIsDigit = false;
        boolean started = false;
        for (int index = 0; index < trimmed.length(); index++) {
            char character = trimmed.charAt(index);
            boolean digit = Character.isDigit(character);
            if (!Character.isLetterOrDigit(character)) {
                // 分隔符（. - _ + 空格等）结束当前段
                if (started && current.length() > 0) {
                    segments.add(normalize(current.toString(), currentIsDigit));
                    current.setLength(0);
                }
                started = false;
                continue;
            }
            if (!started) {
                started = true;
                currentIsDigit = digit;
            } else if (digit != currentIsDigit) {
                segments.add(normalize(current.toString(), currentIsDigit));
                current.setLength(0);
                currentIsDigit = digit;
            }
            current.append(character);
        }
        if (started && current.length() > 0) {
            segments.add(normalize(current.toString(), currentIsDigit));
        }
        // 开头的 v/V 只是前缀（v3.1 与 3.1 等价）
        if (!segments.isEmpty() && "v".equals(segments.getFirst())) {
            segments.removeFirst();
        }
        if (segments.isEmpty()) {
            return null;
        }
        return new PluginVersion(trimmed, List.copyOf(segments));
    }

    private static String normalize(String segment, boolean digit) {
        return digit ? stripLeadingZeros(segment) : segment.toLowerCase(Locale.ROOT);
    }

    private static String stripLeadingZeros(String value) {
        int index = 0;
        while (index < value.length() - 1 && value.charAt(index) == '0') {
            index++;
        }
        return value.substring(index);
    }

    /** 原始版本字符串。 */
    public String raw() {
        return raw;
    }

    /**
     * @return 大于 0 表示本版本更新，小于 0 表示对方更新，0 表示完全相同
     */
    @Override
    public int compareTo(PluginVersion other) {
        int shared = Math.min(segments.size(), other.segments.size());
        for (int index = 0; index < shared; index++) {
            int result = compareSegment(segments.get(index), other.segments.get(index));
            if (result != 0) {
                return result;
            }
        }
        if (segments.size() == other.segments.size()) {
            return 0;
        }
        // 前缀相同：多出来的那一段决定新旧
        boolean leftLonger = segments.size() > other.segments.size();
        String extra = (leftLonger ? segments : other.segments).get(shared);
        int result = compareExtraSegment(extra);
        return leftLonger ? result : -result;
    }

    /**
     * 判断"多出来的那一段"代表更新还是更旧。
     *
     * <ul>
     *   <li>数字段（{@code 3.1.1}、{@code build.16}）代表更新；</li>
     *   <li>{@code beta}/{@code rc}/{@code snapshot} 等预发布标记代表更旧；</li>
     *   <li>其它限定词（如 {@code build}）按后发布的构建处理，代表更新。</li>
     * </ul>
     */
    private static int compareExtraSegment(String extra) {
        if (isDigit(extra)) {
            return 1;
        }
        return PRERELEASE_MARKERS.contains(extra) ? -1 : 1;
    }

    private static int compareSegment(String left, String right) {
        boolean leftDigit = isDigit(left);
        boolean rightDigit = isDigit(right);
        if (leftDigit && rightDigit) {
            return compareNumeric(left, right);
        }
        if (leftDigit != rightDigit) {
            // 数字段优先于字母段：3.1 > 3.1-beta
            return leftDigit ? 1 : -1;
        }
        return left.compareTo(right);
    }

    private static boolean isDigit(String segment) {
        return !segment.isEmpty() && Character.isDigit(segment.charAt(0));
    }

    /** 逐位比较，避免超长数字段溢出。 */
    private static int compareNumeric(String left, String right) {
        if (left.length() != right.length()) {
            return Integer.compare(left.length(), right.length());
        }
        return left.compareTo(right);
    }

    /** 便捷比较：{@code candidate} 是否比 {@code current} 更新。 */
    public static boolean isNewer(@Nullable String candidate, @Nullable String current) {
        PluginVersion candidateVersion = parse(candidate);
        PluginVersion currentVersion = parse(current);
        if (candidateVersion == null || currentVersion == null) {
            // 任一方无法解析时不做"有更新"的判断，避免误报
            return false;
        }
        return candidateVersion.compareTo(currentVersion) > 0;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PluginVersion version)) {
            return false;
        }
        return compareTo(version) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(segments);
    }

    @Override
    public String toString() {
        return raw;
    }
}

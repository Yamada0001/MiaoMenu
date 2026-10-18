package com.fluxcraft.MiaoMenu.net;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

import javax.net.ssl.SSLException;

import org.jspecify.annotations.Nullable;

/**
 * 共享的 HTTP 抓取器：统一 URL 模板、多源回退与错误归一化。
 *
 * <p>更新检查与主题下载都走这里，避免两套网络逻辑各自处理超时、重定向与
 * 失败原因。源模板支持 {@code {repository}}、{@code {branch}}、{@code {path}}
 * 三个占位符；按顺序尝试，成功的源会被记住并优先复用。</p>
 *
 * <p>失败原因会被整理成可读文案（例如 TLS 握手被拦截时提示更换下载源），
 * 而不是把原始异常直接抛给玩家。</p>
 */
public final class HttpFetcher {
    private static final int DEFAULT_TIMEOUT_MILLIS = 8000;
    private static final String USER_AGENT = "MiaoMenu-Network";

    /** 下载进度回调；{@code total <= 0} 表示服务端没给长度。 */
    @FunctionalInterface
    public interface ProgressListener {
        void onProgress(long downloaded, long total);
    }

    /** 下载被主动取消。 */
    public static class CancelledException extends IOException {
        public CancelledException(String message) {
            super(message);
        }
    }

    private final HttpClient client;
    private final Duration timeout;
    private final List<String> templates;
    private final String repository;
    private final String branch;
    private volatile int preferredSourceIndex;

    /**
     * @param sources    源模板（至少一个），支持 {@code {repository}}/{@code {branch}}/{@code {path}}
     * @param repository 仓库 {@code owner/repo}
     * @param branch     分支或标签
     */
    public HttpFetcher(List<String> sources, String repository, String branch, int timeoutMillis) {
        this.timeout = Duration.ofMillis(Math.min(60_000, Math.max(1_000, timeoutMillis)));
        this.repository = repository == null ? "" : repository;
        this.branch = branch == null ? "" : branch;
        List<String> resolved = new ArrayList<>();
        for (String template : sanitize(sources)) {
            resolved.add(template
                    .replace("{repository}", this.repository)
                    .replace("{branch}", this.branch));
        }
        this.templates = List.copyOf(resolved);
        this.client = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** 用一个固定基址构造（{@code base} 末尾斜杠可选），主要给更新检查这类单源场景用。 */
    public static HttpFetcher forBaseUrl(String baseUrl, int timeoutMillis) {
        String base = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        return new HttpFetcher(List.of(base + "{path}"), "", "", timeoutMillis);
    }

    public int defaultTimeoutMillis() {
        return (int) timeout.toMillis();
    }

    /** 当前生效的源模板（已替换仓库与分支）。 */
    public List<String> sources() {
        return templates;
    }

    /** 解析某个仓库内路径的最终 URL（取当前优先源）。 */
    public String resolve(String path) {
        return templates.get(preferredSourceIndex % templates.size()).replace("{path}", path);
    }

    /** 抓取文本（UTF-8）。 */
    public String fetchText(String path) throws IOException {
        return new String(fetch(path, null, null), java.nio.charset.StandardCharsets.UTF_8);
    }

    /** 抓取字节，不报告进度。 */
    public byte[] fetch(String path) throws IOException {
        return fetch(path, null, null);
    }

    /**
     * 抓取字节并在下载过程中回报进度。
     *
     * @param listener 进度回调，可为 {@code null}
     * @param cancelled 取消判定，返回 {@code true} 时抛 {@link CancelledException}，可为 {@code null}
     */
    public byte[] fetch(String path, @Nullable ProgressListener listener, @Nullable BooleanSupplier cancelled)
            throws IOException {
        List<String> errors = new ArrayList<>(templates.size());
        int start = preferredSourceIndex % templates.size();
        for (int attempt = 0; attempt < templates.size(); attempt++) {
            int index = (start + attempt) % templates.size();
            String url = templates.get(index).replace("{path}", path);
            try {
                byte[] body = download(url, listener, cancelled);
                preferredSourceIndex = index;
                return body;
            } catch (CancelledException e) {
                throw e;
            } catch (IOException e) {
                errors.add(describe(url, e));
            }
        }
        throw new IOException("Download failed for " + path + " (" + String.join("; ", errors) + ")");
    }

    private byte[] download(String url, @Nullable ProgressListener listener, @Nullable BooleanSupplier cancelled)
            throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            if (status == 404) {
                closeQuietly(response.body());
                throw new IOException("Not found");
            }
            if (status != 200) {
                closeQuietly(response.body());
                throw new IOException("HTTP " + status);
            }
            long total = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
            try (InputStream stream = response.body()) {
                byte[] body = readAll(stream, total, listener, cancelled);
                if (listener != null) {
                    listener.onProgress(body.length, body.length);
                }
                return body;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while downloading", e);
        }
    }

    private static byte[] readAll(
            InputStream stream,
            long total,
            @Nullable ProgressListener listener,
            @Nullable BooleanSupplier cancelled
    ) throws IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream(
                total > 0 && total < Integer.MAX_VALUE ? (int) total : 8192);
        byte[] chunk = new byte[8192];
        long downloaded = 0L;
        int read;
        while ((read = stream.read(chunk)) != -1) {
            if (cancelled != null && cancelled.getAsBoolean()) {
                throw new CancelledException("Download cancelled");
            }
            buffer.write(chunk, 0, read);
            downloaded += read;
            if (listener != null) {
                listener.onProgress(downloaded, total);
            }
        }
        return buffer.toByteArray();
    }

    /**
     * 把单次失败整理成一行可读原因。
     *
     * <p>TLS 握手被重置在受限网络里很常见，直接抛原始堆栈会让服主以为是插件
     * 缺陷，所以这里明确提示"换源"。</p>
     */
    public static String describe(String url, IOException error) {
        String host = hostOf(url);
        if (error instanceof SSLException) {
            return host + ": TLS handshake failed (network/proxy interference; try another source)";
        }
        String message = error.getMessage();
        return host + ": " + (message == null || message.isBlank() ? error.getClass().getSimpleName() : message);
    }

    public static String hostOf(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? url : host;
        } catch (IllegalArgumentException e) {
            return url;
        }
    }

    /** 把模板补全成可用形式：缺 {@code {path}} 的条目按目录前缀处理。 */
    private static List<String> sanitize(@Nullable List<String> configured) {
        List<String> result = new ArrayList<>();
        if (configured != null) {
            for (String raw : configured) {
                String template = sanitizeOne(raw);
                if (template != null) {
                    result.add(template);
                }
            }
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("At least one http(s) source template is required");
        }
        return List.copyOf(result);
    }

    private static @Nullable String sanitizeOne(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("https://") && !lower.startsWith("http://")) {
            return null;
        }
        if (!value.contains("{path}")) {
            value = (value.endsWith("/") ? value : value + "/") + "{path}";
        }
        return value;
    }

    private static void closeQuietly(@Nullable InputStream stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException ignored) {
                // 关闭失败无需处理
            }
        }
    }
}

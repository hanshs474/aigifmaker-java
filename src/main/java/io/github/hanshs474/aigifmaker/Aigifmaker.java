package io.github.hanshs474.aigifmaker;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Official SDK for <a href="https://aigifmaker.ai">AI GIF Maker</a> (aigifmaker.ai): turn text into animated GIFs.
 *
 * <pre>{@code
 * Aigifmaker gifs = new Aigifmaker(); // reads AIGIFMAKER_API_KEY
 * String url = gifs.generate("a corgi in sunglasses dancing on a beach, confetti falling");
 * int left = gifs.credits();
 * }</pre>
 *
 * <p>Create a key at https://aigifmaker.ai/settings/apikeys. Each GIF costs 1 credit on your AI GIF Maker
 * account, the same as on the website. Java 11+, no dependencies.
 */
public final class Aigifmaker {
    /** The service this library talks to. */
    public static final String BASE_URL = "https://aigifmaker.ai";
    /** Where an API key is created. */
    public static final String KEYS_URL = BASE_URL + "/settings/apikeys";
    /** Where credits are bought. */
    public static final String PRICING_URL = BASE_URL + "/pricing";
    /** Library version, sent in the User-Agent. */
    public static final String VERSION = "0.1.0";

    /** Where a submitted GIF stands. */
    public static final class Status {
        /** The task id. */
        public final String id;
        /** {@code pending}, {@code processing}, {@code success}, {@code failed} or {@code canceled}. */
        public final String status;
        /** The finished GIF URLs once {@code success}; empty before. */
        public final List<String> images;
        /** True when the GIF carries the aigifmaker.ai badge (the free GIF). */
        public final boolean watermarked;
        /** True while the GIF waits in the queue for accounts that have not bought credits; keep polling. */
        public final boolean queued;
        /** Seconds the queue expects to wait, or null. */
        public final Integer queueEta;
        /** Why the GIF failed, or null. */
        public final String errorMessage;
        /** The failure class, e.g. {@code moderation}, or null. */
        public final String failureKind;
        /** What happened to the credit of a failed GIF, or null. */
        public final String refundNote;
        /** The whole {@code data} object as the service sent it. */
        public final Map<String, Object> raw;

        Status(Map<String, Object> d) {
            this.raw = Collections.unmodifiableMap(d);
            this.id = str(d.get("id"));
            this.status = str(d.get("status"));
            List<String> imgs = new ArrayList<>();
            if (d.get("images") instanceof List) {
                for (Object o : (List<?>) d.get("images")) if (o instanceof String) imgs.add((String) o);
            }
            this.images = Collections.unmodifiableList(imgs);
            Object w = d.get("watermarked");
            boolean wm = Boolean.TRUE.equals(w);
            if (w instanceof List) for (Object o : (List<?>) w) if (Boolean.TRUE.equals(o)) wm = true;
            this.watermarked = wm;
            this.queued = Boolean.TRUE.equals(d.get("queued"));
            Object eta = d.get("queueEta");
            this.queueEta = eta instanceof Number ? ((Number) eta).intValue() : null;
            this.errorMessage = str(d.get("errorMessage"));
            this.failureKind = str(d.get("failureKind"));
            this.refundNote = str(d.get("refundNote"));
        }

        /** The first GIF URL, or null until the GIF is ready. */
        public String url() {
            return images.isEmpty() ? null : images.get(0);
        }

        /** True once the GIF is ready. */
        public boolean isSuccess() {
            return "success".equals(status);
        }

        /** True when the GIF failed or was canceled. */
        public boolean isFailed() {
            return "failed".equals(status) || "canceled".equals(status);
        }

        @Override
        public String toString() {
            return "Status{id=" + id + ", status=" + status + ", url=" + url() + ", queued=" + queued + "}";
        }
    }

    /** A parsed HTTP answer. */
    static final class Response {
        final int status;
        final Map<String, Object> body;

        Response(int status, Map<String, Object> body) {
            this.status = status;
            this.body = body;
        }
    }

    /** Transport seam: POST a JSON body (key may be empty), return status and parsed JSON. */
    interface Transport {
        Response post(String url, String json, String key) throws IOException, InterruptedException;
    }

    private final String apiKey;
    private final String base;
    private final Transport transport;
    private Duration timeout = Duration.ofMinutes(15);
    private Duration pollEvery = Duration.ofSeconds(3);

    /** A client that reads the key from the {@code AIGIFMAKER_API_KEY} environment variable. */
    public Aigifmaker() {
        this(System.getenv("AIGIFMAKER_API_KEY"));
    }

    /** A client for one API key. */
    public Aigifmaker(String apiKey) {
        this(apiKey, BASE_URL);
    }

    /** A client for one API key against another base URL. */
    public Aigifmaker(String apiKey, String baseUrl) {
        this(apiKey, baseUrl, null);
    }

    Aigifmaker(String apiKey, String base, Transport transport) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        String b = base == null ? BASE_URL : base;
        this.base = b.endsWith("/") ? b.substring(0, b.length() - 1) : b;
        this.transport = transport != null ? transport : httpTransport();
    }

    /** How long {@link #generate(String)} waits for a GIF. Defaults to 5 minutes. */
    public Aigifmaker timeout(Duration v) {
        timeout = v;
        return this;
    }

    /** How often {@link #generate(String)} asks for the status. Defaults to 3 seconds. */
    public Aigifmaker pollEvery(Duration v) {
        pollEvery = v;
        return this;
    }

    private static Transport httpTransport() {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
        return (url, json, key) -> {
            HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "aigifmaker-java/" + VERSION)
                    .POST(HttpRequest.BodyPublishers.ofString(json));
            // Without a key the request still goes out: the service answers with a wall, which becomes an AuthException.
            if (!key.isEmpty()) req.header("Authorization", "Bearer " + key);
            HttpResponse<String> res = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
            Object parsed;
            try {
                parsed = Json.parse(res.body());
            } catch (RuntimeException e) {
                parsed = null;
            }
            return new Response(res.statusCode(), asMap(parsed));
        };
    }

    /** Turn a prompt into an animated GIF and return its URL: submits, waits, and returns when the GIF is ready. */
    public String generate(String prompt) {
        return waitFor(submit(prompt));
    }

    /** Start a GIF without waiting and return its id; follow it with {@link #status(String)}. */
    public String submit(String prompt) {
        if (prompt == null || prompt.trim().isEmpty()) throw new IllegalArgumentException("prompt is required");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("provider", "openlux");
        body.put("mediaType", "image");
        body.put("model", "gif-maker-v1");
        body.put("scene", "text-to-image");
        body.put("prompt", prompt);
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("output_format", "gif");
        body.put("options", options);
        Object id = call("/api/ai/generate", body).get("id");
        if (id == null) throw new AigifmakerException(AigifmakerException.Kind.OTHER, "the service returned no task id", null, 200);
        return id.toString();
    }

    /** Ask where a GIF stands. */
    public Status status(String id) {
        if (id == null || id.isEmpty()) throw new IllegalArgumentException("id is required");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("taskId", id);
        return new Status(call("/api/ai/query", body));
    }

    /** The credits left on the account. Free to call. */
    public int credits() {
        Object left = call("/api/user/get-user-credits", new LinkedHashMap<>()).get("remainingCredits");
        if (!(left instanceof Number)) {
            throw new AigifmakerException(AigifmakerException.Kind.OTHER, "the service returned no credit balance", null, 200);
        }
        return ((Number) left).intValue();
    }

    private String waitFor(String id) {
        Instant deadline = Instant.now().plus(timeout);
        while (true) {
            Status s = null;
            try {
                s = status(id);
            } catch (AigifmakerException e) {
                // a dropped poll (network, 5xx) is not a failed GIF; a refusal is final
                if (e.kind() != AigifmakerException.Kind.OTHER || e.httpStatus() == 200) throw e;
            }
            if (s != null) {
                if (s.isSuccess() && s.url() != null) return s.url();
                if (s.isFailed()) {
                    String why = s.errorMessage != null ? s.errorMessage : s.status;
                    if ("moderation".equals(s.failureKind)) {
                        throw new AigifmakerException.RejectedException("GIF " + id + " was refused: " + why, null, 200);
                    }
                    throw new AigifmakerException(AigifmakerException.Kind.FAILED, "GIF " + id + " failed: " + why, null, 200);
                }
            }
            if (!Instant.now().plus(pollEvery).isBefore(deadline)) break;
            try {
                Thread.sleep(pollEvery.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AigifmakerException(AigifmakerException.Kind.OTHER, "interrupted");
            }
        }
        throw new AigifmakerException(AigifmakerException.Kind.TIMEOUT,
                "deadline passed; GIF " + id + " may still finish in your AI GIF Maker history");
    }

    private Map<String, Object> call(String path, Map<String, Object> body) {
        Response r;
        try {
            r = transport.post(base + path, Json.write(body), apiKey);
        } catch (IOException e) {
            throw new AigifmakerException(AigifmakerException.Kind.OTHER, e.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AigifmakerException(AigifmakerException.Kind.OTHER, "interrupted");
        }
        Object code = r.body.get("code");
        if (!(code instanceof Number)) {
            String msg = str(r.body.get("message"));
            throw new AigifmakerException(AigifmakerException.Kind.OTHER,
                    msg != null ? msg : "unexpected answer (HTTP " + r.status + ")", null, r.status);
        }
        int c = ((Number) code).intValue();
        if (c != 0) throw AigifmakerException.fromAnswer(c, str(r.body.get("message")), r.status);
        Map<String, Object> data = asMap(r.body.get("data"));
        if (Boolean.TRUE.equals(data.get("wall"))) {
            throw new AigifmakerException.AuthException("an API key is required; create one at " + KEYS_URL, 0, r.status);
        }
        return data;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : new HashMap<>();
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}

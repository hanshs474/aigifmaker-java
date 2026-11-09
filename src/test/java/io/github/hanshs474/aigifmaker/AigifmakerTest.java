package io.github.hanshs474.aigifmaker;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Plain-main tests so the build needs no test framework. Response bodies are the service's real shapes. */
public final class AigifmakerTest {
    static int failures = 0;

    static void check(boolean ok, String what) {
        if (!ok) {
            failures++;
            System.out.println("FAIL " + what);
        }
    }

    static final List<String> urls = new ArrayList<>();
    static final List<String> keys = new ArrayList<>();
    static final List<Map<String, Object>> bodies = new ArrayList<>();

    @SuppressWarnings("unchecked")
    static Aigifmaker client(String key, Function<String, String> answer) {
        return new Aigifmaker(key, "https://t", (url, json, k) -> {
            urls.add(url);
            keys.add(k);
            bodies.add((Map<String, Object>) Json.parse(json));
            return new Aigifmaker.Response(200, (Map<String, Object>) Json.parse(answer.apply(url)));
        }).pollEvery(Duration.ZERO);
    }

    static Aigifmaker client(Function<String, String> answer) {
        return client("sk-test", answer);
    }

    static RuntimeException thrown(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException e) {
            return e;
        }
        return null;
    }

    static final String SUBMITTED = "{\"code\":0,\"message\":\"ok\",\"data\":{\"id\":\"task_1\",\"status\":\"pending\",\"taskId\":\"up_9\",\"provider\":\"openlux\"}}";
    static final String GIF = "https://cdn.aigifmaker.ai/g/task_1.gif";

    static Function<String, String> run(String... polls) {
        Deque<String> q = new ArrayDeque<>(Arrays.asList(polls));
        return url -> url.endsWith("/api/ai/generate") ? SUBMITTED : q.size() > 1 ? q.poll() : q.peek();
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        // success: pending -> processing -> success
        urls.clear(); keys.clear(); bodies.clear();
        Aigifmaker c = client(run(
                "{\"code\":0,\"message\":\"ok\",\"data\":{\"id\":\"task_1\",\"status\":\"processing\",\"images\":[]}}",
                "{\"code\":0,\"message\":\"ok\",\"data\":{\"id\":\"task_1\",\"status\":\"success\",\"images\":[\"" + GIF + "\"],\"watermarked\":[false]}}"));
        check(GIF.equals(c.generate("a cat waving hello")), "success url");
        Map<String, Object> b = bodies.get(0);
        check(urls.get(0).equals("https://t/api/ai/generate") && keys.get(0).equals("sk-test"), "submit path + key");
        check("openlux".equals(b.get("provider")) && "image".equals(b.get("mediaType")) && "gif-maker-v1".equals(b.get("model"))
                && "text-to-image".equals(b.get("scene")) && "a cat waving hello".equals(b.get("prompt"))
                && "gif".equals(((Map<String, Object>) b.get("options")).get("output_format")) && b.size() == 6, "fixed request body");
        check(urls.get(1).equals("https://t/api/ai/query") && "task_1".equals(bodies.get(1).get("taskId")), "query uses data.id");
        check(urls.size() == 3, "polled until success");

        // queued
        c = client(run(
                "{\"code\":0,\"message\":\"ok\",\"data\":{\"id\":\"task_1\",\"status\":\"processing\",\"images\":[],\"queued\":true,\"queueEta\":40}}",
                "{\"code\":0,\"message\":\"ok\",\"data\":{\"id\":\"task_1\",\"status\":\"processing\",\"images\":[],\"queued\":true,\"queueEta\":5}}",
                "{\"code\":0,\"message\":\"ok\",\"data\":{\"id\":\"task_1\",\"status\":\"success\",\"images\":[\"" + GIF + "\"],\"watermarked\":[true]}}"));
        String id = c.submit("x");
        check("task_1".equals(id), "submit id");
        Aigifmaker.Status s = c.status(id);
        check(s.queued && s.queueEta == 40 && "processing".equals(s.status) && s.url() == null, "queued status object");
        check(GIF.equals(c.generate("x")), "queued then success");
        s = client(u -> "{\"code\":0,\"message\":\"ok\",\"data\":{\"id\":\"t\",\"status\":\"success\",\"images\":[\"" + GIF + "\"],\"watermarked\":[true]}}").status("t");
        check(s.isSuccess() && s.watermarked && GIF.equals(s.url()), "watermarked flag");

        // failed
        RuntimeException e = thrown(() -> client(run("{\"code\":0,\"message\":\"ok\",\"data\":{\"id\":\"task_1\",\"status\":\"failed\",\"images\":[],\"errorMessage\":\"upstream returned a still image\",\"refundNote\":\"credit refunded\"}}")).generate("x"));
        check(e instanceof AigifmakerException && ((AigifmakerException) e).kind() == AigifmakerException.Kind.FAILED
                && e.getMessage().contains("upstream returned a still image"), "failed -> FAILED with errorMessage");
        e = thrown(() -> client(run("{\"code\":0,\"message\":\"ok\",\"data\":{\"id\":\"task_1\",\"status\":\"failed\",\"images\":[],\"errorMessage\":\"blocked\",\"failureKind\":\"moderation\"}}")).generate("x"));
        check(e instanceof AigifmakerException.RejectedException, "moderation -> Rejected");

        // invalid key
        Aigifmaker bad = client("sk-bad", u -> "{\"code\":-1,\"message\":\"invalid API key — create one at https://aigifmaker.ai/settings/apikeys\"}");
        check(thrown(bad::credits) instanceof AigifmakerException.AuthException, "credits invalid key -> Auth");
        check(thrown(() -> bad.submit("x")) instanceof AigifmakerException.AuthException, "submit invalid key -> Auth");
        check(thrown(() -> client(u -> "{\"code\":-1,\"message\":\"no auth\"}").status("t")) instanceof AigifmakerException.AuthException, "no auth -> Auth");

        // wall (no key): request still goes out with an empty key, answer becomes Auth
        keys.clear();
        e = thrown(() -> client("", u -> "{\"code\":0,\"message\":\"ok\",\"data\":{\"wall\":true,\"reason\":\"anon_credits\",\"cost\":1,\"remaining\":0,\"grant\":0}}").submit("x"));
        check(e instanceof AigifmakerException.AuthException && e.getMessage().contains("/settings/apikeys"), "wall -> Auth");
        check(keys.size() == 1 && keys.get(0).isEmpty(), "no key sent");

        // insufficient credits
        e = thrown(() -> client(u -> "{\"code\":-1,\"message\":\"insufficient credits\"}").submit("x"));
        check(e instanceof AigifmakerException.QuotaException && e.getMessage().contains("https://aigifmaker.ai/pricing"), "insufficient credits -> Quota");

        // cooldown (code neither 0 nor -1) and other -1
        check(thrown(() -> client(u -> "{\"code\":429,\"message\":\"please wait before trying again\"}").submit("x")) instanceof AigifmakerException.RejectedException, "cooldown -> Rejected");
        e = thrown(() -> client(u -> "{\"code\":-1,\"message\":\"prompt too long\"}").submit("x"));
        check(e != null && e.getClass() == AigifmakerException.class && ((AigifmakerException) e).kind() == AigifmakerException.Kind.OTHER
                && "prompt too long".equals(e.getMessage()), "other -1 -> base");

        // credits
        check(client(u -> "{\"code\":0,\"message\":\"ok\",\"data\":{\"remainingCredits\":42,\"made\":3}}").credits() == 42, "credits");
        check(urls.get(urls.size() - 1).endsWith("/api/user/get-user-credits"), "credits path");

        // timeout
        Aigifmaker slow = client(run("{\"code\":0,\"message\":\"ok\",\"data\":{\"id\":\"task_1\",\"status\":\"processing\"}}"))
                .timeout(Duration.ofMillis(50)).pollEvery(Duration.ofMillis(10));
        e = thrown(() -> slow.generate("x"));
        check(e instanceof AigifmakerException && ((AigifmakerException) e).kind() == AigifmakerException.Kind.TIMEOUT, "timeout");

        check(thrown(() -> client(u -> SUBMITTED).submit(" ")) instanceof IllegalArgumentException, "empty prompt");

        if (failures > 0) {
            System.out.println(failures + " test(s) failed");
            System.exit(1);
        }
        System.out.println("all tests passed");
    }
}

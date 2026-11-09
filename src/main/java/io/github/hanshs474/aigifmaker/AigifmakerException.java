package io.github.hanshs474.aigifmaker;

/**
 * Every error this library throws on purpose. Catch the subclasses ({@link AuthException},
 * {@link QuotaException}, {@link RejectedException}) or check {@link #kind()}.
 */
public class AigifmakerException extends RuntimeException {
    /** Why a call failed, split by what to do next. */
    public enum Kind {
        /** No API key, or the key is invalid or deleted. */
        AUTH,
        /** Not enough credits left for this GIF. */
        QUOTA,
        /** The content filter refused the prompt, or the account is cooling down after a refusal. */
        REJECTED,
        /** The GIF was accepted and then failed; the message carries the reason. Failed GIFs are refunded. */
        FAILED,
        /** The wait ran out; the GIF may still finish in your AI GIF Maker history. */
        TIMEOUT,
        /** Anything else the service answered, or a transport failure. */
        OTHER
    }

    private static final long serialVersionUID = 1L;

    private final Kind kind;
    private final Integer code;
    private final int httpStatus;

    AigifmakerException(Kind kind, String message) {
        this(kind, message, null, 0);
    }

    AigifmakerException(Kind kind, String message, Integer code, int httpStatus) {
        super(message);
        this.kind = kind;
        this.code = code;
        this.httpStatus = httpStatus;
    }

    /** What went wrong. */
    public Kind kind() {
        return kind;
    }

    /** The JSON {@code code} of the answer (-1 for most refusals), or null when there was none. */
    public Integer code() {
        return code;
    }

    /** The HTTP status, or 0 when there was no response. The service answers 200 even for refusals. */
    public int httpStatus() {
        return httpStatus;
    }

    /** Map a refusal ({@code code != 0}) to the error that says what to do next. */
    static AigifmakerException fromAnswer(int code, String message, int httpStatus) {
        String msg = message == null || message.isEmpty() ? "request refused (code " + code + ")" : message;
        String low = msg.toLowerCase(java.util.Locale.ROOT);
        if (low.contains("invalid api key") || low.contains("no auth")) return new AuthException(msg, code, httpStatus);
        if (low.contains("insufficient credits")) {
            return new QuotaException(low.contains("/pricing") ? msg : msg + "; top up at " + Aigifmaker.PRICING_URL, code, httpStatus);
        }
        if (code != -1) return new RejectedException(msg, code, httpStatus);
        return new AigifmakerException(Kind.OTHER, msg, code, httpStatus);
    }

    /** No API key, or the key is invalid or deleted. Create one at https://aigifmaker.ai/settings/apikeys. */
    public static final class AuthException extends AigifmakerException {
        private static final long serialVersionUID = 1L;

        AuthException(String message, Integer code, int httpStatus) {
            super(Kind.AUTH, message, code, httpStatus);
        }
    }

    /** Not enough credits left for this GIF. */
    public static final class QuotaException extends AigifmakerException {
        private static final long serialVersionUID = 1L;

        QuotaException(String message, Integer code, int httpStatus) {
            super(Kind.QUOTA, message, code, httpStatus);
        }
    }

    /** The prompt was refused, or the account is cooling down after a refusal. Reword rather than retry. */
    public static final class RejectedException extends AigifmakerException {
        private static final long serialVersionUID = 1L;

        RejectedException(String message, Integer code, int httpStatus) {
            super(Kind.REJECTED, message, code, httpStatus);
        }
    }
}

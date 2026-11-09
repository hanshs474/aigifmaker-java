import io.github.hanshs474.aigifmaker.Aigifmaker;
import io.github.hanshs474.aigifmaker.AigifmakerException;

/**
 * Two live checks against https://aigifmaker.ai that spend nothing and need no real key:
 * a wrong key on credits(), and a submit with no key at all (the service answers with a wall).
 * java -cp build/aigifmaker-0.1.0.jar example/Smoke.java
 */
public class Smoke {
    public static void main(String[] args) {
        boolean ok = true;
        try {
            new Aigifmaker("sk-invalidtest000").credits();
            System.out.println("FAIL wrong key accepted");
            ok = false;
        } catch (AigifmakerException.AuthException e) {
            System.out.println("ok   wrong key -> AuthException: " + e.getMessage());
        }
        try {
            new Aigifmaker("").submit("a cat waving hello");
            System.out.println("FAIL submit without a key accepted");
            ok = false;
        } catch (AigifmakerException.AuthException e) {
            System.out.println("ok   no key -> AuthException: " + e.getMessage());
        }
        if (!ok) System.exit(1);
    }
}

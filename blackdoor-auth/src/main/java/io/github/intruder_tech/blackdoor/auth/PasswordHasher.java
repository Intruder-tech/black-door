package io.github.intruder_tech.blackdoor.auth;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Salted PBKDF2-HMAC-SHA256, built into the JDK. Stored form:
 * {@code pbkdf2-sha256$<iterations>$<salt>$<hash>}, so the cost can be raised later without
 * breaking existing accounts. Slow on purpose: call from a worker thread.
 */
public final class PasswordHasher {

    /** OWASP recommendation for PBKDF2-HMAC-SHA256. */
    public static final int DEFAULT_ITERATIONS = 600_000;

    private static final String SCHEME = "pbkdf2-sha256";
    private final SecureRandom random = new SecureRandom();
    private final int iterations;

    public PasswordHasher(int iterations) {
        this.iterations = iterations;
    }

    public String hash(String password) {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        Base64.Encoder base64 = Base64.getEncoder().withoutPadding();
        return SCHEME + '$' + iterations + '$' + base64.encodeToString(salt) + '$'
                + base64.encodeToString(derive(password, salt, iterations));
    }

    /** False for a wrong password and for a stored value this class does not understand. */
    public boolean verify(String password, String stored) {
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !parts[0].equals(SCHEME)) {
            return false;
        }
        try {
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            return MessageDigest.isEqual(expected, derive(password, salt, Integer.parseInt(parts[1])));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] derive(String password, byte[] salt, int rounds) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, rounds, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 is not available", e);
        } finally {
            spec.clearPassword();
        }
    }
}

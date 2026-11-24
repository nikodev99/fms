package org.gu.fms.desktop.platform.security;

import jakarta.inject.Singleton;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Empreintes de mots de passe : PBKDF2-HMAC-SHA256, sel aléatoire de 16 octets, 600 000 itérations (recommandation
 * OWASP pour PBKDF2-HMAC-SHA256). Le nombre d'itérations est conservé avec chaque empreinte : il peut être relevé
 * plus tard sans invalider les mots de passe existants. Comparaison en temps constant.
 */
@Singleton
public final class PasswordHasher {

    public static final int ITERATIONS = 600_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final int iterations;

    @jakarta.inject.Inject
    public PasswordHasher() {
        this(ITERATIONS);
    }

    /** Nombre d'itérations réduit pour les tests (100 000 au minimum, contrôlé par la base). */
    public PasswordHasher(int iterations) {
        this.iterations = iterations;
    }

    /** Empreinte, sel et itérations (sel et empreinte en Base64). */
    public record Hash(String hash, String salt, int iterations) {
    }

    public Hash hash(char[] password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return new Hash(Base64.getEncoder().encodeToString(derive(password, salt, iterations)),
                Base64.getEncoder().encodeToString(salt), iterations);
    }

    public boolean verify(char[] password, Hash stored) {
        byte[] expected = Base64.getDecoder().decode(stored.hash());
        byte[] actual = derive(password, Base64.getDecoder().decode(stored.salt()), stored.iterations());
        return MessageDigest.isEqual(expected, actual);
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("PBKDF2WithHmacSHA256 indisponible", e);
        } finally {
            spec.clearPassword();
        }
    }

    /** Clé de récupération : 20 caractères (alphabet sans 0/O/1/I), en groupes de 4 : « ABCD-EFGH-… ». */
    public String newRecoveryKey() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            if (i > 0 && i % 4 == 0) {
                sb.append('-');
            }
            sb.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    /** Forme comparée d'une clé saisie : majuscules, sans espaces ni tirets. */
    public static char[] canonicalKey(String key) {
        String s = key == null ? "" : key.toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        char[] c = s.toCharArray();
        return Arrays.copyOf(c, c.length);
    }
}

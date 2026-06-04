package com.shortlyai.url.shortening;

// Pure utility class — no Spring, no dependencies, fully testable
// Converts a long ID into a short Base62 string and back
public final class Base62 {

    // 62 characters — digits + lowercase + uppercase
    // Order matters — must never change once URLs are in production
    private static final String ALPHABET =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private static final int BASE = ALPHABET.length(); // 62

    // Private constructor — no one should instantiate this class
    private Base62() {}

    // Encode: Long → Base62 string
    // e.g. 12345 → "dnh"
    public static String encode(long id) {
        if (id <= 0) throw new IllegalArgumentException("ID must be positive: " + id);

        StringBuilder result = new StringBuilder();

        // Repeatedly divide by 62, collect remainders as characters
        // Same logic as converting decimal to binary — just base 62
        while (id > 0) {
            result.append(ALPHABET.charAt((int) (id % BASE))); // remainder → character
            id /= BASE; // shrink the number
        }

        // Remainders come out in reverse order — flip the string
        return result.reverse().toString();
    }

    // Decode: Base62 string → Long
    // e.g. "dnh" → 12345
    // Used for reversibility — slug back to DB id for fast lookup
    public static long decode(String slug) {
        long result = 0;

        for (char c : slug.toCharArray()) {
            result = result * BASE + ALPHABET.indexOf(c); // positional value
        }

        return result;
    }
}
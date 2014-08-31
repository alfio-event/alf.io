package io.bagarino.util;

import java.util.UUID;

public final class PasswordGenerator {
    private PasswordGenerator() {
    }

    public static String generateRandomPassword() {
        return Long.toHexString(UUID.randomUUID().getMostSignificantBits());
    }
}

package com.mc.oletrex.inventory.service.impl;

import com.mc.oletrex.inventory.exception.InvalidCursorException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Encodes the position in a list ordered by id as an opaque string.
 * Clients must treat it as a token: the format ("id:<last id>", base64url) can change.
 */
final class IdCursor {

    private static final String PREFIX = "id:";

    private IdCursor() {
    }

    static String encode(long lastId) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((PREFIX + lastId).getBytes(StandardCharsets.UTF_8));
    }

    /** @return the id to continue after; 0 for the first page (null or blank cursor) */
    static long decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0L;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            if (!raw.startsWith(PREFIX)) {
                throw new InvalidCursorException(cursor);
            }
            long id = Long.parseLong(raw.substring(PREFIX.length()));
            if (id < 0) {
                throw new InvalidCursorException(cursor);
            }
            return id;
        } catch (IllegalArgumentException e) { // bad base64 or NumberFormatException
            throw new InvalidCursorException(cursor);
        }
    }
}

/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */


package cafe.jeffrey.shared.common.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * The identity of a published file's content.
 *
 * <p>A hub-published file carries no version number, so a session records the digest of every layer
 * it merged and the hub computes the same digest over the file it renders. Comparing the two says
 * whether a running JVM is still on the current configuration, without a clock, a sequence or any
 * state shared between the two sides.</p>
 */
public abstract class ContentDigest {

    private ContentDigest() {
    }

    private static final String ALGORITHM = "SHA-256";

    private static final HexFormat HEX = HexFormat.of();

    /** Lower-case hex SHA-256 of the given bytes */
    public static String sha256Hex(byte[] content) {
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
        try {
            return HEX.formatHex(MessageDigest.getInstance(ALGORITHM).digest(content));
        } catch (NoSuchAlgorithmException e) {
            // Every JVM ships SHA-256; its absence is an environment fault, not a case to handle
            throw new IllegalStateException("Digest algorithm is not available: " + ALGORITHM, e);
        }
    }

    /** Lower-case hex SHA-256 of the string's UTF-8 bytes, which is how published files are written */
    public static String sha256Hex(String content) {
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
        return sha256Hex(content.getBytes(StandardCharsets.UTF_8));
    }
}

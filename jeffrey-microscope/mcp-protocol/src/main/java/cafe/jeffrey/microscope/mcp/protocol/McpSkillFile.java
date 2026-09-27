/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.microscope.mcp.protocol;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * One file of a skill, as the skills extension's {@code SkillResource} describes it and
 * {@code resources/read} serves it.
 * <p>
 * The bytes are the raw file, exactly as a host will hash them: the {@link #digest()} and
 * {@link #size()} a manifest publishes are computed from them and nothing else, so the record copies
 * them in and out rather than let a caller change what was already published.
 *
 * @param uri      the file's resource URI, {@code skill://<skill>/<relative path>}
 * @param mimeType how {@code resources/read} labels the file
 * @param bytes    the file's raw content
 */
public record McpSkillFile(String uri, String mimeType, byte[] bytes) {

    private static final String DIGEST_ALGORITHM = "SHA-256";
    private static final String DIGEST_PREFIX = "sha256:";
    private static final HexFormat LOWERCASE_HEX = HexFormat.of();

    public McpSkillFile {
        if (uri == null || uri.isBlank()) {
            throw new IllegalArgumentException("A skill file needs a uri");
        }
        if (mimeType == null || mimeType.isBlank()) {
            throw new IllegalArgumentException("A skill file needs a mimeType: " + uri);
        }
        if (bytes == null) {
            throw new IllegalArgumentException("A skill file needs its content: " + uri);
        }
        bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }

    /** {@code sha256:} and 64 lowercase hexadecimal characters over the raw bytes. */
    public String digest() {
        try {
            return DIGEST_PREFIX + LOWERCASE_HEX.formatHex(MessageDigest.getInstance(DIGEST_ALGORITHM).digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            // Every Java platform is required to provide SHA-256.
            throw new IllegalStateException("The platform has no " + DIGEST_ALGORITHM, e);
        }
    }

    /** The length of the raw content in bytes: what the digest covers. */
    public int size() {
        return bytes.length;
    }

    /** The content as UTF-8 text, the way {@code resources/read} carries it. */
    public String text() {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof McpSkillFile file
                && uri.equals(file.uri)
                && mimeType.equals(file.mimeType)
                && Arrays.equals(bytes, file.bytes);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * uri.hashCode() + mimeType.hashCode()) + Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return "McpSkillFile[uri=" + uri + ", mimeType=" + mimeType + ", size=" + bytes.length + "]";
    }
}

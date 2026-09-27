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

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Reads an {@code Mcp-*} header value the way {@code 2026-07-28} lets a client write it: as-is, or,
 * when it cannot travel in an HTTP header, as {@code =?base64?<value>?=} with UTF-8 underneath. The
 * Base64 may be padded or not.
 * <p>
 * Strict on purpose. A header that does not decode cannot be compared with the body, and the transport
 * answers a mismatch with {@code -32020}; guessing at a lenient reading would let a value through that
 * the client never meant to send.
 */
public final class McpHeaderValues {

    private static final String SENTINEL_PREFIX = "=?base64?";
    private static final String SENTINEL_SUFFIX = "?=";
    private static final String UNDECODABLE =
            "The %s header opens a =?base64?…?= value that is not Base64 over UTF-8";

    private McpHeaderValues() {
    }

    /**
     * @param value  the raw header value, or null when absent
     * @param header the header's name, which the refusal names
     * @return the value as the client meant it, or null when absent
     * @throws McpProtocolException {@code -32020} when the value opens the sentinel and does not decode
     */
    public static String decode(String value, String header) {
        if (value == null || !value.startsWith(SENTINEL_PREFIX)) {
            return value;
        }
        if (!value.endsWith(SENTINEL_SUFFIX) || value.length() < SENTINEL_PREFIX.length() + SENTINEL_SUFFIX.length()) {
            throw undecodable(header);
        }
        String encoded = value.substring(SENTINEL_PREFIX.length(), value.length() - SENTINEL_SUFFIX.length());
        // Padded or not: real clients may leave the padding off. The JDK decoder takes both and still
        // refuses a partial padding, a dangling character and anything outside the alphabet.
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (IllegalArgumentException | CharacterCodingException e) {
            throw undecodable(header);
        }
    }

    private static McpProtocolException undecodable(String header) {
        return new McpProtocolException(McpErrorCode.HEADER_MISMATCH, UNDECODABLE.formatted(header));
    }
}

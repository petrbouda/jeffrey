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

package cafe.jeffrey.microscope.core.web;

import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.io.IOException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;

/**
 * Recognises the failures that a client hanging up mid-response produces.
 *
 * <p>The same event reaches the web layer in several shapes depending on how far the response
 * had progressed and who was writing it:
 *
 * <ul>
 *     <li>{@link AsyncRequestNotUsableException} — an SSE stream whose subscriber closed the tab.</li>
 *     <li>{@code ClientAbortException} (a Tomcat {@link IOException}) — a download or raw stream write.</li>
 *     <li>{@code HttpMessageNotWritableException} wrapping either of the above — the message converter
 *         was halfway through serializing a large payload when the socket died, so the broken pipe is
 *         buried under Jackson's own wrapper.</li>
 * </ul>
 *
 * <p>Only the first is recognisable by type alone, which is why the whole cause chain is scanned
 * instead. The Tomcat type is deliberately matched by message rather than imported: the container is
 * an implementation detail of the deployment, and its message ({@code "... Broken pipe"}) is what
 * every servlet container ultimately reports.
 */
final class ClientDisconnects {

    /**
     * Lower-cased fragments of the {@link IOException} messages operating systems produce when the
     * peer is gone: Linux/macOS report a broken pipe or a reset, Windows the two "connection was
     * aborted/forcibly closed" variants.
     */
    private static final Set<String> DISCONNECT_MESSAGE_TOKENS = Set.of(
            "broken pipe",
            "connection reset",
            "an established connection was aborted",
            "an existing connection was forcibly closed");

    private ClientDisconnects() {
    }

    /**
     * Returns {@code true} when the given failure — or anything in its cause chain — is the client
     * having gone away, rather than a fault on this side of the connection.
     */
    static boolean isClientDisconnect(Throwable throwable) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        for (Throwable current = throwable; current != null && visited.add(current); current = current.getCause()) {
            if (current instanceof AsyncRequestNotUsableException) {
                return true;
            }
            if (current instanceof IOException && isDisconnectMessage(current.getMessage())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDisconnectMessage(String message) {
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return DISCONNECT_MESSAGE_TOKENS.stream().anyMatch(normalized::contains);
    }
}

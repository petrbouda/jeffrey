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

package pbouda.jeffrey.shared.common.exception;

import java.net.URI;

/**
 * Exception that preserves the origin context of an error received from a remote Jeffrey instance.
 * Unlike a plain {@link JeffreyException}, this carries the remote URI so logs and error handlers
 * can distinguish locally thrown exceptions from those propagated from a remote server.
 */
public class RemoteJeffreyException extends JeffreyException {

    private final URI remoteUri;

    public RemoteJeffreyException(URI remoteUri, ErrorType type, ErrorCode code, String message) {
        super(type, code, "Remote Jeffrey [%s]: %s".formatted(remoteUri, message));
        this.remoteUri = remoteUri;
    }

    public URI getRemoteUri() {
        return remoteUri;
    }
}

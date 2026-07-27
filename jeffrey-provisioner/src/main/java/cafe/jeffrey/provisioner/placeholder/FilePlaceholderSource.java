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

package cafe.jeffrey.provisioner.placeholder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Resolves {@code <<FILE:/path/to/file>>} to the file's trimmed content.
 *
 * <p>Earns its place in Kubernetes, where facts about the pod are mounted rather than exported:
 * {@code <<FILE:/var/run/secrets/kubernetes.io/serviceaccount/namespace>>} yields the real namespace
 * without the downward API being wired into the pod spec.
 *
 * <p>An unreadable, missing or oversized file resolves to empty rather than throwing — the caller
 * then applies the placeholder's default (or substitutes empty and warns), keeping the provisioner's
 * fail-open guarantee that a misconfiguration never stops the application from starting.
 */
public record FilePlaceholderSource() implements PlaceholderSource {

    private static final Logger LOG = LoggerFactory.getLogger(FilePlaceholderSource.class);

    public static final String TYPE = "FILE";

    /**
     * These placeholders stand in for short scalars — a namespace, a cluster name, a token. A cap
     * keeps a mistyped path from pulling a multi-gigabyte file into a JVM argument.
     */
    private static final long MAX_FILE_SIZE_BYTES = 8 * 1024L;

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Optional<String> lookup(String name) {
        try {
            Path path = Path.of(name);
            if (!Files.isReadable(path)) {
                LOG.warn("Placeholder file is missing or unreadable: path={}", name);
                return Optional.empty();
            }
            long size = Files.size(path);
            if (size > MAX_FILE_SIZE_BYTES) {
                LOG.warn("Placeholder file exceeds the size limit, ignoring: path={} size={} limit={}",
                        name, size, MAX_FILE_SIZE_BYTES);
                return Optional.empty();
            }
            String content = Files.readString(path).strip();
            return content.isEmpty() ? Optional.empty() : Optional.of(content);
        } catch (IOException | RuntimeException e) {
            LOG.warn("Failed to read placeholder file: path={} error={}", name, e.getMessage());
            return Optional.empty();
        }
    }
}

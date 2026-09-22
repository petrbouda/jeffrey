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


package cafe.jeffrey.hub.core.config;

import cafe.jeffrey.shared.common.IDGenerator;
import cafe.jeffrey.shared.common.config.ContentDigest;
import cafe.jeffrey.shared.common.config.ScopedConfigLayout;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * Publishes a scope's configuration onto the shared volume.
 *
 * <p>Two properties matter more than anything else this class does.</p>
 *
 * <p><b>The rename is what makes a concurrent reader safe.</b> A provisioner that already opened
 * the file keeps reading the old content through to the end, because the rename only swaps the
 * directory entry and the bytes it is reading live on until it closes them. Writing into the file
 * in place would instead mutate it underneath that reader, which is how the mechanism this replaced
 * could hand a JVM a half-written file and leave it unprofiled for its whole life.</p>
 *
 * <p><b>The temporary file is unique per publish.</b> An editor's save and the synchronizer's tick
 * can publish one scope at the same moment; with a shared temporary name they would interleave
 * their bytes and rename the result into place. Readers resolve the published name directly and
 * never list the directory, so the extra name costs nothing.</p>
 *
 * <p>Durability is deliberately left to the next tick rather than an {@code fsync}: no writer on
 * this volume forces its writes, and a file truncated by a host crash fails its digest check on the
 * next run of the synchronizer and is rewritten, while the provisioner treats an unreadable file as
 * a skipped layer rather than a failure.</p>
 */
public class FilesystemScopedConfigPublisher implements ScopedConfigPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(FilesystemScopedConfigPublisher.class);

    @Override
    public String publish(Path scopeDir, String content) {
        Path target = ScopedConfigLayout.configFile(scopeDir);
        if (content == null || content.isEmpty()) {
            remove(target);
            return "";
        }

        String digest = ContentDigest.sha256Hex(content);
        if (digest.equals(read(scopeDir).map(ContentDigest::sha256Hex).orElse(null))) {
            LOG.debug("Configuration already published, leaving the file untouched: file={}", target);
            return digest;
        }

        FileSystemUtils.createDirectories(target.getParent());
        writeAtomically(target, content);
        LOG.info("Configuration published: file={} digest={}", target, digest);
        return digest;
    }

    @Override
    public Optional<String> read(Path scopeDir) {
        Path file = ScopedConfigLayout.configFile(scopeDir);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOG.warn("Cannot read a published configuration file: file={} error={}", file, e.getMessage());
            return Optional.empty();
        }
    }

    private static void writeAtomically(Path target, String content) {
        Path temporary = ScopedConfigLayout.temporaryFile(target, IDGenerator.generate());
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to publish configuration file: " + target, e);
        } finally {
            discard(temporary);
        }
    }

    private static void remove(Path target) {
        try {
            if (Files.deleteIfExists(target)) {
                LOG.info("Configuration removed, the scope holds nothing: file={}", target);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to remove configuration file: " + target, e);
        }
    }

    /** Best-effort: a leftover scratch file is untidy, never harmful. */
    private static void discard(Path temporary) {
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException e) {
            LOG.debug("Could not remove a temporary configuration file: file={}", temporary);
        }
    }
}

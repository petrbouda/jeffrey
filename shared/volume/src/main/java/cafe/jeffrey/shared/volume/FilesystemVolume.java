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

package cafe.jeffrey.shared.volume;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A volume that is a directory tree under {@code root}: a local disk, or a shared mount (NFS, a
 * PVC) that the producers write into. Symbolic links are followed, as the rest of the tree reads
 * them.
 */
public final class FilesystemVolume implements VolumeAccess {

    private static final Logger LOG = LoggerFactory.getLogger(FilesystemVolume.class);

    private final Path root;

    public FilesystemVolume(Path root) {
        if (root == null) {
            throw new IllegalArgumentException("A filesystem volume needs its root directory");
        }
        this.root = root;
    }

    public Path root() {
        return root;
    }

    /**
     * Where {@code path} lies on this filesystem. For what only a filesystem does — writing,
     * deleting, renaming — which the storage port does not offer.
     */
    public Path resolve(VolumePath path) {
        Path resolved = root;
        for (String segment : path.segments()) {
            resolved = resolved.resolve(segment);
        }
        return resolved;
    }

    @Override
    public List<VolumeEntry> list(VolumePath directory) {
        Path dir = resolve(directory);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<VolumeEntry> entries = new ArrayList<>();
        try (Stream<Path> children = Files.list(dir)) {
            children.forEach(child -> describeListed(directory.resolve(child.getFileName().toString()), child)
                    .ifPresent(entries::add));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list a volume directory: path=" + dir, e);
        }
        return entries;
    }

    @Override
    public Optional<VolumeEntry> stat(VolumePath path) {
        Path file = resolve(path);
        try {
            return Optional.of(entry(path, Files.readAttributes(file, BasicFileAttributes.class)));
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the attributes of a volume entry: path=" + file, e);
        }
    }

    @Override
    public Optional<byte[]> read(VolumePath path) {
        Path file = resolve(path);
        try {
            return Optional.of(Files.readAllBytes(file));
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read a volume file: path=" + file, e);
        }
    }

    @Override
    public Optional<OpenedFile> open(VolumePath path) {
        Path file = resolve(path);
        FileChannel channel;
        try {
            channel = FileChannel.open(file, StandardOpenOption.READ);
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot open a volume file: path=" + file, e);
        }
        try {
            return Optional.of(new OpenedFile(path.name(), channel.size(), Channels.newInputStream(channel)));
        } catch (IOException e) {
            closeQuietly(channel);
            throw new UncheckedIOException("Cannot read the size of an opened volume file: path=" + file, e);
        } catch (RuntimeException e) {
            closeQuietly(channel);
            throw e;
        }
    }

    private static void closeQuietly(FileChannel channel) {
        try {
            channel.close();
        } catch (IOException e) {
            LOG.debug("Cannot close a volume file that failed to open: reason={}", e.getMessage());
        }
    }

    /**
     * A listed child, or empty when it cannot be described any more. A session directory changes
     * while it is listed — async-profiler deletes its {@code .jfr.N~} cache after each flush and the
     * compression job replaces recordings with archives — and one entry gone between the listing
     * and its stat must not fail the whole listing.
     */
    private static Optional<VolumeEntry> describeListed(VolumePath path, Path file) {
        try {
            return Optional.of(entry(path, Files.readAttributes(file, BasicFileAttributes.class)));
        } catch (IOException e) {
            LOG.debug("Leaving out a volume entry that can no longer be described: path={} reason={}",
                    file, e.getMessage());
            return Optional.empty();
        }
    }

    private static VolumeEntry entry(VolumePath path, BasicFileAttributes attributes) {
        return new VolumeEntry(
                path, kind(attributes), attributes.size(), Optional.of(attributes.creationTime().toInstant()));
    }

    private static VolumeEntry.Kind kind(BasicFileAttributes attributes) {
        if (attributes.isRegularFile()) {
            return VolumeEntry.Kind.FILE;
        }
        if (attributes.isDirectory()) {
            return VolumeEntry.Kind.DIRECTORY;
        }
        return VolumeEntry.Kind.OTHER;
    }
}

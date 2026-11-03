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

package cafe.jeffrey.microscope.connector.local;

import cafe.jeffrey.shared.volume.FilesystemVolume;
import cafe.jeffrey.shared.volume.VolumeEntry;
import cafe.jeffrey.shared.volume.VolumePath;
import cafe.jeffrey.shared.volume.WritableVolume;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * A {@link FilesystemVolume} kept inside its root: an entry whose real location lies outside the
 * root — reached through a symbolic link anywhere along its path — is not part of the volume. It is
 * left out of listings, reads as absent, and a delete leaves it alone. A link that stays inside the
 * root reads as what it points to.
 *
 * <p>The hub's mount is the volume itself and is trusted as it lies; a directory under {@code /tmp}
 * or a copied tree is not, and a delete through a link must never reach the rest of the machine.
 * The check runs on every call, right before the delegate's, so the tree is read live.
 *
 * <p>A root that is not an existing directory is a volume that cannot be reached: every call fails
 * with an {@link UncheckedIOException}, which the connector reports as unavailable.
 */
public final class ContainedFilesystemVolume implements WritableVolume {

    private static final Logger LOG = LoggerFactory.getLogger(ContainedFilesystemVolume.class);

    private static final String ROOT_MISSING = "The local volume's directory is not there: path=%s";

    private final Path root;
    private final FilesystemVolume volume;

    /**
     * @param root the directory holding the workspaces, absolute; it need not exist yet
     */
    ContainedFilesystemVolume(Path root) {
        if (root == null || !root.isAbsolute()) {
            throw new IllegalArgumentException("A local volume needs the absolute path of its directory");
        }
        this.root = root;
        this.volume = new FilesystemVolume(root);
    }

    /** The directory as configured, before any link in it is followed. */
    public Path root() {
        return root;
    }

    @Override
    public List<VolumeEntry> list(VolumePath directory) {
        Path realRoot = realRoot();
        if (!contained(realRoot, directory)) {
            return List.of();
        }
        return volume.list(directory).stream()
                .filter(entry -> contained(realRoot, entry.path()))
                .toList();
    }

    @Override
    public Optional<VolumeEntry> stat(VolumePath path) {
        if (!contained(realRoot(), path)) {
            return Optional.empty();
        }
        return volume.stat(path);
    }

    @Override
    public Optional<OpenedFile> open(VolumePath file) {
        if (!contained(realRoot(), file)) {
            return Optional.empty();
        }
        return volume.open(file);
    }

    @Override
    public void delete(VolumePath path) {
        Path realRoot = realRoot();
        if (!contained(realRoot, path)) {
            LOG.warn("Leaving alone an entry that lies outside the local volume: path={} root={}", path, realRoot);
            return;
        }
        volume.delete(path);
    }

    /**
     * The root's real location, looked at now.
     *
     * @throws UncheckedIOException when the root is not an existing directory
     */
    private Path realRoot() {
        if (!Files.isDirectory(root)) {
            throw new UncheckedIOException(ROOT_MISSING.formatted(root), new NoSuchFileException(root.toString()));
        }
        try {
            return root.toRealPath();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot resolve the local volume's directory: path=" + root, e);
        }
    }

    /**
     * Whether {@code path} lies inside the root once every link along it is followed. Nothing there
     * counts as inside: the delegate then answers it as absent, and a dangling link is removed as
     * the link itself.
     */
    private boolean contained(Path realRoot, VolumePath path) {
        Path location = volume.resolve(path);
        try {
            boolean inside = location.toRealPath().startsWith(realRoot);
            if (!inside) {
                LOG.debug("Leaving out an entry whose real location is outside the local volume: path={}", location);
            }
            return inside;
        } catch (NoSuchFileException e) {
            return true;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot resolve a local volume entry: path=" + location, e);
        }
    }
}

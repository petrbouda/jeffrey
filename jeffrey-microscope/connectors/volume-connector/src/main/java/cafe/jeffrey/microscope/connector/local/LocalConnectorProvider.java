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

import cafe.jeffrey.microscope.connector.api.ConnectorClients;
import cafe.jeffrey.microscope.connector.api.spi.ConnectorProvider;
import cafe.jeffrey.microscope.connector.api.spi.DeclaredConnector;
import cafe.jeffrey.microscope.connector.volume.VolumeConnectorClients;
import cafe.jeffrey.microscope.connector.volume.VolumeSource;
import cafe.jeffrey.microscope.model.connector.ConnectorKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The connector kind of a directory on this machine: the {@code local} block, read live on every
 * call. A directory that is not there yet is still a declaration — it reads as unreachable until it
 * appears.
 */
public final class LocalConnectorProvider implements ConnectorProvider<LocalSettings> {

    private static final Logger LOG = LoggerFactory.getLogger(LocalConnectorProvider.class);

    /** What a local connector is called when its directory has no name (the filesystem root). */
    private static final String ROOT_NAME = LocalDirectoryAddress.KIND.block();

    @Override
    public ConnectorKind kind() {
        return LocalDirectoryAddress.KIND;
    }

    @Override
    public Class<LocalSettings> settingsType() {
        return LocalSettings.class;
    }

    @Override
    public DeclaredConnector declare(LocalSettings settings) {
        if (settings.path() == null || settings.path().isBlank()) {
            throw new IllegalArgumentException(
                    "path is required: the absolute path of the directory holding the workspaces");
        }
        // InvalidPathException, a path the filesystem cannot hold, is an IllegalArgumentException too
        LocalDirectoryAddress address = new LocalDirectoryAddress(Path.of(settings.path().trim()), settings.readOnly());
        if (!Files.isDirectory(address.root())) {
            LOG.warn("The local connector's directory is not there yet; it reads as unreachable until it is: root={}",
                    address.root());
        }
        Path directoryName = address.root().getFileName();
        return new Declared(address, directoryName == null ? ROOT_NAME : directoryName.toString());
    }

    private record Declared(LocalDirectoryAddress address, String defaultName) implements DeclaredConnector {

        @Override
        public ConnectorClients open() {
            return VolumeConnectorClients.of(source());
        }

        /** The directory as a recording volume, keeping the declaration's read-only flag. */
        private VolumeSource source() {
            ContainedFilesystemVolume volume = LocalVolumes.open(address);
            LOG.info("Opened a local recording volume: address={} root={}", address, volume.root());
            return new VolumeSource(volume, LocalDirectoryAddress.KIND.block(), address.readOnly());
        }
    }
}

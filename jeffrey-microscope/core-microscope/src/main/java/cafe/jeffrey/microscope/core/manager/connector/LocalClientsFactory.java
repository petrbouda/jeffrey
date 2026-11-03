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

package cafe.jeffrey.microscope.core.manager.connector;

import cafe.jeffrey.microscope.connector.api.ConnectorClients;
import cafe.jeffrey.microscope.connector.local.ContainedFilesystemVolume;
import cafe.jeffrey.microscope.connector.local.LocalVolumes;
import cafe.jeffrey.microscope.connector.volume.VolumeConnectorClients;
import cafe.jeffrey.microscope.connector.volume.VolumeSource;
import cafe.jeffrey.microscope.model.connector.ConnectorKind;
import cafe.jeffrey.microscope.model.connector.LocalDirectoryAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

/**
 * The clients of the connector when it is the recording volume in a directory on this machine,
 * opened once per address. Nothing is remembered between calls — every level is one listing of its
 * parent — so what the profiler writes and the hub's jobs remove is seen on the next read. Its reads
 * run one after another: a local listing has no round-trip to overlap.
 */
public final class LocalClientsFactory {

    private static final Logger LOG = LoggerFactory.getLogger(LocalClientsFactory.class);

    /** What a local directory reports where a hub reports its version. */
    private static final String DESCRIPTION = ConnectorKind.LOCAL.block();

    private final ConcurrentHashMap<LocalDirectoryAddress, ConnectorClients> cache = new ConcurrentHashMap<>();

    public ConnectorClients apply(LocalDirectoryAddress address) {
        return cache.computeIfAbsent(address, LocalClientsFactory::open);
    }

    private static ConnectorClients open(LocalDirectoryAddress address) {
        ContainedFilesystemVolume volume = LocalVolumes.open(address);
        LOG.info("Opened a local recording volume: address={} root={}", address, volume.root());
        return VolumeConnectorClients.of(new VolumeSource(volume, DESCRIPTION, address.readOnly()));
    }
}

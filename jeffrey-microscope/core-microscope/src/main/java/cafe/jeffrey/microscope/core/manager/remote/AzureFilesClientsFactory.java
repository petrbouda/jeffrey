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

package cafe.jeffrey.microscope.core.manager.remote;

import cafe.jeffrey.microscope.remote.client.RemoteClients;
import cafe.jeffrey.microscope.model.remote.AzureFilesAddress;
import cafe.jeffrey.microscope.model.remote.AzureFilesAuth;
import cafe.jeffrey.microscope.volume.azure.AzureFilesCredential;
import cafe.jeffrey.microscope.volume.azure.AzureFilesVolumes;
import cafe.jeffrey.microscope.volume.client.BoundedReadFanOut;
import cafe.jeffrey.microscope.volume.client.RememberingVolume;
import cafe.jeffrey.microscope.volume.client.VolumeRemoteClients;
import cafe.jeffrey.microscope.volume.client.VolumeSource;
import cafe.jeffrey.shared.common.exception.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The clients of the remote when it is the recording volume on an Azure file share, opened once
 * per address and signed in the way the address says. A SAS token comes from configuration and
 * lives in memory only.
 */
public final class AzureFilesClientsFactory {

    private static final Logger LOG = LoggerFactory.getLogger(AzureFilesClientsFactory.class);

    /** What a share reports where a hub reports its version. */
    private static final String DESCRIPTION = "azure-files";

    private final Clock clock;
    private final Optional<String> sasToken;
    private final ConcurrentHashMap<AzureFilesAddress, RemoteClients> cache = new ConcurrentHashMap<>();

    /**
     * @param sasToken the configured remote's SAS token, when it signs in with one
     */
    public AzureFilesClientsFactory(Clock clock, Optional<String> sasToken) {
        this.clock = clock;
        this.sasToken = sasToken;
    }

    public RemoteClients apply(AzureFilesAddress address) {
        return cache.computeIfAbsent(address, this::open);
    }

    /**
     * The share is read through a {@link RememberingVolume}, so the markers the provisioner writes
     * once are asked for once, not on every page; and the independent reads of one call go out
     * {@link BoundedReadFanOut#DEFAULT_MAX_IN_FLIGHT} at a time, so their round-trips overlap.
     */
    private RemoteClients open(AzureFilesAddress address) {
        RememberingVolume volume = new RememberingVolume(AzureFilesVolumes.open(address, credential(address)), clock);
        LOG.info("Opened an Azure Files recording volume: address={}", address);
        return VolumeRemoteClients.of(new VolumeSource(
                volume, clock, DESCRIPTION, address.readOnly(),
                new BoundedReadFanOut(BoundedReadFanOut.DEFAULT_MAX_IN_FLIGHT)));
    }

    private AzureFilesCredential credential(AzureFilesAddress address) {
        if (address.auth() == AzureFilesAuth.ENTRA) {
            return new AzureFilesCredential.Entra();
        }
        String token = sasToken.orElseThrow(() -> Exceptions.invalidRequest(
                "No SAS token is configured for " + address + ". Set jeffrey.microscope.remote.azure-files.sas-token "
                        + "and restart."));
        warnOnRights(address, SasPermissions.of(token));
        return new AzureFilesCredential.Sas(token);
    }

    /**
     * A token with more rights than the source uses widens what a leaked configuration exposes;
     * one with fewer fails on the first change. Only the rights are logged, never the token.
     */
    private static void warnOnRights(AzureFilesAddress address, SasPermissions permissions) {
        if (address.readOnly() && permissions.grantsChanges()) {
            LOG.warn("The SAS token of a read-only Azure Files source grants more than it needs; issue one with "
                    + "read and list only: address={} permissions={}", address, permissions);
        } else if (!address.readOnly() && !permissions.grantsEveryChange()) {
            LOG.warn("The SAS token of a writable Azure Files source lacks a right its changes need; issue one with "
                    + "read, list, create, write and delete, or declare the source read-only: address={} permissions={}",
                    address, permissions);
        }
    }
}

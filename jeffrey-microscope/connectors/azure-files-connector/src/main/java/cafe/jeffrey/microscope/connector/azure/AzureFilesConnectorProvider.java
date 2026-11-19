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

package cafe.jeffrey.microscope.connector.azure;

import cafe.jeffrey.microscope.connector.api.ConnectorClients;
import cafe.jeffrey.microscope.connector.api.spi.ConnectorProvider;
import cafe.jeffrey.microscope.connector.api.spi.DeclaredConnector;
import cafe.jeffrey.microscope.connector.volume.BoundedReadFanOut;
import cafe.jeffrey.microscope.connector.volume.VolumeConnectorClients;
import cafe.jeffrey.microscope.connector.volume.VolumeSource;
import cafe.jeffrey.microscope.model.connector.ConnectorKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * The connector kind of an Azure file share: the {@code azure-files} block, read over the Azure Files
 * REST API, SMB and NFS shares alike. A SAS token stays inside the declared connector, handed only to
 * the volume it opens; the address carries none.
 */
public final class AzureFilesConnectorProvider implements ConnectorProvider<AzureFilesSettings> {

    private static final Logger LOG = LoggerFactory.getLogger(AzureFilesConnectorProvider.class);

    /** What a share reports as the version in its connector info. */
    private static final String DESCRIPTION = AzureFilesAddress.KIND.block();

    @Override
    public ConnectorKind kind() {
        return AzureFilesAddress.KIND;
    }

    @Override
    public Class<AzureFilesSettings> settingsType() {
        return AzureFilesSettings.class;
    }

    /** Never quotes the token: a refusal is logged. */
    @Override
    public DeclaredConnector declare(AzureFilesSettings settings) {
        AzureFilesProtocol protocol = AzureFilesProtocol.parse(settings.protocol()).orElseThrow(() ->
                new IllegalArgumentException("protocol=%s must be smb or nfs".formatted(settings.protocol())));
        AzureFilesAuth auth = AzureFilesAuth.parse(settings.auth()).orElseThrow(() ->
                new IllegalArgumentException("auth=%s must be sas or entra".formatted(settings.auth())));
        Optional<String> sasToken = Optional.ofNullable(settings.sasToken())
                .map(String::trim)
                .filter(token -> !token.isEmpty());
        if (auth == AzureFilesAuth.SAS && sasToken.isEmpty()) {
            throw new IllegalArgumentException("sas-token is required when auth=sas");
        }
        AzureFilesAddress address = new AzureFilesAddress(
                settings.endpoint(), settings.share(), settings.rootPath(), protocol, auth, settings.readOnly());
        AzureFilesCredential credential = auth == AzureFilesAuth.ENTRA
                ? new AzureFilesCredential.Entra()
                : new AzureFilesCredential.Sas(sasToken.orElseThrow());
        return new Declared(address, credential);
    }

    private record Declared(AzureFilesAddress address, AzureFilesCredential credential) implements DeclaredConnector {

        @Override
        public String defaultName() {
            return address.share();
        }

        @Override
        public ConnectorClients open() {
            return VolumeConnectorClients.of(source());
        }

        /**
         * The independent reads of one call go out {@link BoundedReadFanOut#DEFAULT_MAX_IN_FLIGHT} at a
         * time, so their round-trips overlap. Nothing is remembered: a level is one listing of its
         * parent, with no file in it to read.
         */
        private VolumeSource source() {
            if (credential instanceof AzureFilesCredential.Sas sas) {
                warnOnRights(address, SasPermissions.of(sas.token()));
            }
            AzureFilesVolume volume = AzureFilesVolumes.open(address, credential);
            LOG.info("Opened an Azure Files recording volume: address={}", address);
            return new VolumeSource(volume, DESCRIPTION, address.readOnly(),
                    new BoundedReadFanOut(BoundedReadFanOut.DEFAULT_MAX_IN_FLIGHT));
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
                LOG.warn("The SAS token of a writable Azure Files source lacks a right its changes need; issue one "
                        + "with read, list and delete, or declare the source read-only: address={} permissions={}",
                        address, permissions);
            }
        }
    }
}

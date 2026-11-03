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

package cafe.jeffrey.microscope.core.configuration.properties;

import cafe.jeffrey.microscope.model.connector.AzureFilesAddress;
import cafe.jeffrey.microscope.model.connector.AzureFilesAuth;
import cafe.jeffrey.microscope.model.connector.AzureFilesProtocol;
import cafe.jeffrey.microscope.model.connector.ConnectorAddress;
import cafe.jeffrey.microscope.model.connector.ConnectorInfo;
import cafe.jeffrey.microscope.model.connector.ConnectorKind;
import cafe.jeffrey.microscope.model.connector.LocalDirectoryAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * The one connector declared under {@code jeffrey.microscope.connector.*}: the recording volume on an
 * Azure file share or in a local directory, one block of the two — the block's {@code read-only=true}
 * makes Microscope refuse every change to it:
 * <pre>
 * jeffrey.microscope.connector.azure-files.endpoint=https://jeffreyprod.file.core.windows.net
 * jeffrey.microscope.connector.azure-files.share=recordings
 * jeffrey.microscope.connector.azure-files.protocol=smb
 * jeffrey.microscope.connector.azure-files.sas-token=${AZURE_FILES_SAS}
 * </pre>
 * or
 * <pre>
 * jeffrey.microscope.connector.local.path=/tmp/jeffrey-volume
 * </pre>
 * This configuration is the only place the connector is declared: it is read at startup and never
 * stored, and the UI shows it but never connects, changes or disconnects one. A SAS token never
 * leaves memory; {@link #sasToken()} hands it to whatever opens the share.
 * <p>
 * A JavaBean rather than a record, as the relaxed binder needs setters for the nested block.
 */
@ConfigurationProperties(ConfiguredConnectorProperties.PREFIX)
public class ConfiguredConnectorProperties {

    /** Must mirror the annotation above; used to build property paths for error messages. */
    static final String PREFIX = "jeffrey.microscope.connector";

    private static final Logger LOG = LoggerFactory.getLogger(ConfiguredConnectorProperties.class);

    /** What a local connector is called when neither its name nor its directory names it (the root). */
    private static final String LOCAL_FALLBACK_NAME = ConnectorKind.LOCAL.block();

    /** Microscope reaches exactly one connector, so the two blocks exclude each other. */
    private static final String BOTH_DECLARED = PREFIX + "." + ConnectorKind.AZURE_FILES.block() + " and " + PREFIX
            + "." + ConnectorKind.LOCAL.block() + " are both declared; declare one connector kind, not both";

    private String name;
    private AzureFilesEntry azureFiles;
    private LocalEntry local;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public AzureFilesEntry getAzureFiles() {
        return azureFiles;
    }

    public void setAzureFiles(AzureFilesEntry azureFiles) {
        this.azureFiles = azureFiles;
    }

    public LocalEntry getLocal() {
        return local;
    }

    public void setLocal(LocalEntry local) {
        this.local = local;
    }

    /**
     * The declared connector, or empty when none is declared — or when the declaration is unusable,
     * which is logged and left out rather than failing startup: Microscope is primarily a local
     * profile viewer, and a typo in an optional connector should not cost the user their recordings.
     */
    public Optional<ConnectorInfo> resolve() {
        if (azureFiles != null && local != null) {
            LOG.warn("Ignoring the declared connector: reason={}", BOTH_DECLARED);
            return Optional.empty();
        }
        if (azureFiles != null) {
            return resolveAzureFiles();
        }
        if (local != null) {
            return resolveLocal();
        }
        return Optional.empty();
    }

    private Optional<ConnectorInfo> resolveAzureFiles() {
        String problem = azureFiles.validate(PREFIX + "." + ConnectorKind.AZURE_FILES.block());
        if (problem != null) {
            LOG.warn("Ignoring the declared connector: reason={}", problem);
            return Optional.empty();
        }
        AzureFilesAddress address = azureFiles.address();
        return Optional.of(new ConnectorInfo(resolveName(address.share()), address));
    }

    private Optional<ConnectorInfo> resolveLocal() {
        String property = PREFIX + "." + ConnectorKind.LOCAL.block();
        String problem = local.validate(property);
        if (problem != null) {
            LOG.warn("Ignoring the declared connector: reason={}", problem);
            return Optional.empty();
        }
        LocalDirectoryAddress address = local.address();
        if (!Files.isDirectory(address.root())) {
            LOG.warn("The local connector's directory is not there yet; it reads as unreachable until it is: "
                    + "property={}.path root={}", property, address.root());
        }
        Path directoryName = address.root().getFileName();
        return Optional.of(new ConnectorInfo(
                resolveName(directoryName == null ? LOCAL_FALLBACK_NAME : directoryName.toString()), address));
    }

    /**
     * The declaration read once: the connector and, for a share that signs in with one, its SAS token.
     * What the beans are built from, so an unusable declaration is reported once.
     */
    public Declared read() {
        Optional<ConnectorInfo> connector = resolve();
        return new Declared(connector, sasTokenOf(connector));
    }

    /** The SAS token of a valid declaration that signs in with one; it never leaves memory. */
    public Optional<String> sasToken() {
        return sasTokenOf(resolve());
    }

    private Optional<String> sasTokenOf(Optional<ConnectorInfo> connector) {
        return connector
                .filter(declared -> signsInWithSas(declared.address()))
                .map(_ -> azureFiles.getSasToken().trim());
    }

    private static boolean signsInWithSas(ConnectorAddress address) {
        return address instanceof AzureFilesAddress share && share.auth() == AzureFilesAuth.SAS;
    }

    /** The connector configuration declares, and the SAS token it signs in with when it uses one. */
    public record Declared(Optional<ConnectorInfo> connector, Optional<String> sasToken) {
    }

    /**
     * Falls back to what the address names — the share, or the directory — so an address on its own
     * is a complete declaration.
     */
    private String resolveName(String fallback) {
        if (name != null && !name.isBlank()) {
            return name.trim();
        }
        return fallback;
    }

    /**
     * The {@code azure-files} block of the connector declaration: the share holding the recording volume,
     * and how to sign in to it. A JavaBean for the same reason as the class it sits in.
     */
    public static class AzureFilesEntry {

        private static final String DEFAULT_PROTOCOL = "smb";
        private static final String DEFAULT_AUTH = "sas";

        private String endpoint;
        private String share;
        private String rootPath;
        private String protocol = DEFAULT_PROTOCOL;
        private String auth = DEFAULT_AUTH;
        private String sasToken;
        private boolean readOnly;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getShare() {
            return share;
        }

        public void setShare(String share) {
            this.share = share;
        }

        public String getRootPath() {
            return rootPath;
        }

        public void setRootPath(String rootPath) {
            this.rootPath = rootPath;
        }

        public String getProtocol() {
            return protocol;
        }

        public void setProtocol(String protocol) {
            this.protocol = protocol;
        }

        public String getAuth() {
            return auth;
        }

        public void setAuth(String auth) {
            this.auth = auth;
        }

        public String getSasToken() {
            return sasToken;
        }

        public void setSasToken(String sasToken) {
            this.sasToken = sasToken;
        }

        public boolean isReadOnly() {
            return readOnly;
        }

        public void setReadOnly(boolean readOnly) {
            this.readOnly = readOnly;
        }

        /** The address; call only once {@link #validate} has passed. */
        AzureFilesAddress address() {
            return new AzureFilesAddress(
                    endpoint,
                    share,
                    rootPath,
                    AzureFilesProtocol.parse(protocol).orElseThrow(),
                    AzureFilesAuth.parse(auth).orElseThrow(),
                    readOnly);
        }

        /**
         * @return why this block is unusable, or {@code null} when it is valid. Never quotes the
         * token: the reason is logged.
         */
        String validate(String path) {
            if (AzureFilesProtocol.parse(protocol).isEmpty()) {
                return "%s.protocol=%s must be smb or nfs".formatted(path, protocol);
            }
            Optional<AzureFilesAuth> signIn = AzureFilesAuth.parse(auth);
            if (signIn.isEmpty()) {
                return "%s.auth=%s must be sas or entra".formatted(path, auth);
            }
            if (signIn.get() == AzureFilesAuth.SAS && (sasToken == null || sasToken.isBlank())) {
                return path + ".sas-token is required when auth=sas";
            }
            try {
                address();
            } catch (IllegalArgumentException e) {
                return path + ": " + e.getMessage();
            }
            return null;
        }
    }

    /**
     * The {@code local} block of the connector declaration: the directory on this machine holding the
     * recording volume. A JavaBean for the same reason as the class it sits in.
     */
    public static class LocalEntry {

        private String path;
        private boolean readOnly;

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public boolean isReadOnly() {
            return readOnly;
        }

        public void setReadOnly(boolean readOnly) {
            this.readOnly = readOnly;
        }

        /** The address; call only once {@link #validate} has passed. */
        LocalDirectoryAddress address() {
            return new LocalDirectoryAddress(Path.of(path.trim()), readOnly);
        }

        /**
         * @return why this block is unusable, or {@code null} when it is valid. A directory that is
         * not there yet is valid: the connector reads as unreachable until it appears.
         */
        String validate(String property) {
            if (path == null || path.isBlank()) {
                return property + ".path is required: the absolute path of the directory holding the workspaces";
            }
            try {
                address();
            } catch (IllegalArgumentException e) {
                // InvalidPathException, a path the filesystem cannot hold, is one of these too
                return property + ": " + e.getMessage();
            }
            return null;
        }
    }
}

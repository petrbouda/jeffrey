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

import cafe.jeffrey.microscope.model.remote.AzureFilesAddress;
import cafe.jeffrey.microscope.model.remote.AzureFilesAuth;
import cafe.jeffrey.microscope.model.remote.AzureFilesProtocol;
import cafe.jeffrey.microscope.model.remote.HubAddress;
import cafe.jeffrey.microscope.model.remote.RemoteAddress;
import cafe.jeffrey.microscope.model.remote.RemoteInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Optional;

/**
 * The one remote declared under {@code jeffrey.microscope.remote.*}: a Jeffrey Hub reached over gRPC,
 * <pre>
 * jeffrey.microscope.remote.hostname=hub.example.com
 * jeffrey.microscope.remote.port=443
 * </pre>
 * or the recording volume on an Azure file share, with no hub between —
 * {@code azure-files.read-only=true} makes Microscope refuse every change to it:
 * <pre>
 * jeffrey.microscope.remote.azure-files.endpoint=https://jeffreyprod.file.core.windows.net
 * jeffrey.microscope.remote.azure-files.share=recordings
 * jeffrey.microscope.remote.azure-files.protocol=smb
 * jeffrey.microscope.remote.azure-files.sas-token=${AZURE_FILES_SAS}
 * </pre>
 * This configuration is the only place the remote is declared: it is read at startup and never
 * stored, and the UI shows it but never connects, changes or disconnects one. A SAS token never
 * leaves memory; {@link #sasToken()} hands it to whatever opens the share.
 * <p>
 * A JavaBean rather than a record, as the relaxed binder needs setters for the nested block.
 */
@ConfigurationProperties(ConfiguredRemoteProperties.PREFIX)
public class ConfiguredRemoteProperties {

    /** Must mirror the annotation above; used to build property paths for error messages. */
    static final String PREFIX = "jeffrey.microscope.remote";

    private static final Logger LOG = LoggerFactory.getLogger(ConfiguredRemoteProperties.class);

    private static final int DEFAULT_PORT = 9090;
    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 65535;

    private String name;
    private String hostname;
    private int port = DEFAULT_PORT;
    private boolean plaintext;
    private AzureFilesEntry azureFiles;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isPlaintext() {
        return plaintext;
    }

    public void setPlaintext(boolean plaintext) {
        this.plaintext = plaintext;
    }

    public AzureFilesEntry getAzureFiles() {
        return azureFiles;
    }

    public void setAzureFiles(AzureFilesEntry azureFiles) {
        this.azureFiles = azureFiles;
    }

    /**
     * The declared remote, or empty when none is declared — or when the declaration is unusable,
     * which is logged and left out rather than failing startup: Microscope is primarily a local
     * profile viewer, and a typo in an optional remote should not cost the user their recordings.
     */
    public Optional<RemoteInfo> resolve() {
        if (!declaresAny()) {
            return Optional.empty();
        }
        String problem = validate();
        if (problem != null) {
            LOG.warn("Ignoring the declared remote: reason={}", problem);
            return Optional.empty();
        }
        RemoteAddress address = address();
        return Optional.of(new RemoteInfo(resolveName(address), address));
    }

    /**
     * The declaration read once: the remote and, for a share that signs in with one, its SAS token.
     * What the beans are built from, so an unusable declaration is reported once.
     */
    public Declared read() {
        Optional<RemoteInfo> remote = resolve();
        return new Declared(remote, sasTokenOf(remote));
    }

    /**
     * The SAS token of a valid Azure Files declaration that signs in with one; it never leaves
     * memory.
     */
    public Optional<String> sasToken() {
        return sasTokenOf(resolve());
    }

    private Optional<String> sasTokenOf(Optional<RemoteInfo> remote) {
        return remote
                .map(RemoteInfo::address)
                .filter(address -> address instanceof AzureFilesAddress azure && azure.auth() == AzureFilesAuth.SAS)
                .map(_ -> azureFiles.getSasToken().trim());
    }

    /**
     * The remote configuration declares, and the SAS token it signs in with when it is a share
     * that uses one.
     */
    public record Declared(Optional<RemoteInfo> remote, Optional<String> sasToken) {
    }

    private boolean declaresAny() {
        return hasHostname() || azureFiles != null;
    }

    private boolean hasHostname() {
        return hostname != null && !hostname.isBlank();
    }

    /** The address declared; call only once {@link #validate} has passed. */
    private RemoteAddress address() {
        if (azureFiles != null) {
            return azureFiles.address();
        }
        return new HubAddress(hostname.trim(), port, plaintext);
    }

    /**
     * @return why the declaration is unusable, or {@code null} when it is valid
     */
    private String validate() {
        if (hasHostname() && azureFiles != null) {
            return PREFIX + " declares both a hostname and azure-files; a remote is one or the other";
        }
        if (azureFiles != null) {
            return azureFiles.validate(PREFIX + ".azure-files");
        }
        if (port < MIN_PORT || port > MAX_PORT) {
            return "%s.port=%d must be between %d and %d".formatted(PREFIX, port, MIN_PORT, MAX_PORT);
        }
        return null;
    }

    /** Falls back to the hub's host or the share's name, so an address on its own is a complete declaration. */
    private String resolveName(RemoteAddress address) {
        if (name != null && !name.isBlank()) {
            return name.trim();
        }
        return switch (address) {
            case HubAddress hub -> hub.hostname();
            case AzureFilesAddress share -> share.share();
        };
    }

    /**
     * The {@code azure-files} block of the remote declaration: the share holding the recording volume,
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
}

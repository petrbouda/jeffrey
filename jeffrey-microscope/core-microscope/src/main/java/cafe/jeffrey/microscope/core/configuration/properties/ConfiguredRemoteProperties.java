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
import cafe.jeffrey.microscope.model.remote.RemoteInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Optional;

/**
 * The one remote declared under {@code jeffrey.microscope.remote.*}: the recording volume on an
 * Azure file share — {@code azure-files.read-only=true} makes Microscope refuse every change to it:
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

    private String name;
    private AzureFilesEntry azureFiles;

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

    /**
     * The declared remote, or empty when none is declared — or when the declaration is unusable,
     * which is logged and left out rather than failing startup: Microscope is primarily a local
     * profile viewer, and a typo in an optional remote should not cost the user their recordings.
     */
    public Optional<RemoteInfo> resolve() {
        if (azureFiles == null) {
            return Optional.empty();
        }
        String problem = azureFiles.validate(PREFIX + ".azure-files");
        if (problem != null) {
            LOG.warn("Ignoring the declared remote: reason={}", problem);
            return Optional.empty();
        }
        AzureFilesAddress address = azureFiles.address();
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

    /** The SAS token of a valid declaration that signs in with one; it never leaves memory. */
    public Optional<String> sasToken() {
        return sasTokenOf(resolve());
    }

    private Optional<String> sasTokenOf(Optional<RemoteInfo> remote) {
        return remote
                .filter(declared -> declared.address().auth() == AzureFilesAuth.SAS)
                .map(_ -> azureFiles.getSasToken().trim());
    }

    /** The remote configuration declares, and the SAS token it signs in with when it uses one. */
    public record Declared(Optional<RemoteInfo> remote, Optional<String> sasToken) {
    }

    /** Falls back to the share's name, so an address on its own is a complete declaration. */
    private String resolveName(AzureFilesAddress address) {
        if (name != null && !name.isBlank()) {
            return name.trim();
        }
        return address.share();
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

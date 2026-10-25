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

import cafe.jeffrey.microscope.model.hub.AzureFilesAddress;
import cafe.jeffrey.microscope.model.hub.AzureFilesAuth;
import cafe.jeffrey.microscope.model.hub.AzureFilesProtocol;
import cafe.jeffrey.microscope.model.hub.GrpcHubAddress;
import cafe.jeffrey.microscope.model.hub.HubAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Hubs declared statically under {@code jeffrey.microscope.hubs.*}: a jeffrey-hub reached over gRPC,
 * <pre>
 * jeffrey.microscope.hubs.production.hostname=hub.example.com
 * jeffrey.microscope.hubs.production.port=443
 * </pre>
 * or the recording volume reached straight on an Azure file share, with no hub between —
 * {@code azure-files.read-only=true} makes Microscope refuse every change to it:
 * <pre>
 * jeffrey.microscope.hubs.prod-share.azure-files.endpoint=https://jeffreyprod.file.core.windows.net
 * jeffrey.microscope.hubs.prod-share.azure-files.share=recordings
 * jeffrey.microscope.hubs.prod-share.azure-files.protocol=smb
 * jeffrey.microscope.hubs.prod-share.azure-files.sas-token=${AZURE_FILES_SAS}
 * </pre>
 * A SAS token stays in this configuration: the registry stores how a source signs in, never the
 * token, which {@link #sasTokens()} hands to whatever opens the share.
 * Configuration owns every hub declared here: {@code ConfiguredHubsReconciler} adds, updates and
 * removes rows on each startup so the registry matches this map, and the UI cannot delete them.
 * <p>
 * The prefix is {@code jeffrey.microscope} rather than {@code jeffrey.microscope.hubs} on purpose:
 * the binder appends the field name, so the latter would bind
 * {@code jeffrey.microscope.hubs.hubs.<key>}. Unknown keys under the prefix are ignored by default,
 * so the rest of the {@code jeffrey.microscope.*} namespace is unaffected.
 */
@ConfigurationProperties("jeffrey.microscope")
public class ConfiguredHubsProperties {

    /** Must mirror the annotation above; used to build property paths for error messages. */
    static final String PREFIX = "jeffrey.microscope";

    private static final Logger LOG = LoggerFactory.getLogger(ConfiguredHubsProperties.class);

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-zA-Z0-9]");

    /**
     * Marks a hub id as configuration-owned. Also guarantees the id can never collide with the
     * UUIDv7 that {@code IDGenerator} mints for a hub added through the UI.
     */
    private static final String CONFIG_HUB_ID_PREFIX = "cfg-";

    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 65535;

    private Map<String, HubEntry> hubs = new LinkedHashMap<>();

    public Map<String, HubEntry> getHubs() {
        return hubs;
    }

    public void setHubs(Map<String, HubEntry> hubs) {
        this.hubs = hubs;
    }

    /**
     * Validated, id-resolved view of the declarations, in declaration order.
     * <p>
     * A single malformed entry is skipped with a warning rather than failing startup: Microscope is
     * primarily a local profile viewer, and a typo in an optional hub should not cost the user
     * access to their recordings. Genuine ambiguity is different — two entries that would fight
     * over the same identity or the same address have no correct resolution, so they throw.
     */
    public List<DesiredHub> resolve() {
        List<DesiredHub> resolved = new ArrayList<>();
        Map<String, String> keysById = new HashMap<>();
        Map<String, String> keysByAddress = new HashMap<>();

        for (Map.Entry<String, HubEntry> entry : hubs.entrySet()) {
            String key = entry.getKey();
            HubEntry declared = entry.getValue();
            if (declared == null) {
                LOG.warn("Ignoring hub declaration with no properties: property={}", propertyPath(key));
                continue;
            }

            String invalidReason = declared.validate(key);
            if (invalidReason != null) {
                LOG.warn("Ignoring invalid hub declaration: key={} reason={}", key, invalidReason);
                continue;
            }

            String hubId = hubIdFor(key);
            String clashingKey = keysById.put(hubId, key);
            if (clashingKey != null) {
                throw new IllegalArgumentException(
                        "Hub keys resolve to the same identity: keys=[%s, %s] hub_id=%s. Keys are compared ignoring case and punctuation, so they must differ by more than a dash or an underscore."
                                .formatted(clashingKey, key, hubId));
            }

            HubAddress address = declared.address();
            // Keyed on the address's identity, matching what the hubs table keeps unique — two
            // entries differing only in `plaintext` or in how they sign in would still collide there.
            String clashingAddressKey = keysByAddress.put(address.identity(), key);
            if (clashingAddressKey != null) {
                throw new IllegalArgumentException(
                        "Duplicate hub address in configuration: address=%s keys=[%s, %s]"
                                .formatted(address, clashingAddressKey, key));
            }

            resolved.add(new DesiredHub(key, hubId, declared.resolveName(key), address));
        }

        return resolved;
    }

    /**
     * The SAS token of every Azure Files declaration that signs in with one, keyed by the address's
     * {@link HubAddress#identity()}. Only valid declarations; the tokens never leave memory.
     */
    public Map<String, String> sasTokens() {
        Map<String, String> tokens = new HashMap<>();
        for (Map.Entry<String, HubEntry> entry : hubs.entrySet()) {
            HubEntry declared = entry.getValue();
            if (declared == null || declared.validate(entry.getKey()) != null) {
                continue;
            }
            if (declared.address() instanceof AzureFilesAddress azure && azure.auth() == AzureFilesAuth.SAS) {
                tokens.put(azure.identity(), declared.getAzureFiles().getSasToken().trim());
            }
        }
        return tokens;
    }

    /**
     * Derives a stable hub id from the map key.
     * <p>
     * Stripping punctuation is not cosmetic: an environment variable cannot carry a dash, so Spring
     * binds {@code JEFFREY_MICROSCOPE_HUBS_PRODEU_...} as the key {@code prodeu} while a properties
     * file would spell the same hub {@code prod-eu}. Normalising means both resolve to the same id,
     * so moving a configuration from a file to a container does not silently orphan the
     * {@code origin.hubId} tags already written onto downloaded recordings.
     */
    public static String hubIdFor(String key) {
        return CONFIG_HUB_ID_PREFIX
                + NON_ALPHANUMERIC.matcher(key).replaceAll("").toLowerCase(Locale.ROOT);
    }

    private static String propertyPath(String key) {
        return PREFIX + ".hubs." + key;
    }

    /**
     * One resolved declaration: what the registry should contain for this key.
     */
    public record DesiredHub(String key, String hubId, String name, HubAddress address) {
    }

    /**
     * A JavaBean rather than a record on purpose — the relaxed binder resolves nested map values
     * through {@code JavaBeanBinder}, which needs a no-arg constructor and setters.
     */
    public static class HubEntry {

        private static final int DEFAULT_PORT = 9090;

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

        /** The address this entry declares; call only once {@link #validate} has passed. */
        HubAddress address() {
            if (azureFiles != null) {
                return azureFiles.address();
            }
            return new GrpcHubAddress(hostname.trim(), port, plaintext);
        }

        /**
         * @return why this entry is unusable, or {@code null} when it is valid
         */
        String validate(String key) {
            boolean grpc = hostname != null && !hostname.isBlank();
            if (grpc && azureFiles != null) {
                return propertyPath(key) + " declares both a hostname and azure-files; a hub is one or the other";
            }
            if (azureFiles != null) {
                return azureFiles.validate(propertyPath(key) + ".azure-files");
            }
            if (!grpc) {
                return propertyPath(key) + ".hostname is required";
            }
            if (port < MIN_PORT || port > MAX_PORT) {
                return "%s.port=%d must be between %d and %d"
                        .formatted(propertyPath(key), port, MIN_PORT, MAX_PORT);
            }
            return null;
        }

        /**
         * Falls back to the map key, so a hostname on its own is a complete declaration.
         */
        String resolveName(String key) {
            if (name == null || name.isBlank()) {
                return key;
            }
            return name.trim();
        }
    }

    /**
     * The {@code azure-files} block of a hub declaration: the share holding the recording volume,
     * and how to sign in to it. A JavaBean for the same reason as {@link HubEntry}.
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

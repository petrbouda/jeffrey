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

package cafe.jeffrey.microscope.model.hub;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/**
 * A recording volume on an Azure file share, reached over the Azure Files REST API with no hub in
 * between, SMB and NFS shares alike. The share holds the hub's shared volume as it is, so the
 * workspaces lie directly under {@code rootPath}.
 *
 * @param endpoint the storage account's file endpoint, {@code https://<account>.file.core.windows.net}:
 *                 a scheme and a host, nothing else — a SAS travels separately, never in it
 * @param share    the file share's name
 * @param rootPath the directory within the share that holds the workspaces, {@code /}-separated;
 *                 empty for the share's root
 * @param protocol the protocol the share was created for, which decides what its listing carries
 * @param auth     how to sign in
 * @param readOnly whether Microscope refuses every change to the volume; the credential then needs
 *                 only read and list rights
 */
public record AzureFilesAddress(
        String endpoint,
        String share,
        String rootPath,
        AzureFilesProtocol protocol,
        AzureFilesAuth auth,
        boolean readOnly) implements HubAddress {

    private static final String HTTPS = "https";
    private static final String SCHEME_SEPARATOR = "://";
    private static final String PORT_SEPARATOR = ":";
    private static final String SEPARATOR = "/";
    private static final int NO_PORT = -1;
    private static final String IDENTITY_PREFIX = "azure-files:";
    private static final Set<String> RELATIVE_ELEMENTS = Set.of(".", "..");
    private static final Set<String> BARE_PATHS = Set.of("", SEPARATOR);

    /**
     * Never quotes the value it refuses: a portal's "SAS URL" pasted here carries the signature in
     * its query, and this message is logged.
     */
    private static final String ENDPOINT_SHAPE =
            "An Azure Files endpoint is https://<account>.file.core.windows.net with no path, query, fragment or "
                    + "user info; put a SAS token in azure-files.sas-token, never in the endpoint";

    public AzureFilesAddress {
        endpoint = bareEndpoint(endpoint);
        if (share == null || share.isBlank()) {
            throw new IllegalArgumentException("An Azure Files address needs a share name");
        }
        if (protocol == null || auth == null) {
            throw new IllegalArgumentException("An Azure Files address needs the share's protocol and a way to sign in");
        }
        share = share.trim();
        rootPath = normalizedRoot(rootPath);
    }

    /**
     * The share and the directory in it, never the credential nor whether it may be changed: one
     * volume is one source however it is reached.
     */
    @Override
    public String identity() {
        return IDENTITY_PREFIX + location();
    }

    @Override
    public String toString() {
        return location() + " (" + protocol + ", " + auth + (readOnly ? ", read-only" : "") + ")";
    }

    private String location() {
        return endpoint + SEPARATOR + share + (rootPath.isEmpty() ? "" : SEPARATOR + rootPath);
    }

    /** The endpoint as its scheme, host and port alone, the host lower-cased; anything more is refused. */
    private static String bareEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException(ENDPOINT_SHAPE);
        }
        URI uri;
        try {
            uri = new URI(endpoint.trim());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(ENDPOINT_SHAPE);
        }
        boolean bare = HTTPS.equalsIgnoreCase(uri.getScheme())
                && uri.getHost() != null
                && uri.getRawUserInfo() == null
                && uri.getRawQuery() == null
                && uri.getRawFragment() == null
                && BARE_PATHS.contains(uri.getRawPath() == null ? "" : uri.getRawPath());
        if (!bare) {
            throw new IllegalArgumentException(ENDPOINT_SHAPE);
        }
        return HTTPS + SCHEME_SEPARATOR + uri.getHost().toLowerCase(Locale.ROOT)
                + (uri.getPort() == NO_PORT ? "" : PORT_SEPARATOR + uri.getPort());
    }

    /** Plain directory names joined by {@code /}, with no empty, {@code .} or {@code ..} element. */
    private static String normalizedRoot(String rootPath) {
        if (rootPath == null || rootPath.isBlank()) {
            return "";
        }
        String[] elements = Arrays.stream(rootPath.trim().split(SEPARATOR))
                .filter(element -> !element.isEmpty())
                .toArray(String[]::new);
        for (String element : elements) {
            if (RELATIVE_ELEMENTS.contains(element)) {
                throw new IllegalArgumentException("An Azure Files root path cannot walk up: rootPath=" + rootPath);
            }
        }
        return String.join(SEPARATOR, elements);
    }
}

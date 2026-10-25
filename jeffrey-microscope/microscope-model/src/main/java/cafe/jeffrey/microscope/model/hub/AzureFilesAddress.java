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
import java.util.Set;

/**
 * A recording volume on an Azure file share, read over the Azure Files REST API with no hub in
 * between, SMB and NFS shares alike. The share holds the hub's shared volume as it is, so the
 * workspaces lie directly under {@code rootPath}.
 *
 * @param endpoint the storage account's file endpoint, {@code https://<account>.file.core.windows.net}
 * @param share    the file share's name
 * @param rootPath the directory within the share that holds the workspaces, {@code /}-separated;
 *                 empty for the share's root
 * @param protocol the protocol the share was created for, which decides what its listing carries
 * @param auth     how to sign in
 */
public record AzureFilesAddress(
        String endpoint, String share, String rootPath, AzureFilesProtocol protocol, AzureFilesAuth auth)
        implements HubAddress {

    private static final String HTTPS = "https";
    private static final String SEPARATOR = "/";
    private static final String IDENTITY_PREFIX = "azure-files:";
    private static final Set<String> RELATIVE_ELEMENTS = Set.of(".", "..");

    public AzureFilesAddress {
        if (endpoint == null || !isHttpsUrl(endpoint.trim())) {
            throw new IllegalArgumentException("An Azure Files endpoint must be an https URL: endpoint=" + endpoint);
        }
        if (share == null || share.isBlank()) {
            throw new IllegalArgumentException("An Azure Files address needs a share name");
        }
        if (protocol == null || auth == null) {
            throw new IllegalArgumentException("An Azure Files address needs the share's protocol and a way to sign in");
        }
        endpoint = stripTrailingSeparators(endpoint.trim());
        share = share.trim();
        rootPath = normalizedRoot(rootPath);
    }

    /** The share and the directory in it, never the credential: one volume is one source however it is read. */
    @Override
    public String identity() {
        return IDENTITY_PREFIX + endpoint + SEPARATOR + share + (rootPath.isEmpty() ? "" : SEPARATOR + rootPath);
    }

    @Override
    public boolean readOnly() {
        return true;
    }

    @Override
    public String toString() {
        return endpoint + SEPARATOR + share + (rootPath.isEmpty() ? "" : SEPARATOR + rootPath)
                + " (" + protocol + ", " + auth + ")";
    }

    private static boolean isHttpsUrl(String value) {
        try {
            URI uri = new URI(value);
            return HTTPS.equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private static String stripTrailingSeparators(String value) {
        String stripped = value;
        while (stripped.endsWith(SEPARATOR)) {
            stripped = stripped.substring(0, stripped.length() - 1);
        }
        return stripped;
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

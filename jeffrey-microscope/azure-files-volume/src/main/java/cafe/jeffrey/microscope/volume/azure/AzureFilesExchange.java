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

package cafe.jeffrey.microscope.volume.azure;

import com.azure.core.http.HttpHeaderName;
import com.azure.core.http.HttpHeaders;
import com.azure.core.http.HttpMethod;
import com.azure.core.http.HttpRequest;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * One request to the share and what came of it, described without its payload, its headers and
 * its query string — the query carries the SAS signature, a header the bearer token.
 *
 * @param share   the share the request went to
 * @param number  the request's number among every request sent to the share, from 1
 * @param request the request as it was sent
 * @param outcome the response, or the failure that left the request without one
 */
record AzureFilesExchange(String share, long number, Request request, Outcome outcome) {

    public AzureFilesExchange {
        if (share == null || request == null || outcome == null) {
            throw new IllegalArgumentException("An exchange needs its share, request and outcome");
        }
    }

    /**
     * @param method       the HTTP method
     * @param operation    the Azure Files operation, named for a reader of the log
     * @param path         the URL path: the share, then the entry within it; never the query
     * @param requestBytes the request body's declared length
     */
    record Request(String method, String operation, String path, long requestBytes) {

        private static final String DIRECTORY = "directory";
        private static final String NONE = "";
        private static final String RESTYPE = "restype";
        private static final String COMP = "comp";
        private static final String QUERY_PAIRS = "&";
        private static final String QUERY_VALUE = "=";
        private static final String LABEL_SEPARATOR = "-";
        private static final Pattern ENCODED_SEPARATOR = Pattern.compile("%2F", Pattern.CASE_INSENSITIVE);
        private static final String SEPARATOR = "/";

        /** The calls the volume makes, by what tells them apart on the wire. */
        private static final Map<Call, String> OPERATIONS = Map.ofEntries(
                Map.entry(new Call(HttpMethod.GET, DIRECTORY, "list"), "list"),
                Map.entry(new Call(HttpMethod.GET, DIRECTORY, NONE), "directory-properties"),
                Map.entry(new Call(HttpMethod.HEAD, DIRECTORY, NONE), "directory-properties"),
                Map.entry(new Call(HttpMethod.HEAD, NONE, NONE), "file-properties"),
                Map.entry(new Call(HttpMethod.GET, NONE, NONE), "download"),
                Map.entry(new Call(HttpMethod.PUT, DIRECTORY, NONE), "create-directory"),
                Map.entry(new Call(HttpMethod.PUT, NONE, NONE), "create-file"),
                Map.entry(new Call(HttpMethod.PUT, NONE, "range"), "put-range"),
                Map.entry(new Call(HttpMethod.PUT, NONE, "rename"), "rename"),
                Map.entry(new Call(HttpMethod.DELETE, DIRECTORY, NONE), "delete-directory"),
                Map.entry(new Call(HttpMethod.DELETE, NONE, NONE), "delete-file"));

        /** Read before the request is sent: a later policy signs the same request object. */
        static Request of(HttpRequest request) {
            return new Request(
                    request.getHttpMethod().toString(),
                    Call.of(request).operation(),
                    readablePath(request.getUrl().getPath()),
                    contentLength(request.getHeaders()));
        }

        /**
         * The path with its separators as a reader expects them: the SDK encodes those within the
         * share. Nothing else is decoded, so a name keeps its {@code +} and its escapes.
         */
        private static String readablePath(String encoded) {
            return ENCODED_SEPARATOR.matcher(encoded).replaceAll(SEPARATOR);
        }

        private record Call(HttpMethod method, String restype, String comp) {

            static Call of(HttpRequest request) {
                String restype = NONE;
                String comp = NONE;
                String query = request.getUrl().getQuery();
                if (query != null) {
                    for (String pair : query.split(QUERY_PAIRS)) {
                        String[] parts = pair.split(QUERY_VALUE, 2);
                        String value = parts.length > 1 ? parts[1] : NONE;
                        if (RESTYPE.equals(parts[0])) {
                            restype = value;
                        } else if (COMP.equals(parts[0])) {
                            comp = value;
                        }
                    }
                }
                return new Call(request.getHttpMethod(), restype, comp);
            }

            /** A call the volume is not known to make is named by its method and parameters alone. */
            String operation() {
                String known = OPERATIONS.get(this);
                if (known != null) {
                    return known;
                }
                StringBuilder label = new StringBuilder(method.toString().toLowerCase(Locale.ROOT));
                for (String part : new String[]{restype, comp}) {
                    if (!part.isEmpty()) {
                        label.append(LABEL_SEPARATOR).append(part);
                    }
                }
                return label.toString();
            }
        }
    }

    /** What happened to a request once it was sent. */
    sealed interface Outcome {

        /** From sending the request to its response headers, or to its failure. */
        Duration elapsed();
    }

    /**
     * @param status        the HTTP status the service answered
     * @param responseBytes the response body's declared length; the body itself is never read here
     */
    record Answered(int status, long responseBytes, Duration elapsed) implements Outcome {
    }

    /** No response at all: the connection failed, timed out or was refused before the service answered. */
    record Failed(Throwable error, Duration elapsed) implements Outcome {

        /** The innermost cause: a transport's failure arrives wrapped, once or more. */
        Throwable rootCause() {
            Throwable cause = error;
            while (cause.getCause() != null && cause.getCause() != cause) {
                cause = cause.getCause();
            }
            return cause;
        }
    }

    /** The declared length of a body, zero when there is none or it cannot be read. */
    static long contentLength(HttpHeaders headers) {
        String value = headers.getValue(HttpHeaderName.CONTENT_LENGTH);
        if (value == null) {
            return 0;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}

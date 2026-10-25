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

import cafe.jeffrey.microscope.model.hub.AzureFilesProtocol;
import com.azure.core.http.HttpClient;
import com.azure.core.http.HttpHeaders;
import com.azure.core.http.HttpMethod;
import com.azure.core.http.HttpRequest;
import com.azure.core.http.HttpResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An Azure file share in memory, answering the Azure Files REST calls the SDK makes — listing,
 * file and directory properties, ranged downloads — in the shapes the service documents for each
 * protocol. Plugged into the SDK as its HTTP transport, so every request the adapter makes goes
 * through the SDK's own serialisation, retries and error handling.
 */
final class StubFileShare implements HttpClient {

    private static final String SHARE = "recordings";
    private static final String SEPARATOR = "/";
    private static final Pattern RANGE = Pattern.compile("bytes=(\\d+)-(\\d*)");
    private static final DateTimeFormatter RFC_1123 = DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC);

    private final AzureFilesProtocol protocol;
    private final Map<String, Node> nodes = new TreeMap<>();
    private final List<HttpRequest> requests = new CopyOnWriteArrayList<>();
    private boolean rejectEveryCredential;

    private record Node(boolean directory, byte[] content, Instant createdAt, Instant lastModified) {
    }

    StubFileShare(AzureFilesProtocol protocol) {
        this.protocol = protocol;
    }

    static String shareName() {
        return SHARE;
    }

    StubFileShare directory(String path, Instant createdAt) {
        nodes.put(path, new Node(true, new byte[0], createdAt, createdAt.plusSeconds(1)));
        return this;
    }

    /** A file, its parent directories created along the way. */
    StubFileShare file(String path, String content, Instant createdAt) {
        String[] elements = path.split(SEPARATOR);
        StringBuilder parent = new StringBuilder();
        for (int i = 0; i < elements.length - 1; i++) {
            parent.append(i == 0 ? "" : SEPARATOR).append(elements[i]);
            nodes.putIfAbsent(parent.toString(), new Node(true, new byte[0], createdAt, createdAt));
        }
        nodes.put(path, new Node(false, content.getBytes(StandardCharsets.UTF_8), createdAt, createdAt.plusSeconds(30)));
        return this;
    }

    /** From now on every request is refused as a rejected credential is. */
    void rejectEveryCredential() {
        rejectEveryCredential = true;
    }

    List<HttpRequest> requests() {
        return requests;
    }

    @Override
    public Mono<HttpResponse> send(HttpRequest request) {
        requests.add(request);
        return Mono.fromCallable(() -> answer(request));
    }

    private HttpResponse answer(HttpRequest request) {
        if (rejectEveryCredential) {
            return error(request, 403, "AuthenticationFailed");
        }
        String rawPath = request.getUrl().getPath();
        String prefix = SEPARATOR + SHARE;
        if (!rawPath.startsWith(prefix)) {
            return error(request, 404, "ShareNotFound");
        }
        String path = URLDecoder.decode(rawPath.substring(prefix.length()), StandardCharsets.UTF_8);
        path = path.startsWith(SEPARATOR) ? path.substring(1) : path;
        Map<String, String> query = query(request.getUrl().getQuery());
        boolean directoryCall = "directory".equals(query.get("restype"));

        if (directoryCall && "list".equals(query.get("comp"))) {
            return list(request, path, query);
        }
        if (directoryCall) {
            // Get Directory Properties, which the SDK sends as a GET
            return properties(request, path, true);
        }
        if (request.getHttpMethod() == HttpMethod.HEAD) {
            return properties(request, path, false);
        }
        if (request.getHttpMethod() == HttpMethod.GET) {
            return download(request, path);
        }
        return error(request, 405, "UnsupportedHttpVerb");
    }

    private HttpResponse list(HttpRequest request, String path, Map<String, String> query) {
        boolean asksForTimestamps = query.containsKey("include");
        if (protocol == AzureFilesProtocol.NFS && asksForTimestamps) {
            // The include parameter is SMB-only
            return error(request, 400, "InvalidQueryParameterValue");
        }
        Node directory = path.isEmpty() ? new Node(true, null, Instant.EPOCH, Instant.EPOCH) : nodes.get(path);
        if (directory == null) {
            return error(request, 404, "ResourceNotFound");
        }
        if (!directory.directory()) {
            return error(request, 409, "ResourceTypeMismatch");
        }
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
                .append("<EnumerationResults ServiceEndpoint=\"https://acct.file.core.windows.net/\" ShareName=\"")
                .append(SHARE).append("\" DirectoryPath=\"").append(path).append("\"><Entries>");
        for (Map.Entry<String, Node> entry : children(path).entrySet()) {
            Node node = entry.getValue();
            String tag = node.directory() ? "Directory" : "File";
            xml.append('<').append(tag).append("><Name>").append(entry.getKey()).append("</Name><Properties>");
            if (!node.directory()) {
                xml.append("<Content-Length>").append(node.content().length).append("</Content-Length>");
            }
            if (asksForTimestamps) {
                xml.append("<CreationTime>").append(node.createdAt()).append("</CreationTime>")
                        .append("<LastWriteTime>").append(node.lastModified()).append("</LastWriteTime>")
                        .append("<Last-Modified>").append(RFC_1123.format(node.lastModified())).append("</Last-Modified>");
            }
            xml.append("</Properties></").append(tag).append('>');
        }
        xml.append("</Entries><NextMarker /></EnumerationResults>");
        return response(request, 200, xml.toString().getBytes(StandardCharsets.UTF_8), Map.of("Content-Type", "application/xml"));
    }

    private HttpResponse properties(HttpRequest request, String path, boolean directory) {
        Node node = path.isEmpty() ? new Node(true, null, Instant.EPOCH, Instant.EPOCH) : nodes.get(path);
        if (node == null || node.directory() != directory) {
            return error(request, 404, "ResourceNotFound");
        }
        Map<String, String> headers = new HashMap<>(timestamps(node));
        if (!directory) {
            headers.put("Content-Length", Integer.toString(node.content().length));
            headers.put("x-ms-type", "File");
        }
        return response(request, 200, new byte[0], headers);
    }

    private HttpResponse download(HttpRequest request, String path) {
        Node node = nodes.get(path);
        if (node == null) {
            return error(request, 404, "ResourceNotFound");
        }
        if (node.directory()) {
            return error(request, 409, "ResourceTypeMismatch");
        }
        byte[] content = node.content();
        String range = request.getHeaders().getValue("x-ms-range");
        range = range != null ? range : request.getHeaders().getValue("Range");
        Map<String, String> headers = new HashMap<>(timestamps(node));
        headers.put("x-ms-type", "File");
        headers.put("x-ms-content-length", Integer.toString(content.length));
        Matcher matcher = range == null ? null : RANGE.matcher(range);
        if (matcher == null || !matcher.matches() || content.length == 0) {
            headers.put("Content-Length", Integer.toString(content.length));
            return response(request, 200, content, headers);
        }
        int from = Integer.parseInt(matcher.group(1));
        int to = matcher.group(2).isEmpty() ? content.length - 1 : Math.min(Integer.parseInt(matcher.group(2)), content.length - 1);
        byte[] part = Arrays.copyOfRange(content, from, to + 1);
        headers.put("Content-Length", Integer.toString(part.length));
        headers.put("Content-Range", "bytes %d-%d/%d".formatted(from, to, content.length));
        return response(request, 206, part, headers);
    }

    /** What the service reports of an entry: SMB keeps a creation time, NFS only the last change. */
    private Map<String, String> timestamps(Node node) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Last-Modified", RFC_1123.format(node.lastModified()));
        headers.put("ETag", "\"0x" + Long.toHexString(node.lastModified().toEpochMilli()) + "\"");
        // Every entry of a share is encrypted at rest, and the service says so on every property read
        headers.put("x-ms-server-encrypted", "true");
        if (protocol == AzureFilesProtocol.SMB) {
            headers.put("x-ms-file-creation-time", node.createdAt().toString());
            headers.put("x-ms-file-last-write-time", node.lastModified().toString());
            headers.put("x-ms-file-change-time", node.lastModified().toString());
        }
        return headers;
    }

    /** The direct children of {@code path}, by name. */
    private Map<String, Node> children(String path) {
        Map<String, Node> children = new TreeMap<>();
        String prefix = path.isEmpty() ? "" : path + SEPARATOR;
        for (Map.Entry<String, Node> entry : nodes.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(prefix) && !key.substring(prefix.length()).contains(SEPARATOR) && !key.equals(path)) {
                children.put(key.substring(prefix.length()), entry.getValue());
            }
        }
        return children;
    }

    private static Map<String, String> query(String rawQuery) {
        Map<String, String> query = new HashMap<>();
        if (rawQuery == null) {
            return query;
        }
        for (String pair : rawQuery.split("&")) {
            String[] kv = pair.split("=", 2);
            query.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                    kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
        }
        return query;
    }

    private static HttpResponse error(HttpRequest request, int status, String code) {
        byte[] body = ("<?xml version=\"1.0\" encoding=\"utf-8\"?><Error><Code>" + code
                + "</Code><Message>stub refusal</Message></Error>").getBytes(StandardCharsets.UTF_8);
        return response(request, status, body, Map.of("x-ms-error-code", code, "Content-Type", "application/xml"));
    }

    private static HttpResponse response(HttpRequest request, int status, byte[] body, Map<String, String> headers) {
        HttpHeaders httpHeaders = new HttpHeaders();
        headers.forEach(httpHeaders::set);
        httpHeaders.set("x-ms-request-id", "stub");
        httpHeaders.set("x-ms-version", "2026-06-06");
        if (httpHeaders.getValue("Content-Length") == null) {
            httpHeaders.set("Content-Length", Integer.toString(body.length));
        }
        return new StubResponse(request, status, httpHeaders, body);
    }

    private static final class StubResponse extends HttpResponse {

        private final int status;
        private final HttpHeaders headers;
        private final byte[] body;

        StubResponse(HttpRequest request, int status, HttpHeaders headers, byte[] body) {
            super(request);
            this.status = status;
            this.headers = headers;
            this.body = body;
        }

        @Override
        public int getStatusCode() {
            return status;
        }

        @Override
        @SuppressWarnings("deprecation")
        public String getHeaderValue(String name) {
            return headers.getValue(name);
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }

        @Override
        public Flux<ByteBuffer> getBody() {
            return Flux.just(ByteBuffer.wrap(body));
        }

        @Override
        public Mono<byte[]> getBodyAsByteArray() {
            return Mono.just(body);
        }

        @Override
        public Mono<String> getBodyAsString() {
            return Mono.just(new String(body, StandardCharsets.UTF_8));
        }

        @Override
        public Mono<String> getBodyAsString(Charset charset) {
            return Mono.just(new String(body, charset));
        }
    }
}

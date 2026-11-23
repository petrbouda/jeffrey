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

package cafe.jeffrey.microscope.mcp.server;

/**
 * Where a long-running tool reports its progress while it runs, carried on the {@link McpCallContext}.
 * <p>
 * The modern transport streams a tool's progress to the client as {@code notifications/progress} over
 * SSE. A tool runs on the calling thread and pushes progress here as its phases advance; the server
 * bridges each call to the transport's notifier. A tool that does not report, and every call that is not
 * served over a streaming response, gets {@link #NONE}, which drops what it is handed — so a tool may
 * always call {@link #progress} without checking whether anyone is listening.
 */
@FunctionalInterface
public interface McpProgressSink {

    /** Drops every progress report: for fast tools and for any call not served over a stream. */
    McpProgressSink NONE = (progress, total, message) -> {
    };

    /**
     * @param progress how far the work has got, in the same unit as {@code total}
     * @param total    the amount of work, or a non-positive value when it is not yet known
     * @param message  a short human-readable note on the current phase
     */
    void progress(double progress, double total, String message);
}

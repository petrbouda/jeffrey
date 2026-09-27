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

package cafe.jeffrey.microscope.mcp.protocol;

/**
 * How the exceptions of the server that adapts this protocol read to it.
 * <p>
 * The protocol knows its own failures: an argument a tool refused ({@link IllegalArgumentException}), a
 * refusal a tool wrote for the model ({@link ToolExecutionException}), a resource that is not there
 * ({@link McpResourceNotFoundException}). A server has exceptions of its own — a subject it names as
 * missing, a condition it names as the caller's mistake — and a wrapper it may put around what a tool
 * threw. This is where it says so, so that the words a client is given, and the code a failed resource
 * read carries, are decided the same way for both.
 * <p>
 * Everything the policy does not recognise is internal: the client is told
 * {@link #internalFailureMessage()}, and the detail goes only to the server log.
 */
@FunctionalInterface
public interface McpFailurePolicy {

    /** What a server's own exception means to the caller. */
    enum Kind {

        /** A subject the caller named does not exist: a resource read answers {@code -32602}, naming it. */
        NOT_FOUND,

        /** The caller's mistake, in a sentence it can act on. */
        CALLER_ERROR,

        /** Not one of the server's caller-facing exceptions: its words stay in the server log. */
        UNRECOGNISED
    }

    /** What a client is told for a failure it can do nothing about. */
    String internalFailureMessage();

    /**
     * The exception a tool threw, when the failure is a wrapper the server puts around it; the failure
     * itself otherwise.
     */
    default Throwable unwrap(Throwable failure) {
        return failure;
    }

    /** What one of the server's own exceptions means to the caller; {@link Kind#UNRECOGNISED} for any other. */
    default Kind classify(Throwable failure) {
        return Kind.UNRECOGNISED;
    }
}

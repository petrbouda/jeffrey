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

package cafe.jeffrey.profile.mcp;

import cafe.jeffrey.microscope.mcp.protocol.McpFailurePolicy;
import cafe.jeffrey.shared.common.exception.JeffreyException;

/**
 * How Jeffrey's exceptions read to the protocol.
 * <p>
 * A tool's exception arrives inside the {@link ToolInvocationException} that {@link ToolInvocation}
 * puts around it, and is judged by what the tool threw. A {@link JeffreyException} is the caller's to act
 * on only when Jeffrey names it a client error — a profile that does not exist, a feature this recording
 * did not enable — and a missing subject when its code says so. A {@code JeffreyException} carrying a
 * code is not enough on its own: an internal one carries a code too, and the paths that reach here with
 * one name host file paths.
 *
 * @param internalFailureMessage what a client is told for a failure it can do nothing about
 */
record JeffreyFailurePolicy(String internalFailureMessage) implements McpFailurePolicy {

    JeffreyFailurePolicy {
        if (internalFailureMessage == null || internalFailureMessage.isBlank()) {
            throw new IllegalArgumentException("A failure policy needs the sentence an internal failure gets");
        }
    }

    @Override
    public Throwable unwrap(Throwable failure) {
        if (failure instanceof ToolInvocationException invocation) {
            return invocation.getCause();
        }
        return failure;
    }

    @Override
    public Kind classify(Throwable failure) {
        if (!(failure instanceof JeffreyException jeffrey) || !jeffrey.isClientError()) {
            return Kind.UNRECOGNISED;
        }
        return jeffrey.getCode() != null && jeffrey.getCode().isNotFound() ? Kind.NOT_FOUND : Kind.CALLER_ERROR;
    }
}

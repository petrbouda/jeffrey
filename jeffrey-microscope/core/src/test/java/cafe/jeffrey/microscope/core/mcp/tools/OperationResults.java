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

package cafe.jeffrey.microscope.core.mcp.tools;

/**
 * Results a test registers an operation with. The registry takes a record, which is what makes an
 * operation's result an object on the wire; these are the shapes the tests read back.
 */
public final class OperationResults {

    private OperationResults() {
    }

    public record Value(Object value) {
    }

    public record Recording(Object recordingId) {
    }

    public record Profile(Object profileId) {
    }

    public record Rendered(Object rendered) {
    }
}

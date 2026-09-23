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

package cafe.jeffrey.profile.model;

/**
 * Presentation-role flags a panel carries so the frontend can apply route-based show/hide without
 * inspecting the event code. The frontend hides {@code method} panels when method-tracing is suppressed,
 * and {@code nativeMemory}/{@code blocking} panels outside the primary flamegraph route.
 */
public record Classification(boolean method, boolean nativeMemory, boolean blocking) {

    public static final Classification PLAIN = new Classification(false, false, false);
}

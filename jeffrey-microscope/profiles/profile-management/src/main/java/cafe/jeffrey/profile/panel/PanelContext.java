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

package cafe.jeffrey.profile.panel;

/**
 * Read-time context for building panels. Primary vs differential is the only axis that changes a panel's
 * option defaults (differential drops the method-trace weight toggle and any primary-only thread-mode),
 * and it is known from which controller/endpoint served the request.
 *
 * @param primary true for the primary flamegraph, false for a differential (secondary) comparison
 */
public record PanelContext(boolean primary) {

    public static final PanelContext PRIMARY = new PanelContext(true);
    public static final PanelContext DIFFERENTIAL = new PanelContext(false);
}

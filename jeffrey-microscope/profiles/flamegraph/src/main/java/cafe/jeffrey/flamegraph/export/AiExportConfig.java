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

package cafe.jeffrey.flamegraph.export;

public record AiExportConfig(double minFrameThresholdPct) {

    public AiExportConfig {
        if (!(minFrameThresholdPct > 0.0 && minFrameThresholdPct < 100.0)) {
            throw new IllegalArgumentException(
                    "minFrameThresholdPct must be in (0, 100): " + minFrameThresholdPct);
        }
    }
}

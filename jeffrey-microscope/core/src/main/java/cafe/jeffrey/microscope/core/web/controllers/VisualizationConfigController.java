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

package cafe.jeffrey.microscope.core.web.controllers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the visualization defaults the frontend needs but cannot read for itself.
 * <p>
 * The frame text mode decides how a flamegraph labels its frames, and the flamegraph is drawn in the
 * browser, so the value has to travel there. It is a static application property like every other one
 * — read once at startup, changed by editing {@code application.properties} and restarting — and the
 * per-flamegraph toggle in the UI still overrides it for the graph in front of the reader.
 */
@RestController
@RequestMapping("/api/internal/config/visualization")
public class VisualizationConfigController {

    private static final String FRAME_TEXT_MODE_PROPERTY =
            "${jeffrey.microscope.visualization.flamegraph.frame-text-mode:single-line}";

    private final String frameTextMode;

    public VisualizationConfigController(@Value(FRAME_TEXT_MODE_PROPERTY) String frameTextMode) {
        this.frameTextMode = frameTextMode;
    }

    @GetMapping
    public VisualizationConfigResponse get() {
        return new VisualizationConfigResponse(frameTextMode);
    }

    public record VisualizationConfigResponse(String frameTextMode) {
    }
}

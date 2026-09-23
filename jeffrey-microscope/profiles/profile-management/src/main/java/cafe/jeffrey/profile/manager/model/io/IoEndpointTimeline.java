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

package cafe.jeffrey.profile.manager.model.io;

import cafe.jeffrey.timeseries.SingleSerie;

/**
 * One endpoint's aggregated I/O paired with its shape over the recording, so a gallery of peers can
 * be rendered as sparkline tiles from a single request.
 *
 * @param endpoint totals for the endpoint (bytes, op count, total/max duration)
 * @param serie    per second, read and written combined, zero-filled across the recording — bytes
 *                 or operations depending on the {@link IoMetric} the gallery was built for. The
 *                 serie carries its own name, so the unit travels with the data.
 */
public record IoEndpointTimeline(IoEndpoint endpoint, SingleSerie serie) {
}

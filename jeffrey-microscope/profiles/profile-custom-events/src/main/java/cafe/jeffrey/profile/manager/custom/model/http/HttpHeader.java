/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.profile.manager.custom.model.http;

import java.math.BigDecimal;

public record HttpHeader(
        long requestCount,
        long maxResponseTime,
        long p99ResponseTime,
        long p95ResponseTime,
        BigDecimal successRate,
        long count5xx,
        long count4xx,
        long totalBytesTransferred,
        long totalBytesReceived,
        long totalBytesSent) {
}

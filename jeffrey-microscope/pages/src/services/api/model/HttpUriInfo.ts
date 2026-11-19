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

export default class HttpUriInfo {
  constructor(
    public uri: string,
    public requestCount: number,
    public maxResponseTime: number,
    public p99ResponseTime: number,
    public p95ResponseTime: number,
    public successRate: number,
    public count4xx: number,
    public count5xx: number,
    public totalBytesTransferred: number,
    public totalBytesReceived: number,
    public totalBytesSent: number
  ) {}
}

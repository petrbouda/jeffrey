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

/** One request started through a {@link LatestRequest}. */
export interface RequestTicket {
  /** Whether no later request of the same kind has started, nor a reset dropped this one. */
  isLatest(): boolean;
  /** Aborted once the request is no longer the latest: its answer would be dropped anyway. */
  readonly signal: AbortSignal;
}

/**
 * Orders the requests of one kind — the flamegraph of a zoom, the timeseries of a search — so that
 * only the answer to the one started last is applied. Answers arrive in any order: brushing across
 * the timeseries fires a zoom per move, and the slowest one must not win by arriving last. A request
 * that is overtaken is aborted too, so the browser stops waiting for an answer nobody will use.
 */
export default class LatestRequest {
  private latest = 0;
  private controller: AbortController | null = null;

  start(): RequestTicket {
    this.controller?.abort();
    const controller = new AbortController();
    this.controller = controller;
    const id = ++this.latest;
    return {
      isLatest: () => id === this.latest,
      signal: controller.signal
    };
  }

  /**
   * Drops the request in flight, if any, without starting a new one: a reset that is applied at once
   * must not be undone by a late answer.
   */
  invalidate(): void {
    this.latest++;
    this.controller?.abort();
    this.controller = null;
  }
}

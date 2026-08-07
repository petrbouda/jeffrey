/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * Where opening a profile lands, as one decision instead of a hardcoded path at every entry point.
 *
 * The router's `/profiles/:profileId` redirect only fires for the bare path, so anything that
 * navigates to a sub-path bypasses it. Keeping the choice here means a future change to the landing
 * page is made once rather than hunted for across views, stores and composables.
 */

export const HEAP_DUMP_SOURCE = 'HEAP_DUMP';
export const PPROF_SOURCE = 'PPROF';
export const OTEL_SOURCE = 'OPEN_TELEMETRY';

/** The default landing for a profile carrying the full JFR event set. */
const DASHBOARD_SUB_PATH = 'dashboard';

/**
 * Event sources that carry stack samples only. They hold none of the GC, safepoint or thread data
 * the Summary dashboard is built from, so they land on the flamegraph rather than on a dashboard
 * with nothing to show. A source that is absent from this map lands on the dashboard.
 */
const LANDING_SUB_PATHS: ReadonlyMap<string, string> = new Map([
  [HEAP_DUMP_SOURCE, 'heap-dump/settings'],
  [PPROF_SOURCE, 'flamegraphs/primary'],
  [OTEL_SOURCE, 'flamegraphs/primary']
]);

/**
 * The route to open a profile at.
 *
 * `eventSource` is optional because several entry points — Quick Open, the recordings assistant's
 * openProfile, navigateToProfile — hold only a profile id. Those fall back to the dashboard, which
 * is right for the common case and never lands on Configuration.
 */
export function profileLandingRoute(profileId: string, eventSource?: string | null): string {
  const subPath =
    (eventSource ? LANDING_SUB_PATHS.get(eventSource) : undefined) ?? DASHBOARD_SUB_PATH;
  return `/profiles/${profileId}/${subPath}`;
}

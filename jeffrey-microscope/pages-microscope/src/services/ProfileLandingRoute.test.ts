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

import { describe, expect, it } from 'vitest';
import {
  HEAP_DUMP_SOURCE,
  OTEL_SOURCE,
  PPROF_SOURCE,
  profileLandingRoute
} from '@/services/ProfileLandingRoute';

const PROFILE_ID = 'abc123';

describe('profileLandingRoute', () => {
  // The regression this guards: a JFR profile used to land on /overview, which since the Summary
  // dashboard shipped renders the Configuration page instead of the dashboard.
  it('lands a JFR profile on the Summary dashboard', () => {
    expect(profileLandingRoute(PROFILE_ID, 'JDK')).toBe('/profiles/abc123/dashboard');
  });

  // Stack-sample-only profiles carry none of the GC/safepoint/thread data the dashboard is built
  // from, so they go straight to the flamegraph instead of to an empty dashboard.
  it('lands stack-sample-only profiles on the flamegraph', () => {
    expect(profileLandingRoute(PROFILE_ID, PPROF_SOURCE)).toBe(
      '/profiles/abc123/flamegraphs/primary'
    );
    expect(profileLandingRoute(PROFILE_ID, OTEL_SOURCE)).toBe(
      '/profiles/abc123/flamegraphs/primary'
    );
  });

  it('lands a heap dump on its settings page', () => {
    expect(profileLandingRoute(PROFILE_ID, HEAP_DUMP_SOURCE)).toBe(
      '/profiles/abc123/heap-dump/settings'
    );
  });

  // Several entry points (Quick Open, the assistant's openProfile, navigateToProfile) hold only a
  // profile id. They must still reach the dashboard rather than falling back to Configuration.
  it('falls back to the dashboard when the event source is unknown', () => {
    expect(profileLandingRoute(PROFILE_ID)).toBe('/profiles/abc123/dashboard');
    expect(profileLandingRoute(PROFILE_ID, null)).toBe('/profiles/abc123/dashboard');
    expect(profileLandingRoute(PROFILE_ID, 'SOMETHING_NEW')).toBe('/profiles/abc123/dashboard');
  });

  it('keeps the profile id it was given', () => {
    expect(profileLandingRoute('019fdacc-e52f-70d9')).toBe(
      '/profiles/019fdacc-e52f-70d9/dashboard'
    );
  });
});

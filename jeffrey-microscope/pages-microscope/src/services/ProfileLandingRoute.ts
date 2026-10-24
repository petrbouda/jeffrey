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

import { profileKindOf } from '@/services/ProfileKind';

/**
 * Where opening a profile lands, as one decision instead of a hardcoded path at every entry point.
 *
 * The router's `/profiles/:profileId` redirect only fires for the bare path, so anything that
 * navigates to a sub-path bypasses it. The landing page of each kind lives in `ProfileKind`, so a
 * change to it is made once rather than hunted for across views, stores and composables.
 */

/**
 * The route to open a profile at.
 *
 * `eventSource` is optional because several entry points — Quick Open, the recordings assistant's
 * openProfile, navigateToProfile — hold only a profile id. Those fall back to the dashboard, which
 * is right for the common case and never lands on Configuration.
 */
export function profileLandingRoute(profileId: string, eventSource?: string | null): string {
  return `/profiles/${profileId}/${profileKindOf(eventSource).landing}`;
}

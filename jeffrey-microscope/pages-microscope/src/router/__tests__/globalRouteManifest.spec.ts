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

import { describe, expect, it } from 'vitest';
import { globalChildRoutes } from '@/router/globalRoutes';

// Every top-level page of the application shell (the children of '/', rendered in Index.vue) that a
// link can land on. Only this level counts: a profile's pages are in profile-routes.json, and the
// workspace pages under /hubs/:hubId/workspaces/... are reached from the hub browser, not linked to.
function landablePaths(): string[] {
  return (globalChildRoutes as readonly { path: string; redirect?: unknown }[])
    .filter(route => route.redirect === undefined && route.path.length > 0)
    .map(route => route.path)
    .sort();
}

describe('global route manifest', () => {
  /**
   * The manifest is the contract between this build and the MCP server, whose answers carry a
   * uiLink to pages such as the recordings list and whose tests cannot read the router. A page
   * renamed here without the manifest following would turn those links into the catch-all.
   *
   * Run `npx vitest run -u` after changing a top-level route to rewrite it.
   */
  it('matches the committed manifest the MCP server links against', async () => {
    await expect(`${JSON.stringify(landablePaths(), null, 2)}\n`).toMatchFileSnapshot(
      '../global-routes.json'
    );
  });
});

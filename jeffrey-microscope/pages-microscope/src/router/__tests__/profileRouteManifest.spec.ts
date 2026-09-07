import { describe, expect, it } from 'vitest';
import { profileChildRoutes } from '@/router/profileChildRoutes';

// Every path under /profiles/:profileId that a link can actually land on. Redirect-only entries are
// left out: they resolve, but they are not somewhere a view lives, and a caller pointing at one is
// relying on a hop that may be removed.
function landablePaths(): string[] {
  const paths: string[] = [];

  const walk = (prefix: string, routes: readonly unknown[]): void => {
    for (const entry of routes) {
      const route = entry as { path?: string; redirect?: unknown; children?: readonly unknown[] };
      const path = [prefix, route.path ?? ''].filter(part => part.length > 0).join('/');
      if (route.redirect === undefined && path.length > 0) {
        paths.push(path);
      }
      if (route.children !== undefined) {
        walk(path, route.children);
      }
    }
  };

  walk('', profileChildRoutes);
  return [...new Set(paths)].sort();
}

describe('profile route manifest', () => {
  /**
   * The manifest is the contract between this build and the IntelliJ plugin, which links to these
   * paths from its recording panel and cannot see this file at compile time. The two builds had no
   * way to disagree out loud, and one tile spent a release pointing at a path the router does not
   * serve, landing the reader on the recordings list with no error.
   *
   * Run `npx vitest run -u` after changing a route to rewrite it.
   */
  it('matches the committed manifest the IntelliJ plugin reads', async () => {
    await expect(`${JSON.stringify(landablePaths(), null, 2)}\n`).toMatchFileSnapshot(
      '../profile-routes.json'
    );
  });
});

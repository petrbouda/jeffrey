import { describe, expect, it, vi } from 'vitest';

// Keep the actual route matcher; only replace browser history and the eagerly loaded layout.
vi.mock('vue-router', async importOriginal => {
  const router = await importOriginal<typeof import('vue-router')>();
  return { ...router, createWebHistory: router.createMemoryHistory };
});
vi.mock('@/layout/AppLayout.vue', () => ({ default: {} }));

import router from './index';

const projectRoot = '/hubs/hub-1/workspaces/workspace-1/projects/project-1';

describe('project streaming navigation', () => {
  it('resolves a replay deep link with its project and selected session intact', () => {
    const route = router.resolve(
      `${projectRoot}/events/replay-stream?sessionId=session-1&sessionInstance=worker+one`
    );

    expect(route.name).toBe('project-replay-stream');
    expect(route.params).toEqual({
      hubId: 'hub-1',
      workspaceId: 'workspace-1',
      projectId: 'project-1'
    });
    expect(route.query).toEqual({ sessionId: 'session-1', sessionInstance: 'worker one' });
    expect(route.meta.layout).toBe('project');
  });

  it('handles an obsolete live bookmark through the normal unknown-page redirect', () => {
    const route = router.resolve(`${projectRoot}/events/live-stream?sessionId=session-1`);

    expect(route.matched.at(-1)?.redirect).toBe('/');
  });
});

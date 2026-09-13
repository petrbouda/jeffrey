import { describe, expect, it, vi } from 'vitest';
import { useNavigation } from './useNavigation';

vi.mock('vue-router', () => ({
  useRoute: () => ({
    params: { hubId: 'hub-1', workspaceId: 'workspace-1', projectId: 'project-1' },
    path: '/hubs/hub-1/workspaces/workspace-1/projects/project-1/instances/timeline'
  }),
  useRouter: () => ({ push: vi.fn() })
}));

describe('replay links from a session timeline', () => {
  it('opens replay in the current project without selecting a session when none was supplied', () => {
    const { generateReplayStreamUrl } = useNavigation();

    expect(generateReplayStreamUrl()).toBe(
      '/hubs/hub-1/workspaces/workspace-1/projects/project-1/events/replay-stream'
    );
  });

  it('preserves selected session and instance names containing query delimiters', () => {
    const { generateReplayStreamUrl } = useNavigation();
    const url = new URL(
      generateReplayStreamUrl('session&1', 'worker + batch/2'),
      'http://localhost'
    );

    expect(url.searchParams.get('sessionId')).toBe('session&1');
    expect(url.searchParams.get('sessionInstance')).toBe('worker + batch/2');
    expect([...url.searchParams]).toHaveLength(2);
  });

  it('uses explicit project context when opening replay outside the current project', () => {
    const { generateReplayStreamUrl } = useNavigation();
    const url = new URL(
      generateReplayStreamUrl('session-2', undefined, 'hub-2', 'project-2', 'workspace-2'),
      'http://localhost'
    );

    expect(url.pathname).toBe(
      '/hubs/hub-2/workspaces/workspace-2/projects/project-2/events/replay-stream'
    );
    expect([...url.searchParams]).toEqual([['sessionId', 'session-2']]);
  });
});

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

import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/vue';
import userEvent from '@testing-library/user-event';
import { createMemoryHistory, createRouter, RouterView } from 'vue-router';
import AgentPluginView from './AgentPluginView.vue';
import { usePluginUsage } from '@/composables/usePluginUsage';

const model = usePluginUsage();

// Exercise the real page, router, recorded data and SVG; no component or model doubles.
async function openPage(path: string) {
  // jsdom has no scrolling implementation; navigation still uses the real router.
  vi.spyOn(window, 'scrollTo').mockImplementation(() => {});
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/agent-plugin', component: AgentPluginView }]
  });
  await router.push(path);
  await router.isReady();
  const view = render(RouterView, { global: { plugins: [router] } });
  return { router, ...view };
}

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

async function openProfilesTool() {
  const user = userEvent.setup();
  await user.click(screen.getByRole('tab', { name: /^Tools/ }));
  await user.click(screen.getByText('profiles_list', { selector: 'td' }));
  return user;
}

describe('Agent Plugin navigation', () => {
  it('opens a replay from a tool card without an area switch, and preserves the linked run on reload', async () => {
    const page = await openPage('/agent-plugin?area=jfr');
    const user = await openProfilesTool();
    await user.click(screen.getByRole('button', { name: /^orders-by-coupon-in-sql/ }));
    expect(screen.getByRole('heading', { name: 'orders-by-coupon-in-sql' })).toBeTruthy();
    expect(screen.queryByRole('radiogroup', { name: 'Area of the plugin' })).toBeNull();
    await user.click(screen.getByRole('tab', { name: /^Run 2/ }));
    await waitFor(() => {
      expect(page.router.currentRoute.value.query.run).toBe('2');
    });
    const linked = page.router.currentRoute.value.fullPath;
    page.unmount();
    await openPage(linked);
    expect(screen.getByRole('heading', { name: 'orders-by-coupon-in-sql' })).toBeTruthy();
    expect(screen.getByRole('tab', { name: /^Run 2/ }).getAttribute('aria-selected')).toBe('true');
  });

  it('reveals a cross-area skill selected from a shared tool', async () => {
    await openPage('/agent-plugin?area=jfr');
    const user = await openProfilesTool();
    const group = screen.getByRole('heading', { name: 'Called while active' }).parentElement!;
    await user.click(within(group).getByRole('button', { name: /^heap-sql/ }));
    expect(screen.getByRole('radio', { name: /^Heap dumps/ }).getAttribute('aria-checked')).toBe('true');
    expect(screen.getByRole('button', { name: /^skill heap-sql,/ }).getAttribute('aria-pressed')).toBe('true');
  });

  it('keeps the map area while a replay is opened and left', async () => {
    await openPage('/agent-plugin?area=heap');
    const user = await openProfilesTool();
    await user.click(screen.getByRole('button', { name: /^help-what-is-here/ }));
    expect(screen.getByRole('heading', { name: 'help-what-is-here' })).toBeTruthy();
    await user.click(screen.getByRole('tab', { name: /^Map/ }));
    expect(screen.getByRole('radio', { name: /^Heap dumps/ }).getAttribute('aria-checked')).toBe('true');
  });

  it('honors an explicitly linked case and run', async () => {
    await openPage('/agent-plugin?area=jfr&case=orders-by-coupon-in-sql&run=3');
    expect(screen.getByRole('heading', { name: 'orders-by-coupon-in-sql' })).toBeTruthy();
    expect(screen.getByRole('tab', { name: /^Run 3/ }).getAttribute('aria-selected')).toBe('true');
  });
});

describe('The case list on Replay', () => {
  it('groups every case under its use case', async () => {
    await openPage('/agent-plugin?tab=replay');
    for (const useCase of model.data.useCases) {
      const cases = model.data.replays.filter((replay) => model.data.caseUseCases[replay.case] === useCase.name);
      if (!cases.length) {
        continue;
      }
      const heading = screen.getByRole('heading', { name: `${useCase.label} (${cases.length})` });
      const group = heading.parentElement as HTMLElement;
      for (const replay of cases) {
        expect(within(group).getByRole('button', { name: new RegExp(`^${replay.case}`) })).toBeTruthy();
      }
    }
  });
});

describe('Use cases', () => {
  it('shows one card per use case, each question opening its replay', async () => {
    const page = await openPage('/agent-plugin?tab=use-cases');
    for (const useCase of model.data.useCases) {
      expect(screen.getByRole('heading', { level: 2, name: useCase.label })).toBeTruthy();
    }
    const card = screen.getByRole('region', { name: 'JVM health' });
    await userEvent.setup().click(within(card).getByRole('button', { name: /^Are GC pauses hurting it\?/ }));
    expect(screen.getByRole('heading', { name: 'gc-pauses' })).toBeTruthy();
    expect(page.router.currentRoute.value.query.tab).toBe('replay');
    expect(page.router.currentRoute.value.query.case).toBe('gc-pauses');
  });

  it('names the tool families its runs called, the profile lookup left out', async () => {
    await openPage('/agent-plugin?tab=use-cases');
    for (const useCase of model.data.useCases) {
      const called = new Set<string>();
      for (const replay of model.data.replays.filter((item) => model.data.caseUseCases[item.case] === useCase.name)) {
        for (const step of replay.runs.flatMap((run) => run.steps)) {
          const family = step.kind === 'jeffrey' ? model.tools.get(step.name)?.family : undefined;
          if (family && family !== 'profiles') {
            called.add(family);
          }
        }
      }
      const card = screen.getByRole('region', { name: useCase.label });
      const chips = Array.from(card.querySelectorAll('.node-chip--family .node-chip-label')).map((chip) => chip.textContent);
      expect(new Set(chips)).toEqual(called);
    }
  });

  it('opens from an overview tile at its card', async () => {
    const page = await openPage('/agent-plugin');
    const scrolled = vi.fn();
    Element.prototype.scrollIntoView = scrolled;
    await userEvent.setup().click(screen.getByRole('button', { name: /^JVM health/ }));
    await waitFor(() => {
      expect(page.router.currentRoute.value.query.tab).toBe('use-cases');
      expect(page.router.currentRoute.value.query.useCase).toBe('jvm-health');
    });
    expect(screen.getByRole('tab', { name: /^Use cases/ }).getAttribute('aria-selected')).toBe('true');
    await waitFor(() => {
      expect(scrolled).toHaveBeenCalled();
    });
  });
});

describe('Overview and the bar', () => {
  it('opens on the overview, with the figures and no area switch', async () => {
    await openPage('/agent-plugin');
    expect(screen.getByRole('tab', { name: /^Overview/ }).getAttribute('aria-selected')).toBe('true');
    expect(screen.getByText('recorded eval runs')).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'What do you want to know?' })).toBeTruthy();
    expect(screen.queryByRole('radiogroup', { name: 'Area of the plugin' })).toBeNull();
  });

  it('shows the area switch on the tabs scoped to an area', async () => {
    await openPage('/agent-plugin');
    await userEvent.setup().click(screen.getByRole('tab', { name: /^Map/ }));
    expect(screen.getByRole('radio', { name: /^JFR recordings/ }).getAttribute('aria-checked')).toBe('true');
  });
});

describe('What a case ran on', () => {
  it('marks a case on the made-up workload, names its data in the run, and leaves a case without a recording unmarked', async () => {
    await openPage('/agent-plugin?area=jfr&case=two-areas-delegated&run=1');
    const row = screen.getByRole('button', { name: /^two-areas-delegated/ });
    expect(within(row).getByRole('img', { name: 'Made-up workload' })).toBeTruthy();
    expect(screen.getByText(/A made-up checkout service/)).toBeTruthy();
    const real = screen.getByRole('button', { name: /^gc-pauses/ });
    expect(within(real).getByRole('img', { name: 'Real service' })).toBeTruthy();
    const advice = screen.getByRole('button', { name: /^record-exceptions-next-time/ });
    expect(within(advice).queryByRole('img')).toBeNull();
  });
});

describe('The use cases of a tool', () => {
  it('lists the cases whose runs called it, marked by their data, as many as the Tools table counts', async () => {
    await openPage('/agent-plugin?area=jfr');
    const user = userEvent.setup();
    await user.click(screen.getByRole('tab', { name: /^Tools/ }));
    const row = screen.getByText('profiles_list', { selector: 'td' }).closest('tr')!;
    const counted = Number(row.querySelectorAll('td')[6].textContent);
    await user.click(screen.getByText('profiles_list', { selector: 'td' }));
    const heading = screen.getByRole('heading', { name: /^Use cases \(\d+\)$/ });
    expect(heading.textContent).toBe(`Use cases (${counted})`);
    const uses = heading.parentElement!.querySelectorAll('button');
    expect(uses).toHaveLength(counted);
    expect(within(uses[0] as HTMLElement).getByRole('img', { name: /workload|Real service/ })).toBeTruthy();
    expect(within(uses[0] as HTMLElement).getByText(/^\d+ of \d+ runs$/)).toBeTruthy();
  });
});

describe('The agent on the map', () => {
  const agentCalls = (map: HTMLElement) =>
    Array.from(map.querySelectorAll('path')).filter((path) => path.textContent?.startsWith('jfr-analyst → '));

  it('shows what the agent called as shares of the tool rows, and its lines only once it is picked', async () => {
    await openPage('/agent-plugin?tab=map&area=jfr');
    const map = screen.getByRole('group', { name: 'Skills, agents and tool families' });
    expect(agentCalls(map)).toHaveLength(0);
    // The flamegraph_* row's share: every call the agent made to one of its tools, in the recorded runs.
    const toFlamegraphs = Object.entries(model.data.observed['a:jfr-analyst'] ?? {})
      .filter(([tool]) => tool.startsWith('t:flamegraph_'))
      .reduce((sum, [, count]) => sum + count, 0);
    expect(within(map).getByText(`${toFlamegraphs} via agent`)).toBeTruthy();
    await userEvent.setup().click(screen.getByRole('button', { name: /^agent jfr-analyst,/ }));
    expect(agentCalls(map).length).toBeGreaterThan(0);
  });

  it('keeps a picked skill and its lines while the pointer moves on and its families open', async () => {
    await openPage('/agent-plugin?tab=map&area=jfr');
    const user = userEvent.setup();
    const map = screen.getByRole('group', { name: 'Skills, agents and tool families' });
    const hotFrom = (name: string) =>
      Array.from(map.querySelectorAll('path.map-edge--hot')).filter((path) => path.textContent?.startsWith(`${name} → `));
    const skill = screen.getByRole('button', { name: /^skill analyze-jfr,/ });
    await user.click(skill);
    const lines = hotFrom('analyze-jfr').length;
    expect(lines).toBeGreaterThan(0);
    await user.hover(screen.getByRole('button', { name: /^family code_\*/ }));
    expect(hotFrom('analyze-jfr')).toHaveLength(lines);
    await user.click(screen.getByRole('button', { name: /^family code_\*/ }));
    expect(skill.getAttribute('aria-pressed')).toBe('true');
    expect(screen.getByRole('button', { name: /^tool code_hotspots,/ })).toBeTruthy();
    expect(hotFrom('analyze-jfr').length).toBeGreaterThan(0);
  });
});

describe('Replay trail', () => {
  it('opens a replay in observed mode even after inspecting the described wiring', async () => {
    await openPage('/agent-plugin?area=jfr&case=wall-vs-cpu&run=2');
    const user = userEvent.setup();
    await user.click(screen.getByRole('tab', { name: /^Map/ }));
    await user.click(screen.getByRole('radio', { name: 'As the instructions describe it' }));
    await user.click(screen.getByRole('tab', { name: /^Replay/ }));
    await user.click(screen.getByRole('button', { name: /Show it on the full map/ }));
    expect(screen.getByRole('radio', { name: 'As the runs used it' }).getAttribute('aria-checked')).toBe('true');
  });

  it('excludes future calls when mapping a scrubbed replay prefix', async () => {
    await openPage('/agent-plugin?area=jfr&case=wall-vs-cpu&run=2');
    await fireEvent.input(screen.getByRole('slider', { name: 'Position in the run' }), { target: { value: '0' } });
    await userEvent.setup().click(screen.getByRole('button', { name: /Show it on the full map/ }));
    const map = screen.getByRole('group', { name: 'Skills, agents and tool families' });
    expect(map.querySelectorAll('.map-edge--trail')).toHaveLength(0);
    expect(screen.getByRole('button', { name: /^skill analyze-jfr,/ }).classList.contains('map-node--trail')).toBe(true);
  });


  it('highlights actual owner-to-tool calls without adding edges from other runs', async () => {
    // A run whose flamegraph export was the analyst's alone: the map's analyze-jfr → flamegraph_export edge comes
    // from other runs, and must not light up for this one. Picked from the data, since a new eval round re-records it.
    const exportsBy = (steps: { kind: string; name: string; by?: [string, string] | null }[], owner: string) =>
      steps.some((step) => step.kind === 'jeffrey' && step.name === 'flamegraph_export' && step.by?.[1] === owner);
    const delegated = model.data.replays
      .flatMap((replay) => replay.runs.map((run) => ({ replay, run })))
      .find(({ run }) => exportsBy(run.steps, 'jfr-analyst') && !exportsBy(run.steps, 'analyze-jfr'))!;
    await openPage(`/agent-plugin?area=jfr&case=${delegated.replay.case}&run=${delegated.run.run}`);
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: /Show it on the full map/ }));
    const map = screen.getByRole('group', { name: 'Skills, agents and tool families' });
    const paths = Array.from(map.querySelectorAll('path'));
    const unrelated = paths.find((path) => path.textContent?.startsWith('analyze-jfr → flamegraph_export:'))!;
    expect(unrelated.classList.contains('map-edge--trail')).toBe(false);
    const actual = paths.find((path) => path.textContent?.startsWith('jfr-analyst → flamegraph_export:'))!;
    expect(actual.classList.contains('map-edge--trail')).toBe(true);
  });
});

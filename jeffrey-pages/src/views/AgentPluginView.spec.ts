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
  it('opens a cross-area replay in its own area and preserves the linked run on reload', async () => {
    const page = await openPage('/agent-plugin?area=jfr');
    const user = await openProfilesTool();
    await user.click(screen.getByRole('button', { name: 'orders-by-coupon-in-sql' }));
    await waitFor(() => {
      expect(screen.getByRole('radio', { name: /^Heap dumps/ }).getAttribute('aria-checked')).toBe('true');
    });
    await user.click(screen.getByRole('tab', { name: /^Run 2/ }));
    await waitFor(() => {
      expect(page.router.currentRoute.value.query.area).toBe('heap');
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

  it('keeps the current area when opening a shared replay', async () => {
    await openPage('/agent-plugin?area=heap');
    const user = await openProfilesTool();
    await user.click(screen.getByRole('button', { name: 'help-what-is-here' }));
    expect(screen.getByRole('heading', { name: 'help-what-is-here' })).toBeTruthy();
    expect(screen.getByRole('radio', { name: /^Heap dumps/ }).getAttribute('aria-checked')).toBe('true');
  });

  it('honors an explicitly linked case when its query area disagrees', async () => {
    await openPage('/agent-plugin?area=jfr&case=orders-by-coupon-in-sql&run=3');
    expect(screen.getByRole('heading', { name: 'orders-by-coupon-in-sql' })).toBeTruthy();
    expect(screen.getByRole('radio', { name: /^Heap dumps/ }).getAttribute('aria-checked')).toBe('true');
    expect(screen.getByRole('tab', { name: /^Run 3/ }).getAttribute('aria-selected')).toBe('true');
  });
});

describe('Replay trail', () => {
  it('opens a replay in observed mode even after inspecting the described wiring', async () => {
    await openPage('/agent-plugin?area=jfr&case=named-area-cpu&run=3');
    const user = userEvent.setup();
    await user.click(screen.getByRole('tab', { name: /^Map/ }));
    await user.click(screen.getByRole('radio', { name: 'As the instructions describe it' }));
    await user.click(screen.getByRole('tab', { name: /^Replay/ }));
    await user.click(screen.getByRole('button', { name: /Show it on the full map/ }));
    expect(screen.getByRole('radio', { name: 'As the runs used it' }).getAttribute('aria-checked')).toBe('true');
  });

  it('excludes future calls when mapping a scrubbed replay prefix', async () => {
    await openPage('/agent-plugin?area=jfr&case=named-area-cpu&run=3');
    await fireEvent.input(screen.getByRole('slider', { name: 'Position in the run' }), { target: { value: '0' } });
    await userEvent.setup().click(screen.getByRole('button', { name: /Show it on the full map/ }));
    const map = screen.getByRole('group', { name: 'Skills, agents and tool families' });
    expect(map.querySelectorAll('.map-edge--trail')).toHaveLength(0);
    expect(screen.getByRole('button', { name: /^skill analyze-jfr,/ }).classList.contains('map-node--trail')).toBe(true);
  });


  it('highlights actual owner-to-tool calls without adding edges from other runs', async () => {
    await openPage('/agent-plugin?area=jfr&case=named-area-cpu&run=3');
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

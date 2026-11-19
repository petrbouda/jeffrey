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
import linkParams from '@/router/link-params.json';
import profileRoutes from '@/router/profile-routes.json';
import {
  END_EPOCH_MS_QUERY_PARAM,
  SEARCH_QUERY_PARAM,
  START_EPOCH_MS_QUERY_PARAM
} from '@/services/flamegraphs/FlamegraphLinkQuery';
import { BASELINE_QUERY_PARAM } from '@/services/BaselineQuery';
import { EVENT_TYPE_QUERY_PARAM } from '@/services/events/EventLinkQuery';

/*
 * link-params.json is the contract between this build and the MCP server: for every profile page an
 * MCP answer links to with a query, the query parameters that page reads. The server's tests read it
 * (UiLinkRoutes.assertResolves) and fail on a link parameter the page does not declare, so a name
 * changed on either side breaks a build instead of silently opening the unfiltered page.
 *
 * Renaming a parameter here means renaming it in the page that reads it and in the MCP link that
 * writes it; this spec pins the whole file so none of the three moves alone.
 */
const params = linkParams as Record<string, Record<string, string>>;

describe('link parameter contract', () => {
  it('pins every linked page and the parameters it reads', () => {
    expect(params).toEqual({
      events: { EVENT_TYPE: 'eventType' },
      'flamegraph-view': {
        EVENT_TYPE: 'eventType',
        GRAPH_MODE: 'graphMode',
        BASELINE: 'baseline',
        USE_WEIGHT: 'useWeight',
        USE_THREAD_MODE: 'useThreadMode',
        EXCLUDE_IDLE_SAMPLES: 'excludeIdleSamples',
        EXCLUDE_NON_JAVA_SAMPLES: 'excludeNonJavaSamples',
        ONLY_UNSAFE_ALLOCATION_SAMPLES: 'onlyUnsafeAllocationSamples',
        START_EPOCH_MS: 'startEpochMs',
        END_EPOCH_MS: 'endEpochMs',
        SEARCH: 'search'
      },
      'flamegraphs/differential': { BASELINE: 'baseline' },
      'heap-dump/diff': { BASELINE: 'baseline' },
      'heap-dump/gc-root-path': { OBJECT_ID: 'objectId' },
      'subsecond-view': {
        EVENT_TYPE: 'eventType',
        GRAPH_MODE: 'graphMode',
        BASELINE: 'baseline',
        USE_WEIGHT: 'useWeight'
      },
      'technologies/grpc/overview': { MODE: 'mode' },
      'technologies/grpc/services': { MODE: 'mode', SERVICE: 'service' },
      'technologies/grpc/traffic': { MODE: 'mode' },
      'technologies/http/endpoints': { MODE: 'mode', URI: 'uri' },
      'technologies/http/overview': { MODE: 'mode' },
      'technologies/jdbc/statement-groups': { GROUP: 'group' },
      'traces/attributes/search': { WHERE: 'where', SCOPE: 'scope', TRACE: 'trace' },
      'traces/attributes/values': {
        KEY: 'key',
        SOURCE: 'source',
        OWNER: 'owner',
        EVENT_TYPE: 'eventType'
      },
      'traces/operations': {
        OPERATION: 'operation',
        KIND: 'kind',
        EVENT_TYPE: 'eventType',
        TAB: 'tab',
        TRACE: 'trace',
        SEARCH: 'q',
        ERRORS: 'errors',
        SORT: 'sort'
      }
    });
  });

  it('names only pages the router serves under a profile', () => {
    const served = new Set(profileRoutes as string[]);
    expect(Object.keys(params).filter(route => !served.has(route))).toEqual([]);
  });

  it('is where the flamegraph view takes its window and search names from', () => {
    const view = params['flamegraph-view'];
    expect(START_EPOCH_MS_QUERY_PARAM).toBe(view.START_EPOCH_MS);
    expect(END_EPOCH_MS_QUERY_PARAM).toBe(view.END_EPOCH_MS);
    expect(SEARCH_QUERY_PARAM).toBe(view.SEARCH);
  });

  it('spells the baseline one way on every page, the way ProfileDetail adopts it', () => {
    const spellings = Object.values(params)
      .map(page => page.BASELINE)
      .filter(name => name !== undefined);
    expect(spellings.length).toBeGreaterThan(0);
    expect(new Set(spellings)).toEqual(new Set([BASELINE_QUERY_PARAM]));
  });

  it('is where the events page takes its event type name from', () => {
    expect(EVENT_TYPE_QUERY_PARAM).toBe(params.events.EVENT_TYPE);
  });
});

/*
 * The other direction: the page must still read what the file declares. A page that renames the
 * parameter it reads, or stops reading it, would leave the MCP link writing a name nobody reads -
 * and the Java side, which trusts this file, could not tell.
 */
const SOURCES = import.meta.glob(
  ['/src/views/**/*.vue', '/src/components/**/*.vue', '/src/services/**/*.ts', '/src/router/*.ts'],
  { query: '?raw', import: 'default', eager: true }
) as Record<string, string>;
const LINK_PARAMS_IMPORT = /from\s+['"]@\/router\/link-params\.json['"]/;

function sourceOf(path: string): string {
  const source = SOURCES[`/src/${path.replace(/^@\//, '')}`];
  expect(source, `no source file ${path}`).toBeDefined();
  return source;
}

/** The component each profile route renders, read from the router's own source. */
function componentOf(route: string): string {
  const routes = sourceOf('router/profileChildRoutes.ts');
  const start = routes.indexOf(`path: '${route}'`);
  expect(start, `no route '${route}' in profileChildRoutes.ts`).toBeGreaterThanOrEqual(0);
  const next = routes.indexOf('path:', start + 1);
  const entry = routes.slice(start, next < 0 ? undefined : next);
  const component = /import\('(@\/[^']+)'\)/.exec(entry);
  expect(component, `route '${route}' names no component`).not.toBeNull();
  return component![1];
}

/**
 * Where a page reads a parameter outside its own file: a module it imports that reads the query for
 * it. Each is checked to be imported by the page, so a page that drops the module fails here too.
 */
const READ_THROUGH: Record<string, string[]> = {
  events: ['services/events/EventLinkQuery.ts'],
  'flamegraph-view': ['services/flamegraphs/FlamegraphLinkQuery.ts'],
  'traces/operations': ['components/trace/TraceOperationDetail.vue'],
  'traces/attributes/values': ['services/api/model/trace/TraceAttributeModels.ts']
};

/** ProfileDetail, the parent of every profile route, adopts ?baseline= through BaselineQuery. */
const BASELINE_READER = 'services/BaselineQuery.ts';

function escaped(name: string): string {
  return name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/** Whether a source reads the parameter: `query.x`, `query?.x`, `query['x']`, or its role in the JSON. */
function reads(source: string, name: string, role: string): boolean {
  const byName = new RegExp(
    `\\b\\w*[qQ]uery\\w*\\s*(?:\\?\\.|\\.)\\s*${escaped(name)}\\b|\\w*[qQ]uery\\w*\\s*\\[\\s*['"]${escaped(name)}['"]\\s*\\]`
  );
  const byRole = LINK_PARAMS_IMPORT.test(source) && new RegExp(`\\.${role}\\b`).test(source);
  return byName.test(source) || byRole;
}

describe('every linked page reads what the contract declares', () => {
  it('tells a read from a rename', () => {
    expect(reads('const mode = route.query.mode as string;', 'mode', 'MODE')).toBe(true);
    expect(reads("const q = initialQuery['q'];", 'q', 'SEARCH')).toBe(true);
    expect(reads('const mode = route.query.direction as string;', 'mode', 'MODE')).toBe(false);
    expect(reads('const direction = route.query.modeX;', 'mode', 'MODE')).toBe(false);
  });

  for (const [route, declared] of Object.entries(params)) {
    it(`${route} reads every parameter it declares`, () => {
      const component = componentOf(route);
      const pageSource = sourceOf(component);
      const through = READ_THROUGH[route] ?? [];
      for (const module of through) {
        const name = module
          .split('/')
          .pop()!
          .replace(/\.(ts|vue)$/, '');
        expect(pageSource, `${component} does not import ${module}`).toMatch(
          new RegExp(`from\\s+['"][^'"]*${escaped(name)}(\\.(ts|vue))?['"]`)
        );
      }
      const sources = [pageSource, ...through.map(sourceOf)];
      const unread = Object.entries(declared)
        .filter(([role, name]) => {
          if (role === 'BASELINE') {
            return !reads(sourceOf(BASELINE_READER), name, role);
          }
          return !sources.some(source => reads(source, name, role));
        })
        .map(([, name]) => name);
      expect(unread, `${component} does not read these declared parameters`).toEqual([]);
    });
  }

  it('adopts the baseline on every profile page, in ProfileDetail', () => {
    expect(sourceOf('views/profiles/ProfileDetail.vue')).toMatch(
      /route\.query\[BASELINE_QUERY_PARAM\]/
    );
  });

  /*
   * Both views that take graphMode decide what they draw with one rule, so a link without it opens
   * the primary graph on either rather than reporting a baseline it never asked for.
   */
  it.each(['flamegraph-view', 'subsecond-view'])(
    '%s decides its opening from graphMode with flamegraphViewOpening',
    route => {
      expect(sourceOf(componentOf(route))).toMatch(
        /flamegraphViewOpening\(\s*\w*[qQ]uery\w*\.graphMode/
      );
    }
  );
});

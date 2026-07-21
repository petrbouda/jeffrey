/**
 * Data-driven navigation config for the ProfileDetail sidebar.
 *
 * Every entry of the former hand-written sidebar template lives here; the markup is
 * rendered by `@/components/profile/ProfileSidebar.vue` + `ProfileSidebarItem.vue`.
 */

export type ProfileMode =
  | 'Overview'
  | 'JVM'
  | 'Application'
  | 'Technologies'
  | 'Visualization'
  | 'HeapDump'
  | 'Tools';

export type DifferentialType = 'flamegraphs' | 'subsecond';

export type TechnologyQueryMode = 'server' | 'client';

export interface ProfileNavItem {
  label: string;
  /** Bootstrap icon class without the leading `bi ` (e.g. `bi-stars`). */
  icon: string;
  /** Absolute route path (may include a query string). Absent only on submenu parents. */
  path?: (profileId: string) => string;
  /** Sub-path relative to `/profiles/{profileId}`; set by the `item` helper and used to derive the mode for a URL. */
  subPath?: string;
  /** Feature keys checked via `isFeatureDisabled`; the item is disabled when ANY key is disabled. */
  disabledKeys?: string[];
  /** Extra CSS class rendered next to `nav-item` (e.g. `nav-item-ai`). */
  cssClass?: string;
  /** Renders as an `<a>` emitting `navigate-differential` (with lock icon) instead of a router-link. */
  differentialType?: DifferentialType;
  /** Substring matched against `route.path` for manual active-state handling. */
  activePathIncludes?: string;
  /** Query `mode` matching for Technologies items (`server` also matches a missing mode). */
  activeQueryMode?: TechnologyQueryMode;
  /** Also adds the `active` class when `route.path` equals the item path exactly. */
  activeExactPath?: boolean;
  /** Submenu children (e.g. Garbage Collection). The parent renders as a toggle, not a link. */
  children?: ProfileNavItem[];
}

export interface ProfileNavSection {
  title: string;
  items: ProfileNavItem[];
}

export interface TechnologyNavGroup {
  /** Optional inline section title rendered between items (e.g. OVERVIEW / TRAFFIC). */
  title?: string;
  items: ProfileNavItem[];
}

export interface TechnologyNav {
  key: string;
  name: string;
  icon: string;
  groups: TechnologyNavGroup[];
}

function profilePath(subPath: string): (profileId: string) => string {
  return (profileId: string) => `/profiles/${profileId}${subPath}`;
}

function item(
  label: string,
  icon: string,
  subPath: string,
  options: Partial<ProfileNavItem> = {}
): ProfileNavItem {
  return { label, icon, subPath, path: profilePath(subPath), ...options };
}

/** Technologies item carrying a `?mode=server|client` query with manual active matching. */
function techModeItem(
  label: string,
  icon: string,
  subPath: string,
  mode: TechnologyQueryMode
): ProfileNavItem {
  return {
    label,
    icon,
    path: (profileId: string) => `/profiles/${profileId}${subPath}?mode=${mode}`,
    activePathIncludes: subPath,
    activeQueryMode: mode
  };
}

const HEAP_DUMP_KEY = 'heap-dump';
const AI_ANALYSIS_KEY = 'ai-analysis';
const AI_ITEM_CLASS = 'nav-item-ai';

function httpGroups(mode: TechnologyQueryMode): TechnologyNavGroup[] {
  return [
    {
      items: [
        techModeItem('Timeseries', 'bi-graph-up', '/technologies/http/timeseries', mode),
        techModeItem('Distribution', 'bi-pie-chart', '/technologies/http/distribution', mode),
        techModeItem('Slowest Requests', 'bi-clock-history', '/technologies/http/slowest', mode),
        techModeItem('Endpoint Details', 'bi-share', '/technologies/http/endpoints', mode)
      ]
    }
  ];
}

function grpcGroups(mode: TechnologyQueryMode): TechnologyNavGroup[] {
  return [
    {
      title: 'OVERVIEW',
      items: [
        techModeItem('Timeseries', 'bi-graph-up', '/technologies/grpc/timeseries', mode),
        techModeItem('Distribution', 'bi-pie-chart', '/technologies/grpc/distribution', mode),
        techModeItem('Slowest Calls', 'bi-clock-history', '/technologies/grpc/slowest', mode),
        techModeItem('Service Details', 'bi-diagram-3', '/technologies/grpc/services', mode)
      ]
    },
    {
      title: 'TRAFFIC',
      items: [
        techModeItem('Size Over Time', 'bi-graph-up', '/technologies/grpc/size-timeseries', mode),
        techModeItem(
          'Size Distribution',
          'bi-bar-chart',
          '/technologies/grpc/size-distribution',
          mode
        ),
        techModeItem('Largest Calls', 'bi-box-seam', '/technologies/grpc/largest', mode)
      ]
    }
  ];
}

/** Per-technology sidebar definitions for the Technologies drilled-in state. */
export const technologiesNav: Record<string, TechnologyNav> = {
  'http-server': {
    key: 'http-server',
    name: 'HTTP Server',
    icon: 'bi-globe2',
    groups: httpGroups('server')
  },
  'http-client': {
    key: 'http-client',
    name: 'HTTP Client',
    icon: 'bi-send',
    groups: httpGroups('client')
  },
  'grpc-server': {
    key: 'grpc-server',
    name: 'gRPC Server',
    icon: 'bi-diagram-3',
    groups: grpcGroups('server')
  },
  'grpc-client': {
    key: 'grpc-client',
    name: 'gRPC Client',
    icon: 'bi-arrow-left-right',
    groups: grpcGroups('client')
  },
  jdbc: {
    key: 'jdbc',
    name: 'Database (JDBC)',
    icon: 'bi-database',
    groups: [
      {
        title: 'OVERVIEW',
        items: [
          item('Timeseries', 'bi-graph-up', '/technologies/jdbc/timeseries'),
          item('Distribution', 'bi-pie-chart', '/technologies/jdbc/distribution'),
          item('Slowest Statements', 'bi-clock-history', '/technologies/jdbc/slowest-statements'),
          item('Statement Groups', 'bi-collection', '/technologies/jdbc/statement-groups'),
          item('Connection Pools', 'bi-diagram-3', '/technologies/jdbc-pool')
        ]
      }
    ]
  },
  'method-tracing': {
    key: 'method-tracing',
    name: 'Method Tracing',
    icon: 'bi-speedometer2',
    groups: [
      {
        title: 'OVERVIEW',
        items: [
          item('Timeseries', 'bi-graph-up', '/technologies/method-tracing/timeseries'),
          item('Distribution', 'bi-pie-chart', '/technologies/method-tracing/distribution')
        ]
      },
      {
        title: 'ANALYSIS',
        items: [
          item('Flamegraph', 'bi-fire', '/technologies/method-tracing/flamegraph'),
          item('Slowest Traces', 'bi-hourglass-split', '/technologies/method-tracing/slowest'),
          item('Cumulated Traces', 'bi-layers', '/technologies/method-tracing/cumulated')
        ]
      }
    ]
  },
  'async-profiler': {
    key: 'async-profiler',
    name: 'Async-Profiler Spans',
    icon: 'bi-bounding-box',
    groups: [
      {
        items: [
          item('Spans by Tag', 'bi-table', '/technologies/async-profiler/spans'),
          item('Slowest Spans', 'bi-hourglass-split', '/technologies/async-profiler/slowest-spans')
        ]
      }
    ]
  }
};

/** Sidebar sections for all modes except Technologies (which uses `technologiesNav`). */
export const profileNavSections: Record<
  Exclude<ProfileMode, 'Technologies'>,
  ProfileNavSection[]
> = {
  Overview: [
    {
      title: 'DASHBOARDS',
      items: [item('Summary', 'bi-grid-1x2', '/dashboard')]
    },
    {
      title: 'INSIGHTS',
      items: [
        item('JFR AI Analysis', 'bi-stars', '/ai-analysis', {
          disabledKeys: [AI_ANALYSIS_KEY],
          cssClass: AI_ITEM_CLASS
        }),
        item('Guardian Analysis', 'bi-shield-check', '/guardian'),
        item('Auto Analysis', 'bi-robot', '/auto-analysis')
      ]
    },
    {
      title: 'PROFILE',
      items: [
        item('Configuration', 'bi-gear', '/overview'),
        item('Event Types', 'bi-list-check', '/event-types'),
        item('Event Viewer', 'bi-collection', '/events')
      ]
    }
  ],
  JVM: [
    {
      title: 'MEMORY',
      items: [
        {
          label: 'Garbage Collection',
          icon: 'bi-recycle',
          activePathIncludes: '/garbage-collection',
          children: [
            item('Overview', 'bi-bar-chart-line', '/garbage-collection'),
            item('Timeseries', 'bi-graph-up-arrow', '/garbage-collection/timeseries'),
            item('Configuration', 'bi-gear', '/garbage-collection/configuration'),
            item('G1 Analysis', 'bi-diagram-3', '/garbage-collection/g1'),
            item('ZGC Analysis', 'bi-cpu', '/garbage-collection/zgc')
          ]
        },
        item('String & Symbol Tables', 'bi-fonts', '/string-symbol-tables')
      ]
    },
    {
      title: 'NATIVE MEMORY',
      items: [
        item('Native Memory', 'bi-hdd-stack', '/native-memory'),
        item('Native Library Loads', 'bi-box-arrow-in-down', '/native-library-loads'),
        item('Native Memory Tracking', 'bi-pie-chart', '/nmt')
      ]
    },
    {
      title: 'CODE',
      items: [
        {
          label: 'JIT Compilation',
          icon: 'bi-bar-chart-line',
          activePathIncludes: '/jit-compilation',
          children: [
            item('Compilations', 'bi-bar-chart-line', '/jit-compilation', {
              activeExactPath: true
            }),
            item('Deoptimizations', 'bi-arrow-counterclockwise', '/jit-compilation/deoptimizations')
          ]
        },
        item('Class Loading', 'bi-box-seam', '/class-loading')
      ]
    },
    {
      title: 'RUNTIME',
      items: [
        item('JVM Flags', 'bi-flag', '/flags'),
        item('Performance Counters', 'bi-speedometer2', '/performance-counters', {
          disabledKeys: ['performance-counters']
        }),
        item('VM Operations', 'bi-stopwatch', '/vm-operations'),
        item('Modules', 'bi-boxes', '/modules')
      ]
    },
    {
      title: 'ENVIRONMENT',
      items: [item('System & Host', 'bi-cpu', '/system')]
    }
  ],
  Application: [
    {
      title: 'MEMORY',
      items: [
        item('Heap Allocations', 'bi-box', '/allocations'),
        {
          label: 'Memory Issues',
          icon: 'bi-bug',
          activePathIncludes: '/memory-issues',
          children: [
            item('Leak Candidates', 'bi-bug', '/memory-issues/leak-candidates'),
            item('Finalizers', 'bi-hourglass-split', '/memory-issues/finalizers'),
            item('Reference Processing', 'bi-link-45deg', '/memory-issues/reference-processing')
          ]
        }
      ]
    },
    {
      title: 'THREADS',
      items: [
        item('Statistics', 'bi-graph-up', '/thread-statistics'),
        item('Timeline', 'bi-clock-history', '/threads-timeline'),
        item('Virtual Threads', 'bi-pin-angle', '/virtual-threads'),
        item('Thread Dumps', 'bi-file-earmark-text', '/thread-dumps')
      ]
    },
    {
      title: 'BEHAVIOR',
      items: [
        item('Exceptions', 'bi-exclamation-octagon', '/exceptions'),
        item('Blocking Operations', 'bi-lock', '/blocking-operations')
      ]
    },
    {
      title: 'I/O & SECURITY',
      items: [
        item('File I/O', 'bi-file-earmark', '/file-io'),
        item('Socket I/O', 'bi-ethernet', '/socket-io'),
        item('Security & TLS', 'bi-shield-lock', '/security')
      ]
    },
    {
      title: 'CONTAINERS',
      items: [
        item('Container Configuration', 'bi-server', '/container/configuration', {
          disabledKeys: ['container']
        }),
        item('CPU Throttling Detector', 'bi-thermometer-half', '/container/cpu-throttling', {
          disabledKeys: ['container']
        })
      ]
    }
  ],
  Visualization: [
    {
      title: 'FLAMEGRAPHS',
      items: [
        item('Primary', 'bi-fire', '/flamegraphs/primary'),
        item('Differential', 'bi-file-diff', '/flamegraphs/differential', {
          differentialType: 'flamegraphs',
          activePathIncludes: '/flamegraphs/differential'
        })
      ]
    },
    {
      title: 'SUBSECOND',
      items: [
        item('Primary', 'bi-bar-chart', '/subsecond/primary'),
        item('Differential', 'bi-file-bar-graph', '/subsecond/differential', {
          differentialType: 'subsecond',
          activePathIncludes: '/subsecond/differential'
        })
      ]
    }
  ],
  HeapDump: [
    {
      title: 'OVERVIEW',
      items: [
        item('Heap Dump Overview', 'bi-memory', '/heap-dump/settings'),
        item('AI Analysis', 'bi-stars', '/heap-dump/ai-analysis', {
          disabledKeys: [HEAP_DUMP_KEY, AI_ANALYSIS_KEY],
          cssClass: AI_ITEM_CLASS
        }),
        item('OQL Query', 'bi-terminal', '/heap-dump/oql', { disabledKeys: [HEAP_DUMP_KEY] })
      ]
    },
    {
      title: 'MEMORY ANALYSIS',
      items: [
        item('Class Histogram', 'bi-list-ol', '/heap-dump/histogram', {
          disabledKeys: [HEAP_DUMP_KEY]
        }),
        item('Dominator Tree', 'bi-diagram-2', '/heap-dump/dominator-tree', {
          disabledKeys: [HEAP_DUMP_KEY]
        }),
        item('Leak Suspects', 'bi-bug', '/heap-dump/leak-suspects', {
          disabledKeys: [HEAP_DUMP_KEY]
        }),
        item('Path to GC Root', 'bi-signpost-2', '/heap-dump/gc-root-path', {
          disabledKeys: [HEAP_DUMP_KEY]
        }),
        item('GC Roots', 'bi-diagram-3', '/heap-dump/gc-roots', {
          disabledKeys: [HEAP_DUMP_KEY]
        }),
        item('Collection Analysis', 'bi-collection', '/heap-dump/collection-analysis', {
          disabledKeys: [HEAP_DUMP_KEY]
        }),
        item('Biggest Collections', 'bi-collection-fill', '/heap-dump/biggest-collections', {
          disabledKeys: [HEAP_DUMP_KEY]
        }),
        item('String Analysis', 'bi-fonts', '/heap-dump/string-analysis', {
          disabledKeys: [HEAP_DUMP_KEY]
        })
      ]
    },
    {
      title: 'RUNTIME',
      items: [
        item('Threads', 'bi-cpu', '/heap-dump/threads', { disabledKeys: [HEAP_DUMP_KEY] }),
        item('Class Loaders', 'bi-diagram-2', '/heap-dump/classloader-analysis', {
          disabledKeys: [HEAP_DUMP_KEY]
        })
      ]
    }
  ],
  Tools: [
    {
      title: 'TRANSFORM',
      items: [
        item('Rename Frames', 'bi-pencil-square', '/tools/rename-frames'),
        item('Collapse Frames', 'bi-layers', '/tools/collapse-frames')
      ]
    },
    {
      title: 'CONVERT',
      items: [
        item('To PPROF', 'bi-download', '/tools/to-pprof'),
        item('To OTLP', 'bi-box-arrow-down', '/tools/to-otlp')
      ]
    }
  ]
};

// Sub-paths (relative to /profiles/{profileId}) owned by the Overview and Application modes,
// derived from the nav config itself so moving an item between modes keeps `getModeForPath`
// in sync automatically. Anything not matched by a prefix rule or one of these sets falls
// back to JVM Internals.
function collectSubPaths(sections: ProfileNavSection[]): Set<string> {
  const subPaths = new Set<string>();
  const addItem = (navItem: ProfileNavItem): void => {
    if (navItem.subPath) {
      subPaths.add(navItem.subPath.split('?')[0]);
    }
    for (const child of navItem.children ?? []) {
      addItem(child);
    }
  };
  for (const section of sections) {
    for (const navItem of section.items) {
      addItem(navItem);
    }
  }
  return subPaths;
}

const OVERVIEW_SUB_PATHS = collectSubPaths(profileNavSections.Overview);
const APPLICATION_SUB_PATHS = collectSubPaths(profileNavSections.Application);

const PROFILE_PATH_PREFIX = /^\/profiles\/[^/]+/;

const VISUALIZATION_PATH_MARKERS = [
  '/flamegraphs/',
  '/subsecond/',
  '/timeseries/',
  '/flamegraph-view',
  '/subsecond-view'
];

/**
 * Derive the active mode (top navigation pill) from a route path, so refreshes and
 * cross-mode navigations keep the pill row in sync. Single source of truth shared by
 * `ProfileDetail.vue`.
 */
export function getModeForPath(path: string): ProfileMode {
  if (path.includes('/technologies/')) {
    return 'Technologies';
  }
  if (VISUALIZATION_PATH_MARKERS.some(marker => path.includes(marker))) {
    return 'Visualization';
  }
  if (path.includes('/heap-dump/')) {
    return 'HeapDump';
  }
  if (path.includes('/tools/')) {
    return 'Tools';
  }
  const subPath = path.replace(PROFILE_PATH_PREFIX, '');
  if (OVERVIEW_SUB_PATHS.has(subPath)) {
    return 'Overview';
  }
  if (APPLICATION_SUB_PATHS.has(subPath)) {
    return 'Application';
  }
  return 'JVM';
}

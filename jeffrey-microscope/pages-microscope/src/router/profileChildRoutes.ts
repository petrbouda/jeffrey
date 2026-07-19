// Profile child routes, grouped by feature domain. The groups are concatenated into
// one flat child list under /profiles/:profileId — grouping is organizational only.
// Imported by tests to verify the sidebar navigation config resolves to real routes.

// Overview, analysis, and event browsing
const analysisRoutes = [
  {
    path: '',
    redirect: (to: { params: { profileId: string } }) => `/profiles/${to.params.profileId}/overview`
  },
  {
    path: 'overview',
    name: 'profile-overview',
    component: () => import('@/views/profiles/detail/ProfileConfiguration.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'guardian',
    name: 'profile-guardian',
    component: () => import('@/views/profiles/detail/ProfileGuardian.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'auto-analysis',
    name: 'profile-auto-analysis',
    component: () => import('@/views/profiles/detail/ProfileAutoAnalysis.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'ai-analysis',
    name: 'profile-ai-analysis',
    component: () => import('@/views/profiles/ProfileAiJfrAnalysis.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'event-types',
    name: 'profile-event-types',
    component: () => import('@/views/profiles/detail/ProfileEventTypes.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'events',
    name: 'profile-events',
    component: () => import('@/views/profiles/detail/ProfileEvents.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'flags',
    name: 'profile-flags',
    component: () => import('@/views/profiles/detail/ProfileFlags.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'performance-counters',
    name: 'profile-performance-counters',
    component: () => import('@/views/profiles/detail/ProfilePerformanceCounters.vue'),
    meta: { layout: 'profile' }
  }
];

// Flamegraphs and sub-second visualizations
const visualizationRoutes = [
  {
    path: 'flamegraphs/primary',
    name: 'profile-flamegraphs-primary',
    component: () => import('@/views/profiles/detail/ProfileFlamegraphsPrimary.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'flamegraphs/differential',
    name: 'profile-flamegraphs-differential',
    component: () => import('@/views/profiles/detail/ProfileFlamegraphsDifferential.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'subsecond/primary',
    name: 'profile-subsecond-primary',
    component: () => import('@/views/profiles/detail/ProfileSubsecondPrimary.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'subsecond/differential',
    name: 'profile-subsecond-differential',
    component: () => import('@/views/profiles/detail/ProfileSubsecondDifferential.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'flamegraph-view',
    name: 'flamegraph',
    component: () => import('@/views/profiles/detail/ProfileFlamegraphView.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'subsecond-view',
    name: 'subsecond',
    component: () => import('@/views/profiles/detail/ProfileSubSecondView.vue'),
    meta: { layout: 'profile' }
  }
];

// Garbage collection
const gcRoutes = [
  {
    path: 'garbage-collection',
    name: 'profile-garbage-collection',
    component: () => import('@/views/profiles/detail/ProfileGarbageCollection.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'garbage-collection/timeseries',
    name: 'profile-garbage-collection-timeseries',
    component: () => import('@/views/profiles/detail/ProfileGCTimeseries.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'garbage-collection/configuration',
    name: 'profile-garbage-collection-configuration',
    component: () => import('@/views/profiles/detail/ProfileGCConfiguration.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'garbage-collection/g1',
    name: 'profile-garbage-collection-g1',
    component: () => import('@/views/profiles/detail/ProfileGCG1.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'garbage-collection/zgc',
    name: 'profile-garbage-collection-zgc',
    component: () => import('@/views/profiles/detail/ProfileGCZgc.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'string-symbol-tables',
    name: 'profile-string-symbol-tables',
    component: () => import('@/views/profiles/detail/ProfileGCStringSymbolTables.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'garbage-collection/finalizers',
    name: 'profile-garbage-collection-finalizers',
    component: () => import('@/views/profiles/detail/ProfileGCFinalizers.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'garbage-collection/reference-processing',
    name: 'profile-garbage-collection-reference-processing',
    component: () => import('@/views/profiles/detail/ProfileGCReferenceProcessing.vue'),
    meta: { layout: 'profile' }
  }
];

// Threads
const threadRoutes = [
  {
    path: 'thread-statistics',
    name: 'profile-thread-statistics',
    component: () => import('@/views/profiles/detail/ProfileThreadStatistics.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'threads-timeline',
    name: 'profile-threads-timeline',
    component: () => import('@/views/profiles/detail/ProfileThreadsTimeline.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'virtual-threads',
    name: 'profile-virtual-threads',
    component: () => import('@/views/profiles/detail/ProfileVirtualThreads.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'thread-dumps',
    name: 'profile-thread-dumps',
    component: () => import('@/views/profiles/detail/ProfileThreadDumps.vue'),
    meta: { layout: 'profile' }
  }
];

// JIT compiler
const jitRoutes = [
  {
    path: 'jit-compilation',
    name: 'profile-jit-compilation',
    component: () => import('@/views/profiles/detail/ProfileJitCompilation.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'jit-compilation/deoptimizations',
    name: 'profile-jit-deoptimizations',
    component: () => import('@/views/profiles/detail/ProfileJitDeoptimizations.vue'),
    meta: { layout: 'profile' }
  }
];

// Memory (allocations, native memory, leaks)
const memoryRoutes = [
  {
    path: 'native-memory',
    name: 'profile-native-memory',
    component: () => import('@/views/profiles/detail/ProfileNativeMemory.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'native-library-loads',
    name: 'profile-native-library-loads',
    component: () => import('@/views/profiles/detail/ProfileNativeLibraryLoads.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'nmt',
    name: 'profile-nmt',
    component: () => import('@/views/profiles/detail/ProfileNmt.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'allocations',
    name: 'profile-allocations',
    component: () => import('@/views/profiles/detail/ProfileAllocations.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'leak-candidates',
    name: 'profile-leak-candidates',
    component: () => import('@/views/profiles/detail/ProfileLeakCandidates.vue'),
    meta: { layout: 'profile' }
  }
];

// JVM runtime and infrastructure
const runtimeRoutes = [
  {
    path: 'class-loading',
    name: 'profile-class-loading',
    component: () => import('@/views/profiles/detail/ProfileClassLoading.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'exceptions',
    name: 'profile-exceptions',
    component: () => import('@/views/profiles/detail/ProfileExceptions.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'system',
    name: 'profile-system',
    component: () => import('@/views/profiles/detail/ProfileSystem.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'modules',
    name: 'profile-modules',
    component: () => import('@/views/profiles/detail/ProfileModules.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'vm-operations',
    name: 'profile-vm-operations',
    component: () => import('@/views/profiles/detail/ProfileVmOperations.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'blocking-operations',
    name: 'profile-blocking-operations',
    component: () => import('@/views/profiles/detail/ProfileBlockingOperations.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'socket-io',
    name: 'profile-socket-io',
    component: () => import('@/views/profiles/detail/ProfileSocketIo.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'file-io',
    name: 'profile-file-io',
    component: () => import('@/views/profiles/detail/ProfileFileIo.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'security',
    name: 'profile-security',
    component: () => import('@/views/profiles/detail/ProfileSecurity.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'container/configuration',
    name: 'profile-container-configuration',
    component: () => import('@/views/profiles/detail/ProfileContainerConfiguration.vue'),
    meta: { layout: 'profile' }
  }
];

// Heap dump analysis
const heapDumpRoutes = [
  {
    path: 'heap-dump/settings',
    name: 'profile-heap-dump-settings',
    component: () => import('@/views/profiles/detail/ProfileHeapDumpSettings.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'heap-dump/histogram',
    name: 'profile-heap-dump-histogram',
    component: () => import('@/views/profiles/detail/ProfileHeapDumpHistogram.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'heap-dump/string-analysis',
    name: 'profile-heap-dump-string-analysis',
    component: () => import('@/views/profiles/detail/ProfileHeapDumpStringAnalysis.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'heap-dump/oql',
    name: 'profile-heap-dump-oql',
    component: () => import('@/views/profiles/detail/ProfileHeapDumpOQL.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'heap-dump/gc-roots',
    name: 'profile-heap-dump-gc-roots',
    component: () => import('@/views/profiles/detail/ProfileHeapDumpGCRoots.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'heap-dump/gc-root-path',
    name: 'profile-heap-dump-gc-root-path',
    component: () => import('@/views/profiles/detail/ProfileHeapDumpGCRootPath.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'heap-dump/threads',
    name: 'profile-heap-dump-threads',
    component: () => import('@/views/profiles/detail/ProfileHeapDumpThreads.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'heap-dump/ai-analysis',
    name: 'profile-heap-dump-ai-analysis',
    component: () => import('@/views/profiles/ProfileHeapDumpAiAnalysis.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'heap-dump/dominator-tree',
    name: 'profile-heap-dump-dominator-tree',
    component: () => import('@/views/profiles/detail/ProfileHeapDumpDominatorTree.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'heap-dump/collection-analysis',
    name: 'profile-heap-dump-collection-analysis',
    component: () => import('@/views/profiles/detail/ProfileHeapDumpCollectionAnalysis.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'heap-dump/leak-suspects',
    name: 'profile-heap-dump-leak-suspects',
    component: () => import('@/views/profiles/detail/ProfileHeapDumpLeakSuspects.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'heap-dump/biggest-collections',
    name: 'profile-heap-dump-biggest-collections',
    component: () => import('@/views/profiles/detail/ProfileHeapDumpBiggestCollections.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'heap-dump/classloader-analysis',
    name: 'profile-heap-dump-classloader-analysis',
    component: () => import('@/views/profiles/detail/ProfileHeapDumpClassLoaderAnalysis.vue'),
    meta: { layout: 'profile' }
  }
];

// Technologies (HTTP, gRPC, JDBC, tracing)
const technologyRoutes = [
  {
    path: 'technologies/hub',
    name: 'profile-technologies-hub',
    component: () => import('@/views/profiles/detail/technologies/ProfileTechnologiesHub.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'technologies/http/overview',
    name: 'profile-technologies-http-overview',
    component: () => import('@/views/profiles/detail/technologies/ProfileHttpOverview.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'technologies/http/timeseries',
    name: 'profile-technologies-http-timeseries',
    component: () => import('@/views/profiles/detail/technologies/ProfileHttpTimeseries.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'technologies/http/distribution',
    name: 'profile-technologies-http-distribution',
    component: () => import('@/views/profiles/detail/technologies/ProfileHttpDistribution.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'technologies/http/slowest',
    name: 'profile-technologies-http-slowest',
    component: () => import('@/views/profiles/detail/technologies/ProfileHttpSlowestRequests.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'technologies/http/endpoints',
    name: 'profile-technologies-http-endpoints',
    component: () => import('@/views/profiles/detail/technologies/ProfileHttpEndpoints.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'technologies/jdbc',
    name: 'profile-technologies-jdbc',
    component: () => import('@/views/profiles/detail/technologies/ProfileJdbc.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'technologies/jdbc/overview',
    name: 'profile-technologies-jdbc-overview',
    component: () => import('@/views/profiles/detail/technologies/ProfileJdbcOverview.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'technologies/jdbc/timeseries',
    name: 'profile-technologies-jdbc-timeseries',
    component: () => import('@/views/profiles/detail/technologies/ProfileJdbcTimeseries.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'technologies/jdbc/distribution',
    name: 'profile-technologies-jdbc-distribution',
    component: () => import('@/views/profiles/detail/technologies/ProfileJdbcDistribution.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'technologies/jdbc/slowest-statements',
    name: 'profile-technologies-jdbc-slowest-statements',
    component: () =>
      import('@/views/profiles/detail/technologies/ProfileJdbcSlowestStatements.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'technologies/jdbc/statement-groups',
    name: 'profile-technologies-jdbc-statement-groups',
    component: () => import('@/views/profiles/detail/technologies/ProfileJdbcStatementGroups.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'technologies/jdbc-pool',
    name: 'profile-technologies-jdbc-pool',
    component: () => import('@/views/profiles/detail/technologies/ProfileJdbcPool.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'technologies/method-tracing/timeseries',
    name: 'profile-technologies-method-tracing-timeseries',
    component: () =>
      import('@/views/profiles/detail/technologies/ProfileMethodTracingTimeseries.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'technologies/method-tracing/distribution',
    name: 'profile-technologies-method-tracing-distribution',
    component: () =>
      import('@/views/profiles/detail/technologies/ProfileMethodTracingDistribution.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'technologies/method-tracing/flamegraph',
    name: 'profile-technologies-method-tracing-flamegraph',
    component: () =>
      import('@/views/profiles/detail/technologies/ProfileMethodTracingFlamegraph.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'technologies/method-tracing/slowest',
    name: 'profile-technologies-method-tracing-slowest',
    component: () => import('@/views/profiles/detail/technologies/ProfileMethodTracingSlowest.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'technologies/method-tracing/cumulated',
    name: 'profile-technologies-method-tracing-cumulated',
    component: () =>
      import('@/views/profiles/detail/technologies/ProfileMethodTracingCumulated.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'technologies/async-profiler/spans',
    name: 'profile-technologies-async-profiler-spans',
    component: () => import('@/views/profiles/detail/technologies/ProfileAsyncProfilerSpans.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'technologies/async-profiler/slowest-spans',
    name: 'profile-technologies-async-profiler-slowest-spans',
    component: () =>
      import('@/views/profiles/detail/technologies/ProfileAsyncProfilerSlowestSpans.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'technologies/grpc/overview',
    name: 'profile-technologies-grpc-overview',
    component: () => import('@/views/profiles/detail/technologies/ProfileGrpcOverview.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'technologies/grpc/timeseries',
    name: 'profile-technologies-grpc-timeseries',
    component: () => import('@/views/profiles/detail/technologies/ProfileGrpcTimeseries.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'technologies/grpc/distribution',
    name: 'profile-technologies-grpc-distribution',
    component: () => import('@/views/profiles/detail/technologies/ProfileGrpcDistribution.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'technologies/grpc/slowest',
    name: 'profile-technologies-grpc-slowest',
    component: () => import('@/views/profiles/detail/technologies/ProfileGrpcSlowestCalls.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'technologies/grpc/services',
    name: 'profile-technologies-grpc-services',
    component: () => import('@/views/profiles/detail/technologies/ProfileGrpcServices.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'technologies/grpc/traffic',
    name: 'profile-technologies-grpc-traffic',
    component: () => import('@/views/profiles/detail/technologies/ProfileGrpcTraffic.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'technologies/grpc/size-timeseries',
    name: 'profile-technologies-grpc-size-timeseries',
    component: () => import('@/views/profiles/detail/technologies/ProfileGrpcSizeTimeseries.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'technologies/grpc/size-distribution',
    name: 'profile-technologies-grpc-size-distribution',
    component: () => import('@/views/profiles/detail/technologies/ProfileGrpcSizeDistribution.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'technologies/grpc/largest',
    name: 'profile-technologies-grpc-largest',
    component: () => import('@/views/profiles/detail/technologies/ProfileGrpcLargestCalls.vue'),
    meta: { layout: 'profile' }
  }
];

// Recording tools
const toolsRoutes = [
  {
    path: 'tools/rename-frames',
    name: 'profile-tools-rename-frames',
    component: () => import('@/views/profiles/detail/ProfileToolsRenameFrames.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'tools/collapse-frames',
    name: 'profile-tools-collapse-frames',
    component: () => import('@/views/profiles/detail/ProfileToolsCollapseFrames.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'tools/to-pprof',
    name: 'profile-tools-to-pprof',
    component: () => import('@/views/profiles/detail/ProfileToolsToPprof.vue'),
    meta: { layout: 'profile' }
  },
  {
    path: 'tools/to-otlp',
    name: 'profile-tools-to-otlp',
    component: () => import('@/views/profiles/detail/ProfileToolsToOtlp.vue'),
    meta: { layout: 'profile' }
  }
];

export const profileChildRoutes = [
  ...analysisRoutes,
  ...visualizationRoutes,
  ...gcRoutes,
  ...threadRoutes,
  ...jitRoutes,
  ...memoryRoutes,
  ...runtimeRoutes,
  ...heapDumpRoutes,
  ...technologyRoutes,
  ...toolsRoutes
];

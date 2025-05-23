<template>
  <div class="jit-compilation-container">
    <!-- Header Section with Stats Overview -->
    <DashboardHeader 
      title="JIT Compilation"
      description="Real-time insights into Java Just-In-Time compilation performance"
      icon="lightning-charge-fill"
    />

    <!-- Main Dashboard Grid -->
    <div class="dashboard-grid">
      <!-- Row 1: Dashboard Cards -->
      <div class="dashboard-row">
        <DashboardCard
          title="Compilations"
          :value="jitData.compileCount"
          variant="highlight"
          :valueA="jitData.standardCompileCount"
          :valueB="jitData.osrCompileCount"
          labelA="Standard"
          labelB="OSR"
          comparison="a-greater"
        />
        
        <DashboardCard
          title="Failed Compilations"
          :value="jitData.bailoutCount + jitData.invalidatedCount"
          variant="danger"
          :valueA="jitData.bailoutCount"
          :valueB="jitData.invalidatedCount"
          labelA="Bailouts"
          labelB="Invalidations"
          comparison="a-greater"
        />
      </div>

      <!-- Row 2: Code Size Card and Compilation Time Card -->
      <div class="dashboard-row mb-4">
        <DashboardCard
          title="Memory Usage (nMethods)"
          :value="formatBytes(jitData.nmethodsSize)"
          :valueA="formatBytes(jitData.nmethodCodeSize)"
          :valueB="formatBytes(jitData.nmethodsSize - jitData.nmethodCodeSize)"
          labelA="Code"
          labelB="Metadata"
          variant="info"
          comparison="a-greater"
        />
        
        <DashboardCard
          title="Peak Compilation Time"
          :value="formatTime(jitData.peakTimeSpent)"
          variant="warning"
          :valueA="formatTime(jitData.totalTimeSpent)"
          labelA="Total Time"
        />
      </div>

      <!-- Row 3: Long Compilation Table -->
      <div class="data-table-card">
        <div class="chart-card-header">
          <h5>Long Compilations</h5>
          <div class="chart-controls">
            <span class="badge bg-primary">
              <i class="bi bi-clock-history me-1"></i>
              Threshold: 50ms
            </span>
          </div>
        </div>
        <div class="table-responsive">
          <table class="table table-hover">
            <thead>
              <tr>
                <th>ID</th>
                <th>Method</th>
                <th>Compiler</th>
                <th>Level</th>
                <th>Time</th>
                <th>Code Size</th>
                <th>Inlined</th>
                <th>Arena</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="compilation in jitData.longCompilations" :key="compilation.compileId" 
                  :class="{ 'table-danger': !compilation.succeeded, 'table-warning': compilation.timeSpent > 150 && compilation.succeeded }">
                <td>{{ compilation.compileId }}</td>
                <td>
                  <div class="method-cell">
                    <span class="method-name">{{ getSimpleMethodName(compilation.method) }}</span>
                    <span class="method-path text-muted small">{{ getMethodPath(compilation.method) }}</span>
                  </div>
                </td>
                <td>
                  <span class="badge" :class="getCompilerBadgeClass(compilation.compiler)">
                    {{ compilation.compiler }}
                  </span>
                </td>
                <td>
                  <div class="d-flex align-items-center">
                    <div class="tier-indicator" :class="getTierClass(compilation.compileLevel)"></div>
                    {{ compilation.compileLevel }}
                  </div>
                </td>
                <td>
                  <span :class="{ 'text-danger fw-bold': compilation.timeSpent > 200, 'text-warning fw-medium': compilation.timeSpent > 100 && compilation.timeSpent <= 200 }">
                    {{ formatTime(compilation.timeSpent) }}
                  </span>
                </td>
                <td>{{ formatBytes(compilation.codeSize) }}</td>
                <td>{{ formatBytes(compilation.inlinedBytes) }}</td>
                <td>{{ formatBytes(compilation.arenaBytes) }}</td>
                <td>
                  <span v-if="compilation.succeeded" class="badge bg-success">Success</span>
                  <span v-else class="badge bg-danger">Failed</span>
                  <span v-if="compilation.isOsr" class="badge bg-info ms-1">OSR</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import DashboardHeader from '@/components/DashboardHeader.vue';
import DashboardCard from '@/components/DashboardCard.vue';

// TypeScript interface for JIT compilation data
interface JitCompilationData {
  compileCount: number;           // Compiled Methods
  bailoutCount: number;           // Bailouts
  invalidatedCount: number;       // Invalidated Compilations
  osrCompileCount: number;        // OSR Compilations
  standardCompileCount: number;   // Standard Compilations
  osrBytesCompiled: number;       // OSR Bytes Compiled
  standardBytesCompiled: number;  // Standard Bytes Compiled
  nmethodsSize: number;           // Compilation Resulting Size
  nmethodCodeSize: number;        // Compilation Resulting Code Size
  peakTimeSpent: number;          // Peak Time in milliseconds
  totalTimeSpent: number;         // Total time in milliseconds
  longCompilations: LongCompilation[]; // Compilations that cross a threshold
}

// Interface for long compilations
interface LongCompilation {
  compileId: number;              // Compilation Identifier
  compiler: CompilerType;         // Compiler type
  method: string;                 // Method being compiled
  compileLevel: number;           // Compilation Level
  succeeded: boolean;             // Whether compilation succeeded
  isOsr: boolean;                 // Whether this is an On Stack Replacement
  codeSize: number;               // Compiled Code Size in bytes
  inlinedBytes: number;           // Inlined Code Size in bytes
  arenaBytes: number;             // Arena Usage in bytes
  timeSpent: number;              // Time spent on compilation in milliseconds
}

// Enum for compiler types
enum CompilerType {
  C1 = "C1",
  C2 = "C2",
  JVMCI = "JVMCI"
}

// Mock data for JIT compilation
const jitData = ref<JitCompilationData>({
  compileCount: 8742,
  bailoutCount: 124,
  invalidatedCount: 89,
  osrCompileCount: 1245,
  standardCompileCount: 7497,
  osrBytesCompiled: 3245678,
  standardBytesCompiled: 19876543,
  nmethodsSize: 25678432,
  nmethodCodeSize: 18345678,
  peakTimeSpent: 842,
  totalTimeSpent: 12478,
  longCompilations: [
    {
      compileId: 1,
      compiler: CompilerType.C2,
      method: "java.lang.String::substring",
      compileLevel: 4,
      succeeded: true,
      isOsr: false,
      codeSize: 5120,
      inlinedBytes: 2048,
      arenaBytes: 1024,
      timeSpent: 150
    },
    {
      compileId: 2,
      compiler: CompilerType.C1,
      method: "java.util.ArrayList::add",
      compileLevel: 1,
      succeeded: true,
      isOsr: true,
      codeSize: 2560,
      inlinedBytes: 1024,
      arenaBytes: 512,
      timeSpent: 75
    },
    {
      compileId: 3,
      compiler: CompilerType.JVMCI,
      method: "java.util.HashMap::get",
      compileLevel: 4,
      succeeded: false,
      isOsr: false,
      codeSize: 4096,
      inlinedBytes: 2048,
      arenaBytes: 1024,
      timeSpent: 200
    },
    {
      compileId: 4,
      compiler: CompilerType.C2,
      method: "org.apache.commons.lang3.StringUtils::containsIgnoreCase",
      compileLevel: 4,
      succeeded: true,
      isOsr: false,
      codeSize: 8192,
      inlinedBytes: 3584,
      arenaBytes: 2048,
      timeSpent: 320
    },
    {
      compileId: 5,
      compiler: CompilerType.C1,
      method: "java.util.concurrent.ConcurrentHashMap::putVal",
      compileLevel: 3,
      succeeded: true,
      isOsr: false,
      codeSize: 4352,
      inlinedBytes: 1792,
      arenaBytes: 1024,
      timeSpent: 95
    },
    {
      compileId: 6,
      compiler: CompilerType.C2,
      method: "java.io.BufferedInputStream::read",
      compileLevel: 4,
      succeeded: true,
      isOsr: true,
      codeSize: 3072,
      inlinedBytes: 1536,
      arenaBytes: 768,
      timeSpent: 180
    },
    {
      compileId: 7,
      compiler: CompilerType.JVMCI,
      method: "com.fasterxml.jackson.databind.ObjectMapper::readValue",
      compileLevel: 4,
      succeeded: false,
      isOsr: false,
      codeSize: 12288,
      inlinedBytes: 6144,
      arenaBytes: 4096,
      timeSpent: 450
    },
    {
      compileId: 8,
      compiler: CompilerType.C2,
      method: "java.util.regex.Pattern$CharProperty::match",
      compileLevel: 4,
      succeeded: true,
      isOsr: false,
      codeSize: 7168,
      inlinedBytes: 3072,
      arenaBytes: 1536,
      timeSpent: 220
    },
    {
      compileId: 9,
      compiler: CompilerType.C2,
      method: "org.hibernate.collection.internal.PersistentSet::size",
      compileLevel: 4,
      succeeded: true,
      isOsr: false,
      codeSize: 2048,
      inlinedBytes: 1024,
      arenaBytes: 512,
      timeSpent: 120
    },
    {
      compileId: 10,
      compiler: CompilerType.C1,
      method: "java.util.concurrent.locks.ReentrantLock::lock",
      compileLevel: 2,
      succeeded: true,
      isOsr: false,
      codeSize: 1536,
      inlinedBytes: 768,
      arenaBytes: 384,
      timeSpent: 65
    }
  ]
});

// Formatting functions
const formatBytes = (bytes: number): string => {
  if (bytes === 0) return '0 Bytes';
  
  const k = 1024;
  const sizes = ['Bytes', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
};

const formatTime = (milliseconds: number): string => {
  if (milliseconds < 1000) {
    return `${milliseconds} ms`;
  } else {
    return `${(milliseconds / 1000).toFixed(2)} s`;
  }
};

// Chart helper functions
const getBarHeight = (value: number, maxValue: number): number => {
  return value > 0 && maxValue > 0 ? (value / maxValue) * 100 : 0;
};

// Method name and path helpers
const getSimpleMethodName = (method: string): string => {
  const parts = method.split('::');
  const className = parts[0].substring(parts[0].lastIndexOf('.') + 1);
  const methodName = parts.length > 1 ? parts[1] : '';
  return className + '.' + methodName;
};

const getMethodPath = (method: string): string => {
  const parts = method.split('::');
  if (parts.length <= 1) return '';
  
  const fullClassName = parts[0];
  return fullClassName;
};

// Compiler badge class helper
const getCompilerBadgeClass = (compiler: CompilerType): string => {
  switch (compiler) {
    case CompilerType.C1:
      return 'bg-primary';
    case CompilerType.C2:
      return 'bg-success';
    case CompilerType.JVMCI:
      return 'bg-info';
    default:
      return 'bg-secondary';
  }
};

// Tier class helper
const getTierClass = (level: number): string => {
  if (level <= 1) return 'tier-bronze';
  if (level <= 3) return 'tier-silver';
  return 'tier-gold';
};
</script>

<style scoped>
.jit-compilation-container {
  width: 100%;
  color: #333;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, 'Open Sans', 'Helvetica Neue', sans-serif;
}

.dashboard-header {
  padding: 1rem 0;
}

.dashboard-title {
  font-size: 1.75rem;
  font-weight: 600;
  color: #111;
  margin-bottom: 0.25rem;
  display: flex;
  align-items: center;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 1.5rem;
}

/* Dashboard Cards Row */
.dashboard-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 1rem;
}

/* Data Table Card */
.data-table-card {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.chart-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem 1.5rem;
  border-bottom: 1px solid #f0f0f0;
}

.chart-card-header h5 {
  margin: 0;
  font-size: 1.1rem;
  font-weight: 600;
  color: #333;
}

.chart-controls {
  display: flex;
  align-items: center;
}

.table {
  margin-bottom: 0;
}

.table thead th {
  background: #f7f9fc;
  font-weight: 600;
  font-size: 0.9rem;
  color: #555;
  border-top: none;
  border-bottom-width: 1px;
}

.table tbody td {
  font-size: 0.9rem;
  padding: 0.75rem 1.5rem;
  vertical-align: middle;
}

/* Long Compilation Table Styles */
.method-cell {
  display: flex;
  flex-direction: column;
}

.method-name {
  font-weight: 500;
}

.method-path {
  font-size: 0.75rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 250px;
}

.tier-indicator {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  margin-right: 0.5rem;
}

.tier-bronze {
  background-color: #CD7F32;
}

.tier-silver {
  background-color: #C0C0C0;
}

.tier-gold {
  background-color: #FFD700;
}

/* Style updates for the table */
.table-hover tbody tr:hover {
  background-color: rgba(0, 0, 0, 0.02);
}

.table-responsive {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

.mb-4 {
  margin-bottom: 1.5rem;
}

/* Responsive Adjustments */
@media (max-width: 992px) {
  .dashboard-row {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .dashboard-row {
    grid-template-columns: 1fr;
  }
}
</style>

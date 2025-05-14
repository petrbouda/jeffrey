<template>
  <div class="container-fluid p-0">
    <!-- Header Section -->
    <div class="mb-4">
      <h2 class="performance-analysis-title">
        <i class="bi bi-speedometer2 me-2"></i>
        JVM Performance Analysis
      </h2>
      <p class="text-muted fs-6">Analyze and visualize JVM/HotSpot Performance Counters data</p>
    </div>

    <!-- Loading state -->
    <div v-if="loading" class="row">
      <div class="col-12">
        <div class="d-flex justify-content-center my-5">
          <div class="spinner-border text-primary" role="status">
            <span class="visually-hidden">Loading...</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Content state -->
    <div v-else class="row">
      <div class="col-12">
        <!-- Summary Cards -->
        <div class="row mb-4">
          <div class="col-md-3 mb-3">
            <div class="summary-card bg-gradient-blue">
              <div class="summary-icon">
                <i class="bi bi-cpu"></i>
              </div>
              <div class="summary-data">
                <h3 class="summary-value">{{ formatJvmUptime() }}</h3>
                <p class="summary-label">JVM Uptime</p>
              </div>
            </div>
          </div>
          <div class="col-md-3 mb-3">
            <div class="summary-card bg-gradient-green">
              <div class="summary-icon">
                <i class="bi bi-recycle"></i>
              </div>
              <div class="summary-data">
                <h3 class="summary-value">{{ getCounter('sun.gc.collector.0.invocations') || '0' }}</h3>
                <p class="summary-label">GC Collections</p>
              </div>
            </div>
          </div>
          <div class="col-md-3 mb-3">
            <div class="summary-card bg-gradient-orange">
              <div class="summary-icon">
                <i class="bi bi-file-code"></i>
              </div>
              <div class="summary-data">
                <h3 class="summary-value">{{ getCounter('java.cls.loadedClasses') || '0' }}</h3>
                <p class="summary-label">Loaded Classes</p>
              </div>
            </div>
          </div>
          <div class="col-md-3 mb-3">
            <div class="summary-card bg-gradient-purple">
              <div class="summary-icon">
                <i class="bi bi-diagram-3"></i>
              </div>
              <div class="summary-data">
                <h3 class="summary-value">{{ getCounter('java.threads.started') || '0' }}</h3>
                <p class="summary-label">Total Threads</p>
              </div>
            </div>
          </div>
        </div>

        <!-- Dashboard Navigation -->
        <div class="nav-container mb-4">
          <ul class="nav nav-pills nav-fill">
            <li class="nav-item" v-for="section in sections" :key="section.id">
              <button class="nav-link" :class="{ active: activeSection === section.id }"
                      @click="showSection(section.id)">
                <i :class="section.icon"></i>
                {{ section.title }}
              </button>
            </li>
          </ul>
        </div>

        <!-- Dashboard Panels -->
        <div class="dashboard-panel">
          <!-- JVM Performance Panel -->
          <div v-if="activeSection === 'jvm'" class="panel-content">
            <div class="row mb-4">
              <div class="col-md-6 mb-3">
                <div class="dashboard-card">
                  <div class="card-header">
                    <h5><i class="bi bi-clock-history me-2 text-primary"></i> JVM Initialization Timeline</h5>
                  </div>
                  <div class="card-body">
                    <div class="timeline">
                      <div class="timeline-item">
                        <div class="timeline-marker"></div>
                        <div class="timeline-content">
                          <h6>JVM Start</h6>
                          <p>{{ getFormattedValue('sun.rt.createVmBeginTime') }}</p>
                        </div>
                      </div>
                      <div class="timeline-item">
                        <div class="timeline-marker"></div>
                        <div class="timeline-content">
                          <h6>JVM Initialization Complete</h6>
                          <p>{{ getFormattedValue('sun.rt.vmInitDoneTime') }}</p>
                        </div>
                      </div>
                      <div class="timeline-item">
                        <div class="timeline-marker"></div>
                        <div class="timeline-content">
                          <h6>JVM Creation Complete</h6>
                          <p>{{ getFormattedValue('sun.rt.createVmEndTime') }}</p>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
              <div class="col-md-6 mb-3">
                <div class="dashboard-card">
                  <div class="card-header">
                    <h5><i class="bi bi-bar-chart me-2 text-primary"></i> Time Distribution</h5>
                  </div>
                  <div class="card-body d-flex flex-column justify-content-center">
                    <div class="time-distribution-chart">
                      <apexchart type="donut" height="250" :options="timeDistributionOptions"
                                 :series="timeDistributionSeries"></apexchart>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <div class="row mb-4">
              <div class="col-md-6 mb-3">
                <div class="dashboard-card">
                  <div class="card-header">
                    <h5><i class="bi bi-pause-circle me-2 text-primary"></i> Safepoint Statistics</h5>
                  </div>
                  <div class="card-body">
                    <div class="metric-row">
                      <div class="metric-label">Total Safepoints</div>
                      <div class="metric-value">{{ getCounter('sun.rt.safepoints') || '0' }}</div>
                    </div>
                    <div class="metric-row">
                      <div class="metric-label">Safepoint Time</div>
                      <div class="metric-value">{{ getFormattedValue('sun.rt.safepointTime') }}</div>
                    </div>
                    <div class="metric-row">
                      <div class="metric-label">Safepoint Sync Time</div>
                      <div class="metric-value">{{ getFormattedValue('sun.rt.safepointSyncTime') }}</div>
                    </div>
                    <div class="metric-row">
                      <div class="metric-label">Safepoint % of Application Time</div>
                      <div class="metric-value">
                        <div class="progress" style="height: 8px;">
                          <div class="progress-bar bg-primary" role="progressbar"
                               :style="{ width: Math.min(calculateSafepointPercentage(), 100) + '%' }"
                               :aria-valuenow="calculateSafepointPercentage()"
                               aria-valuemin="0" aria-valuemax="100"></div>
                        </div>
                        <small>{{ calculateSafepointPercentage() }}%</small>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
              <div class="col-md-6 mb-3">
                <div class="dashboard-card">
                  <div class="card-header">
                    <h5><i class="bi bi-cpu me-2 text-primary"></i> Runtime Information</h5>
                  </div>
                  <div class="card-body">
                    <div class="metric-row">
                      <div class="metric-label">Java Version</div>
                      <div class="metric-value">{{ getCounter('java.property.java.version') || 'N/A' }}</div>
                    </div>
                    <div class="metric-row">
                      <div class="metric-label">VM Name</div>
                      <div class="metric-value">{{ getCounter('java.property.java.vm.name') || 'N/A' }}</div>
                    </div>
                    <div class="metric-row">
                      <div class="metric-label">VM Version</div>
                      <div class="metric-value">{{ getCounter('java.property.java.vm.version') || 'N/A' }}</div>
                    </div>
                    <div class="metric-row">
                      <div class="metric-label">VM Arguments</div>
                      <div class="metric-value small">
                        <code class="vm-args">{{ getCounter('java.rt.vmArgs') || 'N/A' }}</code>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- GC Panel -->
          <div v-if="activeSection === 'gc'" class="panel-content">
            <div class="row mb-4">
              <div class="col-md-6 mb-3">
                <div class="dashboard-card">
                  <div class="card-header">
                    <h5><i class="bi bi-pie-chart me-2 text-success"></i> Memory Usage</h5>
                  </div>
                  <div class="card-body">
                    <div class="memory-chart-container">
                      <apexchart type="bar" height="250" :options="memoryChartOptions"
                                 :series="memoryChartSeries"></apexchart>
                    </div>
                  </div>
                </div>
              </div>
              <div class="col-md-6 mb-3">
                <div class="dashboard-card">
                  <div class="card-header">
                    <h5><i class="bi bi-info-circle me-2 text-success"></i> GC Information</h5>
                  </div>
                  <div class="card-body">
                    <div class="metric-row">
                      <div class="metric-label">GC Algorithm</div>
                      <div class="metric-value">{{ getCounter('sun.gc.policy.name') || 'N/A' }}</div>
                    </div>
                    <div class="metric-row">
                      <div class="metric-label">Young Gen Collections</div>
                      <div class="metric-value">{{ getCounter('sun.gc.collector.0.invocations') || '0' }}</div>
                    </div>
                    <div class="metric-row">
                      <div class="metric-label">Full GC Collections</div>
                      <div class="metric-value">{{ getCounter('sun.gc.collector.1.invocations') || '0' }}</div>
                    </div>
                    <div class="metric-row">
                      <div class="metric-label">Total GC Pause Time</div>
                      <div class="metric-value">{{ getFormattedValue('sun.gc.collector.0.time') }}</div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <div class="row mb-4">
              <div class="col-md-12">
                <div class="dashboard-card">
                  <div class="card-header">
                    <h5><i class="bi bi-hdd-stack me-2 text-success"></i> Memory Usage Details</h5>
                  </div>
                  <div class="card-body">
                    <div class="memory-gauges">
                      <div class="memory-gauge">
                        <h6 class="gauge-title">Eden Space</h6>
                        <div class="gauge-chart">
                          <div class="gauge-value">
                            {{
                              calculateUsagePercentage('sun.gc.generation.0.space.0.used', 'sun.gc.generation.0.space.0.capacity')
                            }}%
                          </div>
                          <div class="progress" style="height: 10px;">
                            <div class="progress-bar bg-success" role="progressbar"
                                 :style="{ width: calculateUsagePercentage('sun.gc.generation.0.space.0.used', 'sun.gc.generation.0.space.0.capacity') + '%' }"
                                 :aria-valuenow="calculateUsagePercentage('sun.gc.generation.0.space.0.used', 'sun.gc.generation.0.space.0.capacity')"
                                 aria-valuemin="0" aria-valuemax="100"></div>
                          </div>
                          <div class="gauge-details">
                            {{ formatBytes(getCounterAsNumber('sun.gc.generation.0.space.0.used')) }} of
                            {{ formatBytes(getCounterAsNumber('sun.gc.generation.0.space.0.capacity')) }}
                          </div>
                        </div>
                      </div>
                      <div class="memory-gauge">
                        <h6 class="gauge-title">Old Gen</h6>
                        <div class="gauge-chart">
                          <div class="gauge-value">
                            {{
                              calculateUsagePercentage('sun.gc.generation.1.space.0.used', 'sun.gc.generation.1.space.0.capacity')
                            }}%
                          </div>
                          <div class="progress" style="height: 10px;">
                            <div class="progress-bar bg-warning" role="progressbar"
                                 :style="{ width: calculateUsagePercentage('sun.gc.generation.1.space.0.used', 'sun.gc.generation.1.space.0.capacity') + '%' }"
                                 :aria-valuenow="calculateUsagePercentage('sun.gc.generation.1.space.0.used', 'sun.gc.generation.1.space.0.capacity')"
                                 aria-valuemin="0" aria-valuemax="100"></div>
                          </div>
                          <div class="gauge-details">
                            {{ formatBytes(getCounterAsNumber('sun.gc.generation.1.space.0.used')) }} of
                            {{ formatBytes(getCounterAsNumber('sun.gc.generation.1.space.0.capacity')) }}
                          </div>
                        </div>
                      </div>
                      <div class="memory-gauge">
                        <h6 class="gauge-title">Metaspace</h6>
                        <div class="gauge-chart">
                          <div class="gauge-value">
                            {{ calculateUsagePercentage('sun.gc.metaspace.used', 'sun.gc.metaspace.capacity') }}%
                          </div>
                          <div class="progress" style="height: 10px;">
                            <div class="progress-bar bg-info" role="progressbar"
                                 :style="{ width: calculateUsagePercentage('sun.gc.metaspace.used', 'sun.gc.metaspace.capacity') + '%' }"
                                 :aria-valuenow="calculateUsagePercentage('sun.gc.metaspace.used', 'sun.gc.metaspace.capacity')"
                                 aria-valuemin="0" aria-valuemax="100"></div>
                          </div>
                          <div class="gauge-details">
                            {{ formatBytes(getCounterAsNumber('sun.gc.metaspace.used')) }} of
                            {{ formatBytes(getCounterAsNumber('sun.gc.metaspace.capacity')) }}
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- Compiler Panel -->
          <div v-if="activeSection === 'compiler'" class="panel-content">
            <div class="row mb-4">
              <div class="col-md-6 mb-3">
                <div class="dashboard-card">
                  <div class="card-header">
                    <h5><i class="bi bi-lightning me-2 text-info"></i> JIT Compilation</h5>
                  </div>
                  <div class="card-body">
                    <div class="metric-row">
                      <div class="metric-label">Total Compiled Methods</div>
                      <div class="metric-value">{{ getCounter('sun.ci.totalCompiles') || '0' }}</div>
                    </div>
                    <div class="metric-row">
                      <div class="metric-label">Compilation Time</div>
                      <div class="metric-value">{{ getFormattedValue('sun.ci.totalTime') }}</div>
                    </div>
                    <div class="metric-row">
                      <div class="metric-label">Compilation Rate</div>
                      <div class="metric-value">{{ calculateCompilationRate() }} methods/sec</div>
                    </div>
                    <div class="metric-row">
                      <div class="metric-label">% of Application Time</div>
                      <div class="metric-value">
                        <div class="progress" style="height: 8px;">
                          <div class="progress-bar bg-info" role="progressbar"
                               :style="{ width: Math.min(calculateCompilationPercentage(), 100) + '%' }"
                               :aria-valuenow="calculateCompilationPercentage()"
                               aria-valuemin="0" aria-valuemax="100"></div>
                        </div>
                        <small>{{ calculateCompilationPercentage() }}%</small>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
              <div class="col-md-6 mb-3">
                <div class="dashboard-card">
                  <div class="card-header">
                    <h5><i class="bi bi-code-slash me-2 text-info"></i> Compiled Code</h5>
                  </div>
                  <div class="card-body">
                    <apexchart type="pie" height="250" :options="compilationChartOptions"
                               :series="compilationChartSeries"></apexchart>
                  </div>
                </div>
              </div>
            </div>
            <div class="row mb-4">
              <div class="col-md-12">
                <div class="dashboard-card">
                  <div class="card-header">
                    <h5><i class="bi bi-journal-code me-2 text-info"></i> Compilation Details</h5>
                  </div>
                  <div class="card-body">
                    <div class="table-responsive">
                      <table class="table table-sm table-striped">
                        <thead>
                        <tr>
                          <th>Counter</th>
                          <th>Value</th>
                          <th>Description</th>
                        </tr>
                        </thead>
                        <tbody>
                        <tr>
                          <td>Standard Compiles</td>
                          <td>{{ getCounter('sun.ci.standardCompiles') || '0' }}</td>
                          <td>Regular method compilations</td>
                        </tr>
                        <tr>
                          <td>OSR Compiles</td>
                          <td>{{ getCounter('sun.ci.osrCompiles') || '0' }}</td>
                          <td>On-stack replacement compilations</td>
                        </tr>
                        <tr>
                          <td>Native Code Size</td>
                          <td>{{ formatBytes(getCounterAsNumber('sun.ci.nmethodCodeSize')) }}</td>
                          <td>Size of generated native code</td>
                        </tr>
                        <tr>
                          <td>Compiler Threads</td>
                          <td>{{ getCounter('sun.ci.threads') || '0' }}</td>
                          <td>JIT compiler threads active</td>
                        </tr>
                        <tr>
                          <td>Bailouts</td>
                          <td>{{ getCounter('sun.ci.totalBailouts') || '0' }}</td>
                          <td>Compilation bailouts</td>
                        </tr>
                        <tr>
                          <td>Invalidations</td>
                          <td>{{ getCounter('sun.ci.totalInvalidates') || '0' }}</td>
                          <td>Method invalidations</td>
                        </tr>
                        </tbody>
                      </table>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- Bytecode Panel -->
          <div v-if="activeSection === 'bytecode'" class="panel-content">
            <div class="row mb-4">
              <div class="col-md-6 mb-3">
                <div class="dashboard-card">
                  <div class="card-header">
                    <h5><i class="bi bi-file-code me-2 text-warning"></i> Class Loading Summary</h5>
                  </div>
                  <div class="card-body">
                    <apexchart type="donut" height="250" :options="classLoadingChartOptions"
                               :series="classLoadingChartSeries"></apexchart>
                  </div>
                </div>
              </div>
              <div class="col-md-6 mb-3">
                <div class="dashboard-card">
                  <div class="card-header">
                    <h5><i class="bi bi-stopwatch me-2 text-warning"></i> Class Loading Time</h5>
                  </div>
                  <div class="card-body">
                    <div class="metric-row">
                      <div class="metric-label">Total Class Loading Time</div>
                      <div class="metric-value">{{ getFormattedValue('sun.cls.time') }}</div>
                    </div>
                    <div class="metric-row">
                      <div class="metric-label">App Class Loading Time</div>
                      <div class="metric-value">{{ getFormattedValue('sun.cls.appClassLoadTime') }}</div>
                    </div>
                    <div class="metric-row">
                      <div class="metric-label">Class Verification Time</div>
                      <div class="metric-value">{{ getFormattedValue('sun.cls.classVerifyTime') }}</div>
                    </div>
                    <div class="metric-row">
                      <div class="metric-label">Class Initialization Time</div>
                      <div class="metric-value">{{ getFormattedValue('sun.cls.classInitTime') }}</div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <div class="row mb-4">
              <div class="col-md-12">
                <div class="dashboard-card">
                  <div class="card-header">
                    <h5><i class="bi bi-diagram-2 me-2 text-warning"></i> ClassLoader Metrics</h5>
                  </div>
                  <div class="card-body">
                    <div class="row">
                      <div class="col-md-3 mb-3">
                        <div class="class-stat-card">
                          <div class="stat-name">Loaded Classes</div>
                          <div class="stat-value">{{ getCounter('java.cls.loadedClasses') || '0' }}</div>
                          <div class="stat-icon"><i class="bi bi-box"></i></div>
                        </div>
                      </div>
                      <div class="col-md-3 mb-3">
                        <div class="class-stat-card">
                          <div class="stat-name">Unloaded Classes</div>
                          <div class="stat-value">{{ getCounter('java.cls.unloadedClasses') || '0' }}</div>
                          <div class="stat-icon"><i class="bi bi-box-arrow-right"></i></div>
                        </div>
                      </div>
                      <div class="col-md-3 mb-3">
                        <div class="class-stat-card">
                          <div class="stat-name">App Classes</div>
                          <div class="stat-value">{{ getCounter('sun.cls.appClassLoadCount') || '0' }}</div>
                          <div class="stat-icon"><i class="bi bi-boxes"></i></div>
                        </div>
                      </div>
                      <div class="col-md-3 mb-3">
                        <div class="class-stat-card">
                          <div class="stat-name">Method Bytes</div>
                          <div class="stat-value">{{ formatBytes(getCounterAsNumber('sun.cls.methodBytes')) }}</div>
                          <div class="stat-icon"><i class="bi bi-code-square"></i></div>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- Threads Panel -->
          <div v-if="activeSection === 'threads'" class="panel-content">
            <div class="row mb-4">
              <div class="col-md-6 mb-3">
                <div class="dashboard-card">
                  <div class="card-header">
                    <h5><i class="bi bi-diagram-3 me-2 text-danger"></i> Thread Distribution</h5>
                  </div>
                  <div class="card-body">
                    <apexchart type="donut" height="250" :options="threadDistributionChartOptions"
                               :series="threadDistributionSeries"></apexchart>
                  </div>
                </div>
              </div>
              <div class="col-md-6 mb-3">
                <div class="dashboard-card">
                  <div class="card-header">
                    <h5><i class="bi bi-activity me-2 text-danger"></i> Thread Activity</h5>
                  </div>
                  <div class="card-body">
                    <div class="metric-row">
                      <div class="metric-label">Total Created Threads</div>
                      <div class="metric-value">{{ getFormattedValue('java.threads.started') }}</div>
                    </div>
                    <div class="metric-row">
                      <div class="metric-label">Live Threads</div>
                      <div class="metric-value">{{ getFormattedValue('java.threads.live') }}</div>
                    </div>
                    <div class="metric-row">
                      <div class="metric-label">Daemon Threads</div>
                      <div class="metric-value">{{ getFormattedValue('java.threads.daemon') }}</div>
                    </div>
                    <div class="metric-row">
                      <div class="metric-label">Peak Live Threads</div>
                      <div class="metric-value">{{ getFormattedValue('java.threads.livePeak') }}</div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <div class="row mb-4">
              <div class="col-md-12">
                <div class="dashboard-card">
                  <div class="card-header">
                    <h5><i class="bi bi-lock me-2 text-danger"></i> Synchronization Statistics</h5>
                  </div>
                  <div class="card-body">
                    <div class="row">
                      <div class="col-md-3 mb-3">
                        <div class="sync-stat-card">
                          <div class="stat-name">Contended Locks</div>
                          <div class="stat-value">{{ getCounter('sun.rt._sync_ContendedLockAttempts') || '0' }}</div>
                          <div class="stat-icon"><i class="bi bi-lock"></i></div>
                        </div>
                      </div>
                      <div class="col-md-3 mb-3">
                        <div class="sync-stat-card">
                          <div class="stat-name">Inflations</div>
                          <div class="stat-value">{{ getCounter('sun.rt._sync_Inflations') || '0' }}</div>
                          <div class="stat-icon"><i class="bi bi-arrow-up-right-circle"></i></div>
                        </div>
                      </div>
                      <div class="col-md-3 mb-3">
                        <div class="sync-stat-card">
                          <div class="stat-name">Parks</div>
                          <div class="stat-value">{{ getCounter('sun.rt._sync_Parks') || '0' }}</div>
                          <div class="stat-icon"><i class="bi bi-p-circle"></i></div>
                        </div>
                      </div>
                      <div class="col-md-3 mb-3">
                        <div class="sync-stat-card">
                          <div class="stat-name">Notifications</div>
                          <div class="stat-value">{{ getCounter('sun.rt._sync_Notifications') || '0' }}</div>
                          <div class="stat-icon"><i class="bi bi-bell"></i></div>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {onMounted, ref, watch} from 'vue';
import ProfilePerformanceCountersClient from '@/services/ProfilePerformanceCountersClient';
import FormattingService from '@/services/FormattingService';
import PerformanceCounter from "@/services/model/PerformanceCounter.ts";
import {useRoute} from "vue-router";

const route = useRoute();

// State
const loading = ref(true);
const counters = ref<PerformanceCounter[]>([]);
const activeSection = ref<string>('jvm');

// Define available sections
const sections = [
  {id: 'jvm', title: 'JVM', icon: 'bi bi-cpu me-2'},
  {id: 'gc', title: 'Garbage Collection', icon: 'bi bi-recycle me-2'},
  {id: 'compiler', title: 'JIT Compiler', icon: 'bi bi-lightning me-2'},
  {id: 'bytecode', title: 'ClassLoader', icon: 'bi bi-file-code me-2'},
  {id: 'threads', title: 'Threads', icon: 'bi bi-diagram-3 me-2'}
];

// ApexCharts options and series for Time Distribution Chart
const timeDistributionSeries = ref<number[]>([]);
const timeDistributionOptions = ref({
  chart: {
    type: 'donut',
    fontFamily: 'inherit',
  },
  colors: ['#4e73df', '#f6c23e', '#36b9cc', '#1cc88a'],
  labels: ['Application Code', 'Safepoint Pauses', 'JIT Compilation', 'Garbage Collection'],
  plotOptions: {
    pie: {
      donut: {
        size: '70%'
      }
    }
  },
  legend: {
    position: 'bottom',
    fontFamily: 'inherit',
  },
  responsive: [{
    breakpoint: 480,
    options: {
      chart: {
        height: 300
      },
      legend: {
        position: 'bottom'
      }
    }
  }],
  tooltip: {
    y: {
      formatter: function (value: number) {
        const total = timeDistributionSeries.value.reduce((a, b) => a + b, 0);
        const percentage = Math.round((value / total) * 100);
        return `${FormattingService.formatDuration2Units(value)} (${percentage}%)`;
      }
    }
  },
  dataLabels: {
    enabled: false
  }
});

// ApexCharts options and series for Memory Chart
const memoryChartSeries = ref([
  {name: 'Used', data: [0, 0, 0, 0]},
  {name: 'Free', data: [0, 0, 0, 0]}
]);
const memoryChartOptions = ref({
  chart: {
    type: 'bar',
    stacked: true,
    fontFamily: 'inherit',
  },
  colors: ['#1cc88a', '#dddddd'],
  xaxis: {
    categories: ['Eden', 'Survivor', 'Old Gen', 'Metaspace'],
  },
  yaxis: {
    labels: {
      formatter: function (value: number) {
        return FormattingService.formatBytes(value);
      }
    }
  },
  tooltip: {
    y: {
      formatter: function (value: number) {
        return FormattingService.formatBytes(value);
      }
    }
  },
  plotOptions: {
    bar: {
      horizontal: false,
    }
  },
  dataLabels: {
    enabled: false
  },
  legend: {
    position: 'top'
  }
});

// ApexCharts options and series for Compilation Chart
const compilationChartSeries = ref<number[]>([]);
const compilationChartOptions = ref({
  chart: {
    type: 'pie',
    fontFamily: 'inherit',
  },
  colors: ['#4e73df', '#36b9cc'],
  labels: ['Standard Compilations', 'OSR Compilations'],
  legend: {
    position: 'bottom',
    fontFamily: 'inherit',
  },
  dataLabels: {
    enabled: false
  },
  responsive: [{
    breakpoint: 480,
    options: {
      chart: {
        height: 300
      },
      legend: {
        position: 'bottom'
      }
    }
  }]
});

// ApexCharts options and series for Class Loading Chart
const classLoadingChartSeries = ref<number[]>([]);
const classLoadingChartOptions = ref({
  chart: {
    type: 'donut',
    fontFamily: 'inherit',
  },
  colors: ['#f6c23e', '#4e73df'],
  labels: ['App Classes', 'System Classes'],
  plotOptions: {
    pie: {
      donut: {
        size: '70%'
      }
    }
  },
  legend: {
    position: 'bottom',
    fontFamily: 'inherit',
  },
  dataLabels: {
    enabled: false
  },
  responsive: [{
    breakpoint: 480,
    options: {
      chart: {
        height: 300
      },
      legend: {
        position: 'bottom'
      }
    }
  }]
});

// ApexCharts options and series for Thread Distribution Chart
const threadDistributionSeries = ref<number[]>([]);
const threadDistributionChartOptions = ref({
  chart: {
    type: 'donut',
    fontFamily: 'inherit',
  },
  colors: ['#e74a3b', '#4e73df', '#858796'],
  labels: ['Live Application Threads', 'Daemon Threads', 'Terminated Threads'],
  plotOptions: {
    pie: {
      donut: {
        size: '70%'
      }
    }
  },
  legend: {
    position: 'bottom',
    fontFamily: 'inherit',
  },
  dataLabels: {
    enabled: false
  },
  responsive: [{
    breakpoint: 480,
    options: {
      chart: {
        height: 300
      },
      legend: {
        position: 'bottom'
      }
    }
  }]
});

// Methods
const loadPerformanceCounters = async () => {
  loading.value = true;
  try {
    // Use the ProfilePerformanceCountersClient to fetch real data
    const projectId = route.params.projectId as string;
    const profileId = route.params.profileId as string;
    counters.value = await ProfilePerformanceCountersClient.get(projectId, profileId);

    // Initialize charts after getting the data
    setTimeout(() => {
      updateChartData();
    }, 100);
  } catch (error) {
    console.error('Failed to load performance counters:', error);
  } finally {
    loading.value = false;
  }
};

const showSection = (section: string) => {
  activeSection.value = section;
  // Initialize charts after DOM update
  setTimeout(() => {
    updateChartData();
  }, 100);
};

const getCounter = (key: string): string | null => {
  const counter = counters.value.find(c => c.key === key);
  return counter ? counter.value : null;
};

const getCounterAsNumber = (key: string): number => {
  const value = getCounter(key);
  return value ? parseInt(value) : 0;
};

const getFormattedValue = (key: string): string => {
  const counter = counters.value.find(c => c.key === key);
  return counter ? counter.formattedValue : 'N/A';
};

const formatBytes = (bytes: number): string => {
  return FormattingService.formatBytes(bytes);
};

// Analysis calculations
const calculateSafepointPercentage = (): number => {
  const safepointTime = getCounterAsNumber('sun.rt.safepointTime');
  const applicationTime = getCounterAsNumber('sun.rt.applicationTime');

  if (applicationTime === 0) return 0;

  return parseFloat(((safepointTime / applicationTime) * 100).toFixed(2));
};

const calculateCompilationPercentage = (): number => {
  const compilationTime = getCounterAsNumber('sun.ci.totalTime');
  const applicationTime = getCounterAsNumber('sun.rt.applicationTime');

  if (applicationTime === 0) return 0;

  return parseFloat(((compilationTime / applicationTime) * 100).toFixed(2));
};

const calculateCompilationRate = (): string => {
  const totalCompiles = getCounterAsNumber('sun.ci.totalCompiles');
  const totalTime = getCounterAsNumber('sun.ci.totalTime');

  if (totalTime === 0) return '0';

  return (totalCompiles / (totalTime / 1e9)).toFixed(2);
};

const calculateUsagePercentage = (usedKey: string, capacityKey: string): number => {
  const used = getCounterAsNumber(usedKey);
  const capacity = getCounterAsNumber(capacityKey);

  if (capacity === 0) return 0;

  return Math.min(Math.round((used / capacity) * 100), 100);
};

const formatJvmUptime = (): string => {
  const applicationTimeNs = getCounterAsNumber('sun.rt.applicationTime');

  if (applicationTimeNs === 0) return '0s';

  // Get the full formatted duration
  const fullDuration = FormattingService.formatDuration(applicationTimeNs);

  // Check if the duration contains hours or days
  const hasHourOrLonger = fullDuration.includes('h') || fullDuration.includes('d');

  // For durations less than an hour, return only first unit
  // For durations of an hour or longer, return first two units
  return hasHourOrLonger
      ? fullDuration.split(' ').slice(0, 2).join(' ')
      : fullDuration.split(' ').slice(0, 1).join(' ');
};

// Update chart data
const updateChartData = () => {
  updateTimeDistributionChart();
  updateMemoryChart();
  updateCompilationChart();
  updateClassLoadingChart();
  updateThreadDistributionChart();
};

const updateTimeDistributionChart = () => {
  const applicationTime = getCounterAsNumber('sun.rt.applicationTime');
  const safepointTime = getCounterAsNumber('sun.rt.safepointTime');
  const compilationTime = getCounterAsNumber('sun.ci.totalTime');
  const gcTime = getCounterAsNumber('sun.gc.collector.0.time');

  const otherTime = applicationTime - safepointTime - compilationTime - gcTime;

  timeDistributionSeries.value = [otherTime, safepointTime, compilationTime, gcTime];
};

const updateMemoryChart = () => {
  const edenUsed = getCounterAsNumber('sun.gc.generation.0.space.0.used');
  const edenFree = getCounterAsNumber('sun.gc.generation.0.space.0.capacity') - edenUsed;

  const survivorUsed = getCounterAsNumber('sun.gc.generation.0.space.2.used');
  const survivorFree = getCounterAsNumber('sun.gc.generation.0.space.2.capacity') - survivorUsed;

  const oldUsed = getCounterAsNumber('sun.gc.generation.1.space.0.used');
  const oldFree = getCounterAsNumber('sun.gc.generation.1.space.0.capacity') - oldUsed;

  const metaspaceUsed = getCounterAsNumber('sun.gc.metaspace.used');
  const metaspaceFree = getCounterAsNumber('sun.gc.metaspace.capacity') - metaspaceUsed;

  memoryChartSeries.value = [
    {name: 'Used', data: [edenUsed, survivorUsed, oldUsed, metaspaceUsed]},
    {name: 'Free', data: [edenFree, survivorFree, oldFree, metaspaceFree]}
  ];
};

const updateCompilationChart = () => {
  compilationChartSeries.value = [
    getCounterAsNumber('sun.ci.standardCompiles'),
    getCounterAsNumber('sun.ci.osrCompiles')
  ];
};

const updateClassLoadingChart = () => {
  classLoadingChartSeries.value = [
    getCounterAsNumber('sun.cls.appClassLoadCount'),
    getCounterAsNumber('java.cls.loadedClasses') - getCounterAsNumber('sun.cls.appClassLoadCount')
  ];
};

const updateThreadDistributionChart = () => {
  const liveNonDaemon = getCounterAsNumber('java.threads.live') - getCounterAsNumber('java.threads.daemon');
  const daemon = getCounterAsNumber('java.threads.daemon');
  const terminated = getCounterAsNumber('java.threads.started') - getCounterAsNumber('java.threads.live');

  threadDistributionSeries.value = [liveNonDaemon, daemon, terminated];
};

// Lifecycle methods
onMounted(() => {
  loadPerformanceCounters();
});

// Watch for counter changes to update charts
watch(counters, () => {
  if (!loading.value) {
    setTimeout(() => {
      updateChartData();
    }, 100);
  }
}, {deep: true});
</script>

<style scoped>
.performance-analysis-title {
  font-size: 1.75rem;
  font-weight: 600;
  color: #343a40;
  margin-bottom: 0.5rem;
  display: flex;
  align-items: center;
}

.nav-container .nav-link {
  color: #5a5c69;
  font-weight: 600;
  transition: all 0.2s;
}

.nav-container .nav-link.active {
  color: #fff;
  background-color: #4e73df;
}

.nav-container .nav-link:hover:not(.active) {
  color: #4e73df;
  background-color: #f8f9fc;
}

.dashboard-panel {
  margin-top: 1.5rem;
}

.dashboard-card {
  background-color: #fff;
  border-radius: 0.75rem;
  box-shadow: 0 0.15rem 1.75rem 0 rgba(58, 59, 69, 0.1);
  height: 100%;
  transition: transform 0.2s, box-shadow 0.2s;
}

.dashboard-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 0.5rem 2rem 0 rgba(58, 59, 69, 0.15);
}

.dashboard-card .card-header {
  background-color: transparent;
  border-bottom: 1px solid #e3e6f0;
  padding: 1.25rem;
  border-top-left-radius: 0.75rem;
  border-top-right-radius: 0.75rem;
}

.dashboard-card .card-header h5 {
  margin-bottom: 0;
  font-weight: 600;
  display: flex;
  align-items: center;
  font-size: 1.1rem;
  color: #4e73df;
}

.dashboard-card .card-body {
  padding: 1.5rem;
}

.summary-card {
  border-radius: 0.75rem;
  padding: 1.5rem;
  display: flex;
  align-items: center;
  color: white;
  height: 100%;
  box-shadow: 0 0.15rem 1.75rem 0 rgba(58, 59, 69, 0.15);
  transition: transform 0.2s, box-shadow 0.2s;
}

.summary-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 0.5rem 2rem 0 rgba(58, 59, 69, 0.2);
}

.bg-gradient-blue {
  background: linear-gradient(135deg, #6284e8 0%, #4e73df 100%);
}

.bg-gradient-green {
  background: linear-gradient(135deg, #36df9f 0%, #1cc88a 100%);
}

.bg-gradient-orange {
  background: linear-gradient(135deg, #f8d27a 0%, #f6c23e 100%);
}

.bg-gradient-purple {
  background: linear-gradient(135deg, #ea7568 0%, #e74a3b 100%);
}

.summary-icon {
  font-size: 2.5rem;
  margin-right: 1rem;
  opacity: 0.8;
}

.summary-data {
  flex-grow: 1;
}

.summary-value {
  font-size: 2rem;
  font-weight: 700;
  margin-bottom: 0.25rem;
}

.summary-label {
  font-size: 0.9rem;
  opacity: 0.8;
  margin-bottom: 0;
}

.nav-container {
  background-color: #fff;
  border-radius: 0.75rem;
  padding: 0.75rem;
  box-shadow: 0 0.15rem 1.75rem 0 rgba(58, 59, 69, 0.1);
}

.metric-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
  padding-bottom: 0.5rem;
  border-bottom: 1px solid rgba(227, 230, 240, 0.5);
  transition: background-color 0.15s;
  padding: 0.5rem 0;
}

.metric-row:hover {
  background-color: rgba(248, 249, 252, 0.7);
}

.metric-row:last-child {
  margin-bottom: 0;
  padding-bottom: 0;
  border-bottom: none;
}

.metric-label {
  color: #5a5c69;
  font-weight: 600;
  font-size: 0.95rem;
}

.metric-value {
  font-weight: 600;
  color: #4e73df;
  text-align: right;
}

.progress {
  height: 8px;
  border-radius: 4px;
  background-color: rgba(227, 230, 240, 0.6);
  margin: 0.35rem 0;
}

.progress-bar {
  border-radius: 4px;
  transition: width 0.6s ease;
}

.timeline {
  position: relative;
  margin: 0 auto;
  padding-left: 2rem;
}

.timeline:before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  height: 100%;
  width: 3px;
  background: linear-gradient(to bottom, #4e73df 0%, #6284e8 100%);
  border-radius: 3px;
}

.timeline-item {
  position: relative;
  margin-bottom: 2rem;
  padding-bottom: 0.5rem;
  transition: transform 0.2s;
}

.timeline-item:hover {
  transform: translateX(5px);
}

.timeline-item:last-child {
  margin-bottom: 0;
  padding-bottom: 0;
}

.timeline-marker {
  position: absolute;
  left: -2rem;
  top: 0.25rem;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #4e73df;
  box-shadow: 0 0 0 4px rgba(78, 115, 223, 0.2);
  z-index: 1;
  transition: transform 0.2s, box-shadow 0.2s;
}

.timeline-item:hover .timeline-marker {
  transform: scale(1.2);
  box-shadow: 0 0 0 6px rgba(78, 115, 223, 0.3);
}

.timeline-content h6 {
  margin-bottom: 0.25rem;
  font-weight: 600;
  color: #4e73df;
}

.timeline-content p {
  margin-bottom: 0;
  color: #6c757d;
  font-size: 0.9rem;
}

.time-distribution-chart {
  position: relative;
  margin: auto;
  height: 250px;
}

.memory-chart-container {
  position: relative;
  height: 250px;
}

.memory-gauges {
  display: flex;
  flex-wrap: wrap;
  gap: 1.5rem;
}

.memory-gauge {
  flex: 1;
  min-width: 200px;
  transition: transform 0.2s;
}

.memory-gauge:hover {
  transform: translateY(-5px);
}

.gauge-title {
  font-weight: 600;
  margin-bottom: 0.75rem;
  color: #4e73df;
  font-size: 1rem;
}

.gauge-chart {
  margin-bottom: 1rem;
  background-color: #f8f9fc;
  padding: 1rem;
  border-radius: 0.5rem;
  box-shadow: 0 0.1rem 0.5rem rgba(0, 0, 0, 0.05);
}

.gauge-value {
  font-weight: 700;
  font-size: 1.25rem;
  margin-bottom: 0.5rem;
  text-align: right;
  color: #343a40;
}

.gauge-details {
  font-size: 0.85rem;
  color: #6c757d;
  text-align: right;
  margin-top: 0.5rem;
}

.bytecode-metric-card {
  background-color: #f8f9fc;
  border-radius: 0.75rem;
  padding: 1.5rem;
  position: relative;
  overflow: hidden;
  height: 100%;
  transition: transform 0.2s, box-shadow 0.2s;
  box-shadow: 0 0.15rem 0.5rem 0 rgba(58, 59, 69, 0.05);
}

.bytecode-metric-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 0.5rem 1rem 0 rgba(58, 59, 69, 0.1);
}

.class-stat-card {
  background-color: #f8f9fc;
  border-radius: 0.75rem;
  padding: 1.5rem;
  position: relative;
  overflow: hidden;
  text-align: center;
  height: 100%;
  transition: transform 0.2s, box-shadow 0.2s;
  box-shadow: 0 0.15rem 0.5rem 0 rgba(58, 59, 69, 0.05);
}

.class-stat-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 0.5rem 1rem 0 rgba(58, 59, 69, 0.1);
}

.class-stat-card .stat-name {
  font-weight: 600;
  color: #5a5c69;
  margin-bottom: 0.5rem;
}

.class-stat-card .stat-value {
  font-size: 2rem;
  font-weight: 700;
  color: #f6c23e;
}

.class-stat-card .stat-icon {
  margin-top: 0.5rem;
  font-size: 1.5rem;
  color: #f6c23e;
  opacity: 0.5;
}

.sync-stat-card {
  background-color: #f8f9fc;
  border-radius: 0.75rem;
  padding: 1.5rem;
  position: relative;
  overflow: hidden;
  text-align: center;
  height: 100%;
  transition: transform 0.2s, box-shadow 0.2s;
  box-shadow: 0 0.15rem 0.5rem 0 rgba(58, 59, 69, 0.05);
}

.sync-stat-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 0.5rem 1rem 0 rgba(58, 59, 69, 0.1);
}

.sync-stat-card .stat-name {
  font-weight: 600;
  color: #5a5c69;
  margin-bottom: 0.5rem;
}

.sync-stat-card .stat-value {
  font-size: 2rem;
  font-weight: 700;
  color: #e74a3b;
}

.sync-stat-card .stat-icon {
  margin-top: 0.5rem;
  font-size: 1.5rem;
  color: #e74a3b;
  opacity: 0.5;
}

.vm-args {
  display: block;
  font-size: 0.75rem;
  white-space: normal;
  word-break: break-word;
}

@media (max-width: 768px) {
  .performance-analysis-title {
    font-size: 1.5rem;
  }

  .summary-value {
    font-size: 1.5rem;
  }

  .nav-container .nav-link {
    font-size: 0.85rem;
    padding: 0.5rem;
  }
}
</style>

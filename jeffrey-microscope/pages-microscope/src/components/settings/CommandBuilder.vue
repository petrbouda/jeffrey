<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     https://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
  -->

<template>
  <div class="command-builder pt-3">
    <!-- Builder and Live Command Layout -->
    <div class="builder-and-command-layout">
      <!-- Configuration Builder Panel -->
      <div class="configuration-section builder-panel">
        <!-- Builder Mode Content -->
        <div class="config-output-content builder-mode-content">
          <form class="parameter-panel" @submit.prevent="generateConfig">
            <!-- Required Configuration Section -->
            <div class="config-section required-section">
              <div class="section-header">
                <h6 class="section-title">Mandatory Options</h6>
              </div>
              <div class="config-cards-stack">
                <!-- Async-profiler library Card -->
                <ConfigCard
                  title="Async-profiler Library"
                  subtitle="Which build of async-profiler the command runs on"
                  icon="bi-box-seam"
                  card-type="required"
                  :is-enabled="true"
                  color-theme="yellow"
                  :collapsible="true"
                  :is-expanded="mandatoryPanelsExpanded.library"
                  @toggle-collapse="
                    mandatoryPanelsExpanded.library = !mandatoryPanelsExpanded.library
                  "
                >
                  <div class="interval-block">
                    <ChoiceTiles
                      v-model="config.profilerSource"
                      :options="profilerSourceOptions"
                      group-label="Async-profiler library"
                    />
                  </div>
                  <div v-if="config.profilerSource === 'custom'" class="interval-block">
                    <label class="interval-label" for="profilerPath">Profiler Path</label>
                    <input
                      id="profilerPath"
                      v-model="config.agentPathCustom"
                      type="text"
                      class="form-control library-path-input"
                      :placeholder="DEFAULT_AGENT_PATH"
                    />
                    <div class="form-help">
                      Path to <code>libasyncProfiler.so</code> on the machine you will profile. It
                      goes into the command as <code>-agentpath:&lt;path&gt;=</code>.
                    </div>
                  </div>
                </ConfigCard>

                <!-- Output File Pattern Card -->
                <ConfigCard
                  title="Output File Pattern"
                  subtitle="Where AsyncProfiler writes generated profiles"
                  icon="bi-file-earmark-code"
                  card-type="required"
                  :is-enabled="true"
                  color-theme="yellow"
                  :collapsible="true"
                  :is-expanded="mandatoryPanelsExpanded.outputFile"
                  @toggle-collapse="
                    mandatoryPanelsExpanded.outputFile = !mandatoryPanelsExpanded.outputFile
                  "
                >
                  <div class="form-group">
                    <input
                      v-model="config.file"
                      type="text"
                      class="form-control"
                      placeholder="<<JEFFREY:CURRENT_SESSION>>/profile-%t.jfr"
                      required
                    />
                    <div class="form-help">
                      Output file pattern (%t = timestamp, %p = PID, %n{MAX} = sequence number,
                      %{ENV} - environment variable)
                    </div>
                  </div>
                </ConfigCard>

                <!-- Loop Duration Card -->
                <ConfigCard
                  title="Loop Duration"
                  subtitle="Used for continuous profiling, it generates files in a regular time interval"
                  icon="bi-arrow-repeat"
                  card-type="required"
                  :is-enabled="true"
                  color-theme="yellow"
                  :collapsible="true"
                  :is-expanded="mandatoryPanelsExpanded.loopDuration"
                  @toggle-collapse="
                    mandatoryPanelsExpanded.loopDuration = !mandatoryPanelsExpanded.loopDuration
                  "
                >
                  <div class="form-group">
                    <div class="input-group">
                      <input
                        v-model="config.loopValue"
                        type="number"
                        class="form-control"
                        min="1"
                        placeholder="15"
                        required
                      />
                      <select v-model="config.loopUnit" class="form-select select-with-indicator">
                        <option value="s">Seconds</option>
                        <option value="m">Minutes</option>
                        <option value="h">Hours</option>
                        <option value="d">Days</option>
                      </select>
                    </div>
                    <div class="form-help">
                      Specifies time when the current JFR file is dumped and starts writing to a new
                      one. (default 15 minutes)
                    </div>
                  </div>
                </ConfigCard>
              </div>
            </div>

            <!-- Event Options Section -->
            <div class="config-section optional-section">
              <div class="section-header">
                <h6 class="section-title">Event Options</h6>
              </div>

              <div class="config-cards-stack">
                <!-- Event Type Card -->
                <ConfigCard
                  title="CPU Profiling"
                  subtitle="Find hotpaths where the application spends time on CPU"
                  icon="bi-activity"
                  :is-enabled="optionStates.event"
                  @toggle="optionStates.event = $event"
                >
                  <div class="form-group">
                    <select v-model="config.event" class="form-control select-with-indicator">
                      <option value="ctimer">ctimer</option>
                      <option value="cpu">cpu</option>
                    </select>
                    <div class="form-help">Select a CPU profiling mode.</div>
                    <div class="event-extra-hint">
                      <span class="hint-label">ctimer</span>
                      <span class="hint-text"
                        >CPU profiling without kernel stacks. Use when perf_events are unavailable
                        (e.g. in containers).</span
                      >
                    </div>
                    <div class="event-extra-hint">
                      <span class="hint-label">cpu</span>
                      <span class="hint-text"
                        >CPU profiling with kernel stacks via perf_events on Linux (requires it to
                        be enabled). Falls back to other sampling modes depending on the OS.</span
                      >
                    </div>
                    <div class="interval-block">
                      <label class="interval-label">Sampling Interval</label>
                      <div class="input-group">
                        <input
                          v-model.number="config.intervalValue"
                          type="number"
                          class="form-control"
                          placeholder="10"
                        />
                        <select
                          v-model="config.intervalUnit"
                          class="form-select select-with-indicator"
                        >
                          <option
                            v-for="option in SAMPLING_INTERVAL_UNITS"
                            :key="option.unit"
                            :value="option.unit"
                          >
                            {{ option.label }}
                          </option>
                        </select>
                      </div>
                      <div class="form-help">
                        Default is 10 ms. Controls how frequently samples are collected for the
                        selected CPU mode.
                      </div>
                    </div>
                  </div>
                </ConfigCard>

                <!-- Allocation Profiling Card -->
                <ConfigCard
                  title="Allocation Profiling"
                  subtitle="Find where objects are allocated in the heap"
                  icon="bi-box-seam"
                  :is-enabled="optionStates.alloc"
                  @toggle="optionStates.alloc = $event"
                >
                  <div class="interval-block">
                    <label class="interval-label">Sampling Allocation Threshold</label>
                    <div class="input-group">
                      <input
                        v-model.number="config.allocValue"
                        type="number"
                        class="form-control"
                        placeholder="2"
                      />
                      <select v-model="config.allocUnit" class="form-select select-with-indicator">
                        <option
                          v-for="option in BYTE_INTERVAL_UNITS"
                          :key="option.unit"
                          :value="option.unit"
                        >
                          {{ option.label }}
                        </option>
                      </select>
                    </div>
                    <div class="form-help">
                      TLAB-driven sampling that receives notifications when objects are allocated in
                      new TLABs or via slow paths outside TLAB. Adjusting the threshold means taking
                      a sample after specified amount of allocated space on average.
                    </div>
                  </div>
                </ConfigCard>

                <!-- Lock Profiling Card -->
                <ConfigCard
                  title="Lock Profiling"
                  subtitle="Capture contended monitors and locks"
                  icon="bi-shield-lock"
                  :is-enabled="optionStates.lock"
                  @toggle="optionStates.lock = $event"
                >
                  <div class="interval-block">
                    <div class="threshold-label-row">
                      <label class="interval-label" for="lockThresholdValue">Wait Threshold</label>
                      <ThresholdBadge
                        :state="lockThreshold(config.lockThresholdValue, config.lockThresholdUnit)"
                      />
                    </div>
                    <div class="input-group">
                      <input
                        id="lockThresholdValue"
                        v-model.number="config.lockThresholdValue"
                        type="number"
                        class="form-control"
                        min="0"
                        placeholder="every contention"
                      />
                      <select
                        v-model="config.lockThresholdUnit"
                        class="form-select select-with-indicator"
                      >
                        <option
                          v-for="option in DURATION_THRESHOLD_UNITS"
                          :key="option.unit"
                          :value="option.unit"
                        >
                          {{ option.label }}
                        </option>
                      </select>
                    </div>
                    <div class="form-help">
                      Only contentions that waited at least this long are recorded. Starts at 10 µs,
                      async-profiler's own default. 0 or empty records every contention, which can
                      flood the recording on a busy service.
                    </div>
                  </div>
                </ConfigCard>

                <!-- Wall Clock Card -->
                <ConfigCard
                  title="Wall-Clock Profiling"
                  subtitle="Find where the application spends time including I/O and waits"
                  icon="bi-clock"
                  :is-enabled="optionStates.wall"
                  @toggle="optionStates.wall = $event"
                >
                  <div class="interval-block">
                    <label class="interval-label">Sampling Interval</label>
                    <div class="input-group">
                      <input
                        v-model.number="config.wallValue"
                        type="number"
                        class="form-control"
                        placeholder="10"
                      />
                      <select v-model="config.wallUnit" class="form-select select-with-indicator">
                        <option
                          v-for="option in DURATION_THRESHOLD_UNITS"
                          :key="option.unit"
                          :value="option.unit"
                        >
                          {{ option.label }}
                        </option>
                      </select>
                    </div>
                    <div class="form-help">
                      Default is 10 ms. Controls how frequently wall clock samples are collected.
                    </div>
                  </div>
                </ConfigCard>

                <!-- Method Tracing Card -->
                <ConfigCard
                  title="Method Tracing"
                  subtitle="Record calls to specific Java methods, optionally only the slow ones"
                  icon="bi-search"
                  :is-enabled="optionStates.methodTracing"
                  @toggle="optionStates.methodTracing = $event"
                >
                  <div class="interval-block">
                    <!-- Traced methods, each with its own latency threshold -->
                    <div class="interval-block">
                      <label class="interval-label">Traced Methods</label>
                      <div v-if="config.methodTraces.length > 0">
                        <div
                          v-for="(target, index) in config.methodTraces"
                          :key="target.id"
                          class="method-pattern-item"
                        >
                          <div class="pattern-display">
                            <div class="pattern-value">{{ target.pattern }}</div>
                            <div class="pattern-meta">
                              <ThresholdBadge :state="traceThreshold(target)" />
                              <span class="pattern-preview">{{ traceOption(target) }}</span>
                            </div>
                          </div>
                          <div class="trace-threshold">
                            <span class="trace-threshold-sign">≥</span>
                            <input
                              v-model.number="target.latencyValue"
                              type="number"
                              class="form-control"
                              min="0"
                              placeholder="every call"
                              :aria-label="`Latency threshold for ${target.pattern}`"
                            />
                            <select
                              v-model="target.latencyUnit"
                              class="form-select select-with-indicator"
                              :aria-label="`Latency unit for ${target.pattern}`"
                            >
                              <option
                                v-for="option in DURATION_THRESHOLD_UNITS"
                                :key="option.unit"
                                :value="option.unit"
                              >
                                {{ option.label }}
                              </option>
                            </select>
                          </div>
                          <button
                            type="button"
                            class="btn-remove-pattern"
                            title="Remove Method"
                            @click="removeMethodTrace(index)"
                          >
                            <i class="bi bi-x-lg"></i>
                          </button>
                        </div>
                      </div>
                      <div v-else class="no-patterns-message">
                        <i class="bi bi-info-circle"></i>
                        <span>No methods traced yet. Add a method pattern below.</span>
                      </div>
                      <div class="form-help">
                        Calls faster than the threshold are dropped. Clear it to record every call,
                        which can flood the recording on a hot method.
                      </div>
                    </div>

                    <!-- Add New Method -->
                    <div class="interval-block">
                      <label class="interval-label" for="newMethodPattern">Add Method</label>
                      <div class="input-group">
                        <input
                          id="newMethodPattern"
                          v-model="newMethodPattern"
                          type="text"
                          class="form-control"
                          placeholder="com.example.OrderService.place"
                          :aria-invalid="newMethodError !== null"
                          @keyup.enter="addPattern"
                        />
                        <button
                          type="button"
                          class="btn-add-pattern form-select"
                          :disabled="!newMethodPattern.trim()"
                          @click="addPattern"
                        >
                          <i class="bi bi-plus-circle"></i>
                          Add
                        </button>
                      </div>
                      <div v-if="newMethodError !== null" class="field-error" role="alert">
                        <i class="bi bi-exclamation-circle"></i>
                        <span>{{ newMethodError }}</span>
                      </div>
                      <div class="form-help">
                        New methods record every call; set a threshold on the row to keep only slow
                        ones.
                      </div>
                      <div class="form-help">Methods: java.lang.Thread.*, *.&lt;init&gt;</div>
                      <div class="form-help">All in a package: cafe.jeffrey.hub.core.grpc.*.*</div>
                      <div class="form-help">
                        One overload: java.lang.String.indexOf(Ljava/lang/String;)I
                      </div>
                    </div>
                  </div>
                </ConfigCard>

                <!-- Native Memory Card -->
                <ConfigCard
                  title="Native Memory Profiling"
                  subtitle="Profile malloc/free and mmap calls"
                  icon="bi-memory"
                  :is-enabled="optionStates.nativeMem"
                  @toggle="optionStates.nativeMem = $event"
                >
                  <div class="interval-block">
                    <div class="threshold-label-row">
                      <label class="interval-label" for="nativeMemValue">Sampling Interval</label>
                      <ThresholdBadge
                        :state="nativeMemThreshold(config.nativeMemValue, config.nativeMemUnit)"
                      />
                    </div>
                    <div class="input-group">
                      <input
                        id="nativeMemValue"
                        v-model.number="config.nativeMemValue"
                        type="number"
                        class="form-control"
                        min="0"
                        placeholder="every malloc"
                      />
                      <select
                        v-model="config.nativeMemUnit"
                        class="form-select select-with-indicator"
                      >
                        <option
                          v-for="option in BYTE_INTERVAL_UNITS"
                          :key="option.unit"
                          :value="option.unit"
                        >
                          {{ option.label }}
                        </option>
                      </select>
                    </div>
                    <div class="form-help">
                      One sample per this many bytes allocated. Clear the field to record every
                      malloc and free, which is the most expensive thing async-profiler can do.
                    </div>

                    <div class="interval-block">
                      <div class="form-check">
                        <input
                          id="omitFree"
                          v-model="config.nativeMemOmitFree"
                          class="form-check-input"
                          type="checkbox"
                        />
                        <label class="form-check-label" for="omitFree"> Omit free() events </label>
                      </div>
                      <div class="form-help">
                        Only record allocation events, skip free() calls for better performance.
                      </div>
                    </div>
                  </div>
                </ConfigCard>
              </div>
            </div>

            <!-- Advanced Options Section -->
            <div class="config-section optional-section">
              <div class="section-header">
                <h6 class="section-title">Advanced Options</h6>
              </div>

              <div class="config-cards-stack">
                <!-- JFR Sync Card -->
                <ConfigCard
                  title="JFR Synchronization"
                  subtitle="Merge AsyncProfiler events with JFR recording"
                  icon="bi-arrow-down-up"
                  :is-enabled="optionStates.jfrsync"
                  data-jfr-sync-card
                  @toggle="optionStates.jfrsync = $event"
                >
                  <div class="interval-block">
                    <label class="interval-label">Predefined JFC modes</label>
                    <div class="agent-mode-selector">
                      <div class="form-check">
                        <input
                          id="jfcDefault"
                          v-model="config.jfcMode"
                          class="form-check-input"
                          type="radio"
                          name="jfcMode"
                          value="default"
                        />
                        <label class="form-check-label" for="jfcDefault"> default </label>
                      </div>
                      <div class="form-check">
                        <input
                          id="jfcProfile"
                          v-model="config.jfcMode"
                          class="form-check-input"
                          type="radio"
                          name="jfcMode"
                          value="profile"
                        />
                        <label class="form-check-label" for="jfcProfile"> profile </label>
                      </div>
                      <div class="form-check">
                        <input
                          id="jfcCustom"
                          v-model="config.jfcMode"
                          class="form-check-input"
                          type="radio"
                          name="jfcMode"
                          value="custom"
                        />
                        <label class="form-check-label custom-option-label" for="jfcCustom">
                          custom configuration JFC file
                        </label>
                      </div>
                    </div>
                    <div class="form-help">
                      Select predefined JFC (Java Flight Recorder Configuration) modes or use custom
                      path.
                    </div>
                  </div>

                  <div v-if="config.jfcMode === 'custom'" class="interval-block">
                    <label class="interval-label">JFR Custom Configuration Path</label>
                    <input
                      v-model="config.jfrsyncFile"
                      type="text"
                      class="form-control"
                      placeholder="path/to/recording.jfc"
                    />
                    <div class="form-help">
                      Synchronizes JFR from Async-Profiler with JFR from JDK.
                    </div>
                  </div>
                </ConfigCard>

                <!-- Chunk Size Card -->
                <ConfigCard
                  title="Chunk Size"
                  subtitle="Maximum size of each JFR chunk file"
                  icon="bi-file-binary"
                  :is-enabled="optionStates.chunksize"
                  data-chunk-size-card
                  @toggle="optionStates.chunksize = $event"
                >
                  <div class="interval-block">
                    <div class="input-group">
                      <input
                        v-model.number="config.chunksizeValue"
                        type="number"
                        class="form-control"
                        min="1"
                        placeholder="5"
                      />
                      <select
                        v-model="config.chunksizeUnit"
                        class="form-select select-with-indicator"
                      >
                        <option value="k">kB</option>
                        <option value="m">MB</option>
                        <option value="g">GB</option>
                      </select>
                    </div>
                    <div class="form-help">
                      Default of Async-Profiler is 100 MB. A new chunk starts after the specified
                      size.
                    </div>
                  </div>
                </ConfigCard>

                <!-- Chunk Time Card -->
                <ConfigCard
                  title="Chunk Time"
                  subtitle="Maximum duration of each JFR chunk"
                  icon="bi-clock-history"
                  :is-enabled="optionStates.chunktime"
                  @toggle="optionStates.chunktime = $event"
                >
                  <div class="interval-block">
                    <div class="input-group">
                      <input
                        v-model.number="config.chunktimeValue"
                        type="number"
                        class="form-control"
                        min="1"
                        placeholder="1"
                      />
                      <select
                        v-model="config.chunktimeUnit"
                        class="form-select select-with-indicator"
                      >
                        <option value="s">Seconds</option>
                        <option value="m">Minutes</option>
                        <option value="h">Hours</option>
                      </select>
                    </div>
                    <div class="form-help">
                      Default is 1 hour. A new chunk starts after the specified time.
                    </div>
                  </div>
                </ConfigCard>
              </div>
            </div>
          </form>
        </div>
      </div>

      <!-- Live Command Panel -->
      <div class="configuration-section live-command-panel">
        <div class="step-header">
          <div class="step-header-status header-secondary">
            <div class="step-type-info">
              <i class="bi bi-terminal-fill"></i>
              <span>LIVE COMMAND</span>
            </div>
          </div>
          <div class="step-header-content">
            <div class="token-summary">
              <span class="token-summary-title">Active parameters</span>
              <div class="token-chip-group">
                <span v-for="token in builderTokens" :key="token.key" class="token-chip">
                  <span class="token-chip-label">{{ token.label }}</span>
                  <code class="token-chip-value">{{ token.value }}</code>
                </span>
              </div>
            </div>
            <div class="command-format">
              <SegmentedSwitch
                v-model="commandFormat"
                :options="commandFormatOptions"
                group-label="Copy as"
              />
              <span class="command-format-hint">{{ commandFormatHint }}</span>
            </div>
            <div class="config-output-content compact-output" @click="copyToClipboard">
              <code class="config-output-text">{{
                generatedConfig || 'No configuration generated yet.'
              }}</code>
            </div>

            <!-- Builder Actions -->
            <div class="builder-actions">
              <button
                type="button"
                class="btn btn-primary-gradient btn-copy-command"
                :disabled="!generatedConfig"
                @click="copyToClipboard"
              >
                <i class="bi bi-clipboard"></i>
                Copy command
              </button>
            </div>
          </div>
        </div>

        <!-- Chunk Size Warning Panel -->
        <div
          v-if="shouldShowChunkSizeWarning"
          class="warning-panel clickable-warning"
          @click="enableChunkSizeConfiguration()"
        >
          <div class="step-header-status header-warning">
            <div class="step-type-info">
              <i class="bi bi-exclamation-triangle-fill"></i>
              <span>RECOMMENDATION</span>
              <span class="header-description">CHUNK SIZE NOT CONFIGURED</span>
            </div>
            <div class="configure-icon">
              <i class="bi bi-gear-fill"></i>
            </div>
          </div>
          <div class="warning-content">
            <div class="warning-message">
              <span class="warning-text"
                ><span class="fw-bold">Chunk Size</span> helps with parallelization of JFR
                processing with multiple threads</span
              >
            </div>
          </div>
        </div>

        <!-- JFR Sync Warning Panel -->
        <div
          v-if="shouldShowJfrSyncWarning"
          class="warning-panel clickable-warning"
          @click="enableJfrSyncConfiguration()"
        >
          <div class="step-header-status header-warning">
            <div class="step-type-info">
              <i class="bi bi-exclamation-triangle-fill"></i>
              <span>RECOMMENDATION</span>
              <span class="header-description">JFR SYNCHRONIZATION NOT CONFIGURED</span>
            </div>
            <div class="configure-icon">
              <i class="bi bi-gear-fill"></i>
            </div>
          </div>
          <div class="warning-content">
            <div class="warning-message">
              <span class="warning-text"
                ><span class="fw-bold">JFR Synchronization</span> merges AsyncProfiler events with
                JDK's JFR recording for richer profiling data</span
              >
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import ConfigCard from '@/components/settings/ConfigCard.vue';
import ThresholdBadge from '@/components/settings/ThresholdBadge.vue';
import {
  lockThreshold,
  nativeMemThreshold,
  traceThreshold
} from '@/components/settings/thresholdState';
import { useProfilerConfig } from '@/composables/useProfilerConfig';
import { traceOption } from '@/composables/profilerOptions';
import {
  BYTE_INTERVAL_UNITS,
  DURATION_THRESHOLD_UNITS,
  SAMPLING_INTERVAL_UNITS
} from '@/composables/profilerUnits';
import { DEFAULT_AGENT_PATH } from '@/types/profiler';
import type { ProfilerSource } from '@/types/profiler';
import { formatCommand } from '@/composables/profilerCommandFormat';
import type { CommandFormat } from '@/composables/profilerCommandFormat';
import ChoiceTiles from '@shared/components/ChoiceTiles.vue';
import type { ChoiceTileOption } from '@shared/components/ChoiceTiles.vue';
import SegmentedSwitch from '@shared/components/SegmentedSwitch.vue';
import type { SegmentedOption } from '@shared/components/SegmentedSwitch.vue';
import ToastService from '@shared/services/ToastService';

// Mandatory panels collapse state (collapsed by default since defaults should be used in most cases)
const mandatoryPanelsExpanded = ref({
  library: true,
  outputFile: false,
  loopDuration: false
});

// Use the profiler configuration composable
const {
  config,
  optionStates,
  builderTokens,
  generateFromBuilder,
  addMethodTrace,
  removeMethodTrace
} = useProfilerConfig();

// New method trace input; its latency threshold is tuned on the row once added
const newMethodPattern = ref('');

// Why the last pattern was refused; cleared as soon as the pattern is edited
const newMethodError = ref<string | null>(null);

watch(newMethodPattern, () => {
  newMethodError.value = null;
});

const profilerSourceOptions: ChoiceTileOption<ProfilerSource>[] = [
  {
    id: 'jeffrey-jib',
    label: 'Jeffrey JIB',
    description: 'Baked into the image by Jeffrey JIB. Nothing else to configure.',
    icon: 'box-seam',
    badge: 'Recommended'
  },
  {
    id: 'custom',
    label: 'Custom profiler',
    description: 'Your own libasyncProfiler.so, named in the command as \u2011agentpath.',
    icon: 'folder2-open',
    tone: 'warning'
  }
];

// How the Live Command panel hands the command over
const commandFormat = ref<CommandFormat>('env');

const isJeffreyJib = computed((): boolean => config.value.profilerSource === 'jeffrey-jib');

const commandFormatOptions = computed((): SegmentedOption<CommandFormat>[] => [
  { id: 'env', label: 'ENV var' },
  { id: 'hocon', label: 'HOCON' },
  { id: 'raw', label: isJeffreyJib.value ? 'Options' : 'JVM argument' }
]);

const COMMAND_FORMAT_HINTS: Record<CommandFormat, string> = {
  env: 'Set on the pod, wins over the configuration file',
  hocon: 'Paste into the provisioner configuration file',
  raw: ''
};

const commandFormatHint = computed((): string => {
  if (commandFormat.value !== 'raw') {
    return COMMAND_FORMAT_HINTS[commandFormat.value];
  }
  return isJeffreyJib.value
    ? 'Options only, run on the Async-profiler Jeffrey JIB baked in'
    : 'Paste into the JVM arguments of an application you start yourself';
});

// Generated configuration
const generatedConfig = ref('');

// Watch for changes and auto-generate
watch(
  [config, optionStates, commandFormat],
  () => {
    generateConfig();
  },
  { deep: true }
);

// Generate configuration
const generateConfig = () => {
  generatedConfig.value = formatCommand(generateFromBuilder(), commandFormat.value);
};

// Add new method pattern, or show why async-profiler would reject it
const addPattern = () => {
  const pattern = newMethodPattern.value.trim();
  if (!pattern) {
    return;
  }
  const result = addMethodTrace(pattern);
  if (result.added) {
    newMethodPattern.value = '';
  } else {
    newMethodError.value = result.error;
  }
};

// Copy to clipboard
const copyToClipboard = async () => {
  if (generatedConfig.value) {
    try {
      await navigator.clipboard.writeText(generatedConfig.value);
      ToastService.success('Copied to Clipboard', 'Command has been copied to your clipboard.');
    } catch (error) {
      console.error('Failed to copy to clipboard:', error);
      ToastService.error('Copy Failed', 'Failed to copy command to clipboard.');
    }
  }
};

// Check if chunk size warning should be shown
const shouldShowChunkSizeWarning = computed(() => {
  return !optionStates.value.chunksize;
});

// Check if JFR sync warning should be shown
const shouldShowJfrSyncWarning = computed(() => {
  return !optionStates.value.jfrsync;
});

// Enable chunk size configuration. By default, scrolls the chunk-size card
// into view; pass `{ scroll: false }` to skip the scroll (used when the
// recommendation is clicked from an inline preview that's already on screen).
const enableChunkSizeConfiguration = (options: { scroll?: boolean } = {}) => {
  optionStates.value.chunksize = true;

  if (!config.value.chunksizeValue) {
    config.value.chunksizeValue = 100;
    config.value.chunksizeUnit = 'm'; // MB
  }

  if (options.scroll === false) {
    return;
  }

  setTimeout(() => {
    const chunkSizeCard = document.querySelector('[data-chunk-size-card]');
    if (chunkSizeCard) {
      chunkSizeCard.scrollIntoView({
        behavior: 'smooth',
        block: 'center'
      });
    }
  }, 100);
};

const enableJfrSyncConfiguration = (options: { scroll?: boolean } = {}) => {
  optionStates.value.jfrsync = true;

  if (!config.value.jfcMode) {
    config.value.jfcMode = 'default';
  }

  if (options.scroll === false) {
    return;
  }

  setTimeout(() => {
    const jfrSyncCard = document.querySelector('[data-jfr-sync-card]');
    if (jfrSyncCard) {
      jfrSyncCard.scrollIntoView({
        behavior: 'smooth',
        block: 'center'
      });
    }
  }, 100);
};

// Initialize configuration generation
generateConfig();
</script>

<style scoped>
@import '@shared/styles/form-utilities.css';
@import '@shared/styles/shared-components.css';

.command-builder {
  width: 100%;
}

.step-header-status {
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 12px;
  font-size: 10px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: white;
  transition: all 0.2s ease;
}

.header-warning {
  background: linear-gradient(135deg, var(--color-amber), var(--color-amber-highlight));
}

.step-type-info {
  display: flex;
  align-items: center;
  gap: 4px;
}

.step-type-info i {
  font-size: 10px;
}

.header-description {
  font-weight: 400;
  font-style: italic;
  opacity: 0.8;
  font-size: 9px;
  margin-left: 8px;
  letter-spacing: 0.3px;
}

/* Custom JFC Mode Option Styling */
.custom-option-label {
  font-style: italic;
  font-weight: normal;
  opacity: 0.8;
}

/* Builder and Live Command Layout */
.builder-and-command-layout {
  display: flex;
  gap: 24px;
}

.configuration-section {
  flex: 1;
}

.builder-panel {
  flex: 2;
  /* Without this a long generated command stretches the flex child instead of wrapping. */
  min-width: 0;
}

.live-command-panel {
  flex: 1;
  position: sticky;
  top: 20px;
  align-self: flex-start;
  min-width: 0;
}

/* Configuration Section Styling */
.config-section {
  margin-bottom: 32px;
}

.section-header {
  margin-bottom: 20px;
}

.section-title {
  font-size: 0.8rem;
  font-weight: 700;
  color: var(--color-text-muted);
  margin: 0;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  display: flex;
  align-items: center;
  gap: 8px;
}

.section-title::before {
  content: '';
  width: 3px;
  height: 14px;
  background: linear-gradient(135deg, var(--color-primary), var(--color-primary-hover));
  border-radius: 2px;
}

.config-cards-stack {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* Form Styling */
.form-group {
  margin: 0;
}

.form-help {
  font-size: 0.75rem;
  color: var(--color-text-muted);
  margin-top: 6px;
  font-style: italic;
}

.interval-label {
  font-size: 0.78rem;
  font-weight: 600;
  color: var(--color-indigo-text);
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.interval-block {
  margin-top: 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  transition: opacity 0.2s ease;
}

.interval-block:first-child {
  margin-top: 0;
}

.interval-block.disabled {
  opacity: 0.6;
}

.interval-block.disabled .form-control,
.interval-block.disabled .form-select {
  background-color: rgba(241, 245, 249, 0.7);
  cursor: not-allowed;
}

/* Event Hint Styling */
.event-extra-hint {
  margin-top: 10px;
  padding: 10px 14px;
  border-radius: 8px;
  background: linear-gradient(135deg, rgba(94, 100, 255, 0.12), rgba(94, 100, 255, 0.04));
  border: 1px solid rgba(94, 100, 255, 0.18);
  font-size: 0.8rem;
  color: var(--color-text);
  display: flex;
  gap: 8px;
  align-items: flex-start;
}

.event-extra-hint .hint-label {
  font-weight: 700;
  text-transform: uppercase;
  font-size: 0.7rem;
  letter-spacing: 0.05em;
  color: var(--color-indigo-text);
}

.event-extra-hint .hint-text {
  flex: 1;
  color: var(--color-text);
  line-height: 1.4;
}

/* Agent Mode Selector Styling */
.agent-mode-selector {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 16px;
  background: rgba(241, 245, 249, 0.6);
  border-radius: 8px;
  border: 1px solid rgba(2, 132, 199, 0.2);
}

.agent-mode-selector .form-check {
  margin-bottom: 0;
}

.agent-mode-selector .form-check-label {
  font-size: 0.875rem;
  font-weight: 500;
  color: var(--color-text);
  cursor: pointer;
}

.agent-mode-selector .form-check-input:checked + .form-check-label {
  color: var(--color-info-text);
  font-weight: 600;
}

/* Method Pattern List Styling */
.no-patterns-message {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: rgba(94, 100, 255, 0.03);
  border: 1px dashed rgba(94, 100, 255, 0.15);
  border-radius: 8px;
  color: var(--color-text-muted);
  font-size: 0.85rem;
  font-style: italic;
}

.no-patterns-message i {
  color: rgba(94, 100, 255, 0.6);
  font-size: 0.9rem;
}

.method-pattern-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  padding: 10px 14px;
  margin-bottom: 8px;
  background: linear-gradient(135deg, rgba(94, 100, 255, 0.05), rgba(94, 100, 255, 0.02));
  border: 1px solid rgba(94, 100, 255, 0.12);
  border-radius: 8px;
  transition: all 0.2s ease;
}

.method-pattern-item:hover {
  border-color: rgba(94, 100, 255, 0.2);
}

/*
 * The pattern keeps a readable width: below it the row wraps and the threshold controls move to
 * their own line, instead of the pattern shrinking to a few characters per line.
 */
.pattern-display {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1 1 16rem;
  min-width: 0;
  overflow-wrap: anywhere;
}

.pattern-value {
  font-weight: 600;
  color: var(--color-text);
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 0.9rem;
}

.pattern-preview {
  font-size: 0.75rem;
  color: var(--color-text-muted);
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-style: italic;
}

.btn-remove-pattern {
  background: none;
  border: none;
  color: var(--color-danger);
  padding: 4px 8px;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s ease;
  font-size: 1.1rem;
  line-height: 1;
}

.btn-remove-pattern:hover {
  background: rgba(239, 68, 68, 0.1);
  color: var(--color-danger-hover);
}

/* Threshold label + the badge stating what the threshold records */
.threshold-label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.pattern-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.trace-threshold {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.trace-threshold .form-control {
  width: 5rem;
}

.trace-threshold .form-select {
  width: 4.5rem;
}

.trace-threshold-sign {
  color: var(--color-text-muted);
  font-size: 0.85rem;
}

/* Why the typed method pattern was refused, spaced like the help lines under it */
.field-error {
  margin-top: 6px;
}

.btn-add-pattern {
  white-space: nowrap;
  font-weight: 600;
  min-width: 80px;
  text-align: center;
  cursor: pointer;
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.btn-add-pattern:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* The custom library path reads as a path */
.library-path-input {
  font-family: var(--font-family-monospace);
  font-size: 0.8rem;
}

/* Step Header Styling - Matching HubsView */
.step-header {
  background: var(--color-white);
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid var(--color-border);
  box-shadow:
    0 1px 3px rgba(0, 0, 0, 0.1),
    0 1px 2px rgba(0, 0, 0, 0.06);
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  margin-bottom: 24px;
}

.header-secondary {
  background: linear-gradient(135deg, var(--color-success), var(--color-success-hover));
}

.step-header-content {
  padding: 20px 24px;
}

/* Token Chip Styling */
.token-summary {
  margin-bottom: 16px;
}

/* The copy format switch and the one line saying where that format goes */
.command-format {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-2);
  margin-bottom: var(--spacing-3);
}

.command-format-hint {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}

.token-summary-title {
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--color-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: 8px;
  display: block;
}

.token-chip-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.token-chip {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 10px;
  border: 1px dashed rgba(94, 100, 255, 0.2);
  background: rgba(248, 250, 252, 0.85);
  font-size: 0.8rem;
  color: var(--color-text);
  transition:
    border-color 0.15s ease,
    transform 0.15s ease,
    box-shadow 0.15s ease;
  cursor: default;
}

.token-chip:hover {
  border-color: rgba(94, 100, 255, 0.35);
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(94, 100, 255, 0.12);
}

.token-chip-label {
  font-weight: 600;
  text-transform: uppercase;
  font-size: 0.7rem;
  letter-spacing: 0.06em;
  color: var(--color-primary);
  flex-shrink: 0;
}

.token-chip-value {
  background: rgba(94, 100, 255, 0.12);
  padding: 4px 8px;
  border-radius: 6px;
  font-size: 0.76rem;
  color: var(--color-dark);
  overflow-wrap: break-word;
  word-break: break-all;
  min-width: 0;
}

.token-chip[title] {
  cursor: help;
}

/* Configuration Output Styling */
.config-output-content {
}

.compact-output {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.compact-output .config-output-text {
  margin-top: 0;
  border-radius: 10px;
  padding: 12px 14px;
  background: rgba(245, 158, 11, 0.08);
  border: 1px dashed rgba(245, 158, 11, 0.25);
  font-size: 0.8rem;
  line-height: 1.5;
  color: var(--color-dark);
  transition:
    border-color 0.15s ease,
    transform 0.15s ease,
    box-shadow 0.15s ease;
  cursor: pointer;
}

.compact-output .config-output-text:hover {
  border-color: rgba(245, 158, 11, 0.4);
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(245, 158, 11, 0.15);
}

.config-output-text {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 0.8rem;
  color: var(--color-text);
  line-height: 1.6;
  word-break: break-all;
  white-space: pre-wrap;
  display: block;
  margin: 0;
  padding: 0;
  border: none;
}

/* Builder Actions */
.builder-actions {
  display: flex;
  padding-top: 12px;
}

.btn-copy-command {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  padding: 10px 16px;
  font-size: 0.85rem;
  color: var(--color-white);
  cursor: pointer;
}

/* Responsive Design */
@media (max-width: 992px) {
  .builder-and-command-layout {
    flex-direction: column;
  }

  .builder-panel {
    flex: 1 1 auto;
  }

  .live-command-panel {
    flex: 1 1 auto;
  }
}

@media (max-width: 600px) {
  .token-chip {
    padding: 10px 12px;
    font-size: 0.75rem;
  }

  .token-chip-value {
    font-size: 0.7rem;
  }
}

/* Warning Panel Styles */
.warning-panel {
  margin-top: 16px;
  background: var(--color-white);
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid var(--color-border);
  box-shadow:
    0 1px 3px rgba(0, 0, 0, 0.1),
    0 1px 2px rgba(0, 0, 0, 0.06);
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.clickable-warning {
  cursor: pointer;
  user-select: none;
}

.clickable-warning:hover {
  border-color: rgba(245, 158, 11, 0.3);
  box-shadow: 0 2px 8px rgba(245, 158, 11, 0.15);
  transform: translateY(-1px);
}

.clickable-warning:hover .step-header-status {
  background: linear-gradient(135deg, var(--color-amber-highlight), var(--color-amber-darkest));
}

.clickable-warning:hover .configure-icon {
  transform: rotate(15deg);
}

.warning-panel .step-header-status {
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 12px;
  font-size: 9px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: white;
}

.configure-icon {
  display: flex;
  align-items: center;
  font-size: 10px;
  transition: transform 0.2s ease;
  opacity: 0.8;
}

.warning-content {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
}

.warning-message {
  flex: 1;
}

.warning-text {
  color: var(--color-text-muted);
  margin: 0;
  font-size: 0.8rem;
  line-height: 1.4;
}
</style>

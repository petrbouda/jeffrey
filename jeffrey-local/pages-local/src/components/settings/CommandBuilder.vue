<template>
  <div class="command-builder pt-4">
    <div class="step-header">
      <div class="step-header-status header-primary clickable-header" @click="toggleHelp">
        <div class="step-type-info">
          <i class="bi bi-ui-checks-grid"></i>
          <span>ASYNC-PROFILER AGENT BUILDER</span>
          <span class="header-description">Open to get more information about Async-Profiler Agent Configuration</span>
        </div>
        <div class="help-toggle">
          <i :class="isHelpExpanded ? 'bi bi-chevron-up' : 'bi bi-chevron-down'"></i>
        </div>
      </div>

      <!-- Collapsible Help Section -->
      <div v-if="isHelpExpanded" class="step-header-content">
        <div class="help-content">
          <h4>AsyncProfiler Documentation & Resources</h4>
          <p>Learn more about AsyncProfiler configuration and profiling techniques from the official documentation:</p>

          <div class="help-sections">
            <a href="https://github.com/async-profiler/async-profiler/blob/master/docs/ProfilingModes.md" target="_blank" class="help-section help-link-card clickable-card">
              <h5><i class="bi bi-book"></i> Profiling Modes</h5>
              <p>Information about profiling modes</p>
              <div class="source-type">
                <i class="bi bi-file-text"></i>
                <span>Documentation</span>
              </div>
            </a>

            <a href="https://github.com/async-profiler/async-profiler/blob/master/docs/ProfilerOptions.md" target="_blank" class="help-section help-link-card clickable-card">
              <h5><i class="bi bi-gear"></i> Profiler Options</h5>
              <p>Information about Profiling Options</p>
              <div class="source-type">
                <i class="bi bi-file-text"></i>
                <span>Documentation</span>
              </div>
            </a>

            <a href="https://github.com/async-profiler/async-profiler/pull/1435" target="_blank" class="help-section help-link-card clickable-card">
              <h5><i class="bi bi-search"></i> Method Tracing Instrumentation</h5>
              <p>Information about Instrumentation for Method Tracing</p>
              <div class="source-type">
                <i class="bi bi-git"></i>
                <span>Pull Request</span>
              </div>
            </a>

            <a href="https://github.com/async-profiler/async-profiler/discussions/1497" target="_blank" class="help-section help-link-card clickable-card">
              <h5><i class="bi bi-chat-dots"></i> Method Tracing Blog</h5>
              <p>Blog about Method Tracing</p>
              <div class="source-type">
                <i class="bi bi-chat-square-text"></i>
                <span>Discussion</span>
              </div>
            </a>
          </div>
        </div>
      </div>
    </div>

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
                <h6 class="section-title">
                  Mandatory Options
                </h6>
              </div>
              <div class="config-cards-stack">
                <!-- Agent Path Card -->
                <ConfigCard
                    title="Agent Path"
                    subtitle="Path to the AsyncProfiler shared library"
                    icon="bi-folder-fill"
                    card-type="required"
                    :is-enabled="true"
                    color-theme="blue"
                    :collapsible="true"
                    :is-expanded="mandatoryPanelsExpanded.agentPath"
                    @toggle-collapse="mandatoryPanelsExpanded.agentPath = !mandatoryPanelsExpanded.agentPath"
                >
                  <div class="interval-block">
                    <div class="agent-mode-selector">
                      <div class="form-check">
                        <input class="form-check-input" type="radio" name="agentMode" id="agentJeffrey" value="jeffrey" v-model="agentMode">
                        <label class="form-check-label" for="agentJeffrey">
                          Use Agent provided by Jeffrey
                        </label>
                      </div>
                      <div class="form-check">
                        <input class="form-check-input" type="radio" name="agentMode" id="agentCustom" value="custom" v-model="agentMode">
                        <label class="form-check-label" for="agentCustom">
                          Specify Custom Agent Path
                        </label>
                      </div>
                    </div>

                    <div v-if="agentMode === 'jeffrey'">
                      <div class="form-help">Using AsyncProfiler agent embedded in Jeffrey.</div>
                    </div>

                    <div v-if="agentMode === 'custom'">
                      <input
                          type="text"
                          class="form-control"
                          v-model="config.agentPathCustom"
                          placeholder="/path/to/libasyncProfiler.so"
                      >
                      <div class="form-help">Path to your custom AsyncProfiler shared library (.so file).</div>
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
                    @toggle-collapse="mandatoryPanelsExpanded.outputFile = !mandatoryPanelsExpanded.outputFile"
                >
                  <div class="form-group">
                    <input
                        type="text"
                        class="form-control"
                        v-model="config.file"
                        placeholder="<<JEFFREY_CURRENT_SESSION>>/profile-%t.jfr"
                        required
                    >
                    <div class="form-help">Output file pattern (%t = timestamp, %p = PID, %n{MAX} = sequence number, %{ENV} - environment variable)</div>
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
                    @toggle-collapse="mandatoryPanelsExpanded.loopDuration = !mandatoryPanelsExpanded.loopDuration"
                >
                  <div class="form-group">
                    <div class="input-group">
                      <input
                          type="number"
                          class="form-control"
                          v-model="config.loopValue"
                          min="1"
                          placeholder="15"
                          required
                      >
                      <select class="form-select select-with-indicator" v-model="config.loopUnit">
                        <option value="s">Seconds</option>
                        <option value="m">Minutes</option>
                        <option value="h">Hours</option>
                        <option value="d">Days</option>
                      </select>
                    </div>
                    <div class="form-help">Specifies time when the current JFR file is dumped and starts writing to a new one.  (default 15 minutes)</div>
                  </div>
                </ConfigCard>
              </div>
            </div>

            <!-- Event Options Section -->
            <div class="config-section optional-section">
              <div class="section-header">
                <h6 class="section-title">
                  Event Options
                </h6>
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
                    <select class="form-control select-with-indicator" v-model="config.event">
                      <option value="ctimer">ctimer</option>
                      <option value="cpu">cpu</option>
                    </select>
                    <div class="form-help">Select a CPU profiling mode.</div>
                    <div class="event-extra-hint">
                      <span class="hint-label">ctimer</span>
                      <span class="hint-text">CPU profiling without kernel stacks. Use when perf_events are unavailable (e.g. in containers).</span>
                    </div>
                    <div class="event-extra-hint">
                      <span class="hint-label">cpu</span>
                      <span class="hint-text">CPU profiling with kernel stacks via perf_events on Linux (requires it to be enabled). Falls back to other sampling modes depending on the OS.</span>
                    </div>
                    <div class="interval-block">
                      <label class="interval-label">Sampling Interval</label>
                      <div class="input-group">
                        <input
                            type="number"
                            class="form-control"
                            v-model.number="config.intervalValue"
                            placeholder="10"
                        >
                        <select class="form-select select-with-indicator" v-model="config.intervalUnit">
                          <option value="us">Micros</option>
                          <option value="ms">Millis</option>
                        </select>
                      </div>
                      <div class="form-help">Default is 10 ms. Controls how frequently samples are collected for
                        the selected CPU mode.
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
                          type="number"
                          class="form-control"
                          v-model.number="config.allocValue"
                          placeholder="2"
                      >
                      <select class="form-select select-with-indicator" v-model="config.allocUnit">
                        <option value="mb">MB</option>
                        <option value="kb">kB</option>
                      </select>
                    </div>
                    <div class="form-help">TLAB-driven sampling that receives notifications when objects are
                      allocated in new TLABs or via slow paths outside TLAB. Adjusting the threshold means
                      taking a sample after specified amount of allocated space on average.
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
                    <label class="interval-label">Sampling Threshold</label>
                    <div class="input-group">
                      <input
                          type="number"
                          class="form-control"
                          v-model.number="config.lockThresholdValue"
                          placeholder="0">
                      <select class="form-select select-with-indicator" v-model="config.lockThresholdUnit">
                        <option value="us">Micros</option>
                        <option value="ms">Millis</option>
                        <option value="s">Seconds</option>
                      </select>
                    </div>
                    <div class="form-help">Set a wait threshold, locks shorter than the threshold are ignored (captures all if empty).
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
                          type="number"
                          class="form-control"
                          v-model.number="config.wallValue"
                          placeholder="10"
                      >
                      <select class="form-select select-with-indicator" v-model="config.wallUnit">
                        <option value="us">Micros</option>
                        <option value="ms">Millis</option>
                        <option value="s">Seconds</option>
                      </select>
                    </div>
                    <div class="form-help">Default is 10 ms. Controls how frequently wall clock samples are
                      collected.
                    </div>
                  </div>
                </ConfigCard>

                <!-- Method Tracing Card -->
                <ConfigCard
                    title="Method Tracing"
                    subtitle="Trace specific Java methods and JVM methods"
                    icon="bi-search"
                    :is-enabled="optionStates.methodTracing"
                    @toggle="optionStates.methodTracing = $event"
                >
                  <div class="interval-block">
                    <!-- Existing Patterns List -->
                    <div class="interval-block">
                      <label class="interval-label">Active Method Patterns</label>
                      <div v-if="config.methodPatterns.length > 0">
                        <div v-for="(pattern, index) in config.methodPatterns" :key="index" class="method-pattern-item">
                          <div class="pattern-display">
                            <div class="pattern-value">{{ pattern }}</div>
                            <div class="pattern-preview">{{ `trace=${pattern}` }}</div>
                          </div>
                          <button
                            type="button"
                            class="btn-remove-pattern"
                            @click="removeMethodPattern(index)"
                            title="Remove Pattern"
                          >
                            <i class="bi bi-x-lg"></i>
                          </button>
                        </div>
                      </div>
                      <div v-else class="no-patterns-message">
                        <i class="bi bi-info-circle"></i>
                        <span>No method patterns defined. Add patterns to trace specific methods.</span>
                      </div>
                    </div>

                    <!-- Add New Pattern -->
                    <div class="interval-block">
                      <label class="interval-label">Add Method Pattern</label>
                      <div class="input-group">
                        <input
                            type="text"
                            class="form-control"
                            v-model="newMethodPattern"
                            @keyup.enter="addPattern"
                        >
                        <button
                          type="button"
                          class="btn-add-pattern form-select"
                          @click="addPattern"
                          :disabled="!newMethodPattern.trim()"
                        >
                          <i class="bi bi-plus-circle"></i>
                          Add
                        </button>
                      </div>
                      <div class="form-help">Java Methods: java.lang.Thread.*, *.&lt;init&gt;</div>
                      <div class="form-help">Native Methods: Java_java_lang_Throwable_fillInStackTrace</div>
                      <div class="form-help">JVM Methods: G1CollectedHeap::humongous_obj_allocate, JVM_StartThread</div>
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
                    <label class="interval-label">Sampling Threshold</label>
                    <div class="input-group">
                      <input
                          type="number"
                          class="form-control"
                          v-model.number="config.nativeMemValue"
                          placeholder="512"
                      >
                      <select class="form-select select-with-indicator" v-model="config.nativeMemUnit">
                        <option value="mb">MB</option>
                        <option value="kb">kB</option>
                      </select>
                    </div>
                    <div class="form-help">Minimum allocation size to profile. Use 0 for all allocations.</div>

                    <div class="interval-block">
                      <div class="form-check">
                        <input class="form-check-input" type="checkbox" id="omitFree" v-model="config.nativeMemOmitFree">
                        <label class="form-check-label" for="omitFree">
                          Omit free() events
                        </label>
                      </div>
                      <div class="form-help">Only record allocation events, skip free() calls for better performance.</div>
                    </div>
                  </div>
                </ConfigCard>
              </div>
            </div>

            <!-- Advanced Options Section -->
            <div class="config-section optional-section">
              <div class="section-header">
                <h6 class="section-title">
                  Advanced Options
                </h6>
              </div>

              <div class="config-cards-stack">
                <!-- JFR Sync Card -->
                <ConfigCard
                    title="JFR Synchronization"
                    subtitle="Merge AsyncProfiler events with JFR recording"
                    icon="bi-arrow-down-up"
                    :is-enabled="optionStates.jfrsync"
                    @toggle="optionStates.jfrsync = $event"
                    data-jfr-sync-card
                >
                  <div class="interval-block">
                    <label class="interval-label">Predefined JFC modes</label>
                    <div class="agent-mode-selector">
                      <div class="form-check">
                        <input class="form-check-input" type="radio" name="jfcMode" id="jfcDefault" value="default" v-model="config.jfcMode">
                        <label class="form-check-label" for="jfcDefault">
                          default
                        </label>
                      </div>
                      <div class="form-check">
                        <input class="form-check-input" type="radio" name="jfcMode" id="jfcProfile" value="profile" v-model="config.jfcMode">
                        <label class="form-check-label" for="jfcProfile">
                          profile
                        </label>
                      </div>
                      <div class="form-check">
                        <input class="form-check-input" type="radio" name="jfcMode" id="jfcCustom" value="custom" v-model="config.jfcMode">
                        <label class="form-check-label custom-option-label" for="jfcCustom">
                          custom configuration JFC file
                        </label>
                      </div>
                    </div>
                    <div class="form-help">Select predefined JFC (Java Flight Recorder Configuration) modes or use custom path.</div>
                  </div>

                  <div v-if="config.jfcMode === 'custom'" class="interval-block">
                    <label class="interval-label">JFR Custom Configuration Path</label>
                    <input
                        type="text"
                        class="form-control"
                        v-model="config.jfrsyncFile"
                        placeholder="path/to/recording.jfc"
                    >
                    <div class="form-help">Synchronizes JFR from Async-Profiler with JFR from JDK.</div>
                  </div>
                </ConfigCard>

                <!-- Chunk Size Card -->
                <ConfigCard
                    title="Chunk Size"
                    subtitle="Maximum size of each JFR chunk file"
                    icon="bi-file-binary"
                    :is-enabled="optionStates.chunksize"
                    @toggle="optionStates.chunksize = $event"
                    data-chunk-size-card
                >
                  <div class="interval-block">
                    <div class="input-group">
                      <input
                          type="number"
                          class="form-control"
                          v-model.number="config.chunksizeValue"
                          min="1"
                          placeholder="5"
                      >
                      <select class="form-select select-with-indicator" v-model="config.chunksizeUnit">
                        <option value="k">kB</option>
                        <option value="m">MB</option>
                        <option value="g">GB</option>
                      </select>
                    </div>
                    <div class="form-help">Default of Async-Profiler is 100 MB. A new chunk starts after the specified size.</div>
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
                          type="number"
                          class="form-control"
                          v-model.number="config.chunktimeValue"
                          min="1"
                          placeholder="1"
                      >
                      <select class="form-select select-with-indicator" v-model="config.chunktimeUnit">
                        <option value="s">Seconds</option>
                        <option value="m">Minutes</option>
                        <option value="h">Hours</option>
                      </select>
                    </div>
                    <div class="form-help">Default is 1 hour. A new chunk starts after the specified time.</div>
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
                <span
                    v-for="token in builderTokens"
                    :key="token.key"
                    class="token-chip"
                >
                  <span class="token-chip-label">{{ token.label }}</span>
                  <code class="token-chip-value">{{ token.value }}</code>
                </span>
              </div>
            </div>
            <div class="config-output-content compact-output" @click="copyToClipboard">
              <code class="config-output-text">{{ generatedConfig || 'No configuration generated yet.' }}</code>
            </div>

            <!-- Builder Actions -->
            <div class="builder-actions">
              <button
                type="button"
                class="btn-cancel-builder"
                @click="$emit('cancel')"
              >
                <i class="bi bi-x-circle"></i>
                Cancel
              </button>
              <button
                type="button"
                class="btn-accept-command"
                @click="acceptCommand"
              >
                <i class="bi bi-check-circle"></i>
                Accept Command
              </button>
            </div>
          </div>
        </div>

        <!-- Chunk Size Warning Panel -->
        <div
          v-if="shouldShowChunkSizeWarning"
          class="warning-panel clickable-warning"
          @click="enableChunkSizeConfiguration"
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
              <span class="warning-text"><span class="font-bold">Chunk Size</span> helps with parallelization of JFR processing with multiple threads</span>
            </div>
          </div>
        </div>

        <!-- JFR Sync Warning Panel -->
        <div
          v-if="shouldShowJfrSyncWarning"
          class="warning-panel clickable-warning"
          @click="enableJfrSyncConfiguration"
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
              <span class="warning-text"><span class="font-bold">JFR Synchronization</span> merges AsyncProfiler events with JDK's JFR recording for richer profiling data</span>
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
import { useProfilerConfig } from '@/composables/useProfilerConfig';
import ToastService from '@/services/ToastService';

interface Props {
  agentMode?: 'jeffrey' | 'custom';
}

const props = withDefaults(defineProps<Props>(), {
  agentMode: 'jeffrey'
});

const emit = defineEmits<{
  cancel: [];
  'accept-command': [command: string];
}>();

// Local agent mode state
const agentMode = ref(props.agentMode);

// Help section state
const isHelpExpanded = ref(false);

// Mandatory panels collapse state (collapsed by default since defaults should be used in most cases)
const mandatoryPanelsExpanded = ref({
  agentPath: false,
  outputFile: false,
  loopDuration: false
});

// Use the profiler configuration composable
const { config, optionStates, builderTokens, generateFromBuilder, addMethodPattern, removeMethodPattern } = useProfilerConfig();

// New pattern input
const newMethodPattern = ref('');

// Generated configuration
const generatedConfig = ref('');

// Watch for changes and auto-generate
watch([config, optionStates], () => {
  generateConfig();
}, { deep: true });

// Generate configuration
const generateConfig = () => {
  generatedConfig.value = generateFromBuilder();
};

// Add new method pattern
const addPattern = () => {
  if (newMethodPattern.value.trim()) {
    addMethodPattern(newMethodPattern.value.trim());
    newMethodPattern.value = '';
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

// Accept command
const acceptCommand = () => {
  const command = generateFromBuilder();
  emit('accept-command', command);
};

// Toggle help section
const toggleHelp = () => {
  isHelpExpanded.value = !isHelpExpanded.value;
};

// Check if chunk size warning should be shown
const shouldShowChunkSizeWarning = computed(() => {
  return !optionStates.value.chunksize;
});

// Check if JFR sync warning should be shown
const shouldShowJfrSyncWarning = computed(() => {
  return !optionStates.value.jfrsync;
});

// Enable chunk size configuration and scroll to it
const enableChunkSizeConfiguration = () => {
  // Enable chunk size option
  optionStates.value.chunksize = true;

  // Set a reasonable default value if not set
  if (!config.value.chunksizeValue) {
    config.value.chunksizeValue = 100;
    config.value.chunksizeUnit = 'm'; // MB
  }

  // Scroll to chunk size configuration after DOM update
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

// Enable JFR sync configuration and scroll to it
const enableJfrSyncConfiguration = () => {
  // Enable JFR sync option
  optionStates.value.jfrsync = true;

  // Set default JFC mode if not set
  if (!config.value.jfcMode) {
    config.value.jfcMode = 'default';
  }

  // Scroll to JFR sync configuration after DOM update
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
@import '@/styles/form-utilities.css';

.command-builder {
  width: 100%;
}

/* Step Header Styling - Matching ProjectsView */
.step-header {
  background: #ffffff;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid #e5e7eb;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1), 0 1px 2px rgba(0, 0, 0, 0.06);
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  margin-bottom: 24px;
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

.step-header-status.clickable-header {
  cursor: pointer;
  user-select: none;
}

.step-header-status.clickable-header:hover {
  background: linear-gradient(135deg, #4c52ff, #3f46ff);
  box-shadow: 0 2px 8px rgba(94, 100, 255, 0.3);
}


.header-primary {
  background: linear-gradient(135deg, #5e64ff, #4c52ff);
}

.header-secondary {
  background: linear-gradient(135deg, #10b981, #059669);
}

.header-tertiary {
  background: linear-gradient(135deg, #f59e0b, #d97706);
}

.header-warning {
  background: linear-gradient(135deg, #f59e0b, #d97706);
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

.help-toggle {
  display: flex;
  align-items: center;
  font-size: 12px;
  transition: transform 0.2s ease;
  pointer-events: none;
}

.help-toggle i {
  transition: transform 0.2s ease;
  font-size: 10px;
}

/* Help Content Styling */
.help-content {
  padding: 20px 0;
}

.help-content h4 {
  color: #1f2937;
  font-weight: 700;
  margin-bottom: 12px;
  font-size: 1.1rem;
}

.help-content p {
  color: #6b7280;
  margin-bottom: 16px;
  line-height: 1.5;
}

.help-sections {
  display: grid;
  gap: 16px;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
}

.help-section {
  background: rgba(255, 255, 255, 0.7);
  border: 1px solid rgba(94, 100, 255, 0.1);
  border-radius: 8px;
  padding: 16px;
  position: relative;
  padding-bottom: 40px;
}

.help-section h5 {
  color: #374151;
  font-weight: 600;
  margin-bottom: 8px;
  font-size: 0.9rem;
  display: flex;
  align-items: center;
  gap: 8px;
}

.help-section h5 i {
  color: #5e64ff;
}

.help-section p {
  color: #6b7280;
  font-size: 0.85rem;
  margin: 0;
  line-height: 1.4;
}

.help-section code {
  background: rgba(94, 100, 255, 0.1);
  color: #5e64ff;
  padding: 2px 4px;
  border-radius: 3px;
  font-size: 0.8rem;
}

/* Help Link Cards */
.help-link-card {
  transition: all 0.2s ease;
}

.clickable-card {
  text-decoration: none;
  color: inherit;
  cursor: pointer;
  display: block;
}

.clickable-card:hover {
  text-decoration: none;
  color: inherit;
  border-color: rgba(94, 100, 255, 0.25);
  box-shadow: 0 4px 12px rgba(94, 100, 255, 0.15);
}

.clickable-card:hover h5 {
  color: #5e64ff;
}

/* Source Type Indicator */
.source-type {
  position: absolute;
  bottom: 12px;
  left: 16px;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.75rem;
  color: #6b7280;
  font-weight: 500;
}

.source-type i {
  color: #94a3b8;
  font-size: 0.7rem;
}

/* Custom JFC Mode Option Styling */
.custom-option-label {
  font-style: italic;
  font-weight: normal;
  opacity: 0.8;
}

.help-hint-text {
  font-weight: 400;
  font-style: italic;
  opacity: 0.8;
  font-size: 9px;
  margin-left: 6px;
  letter-spacing: 0.3px;
}

.step-header-content {
  padding: 20px 24px;
}

.step-header-title-row {
  margin-bottom: 8px;
}

.help-panel-toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  background: linear-gradient(135deg, #f8faff, #f1f5ff);
  border: 1px solid rgba(94, 100, 255, 0.2);
  border-radius: 6px;
  color: #5e64ff;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  outline: none;
}

.help-panel-toggle:hover {
  background: linear-gradient(135deg, #5e64ff, #4c52ff);
  color: white;
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(94, 100, 255, 0.25);
}

.help-panel-toggle i {
  font-size: 0.8rem;
}

.step-header-title {
  font-size: 1.5rem;
  font-weight: 700;
  color: #1f2937;
  margin: 0 0 8px 0;
  letter-spacing: -0.02em;
}

.step-header-description {
  color: #6b7280;
  font-size: 0.9rem;
}

/* Help Section Styling */
.help-section {
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid #e5e7eb;
  animation: slideDown 0.3s ease-out;
}

@keyframes slideDown {
  from {
    opacity: 0;
    transform: translateY(-10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.help-content {
  background: linear-gradient(135deg, #f8faff, #f1f5ff);
  border: 1px solid rgba(94, 100, 255, 0.1);
  border-radius: 12px;
  padding: 24px;
}

.help-title {
  font-size: 1.1rem;
  font-weight: 700;
  color: #1f2937;
  margin: 0 0 16px 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.help-title i {
  color: #f59e0b;
  font-size: 1.2rem;
}

.help-text {
  color: #4b5563;
  line-height: 1.6;
}

.help-text p {
  margin-bottom: 20px;
  font-size: 0.9rem;
}

.help-categories {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}

.help-category {
  background: rgba(255, 255, 255, 0.7);
  border: 1px solid rgba(94, 100, 255, 0.1);
  border-radius: 8px;
  padding: 16px;
  transition: all 0.2s ease;
}

.help-category:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(94, 100, 255, 0.1);
}

.help-category h6 {
  font-size: 0.85rem;
  font-weight: 600;
  color: #1f2937;
  margin: 0 0 8px 0;
  display: flex;
  align-items: center;
  gap: 6px;
}

.help-category h6 i {
  color: #5e64ff;
  font-size: 0.9rem;
}

.help-category p {
  font-size: 0.8rem;
  color: #6b7280;
  margin: 0;
  line-height: 1.5;
}

.help-tips {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.help-tip {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 16px;
  background: rgba(16, 185, 129, 0.05);
  border: 1px solid rgba(16, 185, 129, 0.1);
  border-radius: 8px;
  font-size: 0.85rem;
}

.help-tip i {
  color: #10b981;
  font-size: 0.9rem;
  margin-top: 1px;
  flex-shrink: 0;
}

.help-tip span {
  color: #374151;
  line-height: 1.4;
}

/* Builder and Live Command Layout */
.builder-and-command-layout {
  display: flex;
  gap: 24px;
  margin-top: 24px;
}

.configuration-section {
  flex: 1;
}

.builder-panel {
  flex: 2;
}

.live-command-panel {
  flex: 1;
  position: sticky;
  top: 20px;
  align-self: flex-start;
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
  color: #6b7280;
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
  background: linear-gradient(135deg, #5e64ff, #4c52ff);
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
  color: #6b7280;
  margin-top: 6px;
  font-style: italic;
}

.interval-label {
  font-size: 0.78rem;
  font-weight: 600;
  color: #3949ab;
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
  color: #374151;
  display: flex;
  gap: 8px;
  align-items: flex-start;
}

.event-extra-hint .hint-label {
  font-weight: 700;
  text-transform: uppercase;
  font-size: 0.7rem;
  letter-spacing: 0.05em;
  color: #3949ab;
}

.event-extra-hint .hint-text {
  flex: 1;
  color: #454f63;
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
  color: #374151;
  cursor: pointer;
}

.agent-mode-selector .form-check-input:checked + .form-check-label {
  color: #0284c7;
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
  color: #6b7280;
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
  padding: 10px 14px;
  margin-bottom: 8px;
  background: linear-gradient(135deg, rgba(94, 100, 255, 0.05), rgba(94, 100, 255, 0.02));
  border: 1px solid rgba(94, 100, 255, 0.12);
  border-radius: 8px;
  transition: all 0.2s ease;
}

.method-pattern-item:hover {
  border-color: rgba(94, 100, 255, 0.2);
  transform: translateY(-1px);
}

.pattern-display {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
}

.pattern-value {
  font-weight: 600;
  color: #374151;
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  font-size: 0.9rem;
}

.pattern-preview {
  font-size: 0.75rem;
  color: #6b7280;
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  font-style: italic;
}

.btn-remove-pattern {
  background: none;
  border: none;
  color: #ef4444;
  padding: 4px 8px;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s ease;
  font-size: 1.1rem;
  line-height: 1;
}

.btn-remove-pattern:hover {
  background: rgba(239, 68, 68, 0.1);
  color: #dc2626;
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

/* Token Chip Styling */
.token-summary {
  margin-bottom: 16px;
}

.token-summary-title {
  font-size: 0.8rem;
  font-weight: 600;
  color: #6b7280;
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
  color: #475569;
  transition: border-color 0.15s ease, transform 0.15s ease, box-shadow 0.15s ease;
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
  color: #5e64ff;
}

.token-chip-value {
  background: rgba(94, 100, 255, 0.12);
  padding: 4px 8px;
  border-radius: 6px;
  font-size: 0.76rem;
  white-space: nowrap;
  color: #1f2937;
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
  color: #1f2937;
  transition: border-color 0.15s ease, transform 0.15s ease, box-shadow 0.15s ease;
  cursor: pointer;
}

.compact-output .config-output-text:hover {
  border-color: rgba(245, 158, 11, 0.4);
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(245, 158, 11, 0.15);
}

.config-output-text {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  font-size: 0.8rem;
  color: #374151;
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
  gap: 10px;
  justify-content: flex-end;
  padding-top: 12px;
}

.btn-cancel-builder,
.btn-accept-command {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  border-radius: 8px;
  font-size: 0.85rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  border: none;
}

.btn-cancel-builder {
  background: linear-gradient(135deg, #fef2f2, #fee2e2);
  border: 1px solid rgba(239, 68, 68, 0.3);
  color: #dc2626;
  font-size: 0.8rem;
  padding: 8px 14px;
  box-shadow: 0 1px 3px rgba(239, 68, 68, 0.1);
}

.btn-cancel-builder:hover {
  background: linear-gradient(135deg, #dc2626, #b91c1c);
  color: white;
  transform: translateY(-1px);
  box-shadow: 0 2px 6px rgba(220, 38, 38, 0.3);
}

.btn-accept-command {
  background: linear-gradient(135deg, #10b981, #047857);
  color: white;
  font-size: 0.8rem;
  padding: 8px 14px;
  box-shadow: 0 2px 6px rgba(16, 185, 129, 0.25);
  border: none;
}

.btn-accept-command:hover {
  background: linear-gradient(135deg, #047857, #065f46);
  transform: translateY(-1px);
  box-shadow: 0 3px 8px rgba(16, 185, 129, 0.35);
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

  .live-command-panel .config-output {
    position: static;
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
  background: #ffffff;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid #e5e7eb;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1), 0 1px 2px rgba(0, 0, 0, 0.06);
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
  background: linear-gradient(135deg, #d97706, #b45309);
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
  color: #6b7280;
  margin: 0;
  font-size: 0.8rem;
  line-height: 1.4;
}
</style>

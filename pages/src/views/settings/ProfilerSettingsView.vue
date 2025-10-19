<template>
  <div>
    <!-- Main Settings Card -->
    <div class="profiler-settings-main-card mb-4">
      <div class="profiler-settings-main-content">


        <!-- Configuration Card -->
        <div class="configuration-section">
          <div class="config-output">
            <div class="config-output-header">
              <span class="config-output-label">
                <i class="bi bi-gear me-2"></i>Configuration Options
              </span>
              <!-- Mode Tabs in Header -->
              <div class="mode-tabs">
                <button
                    type="button"
                    class="mode-tab"
                    :class="{ 'active': configMode === 'builder' }"
                    @click="configMode = 'builder'"
                >
                  <i class="bi bi-ui-checks"></i>
                  <span>Builder</span>
                </button>
                <button
                    type="button"
                    class="mode-tab"
                    :class="{ 'active': configMode === 'raw' }"
                    @click="configMode = 'raw'"
                >
                  <i class="bi bi-code-slash"></i>
                  <span>Raw</span>
                </button>
              </div>
            </div>

            <!-- Builder Mode Content -->
            <div v-if="configMode === 'builder'" class="config-output-content builder-mode-content">
              <div class="builder-layout">
                <form class="parameter-panel" @submit.prevent="generateConfig">

                  <!-- Required Configuration Section -->
                  <div class="config-section required-section">
                    <div class="section-header">
                      <h6 class="section-title">
                        <i class="bi bi-exclamation-circle me-2"></i>
                        Required Configuration
                      </h6>
                      <span class="section-subtitle">These values are always included in the profiler command</span>
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
                            <div class="form-help">Using AsyncProfiler agent provided by Jeffrey installation.</div>
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
                      >
                        <div class="form-group">
                          <input
                              type="text"
                              class="form-control"
                              v-model="config.file"
                              placeholder="%{JEFFREY_CURRENT_SESSION}/profile-%t.jfr"
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
                        <i class="bi bi-sliders me-2"></i>
                        Event Options
                      </h6>
                      <span class="section-subtitle">Enable sampling and profiling modes</span>
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
                          title="Wall Clock"
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
                                  <span class="pattern-value">{{ pattern }}</span>
                                  <span class="pattern-preview">→ trace={{ pattern }}</span>
                                </div>
                                <button
                                  type="button"
                                  class="btn-remove-pattern"
                                  @click="handleRemoveMethodPattern(index)"
                                  title="Remove pattern"
                                >
                                  <i class="bi bi-x"></i>
                                </button>
                              </div>
                            </div>
                            <div v-else class="no-patterns-message">
                              <i class="bi bi-info-circle"></i>
                              <span>No method patterns configured yet. Add patterns below to trace specific methods.</span>
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
                                  placeholder="java.lang.String.*"
                                  @keyup.enter="handleAddMethodPattern"
                              >
                              <button
                                type="button"
                                class="form-select btn-add-pattern"
                                @click="handleAddMethodPattern"
                                :disabled="!newMethodPattern.trim()"
                              >
                                <i class="bi bi-plus"></i>
                                Add
                              </button>
                            </div>
                            <div class="form-help">Specify method pattern to trace. Examples: 'java.lang.String.*' for all String methods, 'com.example.MyClass.myMethod' for specific method, *.&lt;init&gt; for all constructors</div>
                            <div class="form-help">Use a threshold for sampling the method: *.&lt;init&gt;:10ms (all constructors taking more than 10ms)</div>
                          </div>
                        </div>
                      </ConfigCard>

                      <!-- Native Memory Profiling Card -->
                      <ConfigCard
                          title="Native Memory Profiling"
                          subtitle="Track native memory allocations and deallocations"
                          icon="bi-memory"
                          :is-enabled="optionStates.nativeMem"
                          @toggle="optionStates.nativeMem = $event"
                      >
                        <div class="interval-block">
                          <label class="interval-label">Sampling Allocation Threshold</label>
                          <div class="input-group">
                            <input
                                type="number"
                                class="form-control"
                                v-model.number="config.nativeMemValue"
                                placeholder="2"
                            >
                            <select class="form-select select-with-indicator" v-model="config.nativeMemUnit">
                              <option value="mb">MB</option>
                              <option value="kb">kB</option>
                            </select>
                          </div>
                          <div class="form-help">Track native memory allocations. Adjusting the threshold means taking a sample after specified amount of allocated native memory on average.</div>
                        </div>

                        <div class="interval-block">
                          <div class="form-check">
                            <input
                                class="form-check-input"
                                type="checkbox"
                                id="nativeMemOmitFree"
                                v-model="config.nativeMemOmitFree"
                            >
                            <label class="form-check-label" for="nativeMemOmitFree">
                              Omit Free Events
                            </label>
                          </div>
                          <div class="form-help">When enabled, omits memory deallocation events from profiling output.</div>
                        </div>
                      </ConfigCard>

                      <!-- JFR Sync Card -->
                      <ConfigCard
                          title="JFR Sync"
                          subtitle="Define how AsyncProfiler coordinates with JFR"
                          icon="bi-arrow-down-up"
                          :is-enabled="optionStates.jfrsync"
                          @toggle="optionStates.jfrsync = $event"
                      >
                        <div class="interval-block">
                          <div class="jfr-mode-selector mb-3">
                            <div class="form-check">
                              <input class="form-check-input" type="radio" name="jfrMode" id="jfrPredefined" value="predefined" v-model="jfrMode">
                              <label class="form-check-label" for="jfrPredefined">
                                Predefined JFR Configuration
                              </label>
                            </div>
                            <div class="form-check">
                              <input class="form-check-input" type="radio" name="jfrMode" id="jfrCustom" value="custom" v-model="jfrMode">
                              <label class="form-check-label" for="jfrCustom">
                                Custom Configuration File
                              </label>
                            </div>
                          </div>

                          <div v-if="jfrMode === 'predefined'">
                            <select class="form-control select-with-indicator" v-model="config.jfrsync">
                              <option value="default">default</option>
                              <option value="profile">profile</option>
                            </select>
                            <div class="form-help">AsyncProfiler will enable all JFR events provided by the configuration and replace just event types provided by profiler itself.</div>
                          </div>

                          <div v-if="jfrMode === 'custom'">
                            <input
                                type="text"
                                class="form-control"
                                v-model="config.jfrsyncFile"
                                placeholder="/path/to/custom.jfc"
                            >
                            <div class="form-help">Path to a custom JFR configuration file (.jfc) for advanced JFR settings.</div>
                          </div>
                        </div>
                      </ConfigCard>

                    </div>
                  </div>

                  <!-- Advanced Options Section -->
                  <div class="config-section advanced-section">
                    <div class="section-header">
                      <h6 class="section-title">
                        <i class="bi bi-tools me-2"></i>
                        Advanced Options
                      </h6>
                      <span class="section-subtitle">Control chunk rotation and output behaviour</span>
                    </div>

                    <div class="config-cards-stack">
                      <!-- Chunk Size Card -->
                      <ConfigCard
                          title="Chunk Size"
                          subtitle="Approximate size limit per chunk (default 100MB)"
                          icon="bi-file-earmark-break"
                          :is-enabled="optionStates.chunksize"
                          @toggle="optionStates.chunksize = $event"
                      >
                        <div class="form-group">
                          <div class="input-group">
                            <input
                                type="number"
                                class="form-control"
                                v-model="config.chunksizeValue"
                                min="1"
                                placeholder="100"
                            >
                            <select class="form-select select-with-indicator" v-model="config.chunksizeUnit">
                              <option value="m">MB</option>
                            </select>
                          </div>
                          <div class="form-help">New chunk starts when size reaches the limit (default 100MB).</div>
                        </div>
                      </ConfigCard>

                      <!-- Chunk Time Card -->
                      <ConfigCard
                          title="Chunk Time"
                          subtitle="Approximate time limit per chunk (default 1 hour)"
                          icon="bi-hourglass-split"
                          :is-enabled="optionStates.chunktime"
                          @toggle="optionStates.chunktime = $event"
                      >
                        <div class="form-group">
                          <div class="input-group">
                            <input
                                type="number"
                                class="form-control"
                                v-model="config.chunktimeValue"
                                min="1"
                                placeholder="1"
                            >
                            <select class="form-select select-with-indicator" v-model="config.chunktimeUnit">
                              <option value="s">Seconds</option>
                              <option value="m">Minutes</option>
                              <option value="h">Hours</option>
                              <option value="d">Days</option>
                            </select>
                          </div>
                          <div class="form-help">Default is 1 hour. A new chunk starts after the specified time.</div>
                        </div>
                      </ConfigCard>
                    </div>
                  </div>
                </form>

                <aside class="preview-panel">
                  <div class="config-output preview-card sticky-preview">
                    <div class="config-output-header">
                      <span class="config-output-label">
                        <i class="bi bi-terminal me-2"></i>Live Command
                      </span>
                    </div>
                    <div v-if="configMode === 'builder'" class="token-summary">
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
                  </div>
                </aside>
              </div>
            </div>

            <!-- Raw Mode Content -->
            <div v-if="configMode === 'raw'" class="config-output-content compact-output">
              <div class="config-field full-width">
                <label for="rawConfig" class="config-label">
                  <i class="bi bi-terminal me-1"></i>Agent Configuration String
                </label>
                <textarea
                    class="config-textarea"
                    id="rawConfig"
                    rows="6"
                    v-model="rawConfig"
                    placeholder="-agentpath:/path/to/libasyncProfiler.so=start,event=ctimer,wall=10ms,loop=15m,chunksize=5m,jfrsync=default,file=%{JEFFREY_CURRENT_SESSION}/profile-%t.jfr"
                ></textarea>
                <div class="config-help">Enter the complete AsyncProfiler agent configuration string</div>
              </div>
            </div>
          </div>
        </div>

        <!-- Configuration Output -->
        <div v-if="configMode === 'raw'" class="config-output-section">
          <div class="config-output">
            <div class="config-output-header">
              <span class="config-output-label">
                <i class="bi bi-terminal me-2"></i>AsyncProfiler Command
              </span>
              <div class="config-output-actions">
                <button
                    type="button"
                    class="config-action-btn generate-btn-compact"
                    @click="generateConfig"
                    title="Generate configuration"
                >
                  <i class="bi bi-arrow-clockwise"></i>
                </button>
                <button
                    type="button"
                    class="config-action-btn save-btn-compact"
                    @click="saveConfiguration"
                    title="Save configuration"
                >
                  <i class="bi bi-floppy"></i>
                </button>
                <button
                    type="button"
                    class="config-action-btn copy-btn-compact"
                    @click="copyToClipboard"
                    :disabled="!generatedConfig"
                    title="Copy to clipboard"
                >
                  <i class="bi bi-clipboard"></i>
                </button>
              </div>
            </div>
            <div class="config-output-content compact-output" @click="copyToClipboard">
              <code class="config-output-text">{{ generatedConfig || 'No configuration generated yet.' }}</code>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {onMounted, ref, watch} from 'vue';
import ToastService from '@/services/ToastService';
import ConfigCard from '@/components/settings/ConfigCard.vue';
import {useProfilerConfig} from '@/composables/useProfilerConfig';

// Configuration mode
const configMode = ref<'builder' | 'raw'>('builder');

// JFR mode for mutual exclusion
const jfrMode = ref<'predefined' | 'custom'>('predefined');

// Agent mode for mutual exclusion
const agentMode = ref<'jeffrey' | 'custom'>('jeffrey');

// Use the composable for configuration management
const {config, optionStates, builderTokens, generateFromBuilder, addMethodPattern, removeMethodPattern} = useProfilerConfig();

// New pattern input
const newMethodPattern = ref('');


// Raw configuration
const rawConfig = ref('');

// Generated configuration
const generatedConfig = ref('');


// Generate configuration
const generateConfig = () => {
  if (configMode.value === 'builder') {
    generatedConfig.value = generateFromBuilder();
  } else {
    generatedConfig.value = rawConfig.value;
  }

  ToastService.success('Configuration Generated', 'AsyncProfiler configuration string has been generated successfully.');
};

// Method pattern management
const handleAddMethodPattern = () => {
  if (newMethodPattern.value && newMethodPattern.value.trim()) {
    addMethodPattern(newMethodPattern.value.trim());
    newMethodPattern.value = '';
  }
};

const handleRemoveMethodPattern = (index: number) => {
  removeMethodPattern(index);
};

// Auto-update generated command when inputs change
watch(config, () => {
  if (configMode.value === 'builder') {
    generatedConfig.value = generateFromBuilder();
  }
}, {deep: true});

watch(optionStates, () => {
  if (configMode.value === 'builder') {
    generatedConfig.value = generateFromBuilder();
  }
}, {deep: true});

watch(rawConfig, () => {
  if (configMode.value === 'raw') {
    generatedConfig.value = rawConfig.value;
  }
});

watch(configMode, mode => {
  if (mode === 'builder') {
    generatedConfig.value = generateFromBuilder();
  } else {
    generatedConfig.value = rawConfig.value;
  }
});

// Copy to clipboard
const copyToClipboard = async () => {
  if (!generatedConfig.value) return;

  try {
    await navigator.clipboard.writeText(generatedConfig.value);
    ToastService.success('Copied!', 'Configuration copied to clipboard.');
  } catch (error) {
    console.error('Failed to copy to clipboard:', error);
    ToastService.error('Copy Failed', 'Could not copy to clipboard.');
  }
};

// Save configuration
const saveConfiguration = () => {
  // Here you would typically save to localStorage, API, or file
  // For now, we'll just show a success message

  if (!generatedConfig.value) {
    ToastService.warn('No Configuration', 'Please generate a configuration first.');
    return;
  }

  // Save to localStorage as an example
  const configData = {
    mode: configMode.value,
    builderConfig: config.value,
    rawConfig: rawConfig.value,
    generated: generatedConfig.value,
    savedAt: new Date().toISOString()
  };

  localStorage.setItem('jeffrey-profiler-config', JSON.stringify(configData));

  ToastService.success('Configuration Saved', 'AsyncProfiler configuration has been saved successfully.');
};

// Auto-generate on component mount
onMounted(() => {
  generatedConfig.value = generateFromBuilder();
});
</script>

<style scoped>
@import '@/styles/form-utilities.css';
/* Modern Main Card Styling - Matching ProjectsView */
.profiler-settings-main-card {
  background: linear-gradient(135deg, #ffffff, #fafbff);
  border: 1px solid rgba(94, 100, 255, 0.08);
  border-radius: 16px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.04),
  0 1px 3px rgba(0, 0, 0, 0.02);
  backdrop-filter: blur(10px);
}

.profiler-settings-main-content {
  padding: 24px 28px;
}


/* Mode Tabs Styling */
.mode-tabs {
  display: flex;
  background: rgba(248, 250, 252, 0.8);
  border-radius: 6px;
  padding: 2px;
  gap: 2px;
  border: 1px solid rgba(203, 213, 225, 0.5);
}

.mode-tab {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  background: transparent;
  border: none;
  border-radius: 4px;
  font-size: 0.75rem;
  font-weight: 500;
  color: #64748b;
  cursor: pointer;
  transition: all 0.15s ease;
  white-space: nowrap;
}

.mode-tab i {
  font-size: 0.7rem;
  opacity: 0.7;
}

.mode-tab:hover {
  background: rgba(241, 245, 249, 0.8);
  color: #475569;
}

.mode-tab:hover i {
  opacity: 1;
}

.mode-tab.active {
  background: #ffffff;
  color: #334155;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
  font-weight: 600;
}

.mode-tab.active i {
  opacity: 1;
  color: #5e64ff;
}

/* Configuration Card Styling */
.configuration-section {
  margin-bottom: 24px;
}


/* Modern Configuration Design */
.config-section {
  margin-bottom: 0px;
}

.section-header {
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 2px solid rgba(94, 100, 255, 0.08);
}

.section-title {
  display: flex;
  align-items: center;
  color: #374151;
  font-weight: 700;
  font-size: 1rem;
  margin: 0 0 4px 0;
  text-transform: uppercase;
  letter-spacing: 0.02em;
}

.section-title i {
  color: #5e64ff;
  font-size: 1.1rem;
}

.section-subtitle {
  color: #6b7280;
  font-size: 0.875rem;
  font-weight: 400;
}


/* Component-specific styles not covered by utilities */


/* Builder Layout */
.builder-mode-content {
  padding: 24px 24px 28px;
}

.builder-layout {
  display: flex;
  flex-wrap: wrap;
  gap: 28px;
}

.parameter-panel {
  flex: 2 1 640px;
  display: flex;
  flex-direction: column;
  gap: 28px;
}

.parameter-panel .section-header {
  margin-bottom: 12px;
}

.preview-panel {
  flex: 1 1 320px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.preview-panel .config-output-content {
  cursor: pointer;
}

.sticky-preview {
  position: sticky;
  top: 24px;
}

/* Card stacking */
.config-cards-stack {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.token-summary {
  padding: 18px 20px 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.token-summary-title {
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: #475569;
}

.token-chip-group {
  display: flex;
  flex-direction: column;
  gap: 12px;
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

@media (max-width: 992px) {
  .builder-layout {
    flex-direction: column;
  }

  .parameter-panel {
    flex: 1 1 auto;
  }

  .preview-panel {
    position: static;
  }

  .sticky-preview {
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


/* JFR Mode Selector Styling */
.jfr-mode-selector {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 16px;
  background: rgba(248, 250, 252, 0.6);
  border-radius: 8px;
  border: 1px solid rgba(203, 213, 225, 0.4);
}

.jfr-mode-selector .form-check {
  margin-bottom: 0;
}

.jfr-mode-selector .form-check-label {
  font-size: 0.875rem;
  font-weight: 500;
  color: #374151;
  cursor: pointer;
}

.jfr-mode-selector .form-check-input:checked + .form-check-label {
  color: #5e64ff;
  font-weight: 600;
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

/* Jeffrey Agent Display */
.jeffrey-agent-display {
  padding: 12px 16px;
  background: rgba(2, 132, 199, 0.08);
  border: 1px solid rgba(2, 132, 199, 0.2);
  border-radius: 8px;
  margin-bottom: 8px;
}

.agent-parameter {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  font-size: 0.85rem;
  color: #0369a1;
  background: transparent;
  padding: 0;
  border: none;
  font-weight: 600;
}

/* Form Grid Layout */
.configuration-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
  align-items: start;
}

.config-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.config-field.full-width {
  grid-column: 1 / -1;
}

.config-field.half-width {
  grid-column: span 1;
}

/* Form Control Styling */
.config-label {
  display: flex;
  align-items: center;
  font-size: 0.875rem;
  font-weight: 600;
  color: #374151;
  margin-bottom: 4px;
}

.config-label i {
  color: #5e64ff;
  font-size: 0.8rem;
}

.config-input,
.config-select,
.config-textarea {
  padding: 10px 14px;
  border: 1px solid rgba(94, 100, 255, 0.12);
  border-radius: 8px;
  font-size: 0.875rem;
  background: linear-gradient(135deg, #ffffff, #fafbff);
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  color: #374151;
}

.config-input:focus,
.config-select:focus,
.config-textarea:focus {
  outline: none;
  border-color: rgba(94, 100, 255, 0.3);
  box-shadow: 0 0 0 3px rgba(94, 100, 255, 0.05);
  transform: translateY(-1px);
}

.config-textarea {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  resize: vertical;
  min-height: 120px;
}

/* Input Group Styling */
.config-input-group {
  display: flex;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid rgba(94, 100, 255, 0.12);
  background: linear-gradient(135deg, #ffffff, #fafbff);
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.config-input-group:focus-within {
  border-color: rgba(94, 100, 255, 0.3);
  box-shadow: 0 0 0 3px rgba(94, 100, 255, 0.05);
  transform: translateY(-1px);
}

.config-input-group-field {
  flex: 1;
  padding: 10px 14px;
  border: none;
  background: transparent;
  font-size: 0.875rem;
  color: #374151;
}

.config-input-group-field:focus {
  outline: none;
}

.config-input-group-select {
  width: 80px;
  padding: 10px 12px;
  border: none;
  border-left: 1px solid rgba(94, 100, 255, 0.12);
  background: rgba(94, 100, 255, 0.03);
  font-size: 0.875rem;
  color: #374151;
}

.config-input-group-select:focus {
  outline: none;
}

.config-help {
  font-size: 0.75rem;
  color: #6b7280;
  margin-top: 4px;
}

/* Configuration Output Styling */
.config-output-section {
  margin-top: 24px;
}

.config-output {
  background: linear-gradient(135deg, #ffffff, #fafbff);
  border: 1px solid rgba(94, 100, 255, 0.08);
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04),
  0 1px 2px rgba(0, 0, 0, 0.02);
  overflow: hidden;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.config-output-header {
  background: linear-gradient(135deg, #f8f9fa, #ffffff);
  border-bottom: 1px solid rgba(94, 100, 255, 0.08);
  padding: 16px 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.config-output-label {
  display: flex;
  align-items: center;
  color: #374151;
  font-size: 1rem;
  font-weight: 600;
  margin: 0;
}

.config-output-label i {
  color: #5e64ff;
  font-size: 1rem;
}

.config-output-actions {
  display: flex;
  gap: 6px;
}

.config-action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  background: rgba(248, 250, 252, 0.8);
  border: 1px solid rgba(203, 213, 225, 0.5);
  border-radius: 6px;
  color: #64748b;
  font-size: 0.8rem;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.config-action-btn:hover:not(:disabled) {
  background: rgba(241, 245, 249, 0.9);
  color: #475569;
  transform: translateY(-1px);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.generate-btn-compact:hover:not(:disabled) {
  background: rgba(94, 100, 255, 0.1);
  color: #5e64ff;
  border-color: rgba(94, 100, 255, 0.3);
}

.copy-btn-compact:hover:not(:disabled) {
  background: rgba(34, 197, 94, 0.1);
  color: #22c55e;
  border-color: rgba(34, 197, 94, 0.3);
}

.save-btn-compact:hover:not(:disabled) {
  background: rgba(16, 185, 129, 0.1);
  color: #10b981;
  border-color: rgba(16, 185, 129, 0.3);
}

.config-action-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.config-output-content {
  padding: 20px;
  background: linear-gradient(135deg, #f8f9fa, #ffffff);
}

.compact-output {
  border-top: 1px solid rgba(94, 100, 255, 0.08);
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.compact-output .config-output-text {
  margin-top: 0;
  border-radius: 8px;
  padding: 16px;
  background: rgba(94, 100, 255, 0.06);
  border: 1px dashed rgba(94, 100, 255, 0.2);
  font-size: 0.82rem;
  line-height: 1.5;
  color: #1f2937;
  box-shadow: inset 0 1px 2px rgba(148, 163, 184, 0.2);
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
  background: transparent;
  border: none;
}

/* Responsive Design */
@media (max-width: 768px) {
  .configuration-grid {
    grid-template-columns: 1fr;
  }

  .config-field.half-width {
    grid-column: span 1;
  }

  .configuration-actions {
    flex-direction: column;
  }


  .mode-tabs {
    align-self: center;
  }
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

</style>

<!--
  - Jeffrey
  - Copyright (C) 2025 Petr Bouda
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
  <div class="performance-counters-not-available-state">
    <div class="alert alert-warning d-flex align-items-start">
      <i class="bi bi-exclamation-triangle fs-4 me-3 mt-1 text-warning"></i>
      <div>
        <h5 class="alert-heading mb-3">Performance Counters Unavailable</h5>
        <p class="mb-3">
          The Performance Counters dashboard is not available because this profile does not contain
          HotSpot performance counter data/file. Performance counters provide low-level JVM metrics
          that are useful for detailed performance analysis.
        </p>

        <div class="feature-benefits mb-3">
          <h6 class="mb-2">What Performance Counters provide:</h6>
          <ul class="mb-0">
            <li>JIT compiler statistics and timing information</li>
            <li>Classloader metrics and class loading counts</li>
            <li>Memory management and GC performance data</li>
            <li>Thread and synchronization statistics</li>
            <li>Runtime and system properties</li>
          </ul>
        </div>

        <div class="jvm-flags mt-3">
          <h6 class="mb-2">JVM Flags to enable Performance Counters:</h6>
          <pre class="mb-0 bg-light p-2 rounded"><code>-XX:+UsePerfData
-XX:PerfDataSaveFile=&lt;path&gt;.hsperfdata</code></pre>
          <div class="automatic-note mt-2">
            <small class="text-muted">
              <i class="bi bi-info-circle me-1"></i>
              These flags ensure HotSpot performance data is collected and saved
            </small>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// No props needed for this static information component
</script>

<style>
@import '@shared/styles/shared-components.css';
</style>

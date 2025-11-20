<!--
  - Jeffrey
  - Copyright (C) 2025 Petr Bouda
  -
  - This program is free software: you can redistribute it and/or modify
  - it under the terms of the GNU Affero General Public License as published by
  - the Free Software Foundation, either version 3 of the License, or
  - (at your option) any later version.
  -
  - This program is distributed in the hope that it will be useful,
  - but WITHOUT ANY WARRANTY; without even the implied warranty of
  - MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  - GNU Affero General Public License for more details.
  -
  - You should have received a copy of the GNU Affero General Public License
  - along with this program.  If not, see <http://www.gnu.org/licenses/>.
  -->

<template>
  <div class="performance-counters-not-available-state">
    <div class="alert alert-warning d-flex align-items-start">
      <i class="bi bi-exclamation-triangle fs-4 me-3 mt-1 text-warning"></i>
      <div>
        <h5 class="alert-heading mb-3">Performance Counters Unavailable</h5>
        <p class="mb-3">
          The Performance Counters dashboard is not available because this profile does not contain
          HotSpot performance counter data/file. Performance counters provide low-level JVM metrics that are
          useful for detailed performance analysis.
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

<style scoped>
.performance-counters-not-available-state {
  margin: 2rem;
}

.alert {
  border: 1px solid #ffeaa7;
  border-left: 4px solid #ffc107;
  background: linear-gradient(135deg, #fffbf0, #ffffff);
}

.alert-heading {
  color: #b7791f;
  font-weight: 600;
}

.feature-benefits ul {
  padding-left: 1.5rem;
}

.feature-benefits li,
.next-steps li {
  margin-bottom: 0.5rem;
}

.next-steps ol {
  padding-left: 1.5rem;
}

.text-warning {
  color: #b7791f !important;
}

.automatic-note {
  padding-left: 0.5rem;
  margin-top: 0.25rem;
}

.automatic-note small {
  display: flex;
  align-items: center;
}

pre {
  font-size: 0.8rem;
  border: 1px solid #dee2e6;
}

code {
  color: #495057;
}
</style>

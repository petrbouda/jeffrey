<!--
  ~ Jeffrey
  ~ Copyright (C) 2025 Petr Bouda
  ~
  ~ This program is free software: you can redistribute it and/or modify
  ~ it under the terms of the GNU Affero General Public License as published by
  ~ the Free Software Foundation, either version 3 of the License, or
  ~ (at your option) any later version.
  ~
  ~ This program is distributed in the hope that it will be useful,
  ~ but WITHOUT ANY WARRANTY; without even the implied warranty of
  ~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  ~ GNU Affero General Public License for more details.
  ~
  ~ You should have received a copy of the GNU Affero General Public License
  ~ along with this program.  If not, see <http://www.gnu.org/licenses/>.
  -->

<template>
  <div class="feature-disabled-state">
    <div class="alert alert-warning d-flex align-items-start">
      <i class="bi bi-exclamation-triangle fs-4 me-3 mt-1 text-warning"></i>
      <div>
        <h5 class="alert-heading mb-3">Method Tracing Dashboard Unavailable</h5>
        <p class="mb-3">
          Method Tracing dashboard is not available because there are no <code>jdk.MethodTrace</code> events
          in this profile. These events can be emitted by Async-Profiler or directly using JFR configuration.
        </p>

        <div class="feature-benefits mb-3">
          <h6 class="mb-2">What you need to do:</h6>
          <ul class="mb-0">
            <li>
              Go to <router-link to="/profiler-settings" class="text-decoration-none fw-semibold">
                <i class="bi bi-gear me-1"></i>Profiler Settings
              </router-link> to configure method tracing for your AsyncProfiler agent
            </li>
            <li>Enable <code>jdk.MethodTrace</code> event with your desired filter patterns (e.g., <code>trace=java.nio.ByteBuffer.allocateDirect</code>)</li>
            <li>Profile your application with the configured agent</li>
            <li>The dashboard will automatically become available once <code>jdk.MethodTrace</code> events are detected</li>
          </ul>
        </div>

        <div class="next-steps">
          <h6 class="mb-2">More Information:</h6>
          <p class="mb-0 small text-muted">
            <i class="bi bi-github me-1"></i>
            For detailed information about method tracing in Async-Profiler, see:
            <a href="https://github.com/async-profiler/async-profiler/discussions/1497" target="_blank" class="text-decoration-none">
              async-profiler/discussions/1497
            </a>
          </p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
</script>

<style scoped>
.feature-disabled-state {
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

pre {
  font-size: 0.8rem;
  border: 1px solid #dee2e6;
}

code {
  color: #495057;
}

a {
  color: #0d6efd;
}

a:hover {
  color: #0a58ca;
}

.example-link {
  padding-left: 0.5rem;
  margin-top: 0.25rem;
}

.example-link small {
  display: flex;
  align-items: center;
}

.example-link a {
  font-weight: 500;
  display: inline-flex;
  align-items: center;
}

.example-link a:hover {
  text-decoration: underline !important;
}
</style>

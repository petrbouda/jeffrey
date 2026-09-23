<!--
  ~ Jeffrey
  ~ Copyright (C) 2025 Petr Bouda
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~     https://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  -->

<template>
  <div class="feature-disabled-state">
    <div class="alert alert-warning d-flex align-items-start">
      <i class="bi bi-exclamation-triangle fs-4 me-3 mt-1 text-warning"></i>
      <div>
        <h5 class="alert-heading mb-3">Method Tracing Dashboard Unavailable</h5>
        <p class="mb-3">
          Method Tracing dashboard is not available because there are no
          <code>jdk.MethodTrace</code> events in this profile. These events can be emitted by
          Async-Profiler or directly using JFR configuration.
        </p>

        <div class="feature-benefits mb-3">
          <h6 class="mb-2">What you need to do:</h6>
          <ul class="mb-0">
            <li>
              Go to
              <router-link to="/profiler-builder" class="text-decoration-none fw-semibold">
                <i class="bi bi-cpu me-1"></i>Profiler Builder
              </router-link>
              to configure method tracing for your AsyncProfiler agent
            </li>
            <li>
              Enable <code>jdk.MethodTrace</code> event with your desired filter patterns (e.g.,
              <code>trace=java.nio.ByteBuffer.allocateDirect</code>)
            </li>
            <li>Profile your application with the configured agent</li>
            <li>
              The dashboard will automatically become available once
              <code>jdk.MethodTrace</code> events are detected
            </li>
          </ul>
        </div>

        <div class="next-steps">
          <h6 class="mb-2">More Information:</h6>
          <p class="mb-0 small text-muted">
            <i class="bi bi-github me-1"></i>
            For detailed information about method tracing in Async-Profiler, see:
            <a
              href="https://github.com/async-profiler/async-profiler/discussions/1497"
              target="_blank"
              class="text-decoration-none"
            >
              async-profiler/discussions/1497
            </a>
          </p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts"></script>

<style>
@import '@shared/styles/shared-components.css';
</style>

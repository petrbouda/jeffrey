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
  <div class="gc-not-recorded-state">
    <div class="alert alert-warning d-flex align-items-start">
      <i class="bi bi-exclamation-triangle fs-4 me-3 mt-1 text-warning"></i>
      <div>
        <h5 class="alert-heading mb-3">Garbage Collection Unavailable</h5>
        <p class="mb-3">
          This recording has no garbage-collection events, so there are no pauses, causes or
          collector phases to show. It is typically a recording made by async-profiler, which writes
          heap summaries but not <code>jdk.GarbageCollection</code> or
          <code>jdk.GCConfiguration</code>. Heap usage is still available on the Heap Allocations
          page.
        </p>

        <div class="next-steps">
          <h6 class="mb-2">To record GC events next time:</h6>
          <ul class="mb-0">
            <li>
              Add <code>jfrsync=default</code> to the async-profiler options, so the JDK's own
              Flight Recorder events are written into the same file
            </li>
            <li>
              Or record with JDK Flight Recorder directly, using its <code>default</code> or
              <code>profile</code> settings
            </li>
            <li>The Garbage Collection pages become available once GC events are present</li>
          </ul>
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

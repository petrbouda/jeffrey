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
  <div class="feature-disabled-state">
    <div class="alert alert-warning d-flex align-items-start">
      <i class="bi bi-exclamation-triangle fs-4 me-3 mt-1 text-warning"></i>
      <div>
        <h5 class="alert-heading mb-3">{{ title }} Unavailable</h5>
        <p class="mb-3">
          {{ title }} is not available because of missing {{ eventType }} events in this profile. To
          enable this dashboard, you need to include the Jeffrey Tracing library in your
          application.
        </p>

        <div class="feature-benefits mb-3">
          <h6 class="mb-2">What you need to do:</h6>
          <ul class="mb-0">
            <li>Add the Jeffrey Tracing dependency to your project</li>
            <li>
              Instrument your code to emit {{ eventType }} events at relevant points
              <div class="example-link mt-1">
                <small class="text-muted">
                  <i class="bi bi-github me-1"></i>
                  See implementation examples:
                  <a
                    href="https://github.com/petrbouda/jeffrey-testapp"
                    target="_blank"
                    class="text-decoration-none"
                  >
                    jeffrey-testapp
                  </a>
                </small>
              </div>
            </li>
            <li>Rebuild and restart your application with profiling enabled</li>
            <li>The dashboard will automatically become available once events are detected</li>
          </ul>
        </div>

        <div class="next-steps">
          <h6 class="mb-2">Maven Dependency:</h6>
          <p class="mb-2 small text-muted">
            Events are available on Maven Central:
            <a
              href="https://central.sonatype.com/artifact/cafe.jeffrey-analyst/jeffrey-events"
              target="_blank"
              class="text-decoration-none"
            >
              https://central.sonatype.com/artifact/cafe.jeffrey-analyst/jeffrey-events
            </a>
          </p>
          <pre class="mt-2 mb-0 bg-light p-2 rounded"><code>&lt;dependency&gt;
    &lt;groupId&gt;cafe.jeffrey-analyst&lt;/groupId&gt;
    &lt;artifactId&gt;jeffrey-events&lt;/artifactId&gt;
    &lt;version&gt;0.10.0&lt;/version&gt;
&lt;/dependency&gt;</code></pre>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// Define props
interface Props {
  title: string;
  eventType: string;
}

const props = withDefaults(defineProps<Props>(), {
  title: 'Dashboard',
  eventType: 'required'
});
</script>

<style>
@import '@shared/styles/shared-components.css';
</style>

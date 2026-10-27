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

<script setup lang="ts">
/** Where Jeffrey Pages explains how to make a session report its heartbeat. */
const HEARTBEAT_SETUP_DOCS_URL =
  'https://www.jeffrey-analyst.cafe/docs/hub/recording-sessions/lifecycle#sessions-without-heartbeats';
</script>

<template>
  <div class="field-alert field-alert-warning heartbeat-note" role="note" @click.stop>
    <i class="bi bi-heartbreak"></i>
    <div>
      <div class="heartbeat-note-title">No heartbeat received</div>
      <div>
        This session sent no heartbeat within its startup grace (1 minute by default), so the hub
        finished it. Every profiled JVM must report a heartbeat — through the Jeffrey Agent the
        Provisioner attaches by default, or the
        <code class="heartbeat-note-code">jeffrey-heartbeat</code> library.
      </div>
      <a
        class="heartbeat-note-link"
        :href="HEARTBEAT_SETUP_DOCS_URL"
        target="_blank"
        rel="noopener noreferrer"
      >
        How to set up heartbeats <i class="bi bi-box-arrow-up-right"></i>
      </a>
    </div>
  </div>
</template>

<style scoped>
.heartbeat-note {
  margin: 0 var(--spacing-3) var(--spacing-3);
  cursor: default;
}

.heartbeat-note-title {
  font-weight: var(--font-weight-semibold);
  margin-bottom: var(--spacing-1);
}

.heartbeat-note-code {
  font-family: var(--font-family-monospace);
  color: inherit;
}

.heartbeat-note-link {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-1);
  margin-top: var(--spacing-1);
  font-weight: var(--font-weight-semibold);
  color: inherit;
  text-decoration: underline;
}
</style>

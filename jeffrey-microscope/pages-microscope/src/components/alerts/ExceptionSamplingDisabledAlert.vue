<!--
  ~ Jeffrey
  ~ Copyright (C) 2026 Petr Bouda
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
  <DisabledEventsNotice
    class="mb-4"
    title="Per-type exception detail is incomplete"
    action-label="How to get the full breakdown"
  >
    This recording created
    <strong>{{ FormattingService.formatNumber(totalThrowables) }}</strong> throwables, but
    <code>jdk.JavaExceptionThrow</code> events are <strong>disabled</strong>. The per-type breakdown
    below therefore only covers the
    <strong>{{ FormattingService.formatNumber(errorCount) }}</strong>
    <code>jdk.JavaErrorThrow</code>
    (Error) throws — the remaining exceptions exist only as the aggregate total above and have no
    per-type detail.

    <template #action>
      <p>
        Enable <code>jdk.JavaExceptionThrow</code> events — the <code>profile</code> settings preset
        turns them on automatically:
      </p>
      <ul>
        <li>
          <strong>AsyncProfiler</strong>: use jfrSync with the <code>profile</code> settings
          (<code>jfrsync=profile</code>).
        </li>
        <li>
          <strong>Standard JFR</strong>: start the recording with the <code>profile</code> settings
          (<code>settings=profile</code>) instead of <code>default</code>.
        </li>
      </ul>
      <p>
        Profile your application again — once <code>jdk.JavaExceptionThrow</code> events are
        present, every exception type appears in the breakdown below.
      </p>
    </template>
  </DisabledEventsNotice>
</template>

<script setup lang="ts">
import DisabledEventsNotice from '@/components/alerts/DisabledEventsNotice.vue';
import FormattingService from '@shared/services/FormattingService';

defineProps<{
  totalThrowables: number;
  errorCount: number;
}>();
</script>

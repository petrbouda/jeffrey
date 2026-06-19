<!--
  ~ Jeffrey
  ~ Copyright (C) 2026 Petr Bouda
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
  <DisabledEventsNotice
    class="mb-4"
    title="Per-type exception detail is incomplete"
    action-label="How to get the full breakdown"
  >
    This recording created <strong>{{ FormattingService.formatNumber(totalThrowables) }}</strong>
    throwables, but <code>jdk.JavaExceptionThrow</code> events are <strong>disabled</strong>. The
    per-type breakdown below therefore only covers the
    <strong>{{ FormattingService.formatNumber(errorCount) }}</strong> <code>jdk.JavaErrorThrow</code>
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
        Profile your application again — once <code>jdk.JavaExceptionThrow</code> events are present,
        every exception type appears in the breakdown below.
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

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
  <div class="feature-disabled-state">
    <div class="alert alert-warning d-flex align-items-start">
      <i class="bi bi-exclamation-triangle fs-4 me-3 mt-1 text-warning"></i>
      <div>
        <h5 class="alert-heading mb-3">No Traces In This Profile</h5>
        <p class="mb-3">
          Traces are built from events that carry a trace id. This recording has none, so there is
          nothing to assemble into a span tree.
        </p>

        <div class="feature-benefits mb-3">
          <h6 class="mb-2">What you need to do:</h6>
          <ul class="mb-0">
            <li>
              Add <code>cafe.jeffrey-analyst:jeffrey-events</code> to the application you want to
              profile
            </li>
            <li>
              Wrap the work you care about in <code>Tracer.run(...)</code>, or let the HTTP, gRPC
              and JDBC instrumentation stamp its own events
            </li>
            <li>
              Record with <code>jeffrey.TraceSpan</code> enabled — it has a 1&nbsp;ms threshold by
              default, so raise or clear it if you want very short spans
            </li>
            <li>
              Traces appear here automatically once the profile contains events carrying trace ids
            </li>
          </ul>
        </div>

        <div class="next-steps">
          <h6 class="mb-2">Note:</h6>
          <p class="mb-0 small text-muted">
            <i class="bi bi-info-circle me-1"></i>
            Traces are assembled within a single JVM recording. Spans from other processes are not
            joined, even when they share a trace id.
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

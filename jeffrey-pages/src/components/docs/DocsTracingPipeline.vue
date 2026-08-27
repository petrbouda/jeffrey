<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
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

<script setup lang="ts">
/**
 * The tracing pipeline, drawn as the split between the two times things happen.
 *
 * Everything left of the seam is written while the JVM runs; everything right of it
 * is derived later, on the reader's machine. The recording sits on the seam because
 * it is the only interface between the two sides — there is no collector, no exporter
 * and no "send data" step in between. Shares the palette and chip vocabulary of
 * DocsArchDiagram so the two pictures read as one system.
 */
</script>

<template>
  <div class="pipe-diagram">
    <div class="pipe-split">

      <!-- Runtime: the app and the JVM writing into the same recording -->
      <div class="pipe-zone runtime">
        <div class="pipe-zone-label">
          <i class="bi bi-clock-history"></i>
          <span>Runtime</span>
          <small>wherever the app runs</small>
        </div>
        <div class="pipe-box">
          <h4>Your Application</h4>
          <div class="pipe-chip entry"><code>jeffrey-events</code> — zero dependencies</div>
          <div class="pipe-chip entry"><code>Tracer.run(…)</code> / <code>@Traced</code> + Agent</div>
          <div class="pipe-chip neutral">HTTP / gRPC / JDBC glue</div>
        </div>
        <div class="pipe-box">
          <h4>The JVM, in parallel</h4>
          <div class="pipe-chip neutral"><code>jdk.ExecutionSample</code> — CPU samples</div>
          <div class="pipe-chip neutral"><code>jdk.SocketRead</code>, locks, GC, safepoints</div>
        </div>
      </div>

      <!-- The seam: one file, the only interface between the two sides -->
      <div class="pipe-seam">
        <span class="pipe-seam-caption">everything is written</span>
        <div class="pipe-file">
          <i class="bi bi-file-earmark-binary"></i>
          <h4>One JFR Recording</h4>
          <span class="pipe-file-name">profile.jfr</span>
        </div>
        <span class="pipe-seam-caption">nothing is derived yet</span>
      </div>

      <!-- Analysis time: everything is computed on the reader's machine -->
      <div class="pipe-zone analysis">
        <div class="pipe-zone-label">
          <i class="bi bi-pc-display"></i>
          <span>Analysis Time</span>
          <small>your machine</small>
        </div>
        <div class="pipe-box">
          <h4>Jeffrey Microscope</h4>
          <div class="pipe-chip neutral">parse → assemble span trees</div>
          <div class="pipe-chip neutral">promote <code>jdk.*</code> blocking into leaf spans</div>
          <div class="pipe-chip neutral">match GC pauses across trace windows</div>
        </div>
        <div class="pipe-box">
          <h4>What you get</h4>
          <div class="pipe-chip scope">Waterfall &amp; Operations</div>
          <div class="pipe-chip analysis">Flamegraph per span</div>
          <div class="pipe-chip neutral">Trace search</div>
        </div>
      </div>

    </div>
  </div>
</template>

<style scoped>
/* ===== TRACING PIPELINE: RUNTIME / ANALYSIS SPLIT ===== */
.pipe-diagram {
  margin: 1.5rem 0;
  padding: 2rem 1.5rem;
  background: linear-gradient(180deg, #f8fafc 0%, #f1f5f9 100%);
  border: 1px solid #e2e8f0;
  border-radius: 12px;
}

.pipe-split {
  display: flex;
  align-items: stretch;
  gap: 0;
}

/* Zones */
.pipe-zone {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  padding: 0.85rem;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
}

.pipe-zone.runtime { background: rgba(239, 68, 68, 0.045); }
.pipe-zone.analysis { background: rgba(94, 100, 255, 0.05); }

.pipe-zone-label {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.6rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.pipe-zone.runtime .pipe-zone-label { color: #991b1b; }
.pipe-zone.analysis .pipe-zone-label { color: #3730a3; }

.pipe-zone-label i { font-size: 0.8rem; }

.pipe-zone-label small {
  margin-left: auto;
  font-weight: 500;
  letter-spacing: 0.03em;
  color: #94a3b8;
}

/* Boxes inside a zone */
.pipe-box {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  padding: 0.6rem;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
}

.pipe-box h4 {
  margin: 0;
  font-size: 0.8rem;
  font-weight: 600;
  color: #343a40;
}

.pipe-chip {
  padding: 0.3rem 0.6rem;
  border-radius: 5px;
  font-size: 0.7rem;
  font-weight: 500;
}

.pipe-chip code {
  font-size: 0.95em;
  background: none;
  padding: 0;
  color: inherit;
}

.pipe-chip.neutral { background: #f3f4f6; color: #374151; }
.pipe-chip.entry { background: #ecfdf5; color: #065f46; }
.pipe-chip.scope { background: #e0e7ff; color: #3730a3; }
.pipe-chip.analysis { background: #fef3c7; color: #92400e; }

/* The seam: the recording is the only interface between the two sides */
.pipe-seam {
  position: relative;
  flex: 0 0 210px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.4rem;
  padding: 0 0.75rem;
}

.pipe-seam::before {
  content: "";
  position: absolute;
  top: 0;
  bottom: 0;
  left: 50%;
  border-left: 2px dashed #f59e0b;
  opacity: 0.5;
}

.pipe-file {
  position: relative;
  z-index: 1;
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.35rem;
  padding: 0.65rem;
  background: #fff;
  border: 2px solid #f59e0b;
  border-radius: 10px;
}

.pipe-file i {
  font-size: 1.2rem;
  color: #f59e0b;
}

.pipe-file h4 {
  margin: 0;
  font-size: 0.8rem;
  font-weight: 600;
  color: #92400e;
}

.pipe-file-name {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 0.65rem;
  color: #64748b;
}

.pipe-seam-caption {
  position: relative;
  z-index: 1;
  padding: 0.15rem 0.4rem;
  background: #f1f5f9;
  font-size: 0.6rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: #64748b;
  text-align: center;
}

/* Responsive */
@media (max-width: 768px) {
  .pipe-split {
    flex-direction: column;
    gap: 0.75rem;
  }

  .pipe-seam {
    flex: none;
    padding: 0.5rem 0;
  }

  .pipe-seam::before {
    top: 50%;
    bottom: auto;
    left: 0;
    right: 0;
    border-left: none;
    border-top: 2px dashed #f59e0b;
  }
}
</style>

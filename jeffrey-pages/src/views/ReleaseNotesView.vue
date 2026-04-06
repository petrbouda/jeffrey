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
import { ref, computed, onMounted, onUnmounted } from 'vue'

const selectedVersion = ref('0.7.0')

interface GalleryImage {
  src: string
  caption: string
}

const quickAnalysisImages: GalleryImage[] = [
  { src: '/images/release-notes/quick-analysis/01-upload.png', caption: 'Upload & manage recordings' },
  { src: '/images/release-notes/quick-analysis/02-gc-analysis.png', caption: 'GC analysis overview' },
  { src: '/images/release-notes/quick-analysis/03-flamegraph-selection.png', caption: 'Flamegraph selection' },
  { src: '/images/release-notes/quick-analysis/04-flamegraph-timeseries.png', caption: 'Flamegraph with timeseries' },
  { src: '/images/release-notes/quick-analysis/05-differential.png', caption: 'Differential comparison' },
  { src: '/images/release-notes/quick-analysis/06-flamegraph-detail.png', caption: 'Detailed flamegraph' },
]

const techStackImages: GalleryImage[] = [
  { src: '/images/release-notes/tech-stack/01-overview.png', caption: 'Technology dashboards overview' },
  { src: '/images/release-notes/tech-stack/02-http-timeseries.png', caption: 'HTTP Server — metrics timeline' },
  { src: '/images/release-notes/tech-stack/03-http-slowest.png', caption: 'HTTP Server — slowest requests' },
  { src: '/images/release-notes/tech-stack/04-jdbc-timeseries.png', caption: 'Database (JDBC) — metrics timeline' },
  { src: '/images/release-notes/tech-stack/05-jdbc-distribution.png', caption: 'Database (JDBC) — distribution' },
]

const serverRecordingImages: GalleryImage[] = [
  { src: '/images/release-notes/server-recording/01-architecture.png', caption: 'Jeffrey Local + Server architecture' },
  { src: '/images/release-notes/server-recording/02-workspaces.png', caption: 'Workspaces & projects' },
  { src: '/images/release-notes/server-recording/02-instances-overview.png', caption: 'Instances overview & lifecycle' },
  { src: '/images/release-notes/server-recording/03-instance-timeline.png', caption: 'Instance timeline' },
  { src: '/images/release-notes/server-recording/04-session-detail.png', caption: 'Session detail & artifacts' },
  { src: '/images/release-notes/server-recording/05-download-assistant.png', caption: 'Download assistant' },
  { src: '/images/release-notes/server-recording/06-recordings.png', caption: 'Recordings management' },
]

const heapDumpImages: GalleryImage[] = [
  { src: '/images/release-notes/heap-dump/01-initializing.png', caption: 'Heap dump initialization' },
  { src: '/images/release-notes/heap-dump/02-overview.png', caption: 'Heap dump overview' },
  { src: '/images/release-notes/heap-dump/03-class-histogram.png', caption: 'Class histogram' },
  { src: '/images/release-notes/heap-dump/04-dominator-tree.png', caption: 'Dominator tree' },
  { src: '/images/release-notes/heap-dump/05-string-analysis.png', caption: 'String deduplication analysis' },
  { src: '/images/release-notes/heap-dump/06-oql-assistant.png', caption: 'OQL query with AI assistant' },
  { src: '/images/release-notes/heap-dump/07-instance-tree.png', caption: 'Instance tree & object details' },
]

const aiAnalysisImages: GalleryImage[] = [
  { src: '/images/release-notes/ai-analysis/01-jfr-analysis.png', caption: 'JFR AI Analysis — chat interface' },
  { src: '/images/release-notes/ai-analysis/02-oql-assistant.png', caption: 'OQL Assistant — query generation' },
  { src: '/images/release-notes/ai-analysis/03-gc-analysis.png', caption: 'AI GC pause time analysis' },
  { src: '/images/release-notes/ai-analysis/09-gc-detailed.png', caption: 'AI GC detailed breakdown' },
  { src: '/images/release-notes/ai-analysis/04-gc-recommendations.png', caption: 'AI findings & recommendations' },
  { src: '/images/release-notes/ai-analysis/05-heap-dump-ai.png', caption: 'Heap Dump AI Analysis' },
  { src: '/images/release-notes/ai-analysis/06-heap-dump-overview.png', caption: 'AI heap dump overview' },
  { src: '/images/release-notes/ai-analysis/07-heap-dump-findings.png', caption: 'AI heap dump findings' },
  { src: '/images/release-notes/ai-analysis/08-instance-tree.png', caption: 'Instance tree & object details' },
]

const docsImages: GalleryImage[] = [
  { src: '/images/release-notes/docs/01-architecture.png', caption: 'Documentation — Architecture Overview' },
]

const toolsImages: GalleryImage[] = [
  { src: '/images/release-notes/tools/01-flamegraph-before.png', caption: 'Flamegraph — before collapse' },
  { src: '/images/release-notes/tools/02-collapse-frames.png', caption: 'Collapse Frames configuration' },
  { src: '/images/release-notes/tools/03-flamegraph-after.png', caption: 'Flamegraph — after collapse' },
]

const profilerSettingsImages: GalleryImage[] = [
  { src: '/images/release-notes/profiler-settings/01-visual-builder.png', caption: 'Visual Builder — CPU profiling & live command' },
  { src: '/images/release-notes/profiler-settings/02-event-options.png', caption: 'Event options & advanced settings' },
]

const activeImages = ref<Record<string, number>>({
  quickAnalysis: 0,
  techStack: 0,
  serverRecording: 0,
  heapDump: 0,
  aiAnalysis: 0,
  profilerSettings: 0,
  docs: 0,
  tools: 0,
})

const lightboxFeature = ref<GalleryImage[] | null>(null)
const lightboxIndex = ref(0)

const lightboxImage = computed(() =>
  lightboxFeature.value ? lightboxFeature.value[lightboxIndex.value] : null
)

const lightboxHasPrev = computed(() => lightboxIndex.value > 0)
const lightboxHasNext = computed(() =>
  lightboxFeature.value ? lightboxIndex.value < lightboxFeature.value.length - 1 : false
)

function selectImage(feature: string, index: number) {
  activeImages.value[feature] = index
}

function openLightbox(images: GalleryImage[], index: number) {
  lightboxFeature.value = images
  lightboxIndex.value = index
  document.body.style.overflow = 'hidden'
}

function closeLightbox() {
  lightboxFeature.value = null
  document.body.style.overflow = ''
}

function lightboxPrev() {
  if (lightboxHasPrev.value) lightboxIndex.value--
}

function lightboxNext() {
  if (lightboxHasNext.value) lightboxIndex.value++
}

function onLightboxKey(e: KeyboardEvent) {
  if (!lightboxFeature.value) return
  if (e.key === 'ArrowLeft') lightboxPrev()
  else if (e.key === 'ArrowRight') lightboxNext()
  else if (e.key === 'Escape') closeLightbox()
}

onMounted(() => document.addEventListener('keydown', onLightboxKey))
onUnmounted(() => document.removeEventListener('keydown', onLightboxKey))
</script>

<template>
  <div class="release-notes-page">
    <!-- Hero -->
    <section class="hero-banner">
      <div class="hero-inner">
        <div class="hero-image-box">
          <img src="/images/jeffrey-easter.jpg" alt="Jeffrey Easter Release">
        </div>
        <div class="hero-text">
          <div class="hero-eyebrow">Release Notes</div>
          <h1 class="hero-title"><span>Easter</span> Release</h1>
          <p class="hero-version">Version 0.7.0 &middot; April 2026</p>
          <p class="hero-summary">Quick analysis, technology stack dashboards, continuous recording, heap dump analysis, AI-powered insights, real-time alerts, and a visual profiler settings builder.</p>
        </div>
      </div>
    </section>

    <!-- Version Switcher -->
    <div class="version-bar">
      <div class="version-bar-inner">
        <div class="version-bar-left">
          <h3>Release Notes</h3>
          <select v-model="selectedVersion" class="version-select">
            <option value="0.7.0">0.7.0 &mdash; Easter Release</option>
            <option value="0.6.0">0.6.0</option>
          </select>
        </div>
      </div>
    </div>

    <!-- Get Started -->
    <section class="get-started">
      <div class="get-started-inner">
        <div class="gs-title-row">
          <h2><i class="bi bi-rocket-takeoff"></i>Get Started in Seconds</h2>
          <a href="https://www.jeffrey-analyst.cafe/launch-it" target="_blank" class="gs-more-link">More options <i class="bi bi-arrow-right"></i></a>
        </div>
        <div class="gs-commands">
          <div class="gs-cmd-card">
            <div class="gs-cmd-top">
              <div class="gs-cmd-icon java"><i class="bi bi-cup-hot"></i></div>
              <div>
                <div class="gs-cmd-label">Plain Java</div>
                <div class="gs-cmd-sub"><a href="https://github.com/petrbouda/jeffrey/releases/download/v0.7.0/jeffrey.jar">Download jeffrey.jar</a> &middot; Java 25+</div>
              </div>
            </div>
            <div class="gs-code"><span class="dollar">$</span> <span class="text">java -jar jeffrey.jar</span></div>
            <p class="gs-open">Then open <a href="http://localhost:8080" target="_blank">localhost:8080</a></p>
          </div>
          <div class="gs-cmd-card">
            <div class="gs-cmd-top">
              <div class="gs-cmd-icon docker"><i class="bi bi-box-seam"></i></div>
              <div>
                <div class="gs-cmd-label">Docker with Examples</div>
                <div class="gs-cmd-sub">Pre-loaded sample recordings</div>
              </div>
              <span class="gs-cmd-badge">Recommended</span>
            </div>
            <div class="gs-code"><span class="dollar">$</span> <span class="text">docker run -it --network host petrbouda/jeffrey-examples:0.7.0</span></div>
            <p class="gs-open">Then open <a href="http://localhost:8080" target="_blank">localhost:8080</a></p>
          </div>
        </div>
      </div>
    </section>

    <!-- Feature 01: Quick Analysis -->
    <div class="feature-section-bg">
      <section class="feature-section">
        <div class="feature-row">
          <div class="feature-text">
            <div class="feature-number">Feature 01</div>
            <h2>Quick Analysis</h2>
            <p>The fastest path from a JFR file to actionable insights. Drag and drop a recording anywhere in the app and get instant flamegraphs, timeseries, and thread analysis &mdash; no workspace or project setup required.</p>
            <p>Supports JFR files (.jfr, .jfr.lz4) and heap dumps (.hprof, .hprof.gz). Organize your recordings into named groups for easy management of ad-hoc investigations.</p>
            <div class="feature-highlights">
              <span class="feature-highlight"><i class="bi bi-upload"></i> Drag & Drop</span>
              <span class="feature-highlight"><i class="bi bi-lightning"></i> Instant Analysis</span>
              <span class="feature-highlight"><i class="bi bi-gear"></i> Zero Setup</span>
            </div>
          </div>
          <div class="feature-gallery">
            <div class="gallery-main" @click="openLightbox(quickAnalysisImages, activeImages.quickAnalysis)">
              <img :src="quickAnalysisImages[activeImages.quickAnalysis].src" :alt="quickAnalysisImages[activeImages.quickAnalysis].caption">
              <div class="gallery-caption">{{ quickAnalysisImages[activeImages.quickAnalysis].caption }}</div>
            </div>
            <div class="gallery-thumbs">
              <button
                v-for="(img, i) in quickAnalysisImages"
                :key="i"
                class="gallery-thumb"
                :class="{ active: activeImages.quickAnalysis === i }"
                @click="selectImage('quickAnalysis', i)"
              >
                <img :src="img.src" :alt="img.caption">
              </button>
            </div>
          </div>
        </div>
      </section>
    </div>

    <!-- Feature 02: Technology Stack Analysis -->
    <section class="feature-section">
      <div class="feature-row reverse">
        <div class="feature-text">
          <div class="feature-number">Feature 02</div>
          <h2>Technology Stack Analysis</h2>
          <p>Analyze HTTP, JDBC, and gRPC traffic directly from your JFR recordings. Add the <a href="https://github.com/petrbouda/jeffrey-events" target="_blank">jeffrey-events</a> library to your application to emit custom JFR events &mdash; Jeffrey automatically detects them and activates dedicated dashboards with timeseries, latency distributions, slowest requests, and more.</p>
          <p>
            Available on
            <a href="https://github.com/petrbouda/jeffrey-events" target="_blank">GitHub</a> and
            <a href="https://mvnrepository.com/artifact/cafe.jeffrey-analyst/jeffrey-events" target="_blank">Maven Central</a>.
          </p>
          <div class="feature-highlights">
            <span class="feature-highlight"><i class="bi bi-globe"></i> HTTP Analysis</span>
            <span class="feature-highlight"><i class="bi bi-database"></i> JDBC Analysis</span>
            <span class="feature-highlight"><i class="bi bi-diagram-3"></i> gRPC Analysis</span>
            <span class="feature-highlight"><i class="bi bi-github"></i> Open Source</span>
          </div>
        </div>
        <div class="feature-gallery">
          <div class="gallery-main" @click="openLightbox(techStackImages, activeImages.techStack)">
            <img :src="techStackImages[activeImages.techStack].src" :alt="techStackImages[activeImages.techStack].caption">
            <div class="gallery-caption">{{ techStackImages[activeImages.techStack].caption }}</div>
          </div>
          <div class="gallery-thumbs">
            <button
              v-for="(img, i) in techStackImages"
              :key="i"
              class="gallery-thumb"
              :class="{ active: activeImages.techStack === i }"
              @click="selectImage('techStack', i)"
            >
              <img :src="img.src" :alt="img.caption">
            </button>
          </div>
        </div>
      </div>
    </section>

    <!-- Feature 03: Server Continuous Recording -->
    <div class="feature-section-bg">
      <section class="feature-section">
        <div class="feature-row">
          <div class="feature-text">
            <div class="feature-number">Feature 03</div>
            <h2>Server Continuous Recording</h2>
            <p>A split architecture with <strong>jeffrey-server</strong> collecting recordings in Kubernetes and <strong>jeffrey-local</strong> providing full analysis on your machine. Applications write JFR chunks to shared storage, and Jeffrey discovers sessions, instances, and artifacts automatically.</p>
            <p>Visualize application instance timelines, browse recording sessions, and merge JFR chunks for deep analysis. Artifacts include heap dumps, JVM logs, and performance counters &mdash; all collected continuously alongside your JFR recordings.</p>
            <div class="feature-highlights">
              <span class="feature-highlight"><i class="bi bi-cloud"></i> Kubernetes</span>
              <span class="feature-highlight"><i class="bi bi-record-circle"></i> Continuous Profiling</span>
              <span class="feature-highlight"><i class="bi bi-bar-chart-steps"></i> Session Timelines</span>
            </div>
          </div>
          <div class="feature-gallery">
            <div class="gallery-main" @click="openLightbox(serverRecordingImages, activeImages.serverRecording)">
              <img :src="serverRecordingImages[activeImages.serverRecording].src" :alt="serverRecordingImages[activeImages.serverRecording].caption">
              <div class="gallery-caption">{{ serverRecordingImages[activeImages.serverRecording].caption }}</div>
            </div>
            <div class="gallery-thumbs">
              <button
                v-for="(img, i) in serverRecordingImages"
                :key="i"
                class="gallery-thumb"
                :class="{ active: activeImages.serverRecording === i }"
                @click="selectImage('serverRecording', i)"
              >
                <img :src="img.src" :alt="img.caption">
              </button>
            </div>
          </div>
        </div>
      </section>
    </div>

    <!-- Feature 04: Heap Dump Analysis -->
    <section class="feature-section">
      <div class="feature-row reverse">
        <div class="feature-text">
          <div class="feature-number">Feature 04</div>
          <h2>Heap Dump Analysis</h2>
          <p>Comprehensive .hprof analysis with dominator trees to find the biggest memory retainers, GC root path tracing to understand why objects stay alive, class histograms, string deduplication analysis, and collection efficiency insights.</p>
          <p>An interactive OQL query interface with AI assistant lets you write custom queries with full JavaScript support for advanced heap investigations.</p>
          <div class="feature-highlights">
            <span class="feature-highlight"><i class="bi bi-diagram-3"></i> Dominator Trees</span>
            <span class="feature-highlight"><i class="bi bi-signpost-split"></i> GC Root Paths</span>
            <span class="feature-highlight"><i class="bi bi-code-slash"></i> OQL Queries</span>
          </div>
        </div>
        <div class="feature-gallery">
          <div class="gallery-main" @click="openLightbox(heapDumpImages, activeImages.heapDump)">
            <img :src="heapDumpImages[activeImages.heapDump].src" :alt="heapDumpImages[activeImages.heapDump].caption">
            <div class="gallery-caption">{{ heapDumpImages[activeImages.heapDump].caption }}</div>
          </div>
          <div class="gallery-thumbs">
            <button
              v-for="(img, i) in heapDumpImages"
              :key="i"
              class="gallery-thumb"
              :class="{ active: activeImages.heapDump === i }"
              @click="selectImage('heapDump', i)"
            >
              <img :src="img.src" :alt="img.caption">
            </button>
          </div>
        </div>
      </div>
    </section>

    <!-- Feature 05: AI-Powered Analysis -->
    <div class="feature-section-bg">
      <section class="feature-section">
        <div class="feature-row">
          <div class="feature-text">
            <div class="feature-number">Feature 05</div>
            <h2>AI-Powered Analysis</h2>
            <p>Let AI do the heavy lifting. Jeffrey integrates with <strong>Claude</strong> and <strong>ChatGPT</strong> to analyze your JFR recordings and heap dumps. The AI executes queries against your profiling data, identifies bottlenecks, and provides optimization suggestions in natural language.</p>
            <p>Three specialized assistants: <strong>JFR Analysis</strong> for profiling events, <strong>Heap Dump Analysis</strong> for memory investigations, and an <strong>OQL Assistant</strong> that generates and explains Object Query Language queries.</p>
            <div class="feature-highlights">
              <span class="feature-highlight"><i class="bi bi-robot"></i> Claude & ChatGPT</span>
              <span class="feature-highlight"><i class="bi bi-fire"></i> JFR Analysis</span>
              <span class="feature-highlight"><i class="bi bi-hdd-stack"></i> Heap Dump AI</span>
              <span class="feature-highlight"><i class="bi bi-chat-dots"></i> OQL Assistant</span>
            </div>
          </div>
          <div class="feature-gallery">
            <div class="gallery-main" @click="openLightbox(aiAnalysisImages, activeImages.aiAnalysis)">
              <img :src="aiAnalysisImages[activeImages.aiAnalysis].src" :alt="aiAnalysisImages[activeImages.aiAnalysis].caption">
              <div class="gallery-caption">{{ aiAnalysisImages[activeImages.aiAnalysis].caption }}</div>
            </div>
            <div class="gallery-thumbs">
              <button
                v-for="(img, i) in aiAnalysisImages"
                :key="i"
                class="gallery-thumb"
                :class="{ active: activeImages.aiAnalysis === i }"
                @click="selectImage('aiAnalysis', i)"
              >
                <img :src="img.src" :alt="img.caption">
              </button>
            </div>
          </div>
        </div>
      </section>
    </div>

    <!-- Feature 06: Messages & Alerts -->
    <section class="feature-section">
      <div class="feature-row reverse">
        <div class="feature-text">
          <div class="feature-number">Feature 06</div>
          <h2>Real-time Messages & Alerts</h2>
          <p>Applications emit structured alerts and messages as JFR events using the <strong>jeffrey-events</strong> library. Jeffrey processes them in real time &mdash; no batching, no polling &mdash; and surfaces them in dedicated dashboards with severity breakdowns, time series charts, and filtering.</p>
          <p>Four severity levels (Critical, High, Medium, Low) with category-based organization, full-text search, and configurable time ranges. Available in remote workspaces for continuous production monitoring.</p>
          <div class="feature-highlights">
            <span class="feature-highlight"><i class="bi bi-broadcast"></i> Real-time</span>
            <span class="feature-highlight"><i class="bi bi-exclamation-triangle"></i> Severity Levels</span>
            <span class="feature-highlight"><i class="bi bi-graph-up"></i> Time Series</span>
          </div>
        </div>
        <div class="feature-gallery">
          <div class="gallery-main" @click="openLightbox([{ src: '/images/release-notes/messages-alerts/01-messages.png', caption: 'Important Messages dashboard' }], 0)">
            <img src="/images/release-notes/messages-alerts/01-messages.png" alt="Important Messages dashboard">
            <div class="gallery-caption">Important Messages dashboard</div>
          </div>
        </div>
      </div>
    </section>

    <!-- Feature 07: Profiler Settings -->
    <div class="feature-section-bg">
      <section class="feature-section">
        <div class="feature-row">
          <div class="feature-text">
            <div class="feature-number">Feature 07</div>
            <h2>Profiler Settings</h2>
            <p>Configure Async-Profiler directly from Jeffrey's UI. Define profiling parameters &mdash; sampling interval, event types, stack depth, and output format &mdash; at global, workspace, or project level with an inheritance hierarchy that lets you override settings where needed.</p>
            <p>Settings are automatically propagated to shared storage and picked up by Jeffrey CLI, so your applications start profiling with the right configuration without any manual setup.</p>
            <div class="feature-highlights">
              <span class="feature-highlight"><i class="bi bi-sliders"></i> Async-Profiler Config</span>
              <span class="feature-highlight"><i class="bi bi-layers"></i> Settings Inheritance</span>
              <span class="feature-highlight"><i class="bi bi-arrow-repeat"></i> Auto-propagation</span>
            </div>
          </div>
          <div class="feature-gallery">
            <div class="gallery-main" @click="openLightbox(profilerSettingsImages, activeImages.profilerSettings)">
              <img :src="profilerSettingsImages[activeImages.profilerSettings].src" :alt="profilerSettingsImages[activeImages.profilerSettings].caption">
              <div class="gallery-caption">{{ profilerSettingsImages[activeImages.profilerSettings].caption }}</div>
            </div>
            <div class="gallery-thumbs">
              <button
                v-for="(img, i) in profilerSettingsImages"
                :key="i"
                class="gallery-thumb"
                :class="{ active: activeImages.profilerSettings === i }"
                @click="selectImage('profilerSettings', i)"
              >
                <img :src="img.src" :alt="img.caption">
              </button>
            </div>
          </div>
        </div>
      </section>
    </div>

    <!-- Improvements Section -->
    <div class="improvements-section">
      <div class="improvements-inner">
        <div class="improvements-header">
          <div class="improvements-header-line"></div>
          <h2><i class="bi bi-wrench"></i> Improvements</h2>
          <div class="improvements-header-line"></div>
        </div>
        <div class="improvements-grid">
          <div class="improvement-tile">
            <div class="imp-tile-gallery">
              <div class="imp-tile-main" @click="openLightbox(docsImages, activeImages.docs)">
                <img :src="docsImages[activeImages.docs].src" :alt="docsImages[activeImages.docs].caption">
              </div>
              <div v-if="docsImages.length > 1" class="imp-tile-thumbs">
                <button
                  v-for="(img, i) in docsImages"
                  :key="i"
                  class="imp-thumb"
                  :class="{ active: activeImages.docs === i }"
                  @click="selectImage('docs', i)"
                >
                  <img :src="img.src" :alt="img.caption">
                </button>
              </div>
            </div>
            <div class="imp-tile-body">
              <h3>Updated Documentation</h3>
              <p>Comprehensive guides covering all new features, deployment options, and configuration reference at <a href="https://www.jeffrey-analyst.cafe/" target="_blank">jeffrey-analyst.cafe</a>.</p>
              <div class="imp-pills">
                <span class="imp-pill"><i class="bi bi-journal-text"></i> Guides</span>
                <span class="imp-pill"><i class="bi bi-gear"></i> Configuration</span>
                <span class="imp-pill"><i class="bi bi-box-seam"></i> Deployment</span>
              </div>
            </div>
          </div>
          <div class="improvement-tile">
            <div class="imp-tile-gallery">
              <div class="imp-tile-main" @click="openLightbox(toolsImages, activeImages.tools)">
                <img :src="toolsImages[activeImages.tools].src" :alt="toolsImages[activeImages.tools].caption">
              </div>
              <div v-if="toolsImages.length > 1" class="imp-tile-thumbs">
                <button
                  v-for="(img, i) in toolsImages"
                  :key="i"
                  class="imp-thumb"
                  :class="{ active: activeImages.tools === i }"
                  @click="selectImage('tools', i)"
                >
                  <img :src="img.src" :alt="img.caption">
                </button>
              </div>
            </div>
            <div class="imp-tile-body">
              <h3>JFR Tools</h3>
              <p>Frame transformations for flamegraphs &mdash; <strong>Rename Frames</strong> with pattern matching and <strong>Collapse Frames</strong> to merge consecutive entries and strip framework noise.</p>
              <div class="imp-pills">
                <span class="imp-pill"><i class="bi bi-pencil"></i> Rename Frames</span>
                <span class="imp-pill"><i class="bi bi-arrows-collapse"></i> Collapse Frames</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Lightbox -->
    <div v-if="lightboxImage" class="lightbox-overlay" @click="closeLightbox">
      <button class="lightbox-close" @click="closeLightbox"><i class="bi bi-x-lg"></i></button>
      <button v-if="lightboxHasPrev" class="lightbox-nav lightbox-prev" @click.stop="lightboxPrev"><i class="bi bi-chevron-left"></i></button>
      <img :src="lightboxImage.src" :alt="lightboxImage.caption" class="lightbox-img" @click.stop>
      <button v-if="lightboxHasNext" class="lightbox-nav lightbox-next" @click.stop="lightboxNext"><i class="bi bi-chevron-right"></i></button>
      <div class="lightbox-caption" @click.stop>{{ lightboxImage.caption }} &middot; {{ lightboxIndex + 1 }} / {{ lightboxFeature!.length }}</div>
    </div>
  </div>
</template>

<style scoped>
/* Hero */
.hero-banner {
  background: linear-gradient(135deg, #0f0f1a 0%, #1a1a2e 50%, #16213e 100%);
  padding: 44px 0;
  position: relative;
  overflow: hidden;
}

.hero-banner::before {
  content: '';
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 30% 50%, rgba(94, 100, 255, 0.12) 0%, transparent 50%),
    radial-gradient(circle at 70% 40%, rgba(168, 85, 247, 0.08) 0%, transparent 50%);
}

.hero-inner {
  max-width: 900px;
  margin: 0 auto;
  padding: 0 20px;
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 40px;
}

.hero-image-box {
  flex: 0 0 160px;
  width: 160px;
  height: 160px;
  border-radius: 20px;
  overflow: hidden;
  box-shadow: 0 16px 50px rgba(0, 0, 0, 0.4);
  border: 2px solid rgba(255, 255, 255, 0.1);
}

.hero-image-box img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.hero-text {
  text-align: left;
  flex: 1;
}

.hero-eyebrow {
  font-size: 0.7rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 3px;
  color: #a5a8ff;
  margin-bottom: 6px;
}

.hero-title {
  font-size: 2.4rem;
  font-weight: 800;
  color: #ffffff;
  line-height: 1.1;
  margin-bottom: 4px;
}

.hero-title span {
  background: linear-gradient(135deg, #5e64ff, #a855f7, #ec4899);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.hero-version {
  font-size: 0.95rem;
  color: rgba(255, 255, 255, 0.35);
  font-weight: 300;
  margin-bottom: 10px;
}

.hero-summary {
  font-size: 0.95rem;
  color: rgba(255, 255, 255, 0.5);
  line-height: 1.65;
  margin: 0;
}

/* Version Bar */
.version-bar {
  background: #ffffff;
  border-bottom: 1px solid #e9ecef;
  padding: 14px 0;
  position: sticky;
  top: 62px;
  z-index: 999;
}

.version-bar-inner {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.version-bar-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.version-bar-left h3 {
  font-size: 1rem;
  font-weight: 700;
  color: #343a40;
  margin: 0;
}

.version-select {
  appearance: none;
  background: #f8f9fa url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' fill='%236c757d' viewBox='0 0 16 16'%3E%3Cpath d='M7.247 11.14L2.451 5.658C1.885 5.013 2.345 4 3.204 4h9.592a1 1 0 0 1 .753 1.659l-4.796 5.48a1 1 0 0 1-1.506 0z'/%3E%3C/svg%3E") no-repeat right 12px center;
  border: 1px solid #dee2e6;
  border-radius: 8px;
  padding: 8px 36px 8px 14px;
  font-size: 0.9rem;
  font-weight: 600;
  color: #343a40;
  cursor: pointer;
  transition: border-color 0.2s;
}

.version-select:focus {
  outline: none;
  border-color: #5e64ff;
}

/* Get Started Panel */
.get-started {
  background: #f8f9fa;
  padding: 48px 0;
  border-bottom: 1px solid #e9ecef;
}

.get-started-inner {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px;
}

.gs-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}

.gs-title-row h2 {
  font-size: 1.3rem;
  font-weight: 700;
  color: #343a40;
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0;
}

.gs-title-row h2 i {
  color: #5e64ff;
}

.gs-more-link {
  font-size: 0.85rem;
  color: #5e64ff;
  text-decoration: none;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 4px;
}

.gs-more-link:hover {
  text-decoration: underline;
}

.gs-commands {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

.gs-cmd-card {
  background: #ffffff;
  border: 1px solid #e9ecef;
  border-radius: 14px;
  padding: 24px;
  transition: all 0.3s;
}

.gs-cmd-card:hover {
  border-color: #5e64ff;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.06);
}

.gs-cmd-top {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}

.gs-cmd-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.1rem;
}

.gs-cmd-icon.java {
  background: rgba(245, 158, 11, 0.12);
  color: #d97706;
}

.gs-cmd-icon.docker {
  background: rgba(59, 130, 246, 0.12);
  color: #2563eb;
}

.gs-cmd-label {
  font-size: 0.95rem;
  font-weight: 600;
  color: #343a40;
}

.gs-cmd-sub {
  font-size: 0.8rem;
  color: #6c757d;
  font-weight: 400;
}

.gs-cmd-sub a {
  color: #5e64ff;
  text-decoration: none;
  font-weight: 600;
}

.gs-cmd-sub a:hover {
  text-decoration: underline;
}

.gs-cmd-badge {
  margin-left: auto;
  background: rgba(16, 185, 129, 0.1);
  color: #059669;
  font-size: 0.65rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  padding: 3px 8px;
  border-radius: 4px;
}

.gs-code {
  background: #f1f3f5;
  border: 1px solid #e2e6ea;
  border-radius: 8px;
  padding: 14px 18px;
  font-family: 'JetBrains Mono', ui-monospace, monospace;
  font-size: 0.82rem;
  color: #343a40;
  display: flex;
  align-items: center;
  gap: 8px;
}

.gs-code .dollar {
  color: #adb5bd;
}

.gs-code .text {
  color: #343a40;
}

.gs-open {
  margin-top: 10px;
  margin-bottom: 0;
  font-size: 0.8rem;
  color: #6c757d;
}

.gs-open a {
  color: #5e64ff;
  text-decoration: none;
  font-weight: 600;
}

.gs-open a:hover {
  text-decoration: underline;
}

/* Feature Sections */
.feature-section {
  max-width: 1200px;
  margin: 0 auto;
  padding: 80px 20px;
}

.feature-section-bg {
  background: #f8f9fa;
}

.feature-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 60px;
  align-items: center;
}

.feature-row.reverse {
  direction: rtl;
}

.feature-row.reverse > * {
  direction: ltr;
}

.feature-number {
  font-size: 0.75rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 2px;
  color: #5e64ff;
  margin-bottom: 8px;
}

.feature-text h2 {
  font-size: 2rem;
  font-weight: 800;
  color: #343a40;
  margin-bottom: 16px;
  line-height: 1.2;
}

.feature-text p {
  font-size: 1.05rem;
  color: #6c757d;
  line-height: 1.8;
  margin-bottom: 16px;
}

.feature-text a {
  color: #5e64ff;
  font-weight: 600;
  text-decoration: none;
}

.feature-text a:hover {
  text-decoration: underline;
}

.feature-highlights {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 4px;
}

.feature-highlight {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: rgba(94, 100, 255, 0.08);
  color: #5e64ff;
  padding: 5px 12px;
  border-radius: 6px;
  font-size: 0.8rem;
  font-weight: 600;
}

.feature-visual {
  background: #f1f3f5;
  border-radius: 16px;
  aspect-ratio: 4/3;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #adb5bd;
  font-size: 0.9rem;
  border: 1px solid #e9ecef;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.06);
  gap: 8px;
}

/* Image Gallery */
.feature-gallery {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.gallery-main {
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid #e9ecef;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.06);
  cursor: pointer;
  position: relative;
  transition: box-shadow 0.3s;
}

.gallery-main:hover {
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.1);
}

.gallery-main img {
  width: 100%;
  display: block;
}

.gallery-caption {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  background: linear-gradient(transparent, rgba(0, 0, 0, 0.6));
  color: #ffffff;
  padding: 24px 16px 10px;
  font-size: 0.8rem;
  font-weight: 500;
}

.gallery-thumbs {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.gallery-thumb {
  height: 56px;
  border: 2px solid #e9ecef;
  border-radius: 6px;
  overflow: hidden;
  cursor: pointer;
  padding: 0;
  background: none;
  transition: all 0.2s;
  opacity: 0.6;
}

.gallery-thumb.active {
  border-color: #5e64ff;
  opacity: 1;
}

.gallery-thumb:hover {
  opacity: 1;
}

.gallery-thumb img {
  height: 100%;
  width: auto;
  display: block;
}

/* Lightbox */
.lightbox-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.85);
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
  cursor: zoom-out;
}

.lightbox-img {
  max-width: 95%;
  max-height: 90vh;
  border-radius: 8px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.5);
}

.lightbox-close {
  position: absolute;
  top: 20px;
  right: 24px;
  background: none;
  border: none;
  color: #ffffff;
  font-size: 1.5rem;
  cursor: pointer;
  opacity: 0.7;
  transition: opacity 0.2s;
  padding: 8px;
}

.lightbox-close:hover {
  opacity: 1;
}

.lightbox-nav {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.2);
  color: #ffffff;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.2rem;
  cursor: pointer;
  transition: all 0.2s;
  backdrop-filter: blur(4px);
}

.lightbox-nav:hover {
  background: rgba(255, 255, 255, 0.2);
}

.lightbox-prev {
  left: 20px;
}

.lightbox-next {
  right: 20px;
}

.lightbox-caption {
  position: absolute;
  bottom: 20px;
  left: 50%;
  transform: translateX(-50%);
  color: rgba(255, 255, 255, 0.7);
  font-size: 0.85rem;
  font-weight: 500;
  background: rgba(0, 0, 0, 0.4);
  padding: 6px 16px;
  border-radius: 20px;
  backdrop-filter: blur(4px);
  white-space: nowrap;
}

/* Improvements Section */
.improvements-section {
  background: #f8f9fa;
  padding: 60px 0 80px;
  border-top: 1px solid #e9ecef;
}

.improvements-inner {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px;
}

.improvements-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 28px;
}

.improvements-header h2 {
  font-size: 1.2rem;
  font-weight: 700;
  color: #343a40;
  margin: 0;
  white-space: nowrap;
  display: flex;
  align-items: center;
  gap: 8px;
}

.improvements-header h2 i {
  color: #10b981;
}

.improvements-header-line {
  flex: 1;
  height: 1px;
  background: #dee2e6;
}

.improvements-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
}

.improvement-tile {
  background: #ffffff;
  border: 1px solid #e9ecef;
  border-radius: 14px;
  overflow: hidden;
  transition: all 0.3s;
}

.improvement-tile:hover {
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.08);
  border-color: #10b981;
}

.imp-tile-gallery {
  border-bottom: 1px solid #e9ecef;
}

.imp-tile-main {
  cursor: pointer;
  overflow: hidden;
}

.imp-tile-main img {
  width: 100%;
  display: block;
  transition: transform 0.3s;
}

.imp-tile-main:hover img {
  transform: scale(1.02);
}

.imp-tile-thumbs {
  display: flex;
  gap: 4px;
  padding: 6px;
  background: #f8f9fa;
}

.imp-thumb {
  flex: 1;
  border: 2px solid #e9ecef;
  border-radius: 4px;
  overflow: hidden;
  cursor: pointer;
  padding: 0;
  background: none;
  opacity: 0.6;
  transition: all 0.2s;
}

.imp-thumb.active {
  border-color: #10b981;
  opacity: 1;
}

.imp-thumb:hover {
  opacity: 1;
}

.imp-thumb img {
  width: 100%;
  display: block;
}

.imp-tile-body {
  padding: 20px 24px;
}

.imp-tile-body h3 {
  font-size: 1.1rem;
  font-weight: 700;
  color: #343a40;
  margin-bottom: 8px;
}

.imp-tile-body p {
  font-size: 0.9rem;
  color: #6c757d;
  line-height: 1.6;
  margin: 0 0 12px;
}

.imp-tile-body a {
  color: #5e64ff;
  text-decoration: none;
  font-weight: 600;
}

.imp-tile-body a:hover {
  text-decoration: underline;
}

.imp-pills {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.imp-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  background: rgba(16, 185, 129, 0.08);
  color: #10b981;
  padding: 4px 10px;
  border-radius: 5px;
  font-size: 0.75rem;
  font-weight: 600;
}

/* Responsive */
@media (max-width: 768px) {
  .hero-inner {
    flex-direction: column;
    text-align: center;
    gap: 20px;
  }

  .hero-text {
    text-align: center;
  }

  .hero-title {
    font-size: 1.8rem;
  }

  .feature-row,
  .feature-row.reverse {
    grid-template-columns: 1fr;
    gap: 30px;
    direction: ltr;
  }

  .feature-section {
    padding: 50px 20px;
  }

  .gs-commands {
    grid-template-columns: 1fr;
  }

  .gs-title-row {
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
  }

  .improvements-grid {
    grid-template-columns: 1fr;
  }
}
</style>

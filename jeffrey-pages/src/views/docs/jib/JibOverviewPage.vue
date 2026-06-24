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
import { onMounted } from 'vue';
import DocsCallout from '@/components/docs/DocsCallout.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';
const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'how-it-works', text: 'How It Works', level: 2 },
  { id: 'runtime-kill-switch', text: 'Runtime Kill Switch', level: 2 },
  { id: 'limitations', text: 'Limitations', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
      <DocsPageHeader
        title="Jeffrey JIB"
        icon="bi bi-box-seam"
      />

      <div class="docs-content">
        <p><strong>Jeffrey JIB</strong> is a pair of
          <a href="https://github.com/GoogleContainerTools/jib" target="_blank" rel="noopener">JIB</a>
          plugin extensions (Gradle and Maven) that wrap the container entrypoint so Jeffrey
          profiling is initialised before the app starts &mdash; without forcing operators to
          override the container <code>command:</code> in Kubernetes YAML.</p>

        <p>This page explains how the extension works. For the full property reference see
          <router-link to="/docs/jib/configuration">Configuration</router-link>, and for the
          Gradle/Maven wiring see <router-link to="/docs/jib/setup">Build Setup</router-link>.</p>

        <DocsCallout type="info">
          <strong>Open Source Library:</strong> Jeffrey JIB lives in the Jeffrey monorepo under
          <a href="https://github.com/petrbouda/jeffrey/tree/master/utilities/jeffrey-jib" target="_blank" rel="noopener">utilities/jeffrey-jib</a>
          and is published to Maven Central as
          <code>cafe.jeffrey-analyst:jeffrey-jib-gradle</code> and
          <code>cafe.jeffrey-analyst:jeffrey-jib-maven</code>.
        </DocsCallout>

        <h2 id="how-it-works">How It Works</h2>
        <p>At image-build time, the extension modifies JIB's <code>ContainerBuildPlan</code>:</p>

        <div class="feature-list">
          <div class="feature-item">
            <i class="bi bi-check-circle-fill"></i>
            <div>Installs a small shell wrapper at <code>/usr/local/bin/jeffrey-entrypoint</code> as a new image layer.</div>
          </div>
          <div class="feature-item">
            <i class="bi bi-check-circle-fill"></i>
            <div>Replaces the image <code>ENTRYPOINT</code> with the wrapper.</div>
          </div>
          <div class="feature-item">
            <i class="bi bi-check-circle-fill"></i>
            <div>Moves JIB's auto-derived <code>java -cp @/app/jib-classpath-file &lt;MainClass&gt;</code> into <code>CMD</code>.</div>
          </div>
          <div class="feature-item">
            <i class="bi bi-check-circle-fill"></i>
            <div>Preserves JIB's main-class detection, classpath-file assembly, <code>jvmFlags</code>, base image, and target architecture.</div>
          </div>
        </div>

        <p>At container start, the wrapper runs <code>provisioner init</code> &mdash; resolved from
          <code>${JEFFREY_HOME}/libs/current/provisioner-&lt;arch&gt;</code> on a shared volume populated by
          Jeffrey Hub's <code>copy-libs</code> feature &mdash; and then <code>exec</code>s the original JIB
          command with the provisioner-produced argfile inserted right after the <code>java</code> binary.
          If the shared-volume root is not configured at runtime (neither <code>JEFFREY_HOME</code> nor
          <code>JEFFREY_PROVISIONER_PATH</code> is set), the wrapper logs a warning and skips init entirely &mdash;
          see <a href="#runtime-kill-switch">Runtime Kill Switch</a>.</p>

        <DocsCallout type="info">
          <strong>Why a shared volume?</strong> The extension does <strong>not</strong> bake Jeffrey
          binaries into your image. It relies on a Jeffrey Hub running elsewhere in the cluster
          with <code>copy-libs.enabled=true</code> to populate the shared <code>jeffrey-home</code>
          volume that your app pods also mount. Keeps the extension JAR tiny and versioning automatic.
        </DocsCallout>

        <h2 id="runtime-kill-switch">Runtime Kill Switch</h2>
        <p><strong>Explicit opt-out.</strong> Set <code>JEFFREY_ENABLED=false</code> (or <code>0</code>,
          <code>no</code>, <code>off</code>, case-insensitive) in the container env to bypass profiling
          entirely. The wrapper skips <code>provisioner init</code>, async-profiler, and argfile
          injection, and <code>exec</code>s the JIB-produced <code>java</code> command verbatim &mdash;
          identical behaviour to a non-instrumented image, no rebuild required.</p>

        <p>Useful for emergency disablement, per-pod opt-out, dev/local runs without the shared
          volume, and A/B comparisons.</p>

        <p><strong>Implicit fallthrough (fail-open).</strong> If neither <code>JEFFREY_HOME</code> nor
          <code>JEFFREY_PROVISIONER_PATH</code> is set at container start, the wrapper logs a warning to
          stderr (&ldquo;Jeffrey is disabled, starting application without profiling&rdquo;) and
          <code>exec</code>s the JIB command verbatim. Misconfiguration can never prevent an app
          from booting &mdash; the worst case is profiling silently turning off, which the warning
          surfaces in the pod logs.</p>

        <DocsCallout type="info">
          The &ldquo;app still starts&rdquo; guarantee holds only when a downstream command is
          actually present in the container. If the JIB CMD is missing entirely, the wrapper has
          nothing to exec and still exits non-zero &mdash; a configuration error, not a profiling
          concern.
        </DocsCallout>

        <h2 id="limitations">Limitations</h2>
        <div class="feature-list feature-list-warning">
          <div class="feature-item feature-item-warning">
            <i class="bi bi-exclamation-triangle-fill"></i>
            <div><strong>Requires a POSIX shell in the base image.</strong> True distroless images
              (<code>gcr.io/distroless/java-*</code>) lack <code>/bin/sh</code> and are incompatible.
              Use the status-quo Kubernetes <code>command:</code> pattern there.</div>
          </div>
          <div class="feature-item feature-item-warning">
            <i class="bi bi-exclamation-triangle-fill"></i>
            <div><strong>Requires Jeffrey Hub elsewhere in the cluster</strong> with
              <code>copy-libs.enabled=true</code>, writing to the shared <code>jeffrey-home</code>
              volume your app pods mount. Without it, the wrapper cannot locate
              <code>provisioner-&lt;arch&gt;</code> at runtime.</div>
          </div>
        </div>
      </div>

      <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

/* Feature List */
.feature-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  margin: 1.5rem 0;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem 1rem;
  background: #f0fdf4;
  border-radius: 8px;
  border: 1px solid #bbf7d0;
}

.feature-item i {
  color: #10b981;
  font-size: 1rem;
}

.feature-item div {
  font-size: 0.875rem;
  color: #374151;
  line-height: 1.5;
}

.feature-item strong {
  color: #1f2937;
}

.feature-item code {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 0.82rem;
  padding: 0.05rem 0.35rem;
  background: rgba(16, 185, 129, 0.1);
  border-radius: 4px;
}

/* Warning variant */
.feature-list-warning .feature-item-warning {
  background: #fffbeb;
  border-color: #fde68a;
}

.feature-list-warning .feature-item-warning i {
  color: #d97706;
}

.feature-list-warning .feature-item-warning code {
  background: rgba(217, 119, 6, 0.1);
}
</style>

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
  { id: 'gradle-setup', text: 'Gradle Setup', level: 2 },
  { id: 'maven-setup', text: 'Maven Setup', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
      <DocsPageHeader
        title="JIB Build Setup"
        icon="bi bi-hammer"
      />

      <div class="docs-content">
        <p>Wire the extension into your JIB build. The one property that must always be reachable is
          <code>jeffreyHome</code> &mdash; either baked as an image <code>ENV</code> default at build
          time (shown below) or provided at runtime via a <code>JEFFREY_HOME</code> env var on the pod.
          See <router-link to="/docs/jib/configuration">Configuration</router-link> for the full
          property reference.</p>

        <h2 id="gradle-setup">Gradle Setup</h2>
        <p>Add the extension as a dependency of the JIB Gradle plugin, then reference it from
          <code>pluginExtensions</code>. If <code>jeffreyHome</code> is reachable neither here nor at
          runtime, the wrapper logs a warning and starts the app without profiling.</p>

        <div class="code-block">
          <pre><code>jib {
  pluginExtensions {
    pluginExtension {
      implementation = "cafe.jeffrey.jib.gradle.JeffreyJibGradleExtension"
      properties = mapOf(
        "jeffreyHome" to "/shared/disk/jeffrey",
      )
    }
  }
}</code></pre>
        </div>

        <p>This builds an image whose wrapper resolves the provisioner from
          <code>${JEFFREY_HOME}/libs/current/provisioner-&lt;arch&gt;</code> on the shared volume you
          mount at that path. Every other property has a sensible default; you only set them to
          override.</p>

        <DocsCallout type="warning">
          <strong><code>jeffreyHome</code> must point at a shared volume / disk.</strong>
          Jeffrey Hub (with <code>copy-libs.enabled=true</code>) writes the provisioner binaries and
          libs to this path, and every monitored application pod must mount the <em>same</em>
          volume at the <em>same</em> path so its entrypoint wrapper can resolve
          <code>${JEFFREY_HOME}/libs/current/provisioner-&lt;arch&gt;</code> at container start. A
          host-local directory or a per-pod ephemeral volume will not work &mdash; both endpoints
          need to see the bytes Jeffrey Hub published.
        </DocsCallout>

        <p>Add explicit overrides via the string <code>properties</code> DSL &mdash; for example,
          gating via a Gradle property and pointing at a non-default shared volume. Referencing the
          key constants on <code>JeffreyJibConfig</code> keeps the build file typo-proof and
          auto-completable in the IDE:</p>

        <div class="code-block">
          <pre><code>import cafe.jeffrey.jib.JeffreyJibConfig
import cafe.jeffrey.jib.gradle.JeffreyJibGradleExtension

jib {
  pluginExtensions {
    pluginExtension {
      implementation = JeffreyJibGradleExtension::class.java.name
      properties = mapOf(
        JeffreyJibConfig.JEFFREY_HOME to "/shared/disk/jeffrey",
        JeffreyJibConfig.OVERRIDE_CONFIG to "/jeffrey/jeffrey-overrides.conf",
      )
    }
  }
}</code></pre>
        </div>

        <p>The string form (<code>"enabled"</code>, <code>"jeffreyHome"</code>, …) works just as
          well and avoids the imports if you prefer a zero-dependency build script. Both are
          equivalent at runtime.</p>

        <DocsCallout type="info">
          <strong>Why the <code>properties</code> DSL?</strong> It works on every Gradle version
          JIB supports. The typed <code>configuration(Action&lt;JeffreyJibConfig&gt;) { … }</code> form
          is also accepted but is fragile across Gradle versions &mdash; prefer
          <code>properties</code> for portability.
        </DocsCallout>

        <p>Add the artifact to your build-script classpath (<code>buildscript.dependencies</code> or
          the <code>jib</code> plugin's <code>dependencies</code> block, depending on how you apply the
          plugin).</p>

        <h2 id="maven-setup">Maven Setup</h2>
        <p>Attach the extension as a plugin dependency and reference it from
          <code>pluginExtensions</code>. As with Gradle, <code>jeffreyHome</code> must be reachable
          either here (baked as an image <code>ENV</code> default) or at runtime via a
          <code>JEFFREY_HOME</code> env var &mdash; otherwise the wrapper warns and starts the app
          without profiling.</p>

        <div class="code-block">
          <pre><code>&lt;plugin&gt;
  &lt;groupId&gt;com.google.cloud.tools&lt;/groupId&gt;
  &lt;artifactId&gt;jib-maven-plugin&lt;/artifactId&gt;
  &lt;dependencies&gt;
    &lt;dependency&gt;
      &lt;groupId&gt;cafe.jeffrey-analyst&lt;/groupId&gt;
      &lt;artifactId&gt;jeffrey-jib-maven&lt;/artifactId&gt;
      &lt;version&gt;${jeffrey-jib.version}&lt;/version&gt;
    &lt;/dependency&gt;
  &lt;/dependencies&gt;
  &lt;configuration&gt;
    &lt;pluginExtensions&gt;
      &lt;pluginExtension&gt;
        &lt;implementation&gt;cafe.jeffrey.jib.maven.JeffreyJibMavenExtension&lt;/implementation&gt;
        &lt;properties&gt;
          &lt;jeffreyHome&gt;/shared/disk/jeffrey&lt;/jeffreyHome&gt;
        &lt;/properties&gt;
      &lt;/pluginExtension&gt;
    &lt;/pluginExtensions&gt;
  &lt;/configuration&gt;
&lt;/plugin&gt;</code></pre>
        </div>

        <p>Add more properties the same way &mdash; each property name matches a setter on
          <code>JeffreyJibConfig</code>:</p>

        <div class="code-block">
          <pre><code>&lt;properties&gt;
  &lt;enabled&gt;true&lt;/enabled&gt;
  &lt;jeffreyHome&gt;/shared/disk/jeffrey&lt;/jeffreyHome&gt;
  &lt;overrideConfig&gt;/jeffrey/jeffrey-overrides.conf&lt;/overrideConfig&gt;
&lt;/properties&gt;</code></pre>
        </div>
      </div>

      <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

/* Code Block */
.code-block {
  margin: 1rem 0;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
}

.code-block pre {
  margin: 0;
  padding: 1rem;
  background: #1e293b;
  overflow-x: auto;
}

.code-block code {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 0.85rem;
  color: #e2e8f0;
  line-height: 1.5;
}
</style>

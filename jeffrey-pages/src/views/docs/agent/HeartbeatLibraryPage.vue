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
import { onMounted } from 'vue';
import DocsCallout from '@/components/docs/DocsCallout.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';
const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'overview', text: 'Overview', level: 2 },
  { id: 'on-by-default', text: 'On by Default', level: 2 },
  { id: 'with-the-agent', text: 'With the Jeffrey Agent', level: 3 },
  { id: 'spring-boot', text: 'Spring Boot', level: 2 },
  { id: 'plain-java', text: 'Plain Java', level: 2 },
  { id: 'configuration', text: 'Configuration', level: 2 },
  { id: 'failure-behaviour', text: 'What Happens When It Cannot Report', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
      <DocsPageHeader
        title="Heartbeat Library"
        icon="bi bi-heart-pulse"
      />

      <div class="docs-content">
        <h2 id="overview">Overview</h2>
        <p><strong>jeffrey-heartbeat</strong> reports to a Jeffrey Hub that your JVM is alive, and tells it when the JVM stopped. It writes two files into the session directory: a timestamp it rewrites every few seconds, and a clean-exit marker on shutdown.</p>
        <p>That is the whole of it. It emits no events, instruments nothing, and brings one dependency: the SLF4J API it logs through.</p>
        <p>A provisioned JVM does not need it: the <router-link to="/docs/agent/jeffrey-agent">Jeffrey Agent</router-link>, bundled in the Provisioner and attached by default, writes the same files with no change to the application. The library is the alternative for a deployment that switches the agent off (<code>JEFFREY_AGENT_ENABLED=false</code>), and it is safe to carry alongside the agent: when the agent already reports, the library stays inert.</p>

        <DocsCallout type="info">
          A provisioned application configures itself. Jeffrey Provisioner passes <code>-Djeffrey.heartbeat.dir</code> in the argfile the JVM starts with, and exports the matching <code>JEFFREY_HEARTBEAT_DIR</code> into its <code>.env</code> for a deployment that sources one — so on Spring Boot the whole integration is one dependency and no code.
        </DocsCallout>

        <h2 id="on-by-default">On by Default</h2>
        <p>The library <strong>reports whenever it is on the class path</strong> and the Jeffrey Agent is not already reporting. The Provisioner has no setting for the library itself: it names the directory, through <code>-Djeffrey.heartbeat.dir</code>, and decides only whether the agent is attached (<code>jeffrey-agent.enabled</code>). With no directory named — an application that was not provisioned, such as one running on a developer's laptop — the library starts inert, which is what makes the dependency safe to leave in.</p>
        <p>Switching it off is the <strong>application's decision</strong>: set <code>jeffrey.heartbeat.enabled=false</code> in <code>application.yaml</code> or as a system property, or <code>JEFFREY_HEARTBEAT_ENABLED=false</code> in the application's own deployment. Set as a system property or environment variable, the same switch silences the Jeffrey Agent too — the agent does not read <code>application.yaml</code>.</p>
        <pre class="doc-code"><code>jeffrey:
  heartbeat:
    enabled: false</code></pre>
        <p>The Hub needs no word about either choice. It holds a session to the heartbeat deadline only once the session has written a liveness file; a session that writes none within the startup grace is flagged as missing its heartbeat, is not taken as live, and is closed when the instance's next session appears. See <router-link to="/docs/hub/recording-sessions/lifecycle">Session Lifecycle</router-link> for what the Hub does in each case.</p>

        <h3 id="with-the-agent">With the Jeffrey Agent</h3>
        <p>Once the agent is beating it sets the system property <code>jeffrey.heartbeat.agent=true</code>. The library — and so the Spring Boot starter — sees it, stays inert and logs that the agent already reports, so there is only ever one writer of the liveness files. An application that carries the library needs no change when the agent is on, and reports on its own the moment a deployment switches the agent off.</p>

        <h2 id="spring-boot">Spring Boot</h2>
        <p>One dependency, no code:</p>
        <pre class="doc-code"><code>&lt;dependency&gt;
    &lt;groupId&gt;cafe.jeffrey-analyst&lt;/groupId&gt;
    &lt;artifactId&gt;jeffrey-heartbeat-spring-boot-starter&lt;/artifactId&gt;
&lt;/dependency&gt;</code></pre>
        <p>The auto-configuration starts the heartbeat from what the Provisioner exported and closes it when the application context shuts down — which is what writes the clean-exit marker, so the Hub finishes the session at once instead of waiting for the heartbeat to go stale.</p>
        <p>Declare your own <code>JeffreyHeartbeat</code> bean and the auto-configuration backs off.</p>
        <p>The auto-configuration's only condition is <code>jeffrey.heartbeat.enabled</code>, which defaults to <code>true</code> when unset — it needs no other switch to be on. Setting it to <code>false</code> leaves the application without a heartbeat bean.</p>
        <p>Jeffrey Hub reports its own liveness with this library. Its image is built with <router-link to="/docs/hub/deployment/jeffrey-jib">jeffrey-jib</router-link>, so a pod that sets <code>JEFFREY_ENABLED=true</code> runs the Hub as a profiled JVM like any other application, and its session is reported the same way. The Hub declares the bean in its own configuration rather than through the starter, on the same terms: on unless <code>jeffrey.heartbeat.enabled=false</code>, and inert when the Hub is started without the Provisioner and so has no directory.</p>

        <h2 id="plain-java">Plain Java</h2>
        <p>Without Spring, one call at startup:</p>
        <pre class="doc-code"><code>&lt;dependency&gt;
    &lt;groupId&gt;cafe.jeffrey-analyst&lt;/groupId&gt;
    &lt;artifactId&gt;jeffrey-heartbeat&lt;/artifactId&gt;
&lt;/dependency&gt;</code></pre>
        <pre class="doc-code"><code>public static void main(String[] args) {
    JeffreyHeartbeat.startFromEnvironment();
    // ... your application
}</code></pre>
        <p><code>startFromEnvironment()</code> registers a JVM shutdown hook that writes the clean-exit marker, so there is nothing to close. An application that owns its own lifecycle can call <code>JeffreyHeartbeat.start(settings)</code> instead and close the returned instance itself — that is what the Spring starter does, because inside a container a shutdown hook would be a second thing racing to write the same file.</p>

        <h2 id="configuration">Configuration</h2>
        <p>Every value is optional. A system property wins over an environment variable, and Spring Boot's relaxed binding maps the variables onto the properties with nothing declared anywhere.</p>
        <div class="table-responsive">
          <table class="table table-sm table-hover mb-0">
            <thead>
              <tr>
                <th>Environment variable</th>
                <th>Spring property</th>
                <th>Default</th>
                <th>Description</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td><code>JEFFREY_HEARTBEAT_ENABLED</code></td>
                <td><code>jeffrey.heartbeat.enabled</code></td>
                <td><code>true</code></td>
                <td>Whether liveness is reported at all. The application's own switch — nothing in Jeffrey sets it, so an application carrying the library reports unless it sets <code>false</code>. As a system property or environment variable it silences the Jeffrey Agent too</td>
              </tr>
              <tr>
                <td><code>JEFFREY_HEARTBEAT_DIR</code></td>
                <td><code>jeffrey.heartbeat.dir</code></td>
                <td>—</td>
                <td>Where the liveness files go. Passed by the Provisioner in the argfile, and exported into the <code>.env</code> too</td>
              </tr>
              <tr>
                <td><code>JEFFREY_CURRENT_SESSION</code></td>
                <td>—</td>
                <td>—</td>
                <td>Fallback: the session directory, whose <code>.heartbeat</code> folder is the same place. Lets a session provisioned by an older CLI still resolve</td>
              </tr>
              <tr>
                <td><code>JEFFREY_HEARTBEAT_INTERVAL</code></td>
                <td><code>jeffrey.heartbeat.interval</code></td>
                <td><code>5s</code></td>
                <td>How often the heartbeat is rewritten, in milliseconds. Must stay below the Hub's staleness threshold</td>
              </tr>
            </tbody>
          </table>
        </div>

        <h2 id="failure-behaviour">What Happens When It Cannot Report</h2>
        <p>Nothing fails your application. The library is built so the dependency is safe to leave in permanently:</p>
        <ul>
          <li><strong>Not provisioned at all</strong> — no directory is resolved, so the heartbeat starts inert. The same jar runs unchanged on a developer's laptop and under a Provisioner.</li>
          <li><strong>A malformed setting</strong> — logged once and replaced by the default, rather than throwing during startup.</li>
          <li><strong>The directory cannot be created, or the volume is unwritable</strong> — logged and skipped; the next beat recovers on its own.</li>
          <li><strong>The clean-exit marker cannot be written</strong> — the session still finishes, from the last heartbeat, one staleness threshold later.</li>
        </ul>
        <p>The thread it uses is a daemon, so reporting liveness is never the reason a JVM stays up.</p>

        <DocsCallout type="info">
          The library brings <strong>one dependency</strong>, deliberately kept to that: it is compiled into applications Jeffrey profiles but does not own, so anything it brought along would be a version those applications did not choose. <code>slf4j-api</code> is the exception, because it is a facade with no implementation — it ships no binding, so the handful of lines it writes go through <em>your</em> logging configuration, under the <code>cafe.jeffrey.heartbeat</code> logger, and are silenced or routed like any other.
        </DocsCallout>
      </div>

      <DocsNavFooter />
  </article>
</template>

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
  { id: 'where-it-lives', text: 'Where the Jar Lives', level: 2 },
  { id: 'configuration', text: 'Configuration', level: 2 },
  { id: 'switching-off', text: 'Switching It Off', level: 2 },
  { id: 'coexistence', text: 'Alongside the Heartbeat Library', level: 2 },
  { id: 'failure-behaviour', text: 'What Happens When It Cannot Report', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
      <DocsPageHeader
        title="Jeffrey Agent"
        icon="bi bi-cpu"
      />

      <div class="docs-content">
        <h2 id="overview">Overview</h2>
        <p><strong>jeffrey-agent</strong> is a Java agent that reports to a Jeffrey Hub that your JVM is alive, and tells it when the JVM stopped. It runs the very same code the <router-link to="/docs/agent/heartbeat-library">Heartbeat Library</router-link> runs — the small <code>jeffrey-heartbeat-core</code> module, packaged inside the agent under its own package name so it never clashes with a copy the application carries — and writes the same files:</p>
        <ul>
          <li><code>&lt;session&gt;/.heartbeat/heartbeat</code> — epoch milliseconds, rewritten every 5&nbsp;seconds</li>
          <li><code>&lt;session&gt;/.heartbeat/finished</code> — the clean-exit marker, written when the JVM shuts down cleanly</li>
        </ul>
        <p>That is all it does. It touches no application bytecode, brings no dependency onto the application's class path, runs on Java 21 and newer, and logs through <code>System.Logger</code>, so its few lines follow whatever the JVM's logging is routed to.</p>

        <DocsCallout type="info">
          The application needs no dependency and no code. Every <router-link to="/docs/jib">jeffrey-jib</router-link> image already carries the agent at <code>/opt/jeffrey/jeffrey-agent.jar</code>, next to the Provisioner and async-profiler, so there is nothing separate to download. Outside jeffrey-jib, take <code>jeffrey-agent.jar</code> from the Jeffrey release and point the Provisioner at it, as you do for async-profiler.
        </DocsCallout>

        <h2 id="on-by-default">On by Default</h2>
        <p>Every provisioned session reports liveness through the agent unless the deployment switches it off. The Hub and Microscope no longer read heartbeats: session order and file names decide everything, see <router-link to="/docs/hub/recording-sessions/lifecycle">Session Lifecycle</router-link>.</p>
        <p>The Provisioner's INFO verdict line states the choice for every session as <code>jeffrey_agent=true</code> or <code>jeffrey_agent=false</code>.</p>

        <h2 id="where-it-lives">Where the Jar Lives</h2>
        <p>The agent is located exactly like async-profiler: the Provisioner is given a path, checks the file is there, and the JVM loads the jar from that path. Nothing is copied into the session, so the session directory holds only what the JVM writes.</p>
        <ul>
          <li><strong>jeffrey-jib images</strong> — the extension bakes <code>/opt/jeffrey/jeffrey-agent.jar</code> into the image, from the same Jeffrey release as the Provisioner, and sets <code>JEFFREY_AGENT_PATH</code> to it.</li>
          <li><strong>Anywhere else</strong> — download <code>jeffrey-agent.jar</code> from the Jeffrey release and set <code>jeffrey-agent.path</code> (or <code>JEFFREY_AGENT_PATH</code>) to where you put it.</li>
        </ul>
        <p>At <code>init</code>, with the agent enabled, the argfile attaches it and names the directory its liveness files go to:</p>
        <pre class="doc-code"><code>-Djeffrey.heartbeat.dir=&lt;session&gt;/.heartbeat
-javaagent:/opt/jeffrey/jeffrey-agent.jar</code></pre>
        <pre class="doc-code"><code>&lt;session&gt;/
└── .heartbeat/                  # liveness files the agent writes
    ├── heartbeat
    └── finished</code></pre>
        <p>Both lines also reach <code>JDK_JAVA_OPTIONS</code> when <code>jdk-java-options</code> is on. The <code>-Djeffrey.heartbeat.dir</code> line is the one the Provisioner always wrote; the agent and the library read the same property.</p>

        <h2 id="configuration">Configuration</h2>
        <p>Two Provisioner settings decide whether the agent is attached and from where. As with every other setting, the environment variable wins over the HOCON file.</p>
        <pre class="doc-code"><code>jeffrey-agent {
  enabled = true
  path = "/opt/jeffrey/jeffrey-agent.jar"   # baked by jeffrey-jib; set it yourself elsewhere
}</code></pre>
        <div class="table-responsive">
          <table class="table table-sm table-hover mb-0">
            <thead>
              <tr>
                <th>HOCON key</th>
                <th>Environment variable</th>
                <th>Default</th>
                <th>Description</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td><code>jeffrey-agent.enabled</code></td>
                <td><code>JEFFREY_AGENT_ENABLED</code></td>
                <td><code>true</code></td>
                <td>Whether the Provisioner attaches the agent with <code>-javaagent</code></td>
              </tr>
              <tr>
                <td><code>jeffrey-agent.path</code></td>
                <td><code>JEFFREY_AGENT_PATH</code></td>
                <td>—</td>
                <td>The agent jar the JVM loads. Baked into the image by jeffrey-jib; set it yourself everywhere else. Unset, or naming a file that is not there, the session runs without the agent and a warning is logged</td>
              </tr>
            </tbody>
          </table>
        </div>
        <p>Inside the JVM the agent reads the same settings as the library; a system property wins over its environment variable:</p>
        <div class="table-responsive">
          <table class="table table-sm table-hover mb-0">
            <thead>
              <tr>
                <th>System property</th>
                <th>Environment variable</th>
                <th>Default</th>
                <th>Description</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td><code>jeffrey.heartbeat.dir</code></td>
                <td><code>JEFFREY_HEARTBEAT_DIR</code></td>
                <td>—</td>
                <td>Where the liveness files go. Passed by the Provisioner in the argfile</td>
              </tr>
              <tr>
                <td>—</td>
                <td><code>JEFFREY_CURRENT_SESSION</code></td>
                <td>—</td>
                <td>Fallback: the session directory, whose <code>.heartbeat</code> folder is the same place</td>
              </tr>
              <tr>
                <td><code>jeffrey.heartbeat.interval</code></td>
                <td><code>JEFFREY_HEARTBEAT_INTERVAL</code></td>
                <td><code>5s</code></td>
                <td>How often the heartbeat is rewritten, in milliseconds. Must stay below the Hub's staleness threshold</td>
              </tr>
              <tr>
                <td><code>jeffrey.heartbeat.enabled</code></td>
                <td><code>JEFFREY_HEARTBEAT_ENABLED</code></td>
                <td><code>true</code></td>
                <td>The application's own off switch. <code>false</code> leaves the agent attached but silent, exactly as it silences the library</td>
              </tr>
            </tbody>
          </table>
        </div>

        <h2 id="switching-off">Switching It Off</h2>
        <p>Set <code>jeffrey-agent.enabled = false</code> in the Provisioner configuration, or <code>JEFFREY_AGENT_ENABLED=false</code> in the deployment. No <code>-javaagent</code> is added, even in an image that carries the jar, and the Provisioner logs:</p>
        <pre class="doc-code"><code>Jeffrey agent DISABLED, liveness is reported only if the application carries jeffrey-heartbeat</code></pre>
        <p>Liveness then comes only from the application itself, through <router-link to="/docs/agent/heartbeat-library">jeffrey-heartbeat</router-link> or <code>jeffrey-heartbeat-spring-boot-starter</code>. With neither, the session writes no liveness files, which changes nothing for the Hub or Microscope: neither reads heartbeats.</p>

        <h2 id="coexistence">Alongside the Heartbeat Library</h2>
        <p>An application that already carries the library is safe with the agent on. Once the agent is beating it sets the system property <code>jeffrey.heartbeat.agent=true</code>; the library — and so the Spring Boot starter — sees it, stays inert, and logs that the agent already reports. There is only ever one writer of the liveness files.</p>

        <h2 id="failure-behaviour">What Happens When It Cannot Report</h2>
        <p>Nothing fails your application, and nothing fails provisioning:</p>
        <ul>
          <li><strong>No agent path is configured, or the jar is not there</strong> — the session is provisioned without the agent and a warning is logged; startup goes on.</li>
          <li><strong>No heartbeat directory is resolved</strong> — the agent starts inert.</li>
          <li><strong>The heartbeat directory cannot be created</strong> — a warning is logged and the agent stays inert for this JVM.</li>
          <li><strong>A beat cannot be written</strong> — skipped; the next beat recovers on its own.</li>
          <li><strong>The clean-exit marker cannot be written</strong> — the session still finishes, from the last heartbeat, one staleness threshold later.</li>
        </ul>
        <p>The agent never fails the JVM, and its thread is a daemon, so reporting liveness is never the reason a JVM stays up.</p>
      </div>

      <DocsNavFooter />
  </article>
</template>

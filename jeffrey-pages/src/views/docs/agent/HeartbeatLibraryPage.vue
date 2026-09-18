<script setup lang="ts">
import { onMounted } from 'vue';
import DocsCallout from '@/components/docs/DocsCallout.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';
const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'overview', text: 'Overview', level: 2 },
  { id: 'declaring-it', text: 'Declaring That a Session Reports', level: 2 },
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

        <DocsCallout type="info">
          A provisioned application configures itself. Jeffrey Provisioner passes <code>-Djeffrey.heartbeat.dir</code> and <code>-Djeffrey.heartbeat.enabled</code> in the argfile the JVM starts with, and exports the matching <code>JEFFREY_HEARTBEAT_*</code> variables into its <code>.env</code> for a deployment that sources one — so on Spring Boot the whole integration is one dependency and no code.
        </DocsCallout>

        <h2 id="declaring-it">Declaring That a Session Reports</h2>
        <p>Whether this library is on an application's class path is a <strong>build-time fact</strong>, and the Provisioner only writes JVM arguments — it cannot detect it. So the session declares it, and the declaration travels three ways: into the argfile as <code>-Djeffrey.heartbeat.enabled</code>, into the <code>.env</code> for a deployment that sources one, and into the session marker the Hub reconciles.</p>
        <p><strong>It is off by default</strong>, and the asymmetry is deliberate. A session that declares nothing is simply finished later — when the instance's next session appears. A session that declares liveness and then reports none is held to a deadline it cannot meet, and the Hub marks it finished at its own start timestamp seconds after the JVM came up, while the profiler is still writing into it. Once the dependency is actually there, turn it on:</p>
        <pre class="doc-code"><code>heartbeat { enabled = true }</code></pre>
        <p>See <router-link to="/docs/hub/recording-sessions/lifecycle">Session Lifecycle</router-link> for what the Hub does with each answer.</p>

        <h2 id="spring-boot">Spring Boot</h2>
        <p>One dependency, no code:</p>
        <pre class="doc-code"><code>&lt;dependency&gt;
    &lt;groupId&gt;cafe.jeffrey-analyst&lt;/groupId&gt;
    &lt;artifactId&gt;jeffrey-heartbeat-spring-boot-starter&lt;/artifactId&gt;
&lt;/dependency&gt;</code></pre>
        <p>The auto-configuration starts the heartbeat from what the Provisioner exported and closes it when the application context shuts down — which is what writes the clean-exit marker, so the Hub finishes the session at once instead of waiting for the heartbeat to go stale.</p>
        <p>Declare your own <code>JeffreyHeartbeat</code> bean and the auto-configuration backs off.</p>
        <p>The auto-configuration is gated on <code>JEFFREY_ENABLED=true</code>, the master switch of a <router-link to="/docs/hub/deployment/jeffrey-jib">jeffrey-jib</router-link> container, which Spring Boot binds to <code>jeffrey.enabled</code>. There is no default: a container that leaves it unset, or sets it to <code>false</code>, gets no heartbeat bean at all. A deployment that runs the Provisioner without jeffrey-jib sets the variable itself, or passes <code>-Djeffrey.enabled=true</code>.</p>
        <p>Jeffrey Hub reports its own liveness with this library. Its image is built with jeffrey-jib, so a pod that sets <code>JEFFREY_ENABLED=true</code> runs the Hub as a profiled JVM like any other application, and a session that declares <code>heartbeat.enabled = true</code> for it is reported the same way. The Hub declares the bean in its own configuration rather than through the starter, and creates it only when <code>jeffrey.heartbeat.enabled=true</code> is present — which the Provisioner writes into the argfile on exactly that path. A Hub started without profiling has no such property and no heartbeat.</p>

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
                <td><code>JEFFREY_ENABLED</code></td>
                <td><code>jeffrey.enabled</code></td>
                <td>—</td>
                <td>Spring Boot starter only. The master switch of a jeffrey-jib container; the auto-configuration contributes nothing unless it is <code>true</code>. Plain-Java use ignores it</td>
              </tr>
              <tr>
                <td><code>JEFFREY_HEARTBEAT_ENABLED</code></td>
                <td><code>jeffrey.heartbeat.enabled</code></td>
                <td><code>true</code></td>
                <td>Whether liveness is reported at all. The library's own default is <code>true</code>, so an application that sets the directory reports; the Provisioner passes what the session declared, which is <code>false</code> unless the deployment said otherwise</td>
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

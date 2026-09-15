<script setup lang="ts">
import { onMounted } from 'vue';
import DocsCallout from '@/components/docs/DocsCallout.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';
const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'overview', text: 'Overview', level: 2 },
  { id: 'agent-or-library', text: 'Agent or Library?', level: 2 },
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
        <p><strong>jeffrey-heartbeat</strong> reports to a Jeffrey Hub that your JVM is alive, and tells it when the JVM stopped — the same job the <router-link to="/docs/agent/overview">Jeffrey Agent</router-link> does, for an application that would rather add a dependency than a JVM flag.</p>
        <p>Both write the same two files into the session directory, so the Hub cannot tell which one ran, and does not need to. What it needs is that <em>exactly one</em> of them does.</p>

        <DocsCallout type="info">
          A provisioned application configures itself. Jeffrey Provisioner exports <code>JEFFREY_HEARTBEAT_DIR</code> and <code>JEFFREY_HEARTBEAT_ENABLED</code> into its <code>.env</code> file, and the library reads them — so on Spring Boot the whole integration is one dependency and no code.
        </DocsCallout>

        <h2 id="agent-or-library">Agent or Library?</h2>
        <p>The Provisioner decides for you: it exports <code>JEFFREY_HEARTBEAT_ENABLED=false</code> whenever it attached the agent, which stands the library down. Leave both in place and the right one reports.</p>
        <div class="table-responsive">
          <table class="table table-sm table-hover mb-0">
            <thead>
              <tr>
                <th></th>
                <th>Jeffrey Agent</th>
                <th>jeffrey-heartbeat</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>Added by</td>
                <td><code>-javaagent</code> on the command line</td>
                <td>A dependency in your build</td>
              </tr>
              <tr>
                <td>Works on a JVM you did not build</td>
                <td>Yes</td>
                <td>No</td>
              </tr>
              <tr>
                <td>Reports from</td>
                <td><code>premain</code>, before the application starts</td>
                <td>Whenever your application starts it</td>
              </tr>
              <tr>
                <td>Clean exit written at</td>
                <td>JVM shutdown</td>
                <td>Context shutdown, or JVM shutdown</td>
              </tr>
              <tr>
                <td>Also emits <code>jeffrey.AppInformation</code></td>
                <td>Yes</td>
                <td>No</td>
              </tr>
              <tr>
                <td>Minimum Java</td>
                <td>21</td>
                <td>21</td>
              </tr>
            </tbody>
          </table>
        </div>
        <p>Prefer the agent where you can attach one: it starts earlier, stops later, and carries the recording's identity event as well. Reach for the library where the command line is not yours to change — a platform that owns the JVM arguments, or a build where adding a dependency is simply the shorter path.</p>

        <h2 id="spring-boot">Spring Boot</h2>
        <p>One dependency, no code:</p>
        <pre class="doc-code"><code>&lt;dependency&gt;
    &lt;groupId&gt;cafe.jeffrey-analyst&lt;/groupId&gt;
    &lt;artifactId&gt;jeffrey-heartbeat-spring-boot-starter&lt;/artifactId&gt;
&lt;/dependency&gt;</code></pre>
        <p>The auto-configuration starts the heartbeat from what the Provisioner exported and closes it when the application context shuts down — which is what writes the clean-exit marker, so the Hub finishes the session at once instead of waiting for the heartbeat to go stale.</p>
        <p>Declare your own <code>JeffreyHeartbeat</code> bean and the auto-configuration backs off.</p>

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
                <td>Whether liveness is reported at all. The Provisioner exports <code>false</code> when it attached the agent</td>
              </tr>
              <tr>
                <td><code>JEFFREY_HEARTBEAT_DIR</code></td>
                <td><code>jeffrey.heartbeat.dir</code></td>
                <td>—</td>
                <td>Where the liveness files go. Exported by the Provisioner</td>
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
          The library has <strong>no dependencies at all</strong>, deliberately: it is compiled into applications Jeffrey profiles but does not own, so anything it brought along would be a version those applications did not choose.
        </DocsCallout>
      </div>

      <DocsNavFooter />
  </article>
</template>

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
import { useDocHeadings } from '@/composables/useDocHeadings';
import DocsCallout from '@/components/docs/DocsCallout.vue';
import DocsCodeBlock from '@/components/docs/DocsCodeBlock.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'overview', text: 'Overview', level: 2 },
  { id: 'server', text: 'Server', level: 2 },
  { id: 'uploads', text: 'File Uploads', level: 2 },
  { id: 'core-directories', text: 'Core Directories', level: 2 },
  { id: 'update-check', text: 'Update Check', level: 2 },
  { id: 'hubs', text: 'Declared Hubs', level: 2 },
  { id: 'mcp-server', text: 'MCP Server', level: 2 },
  { id: 'ide-integration', text: 'IDE Integration', level: 2 },
  { id: 'ai-assistant', text: 'AI Assistant', level: 2 }
];

const hubsPropertiesExample = `jeffrey.microscope.hubs.production.name=Production
jeffrey.microscope.hubs.production.hostname=hub.example.com
jeffrey.microscope.hubs.production.port=443

jeffrey.microscope.hubs.staging.hostname=staging.internal
jeffrey.microscope.hubs.staging.port=9090
jeffrey.microscope.hubs.staging.plaintext=true`;

const hubsEnvExample = `JEFFREY_MICROSCOPE_HUBS_PRODUCTION_NAME=Production
JEFFREY_MICROSCOPE_HUBS_PRODUCTION_HOSTNAME=hub.example.com
JEFFREY_MICROSCOPE_HUBS_PRODUCTION_PORT=443`;

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Application Properties"
      icon="bi bi-file-earmark-text"
    />

    <div class="docs-content">
      <h2 id="overview">Overview</h2>
      <p>
        These are the most frequently changed properties when running Jeffrey Microscope.
        All Microscope-specific keys live under the <code>jeffrey.microscope.</code> namespace.
        Standard Spring Boot keys (e.g. <code>server.*</code>, <code>spring.*</code>, <code>logging.*</code>) are also supported.
      </p>

      <DocsCallout type="info">
        <strong>All optional:</strong> every property has a sensible default in code or in
        <code>application.properties</code> bundled with <code>microscope.jar</code>.
        Override only what you need via your own <code>application.properties</code>,
        environment variables, or command-line arguments.
      </DocsCallout>

      <h2 id="server">Server</h2>
      <table>
        <thead>
          <tr>
            <th>Property</th>
            <th>Default</th>
            <th>Description</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>server.port</code></td>
            <td><code>8585</code></td>
            <td>HTTP port for the Microscope web UI. Standard Spring Boot property. Microscope sets 8585 rather than Spring Boot's own 8080, which is usually taken by the application being profiled.</td>
          </tr>
        </tbody>
      </table>

      <h2 id="uploads">File Uploads</h2>
      <p>
        JFR recordings and heap dumps can easily reach multiple gigabytes, so Microscope ships with
        upload size limits disabled. Override these only if you want to clamp incoming uploads.
      </p>
      <table>
        <thead>
          <tr>
            <th>Property</th>
            <th>Default</th>
            <th>Description</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>spring.servlet.multipart.max-file-size</code></td>
            <td><code>-1</code></td>
            <td>Max single-file size. <code>-1</code> means unlimited.</td>
          </tr>
          <tr>
            <td><code>spring.servlet.multipart.max-request-size</code></td>
            <td><code>-1</code></td>
            <td>Max total request size. <code>-1</code> means unlimited.</td>
          </tr>
        </tbody>
      </table>

      <h2 id="core-directories">Core Directories</h2>
      <table>
        <thead>
          <tr>
            <th>Property</th>
            <th>Default</th>
            <th>Description</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>jeffrey.microscope.home.dir</code></td>
            <td><code>${user.home}/.jeffrey-microscope</code></td>
            <td>
              Base directory for all Microscope data (DuckDB files, recordings, profiles).
              Equivalent env var: <code>JEFFREY_MICROSCOPE_HOME_DIR</code>.
            </td>
          </tr>
          <tr>
            <td><code>jeffrey.microscope.temp.dir</code></td>
            <td><code>${jeffrey.microscope.home.dir}/temp</code></td>
            <td>Working directory for temporary files (uploads, parsing scratch space).</td>
          </tr>
        </tbody>
      </table>

      <h2 id="update-check">Update Check</h2>
      <table>
        <thead>
          <tr>
            <th>Property</th>
            <th>Default</th>
            <th>Description</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>jeffrey.microscope.update-check.enabled</code></td>
            <td><code>true</code></td>
            <td>
              Periodically checks GitHub releases for new Microscope versions.
              Set to <code>false</code> in air-gapped environments.
            </td>
          </tr>
        </tbody>
      </table>

      <h2 id="hubs">Declared Hubs</h2>
      <p>
        Connections to <router-link to="/docs/hub">Jeffrey Hub</router-link> can be declared
        up front, so a container or a pod starts already connected instead of waiting for someone to
        add a hub through the UI. Each hub is declared under a key of your choosing
        (<code>production</code>, <code>staging</code>, …), which names the hub and is what its stored
        id is derived from — keep it stable.
      </p>

      <DocsCodeBlock language="properties" :code="hubsPropertiesExample" />

      <table>
        <thead>
          <tr>
            <th>Property</th>
            <th>Default</th>
            <th>Description</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>jeffrey.microscope.hubs.&lt;key&gt;.hostname</code></td>
            <td>—</td>
            <td><strong>Required.</strong> Host name of the hub's gRPC endpoint.</td>
          </tr>
          <tr>
            <td><code>jeffrey.microscope.hubs.&lt;key&gt;.port</code></td>
            <td><code>9090</code></td>
            <td>gRPC port. Note this is the gRPC port, not the hub's HTTP port.</td>
          </tr>
          <tr>
            <td><code>jeffrey.microscope.hubs.&lt;key&gt;.name</code></td>
            <td>the key</td>
            <td>Display name shown in the server switcher.</td>
          </tr>
          <tr>
            <td><code>jeffrey.microscope.hubs.&lt;key&gt;.plaintext</code></td>
            <td><code>false</code></td>
            <td>
              Connect in cleartext h2c instead of TLS. Enable for in-cluster Service DNS or
              trusted-LAN setups.
            </td>
          </tr>
        </tbody>
      </table>

      <p>Every property has the usual environment-variable form:</p>

      <DocsCodeBlock language="bash" :code="hubsEnvExample" />

      <DocsCallout type="info">
        <strong>Configuration owns these hubs.</strong> On every startup the declared hubs are added,
        updated and removed so the registry matches this configuration, and they appear in the UI with
        a <strong>Config</strong> badge and a disabled remove button — deleting one there would only
        last until the next restart. Hubs added through the UI are never touched by this. To remove a
        declared hub, drop it from the configuration and restart.
      </DocsCallout>

      <DocsCallout type="warning">
        <strong>Environment-variable keys must be a single alphanumeric token.</strong> A dashed key
        does not survive the translation — <code>JEFFREY_MICROSCOPE_HUBS_PROD_EU_HOSTNAME</code> is
        read as the key <code>prod</code> with an unrecognised <code>eu.hostname</code> underneath it.
        Use <code>JEFFREY_MICROSCOPE_HUBS_PRODEU_HOSTNAME</code> instead. A hub written
        <code>prod-eu</code> in a properties file and <code>prodeu</code> in an environment variable
        resolves to the same hub, so a configuration can move between the two forms.
      </DocsCallout>

      <DocsCallout type="tip">
        <strong>No connection is attempted at startup.</strong> A declared hub is registered whether
        or not it answers, so a hub that boots after Microscope — the normal case in a compose file or
        a pod — starts working as soon as it comes up.
      </DocsCallout>

      <h2 id="mcp-server">MCP Server</h2>
      <table>
        <thead>
          <tr>
            <th>Property</th>
            <th>Default</th>
            <th>Description</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>jeffrey.microscope.mcp.enabled</code></td>
            <td><code>true</code></td>
            <td>
              Serves the MCP endpoint at <code>/api/internal/mcp</code>, which an external coding-agent
              session &mdash; Claude Code, Codex, anything that speaks MCP &mdash; reads profiles through. Set to <code>false</code> to make it answer
              <code>404</code>. Read at startup, so a change takes a restart. See
              <router-link to="/docs/microscope-mcp/enabling">Enabling the Server</router-link>.
            </td>
          </tr>
          <tr>
            <td><code>jeffrey.microscope.mcp.hubs.enabled</code></td>
            <td><code>true</code></td>
            <td>
              Advertises the <code>hubs_</code> tools, which list the recording sessions on the
              connected <router-link to="/docs/hub">Jeffrey Hubs</router-link> and pull one in to be
              analysed. Its own switch because it is the one family that reaches <em>off</em> this
              machine: everything else reads what is already here. Set to <code>false</code> to keep
              the agent to this machine. Read at startup.
            </td>
          </tr>
          <tr>
            <td><code>jeffrey.microscope.mcp.ide.enabled</code></td>
            <td><code>true</code></td>
            <td>
              Advertises the <code>ide_</code> tools, which ask the developer's running IntelliJ where
              a frame lives, read a class through it, and open a file in it. Its own switch for the
              same reason as <code>hubs_</code>, one step closer to home: everything else reads a
              recording Jeffrey already holds, while this reaches into another process on this machine
              and can put a file on somebody's screen. It needs the
              <router-link to="/docs/intellij-plugin">IntelliJ plugin</router-link>, and answers
              nothing until a window is linked to the profile. Read at startup.
            </td>
          </tr>
          <tr>
            <td><code>jeffrey.microscope.mcp.families</code></td>
            <td><em>empty</em></td>
            <td>
              Which tool families to advertise, comma-separated; empty means all of them. Families are
              named by their tool prefix: <code>profiles</code>, <code>jfr</code>,
              <code>flamegraph</code>, <code>compare</code>, <code>traces</code>, <code>jvm</code>,
              <code>http</code>, <code>jdbc</code>, <code>grpc</code>, <code>methodtracing</code>,
              <code>io</code>, <code>blocking</code>, <code>timeline</code>, <code>memory</code>,
              <code>heap</code>, <code>recordings</code>, <code>hubs</code>, <code>ide</code>. Worth setting
              only for a
              client that pays for the whole tool list on every turn &mdash; Codex loads every schema
              each time, where Claude Code fetches them on demand. A family named here but not built
              (<code>hubs</code> with hub access off) is simply absent rather than a startup failure.
              Read at startup.
            </td>
          </tr>
        </tbody>
      </table>

      <DocsCallout type="info">
        <strong>The endpoint also refuses a foreign <code>Origin</code> on its own</strong>, whatever
        these properties say &mdash; the DNS-rebinding check the MCP specification asks of a local HTTP
        server. A CLI client sends no <code>Origin</code>, so Claude Code and Codex never notice.
      </DocsCallout>

      <h2 id="ide-integration">IDE Integration</h2>
      <p>
        IDE integration is always available &mdash; the per-profile picker shows onboarding until an
        IntelliJ window is linked. The defaults suit a stock IntelliJ, so these only matter when your
        IDE binds elsewhere or you want a different bridge. See
        <router-link to="/docs/intellij-plugin/configuration">IntelliJ Plugin Configuration</router-link>.
      </p>
      <table>
        <thead>
          <tr>
            <th>Property</th>
            <th>Default</th>
            <th>Description</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>jeffrey.microscope.ide.scan.port-start</code></td>
            <td><code>63342</code></td>
            <td>
              First port of the localhost range Microscope scans to find a running IDE, calling
              <code>/api/jeffrey/instance</code> on each. The defaults are IntelliJ's built-in server
              range. Scanning is lazy and the chosen port is cached until it stops responding.
            </td>
          </tr>
          <tr>
            <td><code>jeffrey.microscope.ide.scan.port-end</code></td>
            <td><code>63362</code></td>
            <td>Last port of that range.</td>
          </tr>
          <tr>
            <td><code>jeffrey.microscope.ide.mode</code></td>
            <td><em>Jeffrey plugin</em></td>
            <td>
              Which bridge <em>Open in IDE</em> and <em>View Source</em> talk to. Set to
              <code>jfr-profiler-plugin</code> to route them to the third-party Java JFR Profiler
              plugin instead, which gives up the window picker, the checkout reporting and the
              <code>ide_</code> MCP tools. Requires <code>ide.base-url</code> as well.
            </td>
          </tr>
          <tr>
            <td><code>jeffrey.microscope.ide.base-url</code></td>
            <td><em>none</em></td>
            <td>
              Address of that third-party plugin, e.g. <code>http://localhost:4243</code>. Required
              when &mdash; and only when &mdash; <code>ide.mode</code> selects it; the first-party
              plugin is discovered by scanning and needs no address.
            </td>
          </tr>
        </tbody>
      </table>

      <h2 id="ai-assistant">AI Assistant</h2>
      <table>
        <thead>
          <tr>
            <th>Property</th>
            <th>Default</th>
            <th>Description</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>jeffrey.microscope.ai.provider</code></td>
            <td><code>none</code></td>
            <td>
              AI provider. One of <code>claude</code>, <code>chatgpt</code>, <code>ollama</code>, <code>claude-code</code>, or <code>none</code> (disabled).
            </td>
          </tr>
          <tr>
            <td><code>jeffrey.microscope.ai.model</code></td>
            <td><code>claude-opus-5</code></td>
            <td>Model identifier matching the chosen provider.</td>
          </tr>
          <tr>
            <td><code>jeffrey.microscope.ai.max-tokens</code></td>
            <td><code>128000</code></td>
            <td>Maximum tokens in an AI response.</td>
          </tr>
          <tr>
            <td><code>jeffrey.microscope.ai.source-access.enabled</code></td>
            <td><code>false</code></td>
            <td>
              Lets an AI analysis read the checkout of the IDE window the profile is linked to, so a
              finding lands on the code rather than stopping at a method name. Off by default because
              it is the one setting that sends source to the configured AI provider. Read-only, and
              only ever the linked directory.
            </td>
          </tr>
        </tbody>
      </table>

      <DocsCallout type="tip">
        <strong>API key:</strong> store the provider's API key as a
        <router-link to="/docs/microscope/configuration/secrets">secret</router-link>
        rather than placing it in <code>application.properties</code>.
      </DocsCallout>

      <DocsCallout type="info">
        <strong>Editable at runtime:</strong> every category in <strong>Settings</strong> in the
        Microscope UI is hot-reloaded — the AI properties above, the log level
        (<code>logging.level.cafe.jeffrey</code>) and the flamegraph thresholds. A change saved there is
        stored in the Microscope database and applied immediately — switching AI provider, pasting an API
        key or raising the log level does not need a restart. Values stored that way take precedence over
        <code>application.properties</code>, system properties and environment variables.
      </DocsCallout>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>

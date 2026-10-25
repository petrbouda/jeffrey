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
  { id: 'remote', text: 'Remote', level: 2 },
  { id: 'remote-azure-files', text: 'Azure File Share', level: 3 },
  { id: 'mcp-server', text: 'MCP Server', level: 2 },
  { id: 'ide-integration', text: 'IDE Integration', level: 2 }
];

const remotePropertiesExample = `jeffrey.microscope.remote.name=Production
jeffrey.microscope.remote.hostname=hub.example.com
jeffrey.microscope.remote.port=443
jeffrey.microscope.remote.plaintext=false`;

const azureFilesPropertiesExample = `jeffrey.microscope.remote.name=Production share
jeffrey.microscope.remote.azure-files.endpoint=https://jeffreyprod.file.core.windows.net
jeffrey.microscope.remote.azure-files.share=recordings
jeffrey.microscope.remote.azure-files.protocol=smb
jeffrey.microscope.remote.azure-files.auth=sas
jeffrey.microscope.remote.azure-files.sas-token=\${AZURE_FILES_SAS}
jeffrey.microscope.remote.azure-files.read-only=false`;

const remoteEnvExample = `JEFFREY_MICROSCOPE_REMOTE_NAME=Production
JEFFREY_MICROSCOPE_REMOTE_HOSTNAME=hub.example.com
JEFFREY_MICROSCOPE_REMOTE_PORT=443`;

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

      <h2 id="remote">Remote</h2>
      <p>
        The <strong>remote</strong> is the one place Microscope reads recordings from beyond its own
        disk: either a <router-link to="/docs/hub">Jeffrey Hub</router-link> reached over gRPC, or the
        recording volume itself on an Azure file share (below). Microscope connects to exactly one
        remote. Declaring it up front lets a container or a pod start already connected instead of
        waiting for someone to connect it on the <strong>Remote</strong> page. For a Jeffrey Hub only
        <code>hostname</code> is required.
      </p>

      <DocsCodeBlock language="properties" :code="remotePropertiesExample" />

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
            <td><code>jeffrey.microscope.remote.hostname</code></td>
            <td>—</td>
            <td><strong>Required.</strong> Host name of the hub's gRPC endpoint.</td>
          </tr>
          <tr>
            <td><code>jeffrey.microscope.remote.port</code></td>
            <td><code>9090</code></td>
            <td>gRPC port. Note this is the gRPC port, not the hub's HTTP port.</td>
          </tr>
          <tr>
            <td><code>jeffrey.microscope.remote.name</code></td>
            <td>the hostname</td>
            <td>Display name shown on the Remote page and in the breadcrumb.</td>
          </tr>
          <tr>
            <td><code>jeffrey.microscope.remote.plaintext</code></td>
            <td><code>false</code></td>
            <td>
              Connect in cleartext h2c instead of TLS. Enable for in-cluster Service DNS or
              trusted-LAN setups.
            </td>
          </tr>
        </tbody>
      </table>

      <h3 id="remote-azure-files">Azure File Share</h3>
      <p>
        Instead of a <code>hostname</code>, the remote can be the recording volume itself,
        reached straight on an <strong>Azure file share</strong> with no Jeffrey Hub in between — the
        share the provisioner writes into, mounted in AKS through the <code>azurefile-csi</code>
        storage class. Microscope works with it over the Azure Files REST API, so it needs no mount
        of its own. It lists and downloads like a hub and makes the same changes a hub makes —
        unless it is declared <code>read-only</code>; see
        <router-link to="/docs/microscope/workspaces#azure-files">Workspaces</router-link> for what
        that means in the UI.
      </p>

      <DocsCodeBlock language="properties" :code="azureFilesPropertiesExample" />

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
            <td><code>jeffrey.microscope.remote.azure-files.endpoint</code></td>
            <td>—</td>
            <td>
              <strong>Required.</strong> The storage account's file endpoint, e.g.
              <code>https://&lt;account&gt;.file.core.windows.net</code>: <code>https</code>, a host
              and nothing more. A URL with a path, a query or credentials — such as the portal's
              <em>File service SAS URL</em> — is refused, so a SAS never ends up stored, listed or
              logged as part of the address; put it in <code>sas-token</code>.
            </td>
          </tr>
          <tr>
            <td><code>jeffrey.microscope.remote.azure-files.share</code></td>
            <td>—</td>
            <td>
              <strong>Required.</strong> Name of the file share; also the remote's display name when
              <code>jeffrey.microscope.remote.name</code> is not set.
            </td>
          </tr>
          <tr>
            <td><code>jeffrey.microscope.remote.azure-files.root-path</code></td>
            <td>the share's root</td>
            <td>
              Directory on the share that holds the workspaces — the same directory the hub would use
              as its volume. Every directory under it is shown as a workspace.
            </td>
          </tr>
          <tr>
            <td><code>jeffrey.microscope.remote.azure-files.protocol</code></td>
            <td><code>smb</code></td>
            <td>
              <code>smb</code> or <code>nfs</code> — the share's protocol. An NFS share's listings
              carry no file times, so Microscope reads them one file at a time; expect larger
              projects to load more slowly.
            </td>
          </tr>
          <tr>
            <td><code>jeffrey.microscope.remote.azure-files.auth</code></td>
            <td><code>sas</code></td>
            <td>
              <code>sas</code> signs in with <code>sas-token</code>. <code>entra</code> signs in with
              Microsoft Entra ID through <code>DefaultAzureCredential</code> (managed identity,
              workload identity, environment variables, the Azure CLI, …, which it may run); that
              identity needs the <strong>Storage File Data Privileged Contributor</strong> role on
              the share, or <strong>Storage File Data Privileged Reader</strong> with
              <code>read-only=true</code>.
            </td>
          </tr>
          <tr>
            <td><code>jeffrey.microscope.remote.azure-files.sas-token</code></td>
            <td>—</td>
            <td>
              <strong>Required with <code>auth=sas</code>.</strong> A SAS token granting read, list,
              create, write and delete on the share — read and list alone with
              <code>read-only=true</code>; Microscope warns at startup, naming only the rights, when
              a token grants more or less than its source uses. It stays in the configuration:
              Microscope never stores it in its database and never logs it, so pass it from a secret
              (an environment variable or a mounted secret file).
            </td>
          </tr>
          <tr>
            <td><code>jeffrey.microscope.remote.azure-files.read-only</code></td>
            <td><code>false</code></td>
            <td>
              Refuse every change to the share — no workspace created or deleted, no project
              deleted, no session deleted or pinned — and hide those actions in the UI. The
              credential then needs only read rights.
            </td>
          </tr>
        </tbody>
      </table>

      <p>
        The remote is either a <code>hostname</code> or an <code>azure-files</code> block, never
        both. A declaration that cannot be used &mdash; both forms at once, a port out of range, an
        Azure Files block missing its endpoint or share &mdash; is logged as a warning and ignored,
        rather than failing startup: Microscope then runs as if no remote were declared.
      </p>

      <DocsCallout type="warning">
        <strong>Give recordings a storage account of their own.</strong> Both privileged roles, which
        the Entra sign-in needs, ignore file-level permissions and reach every share in the storage
        account, not just this one. A SAS can be scoped to one share; a role cannot.
      </DocsCallout>

      <p>Every property has the usual environment-variable form:</p>

      <DocsCodeBlock language="bash" :code="remoteEnvExample" />

      <DocsCallout type="info">
        <strong>Configuration wins.</strong> A remote declared here is connected, updated or
        disconnected on every startup to match the configuration, replacing whatever the UI had
        connected. It appears on the Remote page with a <strong>Config</strong> badge and a disabled
        disconnect button: the UI can neither disconnect nor change it. To remove it, drop it from the
        configuration and restart. Only when nothing is declared can the Remote page connect a
        Jeffrey Hub (the form probes it first) and disconnect it again &mdash; and it connects one
        only: a second remote is refused.
      </DocsCallout>

      <DocsCallout type="tip">
        <strong>No connection is attempted at startup.</strong> A declared remote is stored whether
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
              Serves the MCP endpoint at <code>/api/mcp</code>, which an external coding-agent
              session that speaks MCP <code>2026-07-28</code> &mdash; Claude Code on its v2 MCP runtime, Codex v0.147.0+ with its <code>mcp_2026_07_28</code> flag on, any other client once it supports that revision &mdash; reads profiles through. Set to <code>false</code> to make it answer
              <code>404</code>. Read at startup, so a change takes a restart. See
              <router-link to="/docs/microscope-mcp/enabling">Enabling the Server</router-link>.
            </td>
          </tr>
          <tr>
            <td><code>jeffrey.microscope.mcp.allowed-hosts</code></td>
            <td><code>localhost,127.0.0.1,::1</code></td>
            <td>
              The hostnames the endpoint will answer to, comma-separated, checked against the host the
              request was actually addressed to. Anything else gets <code>403</code>, with or without an
              <code>Origin</code> header. It is what stops a page in a browser from choosing both sides
              of the origin comparison: rebind a name you control to <code>127.0.0.1</code> and the
              <code>Host</code> and <code>Origin</code> it sends match each other perfectly. The list
              replaces the default rather than adding to it, so keep the loopback names any client still
              uses. It has to be widened whenever a client dials anything but loopback: an agent in a
              devcontainer reaching the host as <code>host.docker.internal</code>, or a machine on the LAN
              dialling this one by name. Behind a reverse proxy, name the hostname clients dial and either
              turn on <code>jeffrey.microscope.mcp.trust-forwarded-headers</code> or set Spring Boot's
              <code>server.forward-headers-strategy</code>. Read at startup. See
              <router-link to="/docs/microscope-mcp/enabling">Enabling the Server</router-link>.
            </td>
          </tr>
          <tr>
            <td><code>jeffrey.microscope.mcp.trust-forwarded-headers</code></td>
            <td><code>false</code></td>
            <td>
              Takes the host from the first <code>X-Forwarded-Host</code> value and the scheme from
              <code>X-Forwarded-Proto</code> for the allowed-hosts check and the <code>Origin</code>
              comparison alike. Only for a Jeffrey reachable solely through a proxy that sets both &mdash;
              anyone who reaches Jeffrey directly can write them. Read at startup. See
              <router-link to="/docs/microscope-mcp/enabling#behind-a-reverse-proxy">Behind a Reverse Proxy</router-link>.
            </td>
          </tr>
          <tr>
            <td><code>jeffrey.microscope.mcp.token</code></td>
            <td><em>empty</em></td>
            <td>
              Optional bearer token for the MCP endpoint. When set, a request without
              <code>Authorization: Bearer &lt;token&gt;</code>, or with another token, gets <code>401</code>
              with <code>WWW-Authenticate: Bearer</code>; empty requires nothing and ignores the header.
              Claude Code sends the plugin&rsquo;s <em>Jeffrey MCP token</em> setting; Codex, Gemini and
              other clients send <code>JEFFREY_MCP_TOKEN</code>. Read at
              startup. See <router-link to="/docs/microscope-mcp/enabling#bearer-token">Bearer Token</router-link>.
            </td>
          </tr>
          <tr>
            <td><code>jeffrey.microscope.mcp.remote.enabled</code></td>
            <td><code>true</code></td>
            <td>
              Advertises the <code>remote_</code> tools, which list the recording sessions on the
              connected <a href="#remote">remote</a> and pull one in to be analysed. Its own switch because it is the one family that reaches <em>off</em> this
              machine: everything else reads what is already here. Set to <code>false</code> to keep
              the agent to this machine. Read at startup.
            </td>
          </tr>
          <tr>
            <td><code>jeffrey.microscope.mcp.remote.scan-timeout</code></td>
            <td><code>PT20S</code></td>
            <td>
              How long the <code>remote_</code> tools spend discovering what the remote holds before they
              answer with what did arrive. One deadline shared by every remote call a scan makes, and a
              cancelling one: a project that stops answering is abandoned rather than waited out, and the
              sessions other projects already returned are still reported, each unreachable scope named
              beside them. A positive ISO-8601 duration. Read at startup.
            </td>
          </tr>
          <tr>
            <td><code>jeffrey.microscope.mcp.remote.download-response-timeout</code></td>
            <td><code>PT45S</code></td>
            <td>
              How long <code>remote_download</code> &mdash; and <code>remote_fetchFile</code>, which shares
              this setting &mdash; waits before it answers that the transfer is still running, session
              lookup included. It bounds the <em>answer</em>, not the work: a transfer that outlasts it
              carries on, and calling the tool again with the same arguments reports the finished copy. Sized under the shortest client timeout worth designing for, so raising it
              mostly buys a client that has already given up. A positive ISO-8601 duration. Read at
              startup.
            </td>
          </tr>
          <tr>
            <td><code>jeffrey.microscope.mcp.remote.download-timeout</code></td>
            <td><code>PT1H</code></td>
            <td>
              How long a transfer itself may take, once it is running in the background &mdash; a whole
              session under <code>remote_download</code> or one artifact under <code>remote_fetchFile</code>,
              which is the same bytes off the same link. Its own setting because it measures something
              else entirely from the response deadline above: gigabytes over whatever link the remote is on.
              This is the one to raise for large recordings on a slow connection. A positive ISO-8601 duration. Read at startup. See
              <router-link to="/docs/microscope-mcp/enabling">Enabling the Server</router-link>.
            </td>
          </tr>
          <tr>
            <td><code>jeffrey.microscope.mcp.remote.ask-window-over-duration</code></td>
            <td><code>PT1H</code></td>
            <td>
              When a client that declared MCP form elicitation asks <code>remote_download</code> for a
              whole session running longer than this &mdash; to its finish, or to now while it is still
              recording &mdash; the user is first asked which part to bring: the last N minutes, its
              startup, its latest or busiest chunk, the minutes before or around a moment, a window of
              their own, or all of it. A smaller session is downloaded whole
              without a question, and so is every session for a client that cannot show a form; a call
              that already names a <code>window</code>, bounds or <code>fileIds</code> is never asked. An ISO-8601 duration;
              <code>PT0S</code> means always ask. Read at startup. See
              <router-link to="/docs/microscope-mcp/tools#window-question">Which Part of a Large Session</router-link>.
            </td>
          </tr>
          <tr>
            <td><code>jeffrey.microscope.mcp.remote.ask-window-over-size</code></td>
            <td><code>1GB</code></td>
            <td>
              The size half of the rule above: a session holding more than this is asked about
              whatever its length. A data size such as <code>500MB</code> or <code>2GB</code>, where
              <code>1GB</code> is 1024<sup>3</sup> bytes; <code>0B</code> means always ask. Read at startup.
            </td>
          </tr>
          <tr>
            <td><code>jeffrey.microscope.mcp.recordings.max-concurrent-imports</code></td>
            <td><code>2</code></td>
            <td>
              How many <code>recordings_analyzeFile</code> imports run at once. A call beyond that
              number is not refused: it is reported as <code>QUEUED</code> by
              <code>operations_status</code> and starts when a slot frees, and
              <code>operations_cancel</code> on a queued attempt means it never starts. A positive
              integer. Read at startup.
            </td>
          </tr>
          <tr>
            <td><code>jeffrey.microscope.mcp.diagnostics.probe-timeout</code></td>
            <td><code>PT2S</code></td>
            <td>
              How long the <code>jeffrey://diagnostics</code> resource asks the connected remote for
              its version before counting it as unreachable. A short budget is the point: the resource
              answers whether the remote responds, not what it holds. A positive ISO-8601 duration. Read at startup.
            </td>
          </tr>
          <tr>
            <td><code>jeffrey.microscope.mcp.ide.enabled</code></td>
            <td><code>true</code></td>
            <td>
              Advertises the <code>ide_</code> tools, which ask the developer's running IntelliJ where
              a frame lives, read a class through it, and open a file in it. Its own switch for the
              same reason as <code>remote_</code>, one step closer to home: everything else reads a
              recording Jeffrey already holds, while this reaches into another process on this machine
              and can put a file on somebody's screen. It needs the
              <router-link to="/docs/intellij-plugin">IntelliJ plugin</router-link>, and answers
              nothing until a window is linked to the profile. Read at startup.
            </td>
          </tr>
          <tr>
            <td><code>jeffrey.microscope.mcp.preset</code></td>
            <td><code>all</code></td>
            <td>
              A named selection of tool families: <code>all</code>, <code>jfr</code>, <code>heap</code> or
              <code>remote</code>. Each focused preset keeps <code>profiles</code>, <code>recordings</code>
              and <code>operations</code> beside the families it is named for, so a profile can still be
              found, created and followed. A non-empty <code>families</code> list wins over it; an unknown
              preset name rejects startup. Read at startup.
            </td>
          </tr>
          <tr>
            <td><code>jeffrey.microscope.mcp.families</code></td>
            <td><em>empty</em></td>
            <td>
              Which tool families to advertise, comma-separated; empty means whatever the preset selects,
              all of them by default. Families are
              named by their tool prefix: <code>profiles</code>, <code>operations</code>, <code>jfr</code>,
              <code>flamegraph</code>, <code>compare</code>, <code>traces</code>, <code>jvm</code>,
              <code>http</code>, <code>jdbc</code>, <code>grpc</code>, <code>methodtracing</code>,
              <code>io</code>, <code>blocking</code>, <code>timeline</code>, <code>memory</code>,
              <code>heap</code>, <code>recordings</code>, <code>remote</code>, <code>ide</code>. Keep
              <code>operations</code> in any list that keeps <code>recordings</code>, <code>heap</code>,
              <code>remote</code>, <code>ide</code> or <code>jvm</code> &mdash; startup fails otherwise &mdash;
              since it is how a client follows and cancels the work those start; and keep
              <code>recordings</code> in any list that keeps <code>remote</code>, since a downloaded
              session is analysed with <code>recordings_analyzeRecording</code>. Worth setting
              only for a
              client that pays for the whole tool list on every turn &mdash; Codex loads every schema
              each time, where Claude Code fetches them on demand. A family named here but not built
              (<code>remote</code> with remote access off) is simply absent rather than a startup failure.
              Read at startup.
            </td>
          </tr>
        </tbody>
      </table>

      <DocsCallout type="info">
        <strong>The endpoint also refuses a foreign <code>Origin</code> on its own</strong>, whatever
        these properties say &mdash; the DNS-rebinding check the MCP specification asks of a local HTTP
        server. A CLI client sends no <code>Origin</code>, so Claude Code, Codex and Gemini never notice.
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

      <DocsCallout type="info">
        <strong>Configuration is static:</strong> every property is read once at startup. There is no
        Settings page and nothing is stored in the Microscope database — the log level
        (<code>logging.level.cafe.jeffrey</code>), the flamegraph threshold and the frame text mode
        are all set in <code>application.properties</code>, as a system property or through an
        environment variable, and take effect on the next restart.
      </DocsCallout>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>

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
import DocsCodeBlock from '@/components/docs/DocsCodeBlock.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'it-is-already-on', text: 'It Is Already On', level: 2 },
  { id: 'the-endpoint-url', text: 'The Endpoint URL', level: 2 },
  { id: 'turning-it-off', text: 'Turning It Off', level: 2 },
  { id: 'while-it-is-off', text: 'While It Is Off', level: 2 },
  { id: 'turning-hub-access-off', text: 'Turning Hub Access Off', level: 2 },
  { id: 'turning-ide-access-off', text: 'Turning IDE Access Off', level: 2 },
  { id: 'trimming-the-tool-list', text: 'Trimming the Tool List', level: 2 },
  { id: 'what-a-session-holds-open', text: 'What a Session Holds Open', level: 2 },
  { id: 'security', text: 'Security', level: 2 },
  { id: 'behind-a-reverse-proxy', text: 'Behind a Reverse Proxy', level: 3 },
  { id: 'bearer-token', text: 'Bearer Token', level: 3 }
];

onMounted(() => {
  setHeadings(headings);
});

const propertyToggle = `jeffrey.microscope.mcp.enabled=false`;

const hubsToggle = `jeffrey.microscope.mcp.hubs.enabled=false`;

const hubTimeouts = `# Positive ISO-8601 durations; defaults shown
jeffrey.microscope.mcp.hubs.scan-timeout=PT20S
jeffrey.microscope.mcp.hubs.download-response-timeout=PT45S
jeffrey.microscope.mcp.hubs.download-timeout=PT1H`;

const windowQuestion = `# A whole-session hubs_download of a session longer than this, or holding more than
# that, first asks a client that declared form elicitation which part to bring; defaults shown.
# PT0S or 0B means always ask.
jeffrey.microscope.mcp.hubs.ask-window-over-duration=PT1H
jeffrey.microscope.mcp.hubs.ask-window-over-size=1GB`;

const otherLimits = `# How many recordings_analyzeFile imports may run at once; the rest queue
jeffrey.microscope.mcp.recordings.max-concurrent-imports=2

# How long the jeffrey://diagnostics resource waits on each hub before calling it unreachable
jeffrey.microscope.mcp.diagnostics.probe-timeout=PT2S`;

const ideToggle = `jeffrey.microscope.mcp.ide.enabled=false`;

const presetProperty = `# all (default), jfr, heap, or hub
jeffrey.microscope.mcp.preset=heap`;

const familiesProperty = `# Explicit families override the preset; empty uses the preset
jeffrey.microscope.mcp.families=profiles,flamegraph,jvm,heap,operations`;

const defaultEndpoint = `http://localhost:8585/api/mcp`;

const loopback = `# application.properties -- reachable from this machine and nowhere else
server.address=127.0.0.1`;

const allowedHosts = `# Replace microscope.example.com with the hostname your clients use
jeffrey.microscope.mcp.allowed-hosts=localhost,127.0.0.1,::1,microscope.example.com`;

const forwardedHeaders = `# Only when Jeffrey is reachable solely through a proxy that sets both headers
jeffrey.microscope.mcp.trust-forwarded-headers=true
jeffrey.microscope.mcp.allowed-hosts=microscope.example.com`;

const tokenProperty = `# Every request must then carry: Authorization: Bearer <token>
jeffrey.microscope.mcp.token=a-long-random-string`;

const tunnel = `ssh -N -L 8585:localhost:8585 you@the-host-running-jeffrey`;

const serverProbe = `curl -s -X POST http://localhost:8585/api/mcp \\
  -H 'Content-Type: application/json' \\
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'
# a tool list means it is serving; 404 means it was turned off`;

const disabledProbe = `curl -s -o /dev/null -w '%{http_code}\\n' \\
  -X POST http://localhost:8585/api/mcp \\
  -H 'Content-Type: application/json' \\
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'
# 404 while disabled, 200 once enabled`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Enabling the Server"
      icon="bi bi-toggle-on"
    />

    <div class="docs-content">
      <p>The MCP server is <strong>on by default</strong>. A fresh Jeffrey already answers on the endpoint below, so connecting a client is the only step &mdash; see <router-link to="/docs/microscope-mcp/claude-code">Claude Code</router-link> or <router-link to="/docs/microscope-mcp/codex">Codex</router-link>.</p>

      <h2 id="it-is-already-on">It Is Already On</h2>
      <p>There is nothing to switch on, and nothing in the UI that reports on it: every property below is read once at startup, so the configuration a Jeffrey runs with is the one it was deployed with. Whether the endpoint is serving belongs with the bind address and the reverse proxy, not with the preferences a reader edits in a browser &mdash; so this page, and the properties file, are where it is decided. To check a running Jeffrey, ask the endpoint itself:</p>
      <DocsCodeBlock :code="serverProbe" language="bash" />

      <h2 id="the-endpoint-url">The Endpoint URL</h2>
      <p>One endpoint serves the whole installation:</p>
      <DocsCodeBlock :code="defaultEndpoint" language="bash" />

      <p>The profile is a tool argument rather than part of the URL, so a client registers this address once and can then move between profiles &mdash; and between the JFR, flamegraph, trace and heap-dump families &mdash; inside a single session.</p>

      <DocsCallout type="tip" title="Do not type the URL from memory">
        Build it from the address bar of the Jeffrey UI you already have open, plus <code>/api/mcp</code>. Behind a container, a reverse proxy or a non-default port, <code>localhost:8585</code> is wrong &mdash; and wrong in a way you would only discover after pasting the command.
      </DocsCallout>

      <DocsCallout type="info" title="The endpoint is /api/mcp">
        <code>POST /api/mcp</code> is the one address the server answers on; no other path serves MCP.
      </DocsCallout>

      <h2 id="turning-it-off">Turning It Off</h2>
      <p>An installation that should not expose the endpoint switches it off with an application property:</p>
      <DocsCodeBlock :code="propertyToggle" language="properties" />

      <p>It is read once at startup, so the change takes a restart. See <router-link to="/docs/microscope/configuration/application-properties">Application Properties</router-link> for where such properties belong.</p>

      <h2 id="while-it-is-off">While It Is Off</h2>
      <p>A disabled server answers <code>404</code>. It does not answer a JSON-RPC error explaining that it is disabled, because a disabled server should look like no server at all rather than like one refusing to talk &mdash; an unauthenticated probe learns nothing about whether this Jeffrey has profiles worth asking for.</p>

      <p>The practical consequence: if a client reports that the server is unreachable or every tool call fails, check whether this installation set the property above.</p>
      <DocsCodeBlock :code="disabledProbe" language="bash" />

      <h2 id="turning-hub-access-off">Turning Hub Access Off</h2>
      <p>The <code>hubs_</code> family lets a session list the recording sessions on the <router-link to="/docs/hub">Jeffrey Hubs</router-link> this Microscope is connected to, and pull one in to analyse. It is on by default with the endpoint, and has its own switch:</p>
      <DocsCodeBlock :code="hubsToggle" language="properties" />

      <p>It is one of two families with a switch of its own, because it is the only one that leaves this machine. Everything else the server does &mdash; reading a profile, importing a recording, building a heap index &mdash; happens on the host Jeffrey already runs on. Hub access reaches <em>out</em>, to whatever infrastructure the configured hubs point at, and can move gigabytes off it. An installation happy for an agent to analyse a developer&rsquo;s own <code>.jfr</code> may not be happy for it to pull production recordings.</p>

      <p>Like the endpoint toggle, this one is read once at startup. Whether a family is advertised shows in the client&rsquo;s own tool list &mdash; no <code>hubs_</code> tool means it is off. The switch takes <code>hubs_files</code> and <code>hubs_fetchFile</code> with it.</p>

      <p>Hub discovery has a shared deadline across all remote calls. Downloads &mdash; and single-file fetches, which share the two settings &mdash; have a response deadline, including session lookup, and a separate transfer deadline for work continuing in the background. Increase the transfer deadline for large recordings on a slow connection:</p>
      <DocsCodeBlock :code="hubTimeouts" language="properties" />

      <p>A client that declared MCP form elicitation is asked which part of a <em>large</em> session to bring before a whole-session <code>hubs_download</code> moves it &mdash; the last hour, the last few minutes, a window of the user&rsquo;s own, or all of it; the <router-link to="/docs/microscope-mcp/tools#window-question">Tool Reference</router-link> has the question and its answers. Two properties say what large means: a session that runs longer than the duration &mdash; to its finish, or to now while it is still recording &mdash; or holds more than the size. Anything smaller comes down whole without a question, and so does every session for a client that did not declare form elicitation, and a call that already names a window or <code>fileIds</code>. The duration is ISO-8601; the size is a data size such as <code>500MB</code> or <code>2GB</code>, where <code>1GB</code> is 1024<sup>3</sup> bytes. <code>PT0S</code> or <code>0B</code> means always ask. Both are read at startup.</p>
      <DocsCodeBlock :code="windowQuestion" language="properties" />

      <DocsCallout type="info" title="The expensive tools have no switch">
        <code>heap_prepare</code> builds the heap index and its dominator tree, and <code>jvm_autoAnalysis</code> takes a <code>compute</code> flag to run the rule set &mdash; each can occupy a core for minutes. They used to be withheld by a property of their own, which was dropped: it never bounded what it claimed to, since a single <code>jfr_executeQuery</code> can cost as much, and withholding them left the heap family telling a reader to go and open the browser instead. What they write is a cache &mdash; the same artefacts the <strong>Initialize</strong> button produces, so a run started from a session shows up in the browser and the other way round. No dump is altered and nothing is deleted.
      </DocsCallout>

      <p>Two further bounds have properties of their own. An import is a file copy followed by a full parse, so a client that points at several recordings in one turn would otherwise start all of them together; the ones beyond the limit are queued rather than refused, and <code>operations_status</code> shows them so. The diagnostics probe is short on purpose &mdash; <code>jeffrey://diagnostics</code> is a health check rather than a scan, and a hub that cannot answer within it is exactly what the reachability figure reports.</p>
      <DocsCodeBlock :code="otherLimits" language="properties" />

      <h2 id="turning-ide-access-off">Turning IDE Access Off</h2>
      <p>The <router-link to="/docs/microscope-mcp/tools#ide"><code>ide_</code></router-link> family lets a session ask the developer&rsquo;s running IntelliJ where a frame lives, read a class through it, and open a file in it. It needs the <router-link to="/docs/intellij-plugin">Jeffrey IntelliJ plugin</router-link>, is on by default with the endpoint, and has its own switch:</p>
      <DocsCodeBlock :code="ideToggle" language="properties" />

      <p>Its own switch for the same reason as <code>hubs_</code>, one step closer to home. Every other family reads a recording Jeffrey already holds; this one reaches into another process on this machine, and <code>ide_open</code> moves a developer&rsquo;s cursor while they are working. Nothing here reads a file the IDE does not already have open as a trusted project, and nothing writes to the checkout &mdash; but an installation that would rather an agent never touched the editor turns the family off here.</p>

      <p>With the family on, it still answers nothing until a window is linked to the profile: a lookup links the single unambiguous candidate and otherwise reports the candidates rather than guessing between two checkouts.</p>

      <h2 id="trimming-the-tool-list">Trimming the Tool List</h2>
      <p>The default <code>all</code> preset advertises every enabled family. Select a smaller preset when a client only needs one workflow:</p>
      <DocsCodeBlock :code="presetProperty" language="properties" />
      <table>
        <thead><tr><th>Preset</th><th>Families</th></tr></thead>
        <tbody>
          <tr><td><code>all</code></td><td>All families</td></tr>
          <tr><td><code>jfr</code></td><td>profiles, recordings, jfr, flamegraph, jvm, compare, traces, http, jdbc, grpc, methodtracing, io, blocking, timeline, memory, operations &mdash; everything the <code>analyze-jfr</code> skill routes to; all but heap, hubs and ide</td></tr>
          <tr><td><code>heap</code></td><td>profiles, recordings, heap, operations</td></tr>
          <tr><td><code>hub</code></td><td>profiles, recordings, hubs, operations</td></tr>
        </tbody>
      </table>
      <p>For a custom selection, set <code>families</code>. A nonempty list overrides the preset; the Hub and IDE switches still apply. Names use lowercase, and unknown preset or family names fail startup with an explanation.</p>
      <DocsCodeBlock :code="familiesProperty" language="properties" />

      <p>Families are named by the prefix their tools carry: <code>profiles</code>, <code>jfr</code>, <code>flamegraph</code>, <code>compare</code>, <code>traces</code>, <code>jvm</code>, <code>http</code>, <code>jdbc</code>, <code>grpc</code>, <code>methodtracing</code>, <code>io</code>, <code>blocking</code>, <code>timeline</code>, <code>memory</code>, <code>heap</code>, <code>recordings</code>, <code>hubs</code>, <code>ide</code>, <code>operations</code>. Skills may route to families beyond a narrow preset; use <code>all</code> for unrestricted analysis workflows. Read <code>jeffrey://server</code> through <code>resources/read</code> to see the effective families, tool count, build version and supported protocol revisions. The dynamic <code>jeffrey://diagnostics</code> resource adds readiness, bounded Hub probes and aggregate tool measurements. A custom selection that keeps a family with a writer &mdash; <code>recordings</code>, <code>heap</code>, <code>hubs</code>, <code>ide</code> or <code>jvm</code> &mdash; must keep <code>operations</code> too, or startup fails naming the rule: it is how a client polls and cancels the work those families start. For the same reason a list that keeps <code>hubs</code> must keep <code>recordings</code>: a downloaded session becomes a profile through <code>recordings_analyzeRecording</code>.</p>

      <h2 id="what-a-session-holds-open">What a Session Holds Open</h2>
      <p>Each profile is its own DuckDB database, and Jeffrey's connection pools evict idle databases after a few minutes. That is right for the UI, where a reader moves on, and wrong for an interactive session that may spend twenty minutes on one profile with long pauses for reading.</p>

      <p>The first tool call for a profile takes a <strong>lease</strong> on its database. Each active call keeps its profiles open until it finishes, including both profiles in a comparison. Background heap preparation holds its own lease until the work and result storage finish. Once the last use finishes, the cached lease stays available for <strong>30 minutes</strong> of inactivity; a later call opens it again. Eviction and cache shutdown defer closing resources that are still in use.</p>

      <h2 id="security">Security</h2>
      <DocsCallout type="warning" title="Unauthenticated unless you set a token">
        By default the MCP endpoint carries the same trust assumption as the rest of Jeffrey&rsquo;s API: anyone who can reach the address can read every profile in that installation &mdash; the recordings, their stack traces, their SQL statements, and the contents of any heap dump you have indexed. What decides who can read all of that is the address Jeffrey binds to, whatever sits in front of it, and &mdash; for this endpoint only &mdash; the optional <a href="#bearer-token">bearer token</a>.
      </DocsCallout>

      <p>The MCP endpoint checks the request hostname against an independent allowlist. By default it accepts <code>localhost</code>, <code>127.0.0.1</code>, and IPv6 loopback <code>::1</code>. Other hostnames receive <code>403</code>, even when the request carries no <code>Origin</code> header. If an <code>Origin</code> is present, its scheme, hostname and port must also match the effective request address. This prevents a caller from bypassing the origin check by choosing matching, arbitrary <code>Host</code> and <code>Origin</code> headers.</p>

      <p>For remote clients or a reverse proxy, configure the hostname they use and restart Microscope. The same applies one step closer to home: an agent running inside a devcontainer reaches a Jeffrey on the host as <code>host.docker.internal</code>, and that name has to be on the list or every call answers <code>403</code>. The property replaces the default list, so retain any loopback names still in use:</p>
      <DocsCodeBlock :code="allowedHosts" language="properties" />
      <p>A refused host is answered <code>403</code> with a body that names <code>jeffrey.microscope.mcp.allowed-hosts</code> and the host it refused, and the plugin&rsquo;s session-start check quotes that sentence when a session opens, so the fix does not need this page.</p>

      <h3 id="behind-a-reverse-proxy">Behind a Reverse Proxy</h3>
      <p>Behind a proxy the servlet sees the proxy&rsquo;s connection to Jeffrey, not the address the client used. Turn on <code>trust-forwarded-headers</code> and the guard takes the host from the first <code>X-Forwarded-Host</code> value (with its port, or the scheme&rsquo;s default) and the scheme from <code>X-Forwarded-Proto</code>, for the allowlist and the <code>Origin</code> comparison alike &mdash; so list the public hostname:</p>
      <DocsCodeBlock :code="forwardedHeaders" language="properties" />
      <p>It is off by default for a reason: anyone who can reach Jeffrey directly can write those headers. Turn it on only when the proxy is the one way in and sets both. Spring Boot&rsquo;s <code>server.forward-headers-strategy</code> is the application-wide alternative, and makes the servlet request itself carry the public scheme, hostname and port. The hostname allowlist does not authenticate clients; the controls below still determine who can reach the installation.</p>

      <h3 id="bearer-token">Bearer Token</h3>
      <p>For a Jeffrey reachable from more than your machine &mdash; a shared host, a container another team reaches, a proxy without authentication of its own &mdash; set a shared secret, and every request must present it:</p>
      <DocsCodeBlock :code="tokenProperty" language="properties" />
      <p>A request without <code>Authorization: Bearer &lt;token&gt;</code>, or with another token, is answered <code>401</code> with <code>WWW-Authenticate: Bearer</code>; the comparison is constant-time, and the token never appears in a log or in the refusal. The host and origin checks run first, so a misaddressed request still gets the <code>403</code> that names the property to change. Empty &mdash; the default &mdash; requires nothing, and an <code>Authorization</code> header is then ignored, which is why the plugin manifests can always send one.</p>
      <p>Where the token goes depends on the client, and the <code>401</code> says so in the same words: <em>Claude Code: the plugin&rsquo;s Jeffrey MCP token setting (/plugin &rarr; microscope); Codex / Gemini / other clients: <code>JEFFREY_MCP_TOKEN</code> (Codex: <code>bearer_token_env_var</code>)</em>. <router-link to="/docs/microscope-mcp/claude-code">Claude Code</router-link> sends only its plugin setting and never reads the variable; <router-link to="/docs/microscope-mcp/codex">Codex</router-link> reads the variable named by <code>bearer_token_env_var</code>, and <router-link to="/docs/microscope-mcp/gemini">Gemini CLI</router-link> sends the variable as a header. A token is one secret shared by everyone who holds it; an authenticating proxy is still the answer when you need one identity per person.</p>

      <p>So decide what can reach the address:</p>
      <ul>
        <li><strong>Bound to loopback.</strong> Enough for a Jeffrey and an agent session on the same machine, and the setting worth making first. It is <em>not</em> the default: Jeffrey binds every interface, as Spring Boot does unless told otherwise, so a laptop on a shared network is reachable by that network until you say
          <DocsCodeBlock :code="loopback" language="properties" />
          After that the endpoint is reachable from that machine and nowhere else, which is what makes an on-by-default endpoint safe.</li>
        <li><strong>Through an SSH tunnel.</strong> For a Jeffrey on a remote host or in a container, forward the port rather than publishing it:
          <DocsCodeBlock :code="tunnel" language="bash" />
          The client then points at <code>localhost</code> and the endpoint is never exposed.
        </li>
        <li><strong>Behind an authenticating reverse proxy.</strong> The right answer for any installation more than one person can reach &mdash; it gives one identity per person, which a shared secret never could. Pair it with <a href="#behind-a-reverse-proxy">trusted forwarded headers</a>.</li>
        <li><strong>With a bearer token.</strong> The <a href="#bearer-token">token</a> is the lighter option when a proxy is more than the installation needs: one secret, set in <code>application.properties</code> and in each client.</li>
      </ul>

      <p>Three things limit the blast radius even so. Every analysis tool is read-only &mdash; the eleven that are not create a profile or a cache, delete a recording and its profile, start or stop background work, or act on the editor beside Jeffrey, and none of them rewrites a profile's data &mdash; and the SQL tools refuse a second statement after a semicolon rather than running it. The SQL engine itself is sandboxed: a profile database is opened with DuckDB's external file access and extension autoloading turned off, so a query is confined to that profile's tables and cannot read a file from the host or fetch anything over the network, however it is spelled. And the server has no shell: it answers questions about profiles, and does not run anything.</p>

      <p>The <code>recordings_</code> family is the first of two exceptions worth understanding, because it is where the server touches this machine's filesystem. A client sends a <em>path</em>, not a file &mdash; a JFR recording routinely runs to hundreds of megabytes, and base64 through a JSON-RPC message would spend the client's whole context on bytes neither side ever reads. The path is therefore opened by the Jeffrey process, on the machine Jeffrey runs on, and Jeffrey copies whatever it finds there into the Quick Analysis store.</p>

      <p>On a loopback Jeffrey that is exactly what you want: the file in your repository is on the same disk, and the caller is you. On a shared installation it means a client that can reach the address can have Jeffrey read a file it chooses from that host &mdash; it must carry a recording extension and survive the parser, so it is a narrow door rather than an open one, but it is a door. No property closes it for a caller who can reach the endpoint: if the address is reachable by anyone you would not hand a shell to, bind Jeffrey to loopback, put an authenticating proxy in front of it, or set a token only those you trust hold.</p>

      <p>The <code>hubs_</code> family is the second exception, and it points the other way: it is the one place the server reaches <em>off</em> this machine. A client that can reach the endpoint can list what every connected hub holds and have Jeffrey pull a session down &mdash; production stack traces, SQL statements and heap dumps included &mdash; onto the host Jeffrey runs on.</p>

      <p>What bounds it is that the client cannot name an address. The hubs are the ones this installation was configured with, in a file or through its UI, so the reachable set is the operator's decision and not the caller's; there is no tool that adds one. What it does <em>not</em> bound is which of those hubs, so on an installation connected to production, endpoint access is production-recording access. Switch it off with the property in <a href="#turning-hub-access-off">Turning Hub Access Off</a> if that is not what you want.</p>

    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>

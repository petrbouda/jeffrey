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
  { id: 'trimming-the-tool-list', text: 'Trimming the Tool List', level: 2 },
  { id: 'what-a-session-holds-open', text: 'What a Session Holds Open', level: 2 },
  { id: 'security', text: 'Security', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const propertyToggle = `jeffrey.microscope.mcp.enabled=false`;

const hubsToggle = `jeffrey.microscope.mcp.hubs.enabled=false`;

const familiesProperty = `# Only these families are advertised; empty (the default) means all of them
jeffrey.microscope.mcp.families=profiles,flamegraph,jvm,heap`;

const defaultEndpoint = `http://localhost:8585/api/internal/mcp`;

const loopback = `# application.properties -- reachable from this machine and nowhere else
server.address=127.0.0.1`;

const tunnel = `ssh -N -L 8585:localhost:8585 you@the-host-running-jeffrey`;

const disabledProbe = `curl -s -o /dev/null -w '%{http_code}\\n' \\
  -X POST http://localhost:8585/api/internal/mcp \\
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
      <p>Open <strong>Settings &rarr; Coding Agents (MCP)</strong> to confirm it and to copy the connection details. The tab reports whether the endpoint is serving; it does not offer a switch, because whether Jeffrey exposes the endpoint at all belongs with the bind address and the reverse proxy rather than with the preferences a reader edits in the UI.</p>

      <h2 id="the-endpoint-url">The Endpoint URL</h2>
      <p>One endpoint serves the whole installation:</p>
      <DocsCodeBlock :code="defaultEndpoint" language="bash" />

      <p>The profile is a tool argument rather than part of the URL, so a client registers this address once and can then move between profiles &mdash; and between the JFR, flamegraph, trace and heap-dump families &mdash; inside a single session.</p>

      <DocsCallout type="tip" title="Do not type the URL from memory">
        The Settings tab shows the endpoint <em>as your browser just reached it</em>, derived from the request rather than hardcoded. Behind a container, a reverse proxy or a non-default port, <code>localhost:8585</code> is wrong &mdash; and wrong in a way you would only discover after pasting the command. Copy the one the tab shows.
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

      <p>It is the only family with a switch of its own, because it is the only one that leaves this machine. Everything else the server does &mdash; reading a profile, importing a recording, building a heap index &mdash; happens on the host Jeffrey already runs on. Hub access reaches <em>out</em>, to whatever infrastructure the configured hubs point at, and can move gigabytes off it. An installation happy for an agent to analyse a developer&rsquo;s own <code>.jfr</code> may not be happy for it to pull production recordings.</p>

      <p>Like the endpoint toggle, this one is read once at startup, and <strong>Settings &rarr; Coding Agents (MCP)</strong> reports which way it is set.</p>

      <DocsCallout type="info" title="The expensive tools have no switch">
        <code>heap_prepare</code> builds the heap index and its dominator tree, and <code>jvm_autoAnalysis</code> takes a <code>compute</code> flag to run the rule set &mdash; each can occupy a core for minutes. They used to be withheld by a property of their own, which was dropped: it never bounded what it claimed to, since a single <code>jfr_executeQuery</code> can cost as much, and withholding them left the heap family telling a reader to go and open the browser instead. What they write is a cache &mdash; the same artefacts the <strong>Initialize</strong> button produces, so a run started from a session shows up in the browser and the other way round. No dump is altered and nothing is deleted.
      </DocsCallout>

      <h2 id="trimming-the-tool-list">Trimming the Tool List</h2>
      <p>Jeffrey advertises a hundred-odd tools across seventeen families. Claude Code fetches their schemas on demand; Codex loads every one of them into the model&rsquo;s context on every turn. For a reader who only ever asks one kind of question, that is a lot to carry, and the endpoint can be told to advertise less:</p>
      <DocsCodeBlock :code="familiesProperty" language="properties" />

      <p>Families are named by the prefix their tools carry: <code>profiles</code>, <code>jfr</code>, <code>flamegraph</code>, <code>compare</code>, <code>traces</code>, <code>jvm</code>, <code>http</code>, <code>jdbc</code>, <code>grpc</code>, <code>methodtracing</code>, <code>io</code>, <code>blocking</code>, <code>timeline</code>, <code>memory</code>, <code>heap</code>, <code>recordings</code>, <code>hubs</code>. Leave it empty unless you have a reason: the skills route between families freely, and one that is not advertised is one their advice will send the model to in vain.</p>

      <h2 id="what-a-session-holds-open">What a Session Holds Open</h2>
      <p>Each profile is its own DuckDB database, and Jeffrey's connection pools evict idle databases after a few minutes. That is right for the UI, where a reader moves on, and wrong for an interactive session that may spend twenty minutes on one profile with long pauses for reading.</p>

      <p>So the first tool call for a profile takes a <strong>lease</strong> on that profile's database and holds it. The lease is released after <strong>30 minutes</strong> without a call for that profile; the next call simply takes a new one. Nothing needs closing by hand, and no session breaks halfway through because you stopped to read the code.</p>

      <h2 id="security">Security</h2>
      <DocsCallout type="warning" title="Unauthenticated, with nothing to turn on">
        The MCP endpoint carries the same trust assumption as the rest of Jeffrey&rsquo;s API: anyone who can reach the address can read every profile in that installation &mdash; the recordings, their stack traces, their SQL statements, and the contents of any heap dump you have indexed. Jeffrey has no authentication to switch on, here or anywhere else, so what decides who can read all of that is the address Jeffrey binds to and whatever sits in front of it.
      </DocsCallout>

      <p>One thing it does refuse on its own: a request carrying an <code>Origin</code> header naming somewhere other than the address it served. That is the check the MCP specification asks of every local HTTP server, and it closes a path that has nothing to do with your network &mdash; a page in a browser you merely visited can post to <code>localhost</code>, and without the check the server would answer it. A CLI client sends no <code>Origin</code> at all, so Claude Code and Codex never notice.</p>

      <p>So decide what can reach the address:</p>
      <ul>
        <li><strong>Bound to loopback.</strong> Enough for a Jeffrey and an agent session on the same machine, and the setting worth making first. It is <em>not</em> the default: Jeffrey binds every interface, as Spring Boot does unless told otherwise, so a laptop on a shared network is reachable by that network until you say
          <DocsCodeBlock :code="loopback" language="properties" />
          After that the endpoint is reachable from that machine and nowhere else, which is what makes an on-by-default endpoint safe.</li>
        <li><strong>Through an SSH tunnel.</strong> For a Jeffrey on a remote host or in a container, forward the port rather than publishing it:
          <DocsCodeBlock :code="tunnel" language="bash" />
          The client then points at <code>localhost</code> and the endpoint is never exposed.
        </li>
        <li><strong>Behind an authenticating reverse proxy.</strong> The only way to put authentication in front of this endpoint, and the right answer for any installation more than one person can reach &mdash; it also gives one identity per person, which a shared secret never could.</li>
      </ul>

      <p>Three things limit the blast radius even so. Every analysis tool is read-only &mdash; the one JFR tool that writes is deliberately not exposed, so an external client can read a profile's data but not rewrite it, and the SQL tools refuse a second statement after a semicolon rather than running it. The SQL engine itself is sandboxed: a profile database is opened with DuckDB's external file access and extension autoloading turned off, so a query is confined to that profile's tables and cannot read a file from the host or fetch anything over the network, however it is spelled. And the server has no shell: it answers questions about profiles, and does not run anything.</p>

      <p>The <code>recordings_</code> family is the first of two exceptions worth understanding, because it is where the server touches this machine's filesystem. A client sends a <em>path</em>, not a file &mdash; a JFR recording routinely runs to hundreds of megabytes, and base64 through a JSON-RPC message would spend the client's whole context on bytes neither side ever reads. The path is therefore opened by the Jeffrey process, on the machine Jeffrey runs on, and Jeffrey copies whatever it finds there into the Quick Analysis store.</p>

      <p>On a loopback Jeffrey that is exactly what you want: the file in your repository is on the same disk, and the caller is you. On a shared installation it means a client that can reach the address can have Jeffrey read a file it chooses from that host &mdash; it must carry a recording extension and survive the parser, so it is a narrow door rather than an open one, but it is a door. There is no property that closes it: if the address is reachable by anyone you would not hand a shell to, bind Jeffrey to loopback or put an authenticating proxy in front of it.</p>

      <p>The <code>hubs_</code> family is the second exception, and it points the other way: it is the one place the server reaches <em>off</em> this machine. A client that can reach the endpoint can list what every connected hub holds and have Jeffrey pull a session down &mdash; production stack traces, SQL statements and heap dumps included &mdash; onto the host Jeffrey runs on.</p>

      <p>What bounds it is that the client cannot name an address. The hubs are the ones this installation was configured with, in a file or through its UI, so the reachable set is the operator's decision and not the caller's; there is no tool that adds one. What it does <em>not</em> bound is which of those hubs, so on an installation connected to production, endpoint access is production-recording access. Switch it off with the property in <a href="#turning-hub-access-off">Turning Hub Access Off</a> if that is not what you want.</p>

    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>

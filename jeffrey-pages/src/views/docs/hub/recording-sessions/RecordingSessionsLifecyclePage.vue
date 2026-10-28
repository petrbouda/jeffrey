<!--
  - Jeffrey
  - Copyright (C) 2025 Petr Bouda
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
  { id: 'session-order', text: 'Session Order', level: 2 },
  { id: 'newest-recording', text: 'The Newest Recording', level: 2 },
  { id: 'downloads', text: 'Downloads', level: 2 },
  { id: 'last-data', text: 'Last Data', level: 2 },
  { id: 'failed-sessions', text: 'Failed Sessions', level: 2 },
  { id: 'wrong-sizes', text: 'When the Sizes Are Wrong', level: 3 },
  { id: 'jvm-crash-logs', text: 'JVM Crash Logs', level: 3 },
  { id: 'session-cleanup', text: 'Session Cleanup', level: 2 },
  { id: 'compression', text: 'Compression', level: 2 },
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
      <DocsPageHeader
        title="Lifecycle"
        icon="bi bi-arrow-repeat"
      />

      <div class="docs-content">
        <p>A recording session is never marked as running or finished. Everything the Hub and Microscope decide about it follows from two things on the volume: the order of an instance's session directories and the names of the recording files inside them. Nothing is stored and no job changes a session's state; a restart of either changes nothing.</p>

        <h2 id="session-order">Session Order</h2>
        <p>The Provisioner names every session directory with a <strong>UUIDv7</strong>, whose timestamp is the session's start, so the names alone order an instance's sessions. A session is a visible directory named with a UUIDv7; anything else under an instance is ignored. There is no session marker file &mdash; the folder name is the session's id and its start.</p>

        <div class="lifecycle-cards">
          <div class="lifecycle-card newest">
            <div class="lifecycle-icon"><i class="bi bi-record-circle"></i></div>
            <div class="lifecycle-content">
              <h4>Newest session</h4>
              <p>The session with the newest name in its instance. No newer session has started there, so it <strong>may still grow</strong>: the JVM may still be writing recordings into it.</p>
              <ul class="lifecycle-details">
                <li>Lists every file, its newest recording included</li>
                <li>Every file can be downloaded</li>
                <li>Cannot be deleted from Microscope</li>
              </ul>
            </div>
          </div>
          <div class="lifecycle-card ended">
            <div class="lifecycle-icon"><i class="bi bi-check-circle"></i></div>
            <div class="lifecycle-content">
              <h4>Ended session</h4>
              <p>Every older session. A newer session in the same instance ended it &mdash; the JVM restarted, after a crash, an OOM kill or a redeploy &mdash; so nothing is added to it any more.</p>
              <ul class="lifecycle-details">
                <li>Select specific JFR chunks to download</li>
                <li>Download all session artifacts</li>
                <li>Can be deleted from Microscope on a writable remote</li>
              </ul>
            </div>
          </div>
        </div>

        <p>An instance whose JVM stopped for good keeps its last session as the newest one. Its data simply stops getting newer, and retention takes it once its <a href="#last-data">last data</a> is older than the window.</p>

        <DocsCallout type="warning">
          <strong>Two things this order relies on.</strong> The profiler runs with <code>loop</code> and a timestamp (<code>%t</code>) in its file names, with a loop well under the session retention &mdash; a session is aged by its newest recording's name, so a profiler that wrote only when the JVM exits would look old while it still runs. And each instance directory belongs to exactly <strong>one JVM</strong>: a second JVM provisioned under the same instance name would make the first one's session read as ended.
        </DocsCallout>

        <DocsCallout type="tip">
          <strong>Selective analysis:</strong> You don't need the whole session. Select specific time periods to analyze - for example, only the startup phase or a specific incident window. The chunks you pick are fetched separately and kept as the several files the recording is made of; they must be an unbroken run, because the recording reports one span across the files it holds and a hole inside that span would be invisible.
        </DocsCallout>

        <DocsCallout type="info">
          <strong>Chunk times follow the profiled machine's time zone.</strong> A chunk is ordered and selected by the timestamp in its own name, which async-profiler writes in the profiled JVM's local time zone. The provisioner therefore writes the UTC offset of its own zone (from <code>TZ</code> or <code>/etc/localtime</code>) at the session's start into the file name as literal text &mdash; <code>profile-20261027-085500+0100.jfr</code>, and <code>+0000</code> on a UTC machine (<code>profile-20260220-120500+0000.jfr</code>). The Hub and Microscope read each chunk's timestamp at the offset its name carries, so a container in any time zone gets the right instants. The offset is fixed for the session: in a zone with daylight saving, the chunks a JVM writes after a switch read one hour off until it restarts; UTC machines are never affected. A name without an offset is read as UTC, so a custom profiler command should write its file through <code>&lt;&lt;JEFFREY:FILE_PATTERN&gt;&gt;</code>. Setting only <code>-Duser.timezone</code> on the JVM does not change what async-profiler writes, so it does not need to match.
        </DocsCallout>

        <h2 id="newest-recording">The Newest Recording</h2>
        <p>The newest recording file &mdash; by the timestamp in its name &mdash; of each instance's newest session is the only file that may still be on its way onto the volume. It is <strong>never compressed</strong> and <strong>never deleted</strong> by retention, live trim or the storage quota. It is listed and downloadable like any other file. Every other recording, and every recording of an ended session, is complete.</p>

        <h2 id="downloads">Downloads</h2>
        <p>Every recording file is downloadable, the newest included. async-profiler (in <code>jfrsync</code> mode, <code>loop=15m</code> by default) writes each <code>profile-%t&lt;offset&gt;.jfr</code> whole when its loop ends, so a file is complete except during the few seconds of that dump.</p>
        <p>Microscope checks each downloaded <code>.jfr</code> on arrival: the chunk headers' sizes must chain to the end of the file, and no chunk may still carry async-profiler's placeholder header. A file that fails the check was caught mid-dump and is refused with <em>&ldquo;arrived incomplete &hellip; ask for it again in a moment&rdquo;</em>; a few seconds later the same download succeeds.</p>

        <h2 id="last-data">Last Data</h2>
        <p>A session's data ends at its newest recording's start plus the measured chunk length &mdash; the lower median of the spacing between consecutive chunk starts, or just that start while the session holds a single chunk. Microscope shows it as <strong>Last data</strong> on a session once its card is expanded (an instance shows none), a session's duration runs from its start to it, and a &ldquo;last N minutes&rdquo; window is measured back from it rather than from now.</p>

        <h2 id="failed-sessions">Failed Sessions</h2>
        <p>A process that is killed before it writes any data — an OOM kill without a heap dump, or a container restarted by a failing healthcheck — leaves behind a <strong>failed session</strong>: a session a newer one ended before it wrote any data, with zero bytes. The newest session is never failed. A crash-looping container can produce dozens of them in a row. Microscope's instance page lists sessions by name only, so it shows no failed-session band: a failed session is a session card like any other, and expanding it shows its empty files. A run of consecutive failed sessions also counts as one session toward <code>max-sessions</code> (see <a href="#session-cleanup">Session Cleanup</a>), so a burst of them never pushes the last real session out.</p>

        <h3 id="wrong-sizes">When the Sizes Are Wrong</h3>
        <p>File sizes are read straight off the session's directory on the volume each time a session is expanded, and a filesystem can misreport them: an object-storage FUSE mount, or an NFS mount with attribute caching, serves each file's size as of when the file was created, so a session that is recording perfectly well can report nothing but zeros for as long as that cache holds. A session that is not failed and whose files all measure zero after a minute of recorded data is therefore badged <strong>sizes unavailable</strong> rather than shown with a confident <code>0.00 B</code>.</p>

        <p>An SMB share — Azure Files, or a Windows file server — has a variant of its own. The profiled JVM keeps its <code>-Xlog</code> file open for the whole session and async-profiler keeps its <code>.jfr.N~</code> cache open, and for a file another client holds open for writing the share answers a <em>directory listing</em> with the directory entry's copy of the size, refreshed only when the writer flushes or closes. Microscope lists the share over the Azure Files REST API, so a session's figures are whatever that listing reports. The hub's retention and quota passes read the same directory off its own mount, where the Linux SMB client additionally caches the figure for every file in the listing; a low <code>actimeo</code> on the hub's mount shortens how long a stale one is served. Inside the hub container the two views can be told apart with <code>stat -c '%s' &lt;file&gt;</code> (the cached listing figure) against <code>stat --cached=never -c '%s' &lt;file&gt;</code> and <code>wc -c &lt; &lt;file&gt;</code> (both go to the server); the Azure portal or Storage Explorer, which reads the file record over REST, shows the real size throughout.</p>

        <p>Downloading is never refused because of a reported size — the download reads the file's real content, whatever the directory claims. So the files of such a session stay listed and stay retrievable. Seeing that badge on a session you know is recording means the volume is serving stale file metadata: compare <code>stat -c '%s' &lt;file&gt;</code> with <code>wc -c &lt; &lt;file&gt;</code> inside the hub container, and check the mount's attribute-cache settings (gcsfuse <code>metadata-cache:ttl-secs</code> / <code>stat-cache-ttl</code>, FUSE <code>attr_timeout</code>, NFS and SMB <code>actimeo</code>). Note that an <em>ended</em> session with zero bytes is still read as a failed session — a crash is the ordinary explanation there, and the badge does not second-guess it.</p>

        <h3 id="jvm-crash-logs">JVM Crash Logs</h3>
        <p>When a JVM crashes, a HotSpot error log (<code>hs_err_pid*.log</code>, or <code>hs-jvm-err.log</code> when the Provisioner named it) is left in the session directory. Microscope classifies it as a <em>HotSpot Error Log</em> and lists it beside the session's other files, where it can be downloaded or fetched by a coding agent. Nothing reads the file and no event is raised for it: the session ends when the restarted JVM starts a newer one, and the crashed session is aged out like any other.</p>

        <h2 id="session-cleanup">Session Cleanup</h2>
        <p>Old data is reclaimed automatically by the <strong>Project Retention</strong> job, every 5 minutes by default. Each pass lists a project's sessions once and applies these rules in order, each seeing what the previous one deleted:</p>
        <ul>
          <li><code>session-retention</code> (7 days by default) - Deletes whole sessions whose <a href="#last-data">last data</a> is older than the window. A session that is still recording always has recent data, so it is never aged out; an instance that stopped goes with its last session once the window has passed.</li>
          <li><code>max-sessions</code> (10 by default) - Caps each instance at that many logical sessions; the oldest beyond the cap go even before the age window expires. A consecutive run of failed sessions counts as one, and the instance's newest session is never taken by the cap.</li>
          <li><code>active-session-file-retention</code> (7 days by default) - Trims recordings older than the window inside each instance's <em>newest</em> session, so a long-running JVM cannot grow one session without bound. The newest recording is never removed, and ended sessions are left whole.</li>
          <li><code>max-size</code> (20 GB by default) - Caps total disk per project. Age alone cannot bound disk usage, so when a project exceeds its budget the pass reclaims oldest-first: whole ended sessions first, then recordings of the newest sessions &mdash; never their newest recording, so a project may stay slightly over budget, which is logged.</li>
          <li><code>instance-retention</code> (14 days by default) - Deletes instances (including their directories on disk) started longer ago than the window that hold no session.</li>
        </ul>
        <p>Deleting an instance's last session — by retention or by hand — deletes the instance's directory with it, so there is no expired state. A <code>.retained</code> file left in a session directory by an older version is ignored and goes with its session's directory.</p>
        <p>What a pass leaves is what the storage dashboard shows.</p>

        <h2 id="compression">Compression</h2>
        <p>The <strong>JFR Compression</strong> job LZ4-compresses recordings to save storage space, every minute by default. Each tick compresses the recordings of each instance's two newest sessions &mdash; the newest, which keeps recording, and the one it ended &mdash; and never the newest recording of the newest session. Older sessions were reached by an earlier tick.</p>
      </div>

      <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

/* Lifecycle Cards */
.lifecycle-cards {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  margin: 1.5rem 0;
}

.lifecycle-card {
  display: flex;
  gap: 1rem;
  padding: 1.25rem;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.lifecycle-icon {
  width: 48px;
  height: 48px;
  min-width: 48px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.lifecycle-icon i {
  font-size: 1.5rem;
}

.lifecycle-card.newest .lifecycle-icon {
  background: rgba(239, 68, 68, 0.1);
}

.lifecycle-card.newest .lifecycle-icon i {
  color: #ef4444;
}

.lifecycle-card.ended .lifecycle-icon {
  background: rgba(16, 185, 129, 0.1);
}

.lifecycle-card.ended .lifecycle-icon i {
  color: #10b981;
}

.lifecycle-content {
  flex: 1;
}

.lifecycle-content h4 {
  margin: 0 0 0.5rem 0;
  font-size: 1.1rem;
  font-weight: 600;
  color: #343a40;
}

.lifecycle-content p {
  margin: 0 0 0.75rem 0;
  font-size: 0.9rem;
  color: #5e6e82;
  line-height: 1.5;
}

.lifecycle-details {
  margin: 0;
  padding-left: 1.25rem;
}

.lifecycle-details li {
  font-size: 0.85rem;
  color: #6b7280;
  margin-bottom: 0.25rem;
}
</style>

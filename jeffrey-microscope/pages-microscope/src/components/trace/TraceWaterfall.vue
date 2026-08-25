<!--
  ~ Jeffrey
  ~ Copyright (C) 2026 Petr Bouda
  ~
  ~ This program is free software: you can redistribute it and/or modify
  ~ it under the terms of the GNU Affero General Public License as published by
  ~ the Free Software Foundation, either version 3 of the License, or
  ~ (at your option) any later version.
  ~
  ~ This program is distributed in the hope that it will be useful,
  ~ but WITHOUT ANY WARRANTY; without even the implied warranty of
  ~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  ~ GNU Affero General Public License for more details.
  ~
  ~ You should have received a copy of the GNU Affero General Public License
  ~ along with this program.  If not, see <http://www.gnu.org/licenses/>.
  -->

<template>
  <div class="waterfall">
    <!-- The drawing in one card, the legend in its own below — two panels, not one surface. -->
    <div class="wf-card">
    <div class="wf-toolbar">
      <button
        type="button"
        class="wf-toggle"
        :class="{ active: criticalOnly }"
        :aria-pressed="criticalOnly"
        :disabled="!hasOffPathSpans"
        :title="criticalOnlyTitle"
        @click="criticalOnly = !criticalOnly"
      >
        <i class="bi bi-signpost-split"></i> Critical path only
      </button>
      <button
        type="button"
        class="wf-toggle"
        :disabled="parents.size === 0"
        :title="allCollapsed ? 'Expand every span' : 'Collapse every span that has children'"
        @click="toggleAll"
      >
        <i :class="allCollapsed ? 'bi bi-arrows-expand' : 'bi bi-arrows-collapse'"></i>
        {{ allCollapsed ? 'Expand all' : 'Collapse all' }}
      </button>
      <!--
        The three overlay families as real switches inside a labeled group, apart from the view
        actions beside them — a control that draws something over the waterfall and a control that
        rearranges it are two kinds of thing, and stopped wearing one costume. The promoted waits
        stay split along the reader's question: locks, parks and stalls under "Blocking ops", file
        and socket reads under "I/O ops" — different suspicions, so hiding one family must not take
        the other down with it. Both on by default because they are the feature: a trace that says
        where its time went. A family that recorded nothing keeps its switch visible but dashed,
        with the zero said out loud, so its absence never reads as a missing feature.
      -->
      <span class="wf-overlays" role="group" aria-label="Overlay families">
        <span class="wf-overlays-label">Overlays</span>
        <button
          type="button"
          class="wf-switch-item"
          :aria-pressed="bandCategories.length > 0 && showContext"
          :disabled="bandCategories.length === 0"
          :title="contextToggleTitle"
          @click="showContext = !showContext"
        >
          <span class="wf-switch" :class="{ on: bandCategories.length > 0 && showContext }"></span>
          JVM context
          <span v-if="bandCategories.length === 0" class="wf-zero">0 events</span>
        </button>
        <button
          type="button"
          class="wf-switch-item"
          :aria-pressed="showBlockingOps && promotedBlockingCount > 0"
          :disabled="promotedBlockingCount === 0"
          :title="blockingToggleTitle"
          @click="showBlockingOps = !showBlockingOps"
        >
          <span class="wf-switch" :class="{ on: showBlockingOps && promotedBlockingCount > 0 }"></span>
          Blocking ops
          <span v-if="promotedBlockingCount === 0" class="wf-zero">0 events</span>
        </button>
        <button
          type="button"
          class="wf-switch-item"
          :aria-pressed="showIoOps && promotedIoCount > 0"
          :disabled="promotedIoCount === 0"
          :title="ioToggleTitle"
          @click="showIoOps = !showIoOps"
        >
          <span class="wf-switch" :class="{ on: showIoOps && promotedIoCount > 0 }"></span>
          I/O ops
          <span v-if="promotedIoCount === 0" class="wf-zero">0 events</span>
        </button>
        <!--
          The two instant families, each with its own switch for the same reason Blocking ops and
          I/O ops have theirs: they answer different questions, so silencing one must not silence
          the other.
        -->
        <button
          type="button"
          class="wf-switch-item"
          :aria-pressed="showNotifications && notifications.length > 0"
          :disabled="notifications.length === 0"
          :title="
            notifications.length === 0
              ? 'This trace recorded no notifications'
              : 'Show what the application said while this trace ran'
          "
          @click="showNotifications = !showNotifications"
        >
          <span
            class="wf-switch"
            :class="{ on: showNotifications && notifications.length > 0 }"
          ></span>
          Notifications
          <span v-if="notifications.length === 0" class="wf-zero">0 events</span>
        </button>
        <button
          type="button"
          class="wf-switch-item"
          :aria-pressed="showExceptions && exceptions.length > 0"
          :disabled="exceptions.length === 0"
          :title="
            exceptions.length === 0
              ? 'This trace recorded no throws'
              : 'Show the throws recorded inside this trace'
          "
          @click="showExceptions = !showExceptions"
        >
          <span class="wf-switch" :class="{ on: showExceptions && exceptions.length > 0 }"></span>
          Exceptions
          <span v-if="exceptions.length === 0" class="wf-zero">0 events</span>
        </button>
      </span>
      <!--
        Only when there is an error to jump to: in a 200-span trace the one red badge can sit three
        folds deep and two screens down, and "this trace failed" without a way to the failure is a
        finding withheld.
      -->
      <button
        v-if="firstErrorSpan"
        type="button"
        class="wf-toggle wf-error-jump"
        title="Expand to and select the first failed span"
        @click="jumpToFirstError"
      >
        <i class="bi bi-exclamation-triangle"></i> First error
      </button>
    </div>

    <!--
      One lane per kind of pause, above the spans. The lane reuses the row grid, so its track lines
      up with the bars without either side knowing the other's measurements, and it is the lane
      rather than the stripe that carries the labels and the hit targets: the stripe is a wash
      behind rows that come and go as the detail panel opens, with nothing to click.
    -->
    <div v-for="lane in laneGroups" :key="lane.category" class="wf-lane">
      <!--
        The name sits at the left edge, under the Span header, and the gutter between it and the
        track carries what the lane could never say on its own: what share of the trace it cost and
        how many events that was. Twenty rems of empty column was the alternative.
      -->
      <span class="lane-label">
        <span class="lane-name">
          <i class="lane-dot" :style="{ background: contextColor(lane.category) }"></i>
          {{ contextLabel(lane.category) }}
        </span>
        <span class="lane-meter" :title="laneShareTitle(lane)">
          <i
            :style="{
              width: laneSharePercent(lane.bands) + '%',
              background: contextColor(lane.category)
            }"
          ></i>
        </span>
        <span class="lane-stat">
          {{ laneSharePercent(lane.bands).toFixed(1) }}% · {{ lane.bands.length }}×
        </span>
      </span>
      <span class="lane-track">
        <span
          v-for="(band, index) in lane.bands"
          :key="index"
          class="lane-band"
          :style="{
            left: band.leftPercent + '%',
            width: band.widthPercent + '%',
            background: contextColor(band.category)
          }"
          :title="bandTitle(band)"
        >
          <span v-if="band.widthPercent > 6" class="lane-band-text">
            {{ FormattingService.formatDuration2Units(band.durationNanos) }}
          </span>
        </span>
      </span>
      <span class="wf-duration">{{ laneTotal(lane.bands) }}</span>
    </div>


    <!--
      One rail per instant family, above the span rows and outside them. Nothing that rearranges the
      rows can take a mark away: fold a subtree, filter to the critical path, draw a span at the
      minimum bar width, and the rail is unchanged. That is the whole reason it exists, and it is
      what lets the pins on the bars be the *other* half of the reading rather than the only one.

      A mark is a shape before it is a colour -- a diamond for what the application said, a cross
      for what was thrown at it -- because a CRITICAL notification and an escaped throw are both red
      and mean entirely different things.
    -->
    <div v-if="showNotifications && notificationMarks.length > 0" class="wf-lane wf-rail">
      <span class="lane-label">
        <span class="lane-name">
          <i class="rail-glyph ntf" :style="{ background: severityColor(worstNotificationSeverity) }"></i>
          Notifications
        </span>
        <span class="lane-stat">
          <template v-if="worstNotificationSeverity !== null">
            {{ severityLabel(worstNotificationSeverity).toLowerCase() }} &middot;
          </template>
          {{ notificationMarks.length }}&times;
        </span>
      </span>
      <span class="lane-track">
        <span class="rail-rule"></span>
        <button
          v-for="mark in notificationMarks"
          :key="mark.entry.notificationId"
          type="button"
          class="rail-mark ntf"
          :class="{ open: openEntryId === mark.entry.notificationId }"
          :style="{ left: mark.leftPercent + '%', '--mark': mark.color }"
          :title="`${mark.entry.title ?? mark.entry.type ?? 'Notification'} — ${offsetIntoTrace(mark.entry.startEpochMicros)}`"
          @click="toggleEntry(mark.entry.notificationId)"
        ></button>

        <!--
          The popover opens upward, over the toolbar and the pause lanes. Opening downward buries
          the exception rail, the scale and the first rows -- the drawing the reader is here for.
          Whatever it hides should be something they are not using.
        -->
        <div
          v-if="openNotification !== null"
          class="rail-pop up"
          :style="{
            left: offsetPercent(openNotification.startEpochMicros, traceWindow(spans)) + '%',
            '--mark': severityColor(openNotification.severity)
          }"
        >
          <div class="pop-head">
            <span class="pop-sev">{{ severityLabel(openNotification.severity) }}</span>
            <span class="pop-type">{{ openNotification.type }}</span>
            <span class="pop-at">{{ offsetIntoTrace(openNotification.startEpochMicros) }}</span>
          </div>
          <p class="pop-title">{{ openNotification.title ?? openNotification.type }}</p>
          <p v-if="openNotification.message" class="pop-body">{{ openNotification.message }}</p>
          <p class="pop-meta">
            <span v-if="openNotification.category">{{ openNotification.category }}</span>
            <span v-if="openNotification.source">{{ openNotification.source }}</span>
          </p>
          <!--
            The bridge from the fast read to the slow one: the popover answers "what did it say",
            this opens the span where the same entry sits beside everything else it carries.
          -->
          <button
            v-if="openNotification.spanId !== null"
            type="button"
            class="pop-link"
            @click="selectSpanOf(openNotification.spanId)"
          >
            <i class="bi bi-arrow-return-right"></i>
            Select {{ spanNameOf(openNotification.spanId) }}
          </button>
          <p v-else class="pop-orphan">
            No span was open when this fired, so there is no bar to select.
          </p>
        </div>
      </span>
      <span class="wf-duration">{{ notificationMarks.length }}</span>
    </div>

    <div v-if="showExceptions && exceptionMarks.length > 0" class="wf-lane wf-rail">
      <span class="lane-label">
        <span class="lane-name">
          <i
            class="rail-glyph exc"
            :style="{ color: exceptionColor(escapedExceptionCount > 0) }"
          ></i>
          Exceptions
        </span>
        <span class="lane-stat">
          <template v-if="escapedExceptionCount > 0">
            {{ escapedExceptionCount }} escaped &middot;
          </template>
          {{ exceptionMarks.length }}&times;
        </span>
      </span>
      <span class="lane-track">
        <span class="rail-rule"></span>
        <button
          v-for="mark in exceptionMarks"
          :key="mark.entry.exceptionId"
          type="button"
          class="rail-mark exc"
          :class="{ open: openEntryId === mark.entry.exceptionId, escaped: mark.entry.escaped }"
          :style="{ left: mark.leftPercent + '%', '--mark': mark.color }"
          :title="`${mark.entry.thrownClass} — ${offsetIntoTrace(mark.entry.startEpochMicros)}`"
          @click="toggleEntry(mark.entry.exceptionId)"
        ></button>

      </span>
      <span class="wf-duration">{{ exceptionMarks.length }}</span>
    </div>

    <!--
      A throw's stack is the one thing on this screen that will not fit in a popover: 253 frames on a
      real trace, and a floating panel wide enough to hold a fully qualified frame covers the bars
      the reader is comparing against. So it docks instead -- a strip the full width of the dialog,
      between the rail it belongs to and the waterfall, pushing the bars down rather than over them.
      It stays until dismissed, which is what lets the stack be scrolled and read rather than held
      open by the cursor.

      Notifications keep their popover on purpose: a title and a sentence fit in one, and docking
      something that small would spend the width for nothing.
    -->
    <div
      v-if="openException !== null"
      class="exc-dock"
      :style="{ '--mark': exceptionColor(openException.escaped) }"
    >
      <div class="dock-head">
        <Badge v-if="openException.escaped" variant="danger" size="xs" value="escaped" />
        <span v-else class="pop-sev">Caught</span>
        <!--
          A docked strip does not go away when the cursor leaves, so it has to offer a way out that
          is not "find the cross you clicked".
        -->
        <button type="button" class="dock-close" title="Close" @click="openEntryId = null">
          <i class="bi bi-x-lg"></i>
        </button>
      </div>
      <!--
        No class or message here: the stack opens with the line a JVM prints, which says both. The
        header keeps only what that line cannot — whether the throw escaped, and when it happened.
      -->
      <TraceStackTrace
        v-if="openException.stacktraceId"
        class="dock-stack"
        :profile-id="profileId"
        :stacktrace-id="openException.stacktraceId"
        :thrown-class="openException.thrownClass"
        :message="openException.message"
      >
        <template #lead>
          <!--
            Guarded like the notification popover's own Select: a throw with no span open when it
            fired has no bar to select, and `spanNameOf(null)` would label the button "Select ".
          -->
          <button
            v-if="openException.spanId !== null"
            type="button"
            class="btn btn-sm btn-outline-primary dock-select"
            @click="selectSpanOf(openException.spanId)"
          >
            <i class="bi bi-arrow-return-right"></i>
            Select {{ spanNameOf(openException.spanId) }}
          </button>
        </template>
      </TraceStackTrace>
      <!--
        The same button again, because a throw JFR sampled without a stack has no toolbar to put it
        in — and "which span was this?" is exactly as reasonable a question when the stack is
        missing. Two call sites rather than one control that has to know about both shapes.
      -->
      <div v-else class="dock-none">
        <p class="dock-none-cls mono">
          {{ openException.thrownClass
          }}<template v-if="openException.message">: {{ openException.message }}</template>
        </p>
        <p>The recording captured no stack for this throw.</p>
        <button
          v-if="openException.spanId !== null"
          type="button"
          class="btn btn-sm btn-outline-primary dock-select"
          @click="selectSpanOf(openException.spanId)"
        >
          <i class="bi bi-arrow-return-right"></i>
          Select {{ spanNameOf(openException.spanId) }}
        </button>
      </div>
    </div>

    <div class="wf-head">
      <span>Span</span>
      <span class="wf-scale">
        <span>0</span>
        <span>{{ FormattingService.formatDuration2Units(windowNanos) }}</span>
      </span>
      <span class="wf-duration">Duration</span>
    </div>

    <!--
      The same intervals again, washed across the span rows so it is visible which spans a pause
      actually crossed. Inert to the pointer and behind the bars: it is background, and the rows
      underneath stay clickable.
    -->
    <div class="wf-rows" @pointermove="trackCursor" @pointerleave="clearCursor">
      <!--
        Laid out with the row grid rather than at a measured offset, so the stripes track the name
        and duration columns however those are sized — no pixel arithmetic, and nothing to recompute
        when the detail panel opens a row and makes the list taller.

        It stretches the whole of `.wf-rows`, which is taller than the rows themselves once a detail
        panel is open, so every other child claims a layer above it — see `.wf-rows > :not(...)`.
      -->
      <div v-if="bands.length > 0" class="wf-stripes" aria-hidden="true">
        <span></span>
        <span class="wf-stripes-track">
          <span
            v-for="(band, index) in bands"
            :key="index"
            class="wf-stripe"
            :style="{
              left: band.leftPercent + '%',
              width: band.widthPercent + '%',
              '--stripe-color': contextColor(band.category)
            }"
          ></span>
        </span>
        <span></span>
      </div>

      <!--
        The instant under the pointer, named. The wash can say a pause was crossed but never which
        one or how long it ran: below a pixel every band is drawn at the same floor width, so the
        picture cannot be read as a duration however carefully it is drawn. This reads the one place
        the reader is actually pointing, where the numbers are exact.

        Shares the row grid with the stripes, so the reading and the bands agree on where an instant
        is without either measuring the other. Pointer-only, and so hidden from assistive tech: the
        same offset and wall-clock reach the keyboard through the span detail, which Enter opens.
      -->
      <div class="wf-cursor" aria-hidden="true">
        <span></span>
        <span ref="cursorTrack" class="wf-cursor-track">
          <span v-if="cursorPercent !== null" class="wf-cursor-line" :style="cursorStyle">
            <span class="wf-cursor-chip">
              +{{ cursorOffset }}
              <span v-if="cursorBand !== null" class="wf-cursor-pause">
                <i class="lane-dot" :style="{ background: contextColor(cursorBand.category) }"></i>
                {{ contextLabel(cursorBand.category) }}
                {{ FormattingService.formatDuration2Units(cursorBand.durationNanos) }}
              </span>
            </span>
          </span>
        </span>
        <span></span>
      </div>

      <template v-for="{ run, span } in displayRows" :key="run ? run.key : span!.spanId">
        <!--
          A run of same-named leaf siblings drawn as one synthesized row: the count says how many,
          the sigma says what they cost together, and the lane keeps a tick per occurrence so the
          rhythm of the run survives the merge. 462 file writes are one question, not 462 rows.
        -->
        <template v-if="run">
          <button
            type="button"
            class="wf-row wf-run-row"
            :class="{ 'detail-open': openRunDetail === run.key }"
            tabindex="-1"
            :title="isRunExpanded(run) ? 'Collapse the run' : `Expand ${run.spans.length} spans`"
            @click="toggleRun(run.key)"
          >
            <span class="wf-name">
              <span class="wf-indent" :style="{ width: indentRem(run.spans[0].depth) + 'rem' }"></span>
              <span class="wf-twist" role="presentation">
                <i :class="isRunExpanded(run) ? 'bi bi-caret-down-fill' : 'bi bi-caret-right-fill'"></i>
              </span>
              <span class="wf-kind" :class="kindClass(run.spans[0])" :style="kindStyle(run.spans[0])"></span>
              <span class="wf-label">{{ run.spans[0].name }}</span>
              <span class="wf-run-count" :style="kindStyle(run.spans[0])">×{{ run.spans.length }}</span>
              <!--
                A span rather than a nested button, for the same reason the twistie is one. Clicks
                are stopped so opening the statistics does not also unfold the run.
              -->
              <span
                class="wf-run-stats-toggle"
                :class="{ open: openRunDetail === run.key }"
                role="button"
                :title="openRunDetail === run.key ? 'Hide the run statistics' : 'Show the run statistics'"
                @click.stop="toggleRunDetail(run.key)"
              >
                <span class="wf-run-stats-glyph" aria-hidden="true">
                  <i style="height: 3px"></i><i style="height: 8px"></i><i style="height: 5px"></i><i style="height: 2px"></i>
                </span>
                stats
              </span>
            </span>

            <span class="wf-track">
              <span
                v-for="tick in run.spans"
                :key="tick.spanId"
                class="wf-bar wf-run-tick"
                :class="barClass(tick)"
                :style="barStyle(tick)"
              ></span>
            </span>

            <span class="wf-duration">
              Σ {{ FormattingService.formatDuration2Units(run.totalNanos) }}
            </span>
          </button>

          <!--
            The run's facts, shown while the stats chip is pressed: labeled figures with room to be
            read, and the durations as a small histogram — the shape of 462 writes, which no single
            number carries. The row itself only folds and unfolds the individuals.
          -->
          <div v-if="openRunDetail === run.key" class="wf-run-detail-row">
          <div class="wf-run-detail" :style="{ marginLeft: runDetailIndent(run) }">
            <span class="wf-run-stat">
              <span class="wf-run-stat-label">Spans</span>
              <span class="wf-run-stat-value">{{ run.spans.length }}</span>
            </span>
            <span class="wf-run-stat">
              <span class="wf-run-stat-label">Total</span>
              <span class="wf-run-stat-value">{{ FormattingService.formatDuration2Units(run.totalNanos) }}</span>
            </span>
            <span class="wf-run-stat">
              <span class="wf-run-stat-label">Median</span>
              <span class="wf-run-stat-value">{{ FormattingService.formatDuration2Units(run.medianNanos) }}</span>
            </span>
            <span class="wf-run-stat">
              <span class="wf-run-stat-label">P95</span>
              <span class="wf-run-stat-value">{{ FormattingService.formatDuration2Units(run.p95Nanos) }}</span>
            </span>
            <span class="wf-run-stat">
              <span class="wf-run-stat-label">Max</span>
              <span class="wf-run-stat-value">{{ FormattingService.formatDuration2Units(run.maxNanos) }}</span>
            </span>
            <span
              class="wf-run-histogram"
              title="How the run's durations are distributed, fastest on the left, slowest on the right"
            >
              <i
                v-for="(bucket, index) in runHistogram(run)"
                :key="index"
                :class="{ hot: bucket.height === 1 }"
                :style="{ height: 4 + bucket.height * 26 + 'px', ...(kindStyle(run.spans[0]) ?? {}) }"
                :title="bucketTitle(bucket)"
              ></i>
            </span>
          </div>
          </div>
        </template>

        <template v-else-if="span">
        <button
          type="button"
          class="wf-row"
          :class="{ selected: span.spanId === selectedSpanId, critical: isCritical(span) }"
          :aria-expanded="span.spanId === selectedSpanId"
          :data-span-id="span.spanId"
          tabindex="-1"
          @click="$emit('select', span)"
        >
          <span class="wf-name">
            <span class="wf-indent" :style="{ width: indentRem(span.depth) + 'rem' }"></span>
            <!--
            The twistie is a span, not a nested button: the row itself is the button, and nesting one
            inside another is invalid markup that browsers resolve by dropping it. Clicks are stopped
            here so folding a subtree does not also select the row.
          -->
            <span
              v-if="parents.has(span.spanId)"
              class="wf-twist"
              role="presentation"
              :title="twistTitle(span)"
              @click.stop="toggleCollapsed(span.spanId)"
            >
              <i
                :class="
                  collapsed.has(span.spanId) ? 'bi bi-caret-right-fill' : 'bi bi-caret-down-fill'
                "
              ></i>
            </span>
            <span v-else class="wf-twist is-leaf"></span>
            <span class="wf-kind" :class="kindClass(span)" :style="kindStyle(span)"></span>
            <span class="wf-label" :title="span.name">{{ span.name }}</span>
            <Badge v-if="span.status === 'ERROR'" variant="danger" size="xs" value="error" />
            <!--
              What this span itself carries, coloured by the worst of it. Its own entries stay
              pinned to its bar whether or not it is folded -- folding hides a span's children, not
              the span -- so this count never changes as the tree opens and closes.
            -->
            <span
              v-if="showNotifications && (notificationsBySpan.get(span.spanId)?.length ?? 0) > 0"
              class="wf-count ntf"
              :style="{
                '--mark': severityColor(worstSeverity(notificationsBySpan.get(span.spanId) ?? []))
              }"
              :title="notificationCountTitle(span)"
            >
              {{ notificationsBySpan.get(span.spanId)!.length }}
            </span>
            <span
              v-if="showExceptions && (exceptionsBySpan.get(span.spanId)?.length ?? 0) > 0"
              class="wf-count exc"
              :style="{
                '--mark': exceptionColor(anyEscaped(exceptionsBySpan.get(span.spanId) ?? []))
              }"
              :title="exceptionCountTitle(span)"
            >
              {{ exceptionsBySpan.get(span.spanId)!.length }}
            </span>
            <span v-if="collapsed.has(span.spanId)" class="wf-folded">
              +{{ foldedCounts.get(span.spanId) ?? 0 }}
            </span>
            <!-- A fold that swallows a failure must not look like a fold that swallows routine. -->
            <i
              v-if="collapsed.has(span.spanId) && (errorDescendantCounts.get(span.spanId) ?? 0) > 0"
              class="wf-folded-error"
              :title="hiddenErrorTitle(span)"
            ></i>
            <!--
              And a fold that swallows instants has to say so too, for the same reason: their pins
              went into the fold with their spans, and only the rail still shows them. Drawn hollow,
              so what is hidden never reads as what is here.
            -->
            <span
              v-if="
                showNotifications &&
                collapsed.has(span.spanId) &&
                (foldedNotificationCounts.get(span.spanId) ?? 0) > 0
              "
              class="wf-count ntf folded"
              :title="`${foldedNotificationCounts.get(span.spanId)} notifications are inside this fold`"
            >
              {{ foldedNotificationCounts.get(span.spanId) }}
            </span>
            <span
              v-if="
                showExceptions &&
                collapsed.has(span.spanId) &&
                (foldedExceptionCounts.get(span.spanId) ?? 0) > 0
              "
              class="wf-count exc folded"
              :title="`${foldedExceptionCounts.get(span.spanId)} throws are inside this fold`"
            >
              {{ foldedExceptionCounts.get(span.spanId) }}
            </span>
          </span>

          <span class="wf-track">
            <span
              class="wf-bar"
              :class="barClass(span)"
              :style="barStyle(span)"
              :title="tooltip(span)"
            >
              <span
                v-for="(segment, index) in bar(span).selfSegments"
                :key="index"
                class="wf-self"
                :style="{ left: segment.leftPercent + '%', width: segment.widthPercent + '%' }"
              ></span>
            </span>

            <!--
              The other half of the rail's reading: the same instants, on the bar that raised them.
              Positioned against the whole track rather than the bar, so a pin and its rail mark sit
              at the same x and the two readings visibly line up.

              Inert to the pointer -- the row underneath is the click target, and it opens the panel
              where these are listed in full. A pin is a mark, not a control.
            -->
            <template v-if="showNotifications">
              <span
                v-for="notification in notificationsBySpan.get(span.spanId) ?? []"
                :key="notification.notificationId"
                class="wf-pin ntf"
                :style="{
                  left: offsetPercent(notification.startEpochMicros, traceWindow(spans)) + '%',
                  '--mark': severityColor(notification.severity)
                }"
              ></span>
            </template>
            <template v-if="showExceptions">
              <span
                v-for="exception in exceptionsBySpan.get(span.spanId) ?? []"
                :key="exception.exceptionId"
                class="wf-pin exc"
                :class="{ escaped: exception.escaped }"
                :style="{
                  left: offsetPercent(exception.startEpochMicros, traceWindow(spans)) + '%',
                  '--mark': exceptionColor(exception.escaped)
                }"
              ></span>
            </template>
          </span>

          <span class="wf-duration">{{
            FormattingService.formatDuration2Units(span.durationNanos)
          }}</span>
        </button>

        <!--
        The detail belongs to the row above it, so it is drawn as the next row rather than in a panel
        under the waterfall: on a trace of twenty-odd spans, a panel at the bottom scrolls the bar
        that was clicked out of view, which is the one thing the reader is comparing against.
      -->
        <TraceSpanInlineDetail
          v-if="span.spanId === selectedSpanId"
          :profile-id="profileId"
          :span="span"
          :fields="eventFields[span.eventType] ?? []"
          :child-count="childCounts.get(span.spanId) ?? 0"
          :waits="context?.spanWaits?.[span.spanId] ?? []"
          :notifications="notificationsBySpan.get(span.spanId) ?? []"
          :exceptions="exceptionsBySpan.get(span.spanId) ?? []"
          @view-events="$emit('viewEvents')"
          @view-flamegraph="$emit('viewFlamegraph')"
        />
        </template>
      </template>

      <EmptyState
        v-if="rows.length === 0"
        icon="bi-signpost-split"
        title="No spans shown"
        description="Every span is hidden by the current filter."
      >
        <template #action>
          <!-- The state names the filter as the culprit, so it must also offer the way out. -->
          <button type="button" class="wf-toggle" @click="showAllSpans">Show all spans</button>
        </template>
      </EmptyState>
    </div>
    </div>

    <div class="wf-legend">
      <!--
        One two-tone example instead of two single-hue entries: a bar's self time is solid and its
        children washed *in the span's own kind colour*, so a lone green "self" swatch was only true
        for internal spans. The example shows the relationship, which is what actually generalises.
      -->
      <span><i class="swatch swatch-selfchildren"></i> solid self · washed children</span>
      <span><i class="swatch swatch-critical"></i> on the critical path</span>
      <span><i class="swatch swatch-error"></i> error span</span>
      <!--
        Descriptive, like every other entry here. These used to filter one category at a time, which
        put a second control over the promoted rows in a place that reads as a key: the masters above
        already answer "show me the waits or not", and two controls over one family only raised the
        question of which one was in force.
      -->
      <span v-for="category in contextCategories" :key="category">
        <i class="swatch" :style="{ background: contextColor(category) }"></i>
        {{ contextLabel(category) }}
      </span>
      <span><i class="swatch swatch-server"></i> server</span>
      <span><i class="swatch swatch-client"></i> client</span>
      <span><i class="swatch swatch-internal"></i> internal</span>
      <!-- Kind dots (row markers) keep their own hues; the swatches above match the bars. -->
    </div>
  </div>
</template>

<script setup lang="ts">
import { NANOS_PER_MICRO } from '@/services/trace/timeUnits';
import { computed, ref, watch } from 'vue';
import Badge from '@shared/components/Badge.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import FormattingService from '@shared/services/FormattingService';
import TraceSpanInlineDetail from '@/components/trace/TraceSpanInlineDetail.vue';
import TraceStackTrace from '@/components/trace/TraceStackTrace.vue';
import type {
  EventFieldRow,
  TraceContext,
  TraceExceptionRow,
  TraceNotificationRow,
  TraceSpanRow
} from '@/services/api/model/trace/TraceModels';
import type { SpanBar } from '@/services/trace/TraceWaterfallLayout';
import { indentRem, traceWindow, waterfallBars } from '@/services/trace/TraceWaterfallLayout';
import { descendantCounts, spansWithChildren, visibleSpans } from '@/services/trace/traceTree';
import type { ContextBand } from '@/services/trace/TraceContextBands';
import {
  bandAt,
  bandLanes,
  contextBands,
  mergedDurationNanos
} from '@/services/trace/TraceContextBands';
import {
  contextColor,
  contextLabel,
  exceptionColor,
  isIoCategory,
  promotedCategory,
  severityColor,
  severityLabel
} from '@/services/trace/traceLabels';
import {
  anyEscaped,
  bySpan,
  descendantEntryCounts,
  offsetPercent,
  worstSeverity
} from '@/services/trace/traceEntries';

const props = withDefaults(
  defineProps<{
    /** Which profile to read a throw's stack from, for the docked strip and the opened span. */
    profileId: string;
    spans: TraceSpanRow[];
    selectedSpanId?: string | null;
    /** Field metadata per event type, so an opened span can label and format what its event recorded. */
    eventFields: Record<string, EventFieldRow[]>;
    /**
     * What the JVM was doing to the trace. Arrives after the spans do — it is a slower query — so
     * the waterfall must draw perfectly well without it and simply gain the bands when it lands.
     */
    context?: TraceContext | null;
    /**
     * Where the context request stands, so the toolbar never asserts "no pauses" about a JVM it has
     * not heard from yet — a null context also means loading, and also means failed.
     */
    contextState?: 'loading' | 'ready' | 'failed';
    /**
     * The trace's recorded duration, for every "% of the trace" this component hands down. The
     * span-derived window can differ from it (a child outliving its parent widens the window), and
     * using both meant one span's share read as two different percentages in the same dialog.
     */
    traceDurationNanos?: number | null;
    /** What the application said while the trace ran, oldest first. */
    notifications?: TraceNotificationRow[];
    /** Every throw recorded inside the trace, oldest first, each attributed to a span. */
    exceptions?: TraceExceptionRow[];
  }>(),
  {
    selectedSpanId: null,
    context: null,
    contextState: 'ready',
    traceDurationNanos: null,
    notifications: () => [],
    exceptions: () => []
  }
);

const emit = defineEmits<{
  (event: 'select', span: TraceSpanRow): void;
  (event: 'viewEvents'): void;
  (event: 'viewFlamegraph'): void;
}>();

/*
 * ---------------------------------------------------------------------------------------------
 * The two rails.
 *
 * Notifications and exceptions are instants, not spans, so they are drawn twice on purpose: once
 * on a rail above the rows, which folding, the critical-path filter and a two-millisecond span
 * cannot take away, and once as a pin on the bar they belong to, which is the only place that says
 * *which* span. Neither reading works alone -- the rail cannot say which span, the pin disappears
 * into a fold -- and each one covers the other's blind spot.
 *
 * They are split into two families for the same reason Blocking ops and I/O ops are: "what the
 * application said" and "what was thrown at it" are different suspicions, so hiding one must not
 * take the other down with it.
 */

const showNotifications = ref(true);
const showExceptions = ref(true);

const notificationMarks = computed(() =>
  props.notifications.map(notification => ({
    entry: notification,
    leftPercent: offsetPercent(notification.startEpochMicros, traceWindow(props.spans)),
    color: severityColor(notification.severity)
  }))
);

const exceptionMarks = computed(() =>
  props.exceptions.map(exception => ({
    entry: exception,
    leftPercent: offsetPercent(exception.startEpochMicros, traceWindow(props.spans)),
    color: exceptionColor(exception.escaped)
  }))
);

const notificationsBySpan = computed(() => bySpan(props.notifications));
const exceptionsBySpan = computed(() => bySpan(props.exceptions));

/** What a fold swallowed, so a collapsed row can say it the way it already says +N and a red dot. */
const foldedNotificationCounts = computed(() =>
  descendantEntryCounts(props.spans, props.notifications)
);
const foldedExceptionCounts = computed(() => descendantEntryCounts(props.spans, props.exceptions));

const worstNotificationSeverity = computed(() => worstSeverity(props.notifications));

const escapedExceptionCount = computed(
  () => props.exceptions.filter(exception => exception.escaped).length
);

/**
 * The entry whose popover is open, by id. One at a time: it is a third surface inside a fullscreen
 * dialog, and two of them open at once would be a fourth.
 */
const openEntryId = ref<string | null>(null);

const openNotification = computed(
  () =>
    props.notifications.find(
      notification => notification.notificationId === openEntryId.value
    ) ?? null
);

const openException = computed(
  () => props.exceptions.find(exception => exception.exceptionId === openEntryId.value) ?? null
);

function toggleEntry(entryId: string): void {
  openEntryId.value = openEntryId.value === entryId ? null : entryId;
}

/**
 * The popover's footer: the bridge from the fast read to the slow one. Selecting the span opens its
 * detail panel underneath, where the same entry is listed with everything else the span carries.
 */
function selectSpanOf(spanId: string | null): void {
  openEntryId.value = null;
  if (spanId === null) {
    return;
  }
  const span = props.spans.find(candidate => candidate.spanId === spanId);
  if (span === undefined) {
    return;
  }
  // Unfold everything hiding it first, or selecting a span inside a collapsed subtree opens a
  // detail panel for a row that is not on screen.
  revealSpan(span);
  emit('select', span);
}

/** Opens every ancestor of a span, so a row reached from a rail is actually visible. */
function revealSpan(span: TraceSpanRow): void {
  const byId = new Map(props.spans.map(candidate => [candidate.spanId, candidate]));
  const next = new Set(collapsed.value);
  let parentId = span.parentSpanId;
  while (parentId !== null) {
    next.delete(parentId);
    parentId = byId.get(parentId)?.parentSpanId ?? null;
  }
  collapsed.value = next;
}

function notificationCountTitle(span: TraceSpanRow): string {
  const entries = notificationsBySpan.value.get(span.spanId) ?? [];
  const worst = worstSeverity(entries);
  const count = entries.length === 1 ? '1 notification' : `${entries.length} notifications`;
  return worst === null
    ? `${count} in this span`
    : `${count} in this span, worst ${severityLabel(worst).toLowerCase()}`;
}

function exceptionCountTitle(span: TraceSpanRow): string {
  const entries = exceptionsBySpan.value.get(span.spanId) ?? [];
  const count = entries.length === 1 ? '1 throw' : `${entries.length} throws`;
  return anyEscaped(entries)
    ? `${count} in this span, one of which escaped it`
    : `${count} in this span, all caught`;
}

function spanNameOf(spanId: string | null): string | null {
  if (spanId === null) {
    return null;
  }
  return props.spans.find(span => span.spanId === spanId)?.name ?? null;
}

function offsetIntoTrace(startEpochMicros: number): string {
  const micros = Math.max(0, startEpochMicros - traceWindow(props.spans).startMicros);
  return '+' + FormattingService.formatDuration2Units(micros * NANOS_PER_MICRO);
}

/** A span with no geometry cannot happen for a span that is being drawn, but must not throw. */
const EMPTY_BAR: SpanBar = { leftPercent: 0, widthPercent: 0, selfSegments: [] };

const collapsed = ref<Set<string>>(new Set());
const criticalOnly = ref(false);

// A different trace is a different tree, so nothing folded in the last one still applies -- and it
// crossed different pauses, so carrying a hide over would drop bands nobody chose to drop.
watch(
  () => props.spans,
  () => {
    collapsed.value = new Set();
    criticalOnly.value = false;
    showContext.value = true;
    showBlockingOps.value = true;
    showIoOps.value = true;
  }
);

const windowNanos = computed(() => {
  const window = traceWindow(props.spans);
  return (window.endMicros - window.startMicros) * NANOS_PER_MICRO;
});

const parents = computed(() => spansWithChildren(props.spans));

/**
 * Whether the promoted blocking operations — the synthesized leaf spans the derivation built out of
 * lock, park, sleep and stall events — are drawn. On by default: they are the rows that say where a
 * span's time actually went. Off reads the recorded span structure alone. The promoted I/O rows are
 * not under this switch — {@link showIoOps} owns them, so hiding the lock noise cannot silently
 * hide the socket read that explains the trace.
 *
 * The levelled GC phases used to have a toggle of their own here; it drew the same stopped world up
 * to five times over and answered a GC question rather than a latency one, so the nested pauses now
 * simply stay out of the lanes.
 */
const showBlockingOps = ref(true);

/** Whether the promoted file and socket I/O rows are drawn — the other master of the same split. */
const showIoOps = ref(true);

/** Whether the global pause bands are drawn — the third master, over what the JVM did to the trace. */
const showContext = ref(true);

const promotedCount = computed(() => props.spans.filter(span => span.synthesized).length);

const promotedIoCount = computed(() => props.spans.filter(isPromotedIo).length);

const promotedBlockingCount = computed(
  () => promotedCount.value - promotedIoCount.value
);

/** Whether a span is a promoted file or socket I/O wait, as opposed to any other promoted wait. */
function isPromotedIo(span: TraceSpanRow): boolean {
  if (!span.synthesized) {
    return false;
  }
  const category = promotedCategory(span.eventType);
  return category !== null && isIoCategory(category);
}

const blockingToggleTitle = computed(() => {
  if (promotedBlockingCount.value === 0) {
    return 'No blocking operations were promoted in this trace';
  }
  const count = promotedBlockingCount.value;
  const ops = count === 1 ? '1 blocking operation' : `${count} blocking operations`;
  return showBlockingOps.value
    ? `Hide the ${ops} drawn as child spans`
    : `Show the ${ops} drawn as child spans`;
});

const ioToggleTitle = computed(() => {
  if (promotedIoCount.value === 0) {
    return 'No file or socket I/O operations were promoted in this trace';
  }
  const count = promotedIoCount.value;
  const ops = count === 1 ? '1 I/O operation' : `${count} I/O operations`;
  return showIoOps.value
    ? `Hide the ${ops} drawn as child spans`
    : `Show the ${ops} drawn as child spans`;
});

const allBands = computed(() =>
  contextBands(
    (props.context?.pauses ?? []).filter(pause => !pause.nested),
    traceWindow(props.spans)
  )
);

/**
 * The categories drawn as pause lanes and stripes — the GLOBAL ones. The "JVM context" master
 * toggle governs exactly these, so switching the bands off cannot silently swallow the promoted
 * rows, which have their own master.
 */
const bandCategories = computed(() => bandLanes(allBands.value).map(lane => lane.category));

/**
 * Every category the trace recorded — what the legend decodes. Read from the trace rather than from
 * what is currently drawn, so a master switched off does not also take away the key to the colours
 * it hid. Band categories first, then whatever categories the promoted rows add.
 */
const contextCategories = computed(() => {
  const categories = [...bandCategories.value];
  for (const span of props.spans) {
    if (!span.synthesized) {
      continue;
    }
    const category = promotedCategory(span.eventType);
    if (category !== null && !categories.includes(category)) {
      categories.push(category);
    }
  }
  return categories;
});

const bands = computed(() => (showContext.value ? allBands.value : []));

const laneGroups = computed(() => bandLanes(bands.value));

const contextToggleTitle = computed(() => {
  // The absent-categories claim is only true once the request has actually answered. Asserting it
  // while loading or after a failure stated a fact about the JVM nobody had fetched.
  if (props.contextState === 'loading') {
    return 'Loading JVM context…';
  }
  if (props.contextState === 'failed') {
    return 'JVM context could not be loaded';
  }
  if (bandCategories.value.length === 0) {
    return 'No GC pauses or safepoints crossed this trace';
  }
  return showContext.value
    ? 'Hide the pause bands drawn over the spans'
    : 'Show the GC pauses and safepoints that crossed this trace';
});

// Counted once for the whole trace, like the bars and the child counts below: every parent row asks
// for this on each render, and answering per row would rescan the trace for each of them.
const foldedCounts = computed(() => descendantCounts(props.spans));

/**
 * The rows actually drawn: folded subtrees removed first, then the promoted rows the reader has
 * switched off, then the off-path spans when the filter is on. That order is what makes them
 * compose — collapsing hides a subtree whether or not its spans are critical, and each filter then
 * narrows whatever survived. Dropping a synthesized row never breaks the tree: a promoted span is a
 * leaf by construction, so the depth sequence the rendering reads stays intact.
 */
const rows = computed(() => {
  const visible = visibleSpans(props.spans, collapsed.value).filter(isSpanDrawn);
  if (!criticalOnly.value) {
    return visible;
  }
  return visible.filter(isCritical);
});

/**
 * A run shorter than this reads fine as rows; from here up it starts drowning the tree it sits in.
 * A 2 GB upload written through an 8 MB buffer produces hundreds of identical "File write" leaves,
 * and the twenty structural spans around them are what the reader came for.
 */
const MIN_RUN_LENGTH = 5;

/** Consecutive same-named leaf siblings, drawn as one rollup row until expanded. */
interface SpanRun {
  key: string;
  spans: TraceSpanRow[];
  totalNanos: number;
  medianNanos: number;
  p95Nanos: number;
  maxNanos: number;
}

/** One drawn row: either a single span or a whole run. Exactly one side is set. */
interface DisplayRow {
  span?: TraceSpanRow;
  run?: SpanRun;
}

const expandedRuns = ref<Set<string>>(new Set());

/** Whether two visible rows belong to one run. Errors never join one — a rollup must not eat one. */
function sameRun(a: TraceSpanRow, b: TraceSpanRow): boolean {
  return (
    a.parentSpanId === b.parentSpanId &&
    a.name === b.name &&
    a.eventType === b.eventType &&
    a.status !== 'ERROR' &&
    b.status !== 'ERROR'
  );
}

function buildRun(spans: TraceSpanRow[]): SpanRun {
  const durations = spans.map(span => span.durationNanos).sort((a, b) => a - b);
  return {
    // The first span's id keeps the key stable however often the surrounding filters recompute.
    key: `${spans[0].parentSpanId ?? ''}|${spans[0].name}|${spans[0].eventType}|${spans[0].spanId}`,
    spans,
    totalNanos: durations.reduce((sum, nanos) => sum + nanos, 0),
    medianNanos: durations[Math.floor(durations.length / 2)],
    p95Nanos: durations[Math.min(durations.length - 1, Math.floor(durations.length * 0.95))],
    maxNanos: durations[durations.length - 1]
  };
}

/*
 * A run containing the selected span counts as expanded whatever the toggle says: the inline
 * detail is drawn under the selected row, and a rollup hiding the row would hide the detail with
 * it — a jump to the first error must land somewhere visible.
 */
function isRunExpanded(run: SpanRun): boolean {
  if (expandedRuns.value.has(run.key)) {
    return true;
  }
  return props.selectedSpanId != null && run.spans.some(span => span.spanId === props.selectedSpanId);
}

function toggleRun(key: string): void {
  const next = new Set(expandedRuns.value);
  if (next.has(key)) {
    next.delete(key);
  } else {
    next.add(key);
  }
  expandedRuns.value = next;
}

/** Which run's statistics panel is open. One at a time, like the span detail above it. */
const openRunDetail = ref<string | null>(null);

function toggleRunDetail(key: string): void {
  openRunDetail.value = openRunDetail.value === key ? null : key;
}

/** Buckets in the detail strip's histogram — enough to show a shape, few enough to stay a glyph. */
const RUN_HISTOGRAM_BUCKETS = 12;

interface RunHistogramBucket {
  /** Drawn height, 0..1 against the busiest bucket. */
  height: number;
  fromNanos: number;
  toNanos: number;
  count: number;
}

/**
 * The run's durations bucketed min-to-max, each height normalized to the busiest bucket. The shape
 * answers what median and max cannot: were the slow writes a tail, a cluster, or a second mode?
 */
function runHistogram(run: SpanRun): RunHistogramBucket[] {
  const counts = new Array(RUN_HISTOGRAM_BUCKETS).fill(0) as number[];
  const min = Math.min(...run.spans.map(span => span.durationNanos));
  const range = Math.max(1, run.maxNanos - min);
  for (const span of run.spans) {
    const bucket = Math.min(
      RUN_HISTOGRAM_BUCKETS - 1,
      Math.floor(((span.durationNanos - min) / range) * RUN_HISTOGRAM_BUCKETS)
    );
    counts[bucket]++;
  }
  const peak = Math.max(...counts, 1);
  const bucketWidth = range / RUN_HISTOGRAM_BUCKETS;
  return counts.map((count, index) => ({
    height: count / peak,
    fromNanos: min + index * bucketWidth,
    toNanos: min + (index + 1) * bucketWidth,
    count
  }));
}

/**
 * Where a run row's name text begins, in rems: the row's 1rem padding, then the 0.8rem twistie,
 * the 0.45rem kind dot and the two 0.4rem gaps between them. The detail strip starts at the same
 * offset — aligned under the name it belongs to, whatever the run's depth.
 */
const RUN_DETAIL_BASE_REM = 1 + 0.8 + 0.4 + 0.45 + 0.4;

function runDetailIndent(run: SpanRun): string {
  // The extra 2px is the accent gutter every row carries on its left edge.
  return `calc(${RUN_DETAIL_BASE_REM + indentRem(run.spans[0].depth)}rem + 2px)`;
}

/** One bar, said in words: which slice of durations it covers and how many spans landed in it. */
function bucketTitle(bucket: RunHistogramBucket): string {
  const format = FormattingService.formatDuration2Units;
  const spans = bucket.count === 1 ? '1 span' : `${bucket.count} spans`;
  return `${format(bucket.fromNanos)} – ${format(bucket.toNanos)}: ${spans}`;
}

/**
 * The rows as drawn: runs of {@link MIN_RUN_LENGTH}+ identical leaves fold into one rollup entry,
 * everything else passes through one span per row. Only leaves are grouped — merging a parent
 * would hide the structure beneath it, which is the opposite of what the rollup is for.
 */
const displayRows = computed<DisplayRow[]>(() => {
  const list = rows.value;
  const out: DisplayRow[] = [];
  let index = 0;
  while (index < list.length) {
    const start = list[index];
    let end = index;
    if (!parents.value.has(start.spanId)) {
      while (
        end + 1 < list.length &&
        !parents.value.has(list[end + 1].spanId) &&
        sameRun(start, list[end + 1])
      ) {
        end++;
      }
    }
    if (end - index + 1 >= MIN_RUN_LENGTH) {
      const run = buildRun(list.slice(index, end + 1));
      out.push({ run });
      if (isRunExpanded(run)) {
        for (const span of run.spans) {
          out.push({ span });
        }
      }
    } else {
      for (let position = index; position <= end; position++) {
        out.push({ span: list[position] });
      }
    }
    index = end + 1;
  }
  return out;
});

/** Whether a promoted row survives its family's master toggle; a recorded span always draws. */
function isSpanDrawn(span: TraceSpanRow): boolean {
  if (!span.synthesized) {
    return true;
  }
  return isPromotedIo(span) ? showIoOps.value : showBlockingOps.value;
}

/**
 * Whether the filter would remove anything. In a strictly sequential trace every span is on the
 * critical path — correct, but it makes the toggle a no-op, so it is disabled rather than left to
 * look broken.
 */
const hasOffPathSpans = computed(() => props.spans.some(span => !isCritical(span)));

const criticalOnlyTitle = computed(() => {
  if (!hasOffPathSpans.value) {
    return 'Every span in this trace is on the critical path — nothing to hide';
  }
  return 'Show only the spans that determined how long this trace took';
});

const allCollapsed = computed(
  () => parents.value.size > 0 && collapsed.value.size === parents.value.size
);

// Every bar at once: a bar's solid stretches depend on the span's children, so laying them out
// row by row would rescan the whole trace per row.
const bars = computed(() => waterfallBars(props.spans));

// Counted here rather than in the panel, which only ever sees one span: the tree's shape lives in
// this flat list, and counting it once beats scanning every row each time one is opened.
const childCounts = computed(() => {
  const counts = new Map<string, number>();
  for (const span of props.spans) {
    if (span.parentSpanId !== null) {
      counts.set(span.parentSpanId, (counts.get(span.parentSpanId) ?? 0) + 1);
    }
  }
  return counts;
});

function bar(span: TraceSpanRow): SpanBar {
  return bars.value.get(span.spanId) ?? EMPTY_BAR;
}

function isCritical(span: TraceSpanRow): boolean {
  return span.criticalPathNanos > 0;
}

/** The empty state's way out: undo everything that can hide a row. */
function showAllSpans(): void {
  criticalOnly.value = false;
  collapsed.value = new Set();
  showBlockingOps.value = true;
}

/** In drawn order, so "first" means the first the reader would meet scrolling down. */
const firstErrorSpan = computed(() => props.spans.find(span => span.status === 'ERROR') ?? null);

/**
 * How many failed spans each span's subtree holds, counted by walking each error's parent chain —
 * one pass over the errors, which are few, rather than a subtree scan per parent row.
 */
const errorDescendantCounts = computed(() => {
  const byId = new Map(props.spans.map(span => [span.spanId, span]));
  const counts = new Map<string, number>();
  for (const span of props.spans) {
    if (span.status !== 'ERROR') {
      continue;
    }
    let parentId = span.parentSpanId;
    while (parentId !== null) {
      counts.set(parentId, (counts.get(parentId) ?? 0) + 1);
      parentId = byId.get(parentId)?.parentSpanId ?? null;
    }
  }
  return counts;
});

function hiddenErrorTitle(span: TraceSpanRow): string {
  const hidden = errorDescendantCounts.value.get(span.spanId) ?? 0;
  return hidden === 1
    ? 'This folded subtree hides 1 failed span'
    : `This folded subtree hides ${hidden} failed spans`;
}

/**
 * Expands whatever hides the first failed span, then scrolls to and opens it. Every filter that can
 * conceal a row is undone only as far as needed: ancestors unfold, and the critical-path filter is
 * lifted only when the error is off the path it shows.
 */
function jumpToFirstError(): void {
  const target = firstErrorSpan.value;
  if (!target) {
    return;
  }
  const byId = new Map(props.spans.map(span => [span.spanId, span]));
  const next = new Set(collapsed.value);
  let parentId = target.parentSpanId;
  while (parentId !== null) {
    next.delete(parentId);
    parentId = byId.get(parentId)?.parentSpanId ?? null;
  }
  collapsed.value = next;
  if (criticalOnly.value && !isCritical(target)) {
    criticalOnly.value = false;
  }
  scrollRowIntoView(target.spanId);
  if (props.selectedSpanId !== target.spanId) {
    emit('select', target);
  }
}

/**
 * What a band says on hover. Clipping is called out because the number would otherwise be read as
 * the pause's whole length, when part of it happened outside the trace entirely. So is a band drawn
 * at the floor width, for the mirror-image reason: there the drawing overstates the pause, and the
 * band is the only thing that knows by how little.
 */
function bandTitle(band: ContextBand): string {
  const duration = FormattingService.formatDuration2Units(band.durationNanos);
  const name = `${contextLabel(band.category)} · ${band.label} · ${duration}`;
  if (band.clippedStart && band.clippedEnd) {
    return `${name} — ran for the whole trace and beyond it at both ends`;
  }
  if (band.clippedStart) {
    return `${name} — began before this trace did`;
  }
  if (band.clippedEnd) {
    return `${name} — was still running when the trace ended`;
  }
  if (band.clamped) {
    return `${name} — too brief to draw to scale, shown at the minimum width`;
  }
  return name;
}

/**
 * Where the pointer is across the track, 0-100, or null when it is not over a row.
 *
 * The waterfall's other readings are per span, and a span is a poor unit for "what stopped us here":
 * the pauses in this view are global, they cross whatever happened to be running, and the interesting
 * question at a dense stretch of bands is what the JVM was doing at *that instant* — which no span
 * boundary answers.
 */
const cursorPercent = ref<number | null>(null);
const cursorTrack = ref<HTMLElement | null>(null);

const cursorStyle = computed(() => ({ left: (cursorPercent.value ?? 0) + '%' }));

/** How far into the trace the pointer is, in the same units every other duration here is written. */
const cursorOffset = computed(() =>
  FormattingService.formatDuration2Units(((cursorPercent.value ?? 0) / 100) * windowNanos.value)
);

const cursorBand = computed(() =>
  cursorPercent.value === null ? null : bandAt(bands.value, cursorPercent.value)
);

/**
 * Follows the pointer along the track.
 *
 * Measured against the cursor layer's own track cell rather than a row's, because rows come and go
 * as the detail panel opens and closes while this cell is always present and always the same width
 * — the grid gives both of them the same column.
 */
function trackCursor(event: PointerEvent): void {
  const track = cursorTrack.value;
  const target = event.target;
  // Only over the rows themselves. An open detail panel is a table rather than a timeline, and a
  // cursor drawn down it points at nothing.
  if (track === null || !(target instanceof Element) || target.closest('.wf-row') === null) {
    cursorPercent.value = null;
    return;
  }

  const box = track.getBoundingClientRect();
  if (box.width <= 0) {
    cursorPercent.value = null;
    return;
  }
  const percent = ((event.clientX - box.left) / box.width) * 100;
  cursorPercent.value = percent < 0 || percent > 100 ? null : percent;
}

function clearCursor(): void {
  cursorPercent.value = null;
}

/**
 * How much of the trace one lane's pauses came to, for the row's duration column.
 *
 * Merged rather than summed, so switching the GC phase breakdown on cannot change the answer: the
 * phases run inside the pauses above them, and adding both counts the same stopped instant twice.
 */
function laneTotal(laneBands: ContextBand[]): string {
  return FormattingService.formatDuration2Units(
    mergedDurationNanos(laneBands, windowNanos.value)
  );
}

/**
 * The denominator every "% of the trace" in this dialog is taken against — the trace's recorded
 * duration where there is one, and the span-derived window otherwise. The two can differ when a
 * child outlives its parent, and using each in turn made one quantity read as two percentages.
 */
const shareDenominatorNanos = computed(() => props.traceDurationNanos ?? windowNanos.value);

/** What share of the trace a lane cost, 0-100. */
function laneSharePercent(laneBands: ContextBand[]): number {
  const denominator = shareDenominatorNanos.value;
  if (denominator <= 0) {
    return 0;
  }
  return (mergedDurationNanos(laneBands, windowNanos.value) / denominator) * 100;
}

function laneShareTitle(lane: { category: string; bands: ContextBand[] }): string {
  const total = FormattingService.formatDuration2Units(
    mergedDurationNanos(lane.bands, windowNanos.value)
  );
  const events = lane.bands.length === 1 ? '1 event' : `${lane.bands.length} events`;
  // Overlapping pauses are called out because the two numbers otherwise look like they should
  // divide into each other, and for a lane whose bands overlap they do not.
  return (
    `${contextLabel(lane.category)} covered ${total} of this trace ` +
    `— ${laneSharePercent(lane.bands).toFixed(1)}%, across ${events}`
  );
}

function toggleCollapsed(spanId: string): void {
  const next = new Set(collapsed.value);
  if (!next.delete(spanId)) {
    next.add(spanId);
  }
  collapsed.value = next;
}

function toggleAll(): void {
  collapsed.value = allCollapsed.value ? new Set() : new Set(parents.value);
}

function twistTitle(span: TraceSpanRow): string {
  const hidden = foldedCounts.value.get(span.spanId) ?? 0;
  const spans = hidden === 1 ? '1 span' : `${hidden} spans`;
  return collapsed.value.has(span.spanId) ? `Expand ${spans}` : `Collapse ${spans}`;
}

/**
 * Brings a row into view after a jump has unfolded the tree above it. Deferred to the next frame
 * because folding can re-render the list the target row lives in.
 */
function scrollRowIntoView(spanId: string): void {
  requestAnimationFrame(() => {
    const row = document.querySelector<HTMLElement>(`.wf-row[data-span-id="${spanId}"]`);
    row?.scrollIntoView({ block: 'center' });
  });
}

function barStyle(span: TraceSpanRow) {
  const geometry = bar(span);
  const style: Record<string, string> = {
    left: geometry.leftPercent + '%',
    width: geometry.widthPercent + '%'
  };
  // A promoted wait wears its category's colour -- the same one its legend entry, the lanes and
  // the threads timeline use -- rather than a span-kind pastel: it is context turned into a bar,
  // not a new kind of span.
  const category = promotedColorCategory(span);
  if (category !== null) {
    style.background = contextColor(category);
  }
  return style;
}

function barClass(span: TraceSpanRow): string {
  if (span.status === 'ERROR') {
    return 'bar-error';
  }
  if (span.synthesized) {
    return 'bar-synthesized';
  }
  return 'bar-' + span.kind.toLowerCase();
}

function kindClass(span: TraceSpanRow): string {
  return 'kind-' + span.kind.toLowerCase();
}

/** The row marker follows the bar for a promoted wait, so the two cannot disagree about what it is. */
function kindStyle(span: TraceSpanRow): Record<string, string> | undefined {
  const category = promotedColorCategory(span);
  return category === null ? undefined : { background: contextColor(category) };
}

function promotedColorCategory(span: TraceSpanRow): string | null {
  return span.synthesized ? promotedCategory(span.eventType) : null;
}

/**
 * Self time is the number worth surfacing on hover: the duration is already in its own column,
 * so repeating it would say nothing the row does not already show.
 */
function tooltip(span: TraceSpanRow): string {
  const total = FormattingService.formatDuration2Units(span.durationNanos);
  const self = FormattingService.formatDuration2Units(span.selfDurationNanos);
  const thread = span.threadName ? ` · ${span.threadName}` : '';
  const critical = isCritical(span)
    ? `, ${FormattingService.formatDuration2Units(span.criticalPathNanos)} critical`
    : ', off the critical path';
  // The absolute instant is what lines a span up against application logs — the one correlation
  // the recording-relative offsets everywhere else cannot serve.
  const startedMicros = span.startEpochMicros - traceWindow(props.spans).startMicros;
  const offset = FormattingService.formatDuration2Units(startedMicros * NANOS_PER_MICRO);
  const wallClock = FormattingService.formatTimestamp(Math.floor(span.startEpochMicros / 1_000));
  // A promoted row names its source event, so the bar never passes itself off as instrumentation.
  const promoted = span.synthesized ? `\npromoted from ${span.eventType}` : '';
  return `${span.name} — ${total} total, ${self} self${critical}${thread}\nstarted ${offset} into the trace · ${wallClock}${promoted}`;
}
</script>

<style scoped>
/* Two panels with air between them: the drawing, then the legend that decodes it. */
.waterfall {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

/* The panel treatment the Spans views use, so the bars read as one surface rather than as a table. */
.wf-card {
  display: flex;
  flex-direction: column;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}

/* Filters sit above the scale rather than in the modal header: they change what this list draws. */
/*
 * The bottom padding is the gap to the pause lanes below. The controls change what the list draws
 * and the lanes are part of the drawing, so they need to read as two things rather than as one
 * crowded strip.
 */
.wf-toolbar {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.5rem 1rem 1.1rem;
}

.wf-toggle {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: var(--spacing-1) var(--spacing-3);
  border: 1px solid var(--color-border-input);
  border-radius: var(--radius-pill);
  background: var(--color-white);
  color: var(--color-text-muted);
  font-family: inherit;
  /*
   * The house small button. This padding is already `.btn-sm`'s, and `.scope-toggle button` in
   * TraceAttributeSearchBar is the same pill with the same padding at this size — the toolbar was
   * the one place still pairing that padding with full-size text.
   */
  font-size: var(--font-size-sm);
  font-weight: 500;
  cursor: pointer;
}

.wf-toggle:hover:not(:disabled) {
  background: var(--color-bg-hover-alt);
  color: var(--color-dark);
}

.wf-toggle.active {
  background: var(--color-primary-light);
  border-color: var(--color-primary);
  box-shadow: inset 0 0 0 1px var(--color-primary);
  color: var(--color-primary);
  font-weight: 600;
}

.wf-toggle:disabled {
  border-style: dashed;
  background: var(--color-light);
  color: var(--color-text-light);
  cursor: default;
}

/*
 * The overlay switches, boxed and labeled apart from the view actions. Disabled stays legible:
 * the label keeps a readable grey, the track goes hollow with a dashed outline — unmistakably
 * "cannot flip", distinct from an OFF switch which stays solid — and the reason wears the amber
 * zero tag instead of whispering in pale text.
 */
.wf-overlays {
  display: inline-flex;
  align-items: center;
  gap: 0.55rem;
  padding: var(--spacing-1) var(--spacing-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-light);
}

.wf-overlays-label {
  font-size: var(--font-size-xs);
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--color-text-light);
}

.wf-switch-item {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  padding: var(--spacing-1);
  border: 0;
  background: transparent;
  font-family: inherit;
  font-size: var(--font-size-sm);
  color: var(--color-dark);
  cursor: pointer;
}

.wf-switch {
  position: relative;
  width: 26px;
  height: 15px;
  border-radius: var(--radius-pill);
  background: var(--color-border-input);
  flex-shrink: 0;
}

.wf-switch::after {
  content: '';
  position: absolute;
  top: 2px;
  left: 2px;
  width: 11px;
  height: 11px;
  border-radius: var(--radius-circle);
  background: var(--color-white);
  box-shadow: var(--shadow-sm);
}

.wf-switch.on {
  background: var(--color-primary);
}

/* Pill width less the knob and the 2px inset it rests in at the other end: 26 - 11 - 2. */
.wf-switch.on::after {
  left: 13px;
}

.wf-switch-item:disabled {
  color: var(--color-text-muted);
  cursor: default;
}

.wf-switch-item:disabled .wf-switch {
  background: var(--color-white);
  border: 1.5px dashed var(--color-text-light);
}

.wf-switch-item:disabled .wf-switch::after {
  top: 1px;
  left: 1px;
  background: var(--color-lighter);
  box-shadow: none;
}

.wf-zero {
  font-size: var(--font-size-xs);
  font-weight: 600;
  letter-spacing: 0.03em;
  color: var(--color-warning-hover);
  background: var(--color-warning-light);
  border-radius: var(--radius-sm);
  padding: 1px var(--spacing-2);
}

.wf-head,
.wf-row,
.wf-lane,
.wf-stripes,
.wf-cursor {
  display: grid;
  grid-template-columns: 20rem 1fr 5.5rem;
  align-items: center;
  gap: 0.5rem;
}

/* The rows' own stacking context, which the stripe layer stretches over. */
.wf-rows {
  position: relative;
  display: flex;
  flex-direction: column;
}

/*
 * Everything in the block sits above the stripe wash. The rule is here rather than on each child
 * because the wash is stretched by a parent that also holds the open detail panel and the empty
 * state: a child that does not claim a layer is painted over by it, positioned elements being drawn
 * above static ones whatever background they carry. That is what ran GC bands through the span
 * detail's identity table.
 *
 * The two overlays are excluded because they place themselves: the wash below the rows, the cursor
 * above them.
 */
.wf-rows > :not(.wf-stripes, .wf-cursor) {
  position: relative;
  z-index: 1;
}

/* Above the rows so the line is not cut by each one, below the detail panel for the reason above. */
.wf-rows > .span-detail {
  z-index: 3;
}

.wf-lane {
  padding: 0.15rem 1rem;
  /* Matches the row's accent gutter so the lane track and the bar track share an origin. */
  border-left: 2px solid transparent;
}

/* Left-aligned, so the lane names start on the same column as the Span header beneath them. */
.lane-label {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: var(--font-size-xs);
  letter-spacing: 0.03em;
  text-transform: uppercase;
  font-weight: 600;
  color: var(--color-text-muted);
  white-space: nowrap;
}

.lane-name {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  flex: none;
}

/* Fills whatever the name and the figures leave, so the gutter carries a reading instead of air. */
.lane-meter {
  flex: 1 1 auto;
  min-width: 1.5rem;
  height: 0.3rem;
  border-radius: var(--radius-pill);
  background: var(--color-lighter);
  overflow: hidden;
}

.lane-meter i {
  display: block;
  height: 100%;
}

/*
 * Figures, so they take the monospace the duration column uses rather than the label's uppercase
 * treatment — they are numbers that sit beside a number, not part of the name.
 */
.lane-stat {
  flex: none;
  font-family: var(--font-family-monospace);
  font-size: var(--font-size-xs);
  font-variant-numeric: tabular-nums;
  letter-spacing: 0;
  text-transform: none;
  font-weight: 400;
  /* Muted rather than light: these are figures to be read, and --color-text-light against the card
     is under 2:1. Weight and case already tell them apart from the name beside them. */
  color: var(--color-text-muted);
}

.lane-dot {
  width: 0.5rem;
  height: 0.5rem;
  border-radius: var(--radius-xs);
  flex: none;
}

.lane-track {
  position: relative;
  height: 0.9rem;
}

.lane-track::before {
  content: '';
  position: absolute;
  inset: 0.42rem 0 auto 0;
  height: 1px;
  background: var(--color-border-light);
}

.lane-band {
  position: absolute;
  top: 0.08rem;
  height: 0.72rem;
  border-radius: var(--radius-xs);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.lane-band-text {
  font-family: var(--font-family-monospace);
  font-size: 0.55rem;
  font-weight: 600;
  color: var(--color-dark);
  white-space: nowrap;
}

/*
 * The wash behind the bars. Inert to the pointer so the rows above stay clickable, and behind them
 * so it reads as ground rather than as another bar.
 */
.wf-stripes {
  position: absolute;
  inset: 0;
  padding: 0 1rem;
  border-left: 2px solid transparent;
  pointer-events: none;
  z-index: 0;
}

.wf-stripes-track {
  position: relative;
  height: 100%;
}

/*
 * Fill only. The borders this used to carry added a hard 1px edge to each side of every band, which
 * on a trace crossing a hundred pauses drew as a picket fence over the whole track and put 2px on
 * top of a width that is already at its floor for most of them — the two overstatements compounded,
 * and between them the spans underneath stopped being readable.
 *
 * There is deliberately no companion rule giving `clamped` bands a stronger fill to compensate. Most
 * pauses in a real trace are shorter than the floor — a four-second trace puts it at 17ms, and
 * collections are routinely quicker than that — so emphasising the clamped ones emphasises nearly
 * all of them, which is the fence again in another colour. What they get instead is words: the band
 * says in its title that it is drawn at the minimum, and the cursor reads out its real duration.
 */
.wf-stripe {
  position: absolute;
  top: 0;
  bottom: 0;
  background: color-mix(in srgb, var(--stripe-color) 6%, transparent);
}

/*
 * The reading of the instant under the pointer. Inert, like the wash — it follows the pointer and
 * must never be what the pointer lands on.
 */
.wf-cursor {
  position: absolute;
  inset: 0;
  padding: 0 1rem;
  /* Matches the row's accent gutter, so the cursor's track and the bars' share an origin. */
  border-left: 2px solid transparent;
  pointer-events: none;
  z-index: 2;
}

.wf-cursor-track {
  position: relative;
  height: 100%;
}

.wf-cursor-line {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 1px;
  background: var(--color-dark);
}

/*
 * Sits above the rows, over the scale it is a reading of. Pulled to the pointer's own column rather
 * than pinned to a corner: at four seconds across, a readout at the edge is a different measurement
 * from the one being pointed at.
 */
.wf-cursor-chip {
  position: absolute;
  bottom: 100%;
  left: 0;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 0.35rem;
  margin-bottom: 0.15rem;
  padding: 0.1rem 0.45rem;
  border-radius: var(--radius-sm);
  background: var(--color-dark);
  color: var(--color-bg-card);
  font-family: var(--font-family-monospace);
  font-size: var(--font-size-xs);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.wf-cursor-pause {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
}

/* In the header the column is a label rather than a figure, so it keeps the header's own size. */
.wf-head .wf-duration {
  font-size: inherit;
}

.wf-head {
  padding: 0.55rem 1rem 0.4rem;
  /*
   * The same accent gutter the rows and lanes carry. Without it the header sat two pixels left of
   * everything it labels — invisible until the lane names moved to this column and had something to
   * be out of line with.
   */
  border-left: 2px solid transparent;
  font-size: var(--font-size-xs);
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--color-text-muted);
  font-weight: 600;
}

.wf-scale {
  display: flex;
  justify-content: space-between;
  font-variant-numeric: tabular-nums;
}

.wf-row {
  width: 100%;
  padding: 0.28rem 1rem;
  border: 0;
  border-bottom: 1px solid var(--color-border-light);
  /*
   * Carried by every row, transparent unless the span is on the critical path, so switching the
   * filter on and off never shifts the names sideways.
   */
  border-left: 2px solid transparent;
  background: transparent;
  font-family: inherit;
  font-size: var(--font-size-sm);
  text-align: left;
  cursor: pointer;
}

.wf-row.critical {
  border-left-color: var(--color-warning);
}

/*
 * Two rules rather than a fill. The fill was `--color-bg-hover-alt`, three percent off the card it
 * sits on — a separation that works on a clean table and disappears entirely under a pause wash. It
 * was also opaque, so it painted out the bands on the one row being inspected, while the translucent
 * selected state left them showing: hovering a row and selecting it disagreed about what the JVM had
 * been doing to it. A rule top and bottom is unmissable at any density and hides nothing.
 */
.wf-row:hover {
  box-shadow:
    inset 0 1px 0 var(--color-primary),
    inset 0 -1px 0 var(--color-primary);
}

.wf-row.selected {
  background: var(--color-primary-light);
}

.wf-name {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  min-width: 0;
}

.wf-indent {
  flex: none;
}

/*
 * The twistie keeps its width on a leaf, so names stay on one column whether or not a span has
 * children -- a tree whose labels shift left at every leaf is much harder to read down.
 */
.wf-twist {
  flex: none;
  width: 0.8rem;
  font-size: 0.6rem;
  line-height: 1;
  color: var(--color-text-muted);
  cursor: pointer;
}

.wf-twist:hover {
  color: var(--color-dark);
}

.wf-twist.is-leaf {
  cursor: inherit;
}

/* How many rows a fold is hiding, so a collapsed span does not look like a leaf. */
.wf-folded {
  flex: none;
  padding: 0 0.25rem;
  border-radius: var(--radius-xs);
  background: var(--color-lighter);
  color: var(--color-text-muted);
  font-size: 0.6rem;
  font-variant-numeric: tabular-nums;
}

/* The dot that says a fold is hiding a failure — visible at a glance, explained on hover. */
/*
 * ---------------------------------------------------------------------------------------------
 * The instant rails, their pins and their popovers.
 *
 * Shape carries the family before colour does: a notification is a diamond, a throw is a cross, a
 * pause is a band. That is what keeps the severity ramp survivable while it shares danger with the
 * GC lane and warning with the critical-path marker.
 */
.wf-rail .lane-track {
  height: 1.15rem;
}

.rail-glyph {
  width: 0.45rem;
  height: 0.45rem;
  flex: none;
  display: inline-block;
}

.rail-glyph.ntf {
  border-radius: 1px;
  transform: rotate(45deg);
}

.rail-glyph.exc {
  position: relative;
  width: 0.55rem;
  height: 0.55rem;
  background: transparent;
}

.rail-glyph.exc::before,
.rail-glyph.exc::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 0;
  bottom: 0;
  width: 1.5px;
  margin-left: -0.75px;
  border-radius: 1px;
  background: currentColor;
}

.rail-glyph.exc::before {
  transform: rotate(45deg);
}

.rail-glyph.exc::after {
  transform: rotate(-45deg);
}

/* The rail's own hairline, so a mark sits on something rather than floating in the gap. */
.rail-rule {
  position: absolute;
  inset: 50% 0 auto 0;
  height: 1px;
  background: var(--color-border-light);
}

.rail-mark {
  position: absolute;
  top: 50%;
  padding: 0;
  border: 0;
  background: transparent;
  cursor: pointer;
}

.rail-mark.ntf {
  width: 0.55rem;
  height: 0.55rem;
  margin: -0.275rem 0 0 -0.275rem;
  border-radius: 1px;
  background: var(--mark);
  transform: rotate(45deg);
  box-shadow: 0 0 0 2px var(--color-bg-card);
}

.rail-mark.exc {
  width: 0.7rem;
  height: 0.7rem;
  margin: -0.35rem 0 0 -0.35rem;
}

.rail-mark.exc::before,
.rail-mark.exc::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 0;
  bottom: 0;
  width: 1.5px;
  margin-left: -0.75px;
  border-radius: 1px;
  background: var(--mark);
}

.rail-mark.exc::before {
  transform: rotate(45deg);
}

.rail-mark.exc::after {
  transform: rotate(-45deg);
}

/* An escaped throw is the reason a span failed, so it is drawn as heavily as that fact deserves. */
.rail-mark.exc.escaped::before,
.rail-mark.exc.escaped::after {
  width: 2px;
  margin-left: -1px;
}

.rail-mark:focus-visible,
.rail-mark.open {
  outline: 2px solid var(--color-dark);
  outline-offset: 2px;
}

/*
 * A pin is the same instant on the bar that raised it. Positioned against the track, not the bar,
 * so it shares an x with its rail mark and the two readings line up down the column.
 *
 * Inert to the pointer: the row is the click target, and the panel it opens lists these in full.
 */
.wf-pin {
  position: absolute;
  top: 0;
  height: 1.1rem;
  width: 7px;
  margin-left: -3.5px;
  pointer-events: none;
}

.wf-pin::before {
  content: '';
  position: absolute;
  left: 3px;
  top: 0.35rem;
  bottom: 0.1rem;
  width: 1px;
  background: var(--mark);
  opacity: 0.75;
}

.wf-pin::after {
  content: '';
  position: absolute;
  left: 0.5px;
  top: 0.1rem;
  width: 6px;
  height: 6px;
  background: var(--mark);
  box-shadow: 0 0 0 1.5px var(--color-bg-card);
}

.wf-pin.ntf::after {
  border-radius: 1px;
  transform: rotate(45deg);
}

.wf-pin.exc::after {
  border-radius: var(--radius-circle);
}

.wf-pin.exc.escaped::after {
  width: 7px;
  height: 7px;
  left: 0;
}

.wf-count {
  flex: none;
  padding: 0 0.3rem;
  border-radius: var(--radius-xs);
  background: color-mix(in srgb, var(--mark) 14%, transparent);
  color: var(--mark);
  font-size: 0.6rem;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

/*
 * What a fold swallowed, drawn hollow beside the +N count and the hidden-error dot -- the same
 * place and the same job. Hollow so it never reads as what this span itself carries.
 */
.wf-count.folded {
  background: transparent;
  border: 1px dashed var(--mark);
  color: var(--color-text-muted);
  --mark: var(--color-text-light);
}

/*
 * The fast read. Anchored on its own mark and pulled most of its width to the left, so a mark near
 * the right edge cannot push the panel over the Duration column.
 */
.rail-pop {
  position: absolute;
  z-index: 6;
  width: 22rem;
  max-width: 60vw;
  padding: 0.55rem 0.7rem 0.6rem;
  transform: translateX(-70%);
  border: 1px solid var(--color-border-input);
  border-left: 3px solid var(--mark);
  border-radius: var(--radius-md);
  background: var(--color-bg-card);
  box-shadow: var(--shadow-lg);
  text-align: left;
  cursor: default;
  /*
   * The rails sit directly under the toolbar, so upward there is only the dialog's own chrome to
   * grow into. A long message scrolls inside the popover instead of running off the top of the
   * dialog, where it could not be read or dismissed.
   */
  max-height: 13rem;
  overflow-y: auto;
}

.rail-pop.up {
  bottom: 1.35rem;
}

/*
 * The docked strip. Full width by virtue of being a block in the waterfall's own column rather than
 * an absolutely positioned panel over it, so nothing has to be told how wide the dialog is.
 *
 * Sunken, and flush, on purpose. The strip does not float: it sits in the flow and pushes the bars
 * down, which is the whole reason it docks instead of hovering — a panel wide enough for a fully
 * qualified frame would otherwise cover the bars being compared. A raised, inset treatment says the
 * opposite of that, so it is the wrong costume however good it looks.
 *
 * The separation it does need comes from the ground instead: the strip and the card were both
 * --color-bg-card, told apart by a hairline. --color-lighter over an inset shadow reads as a well
 * cut into the card, which is what the parted rows above and below already say.
 */
.exc-dock {
  margin: 0 0 0.4rem;
  border: 1px solid var(--color-border-input);
  border-left: 3px solid var(--mark);
  border-radius: var(--radius-md);
  background: var(--color-lighter);
  box-shadow: var(--shadow-inset);
}

.dock-head {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.26rem 0.5rem;
  border-bottom: 1px solid var(--color-border-input);
  background: transparent;
}

/*
 * A span name is arbitrarily long, so the button is capped and ellipsised rather than allowed to
 * push the panel's own two controls off the end of the row.
 */
.dock-select {
  max-width: 22rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
}

/*
 * The strip does not close itself, so this is the only way out and has to look like one: a real
 * target rather than a glyph, in the muted ink the rest of the header uses rather than the lightest
 * one on the palette.
 */
.dock-close {
  display: inline-grid;
  place-items: center;
  flex: none;
  /* The offset used to push this right; with that gone the button takes the job itself. */
  margin-left: auto;
  width: 1.2rem;
  height: 1.2rem;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-text-muted);
  cursor: pointer;
}

.dock-close:hover {
  background: var(--color-lighter);
  color: var(--color-dark);
}

/*
 * The stack scrolls inside the strip rather than growing it without limit: a 253-frame stack
 * unfolded is taller than the dialog, and a strip that pushed the whole waterfall off the bottom
 * would have traded one covered drawing for another.
 */
.dock-stack {
  max-height: 26rem;
  overflow-y: auto;
  padding: 0.25rem 0.5rem 0.35rem;
}

/*
 * The fold bars are --color-lighter, which was a tint against the white the stack normally sits on
 * and is invisible against the sunken ground this one gives it. The panel that changed the ground
 * is the one that owes them a new one.
 */
.dock-stack :deep(.st-fold) {
  background: var(--color-bg-card);
}

/*
 * With no stack there is no JVM line either, so this is the one place the strip still has to name
 * the throw itself — otherwise it would say only that something was caught.
 */
.dock-none {
  padding: 0.25rem 0.5rem 0.45rem;
  font-size: var(--font-size-xs);
  color: var(--color-text-muted);
}

.dock-none-cls {
  margin-bottom: 0.2rem;
  font-size: var(--font-size-sm);
  font-weight: 700;
  color: var(--color-danger);
}

.pop-head {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  flex-wrap: wrap;
  margin-bottom: 0.2rem;
}

.pop-sev {
  font-size: var(--font-size-xs);
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--mark);
}

.pop-type,
.pop-at {
  font-family: var(--font-family-monospace);
  font-size: var(--font-size-xs);
  color: var(--color-text-muted);
}

.pop-at {
  margin-left: auto;
  font-variant-numeric: tabular-nums;
}

.pop-title {
  font-size: var(--font-size-base);
  font-weight: 700;
  color: var(--color-dark);
  line-height: 1.3;
}

.pop-title.mono {
  font-family: var(--font-family-monospace);
  font-size: var(--font-size-sm);
  overflow-wrap: anywhere;
}

.pop-body {
  margin-top: 0.2rem;
  font-size: var(--font-size-sm);
  color: var(--color-text);
  line-height: 1.5;
}

.pop-meta {
  display: flex;
  gap: 0.8rem;
  flex-wrap: wrap;
  margin-top: 0.35rem;
  font-family: var(--font-family-monospace);
  font-size: var(--font-size-xs);
  color: var(--color-text-muted);
}

.pop-link {
  display: flex;
  align-items: center;
  gap: 0.3rem;
  margin-top: 0.4rem;
  padding: 0.3rem 0 0;
  border: 0;
  border-top: 1px solid var(--color-border-light);
  background: transparent;
  color: var(--color-primary);
  font-family: inherit;
  font-size: var(--font-size-sm);
  font-weight: 600;
  cursor: pointer;
  width: 100%;
}

.pop-orphan {
  margin-top: 0.4rem;
  padding-top: 0.3rem;
  border-top: 1px solid var(--color-border-light);
  font-size: var(--font-size-xs);
  color: var(--color-text-muted);
}

.wf-folded-error {
  flex: none;
  width: 0.4rem;
  height: 0.4rem;
  border-radius: var(--radius-pill);
  background: var(--color-danger);
}

.wf-error-jump {
  color: var(--color-danger);
}

.wf-kind {
  width: 0.45rem;
  height: 0.45rem;
  border-radius: var(--radius-circle);
  flex: none;
}

.kind-server {
  background: var(--color-primary);
}

.kind-client {
  background: var(--color-info);
}

.kind-internal {
  background: var(--color-secondary);
}

.wf-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-dark);
}

/* The track a bar is positioned inside, by percentage of the trace window. */
.wf-track {
  position: relative;
  height: 1.1rem;
}

.wf-track::before {
  content: '';
  position: absolute;
  inset: 0.5rem 0 auto 0;
  height: 1px;
  background: var(--color-border-light);
}

.wf-bar {
  position: absolute;
  top: 0.15rem;
  height: 0.8rem;
  border-radius: var(--radius-xs);
  overflow: hidden;
  display: block;
}

.wf-run-row .wf-label {
  font-weight: 600;
}

.wf-run-count {
  font-family: var(--font-family-monospace);
  font-size: var(--font-size-xs);
  font-weight: 700;
  color: var(--color-white);
  background: var(--color-primary);
  border-radius: var(--radius-pill);
  padding: 1px var(--spacing-2);
  flex-shrink: 0;
}

/* A 4ms write on a 15s trace is subpixel; the floor keeps every occurrence a visible tick. */
.wf-run-tick {
  min-width: 2px;
}

/*
 * While the statistics panel is open it is part of the aggregated row above it, so the row's own
 * bottom border is suppressed and the panel's wrapper carries the full-width divider instead —
 * the pair reads as one unit, separated from the next row rather than glued to it.
 */
.wf-run-row.detail-open {
  border-bottom: 0;
}

.wf-run-stats-toggle {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-1);
  flex-shrink: 0;
  font-size: var(--font-size-xs);
  font-weight: 600;
  color: var(--color-text-muted);
  background: var(--color-white);
  border: 1px solid var(--color-border-input);
  border-radius: var(--radius-pill);
  padding: 1px var(--spacing-2);
  cursor: pointer;
}

.wf-run-stats-toggle:hover {
  color: var(--color-primary);
  border-color: var(--color-primary-border);
}

.wf-run-stats-toggle.open {
  color: var(--color-primary);
  background: var(--color-primary-lighter);
  border-color: var(--color-primary-border);
}

.wf-run-stats-glyph {
  display: inline-flex;
  align-items: flex-end;
  gap: 1px;
  height: 9px;
}

.wf-run-stats-glyph i {
  width: 2.5px;
  background: currentColor;
  opacity: 0.7;
  border-radius: 1px 1px 0 0;
}

.wf-run-detail-row {
  border-bottom: 1px solid var(--color-border-light);
}

.wf-run-detail {
  display: flex;
  align-items: center;
  gap: var(--spacing-6);
  flex-wrap: wrap;
  /* The left margin comes inline, per row — it follows the run's own indent depth. */
  margin: var(--spacing-1) 1rem var(--spacing-2) 0;
  padding: var(--spacing-2) var(--spacing-3);
  background: var(--color-light);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-sm);
}

.wf-run-stat {
  display: flex;
  flex-direction: column;
}

.wf-run-stat-label {
  font-size: var(--font-size-xs);
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.wf-run-stat-value {
  font-family: var(--font-family-monospace);
  font-size: var(--font-size-base);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--color-dark);
}

.wf-run-histogram {
  display: inline-flex;
  align-items: flex-end;
  gap: 3px;
  height: 30px;
}

.wf-run-histogram i {
  width: 22px;
  border-radius: 2px 2px 0 0;
  background: var(--color-primary);
  opacity: 0.35;
}

.wf-run-histogram i.hot {
  opacity: 1;
}

/*
 * The pale body is the whole span; the solid stretches are the span's own work, drawn where it
 * actually happened rather than gathered into a block at the front. The gaps between them are its
 * children, so the row reads as an alternation instead of as two things running at once.
 */
.wf-self {
  position: absolute;
  top: 0;
  height: 100%;
}

.bar-server {
  background: color-mix(in srgb, var(--flamegraph-color-blue) 35%, transparent);
}

.bar-server .wf-self {
  background: var(--flamegraph-color-blue);
}

.bar-client {
  background: color-mix(in srgb, var(--flamegraph-color-cyan) 35%, transparent);
}

.bar-client .wf-self {
  background: var(--flamegraph-color-cyan);
}

.bar-internal {
  background: color-mix(in srgb, var(--flamegraph-color-green) 40%, transparent);
}

.bar-internal .wf-self {
  background: var(--flamegraph-color-green);
}

.bar-error {
  background: var(--color-danger-light);
}

.bar-error .wf-self {
  background: var(--flamegraph-color-red);
}

/*
 * A promoted wait's colour is its category's, set inline where the bar is laid out — the palette
 * lives in traceLabels, not here. The whole bar is solid: a synthesized span is a leaf, so a wash
 * plus self overlay would draw the same stretch twice in two shades of the same colour.
 */
.bar-synthesized {
  opacity: 0.9;
}

.bar-synthesized .wf-self {
  display: none;
}

/*
 * The size is set here rather than left to inherit. This column appears in a row, a lane and the
 * header, and only the row carries a font-size — so a lane's total inherited the page's instead and
 * rendered a quarter larger than the identical column directly beneath it.
 */
.wf-duration {
  font-family: var(--font-family-monospace);
  font-size: var(--font-size-sm);
  font-variant-numeric: tabular-nums;
  text-align: right;
  white-space: nowrap;
  color: var(--color-text);
}

.wf-legend {
  display: flex;
  gap: 0.9rem;
  flex-wrap: wrap;
  font-size: 0.62rem;
  letter-spacing: 0.03em;
  text-transform: uppercase;
  color: var(--color-text-muted);
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 0.6rem 1rem;
}

.wf-legend span {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
}

.swatch {
  width: 0.6rem;
  height: 0.6rem;
  border-radius: var(--radius-xs);
  display: inline-block;
}

/* Half solid, half washed — the anatomy of every bar, in the server hue as the worked example. */
.swatch-selfchildren {
  background: linear-gradient(
    to right,
    var(--flamegraph-color-blue) 50%,
    color-mix(in srgb, var(--flamegraph-color-blue) 35%, transparent) 50%
  );
}

.swatch-error {
  background: var(--flamegraph-color-red);
}

/* Matches the row's left accent rather than a bar colour: the critical path marks rows, not spans. */
.swatch-critical {
  background: var(--color-warning);
}

/* The bar wash itself, not the row-marker hue: a legend must show the colour it explains. */
.swatch-server {
  background: color-mix(in srgb, var(--flamegraph-color-blue) 35%, transparent);
}

.swatch-client {
  background: color-mix(in srgb, var(--flamegraph-color-cyan) 35%, transparent);
}

.swatch-internal {
  background: color-mix(in srgb, var(--flamegraph-color-green) 40%, transparent);
}
</style>

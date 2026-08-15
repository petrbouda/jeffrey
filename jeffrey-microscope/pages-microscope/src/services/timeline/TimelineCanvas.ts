/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import Konva from 'konva';

import FormattingService from '@shared/services/FormattingService';
import {
  TIMELINE_METRICS as M,
  pauseColor,
  pauseLabel,
  resolveToken,
  spanKindColor
} from '@/services/timeline/timelineTheme';
import {
  microsAt,
  panByPixels,
  placeInterval,
  tickStepMicros,
  ticksIn,
  xOf,
  zoomAt
} from '@/services/timeline/TimelineViewport';

import type {
  TimelineSpan,
  TimelineTrack,
  TimelineWindow
} from '@/services/api/model/TimelineModels';
import type { Viewport } from '@/services/timeline/TimelineViewport';

const NANOS_PER_MICRO = 1_000;
const TARGET_TICKS = 8;
/** Below this a span is a sliver; drawing its name wastes the pixels the bar needs. */
const MIN_LABEL_WIDTH_PX = 38;
/** How hard a wheel notch zooms. Tuned so one notch is a noticeable step, not a jump. */
const ZOOM_SENSITIVITY = 0.0016;
/** A pointer that moved less than this between down and up was a click, not a drag. */
const DRAG_THRESHOLD_PX = 3;
/** Forgiveness around a bar's drawn edges, so a 1.5 px minimum-width bar is genuinely clickable. */
const HIT_SLOP_PX = 3;
/** Gap between the cursor and the tooltip, on whichever side the tooltip ends up. */
const TOOLTIP_OFFSET_PX = 14;

export interface TimelineCanvasOptions {
  bounds: Viewport;
  viewport: Viewport;
  onViewportChanged: (view: Viewport) => void;
  /** The clicked span's name travels along so the opened modal can title itself. */
  onSpanSelected: (traceId: string, spanName: string) => void;
}

/** What the pointer is over, for the tooltip and for the click. */
type Hit =
  | { type: 'span'; span: TimelineSpan; track: TimelineTrack }
  | { type: 'pause'; category: string; label: string; fromMicros: number; durationNanos: number };

interface Row {
  kind: 'global' | 'group' | 'thread';
  y: number;
  height: number;
  track?: TimelineTrack;
  label: string;
}

/**
 * Draws the unified timeline and owns its interaction.
 *
 * Konva rather than DOM because the surface is thousands of rectangles that all move together on
 * every pan and zoom; a DOM node per span would spend its whole budget on layout. It is deliberately
 * a plain class rather than a component — the geometry lives in {@link TimelineViewport}, the
 * palette in {@link timelineTheme}, and this holds only the parts that genuinely need a canvas.
 */
export default class TimelineCanvas {
  private readonly stage: Konva.Stage;
  private readonly layer: Konva.Layer;
  private readonly tooltip: HTMLDivElement;
  private readonly host: HTMLElement;
  private readonly options: TimelineCanvasOptions;
  private readonly resizeObserver: ResizeObserver;

  private view: Viewport;
  private data: TimelineWindow | null = null;
  private rows: Row[] = [];

  private dragging = false;
  /** Where the pan last read the pointer, for the per-event viewport shift. */
  private dragOriginX = 0;
  /**
   * Where the pointer went down, never reassigned during the drag. The click-vs-drag test must be
   * cumulative: testing per-event deltas meant a slow, careful pan — the exact motion used to line
   * a pause up with a thread — never exceeded the threshold and was treated as a click.
   */
  private dragStartX = 0;
  private dragMoved = false;

  constructor(host: HTMLElement, options: TimelineCanvasOptions) {
    this.host = host;
    this.options = options;
    this.view = { ...options.viewport };

    this.stage = new Konva.Stage({
      container: host,
      width: Math.max(1, host.clientWidth),
      height: Math.max(1, host.clientHeight)
    });
    this.layer = new Konva.Layer({ listening: false });
    this.stage.add(this.layer);

    this.tooltip = document.createElement('div');
    this.tooltip.className = 'tl-tooltip';
    this.tooltip.style.display = 'none';
    document.body.appendChild(this.tooltip);

    this.bindEvents();

    this.resizeObserver = new ResizeObserver(() => this.handleResize());
    this.resizeObserver.observe(host);

    this.draw();
  }

  setData(data: TimelineWindow): void {
    this.data = data;
    this.draw();
  }

  setViewport(view: Viewport): void {
    this.view = { ...view };
    this.draw();
  }

  destroy(): void {
    this.resizeObserver.disconnect();
    this.stage.destroy();
    this.tooltip.remove();
  }

  private plotWidth(): number {
    return Math.max(1, this.stage.width() - M.gutterWidth);
  }

  private handleResize(): void {
    this.stage.width(Math.max(1, this.host.clientWidth));
    this.stage.height(Math.max(1, this.host.clientHeight));
    this.draw();
  }

  // ---------------------------------------------------------------- interaction

  private bindEvents(): void {
    const container = this.stage.container();

    container.addEventListener('wheel', this.onWheel, { passive: false });
    container.addEventListener('pointerdown', this.onPointerDown);
    container.addEventListener('pointermove', this.onPointerMove);
    container.addEventListener('pointerup', this.onPointerUp);
    container.addEventListener('pointerleave', this.onPointerLeave);
    container.addEventListener('pointercancel', this.onPointerCancel);
    container.style.cursor = 'grab';
    // Without this the browser claims touch gestures for native scrolling and cancels the drag.
    container.style.touchAction = 'none';
  }

  private readonly onWheel = (event: WheelEvent): void => {
    event.preventDefault();
    const x = this.localX(event);
    if (x < M.gutterWidth) {
      return;
    }
    const anchor = microsAt(x - M.gutterWidth, this.view, this.plotWidth());
    const factor = Math.exp(event.deltaY * ZOOM_SENSITIVITY);
    this.view = zoomAt(this.view, anchor, factor, this.options.bounds);
    this.draw();
    this.options.onViewportChanged(this.view);
  };

  private readonly onPointerDown = (event: PointerEvent): void => {
    this.dragging = true;
    this.dragMoved = false;
    this.dragOriginX = event.clientX;
    this.dragStartX = event.clientX;
    this.stage.container().style.cursor = 'grabbing';
    this.stage.container().setPointerCapture(event.pointerId);
  };

  private readonly onPointerMove = (event: PointerEvent): void => {
    if (this.dragging) {
      const delta = event.clientX - this.dragOriginX;
      // Cumulative distance from pointer-down, not this event's delta: a pan is a pan however
      // slowly it was made.
      if (Math.abs(event.clientX - this.dragStartX) > DRAG_THRESHOLD_PX) {
        this.dragMoved = true;
      }
      this.dragOriginX = event.clientX;
      this.view = panByPixels(this.view, delta, this.plotWidth(), this.options.bounds);
      this.draw();
      this.options.onViewportChanged(this.view);
      return;
    }
    this.showTooltip(event);
  };

  private readonly onPointerUp = (event: PointerEvent): void => {
    const wasDrag = this.dragMoved;
    this.dragging = false;
    this.stage.container().style.cursor = 'grab';
    this.stage.container().releasePointerCapture(event.pointerId);
    if (wasDrag) {
      return;
    }
    const hit = this.hitTest(this.localX(event), this.localY(event));
    if (hit?.type === 'span') {
      this.options.onSpanSelected(hit.span.traceId, hit.span.name);
    }
  };

  private readonly onPointerLeave = (): void => {
    this.tooltip.style.display = 'none';
    // An interrupted drag (alt-tab, OS gesture) must not leave the canvas stuck in grabbing.
    this.dragging = false;
    this.stage.container().style.cursor = 'grab';
  };

  private readonly onPointerCancel = (): void => {
    this.dragging = false;
    this.stage.container().style.cursor = 'grab';
    this.tooltip.style.display = 'none';
  };

  private localX(event: MouseEvent): number {
    return event.clientX - this.stage.container().getBoundingClientRect().left;
  }

  private localY(event: MouseEvent): number {
    return event.clientY - this.stage.container().getBoundingClientRect().top;
  }

  private showTooltip(event: PointerEvent): void {
    const hit = this.hitTest(this.localX(event), this.localY(event));
    // The pointer is the affordance: over 3000 rectangles only spans are clickable, and nothing
    // else tells the reader which kind they are on.
    this.stage.container().style.cursor = hit?.type === 'span' ? 'pointer' : 'grab';
    if (!hit) {
      this.tooltip.style.display = 'none';
      return;
    }
    this.tooltip.replaceChildren(...this.describe(hit));
    this.tooltip.style.display = 'block';
    // Flip when the default placement would run off screen: the most recent spans sit at the right
    // edge, which is exactly where a fixed right-of-cursor tooltip is clipped.
    const width = this.tooltip.offsetWidth;
    const height = this.tooltip.offsetHeight;
    const left =
      event.clientX + TOOLTIP_OFFSET_PX + width > window.innerWidth
        ? event.clientX - width - TOOLTIP_OFFSET_PX
        : event.clientX + TOOLTIP_OFFSET_PX;
    const top =
      event.clientY + TOOLTIP_OFFSET_PX + height > window.innerHeight
        ? event.clientY - height - TOOLTIP_OFFSET_PX
        : event.clientY + TOOLTIP_OFFSET_PX;
    this.tooltip.style.left = `${Math.max(0, left)}px`;
    this.tooltip.style.top = `${Math.max(0, top)}px`;
  }

  /**
   * Built as DOM nodes, never as an HTML string: span and thread names come straight from
   * application instrumentation — URIs, SQL, gRPC methods — and routinely contain markup
   * characters. Interpolating them into innerHTML rendered them wrong at best and executed them at
   * worst.
   */
  private describe(hit: Hit): HTMLElement[] {
    const line = (tag: 'b' | 'span', text: string): HTMLElement => {
      const node = document.createElement(tag);
      node.textContent = text;
      return node;
    };

    if (hit.type === 'span') {
      const duration = FormattingService.formatDuration2Units(hit.span.durationNanos);
      const error = hit.span.status === 'ERROR' ? ' · error' : '';
      return [
        line('b', hit.span.name),
        line('span', `${hit.track.threadName ?? 'unknown thread'} · ${hit.span.kind}${error}`),
        line('span', `${duration} · click to open the trace`)
      ];
    }
    return [
      line('b', `${pauseLabel(hit.category)} — ${hit.label}`),
      line('span', FormattingService.formatDuration2Units(hit.durationNanos)),
      line('span', 'stopped every application thread')
    ];
  }

  /**
   * Whether the pointer's plot-x lands on the interval as it was drawn.
   *
   * Tested in pixel space against the same {@link placeInterval} geometry the renderer used, with a
   * small slop, never against the interval's true microsecond extent. Testing in time units undid
   * the minimum-width floor: a 5 µs span in a 30 s window was drawn 1.5 px wide but hoverable only
   * across its ~0.0002 px time footprint — visible and untouchable, the worst of both.
   */
  private hitsInterval(plotX: number, fromMicros: number, toMicros: number): boolean {
    const placed = placeInterval(fromMicros, toMicros, this.view, this.plotWidth());
    if (!placed) {
      return false;
    }
    return plotX >= placed.x - HIT_SLOP_PX && plotX <= placed.x + placed.width + HIT_SLOP_PX;
  }

  private hitTest(x: number, y: number): Hit | null {
    if (x < M.gutterWidth || !this.data) {
      return null;
    }
    const plotX = x - M.gutterWidth;

    for (const row of this.rows) {
      if (y < row.y || y > row.y + row.height) {
        continue;
      }
      if (row.kind === 'global') {
        const pause = this.data.pauses.find(
          p =>
            pauseLabel(p.category) === row.label &&
            this.hitsInterval(
              plotX,
              p.startEpochMicros,
              p.startEpochMicros + p.durationNanos / NANOS_PER_MICRO
            )
        );
        return pause
          ? {
              type: 'pause',
              category: pause.category,
              label: pause.label,
              fromMicros: pause.startEpochMicros,
              durationNanos: pause.durationNanos
            }
          : null;
      }
      if (row.kind !== 'thread' || !row.track) {
        return null;
      }
      const lane = Math.floor((y - row.y - M.threadRowHeight) / (M.spanLaneHeight + M.laneGap));
      if (lane < 0) {
        return null;
      }
      const span = row.track.spans.find(
        s =>
          s.depth === lane &&
          this.hitsInterval(
            plotX,
            s.startEpochMicros,
            s.startEpochMicros + s.durationNanos / NANOS_PER_MICRO
          )
      );
      return span ? { type: 'span', span, track: row.track } : null;
    }
    return null;
  }

  // ---------------------------------------------------------------- drawing

  /**
   * Rows are laid out before anything is drawn, and kept, because hit-testing has to agree with the
   * picture exactly. Deriving the two separately is how a click lands on the row above the one the
   * reader aimed at.
   */
  private layoutRows(): Row[] {
    const rows: Row[] = [];
    let y = M.rulerHeight;
    if (!this.data) {
      return rows;
    }

    const categories = [...new Set(this.data.pauses.map(p => p.category))];
    for (const category of categories) {
      rows.push({ kind: 'global', y, height: M.globalTrackHeight, label: pauseLabel(category) });
      y += M.globalTrackHeight + M.trackGap;
    }

    for (const [group, tracks] of this.groupTracks()) {
      rows.push({ kind: 'group', y, height: M.groupHeaderHeight, label: group });
      y += M.groupHeaderHeight + M.trackGap;
      for (const track of tracks) {
        const height = M.threadRowHeight + track.laneCount * (M.spanLaneHeight + M.laneGap);
        rows.push({ kind: 'thread', y, height, track, label: track.threadName ?? 'unknown' });
        y += height + M.trackGap;
      }
    }
    return rows;
  }

  /**
   * Threads grouped by pool, by stripping the trailing worker index off the name.
   *
   * The same shape `ThreadGroups` gives the existing thread timeline: forty `http-nio-8080-exec-N`
   * rows are one pool, and a reader looking for a slow request wants the pool, not the ordinal.
   */
  private groupTracks(): Map<string, TimelineTrack[]> {
    const groups = new Map<string, TimelineTrack[]>();
    for (const track of this.data?.tracks ?? []) {
      const name = track.threadName ?? 'unknown';
      const group = name.replace(/[-#]?\d+$/, '') || name;
      const bucket = groups.get(group);
      if (bucket) {
        bucket.push(track);
      } else {
        groups.set(group, [track]);
      }
    }
    return groups;
  }

  private draw(): void {
    this.layer.destroyChildren();
    this.rows = this.layoutRows();

    const width = this.stage.width();
    const height = this.stage.height();
    const plot = this.plotWidth();

    const gridColor = resolveToken('--color-border-light', '#edf2f9');
    const borderColor = resolveToken('--color-border', '#eaedf1');
    const mutedColor = resolveToken('--color-text-muted', '#748194');
    const inkColor = resolveToken('--color-dark', '#0b1727');
    const sunkenColor = resolveToken('--color-bg-hover-alt', '#f2f5fc');

    const step = tickStepMicros(this.view.to - this.view.from, TARGET_TICKS);
    const ticks = ticksIn(this.view, step);

    for (const tick of ticks) {
      const x = Math.round(M.gutterWidth + xOf(tick, this.view, plot)) + 0.5;
      this.layer.add(
        new Konva.Line({
          points: [x, M.rulerHeight, x, height],
          stroke: gridColor,
          strokeWidth: 1
        })
      );
    }

    this.drawPauseStripes(height, plot);
    this.drawRows(width, plot, { gridColor, mutedColor, inkColor, sunkenColor });
    this.drawRuler(width, plot, ticks, { borderColor, mutedColor });
    this.drawMinimap(height, plot);

    this.layer.add(
      new Konva.Line({
        points: [M.gutterWidth + 0.5, 0, M.gutterWidth + 0.5, height],
        stroke: borderColor,
        strokeWidth: 1
      })
    );

    this.layer.batchDraw();
  }

  /**
   * The wash down every track, which is the whole point of the view: one pause, one vertical slice,
   * and every thread it stopped visible at once.
   */
  private drawPauseStripes(height: number, plot: number): void {
    for (const pause of this.data?.pauses ?? []) {
      const to = pause.startEpochMicros + pause.durationNanos / NANOS_PER_MICRO;
      const placed = placeInterval(pause.startEpochMicros, to, this.view, plot);
      if (!placed) {
        continue;
      }
      const color = pauseColor(pause.category);
      this.layer.add(
        new Konva.Rect({
          x: M.gutterWidth + placed.x,
          y: M.rulerHeight,
          width: placed.width,
          height: height - M.rulerHeight,
          fill: color,
          opacity: 0.1
        })
      );
    }
  }

  private drawRows(
    width: number,
    plot: number,
    colors: { gridColor: string; mutedColor: string; inkColor: string; sunkenColor: string }
  ): void {
    for (const row of this.rows) {
      if (row.kind === 'group') {
        this.layer.add(
          new Konva.Rect({
            x: 0,
            y: row.y,
            width,
            height: row.height,
            fill: colors.sunkenColor
          })
        );
        this.layer.add(
          new Konva.Text({
            x: 8,
            y: row.y + 4,
            text: `▾ ${row.label}`,
            fontSize: 10,
            fontStyle: '600',
            fill: colors.inkColor
          })
        );
        continue;
      }

      this.layer.add(
        new Konva.Text({
          x: 0,
          y: row.y + 4,
          width: M.gutterWidth - 8,
          align: 'right',
          text: row.label,
          fontSize: 10,
          fill: colors.mutedColor,
          ellipsis: true,
          wrap: 'none'
        })
      );

      if (row.kind === 'global') {
        this.drawGlobalRow(row, plot);
      } else if (row.track) {
        this.drawThreadRow(row, row.track, plot);
      }
    }
  }

  private drawGlobalRow(row: Row, plot: number): void {
    for (const pause of this.data?.pauses ?? []) {
      if (pauseLabel(pause.category) !== row.label) {
        continue;
      }
      const to = pause.startEpochMicros + pause.durationNanos / NANOS_PER_MICRO;
      const placed = placeInterval(pause.startEpochMicros, to, this.view, plot);
      if (!placed) {
        continue;
      }
      this.layer.add(
        new Konva.Rect({
          x: M.gutterWidth + placed.x,
          y: row.y + 4,
          width: placed.width,
          height: row.height - 8,
          fill: pauseColor(pause.category),
          cornerRadius: 1
        })
      );
    }
  }

  private drawThreadRow(row: Row, track: TimelineTrack, plot: number): void {
    const errorColor = resolveToken('--color-danger', '#e63757');
    const labelColor = resolveToken('--color-dark', '#0b1727');

    for (const span of track.spans) {
      const to = span.startEpochMicros + span.durationNanos / NANOS_PER_MICRO;
      const placed = placeInterval(span.startEpochMicros, to, this.view, plot);
      if (!placed) {
        continue;
      }
      const y = row.y + M.threadRowHeight + span.depth * (M.spanLaneHeight + M.laneGap);
      this.layer.add(
        new Konva.Rect({
          x: M.gutterWidth + placed.x,
          y,
          width: placed.width,
          height: M.spanLaneHeight,
          fill: span.status === 'ERROR' ? errorColor : spanKindColor(span.kind),
          cornerRadius: 1
        })
      );

      if (placed.width > MIN_LABEL_WIDTH_PX) {
        this.layer.add(
          new Konva.Text({
            x: M.gutterWidth + placed.x + 3,
            y: y + 1,
            width: placed.width - 6,
            text: span.name,
            fontSize: 8,
            fill: labelColor,
            ellipsis: true,
            wrap: 'none'
          })
        );
      }
    }
  }

  private drawRuler(
    width: number,
    plot: number,
    ticks: number[],
    colors: { borderColor: string; mutedColor: string }
  ): void {
    this.layer.add(
      new Konva.Rect({
        x: 0,
        y: 0,
        width,
        height: M.rulerHeight,
        fill: resolveToken('--color-bg-card', '#ffffff')
      })
    );
    this.layer.add(
      new Konva.Line({
        points: [0, M.rulerHeight + 0.5, width, M.rulerHeight + 0.5],
        stroke: colors.borderColor,
        strokeWidth: 1
      })
    );

    // Labelled from the start of the recording rather than as epoch values: an absolute microsecond
    // count means nothing to a reader, and offsets are what the rest of the product shows.
    const origin = this.options.bounds.from;
    for (const tick of ticks) {
      this.layer.add(
        new Konva.Text({
          x: M.gutterWidth + xOf(tick, this.view, plot) + 4,
          y: M.rulerHeight / 2 - 5,
          text: FormattingService.formatDuration2Units((tick - origin) * NANOS_PER_MICRO),
          fontSize: 10,
          fill: colors.mutedColor
        })
      );
    }
  }

  /** Which slice of the recording is on screen, so zooming deep does not lose the reader. */
  private drawMinimap(height: number, plot: number): void {
    const span = this.options.bounds.to - this.options.bounds.from;
    if (span <= 0) {
      return;
    }
    const y = height - M.minimapHeight;
    this.layer.add(
      new Konva.Rect({
        x: M.gutterWidth,
        y,
        width: plot,
        height: M.minimapHeight,
        fill: resolveToken('--color-lighter', '#edf2f9')
      })
    );

    const from = ((this.view.from - this.options.bounds.from) / span) * plot;
    const to = ((this.view.to - this.options.bounds.from) / span) * plot;
    this.layer.add(
      new Konva.Rect({
        x: M.gutterWidth + from,
        y,
        width: Math.max(3, to - from),
        height: M.minimapHeight,
        fill: resolveToken('--color-primary', '#5e64ff')
      })
    );
  }
}

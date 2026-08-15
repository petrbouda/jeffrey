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
  invalidatePalette,
  pauseColor,
  pauseLabel,
  resolveToken,
  spanKindColor
} from '@/services/timeline/timelineTheme';
import {
  clampViewport,
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
/** Extra rows of forgiveness above the minimap strip, which is only a few pixels tall. */
const MINIMAP_HIT_SLOP_PX = 6;
/** How much of the window one arrow press pans — a step, visibly a move, far from a jump. */
const KEY_PAN_FRACTION = 0.1;
/** One +/− press zooms by this factor, roughly three wheel notches. */
const KEY_ZOOM_FACTOR = 0.8;
/** One ↑/↓ press scrolls this many pixels of rows; PageUp/Down uses the stage height instead. */
const KEY_SCROLL_PX = 48;

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
  | { type: 'pause'; category: string; label: string; fromMicros: number; durationNanos: number }
  | { type: 'state'; category: string; durationNanos: number };

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
  /** The cursor crosshair, on its own layer so following the pointer never rebuilds the scene. */
  private readonly crosshairLayer: Konva.Layer;
  private readonly tooltip: HTMLDivElement;
  private readonly host: HTMLDivElement;
  private readonly options: TimelineCanvasOptions;
  private readonly resizeObserver: ResizeObserver;
  private readonly themeObserver: MutationObserver;
  private readonly schemeQuery: MediaQueryList | null;

  private view: Viewport;
  private data: TimelineWindow | null = null;
  private rows: Row[] = [];
  /** Bottom edge of the last row in content space, for the vertical scroll range. */
  private contentBottom = 0;
  /** How far the rows are scrolled up. The ruler and the minimap stay pinned. */
  private scrollY = 0;
  /** Pool groups folded away by clicking their header. */
  private readonly collapsedGroups = new Set<string>();

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
  /** A drag that started on the minimap scrubs the viewport instead of panning it. */
  private minimapScrubbing = false;

  constructor(host: HTMLDivElement, options: TimelineCanvasOptions) {
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
    this.crosshairLayer = new Konva.Layer({ listening: false });
    this.stage.add(this.crosshairLayer);

    this.tooltip = document.createElement('div');
    this.tooltip.className = 'tl-tooltip';
    this.tooltip.style.display = 'none';
    document.body.appendChild(this.tooltip);

    this.bindEvents();

    this.resizeObserver = new ResizeObserver(() => this.handleResize());
    this.resizeObserver.observe(host);

    // The palette is cached; both ways the theme can change — the explicit toggle stamping
    // data-theme and the OS scheme flipping under the default — must drop it and repaint.
    this.themeObserver = new MutationObserver(() => this.handleThemeChange());
    this.themeObserver.observe(document.documentElement, {
      attributes: true,
      attributeFilter: ['data-theme']
    });
    this.schemeQuery = window.matchMedia ? window.matchMedia('(prefers-color-scheme: dark)') : null;
    this.schemeQuery?.addEventListener('change', this.handleThemeChange);

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

  /** Halves the window around its centre — the remedy the capped-window notice offers. */
  zoomIn(): void {
    const centre = (this.view.from + this.view.to) / 2;
    const quarter = (this.view.to - this.view.from) / 4;
    this.view = clampViewport(
      { from: centre - quarter, to: centre + quarter },
      this.options.bounds
    );
    this.draw();
    this.options.onViewportChanged(this.view);
  }

  destroy(): void {
    this.resizeObserver.disconnect();
    this.themeObserver.disconnect();
    this.schemeQuery?.removeEventListener('change', this.handleThemeChange);
    this.stage.destroy();
    this.tooltip.remove();
  }

  private readonly handleThemeChange = (): void => {
    invalidatePalette();
    this.draw();
  };

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

    // The canvas is an interactive surface, so it must be reachable and drivable without a
    // pointer — before this, the whole view was invisible to the keyboard.
    container.tabIndex = 0;
    container.setAttribute('role', 'application');
    container.setAttribute(
      'aria-label',
      'Unified timeline. Arrow keys pan and scroll, plus and minus zoom, Home fits the whole recording.'
    );
    container.addEventListener('keydown', this.onKeyDown);
  }

  private readonly onKeyDown = (event: KeyboardEvent): void => {
    const plot = this.plotWidth();
    const windowMicros = this.view.to - this.view.from;
    switch (event.key) {
      case 'ArrowLeft':
      case 'ArrowRight': {
        const direction = event.key === 'ArrowLeft' ? 1 : -1;
        const pixels = direction * plot * KEY_PAN_FRACTION;
        this.view = panByPixels(this.view, pixels, plot, this.options.bounds);
        break;
      }
      case '+':
      case '=':
      case '-': {
        const factor = event.key === '-' ? 1 / KEY_ZOOM_FACTOR : KEY_ZOOM_FACTOR;
        const centre = this.view.from + windowMicros / 2;
        this.view = zoomAt(this.view, centre, factor, this.options.bounds);
        break;
      }
      case 'ArrowUp':
      case 'ArrowDown': {
        const direction = event.key === 'ArrowUp' ? -1 : 1;
        this.setScroll(this.scrollY + direction * KEY_SCROLL_PX);
        event.preventDefault();
        return;
      }
      case 'PageUp':
      case 'PageDown': {
        const direction = event.key === 'PageUp' ? -1 : 1;
        this.setScroll(this.scrollY + direction * this.stage.height());
        event.preventDefault();
        return;
      }
      case 'Home': {
        this.view = { ...this.options.bounds };
        break;
      }
      default: {
        return;
      }
    }
    event.preventDefault();
    this.draw();
    this.options.onViewportChanged(this.view);
  };

  /**
   * Perfetto's wheel contract, because it is the one deep-timeline readers already know: a bare
   * wheel scrolls the rows, a horizontal wheel (trackpad) pans time, and zoom asks for Ctrl (or ⌘)
   * so that reading down a 40-thread stage does not fling the time axis around.
   */
  private readonly onWheel = (event: WheelEvent): void => {
    event.preventDefault();
    if (event.ctrlKey || event.metaKey) {
      const x = this.localX(event);
      if (x < M.gutterWidth) {
        return;
      }
      const anchor = microsAt(x - M.gutterWidth, this.view, this.plotWidth());
      const factor = Math.exp(event.deltaY * ZOOM_SENSITIVITY);
      this.view = zoomAt(this.view, anchor, factor, this.options.bounds);
      this.draw();
      this.options.onViewportChanged(this.view);
      return;
    }
    if (Math.abs(event.deltaX) > Math.abs(event.deltaY)) {
      this.view = panByPixels(this.view, -event.deltaX, this.plotWidth(), this.options.bounds);
      this.draw();
      this.options.onViewportChanged(this.view);
      return;
    }
    this.setScroll(this.scrollY + event.deltaY);
  };

  private setScroll(next: number): void {
    const clamped = Math.min(this.maxScroll(), Math.max(0, next));
    if (clamped !== this.scrollY) {
      this.scrollY = clamped;
      this.draw();
    }
  }

  private maxScroll(): number {
    const visible = this.stage.height() - M.minimapHeight;
    return Math.max(0, this.contentBottom - visible);
  }

  private inMinimap(y: number): boolean {
    return y >= this.stage.height() - M.minimapHeight - MINIMAP_HIT_SLOP_PX;
  }

  /** Jumps the viewport so the instant under the minimap pointer is centred, width kept. */
  private scrubMinimap(x: number): void {
    const span = this.options.bounds.to - this.options.bounds.from;
    if (span <= 0) {
      return;
    }
    const fraction = Math.min(1, Math.max(0, (x - M.gutterWidth) / this.plotWidth()));
    const centre = this.options.bounds.from + fraction * span;
    const width = this.view.to - this.view.from;
    this.view = clampViewport(
      { from: centre - width / 2, to: centre + width / 2 },
      this.options.bounds
    );
    this.draw();
    this.options.onViewportChanged(this.view);
  }

  private readonly onPointerDown = (event: PointerEvent): void => {
    if (this.inMinimap(this.localY(event))) {
      this.minimapScrubbing = true;
      this.stage.container().setPointerCapture(event.pointerId);
      this.scrubMinimap(this.localX(event));
      return;
    }
    this.dragging = true;
    this.dragMoved = false;
    this.dragOriginX = event.clientX;
    this.dragStartX = event.clientX;
    this.stage.container().style.cursor = 'grabbing';
    this.stage.container().setPointerCapture(event.pointerId);
  };

  private readonly onPointerMove = (event: PointerEvent): void => {
    if (this.minimapScrubbing) {
      this.scrubMinimap(this.localX(event));
      return;
    }
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
    this.drawCrosshair(this.localX(event));
    this.showTooltip(event);
  };

  private readonly onPointerUp = (event: PointerEvent): void => {
    const wasDrag = this.dragMoved;
    const wasScrub = this.minimapScrubbing;
    this.dragging = false;
    this.minimapScrubbing = false;
    this.stage.container().style.cursor = 'grab';
    this.stage.container().releasePointerCapture(event.pointerId);
    if (wasDrag || wasScrub) {
      return;
    }
    const x = this.localX(event);
    const y = this.localY(event);
    // A click on a pool header folds the pool; unlike a span, the whole row is the target.
    const groupRow = this.groupRowAt(y);
    if (groupRow) {
      this.toggleGroup(groupRow.label);
      return;
    }
    const hit = this.hitTest(x, y);
    if (hit?.type === 'span') {
      this.options.onSpanSelected(hit.span.traceId, hit.span.name);
    }
  };

  private groupRowAt(y: number): Row | null {
    if (y < M.rulerHeight || this.inMinimap(y)) {
      return null;
    }
    const contentY = y + this.scrollY;
    return (
      this.rows.find(
        row => row.kind === 'group' && contentY >= row.y && contentY <= row.y + row.height
      ) ?? null
    );
  }

  private toggleGroup(group: string): void {
    if (!this.collapsedGroups.delete(group)) {
      this.collapsedGroups.add(group);
    }
    this.draw();
  }

  private readonly onPointerLeave = (): void => {
    this.tooltip.style.display = 'none';
    this.crosshairLayer.destroyChildren();
    this.crosshairLayer.batchDraw();
    // An interrupted drag (alt-tab, OS gesture) must not leave the canvas stuck in grabbing.
    this.dragging = false;
    this.minimapScrubbing = false;
    this.stage.container().style.cursor = 'grab';
  };

  private readonly onPointerCancel = (): void => {
    this.dragging = false;
    this.minimapScrubbing = false;
    this.stage.container().style.cursor = 'grab';
    this.tooltip.style.display = 'none';
    this.crosshairLayer.destroyChildren();
    this.crosshairLayer.batchDraw();
  };

  /**
   * The vertical hairline under the cursor with its instant in the ruler — the affordance behind
   * "read straight down": it makes 'this pause and that span overlap' checkable without guessing
   * across 400 vertical pixels. Its own layer, so tracking the pointer costs two nodes, not a
   * scene rebuild.
   */
  private drawCrosshair(x: number): void {
    this.crosshairLayer.destroyChildren();
    if (x >= M.gutterWidth) {
      const micros = microsAt(x - M.gutterWidth, this.view, this.plotWidth());
      const color = resolveToken('--color-text-muted', '#748194');
      this.crosshairLayer.add(
        new Konva.Line({
          points: [x + 0.5, M.rulerHeight, x + 0.5, this.stage.height() - M.minimapHeight],
          stroke: color,
          strokeWidth: 1,
          dash: [3, 3],
          opacity: 0.7
        })
      );
      const label = new Konva.Text({
        y: 2,
        text: FormattingService.formatDuration2Units(
          (micros - this.options.bounds.from) * NANOS_PER_MICRO
        ),
        fontSize: 9,
        fill: color
      });
      // Kept inside the stage even at the right edge, where the reader is most often aiming.
      label.x(Math.min(x + 4, this.stage.width() - label.width() - 2));
      this.crosshairLayer.add(label);
    }
    this.crosshairLayer.batchDraw();
  }

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
    if (hit.type === 'state') {
      return [
        line('b', pauseLabel(hit.category)),
        line('span', FormattingService.formatDuration2Units(hit.durationNanos)),
        line('span', 'what this thread was waiting on')
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
    // The ruler and the minimap are pinned chrome; only the scrolled band underneath holds rows.
    if (y < M.rulerHeight || this.inMinimap(y)) {
      return null;
    }
    const plotX = x - M.gutterWidth;
    const contentY = y + this.scrollY;

    for (const row of this.rows) {
      if (contentY < row.y || contentY > row.y + row.height) {
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
      const lane = Math.floor(
        (contentY - row.y - M.threadRowHeight) / (M.spanLaneHeight + M.laneGap)
      );
      if (lane < 0) {
        // The header strip, where the state underlay lives: a wait is hoverable there.
        const state = row.track.states?.find(s =>
          this.hitsInterval(
            plotX,
            s.startEpochMicros,
            s.startEpochMicros + s.durationNanos / NANOS_PER_MICRO
          )
        );
        return state
          ? { type: 'state', category: state.category, durationNanos: state.durationNanos }
          : null;
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
      if (this.collapsedGroups.has(group)) {
        continue;
      }
      for (const track of tracks) {
        const height = M.threadRowHeight + track.laneCount * (M.spanLaneHeight + M.laneGap);
        rows.push({ kind: 'thread', y, height, track, label: track.threadName ?? 'unknown' });
        y += height + M.trackGap;
      }
    }
    this.contentBottom = y;
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
    // A shrunk stage or a collapsed pool must not leave the rows scrolled past their own end.
    this.scrollY = Math.min(this.scrollY, this.maxScroll());

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

    /*
     * Rows live in a shifted, clipped group: the group scrolls them under a pinned ruler and
     * minimap, and the clip stops a scrolled row drawing over either. Everything outside the group
     * is pinned chrome.
     */
    const scrolled = new Konva.Group({
      y: -this.scrollY,
      clip: {
        x: 0,
        y: M.rulerHeight + this.scrollY,
        width,
        height: height - M.rulerHeight - M.minimapHeight
      }
    });
    this.drawRows(scrolled, width, plot, { gridColor, mutedColor, inkColor, sunkenColor });
    this.layer.add(scrolled);

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
    target: Konva.Group,
    width: number,
    plot: number,
    colors: { gridColor: string; mutedColor: string; inkColor: string; sunkenColor: string }
  ): void {
    const visibleTop = this.scrollY;
    const visibleBottom = this.scrollY + this.stage.height();
    for (const row of this.rows) {
      // Rows scrolled out of the band would be clipped anyway; not building them is what keeps a
      // 40-thread stage cheap to redraw.
      if (row.y + row.height < visibleTop || row.y > visibleBottom) {
        continue;
      }
      if (row.kind === 'group') {
        target.add(
          new Konva.Rect({
            x: 0,
            y: row.y,
            width,
            height: row.height,
            fill: colors.sunkenColor
          })
        );
        const glyph = this.collapsedGroups.has(row.label) ? '▸' : '▾';
        target.add(
          new Konva.Text({
            x: 8,
            y: row.y + 4,
            text: `${glyph} ${row.label}`,
            fontSize: 10,
            fontStyle: '600',
            fill: colors.inkColor
          })
        );
        continue;
      }

      target.add(
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
        this.drawGlobalRow(target, row, plot);
      } else if (row.track) {
        this.drawThreadRow(target, row, row.track, plot);
      }
    }
  }

  private drawGlobalRow(target: Konva.Group, row: Row, plot: number): void {
    for (const pause of this.data?.pauses ?? []) {
      if (pauseLabel(pause.category) !== row.label) {
        continue;
      }
      const to = pause.startEpochMicros + pause.durationNanos / NANOS_PER_MICRO;
      const placed = placeInterval(pause.startEpochMicros, to, this.view, plot);
      if (!placed) {
        continue;
      }
      target.add(
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

  private drawThreadRow(target: Konva.Group, row: Row, track: TimelineTrack, plot: number): void {
    const errorColor = resolveToken('--color-danger', '#e63757');
    const labelColor = resolveToken('--color-dark', '#0b1727');

    /*
     * The state underlay, in the header strip above the span lanes: what the thread was waiting on,
     * in the same category colours the waterfall's stripes use. This is what tells a blank gap with
     * a park under it apart from a blank gap with nothing — two different diagnoses that used to be
     * one absence of pixels.
     */
    for (const state of track.states ?? []) {
      const stateTo = state.startEpochMicros + state.durationNanos / NANOS_PER_MICRO;
      const statePlaced = placeInterval(state.startEpochMicros, stateTo, this.view, plot);
      if (!statePlaced) {
        continue;
      }
      target.add(
        new Konva.Rect({
          x: M.gutterWidth + statePlaced.x,
          y: row.y + 2,
          width: statePlaced.width,
          height: M.threadRowHeight - 4,
          fill: pauseColor(state.category),
          opacity: 0.35,
          cornerRadius: 1
        })
      );
    }

    for (const span of track.spans) {
      const to = span.startEpochMicros + span.durationNanos / NANOS_PER_MICRO;
      const placed = placeInterval(span.startEpochMicros, to, this.view, plot);
      if (!placed) {
        continue;
      }
      const y = row.y + M.threadRowHeight + span.depth * (M.spanLaneHeight + M.laneGap);
      target.add(
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
        target.add(
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

/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import Utils from '@/services/Utils';
import Tooltip from '@/services/tooltip/Tooltip';
import FlamegraphTooltip from '@/services/flamegraphs/tooltips/FlamegraphTooltip';
import FlamegraphData from '@/services/api/model/FlamegraphData';
import Frame from '@/services/api/model/Frame';
import FrameRef from '@/services/flamegraphs/FrameRef';
import FrameRect from '@/services/flamegraphs/FrameRect';
import CanvasBand, { type GraphViewport } from '@/services/flamegraphs/CanvasBand';
import VisibleFrame from '@/services/flamegraphs/VisibleFrame';
import FrameColorResolver from '@/services/flamegraphs/FrameColorResolver';
import FrameType from '@/services/flamegraphs/FrameType';
import FrameSearchPattern from '@/services/flamegraphs/FrameSearchPattern';
import {
  FrameLabels,
  type FrameTextRenderer,
  SingleLineFrameTextRenderer,
  TwoLineFrameTextRenderer
} from '@/services/flamegraphs/FrameTextRenderer';

export default class Flamegraph {
  static HIGHLIGHTED_COLOR = '#f0a0f0';

  private static readonly SINGLE_LINE_RENDERER = new SingleLineFrameTextRenderer();
  private static readonly TWO_LINE_RENDERER = new TwoLineFrameTextRenderer();

  private static readonly BACKGROUND_COLOR = '#ffffff';
  private static readonly UNDER_ROOT_VEIL = 'rgba(255, 255, 255, 0.5)';
  // The white gap between neighbouring frames and rows. A frame no wider than the gap shows no
  // colour at all, so it is not drawn, and neither is its subtree, which is narrower still.
  private static readonly FRAME_GAP_PX = 1;

  // set up by draw function

  private textRenderer: FrameTextRenderer = Flamegraph.SINGLE_LINE_RENDERER;
  private canvasWidth: number = 0;
  private pxPerSample: number | null = null;
  // The rows the canvas holds; the canvas sits at its top inside the container, which is as tall as
  // the whole graph
  private band: CanvasBand = CanvasBand.EMPTY;
  private readonly labels = new FrameLabels();

  // The frame under the pointer, and the tooltip made for it
  private hlFrame: FrameRef | null = null;
  private hlTooltip: string = '';
  private contextFrame: FrameRef | null = null;

  private currentRoot: FrameRef = FrameRef.ROOT;
  // Per title of the pool, 1 when it matches the search; null without a search
  private matchedTitles: Uint8Array | null = null;
  private visibleFrames: VisibleFrame[][];

  private readonly depth: number;

  private readonly data: FlamegraphData;
  // Per level, the left edges and widths the graph is laid out by: samples, or weight
  private readonly lefts: Float64Array[];
  private readonly totals: Float64Array[];
  private readonly useWeight: boolean = false;
  private readonly skipRootInCanvas: boolean = false;
  private readonly contextMenu: any;
  private readonly hl: HTMLElement;

  private readonly flamegraphTooltip: FlamegraphTooltip;
  private readonly tooltip: Tooltip;
  private readonly canvas: HTMLCanvasElement;
  private readonly container: HTMLElement;
  private readonly context: CanvasRenderingContext2D;

  // Any scroll on the page and any resize of the window may move other rows into view
  private viewportRafId: number | null = null;
  private readonly viewportListener = () => this.scheduleViewportCheck();

  // The element whose scrolling dismisses the tooltip, and the listener this graph put on it
  private scrollContainer: HTMLElement | null = null;
  private readonly scrollListener = () => this.onScroll();

  // Throttling state for mouse move events
  private mouseMoveRafId: number | null = null;
  private pendingMouseEvent: MouseEvent | null = null;

  /**
   * The canvas must sit in a positioned container of its own (FlamegraphComponent's wrapper): the
   * container takes the height of the whole graph, the canvas only the band of rows around the
   * window (see {@link CanvasBand}), and the highlight and the tooltip are placed into it.
   * {@link close} takes them out again, so the next graph drawn on the same canvas starts from the
   * same DOM.
   */
  constructor(
    data: FlamegraphData,
    canvas: HTMLCanvasElement,
    flamegraphTooltip: FlamegraphTooltip,
    contextMenu: any,
    useWeight: boolean,
    skipRootInCanvas: boolean = false
  ) {
    this.depth = data.depth;
    this.data = data;
    this.skipRootInCanvas = skipRootInCanvas;
    this.useWeight = Utils.parseBoolean(useWeight);
    this.lefts = data.levels.map(level => (this.useWeight ? level.leftWeight : level.leftSamples));
    this.totals = data.levels.map(level =>
      this.useWeight ? level.totalWeight : level.totalSamples
    );

    this.contextMenu = contextMenu;

    this.canvas = canvas;
    this.canvas.style.position = 'absolute';
    this.canvas.style.left = '0';
    this.context = this.canvas.getContext('2d')!;

    const container = this.canvas.parentElement;
    if (container == null) {
      throw new Error('Flamegraph: the canvas must sit in a container');
    }
    this.container = container;
    this.hl = Flamegraph.createHighlightDiv(container);

    this.tooltip = new Tooltip(this.canvas);
    this.flamegraphTooltip = flamegraphTooltip;

    this.visibleFrames = Flamegraph.initializeLevels(this.depth);
    this.resizeCanvas(this.canvas.offsetWidth);

    // Capturing: scroll events do not bubble, and the element that scrolls differs per page
    document.addEventListener('scroll', this.viewportListener, { capture: true, passive: true });
    window.addEventListener('resize', this.viewportListener, { passive: true });

    this.canvas.oncontextmenu = this.onContextMenu();
    this.canvas.onmousemove = this.onMouseMoveEvent();
    this.canvas.onmouseout = this.onMouseOut();
    this.canvas.ondblclick = this.onDoubleClick();
  }

  private onContextMenu() {
    return (event: MouseEvent) => {
      // Prevent browser's default context menu
      event.preventDefault();

      // Show our custom context menu
      this.contextMenu.show(event);
      this.contextFrame = this.hlFrame;
    };
  }

  private onMouseMoveEvent() {
    // Use requestAnimationFrame to throttle mouse move events to ~60fps
    return (event: MouseEvent) => {
      this.pendingMouseEvent = event;

      if (this.mouseMoveRafId === null) {
        this.mouseMoveRafId = requestAnimationFrame(() => {
          this.mouseMoveRafId = null;
          if (this.pendingMouseEvent) {
            this.processMouseMove(this.pendingMouseEvent);
          }
        });
      }
    };
  }

  private processMouseMove(event: MouseEvent) {
    // The canvas holds only the band, so the pointer is offset by where the band starts
    const graphY = this.band.top + event.offsetY;
    const level = Math.floor(graphY / this.textRenderer.frameHeight) + this.firstDrawnLevel();

    if (level >= this.firstDrawnLevel() && level < this.data.levels.length) {
      const frame = this.lookupFrame(level, event);
      if (frame === null || !frame.equals(this.hlFrame)) {
        this.clearHighlightSelection();
        // Only the frame a tooltip shows is built as an object, once while the pointer is on it
        this.hlTooltip = frame !== null ? this.generateTooltip(frame) : '';
      }
      this.hlFrame = frame;

      if (frame) {
        // if `contextFrame` != null, then context menu is selected.
        if (this.contextFrame == null) {
          this.showHighlight(frame);
        }

        this.tooltip.showTooltip(event, this.hlTooltip);

        this.canvas.style.cursor = 'pointer';
        this.canvas.onclick = () => {
          if (!frame.equals(this.currentRoot)) {
            this.draw(frame);
          }
        };
        return;
      }
    }

    // No frame found at cursor position — clean up previous highlight and tooltip
    this.removeHighlight();
    this.tooltip.hideTooltip();
  }

  private generateTooltip(frame: FrameRef): string {
    const root = this.data.levels[0];
    return this.flamegraphTooltip.generate(
      this.data.frame(frame.level, frame.index),
      root.totalSamples[0],
      root.totalWeight[0]
    );
  }

  closeContextMenu() {
    this.contextFrame = null;
  }

  /** The frame the context menu was opened on, null without one. */
  getContextFrame(): Frame | null {
    return this.contextFrame !== null
      ? this.data.frame(this.contextFrame.level, this.contextFrame.index)
      : null;
  }

  setTwoLineMode(enabled: boolean) {
    this.textRenderer = enabled ? Flamegraph.TWO_LINE_RENDERER : Flamegraph.SINGLE_LINE_RENDERER;
    this.resizeCanvas(this.canvasWidth);
  }

  // Number of frame rows actually rendered in the canvas. When `skipRootInCanvas`
  // is true, level 0 lives in the HTML root header above the canvas, so the canvas
  // is one row shorter than `this.depth`.
  private canvasRows(): number {
    return this.depth - this.firstDrawnLevel();
  }

  // First data level that the canvas draws. 0 normally; 1 when the root has been
  // pulled into an HTML header.
  private firstDrawnLevel(): number {
    return this.skipRootInCanvas ? 1 : 0;
  }

  /**
   * Follows the scrolling of the element the graph sits in, until {@link close}. Replaces an element
   * followed before.
   */
  followScrollOf(container: HTMLElement) {
    this.scrollContainer?.removeEventListener('scroll', this.scrollListener);
    this.scrollContainer = container;
    container.addEventListener('scroll', this.scrollListener);
  }

  /**
   * The graph's container scrolled. Anything anchored to a frame's old position — the tooltip, the
   * context menu, the highlight — is dismissed rather than dragged along.
   */
  private onScroll() {
    this.tooltip.hideTooltip();
    this.closeContextMenu();
    this.contextMenu.hide();
    this.removeHighlight();
  }

  removeHighlight() {
    // Don't remove highlighting if context menu is active
    if (this.contextFrame == null) {
      this.hl.style.display = 'none';
      this.canvas.title = '';
      this.canvas.style.cursor = '';
      this.canvas.onclick = null;
    }
  }

  private onMouseOut() {
    return () => {
      this.removeHighlight();
      this.tooltip.hideTooltip();
    };
  }

  /**
   * A double click selects the frame's title, ready to be copied. Its two clicks have zoomed into the
   * frame already, and that redraw hid the highlight, so the highlight is shown again first.
   */
  private onDoubleClick() {
    return () => {
      if (this.hlFrame !== null) {
        this.showHighlight(this.hlFrame);
        getSelection()?.selectAllChildren(this.hl);
      }
    };
  }

  /**
   * Lays the highlight over the frame. It carries the frame's title in transparent text: the canvas
   * draws the visible label, the text is there for a double click to select.
   */
  private showHighlight(frame: FrameRef) {
    const root = this.currentRoot;
    this.hl.style.left =
      Math.max(this.leftOf(frame) - this.leftOf(root), 0) * this.pxPerSample! + 'px';
    this.hl.style.width =
      Math.min(this.totalOf(frame), this.totalOf(root)) * this.pxPerSample! + 'px';
    this.hl.style.top =
      (frame.level - this.firstDrawnLevel()) * this.textRenderer.frameHeight + 'px';
    this.hl.style.height = this.textRenderer.frameHeight + 'px';
    this.hl.style.lineHeight = this.textRenderer.frameHeight + 'px';
    this.hl.firstChild!.textContent = this.data.title(frame.level, frame.index);
    this.hl.style.display = 'block';
  }

  /**
   * Drops the selection a double click made on the highlight, once the pointer moves on to another
   * frame. A selection anywhere else on the page is left alone.
   */
  private clearHighlightSelection() {
    const selection = getSelection();
    if (selection?.anchorNode != null && this.hl.contains(selection.anchorNode)) {
      selection.removeAllRanges();
    }
  }

  private static createHighlightDiv(container: HTMLElement): HTMLElement {
    container.insertAdjacentHTML(
      'beforeend',
      '<div style="' +
        ' position: absolute;' +
        ' display: none;' +
        ' overflow: hidden;' +
        ' white-space: nowrap;' +
        ' pointer-events: none;' +
        ' background-color: rgba(255,255,255,0.4);' +
        ' font: 12px Arial;' +
        ' height: 20px;"><span style="padding: 0 3px; color: transparent"></span></div>'
    );
    return container.lastElementChild as HTMLElement;
  }

  private static pct(a: number, b: number) {
    return a >= b ? '100' : ((100 * a) / b).toFixed(2);
  }

  /**
   * Binary search for frame lookup - O(log n) instead of O(n)
   * Frames are sorted by x position (leftSamples/leftWeight)
   */
  private lookupFrame(level: number, event: MouseEvent): FrameRef | null {
    const frames = this.visibleFrames[level];
    if (!frames || frames.length === 0) {
      return null;
    }

    const x = event.offsetX;
    let left = 0;
    let right = frames.length - 1;

    while (left <= right) {
      const mid = (left + right) >>> 1;
      const rect = frames[mid].rect;

      if (x < rect.x) {
        right = mid - 1;
      } else if (x >= rect.x + rect.width) {
        left = mid + 1;
      } else {
        // Found the frame containing x
        return new FrameRef(level, frames[mid].index);
      }
    }
    return null;
  }

  resizeWidthCanvas(width: number) {
    this.resizeCanvas(width);
  }

  /**
   * Lays the graph out at the given width: the container takes the height of every row, and the
   * canvas is sized to the band of rows around the window and redrawn.
   */
  private resizeCanvas(width: number) {
    this.canvasWidth = width;
    this.canvas.style.width = width + 'px';
    this.container.style.height = this.graphHeight() + 'px';
    this.applyBand(CanvasBand.around(this.viewport()), true);
  }

  private graphHeight(): number {
    return this.canvasRows() * this.textRenderer.frameHeight;
  }

  private viewport(): GraphViewport {
    return {
      graphHeight: this.graphHeight(),
      rowHeight: this.textRenderer.frameHeight,
      graphTop: this.container.getBoundingClientRect().top,
      windowHeight: window.innerHeight
    };
  }

  private scheduleViewportCheck() {
    if (this.viewportRafId === null) {
      this.viewportRafId = requestAnimationFrame(() => {
        this.viewportRafId = null;
        this.checkViewport();
      });
    }
  }

  /** Redraws around the window once rows outside the drawn band have scrolled into view. */
  private checkViewport() {
    const viewport = this.viewport();
    if (!this.band.covers(viewport)) {
      this.applyBand(CanvasBand.around(viewport), false);
    }
  }

  /**
   * Moves the canvas to the band and redraws it. The backing store is reallocated only when its size
   * changes; it is sized in device pixels, rounded, and scaled per axis by what the rounding left.
   */
  private applyBand(band: CanvasBand, resized: boolean) {
    if (resized || band.height !== this.band.height) {
      const ratio = window.devicePixelRatio || 1;
      this.canvas.width = Math.round(this.canvasWidth * ratio);
      this.canvas.height = Math.round(band.height * ratio);
      this.canvas.style.height = band.height + 'px';
      // Resizing the backing store resets the context, its transform included
      this.context.setTransform(
        this.canvasWidth > 0 ? this.canvas.width / this.canvasWidth : ratio,
        0,
        0,
        band.height > 0 ? this.canvas.height / band.height : ratio,
        0,
        0
      );
    }
    this.band = band;
    this.canvas.style.top = band.top + 'px';
    this.drawRoot();
  }

  static initializeLevels(depth: number) {
    const levels: VisibleFrame[][] = Array(depth + 1);
    for (let h = 0; h < levels.length; h++) {
      levels[h] = [];
    }
    return levels;
  }

  private clearCanvas() {
    this.removeHighlight();
    this.context.fillStyle = Flamegraph.BACKGROUND_COLOR;
    this.context.fillRect(0, 0, this.canvasWidth, this.band.height);
  }

  drawRoot() {
    this.draw(this.currentRoot);
  }

  /**
   * Highlights the frames whose title the pattern matches and returns their share of the current
   * root. Each title of the pool is tested once, not each frame.
   */
  search(pattern: string) {
    const searchPattern = new FrameSearchPattern(pattern);
    this.matchedTitles = Uint8Array.from(this.data.titles, title =>
      searchPattern.matches(title) ? 1 : 0
    );
    this.draw(this.currentRoot);
    const highlightedTotal = this.matchedValue(this.matchedTitles);
    return Flamegraph.pct(highlightedTotal, this.totalOf(this.currentRoot));
  }

  resetSearch() {
    this.matchedTitles = null;
    this.draw(this.currentRoot);
  }

  resetZoom() {
    this.draw(FrameRef.ROOT);
  }

  close() {
    if (this.mouseMoveRafId !== null) {
      cancelAnimationFrame(this.mouseMoveRafId);
      this.mouseMoveRafId = null;
    }
    this.pendingMouseEvent = null;

    this.canvas.oncontextmenu = null;
    this.canvas.onmousemove = null;
    this.canvas.onmouseout = null;
    this.canvas.ondblclick = null;
    this.canvas.onclick = null;

    this.scrollContainer?.removeEventListener('scroll', this.scrollListener);
    this.scrollContainer = null;

    document.removeEventListener('scroll', this.viewportListener, { capture: true });
    window.removeEventListener('resize', this.viewportListener);
    if (this.viewportRafId !== null) {
      cancelAnimationFrame(this.viewportRafId);
      this.viewportRafId = null;
    }

    this.hl.remove();
    this.tooltip.destroy();
    this.contextMenu.hide();
  }

  /**
   * Draws the rows of the band under the given root. Within a level the frames are ordered and do
   * not overlap, so the ones under the root are found by a binary search and the walk stops at the
   * first frame past it.
   */
  private draw(root: FrameRef) {
    this.clearCanvas();
    this.visibleFrames = Flamegraph.initializeLevels(this.depth);
    this.currentRoot = root;

    const xStart = this.leftOf(root);
    const xEnd = xStart + this.totalOf(root);
    const pxPerSample = this.canvasWidth / this.totalOf(root);
    this.pxPerSample = pxPerSample;

    // When `skipRootInCanvas` is true, level 0 is rendered in the HTML root header above the
    // canvas, so the rows start at level 1.
    const rowHeight = this.textRenderer.frameHeight;
    const firstLevel = this.firstDrawnLevel();
    const firstRow = this.band.top / rowHeight;
    const endRow = this.band.bottom / rowHeight;
    for (let row = firstRow; row < endRow; row++) {
      const level = row + firstLevel;
      if (level >= this.data.levels.length) {
        break;
      }
      const y = row * rowHeight - this.band.top;
      const lefts = this.lefts[level];
      const totals = this.totals[level];
      const isUnderRoot = level < root.level;

      for (let i = Flamegraph.firstEndingAfter(lefts, totals, xStart); i < lefts.length; i++) {
        const left = lefts[i];
        if (left >= xEnd) {
          break;
        }
        const width = totals[i] * pxPerSample;
        if (width <= Flamegraph.FRAME_GAP_PX) {
          continue;
        }
        const rect = new FrameRect((left - xStart) * pxPerSample, y, width, rowHeight);
        this.visibleFrames[level].push(new VisibleFrame(rect, i));
        this.drawFrame(level, i, rect, isUnderRoot);
      }
    }
  }

  /** Index of the first frame that ends after `x`; the frames are ordered by their left edge. */
  private static firstEndingAfter(lefts: Float64Array, totals: Float64Array, x: number): number {
    let low = 0;
    let high = lefts.length;
    while (low < high) {
      const mid = (low + high) >>> 1;
      if (lefts[mid] + totals[mid] > x) {
        high = mid;
      } else {
        low = mid + 1;
      }
    }
    return low;
  }

  /**
   * What the search matches under the current root, every row of it and not only the drawn band:
   * the frames that match, without counting again a match nested in another.
   */
  private matchedValue(matchedTitles: Uint8Array): number {
    const xStart = this.leftOf(this.currentRoot);
    const xEnd = xStart + this.totalOf(this.currentRoot);
    // left edge -> widest match starting there
    const highlighted = new Map<number, number>();
    for (let level = this.firstDrawnLevel(); level < this.data.levels.length; level++) {
      const lefts = this.lefts[level];
      const totals = this.totals[level];
      const titleIndex = this.data.levels[level].titleIndex;
      for (let i = Flamegraph.firstEndingAfter(lefts, totals, xStart); i < lefts.length; i++) {
        const left = lefts[i];
        if (left >= xEnd) {
          break;
        }
        if (matchedTitles[titleIndex[i]] === 1 && (highlighted.get(left) ?? -1) < totals[i]) {
          highlighted.set(left, totals[i]);
        }
      }
    }
    return Flamegraph.nonOverlappingTotal(highlighted);
  }

  private static nonOverlappingTotal(highlighted: Map<number, number>): number {
    let total = 0;
    let coveredUntil = 0;
    const lefts = [...highlighted.keys()].sort((a, b) => a - b);
    for (const left of lefts) {
      if (left >= coveredUntil) {
        const value = highlighted.get(left)!;
        total += value;
        coveredUntil = left + value;
      }
    }
    return total;
  }

  private drawFrame(level: number, index: number, rect: FrameRect, isUnderRoot: boolean) {
    const columns = this.data.levels[level];
    const titleIndex = columns.titleIndex[index];
    const type = this.data.type(level, index);
    const isHighlighted = this.matchedTitles !== null && this.matchedTitles[titleIndex] === 1;

    // The gap is left unpainted on the background instead of stroking every frame in white, which
    // looks the same: one fillRect per frame and no path to build
    const gap = Flamegraph.FRAME_GAP_PX;
    const x = rect.x + gap / 2;
    const y = rect.y + gap / 2;
    const width = rect.width - gap;
    const height = rect.height - gap;

    const fill = isHighlighted ? Flamegraph.HIGHLIGHTED_COLOR : this.color(level, index, type);
    this.context.fillStyle = fill;
    this.context.fillRect(x, y, width, height);

    const textX = Math.max(rect.x, 0) + 3;
    const prunedChildren = columns.prunedChildren[index];
    const label =
      type === FrameType.TRUNCATED_SYNTHETIC && prunedChildren
        ? `▾ ${prunedChildren} children pruned`
        : (this.data.titles[titleIndex] ?? '');
    const useLightText = !isHighlighted && FrameColorResolver.isDarkFill(fill);
    this.textRenderer.drawText(
      this.context,
      this.labels,
      label,
      type,
      textX,
      rect.y,
      rect.width,
      useLightText
    );

    if (isUnderRoot) {
      this.context.fillStyle = Flamegraph.UNDER_ROOT_VEIL;
      this.context.fillRect(x, y, width, height);
    }
  }

  private color(level: number, index: number, type: string): string {
    const columns = this.data.levels[level];
    // Differential flamegraph - compute color from diff details
    if (columns.hasDiff[index] === 1) {
      const totalValue = this.totals[level][index];
      const diffValue = this.useWeight ? columns.diffWeight[index] : columns.diffSamples[index];
      // Reconstruct primary and secondary from total (primary + secondary) and diff (primary - secondary)
      const primary = (totalValue + diffValue) / 2;
      const secondary = (totalValue - diffValue) / 2;
      return FrameColorResolver.resolveDiffColor(primary, secondary, type);
    }
    // Regular flamegraph - use frame type color
    return FrameColorResolver.resolveByType(type);
  }

  private leftOf(frame: FrameRef): number {
    return this.lefts[frame.level][frame.index];
  }

  private totalOf(frame: FrameRef): number {
    return this.totals[frame.level][frame.index];
  }
}

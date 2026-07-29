/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

import Tooltip from '../tooltip/Tooltip';
import ThreadGroups from '../thread/ThreadGroups';
import Konva from 'konva';
import ThreadTooltips from '../thread/ThreadTooltips';
import type { CategoryTooltip } from '../thread/ThreadTooltips';
import ThreadWindowDetails from '../thread/ThreadWindowDetails';
import ThreadRowData from '@/services/api/model/ThreadRowData';
import ThreadPeriod from '@/services/api/model/ThreadPeriod';
import ThreadTimeWindow from '@/services/api/model/ThreadTimeWindow';
import TooltipPosition from '@/services/tooltip/TooltipPosition';
import ThreadCommon from '@/services/api/model/ThreadCommon';
import ThreadMetadata from '@/services/api/model/ThreadMetadata';
import EventMetadata from '@/services/api/model/EventMetadata';
import type { ThreadEventState } from '@/services/api/model/ThreadEventDetail';
import Vector2d = Konva.Vector2d;

/**
 * One hoverable band category: where its periods live on a row, which colour draws it, which
 * metadata labels it, and which state the backend knows it by when its events are fetched.
 */
interface ThreadCategory {
  state: ThreadEventState;
  color: string;
  periods: (row: ThreadRowData) => ThreadPeriod[];
  metadata: (metadata: ThreadMetadata) => EventMetadata;
}

interface HoverableCategory extends ThreadCategory {
  groups: ThreadGroups;
}

/**
 * A category drawn at the hovered pixel, with the window to ask about it and the fallback window to
 * use if nothing turns out to start inside that one.
 */
interface HoveredCategory {
  category: HoverableCategory;
  window: ThreadTimeWindow;
  /** Start of the run of activity drawn here, for an event that began before the window. */
  runStart: ThreadTimeWindow;
}

export default class ThreadRow {
  static lifespanColor = 'rgb(96,175,96)';
  static parkedColor = 'rgb(198,193,193)';
  static blockedColor = 'rgb(236,204,116)';
  static waitingColor = 'rgb(134,173,225)';
  static sleepColor = 'rgb(65,126,228)';
  static socketReadColor = 'rgb(228,33,33)';
  static socketWriteColor = 'rgb(241,135,168)';
  static fileReadColor = 'rgb(215,33,228)';
  static fileWriteColor = 'rgb(210,132,236)';

  static readonly FRAME_HEIGHT: number = 20;

  /**
   * How long the pointer has to rest on a band before its events are fetched. Sweeping across a row
   * crosses dozens of bands, and none of them are the one being looked at.
   */
  private static readonly HOVER_SETTLE_MILLIS = 120;

  /**
   * Every band category, in the order they are drawn and listed in a tooltip.
   */
  private static readonly CATEGORIES: ThreadCategory[] = [
    {
      state: 'PARKED',
      color: ThreadRow.parkedColor,
      periods: row => row.parked,
      metadata: metadata => metadata.parked
    },
    {
      state: 'BLOCKED',
      color: ThreadRow.blockedColor,
      periods: row => row.blocked,
      metadata: metadata => metadata.blocked
    },
    {
      state: 'WAITING',
      color: ThreadRow.waitingColor,
      periods: row => row.waiting,
      metadata: metadata => metadata.waiting
    },
    {
      state: 'SLEEP',
      color: ThreadRow.sleepColor,
      periods: row => row.sleep,
      metadata: metadata => metadata.sleep
    },
    {
      state: 'SOCKET_READ',
      color: ThreadRow.socketReadColor,
      periods: row => row.socketRead,
      metadata: metadata => metadata.socketRead
    },
    {
      state: 'SOCKET_WRITE',
      color: ThreadRow.socketWriteColor,
      periods: row => row.socketWrite,
      metadata: metadata => metadata.socketWrite
    },
    {
      state: 'FILE_READ',
      color: ThreadRow.fileReadColor,
      periods: row => row.fileRead,
      metadata: metadata => metadata.fileRead
    },
    {
      state: 'FILE_WRITE',
      color: ThreadRow.fileWriteColor,
      periods: row => row.fileWrite,
      metadata: metadata => metadata.fileWrite
    }
  ];

  private readonly konvaContainer: HTMLElement;
  private readonly threadPointerName: string;
  private readonly threadTooltip: Tooltip;

  private readonly threadCommon: ThreadCommon;
  private readonly threadMetadata: ThreadMetadata;
  private readonly threadRow: ThreadRowData;
  private readonly windowDetails: ThreadWindowDetails;

  private stage: Konva.Stage;
  private threadPointer: HTMLElement;
  private fieldsTimeout: number | undefined;

  /**
   * Nanoseconds of recording time per pixel of this row, set when the row is drawn. Converting a
   * pixel back to time is what lets a tooltip describe the position under the pointer rather than
   * the merged band beneath it.
   */
  private nanosPerPixel = 0;

  /**
   * Bumped whenever the hovered position changes. A window lookup repaints the tooltip only while
   * its generation is still current, so a slow response cannot overwrite what the pointer moved on
   * to.
   */
  private hoverGeneration = 0;

  constructor(
    profileId: string,
    threadCommon: ThreadCommon,
    threadRow: ThreadRowData,
    canvasElementId: string,
    threadGroup: string | null = null
  ) {
    this.threadCommon = threadCommon;
    this.threadMetadata = threadCommon.metadata;
    this.threadRow = threadRow;
    this.windowDetails = new ThreadWindowDetails(profileId, threadRow.threadInfo, threadGroup);

    this.konvaContainer = document.getElementById(canvasElementId) as HTMLElement;
    this.stage = this.createStage();

    this.threadPointerName = canvasElementId + '-pointer';
    this.createHighlightDiv(this.konvaContainer, this.threadPointerName);
    this.threadPointer = document.getElementsByClassName(this.threadPointerName)[0] as HTMLElement;
    this.threadTooltip = new Tooltip(this.konvaContainer);

    this.konvaContainer.onmousemove = this.onMouseMoveEvent();
    this.konvaContainer.onmouseout = this.onMouseOut();
  }

  private onMouseMoveEvent(): (event: MouseEvent) => void {
    return (event: MouseEvent) => {
      const rect = this.konvaContainer.getBoundingClientRect();
      const x = Math.floor(event.clientX - rect.left);

      this.threadPointer.style.left = Math.floor(x + this.konvaContainer.offsetLeft) + 'px';
      this.threadPointer.style.top = Math.floor(this.konvaContainer.offsetTop) + 'px';
      this.threadPointer.style.display = 'block';
    };
  }

  private onMouseOut(): () => void {
    return () => {
      this.removeHighlight();
      this.removeTooltip();
    };
  }

  private removeHighlight(): void {
    this.threadPointer.style.display = 'none';
  }

  private removeTooltip(): void {
    // Invalidates any band lookup still in flight, so it cannot bring the tooltip back
    this.hoverGeneration++;
    this.threadTooltip.hideTooltip();
  }

  private createHighlightDiv(canvas: HTMLElement, threadPointerName: string): void {
    canvas.insertAdjacentHTML(
      'afterend',
      '<div class="' +
        threadPointerName +
        '" style="' +
        ' position: absolute;' +
        ' display: none;' +
        ' overflow: hidden;' +
        ' white-space: nowrap;' +
        ' pointer-events: none;' +
        ' background-color: black;' +
        ' width: 1px;' +
        ' height: ' +
        ThreadRow.FRAME_HEIGHT +
        'px;"></div>'
    );
  }

  public draw(): void {
    const width: number = this.stage.width();
    const pxPerNano = width / this.threadCommon.totalDuration;
    this.nanosPerPixel = this.threadCommon.totalDuration / width;
    const threadInfo = this.threadRow.threadInfo;

    const lifespanGroups = ThreadGroups.of(
      width,
      pxPerNano,
      ThreadRow.lifespanColor,
      this.threadRow.lifespan
    );

    const categories: HoverableCategory[] = ThreadRow.CATEGORIES.map(category => ({
      ...category,
      groups: ThreadGroups.of(width, pxPerNano, category.color, category.periods(this.threadRow))
    }));

    this.stage.add(this.borderLayer());
    this.stage.add(lifespanGroups.createLayer());
    categories.forEach(category => this.stage.add(category.groups.createLayer()));

    this.stage.on('mousemove', () => {
      const pos = this.stage.getPointerPosition() as Vector2d;
      const xPos = Math.floor(pos.x);
      const hoveredWindow = this.windowAt(xPos);

      const hovered: HoveredCategory[] = [];
      categories.forEach(category => {
        const rectangle = category.groups.rectangleAt(xPos);
        if (rectangle === undefined) {
          return;
        }
        hovered.push({
          category,
          window: hoveredWindow,
          runStart: this.windowFrom(rectangle.period.startOffset)
        });
      });

      window.clearTimeout(this.fieldsTimeout);

      if (hovered.length === 0) {
        this.removeTooltip();
        return;
      }

      // Anything scheduled for an earlier position is stale the moment the pointer moves
      const hover = ++this.hoverGeneration;

      // Resolving can take two rounds: a slice with no starts of its own only asks for the event
      // spanning it once the slice itself has come back. Repainting has to drive the next round, or
      // a pointer that never moves again would sit on the placeholder forever.
      const repaint = () => {
        if (hover !== this.hoverGeneration) {
          return;
        }
        this.showTooltip(threadInfo.name, pos, hovered);
        this.resolveWindows(hovered, repaint);
      };

      this.showTooltip(threadInfo.name, pos, hovered);
      this.resolveWindows(hovered, repaint);
    });
  }

  /**
   * The slice of recording time drawn at a pixel. Rounded outwards to whole pixels so neighbouring
   * positions describe adjacent, non-overlapping slices.
   */
  private windowAt(xPos: number): ThreadTimeWindow {
    return new ThreadTimeWindow(
      Math.floor(xPos * this.nanosPerPixel),
      Math.ceil((xPos + 1) * this.nanosPerPixel)
    );
  }

  /**
   * A window at the instant an event began, for the case where the hovered slice holds no starts of
   * its own. The server widens anything narrower than a millisecond, so asking for a zero-length
   * window here is enough to pick that event up.
   */
  private windowFrom(startOffset: number): ThreadTimeWindow {
    return new ThreadTimeWindow(startOffset, startOffset + 1);
  }

  /**
   * Renders the tooltip from what is known right now. Both the count and the field rows describe the
   * hovered slice, so both arrive together and both show a placeholder until they do.
   */
  private showTooltip(threadName: string, pos: Vector2d, hovered: HoveredCategory[]): void {
    const content = hovered.reduce(
      (tooltip, entry) =>
        tooltip +
        ThreadTooltips.basic(
          entry.category.metadata(this.threadMetadata),
          entry.category.color,
          this.categoryTooltip(entry)
        ),
      ThreadTooltips.header(threadName)
    );

    this.threadTooltip.showTooltip(new TooltipPosition(pos.x, pos.y), content);
  }

  /**
   * Turns what has been resolved for one category into what its tooltip should say.
   *
   * An event belongs to the slice it *starts* in, which keeps per-slice counts adding up to the
   * lane's total. The cost is that the middle of a long park or monitor wait holds no start at all,
   * so when a slice comes back empty under a rectangle the event that run began with is shown
   * instead — a coloured bar under the pointer should never describe nothing.
   */
  private categoryTooltip(entry: HoveredCategory): CategoryTooltip {
    const resolved = this.windowDetails.cached(entry.category.state, entry.window);
    if (resolved === undefined) {
      return { kind: 'pending' };
    }
    if (resolved === null) {
      return { kind: 'failed' };
    }

    const windowNanos = resolved.toOffset - resolved.fromOffset;
    if (resolved.eventCount > 0) {
      return {
        kind: 'events',
        count: resolved.eventCount,
        windowNanos: windowNanos,
        values: resolved.events[0]?.values
      };
    }

    const spanning = this.spanningEvent(entry);
    if (spanning === undefined) {
      return { kind: 'pending' };
    }
    if (spanning === null) {
      return { kind: 'empty', windowNanos: windowNanos };
    }
    return { kind: 'ongoing', startOffset: spanning.startOffset, values: spanning.values };
  }

  /**
   * The event a hovered run of activity began with: `undefined` while its lookup is outstanding,
   * `null` once it is known there is none.
   */
  private spanningEvent(
    entry: HoveredCategory
  ): { startOffset: number; values: Array<string> } | null | undefined {
    const resolved = this.windowDetails.cached(entry.category.state, entry.runStart);
    if (resolved === undefined) {
      return undefined;
    }
    const first = resolved?.events[0];
    if (first === undefined) {
      return null;
    }
    return { startOffset: first.startOffset, values: first.values };
  }

  /**
   * Fetches the hovered slices that are not resolved yet, and calls back once any of them arrives.
   * The pointer crosses many positions on the way to the one the user cares about, so the lookup
   * waits for it to settle first.
   */
  private resolveWindows(hovered: HoveredCategory[], onResolved: () => void): void {
    const pending: { state: ThreadEventState; window: ThreadTimeWindow }[] = [];
    hovered.forEach(entry => {
      const resolved = this.windowDetails.cached(entry.category.state, entry.window);
      if (resolved === undefined) {
        pending.push({ state: entry.category.state, window: entry.window });
        return;
      }
      // Only worth chasing the run's first event once the slice itself is known to hold no starts
      if (
        resolved !== null &&
        resolved.eventCount === 0 &&
        this.windowDetails.cached(entry.category.state, entry.runStart) === undefined
      ) {
        pending.push({ state: entry.category.state, window: entry.runStart });
      }
    });

    if (pending.length === 0) {
      return;
    }

    window.clearTimeout(this.fieldsTimeout);
    this.fieldsTimeout = window.setTimeout(() => {
      pending.forEach(entry => {
        this.windowDetails.load(entry.state, entry.window).then(() => onResolved());
      });
    }, ThreadRow.HOVER_SETTLE_MILLIS);
  }

  public resizeCanvas() {
    // The old stage keeps its canvas and its mousemove handler alive until it is told to go
    this.stage.destroy();
    // Every window is derived from the row's width, so none of them survive a resize
    this.windowDetails.clear();
    this.stage = this.createStage();
    this.draw();
  }

  private createStage(): Konva.Stage {
    return new Konva.Stage({
      container: this.konvaContainer.id,
      width: this.konvaContainer.offsetWidth,
      height: ThreadRow.FRAME_HEIGHT
    });
  }

  private borderLayer(): Konva.Layer {
    const borderLayer = new Konva.Layer();
    borderLayer.add(
      new Konva.Rect({
        x: 0,
        y: 0,
        width: this.stage.width(),
        height: ThreadRow.FRAME_HEIGHT,
        stroke: 'black',
        strokeWidth: 1
      })
    );
    return borderLayer;
  }

  onWindowScroll() {
    this.removeTooltip();
  }

  /**
   * Clean up resources to prevent memory leaks
   */
  destroy() {
    // Clear event handlers
    this.konvaContainer.onmousemove = null;
    this.konvaContainer.onmouseout = null;

    // Drop a pending window lookup so it cannot fire against a destroyed tooltip
    window.clearTimeout(this.fieldsTimeout);
    this.windowDetails.clear();

    // Hide and remove tooltip
    this.threadTooltip.hideTooltip();

    // Destroy Konva stage to release canvas resources
    if (this.stage) {
      this.stage.destroy();
    }

    // Remove the highlight pointer element
    if (this.threadPointer && this.threadPointer.parentNode) {
      this.threadPointer.parentNode.removeChild(this.threadPointer);
    }
  }
}

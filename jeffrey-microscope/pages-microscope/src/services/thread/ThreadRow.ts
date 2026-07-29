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
import ThreadTimeWindow from '@/services/api/model/ThreadTimeWindow';
import TooltipPosition from '@/services/tooltip/TooltipPosition';
import ThreadCommon from '@/services/api/model/ThreadCommon';
import ThreadMetadata from '@/services/api/model/ThreadMetadata';
import type { ThreadEventState } from '@/services/api/model/ThreadEventDetail';
import {
  BLOCKED_COLOR,
  FILE_READ_COLOR,
  FILE_WRITE_COLOR,
  LIFESPAN_COLOR,
  PARKED_COLOR,
  SLEEP_COLOR,
  SOCKET_READ_COLOR,
  SOCKET_WRITE_COLOR,
  THREAD_CATEGORIES,
  WAITING_COLOR
} from '@/services/thread/ThreadCategories';
import type { ThreadCategory } from '@/services/thread/ThreadCategories';
import Vector2d = Konva.Vector2d;

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
  // Aliases of the shared palette, kept because the page legend binds these names.
  static lifespanColor = LIFESPAN_COLOR;
  static parkedColor = PARKED_COLOR;
  static blockedColor = BLOCKED_COLOR;
  static waitingColor = WAITING_COLOR;
  static sleepColor = SLEEP_COLOR;
  static socketReadColor = SOCKET_READ_COLOR;
  static socketWriteColor = SOCKET_WRITE_COLOR;
  static fileReadColor = FILE_READ_COLOR;
  static fileWriteColor = FILE_WRITE_COLOR;

  static readonly FRAME_HEIGHT: number = 20;

  /**
   * How long the pointer has to rest on a band before its events are fetched. Sweeping across a row
   * crosses dozens of bands, and none of them are the one being looked at.
   */
  private static readonly HOVER_SETTLE_MILLIS = 120;

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

  /**
   * The categories this canvas draws. A lane normally draws all of them stacked, which is why a busy
   * category paints over the quieter ones underneath it; a sub-lane is the same renderer restricted
   * to one, so the two agree on geometry, colour and tooltip behaviour by construction.
   */
  private readonly drawnStates: ReadonlySet<ThreadEventState>;

  /**
   * Whether the thread's lifespan is drawn underneath. Only the full lane shows it — repeating the
   * green underlay on every sub-lane would say nothing new and would tint the bands above it.
   */
  private readonly withLifespan: boolean;

  /**
   * Whether this canvas created its own hover cache. A sub-lane borrows the lane's, and must not
   * clear a cache the lane is still using.
   */
  private readonly ownsWindowDetails: boolean;

  /**
   * @param options `states` restricts the canvas to a subset of the categories, and `windowDetails`
   *                shares one hover-lookup cache with the lane this canvas belongs to — the same
   *                thread asking about the same windows twice is pure waste
   */
  constructor(
    profileId: string,
    threadCommon: ThreadCommon,
    threadRow: ThreadRowData,
    canvasElementId: string,
    threadGroup: string | null = null,
    options: {
      states?: ThreadEventState[];
      windowDetails?: ThreadWindowDetails;
    } = {}
  ) {
    this.threadCommon = threadCommon;
    this.threadMetadata = threadCommon.metadata;
    this.threadRow = threadRow;
    this.ownsWindowDetails = options.windowDetails === undefined;
    this.windowDetails =
      options.windowDetails ??
      new ThreadWindowDetails(profileId, threadRow.threadInfo, threadGroup);
    this.drawnStates = new Set(options.states ?? THREAD_CATEGORIES.map(category => category.state));
    this.withLifespan = options.states === undefined;

    this.konvaContainer = document.getElementById(canvasElementId) as HTMLElement;
    this.stage = this.createStage();

    this.threadPointerName = canvasElementId + '-pointer';
    this.createHighlightDiv(this.konvaContainer, this.threadPointerName);
    this.threadPointer = document.getElementsByClassName(this.threadPointerName)[0] as HTMLElement;
    this.threadTooltip = new Tooltip(this.konvaContainer);

    this.konvaContainer.onmousemove = this.onMouseMoveEvent();
    this.konvaContainer.onmouseout = this.onMouseOut();
  }

  /**
   * The hover cache this lane is using, to be handed to its sub-lanes. They ask about the same
   * thread and the same windows, so one cache serves all of them and a window fetched by the lane is
   * already resolved when a sub-lane repeats it.
   */
  public sharedWindowDetails(): ThreadWindowDetails {
    return this.windowDetails;
  }

  private clearOwnedWindowDetails(): void {
    if (this.ownsWindowDetails) {
      this.windowDetails.clear();
    }
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

    const categories: HoverableCategory[] = THREAD_CATEGORIES.filter(category =>
      this.drawnStates.has(category.state)
    ).map(category => ({
      ...category,
      groups: ThreadGroups.of(width, pxPerNano, category.color, category.periods(this.threadRow))
    }));

    this.stage.add(this.borderLayer());
    if (this.withLifespan) {
      this.stage.add(
        ThreadGroups.of(
          width,
          pxPerNano,
          ThreadRow.lifespanColor,
          this.threadRow.lifespan
        ).createLayer()
      );
    }
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
    this.clearOwnedWindowDetails();
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
    this.clearOwnedWindowDetails();

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

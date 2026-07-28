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
import ThreadBandDetails from '../thread/ThreadBandDetails';
import ThreadRectangle from '../thread/ThreadRectangle';
import ThreadRowData from '@/services/api/model/ThreadRowData';
import ThreadPeriod from '@/services/api/model/ThreadPeriod';
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

interface HoveredCategory {
  category: HoverableCategory;
  rectangles: ThreadRectangle[];
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
  private readonly bandDetails: ThreadBandDetails;

  private stage: Konva.Stage;
  private threadPointer: HTMLElement;
  private fieldsTimeout: number | undefined;

  /**
   * Bumped whenever the hovered bands change. A band lookup repaints the tooltip only while its
   * generation is still current, so a slow response cannot overwrite what the pointer moved on to.
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
    this.bandDetails = new ThreadBandDetails(profileId, threadRow.threadInfo, threadGroup);

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
    const pxPerMillis = this.stage.width() / this.threadCommon.totalDuration;
    const threadInfo = this.threadRow.threadInfo;

    const width: number = this.stage.width();
    const lifespanGroups = new ThreadGroups(width, pxPerMillis, ThreadRow.lifespanColor);

    this.threadRow.lifespan.forEach((period: ThreadPeriod) => lifespanGroups.addPeriod(period));

    const categories: HoverableCategory[] = ThreadRow.CATEGORIES.map(category => {
      const groups = new ThreadGroups(width, pxPerMillis, category.color);
      category.periods(this.threadRow).forEach((period: ThreadPeriod) => groups.addPeriod(period));
      return { ...category, groups };
    });

    this.stage.add(this.borderLayer());
    this.stage.add(lifespanGroups.createLayer());
    categories.forEach(category => this.stage.add(category.groups.createLayer()));

    this.stage.on('mousemove', () => {
      const pos = this.stage.getPointerPosition() as Vector2d;
      const xPos = Math.floor(pos.x);

      const hovered: HoveredCategory[] = categories
        .map(category => ({ category, rectangles: category.groups.selectRectangles(xPos) }))
        .filter(entry => entry.rectangles.length > 0);

      window.clearTimeout(this.fieldsTimeout);

      if (hovered.length === 0) {
        this.removeTooltip();
        return;
      }

      // Anything scheduled for an earlier position is stale the moment the pointer moves
      const hover = ++this.hoverGeneration;

      this.showTooltip(threadInfo.name, pos, hovered);
      this.resolveFields(hovered, () => {
        if (hover === this.hoverGeneration) {
          this.showTooltip(threadInfo.name, pos, hovered);
        }
      });
    });
  }

  /**
   * Renders the tooltip from what is known right now — every category header and event count comes
   * from the bands themselves, and the field rows fill in once their lookup lands.
   */
  private showTooltip(threadName: string, pos: Vector2d, hovered: HoveredCategory[]): void {
    const content = hovered.reduce(
      (tooltip, entry) =>
        tooltip +
        ThreadTooltips.basic(
          entry.category.metadata(this.threadMetadata),
          entry.rectangles,
          entry.category.color,
          this.bandDetails.cached(entry.category.state, entry.rectangles[0].period)
        ),
      ThreadTooltips.header(threadName)
    );

    this.threadTooltip.showTooltip(new TooltipPosition(pos.x, pos.y), 0, content);
  }

  /**
   * Fetches the field values for the hovered bands that are not resolved yet, and calls back once
   * any of them arrives. The pointer moves across many bands on the way to the one the user cares
   * about, so the lookup waits for it to settle first.
   */
  private resolveFields(hovered: HoveredCategory[], onResolved: () => void): void {
    const pending = hovered.filter(
      entry =>
        this.bandDetails.cached(entry.category.state, entry.rectangles[0].period) === undefined
    );
    if (pending.length === 0) {
      return;
    }

    window.clearTimeout(this.fieldsTimeout);
    this.fieldsTimeout = window.setTimeout(() => {
      pending.forEach(entry => {
        this.bandDetails
          .load(entry.category.state, entry.rectangles[0].period)
          .then(() => onResolved());
      });
    }, ThreadRow.HOVER_SETTLE_MILLIS);
  }

  public resizeCanvas() {
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

    // Drop a pending band lookup so it cannot fire against a destroyed tooltip
    window.clearTimeout(this.fieldsTimeout);

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

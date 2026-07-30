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

import ThreadRectangle from './ThreadRectangle';
import Konva from 'konva';
import ThreadRow from './ThreadRow';
import Rectangle from './Rectangle';
import ThreadPeriod from '@/services/api/model/ThreadPeriod';

/**
 * The rectangles of one band category on one row, ready to draw and to hit-test.
 *
 * Built once through {@link ThreadGroups.of} and immutable afterwards, so the ordering the
 * hit-test relies on is established a single time rather than re-established on every pointer move.
 */
export default class ThreadGroups {
  static readonly MIN_WIDTH = 1;

  private constructor(
    private readonly color: string,
    private readonly rectangles: ThreadRectangle[]
  ) {}

  /**
   * @param totalWidth  width of the row's canvas in pixels
   * @param pxPerNano   pixels per nanosecond of recording time — the canvas width over the
   *                    recording's total duration, which the timeline carries in nanoseconds
   */
  public static of(
    totalWidth: number,
    pxPerNano: number,
    color: string,
    periods: ThreadPeriod[]
  ): ThreadGroups {
    const rectangles = periods.map(
      period =>
        new ThreadRectangle(ThreadGroups.createRectangle(period, totalWidth, pxPerNano), period)
    );
    rectangles.sort((a, b) => a.start - b.start);
    return new ThreadGroups(color, rectangles);
  }

  public createLayer(): Konva.Layer {
    const layer = new Konva.Layer();
    this.rectangles.forEach(group => {
      const konvaRect = new Konva.Rect({
        x: group.start,
        y: 0,
        width: group.end - group.start,
        height: ThreadRow.FRAME_HEIGHT,
        fill: this.color
      });

      layer.add(konvaRect);
    });
    return layer;
  }

  /**
   * Whether this category has anything drawn at the given pixel — that is, whether it belongs in the
   * tooltip at all. What is *in* that pixel is answered by the server for the hovered time window,
   * not read off the rectangle: a rectangle can stand for a whole run of merged activity.
   */
  public covers(xPos: number): boolean {
    return this.rectangles.some(segment => segment.start <= xPos && segment.end > xPos);
  }

  /**
   * The first rectangle drawn at the given pixel, or `undefined`. Used only to fall back to the
   * event a rectangle began with, when nothing starts inside the hovered window itself.
   */
  public rectangleAt(xPos: number): ThreadRectangle | undefined {
    return this.rectangles.find(segment => segment.start <= xPos && segment.end > xPos);
  }

  private static createRectangle(
    period: ThreadPeriod,
    totalWidth: number,
    pxPerNano: number
  ): Rectangle {
    let x = Math.floor(period.startOffset * pxPerNano);
    // if the activity is at the end of the thread, the x value can be equal to the canvas width (not visible)
    // in this case, we need to decrease the x value by 1 to make the activity visible, e.g. Shutdown hook Thread
    if (x === totalWidth) {
      x = x - (ThreadGroups.MIN_WIDTH + 1);
    }

    const calculatedWidth = Math.round(period.width * pxPerNano);
    return new Rectangle(
      x,
      0,
      Math.max(calculatedWidth, ThreadGroups.MIN_WIDTH),
      ThreadRow.FRAME_HEIGHT
    );
  }
}

/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

/** Where the graph sits against the window, in CSS pixels. */
export interface GraphViewport {
  /** Height of the whole graph, every row of it. */
  graphHeight: number;
  rowHeight: number;
  /** Top edge of the graph relative to the top of the window; negative once scrolled past. */
  graphTop: number;
  windowHeight: number;
}

/**
 * The vertical slice of a graph its canvas holds, in graph coordinates (0 is the top of the first
 * row). The canvas covers the rows in the window plus one window height above and below, not the
 * whole graph: a backing store for a graph a thousand levels deep runs into hundreds of megabytes and
 * past the browser's size limit, after which it draws nothing. The margin lets scrolling show rows
 * that are already drawn; the canvas is redrawn once the window leaves the band.
 */
export default class CanvasBand {
  static readonly EMPTY = new CanvasBand(0, 0);

  readonly top: number;
  readonly bottom: number;

  private constructor(top: number, bottom: number) {
    this.top = top;
    this.bottom = bottom;
  }

  get height(): number {
    return this.bottom - this.top;
  }

  /**
   * The band to draw for the viewport: the visible part of the graph widened by one window height on
   * either side, aligned to whole rows and kept within the graph. A graph shorter than that is drawn
   * whole and never needs a redraw for scrolling.
   */
  static around(viewport: GraphViewport): CanvasBand {
    const { graphHeight, rowHeight, windowHeight } = viewport;
    const visible = CanvasBand.visible(viewport);
    const top = Math.max(visible.top - windowHeight, 0);
    const bottom = Math.min(visible.bottom + windowHeight, graphHeight);
    return new CanvasBand(
      Math.floor(top / rowHeight) * rowHeight,
      Math.min(Math.ceil(bottom / rowHeight) * rowHeight, graphHeight)
    );
  }

  /** Whether every visible row of the graph lies in this band, so nothing new scrolled into view. */
  covers(viewport: GraphViewport): boolean {
    const visible = CanvasBand.visible(viewport);
    return visible.top >= this.top && visible.bottom <= this.bottom;
  }

  equals(other: CanvasBand): boolean {
    return this.top === other.top && this.bottom === other.bottom;
  }

  /** The part of the graph inside the window, in graph coordinates; empty when it is out of view. */
  private static visible(viewport: GraphViewport): { top: number; bottom: number } {
    const { graphHeight, graphTop, windowHeight } = viewport;
    const top = CanvasBand.clamp(-graphTop, 0, graphHeight);
    const bottom = CanvasBand.clamp(windowHeight - graphTop, top, graphHeight);
    return { top, bottom };
  }

  private static clamp(value: number, min: number, max: number): number {
    return Math.min(Math.max(value, min), max);
  }
}

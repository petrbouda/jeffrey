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

import Flamegraph from '@/services/flamegraphs/Flamegraph';

export default class FlameUtils {
  static canvasResize(flamegraph: Flamegraph, minusPadding = 0) {
    const w = document.getElementById('flamegraphCanvas')!.parentElement!.clientWidth;
    if (flamegraph != null) {
      flamegraph.resizeWidthCanvas(w - minusPadding);
    }
  }

  /**
   * Follows the scroll position of the element the graph sits in, so the tooltip and the highlight
   * stay on the frame under the pointer. The id of that element, not a class, despite the name the
   * callers pass it under.
   *
   * <p>A caller without a scroll container passes null, and one whose container is not in the DOM
   * gets the same treatment: this runs inside the graph updater's init callback, so throwing here
   * would strand the "Generating Flamegraph..." preloader on a graph that has already drawn.
   */
  static registerAdjustableScrollableComponent(
    flamegraph: Flamegraph,
    scrollableComponent: string | null
  ) {
    if (scrollableComponent == null) {
      return;
    }

    const el = document.getElementById(scrollableComponent);
    if (el == null) {
      return;
    }

    el.addEventListener('scroll', () => {
      flamegraph.onScroll();
      flamegraph.removeHighlight();
    });
  }
}

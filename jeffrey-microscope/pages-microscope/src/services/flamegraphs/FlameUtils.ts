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

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

import Flamegraph from "@/services/flamegraphs/Flamegraph";

export default class FlameUtils {

    static canvasResize(flamegraph: Flamegraph, minusPadding = 0) {
        let w = document.getElementById("flamegraphCanvas")!.parentElement!.clientWidth
        if (flamegraph != null) {
            flamegraph.resizeWidthCanvas(w - minusPadding)
        }
    }

    static registerAdjustableScrollableComponent(flamegraph: Flamegraph, scrollableComponent: string | null) {
        if (scrollableComponent != null) {
            let el = document.getElementsByClassName(scrollableComponent)[0]
            el.addEventListener("scroll", () => {
                flamegraph.updateScrollPositionY(el.scrollTop)
                flamegraph.removeHighlight()
            });
        }
    }
}

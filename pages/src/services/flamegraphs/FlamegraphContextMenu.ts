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

import {MenuItem} from "primevue/menuitem";

export default class FlamegraphContextMenu {

    static resolve(timeseriesSearchCallback: () => void, resetCallback: () => void): MenuItem[] {
        return this.contextMenuItems(
            timeseriesSearchCallback,
            resetCallback
        )
    }

    static contextMenuItems(searchInTimeseries: () => void, resetZoom: () => void): MenuItem[] {
        let contextMenuItems: MenuItem[] = []

        if (searchInTimeseries != null) {
            contextMenuItems.push({
                label: 'Search the Frame',
                icon: 'pi pi-chart-bar',
                command: searchInTimeseries
            })
        }

        if (resetZoom != null) {
            contextMenuItems.push({
                label: 'Zoom out Flamegraph',
                icon: 'pi pi-search-minus',
                command: resetZoom
            })
        }

        contextMenuItems.push(
            {
                separator: true
            }, {
                label: 'Close',
                icon: 'pi pi-times'
            }
        )
        return contextMenuItems
    }
}

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

/**
 * The menu-entry shape the flamegraph's context menu consumes. This used to be PrimeVue's
 * `MenuItem`, imported from a package that is no longer a dependency — the import only survived
 * because type-only imports are erased before the bundler would have failed to resolve it.
 */
export interface MenuItem {
  label?: string;
  icon?: string;
  command?: () => void;
  separator?: boolean;
}

export default class FlamegraphContextMenu {
  static resolve(timeseriesSearchCallback: () => void, resetCallback: () => void): MenuItem[] {
    return this.contextMenuItems(timeseriesSearchCallback, resetCallback);
  }

  static contextMenuItems(searchInTimeseries: () => void, resetZoom: () => void): MenuItem[] {
    const contextMenuItems: MenuItem[] = [];

    if (searchInTimeseries != null) {
      contextMenuItems.push({
        label: 'Search the Frame',
        icon: 'pi pi-chart-bar',
        command: searchInTimeseries
      });
    }

    if (resetZoom != null) {
      contextMenuItems.push({
        label: 'Zoom out Flamegraph',
        icon: 'pi pi-search-minus',
        command: resetZoom
      });
    }

    contextMenuItems.push(
      {
        separator: true
      },
      {
        label: 'Close',
        icon: 'pi pi-times'
      }
    );
    return contextMenuItems;
  }
}

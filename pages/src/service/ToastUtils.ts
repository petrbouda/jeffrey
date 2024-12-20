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

export default class ToastUtils {

    static exported(toast: any) {
        toast.add({
            severity: 'success',
            summary: 'Successful',
            detail: 'Flamegraph exported',
            life: 3000
        });
    }

    static notUpdatableAfterZoom(toast: any) {
        toast.add({
            severity: 'info',
            summary: 'Flamegraph not updated',
            detail: 'Generated flamegraph doesn\'t get updated after zooming of timeseries graph',
            life: 5000
        });
    }
}

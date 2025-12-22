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

export default class FrameColorResolver {
    private static readonly FRAME_TYPE_COLORS: Record<string, string> = {
        'C1_COMPILED': '#cce880',
        'NATIVE': '#ffa6a6',
        'CPP': '#e3ed6d',
        'INTERPRETED': '#b2e1b2',
        'JIT_COMPILED': '#94f25a',
        'INLINED': '#8eeded',
        'KERNEL': '#f2af5e',
        'THREAD_NAME_SYNTHETIC': '#e17e5a',
        'ALLOCATED_OBJECT_SYNTHETIC': '#00b6ff',
        'ALLOCATED_OBJECT_IN_NEW_TLAB_SYNTHETIC': '#ADE8F4',
        'ALLOCATED_OBJECT_OUTSIDE_TLAB_SYNTHETIC': '#00B4D8',
        'BLOCKING_OBJECT_SYNTHETIC': '#e17e5a',
        'LAMBDA_SYNTHETIC': '#b3c6ff',
        'UNKNOWN': '#000000',
    };

    private static readonly GREEN_COLORS = ['#E5FFCC', '#E5FFBB', '#CCFF99', '#B2FF66', '#99FF33', '#66CC00'];
    private static readonly RED_COLORS = ['#FFEEEE', '#FFDDDD', '#FFCCCC', '#FFAAAA', '#FF8888', '#FF3333'];
    private static readonly NEUTRAL_COLOR = '#E6E6E6';
    // Grey color for frames before marker (guardian analysis)
    private static readonly BEFORE_MARKER_COLOR = '#CCCCCC';

    /**
     * Resolves color for regular flamegraphs based on frame type.
     * @param frameType The type of the frame
     * @param beforeMarker If true, frame is before guardian marker and should be grey
     */
    static resolveByType(frameType: string, beforeMarker?: boolean): string {
        if (beforeMarker) {
            return this.BEFORE_MARKER_COLOR;
        }
        return this.FRAME_TYPE_COLORS[frameType] || '#000000';
    }

    /**
     * Resolves color for differential flamegraphs based on the difference between primary and secondary values.
     * Green colors indicate removal (primary < secondary), red colors indicate addition (primary > secondary).
     */
    static resolveDiffColor(primary: number, secondary: number, frameType: string): string {
        if (frameType === 'LAMBDA_SYNTHETIC') {
            return this.FRAME_TYPE_COLORS[frameType];
        }

        const total = primary + secondary;
        if (total === 0) {
            return this.NEUTRAL_COLOR;
        }

        const diff = Math.abs(primary - secondary);
        const pct = diff / total;

        if (pct <= 0.02) {
            return this.NEUTRAL_COLOR;
        }

        let index: number;
        if (pct <= 0.05) {
            index = 0;
        } else if (pct <= 0.1) {
            index = 1;
        } else if (pct <= 0.4) {
            index = 2;
        } else if (pct <= 0.8) {
            index = 3;
        } else {
            index = 4;
        }

        return primary < secondary ? this.GREEN_COLORS[index] : this.RED_COLORS[index];
    }
}

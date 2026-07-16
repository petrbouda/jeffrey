/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

import type { DonutChartData } from '../components/DonutWithLegend.vue';
import FormattingService from './FormattingService';

export interface DonutItem {
  label: string;
  value: number;
  color: string;
}

/**
 * Builds the DonutChartData structure (series/labels/colors/legendItems) from a flat
 * list of labeled values, removing the per-chart mapping boilerplate. Callers only
 * decide labels, values, and colors; formatting is consistent app-wide.
 */
export function buildDonutData(items: DonutItem[], totalValue: number | string): DonutChartData {
  const total =
    typeof totalValue === 'number' ? FormattingService.formatNumber(totalValue) : totalValue;

  return {
    series: items.map(item => item.value),
    labels: items.map(item => item.label),
    colors: items.map(item => item.color),
    totalValue: total,
    legendItems: items.map(item => ({
      color: item.color,
      label: item.label,
      value: FormattingService.formatNumber(item.value)
    }))
  };
}

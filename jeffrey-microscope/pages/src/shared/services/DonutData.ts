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

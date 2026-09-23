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

import SubSecondData from '@/services/subsecond/model/SubSecondData';
import SubSecondSerie from '@/services/subsecond/model/SubSecondSerie';

export interface DifferenceResult {
  data: SubSecondData;
  minValue: number;
  maxValue: number;
}

/**
 * Computes the difference between primary and secondary SubSecond data.
 * Returns primary - secondary, so positive values mean primary is higher.
 */
export function computeDifference(
  primary: SubSecondData,
  secondary: SubSecondData
): DifferenceResult {
  const differenceSeries: SubSecondSerie[] = [];
  let minValue = 0;
  let maxValue = 0;

  // Iterate through each series (each millisecond bucket row)
  for (let i = 0; i < primary.series.length; i++) {
    const primarySerie = primary.series[i];
    const secondarySerie = secondary.series[i];

    if (!secondarySerie) {
      // If secondary doesn't have this row, use primary values as-is
      differenceSeries.push(primarySerie);
      continue;
    }

    const differenceData: any[] = [];

    // Iterate through each data point (each second column)
    const maxLength = Math.max(primarySerie.data.length, secondarySerie.data.length);

    for (let j = 0; j < maxLength; j++) {
      const primaryPoint = primarySerie.data[j];
      const secondaryPoint = secondarySerie.data[j];

      // Get values, treating missing points as 0
      const primaryValue = primaryPoint ? ((primaryPoint as any).y ?? primaryPoint[1] ?? 0) : 0;
      const secondaryValue = secondaryPoint
        ? ((secondaryPoint as any).y ?? secondaryPoint[1] ?? 0)
        : 0;

      const difference = primaryValue - secondaryValue;

      // Track min/max for color scale
      if (difference < minValue) {
        minValue = difference;
      }
      if (difference > maxValue) {
        maxValue = difference;
      }

      // Get x value from either point
      const xValue = primaryPoint
        ? ((primaryPoint as any).x ?? primaryPoint[0])
        : ((secondaryPoint as any).x ?? secondaryPoint[0]);

      differenceData.push({
        x: xValue,
        y: difference,
        primary: primaryValue,
        secondary: secondaryValue
      });
    }

    differenceSeries.push(
      new SubSecondSerie(primarySerie.name, primarySerie.group, differenceData)
    );
  }

  // Handle case where secondary has more rows than primary
  for (let i = primary.series.length; i < secondary.series.length; i++) {
    const secondarySerie = secondary.series[i];
    const differenceData: any[] = [];

    for (let j = 0; j < secondarySerie.data.length; j++) {
      const secondaryPoint = secondarySerie.data[j];
      const secondaryValue = (secondaryPoint as any).y ?? secondaryPoint[1] ?? 0;
      const difference = -secondaryValue; // Primary is 0, so difference is negative

      if (difference < minValue) {
        minValue = difference;
      }
      if (difference > maxValue) {
        maxValue = difference;
      }

      differenceData.push({
        x: (secondaryPoint as any).x ?? secondaryPoint[0],
        y: difference,
        primary: 0,
        secondary: secondaryValue
      });
    }

    differenceSeries.push(
      new SubSecondSerie(secondarySerie.name, secondarySerie.group, differenceData)
    );
  }

  // Use the larger absolute value for symmetric color scaling
  const absMax = Math.max(Math.abs(minValue), Math.abs(maxValue));

  return {
    data: new SubSecondData(absMax, differenceSeries),
    minValue,
    maxValue
  };
}

import { computeDifference } from './SubSecondDifferenceUtils';
import SubSecondData from './model/SubSecondData';
import SubSecondSerie from './model/SubSecondSerie';

function makeSerie(name: string, data: number[][]): SubSecondSerie {
  return new SubSecondSerie(name, 'group', data);
}

function makeData(maxvalue: number, series: SubSecondSerie[]): SubSecondData {
  return new SubSecondData(maxvalue, series);
}

describe('computeDifference', () => {
  it('returns all zeros when primary equals secondary', () => {
    const serie = makeSerie('row0', [
      [0, 10],
      [1, 20]
    ]);
    const primary = makeData(20, [serie]);
    const secondary = makeData(20, [
      makeSerie('row0', [
        [0, 10],
        [1, 20]
      ])
    ]);

    const result = computeDifference(primary, secondary);

    expect(result.minValue).toBe(0);
    expect(result.maxValue).toBe(0);
    result.data.series[0].data.forEach((point: any) => {
      expect(point.y).toBe(0);
    });
  });

  it('returns positive differences when primary > secondary', () => {
    const primary = makeData(30, [
      makeSerie('row0', [
        [0, 30],
        [1, 20]
      ])
    ]);
    const secondary = makeData(10, [
      makeSerie('row0', [
        [0, 10],
        [1, 10]
      ])
    ]);

    const result = computeDifference(primary, secondary);

    expect(result.maxValue).toBe(20);
    expect(result.minValue).toBe(0);
    expect(result.data.series[0].data[0]).toMatchObject({ x: 0, y: 20 });
    expect(result.data.series[0].data[1]).toMatchObject({ x: 1, y: 10 });
  });

  it('returns negative differences when secondary > primary', () => {
    const primary = makeData(10, [
      makeSerie('row0', [
        [0, 5],
        [1, 10]
      ])
    ]);
    const secondary = makeData(30, [
      makeSerie('row0', [
        [0, 15],
        [1, 30]
      ])
    ]);

    const result = computeDifference(primary, secondary);

    expect(result.minValue).toBe(-20);
    expect(result.data.series[0].data[0]).toMatchObject({ y: -10 });
    expect(result.data.series[0].data[1]).toMatchObject({ y: -20 });
  });

  it('handles primary having more data points than secondary', () => {
    const primary = makeData(10, [
      makeSerie('row0', [
        [0, 10],
        [1, 20],
        [2, 30]
      ])
    ]);
    const secondary = makeData(10, [makeSerie('row0', [[0, 10]])]);

    const result = computeDifference(primary, secondary);

    // Point at index 1 and 2: secondary is missing, treated as 0
    expect(result.data.series[0].data[1]).toMatchObject({ y: 20 });
    expect(result.data.series[0].data[2]).toMatchObject({ y: 30 });
  });

  it('handles secondary having more rows than primary', () => {
    const primary = makeData(10, [makeSerie('row0', [[0, 10]])]);
    const secondary = makeData(10, [
      makeSerie('row0', [[0, 10]]),
      makeSerie('row1', [
        [0, 5],
        [1, 15]
      ])
    ]);

    const result = computeDifference(primary, secondary);

    // Extra secondary row becomes negative
    expect(result.data.series.length).toBe(2);
    expect(result.data.series[1].data[0]).toMatchObject({ y: -5 });
    expect(result.data.series[1].data[1]).toMatchObject({ y: -15 });
  });

  it('uses symmetric color scaling (absMax)', () => {
    const primary = makeData(10, [makeSerie('row0', [[0, 10]])]);
    const secondary = makeData(30, [makeSerie('row0', [[0, 30]])]);

    const result = computeDifference(primary, secondary);

    // difference = -20, so absMax = 20
    expect(result.data.maxvalue).toBe(20);
    expect(result.minValue).toBe(-20);
    expect(result.maxValue).toBe(0);
  });

  it('handles empty series', () => {
    const primary = makeData(0, []);
    const secondary = makeData(0, []);

    const result = computeDifference(primary, secondary);

    expect(result.data.series).toEqual([]);
    expect(result.minValue).toBe(0);
    expect(result.maxValue).toBe(0);
  });
});

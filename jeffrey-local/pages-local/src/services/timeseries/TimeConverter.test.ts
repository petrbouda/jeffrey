import TimeConverter from './TimeConverter';

describe('TimeConverter', () => {

  describe('toChartTime', () => {
    it('converts seconds to milliseconds', () => {
      const converter = new TimeConverter('seconds');
      expect(converter.toChartTime(10)).toBe(10000);
      expect(converter.toChartTime(0)).toBe(0);
    });

    it('passes through milliseconds unchanged', () => {
      const converter = new TimeConverter('milliseconds');
      expect(converter.toChartTime(10000)).toBe(10000);
    });

    it('passes through absolute-milliseconds unchanged', () => {
      const converter = new TimeConverter('absolute-milliseconds');
      expect(converter.toChartTime(1705311000000)).toBe(1705311000000);
    });
  });

  describe('fromChartTime', () => {
    it('converts chart milliseconds back to seconds', () => {
      const converter = new TimeConverter('seconds');
      expect(converter.fromChartTime(10000)).toBe(10);
      expect(converter.fromChartTime(0)).toBe(0);
    });

    it('passes through milliseconds unchanged', () => {
      const converter = new TimeConverter('milliseconds');
      expect(converter.fromChartTime(10000)).toBe(10000);
    });

    it('roundtrips correctly', () => {
      const converter = new TimeConverter('seconds');
      expect(converter.fromChartTime(converter.toChartTime(42))).toBe(42);
    });
  });

  describe('formatTime', () => {
    it('formats relative seconds as HH:MM:SS', () => {
      const converter = new TimeConverter('seconds');
      // 3661 seconds = 1h 1m 1s, toChartTime => 3661000ms, UTC time from epoch
      expect(converter.formatTime(3661)).toBe('01:01:01');
    });

    it('formats zero as 00:00:00', () => {
      const converter = new TimeConverter('seconds');
      expect(converter.formatTime(0)).toBe('00:00:00');
    });

    it('formats relative milliseconds', () => {
      const converter = new TimeConverter('milliseconds');
      // 3661000ms = 1h 1m 1s
      expect(converter.formatTime(3661000)).toBe('01:01:01');
    });
  });

  describe('formatTimeRange', () => {
    it('formats start and end as range', () => {
      const converter = new TimeConverter('seconds');
      const result = converter.formatTimeRange(0, 3600);
      expect(result).toBe('00:00:00 - 01:00:00');
    });
  });

  describe('getVisibleRangeFromMinutes', () => {
    it('converts minutes to seconds for seconds mode', () => {
      const converter = new TimeConverter('seconds');
      expect(converter.getVisibleRangeFromMinutes(5)).toBe(300);
    });

    it('converts minutes to milliseconds for milliseconds mode', () => {
      const converter = new TimeConverter('milliseconds');
      expect(converter.getVisibleRangeFromMinutes(5)).toBe(300000);
    });

    it('converts minutes to milliseconds for absolute-milliseconds mode', () => {
      const converter = new TimeConverter('absolute-milliseconds');
      expect(converter.getVisibleRangeFromMinutes(5)).toBe(300000);
    });
  });

  describe('default constructor', () => {
    it('defaults to seconds', () => {
      const converter = new TimeConverter();
      expect(converter.toChartTime(1)).toBe(1000);
    });
  });
});

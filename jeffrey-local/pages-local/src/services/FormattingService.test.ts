import FormattingService from './FormattingService';

describe('FormattingService', () => {

  describe('formatBytes', () => {
    it('returns "0.00 B" for zero', () => {
      expect(FormattingService.formatBytes(0)).toBe('0.00 B');
    });

    it('returns raw string for negative values', () => {
      expect(FormattingService.formatBytes(-100)).toBe('-100');
    });

    it('formats bytes below 1 KiB', () => {
      expect(FormattingService.formatBytes(500)).toBe('500.00 B');
    });

    it('formats exact KiB boundary', () => {
      expect(FormattingService.formatBytes(1024)).toBe('1.00 KiB');
    });

    it('formats exact MiB boundary', () => {
      expect(FormattingService.formatBytes(1048576)).toBe('1.00 MiB');
    });

    it('formats GiB range', () => {
      expect(FormattingService.formatBytes(1073741824)).toBe('1.00 GiB');
    });

    it('formats TiB range', () => {
      expect(FormattingService.formatBytes(1099511627776)).toBe('1.00 TiB');
    });

    it('formats fractional values', () => {
      expect(FormattingService.formatBytes(1536)).toBe('1.50 KiB');
    });
  });

  describe('formatDuration', () => {
    it('returns "-" for undefined/null/negative', () => {
      expect(FormattingService.formatDuration(undefined as any)).toBe('-');
      expect(FormattingService.formatDuration(null as any)).toBe('-');
      expect(FormattingService.formatDuration(-1)).toBe('-');
    });

    it('returns "∞" for LONG_MAX', () => {
      expect(FormattingService.formatDuration(FormattingService.LONG_MAX)).toBe('∞');
    });

    it('returns "0" for zero', () => {
      expect(FormattingService.formatDuration(0)).toBe('0');
    });

    it('formats nanoseconds only', () => {
      expect(FormattingService.formatDuration(500)).toBe('500ns');
    });

    it('formats microseconds and nanoseconds', () => {
      expect(FormattingService.formatDuration(1500)).toBe('1us 500ns');
    });

    it('formats milliseconds', () => {
      expect(FormattingService.formatDuration(5_000_000)).toBe('5ms');
    });

    it('formats seconds', () => {
      expect(FormattingService.formatDuration(1_000_000_000)).toBe('1s');
    });

    it('formats multi-unit duration', () => {
      // 1h 30m 15s
      const nanos = 1 * 3_600_000_000_000 + 30 * 60_000_000_000 + 15 * 1_000_000_000;
      expect(FormattingService.formatDuration(nanos)).toBe('1h 30m 15s');
    });

    it('formats days', () => {
      const nanos = 2 * 86_400_000_000_000;
      expect(FormattingService.formatDuration(nanos)).toBe('2d');
    });
  });

  describe('formatDuration2Units', () => {
    it('truncates to 2 units', () => {
      // 1h 30m 15s → "1h 30m"
      const nanos = 1 * 3_600_000_000_000 + 30 * 60_000_000_000 + 15 * 1_000_000_000;
      expect(FormattingService.formatDuration2Units(nanos)).toBe('1h 30m');
    });

    it('returns single unit if only one present', () => {
      expect(FormattingService.formatDuration2Units(5_000_000)).toBe('5ms');
    });

    it('propagates special values', () => {
      expect(FormattingService.formatDuration2Units(0)).toBe('0');
      expect(FormattingService.formatDuration2Units(-1)).toBe('-');
    });
  });

  describe('formatTimestamp', () => {
    it('returns "-" for undefined/null', () => {
      expect(FormattingService.formatTimestamp(undefined as any)).toBe('-');
      expect(FormattingService.formatTimestamp(null as any)).toBe('-');
    });

    it('returns "-" for NO_TIMESTAMP sentinel', () => {
      expect(FormattingService.formatTimestamp(FormattingService.NO_TIMESTAMP)).toBe('-');
    });

    it('returns "0" for zero', () => {
      expect(FormattingService.formatTimestamp(0)).toBe('0');
    });

    it('formats valid epoch millis as ISO string', () => {
      const result = FormattingService.formatTimestamp(1705311000000);
      expect(result).toBe('2024-01-15 09:30:00.000Z');
    });
  });

  describe('formatNumber', () => {
    it('returns "-" for undefined/null', () => {
      expect(FormattingService.formatNumber(undefined as any)).toBe('-');
      expect(FormattingService.formatNumber(null as any)).toBe('-');
    });

    it('returns raw number below 1000', () => {
      expect(FormattingService.formatNumber(0)).toBe('0');
      expect(FormattingService.formatNumber(999)).toBe('999');
    });

    it('formats thousands as K', () => {
      expect(FormattingService.formatNumber(1000)).toBe('1.0K');
      expect(FormattingService.formatNumber(1500)).toBe('1.5K');
    });

    it('formats millions as M', () => {
      expect(FormattingService.formatNumber(1000000)).toBe('1.0M');
      expect(FormattingService.formatNumber(2500000)).toBe('2.5M');
    });
  });

  describe('formatPercentage', () => {
    it('formats 0', () => {
      expect(FormattingService.formatPercentage(0)).toBe('0.00%');
    });

    it('formats 50%', () => {
      expect(FormattingService.formatPercentage(0.5)).toBe('50.00%');
    });

    it('formats 100%', () => {
      expect(FormattingService.formatPercentage(1.0)).toBe('100.00%');
    });

    it('formats small fractional', () => {
      expect(FormattingService.formatPercentage(0.001)).toBe('0.10%');
    });
  });

  describe('format dispatcher', () => {
    it('routes MemoryAddress to hex', () => {
      expect(FormattingService.format(255, 'jdk.jfr.MemoryAddress')).toBe('0xFF');
    });

    it('routes DataAmount to formatBytes', () => {
      expect(FormattingService.format(1024, 'jdk.jfr.DataAmount')).toBe('1.00 KiB');
    });

    it('routes Percentage to formatPercentage', () => {
      expect(FormattingService.format(0.5, 'jdk.jfr.Percentage')).toBe('50.00%');
    });

    it('routes Timestamp to formatTimestamp', () => {
      expect(FormattingService.format(null, 'jdk.jfr.Timestamp')).toBe('-');
    });

    it('routes Timespan to formatDuration2Units', () => {
      expect(FormattingService.format(5_000_000, 'jdk.jfr.Timespan')).toBe('5ms');
    });

    it('routes Boolean to formatBoolean', () => {
      expect(FormattingService.format(null, 'Boolean')).toBe('-');
      expect(FormattingService.format(true, 'Boolean')).toBe(true);
    });

    it('returns "-" for null with unknown type', () => {
      expect(FormattingService.format(null, 'SomeUnknown')).toBe('-');
    });

    it('returns raw value for unknown type with non-null', () => {
      expect(FormattingService.format('hello', 'SomeUnknown')).toBe('hello');
    });
  });

  describe('formatDurationFromMillis', () => {
    it('returns em-dash when start is falsy', () => {
      expect(FormattingService.formatDurationFromMillis(0, 1000)).toBe('\u2014');
    });

    it('returns em-dash when end is null/undefined', () => {
      expect(FormattingService.formatDurationFromMillis(1000, null)).toBe('\u2014');
      expect(FormattingService.formatDurationFromMillis(1000, undefined)).toBe('\u2014');
    });

    it('returns em-dash when duration is zero or negative', () => {
      expect(FormattingService.formatDurationFromMillis(1000, 1000)).toBe('\u2014');
      expect(FormattingService.formatDurationFromMillis(2000, 1000)).toBe('\u2014');
    });

    it('formats positive duration', () => {
      // 5000ms difference = 5s
      const result = FormattingService.formatDurationFromMillis(1000, 6000);
      expect(result).toBe('5s');
    });
  });

  describe('formatObjectId', () => {
    it('formats as hex with @ prefix', () => {
      expect(FormattingService.formatObjectId(255)).toBe('@ff');
      expect(FormattingService.formatObjectId(0)).toBe('@0');
      expect(FormattingService.formatObjectId(4096)).toBe('@1000');
    });
  });

  describe('formatObjectParams', () => {
    it('returns empty string for empty object', () => {
      expect(FormattingService.formatObjectParams({})).toBe('');
    });

    it('formats single entry', () => {
      expect(FormattingService.formatObjectParams({ key: 'value' })).toBe('key=value');
    });

    it('formats multiple entries', () => {
      const result = FormattingService.formatObjectParams({ a: '1', b: '2' });
      expect(result).toBe('a=1, b=2');
    });
  });

  describe('formatTimestampUTC', () => {
    it('returns "-" for falsy values', () => {
      expect(FormattingService.formatTimestampUTC(undefined)).toBe('-');
      expect(FormattingService.formatTimestampUTC(null)).toBe('-');
      expect(FormattingService.formatTimestampUTC(0)).toBe('-');
    });

    it('formats valid timestamp with UTC suffix', () => {
      const result = FormattingService.formatTimestampUTC(1705311000000);
      expect(result).toBe('2024-01-15 09:30:00 UTC');
    });
  });

  describe('formatDurationInMillis2Units', () => {
    it('converts millis to nanos and formats', () => {
      // 5000ms = 5s
      expect(FormattingService.formatDurationInMillis2Units(5000)).toBe('5s');
    });
  });
});

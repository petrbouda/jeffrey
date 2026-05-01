import Utils from './Utils';

describe('Utils', () => {
  describe('capitalize', () => {
    it('capitalizes first letter', () => {
      expect(Utils.capitalize('hello')).toBe('Hello');
    });

    it('handles single character', () => {
      expect(Utils.capitalize('a')).toBe('A');
    });

    it('handles empty string', () => {
      expect(Utils.capitalize('')).toBe('');
    });

    it('preserves already capitalized', () => {
      expect(Utils.capitalize('Hello')).toBe('Hello');
    });
  });

  describe('parseBoolean', () => {
    it('returns true for boolean true', () => {
      expect(Utils.parseBoolean(true)).toBe(true);
    });

    it('returns true for string "true"', () => {
      expect(Utils.parseBoolean('true')).toBe(true);
    });

    it('returns false for boolean false', () => {
      expect(Utils.parseBoolean(false)).toBe(false);
    });

    it('returns false for string "false"', () => {
      expect(Utils.parseBoolean('false')).toBe(false);
    });

    it('returns false for null/undefined', () => {
      expect(Utils.parseBoolean(null)).toBe(false);
      expect(Utils.parseBoolean(undefined)).toBe(false);
    });

    it('returns false for random string', () => {
      expect(Utils.parseBoolean('yes')).toBe(false);
    });
  });

  describe('isBlank / isNotBlank', () => {
    it('isBlank returns true for null/undefined', () => {
      expect(Utils.isBlank(null)).toBe(true);
      expect(Utils.isBlank(undefined)).toBe(true);
    });

    it('isBlank returns true for empty/whitespace strings', () => {
      expect(Utils.isBlank('')).toBe(true);
      expect(Utils.isBlank('   ')).toBe(true);
    });

    it('isBlank returns false for non-empty strings', () => {
      expect(Utils.isBlank('hello')).toBe(false);
    });

    it('isNotBlank is inverse of isBlank', () => {
      expect(Utils.isNotBlank('hello')).toBe(true);
      expect(Utils.isNotBlank('')).toBe(false);
      expect(Utils.isNotBlank(null)).toBe(false);
    });
  });

  describe('isPositiveNumber', () => {
    it('returns true for positive number', () => {
      expect(Utils.isPositiveNumber(5)).toBe(true);
    });

    it('returns false for zero', () => {
      expect(Utils.isPositiveNumber(0)).toBe(false);
    });

    it('returns false for negative number', () => {
      expect(Utils.isPositiveNumber(-1)).toBe(false);
    });

    it('returns true for positive string number', () => {
      expect(Utils.isPositiveNumber('5')).toBe(true);
    });

    it('returns false for non-numeric string', () => {
      expect(Utils.isPositiveNumber('abc')).toBe(false);
    });

    it('returns false for null', () => {
      expect(Utils.isPositiveNumber(null)).toBe(false);
    });
  });

  describe('toTimeRange', () => {
    it('converts seconds and millis arrays to TimeRange', () => {
      const result = Utils.toTimeRange([10, 500], [20, 750], true);
      expect(result.start).toBe(10500); // 10*1000 + 500
      expect(result.end).toBe(20750); // 20*1000 + 750
      expect(result.absoluteTime).toBe(true);
    });

    it('handles zero values', () => {
      const result = Utils.toTimeRange([0, 0], [0, 0], false);
      expect(result.start).toBe(0);
      expect(result.end).toBe(0);
      expect(result.absoluteTime).toBe(false);
    });
  });

  describe('formatEventSource', () => {
    it('formats ASYNC_PROFILER', () => {
      expect(Utils.formatEventSource('ASYNC_PROFILER')).toBe('Async Profiler');
    });

    it('formats HEAP_DUMP', () => {
      expect(Utils.formatEventSource('HEAP_DUMP')).toBe('Heap Dump');
    });

    it('formats UNKNOWN', () => {
      expect(Utils.formatEventSource('UNKNOWN')).toBe('Unknown');
    });

    it('returns raw value for unrecognized source', () => {
      expect(Utils.formatEventSource('JDK')).toBe('JDK');
    });
  });

  describe('formatFileType', () => {
    it('formats JFR_LZ4', () => {
      expect(Utils.formatFileType('JFR_LZ4')).toBe('JFR (LZ4)');
    });

    it('formats PERF_COUNTERS', () => {
      expect(Utils.formatFileType('PERF_COUNTERS')).toBe('Perf Counters');
    });

    it('formats HEAP_DUMP_GZ', () => {
      expect(Utils.formatFileType('HEAP_DUMP_GZ')).toBe('Heap Dump (GZ)');
    });

    it('formats HEAP_DUMP', () => {
      expect(Utils.formatFileType('HEAP_DUMP')).toBe('Heap Dump');
    });

    it('formats JVM_LOG', () => {
      expect(Utils.formatFileType('JVM_LOG')).toBe('JVM Log');
    });

    it('returns raw value for unrecognized type', () => {
      expect(Utils.formatFileType('CUSTOM')).toBe('CUSTOM');
    });
  });
});

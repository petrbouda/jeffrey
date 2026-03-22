import EventTypes from './EventTypes';

describe('EventTypes', () => {

  describe('individual event type checks', () => {
    it('isObjectAllocationInNewTLAB', () => {
      expect(EventTypes.isObjectAllocationInNewTLAB('jdk.ObjectAllocationInNewTLAB')).toBe(true);
      expect(EventTypes.isObjectAllocationInNewTLAB('jdk.ExecutionSample')).toBe(false);
    });

    it('isObjectAllocationOutsideTLAB', () => {
      expect(EventTypes.isObjectAllocationOutsideTLAB('jdk.ObjectAllocationOutsideTLAB')).toBe(true);
      expect(EventTypes.isObjectAllocationOutsideTLAB('other')).toBe(false);
    });

    it('isObjectAllocationSample', () => {
      expect(EventTypes.isObjectAllocationSample('jdk.ObjectAllocationSample')).toBe(true);
      expect(EventTypes.isObjectAllocationSample('other')).toBe(false);
    });

    it('isJavaMonitorEnter', () => {
      expect(EventTypes.isJavaMonitorEnter('jdk.JavaMonitorEnter')).toBe(true);
      expect(EventTypes.isJavaMonitorEnter('other')).toBe(false);
    });

    it('isJavaMonitorWait', () => {
      expect(EventTypes.isJavaMonitorWait('jdk.JavaMonitorWait')).toBe(true);
      expect(EventTypes.isJavaMonitorWait('other')).toBe(false);
    });

    it('isThreadPark', () => {
      expect(EventTypes.isThreadPark('jdk.ThreadPark')).toBe(true);
      expect(EventTypes.isThreadPark('other')).toBe(false);
    });

    it('isWallClock', () => {
      expect(EventTypes.isWallClock('profiler.WallClockSample')).toBe(true);
      expect(EventTypes.isWallClock('other')).toBe(false);
    });

    it('isExecutionEventType', () => {
      expect(EventTypes.isExecutionEventType('jdk.ExecutionSample')).toBe(true);
      expect(EventTypes.isExecutionEventType('other')).toBe(false);
    });

    it('isMethodTraceEventType', () => {
      expect(EventTypes.isMethodTraceEventType('jdk.MethodTrace')).toBe(true);
      expect(EventTypes.isMethodTraceEventType('other')).toBe(false);
    });

    it('isMallocAllocationEventType', () => {
      expect(EventTypes.isMallocAllocationEventType('profiler.Malloc')).toBe(true);
      expect(EventTypes.isMallocAllocationEventType('other')).toBe(false);
    });

    it('isNativeLeakEventType', () => {
      expect(EventTypes.isNativeLeakEventType('jeffrey.NativeLeak')).toBe(true);
      expect(EventTypes.isNativeLeakEventType('other')).toBe(false);
    });
  });

  describe('composite checks', () => {
    it('isAllocationEventType matches all allocation sub-types', () => {
      expect(EventTypes.isAllocationEventType('jdk.ObjectAllocationInNewTLAB')).toBe(true);
      expect(EventTypes.isAllocationEventType('jdk.ObjectAllocationOutsideTLAB')).toBe(true);
      expect(EventTypes.isAllocationEventType('jdk.ObjectAllocationSample')).toBe(true);
      expect(EventTypes.isAllocationEventType('jdk.ExecutionSample')).toBe(false);
    });

    it('isBlockingEventType matches all blocking sub-types', () => {
      expect(EventTypes.isBlockingEventType('jdk.JavaMonitorEnter')).toBe(true);
      expect(EventTypes.isBlockingEventType('jdk.JavaMonitorWait')).toBe(true);
      expect(EventTypes.isBlockingEventType('jdk.ThreadPark')).toBe(true);
      expect(EventTypes.isBlockingEventType('jdk.ExecutionSample')).toBe(false);
    });

    it('isDifferential matches blocking types', () => {
      expect(EventTypes.isDifferential('jdk.JavaMonitorEnter')).toBe(true);
      expect(EventTypes.isDifferential('jdk.JavaMonitorWait')).toBe(true);
      expect(EventTypes.isDifferential('jdk.ThreadPark')).toBe(true);
      expect(EventTypes.isDifferential('jdk.ExecutionSample')).toBe(false);
    });
  });

  describe('getSapDocumentationUrl', () => {
    it('returns URL for jdk-prefixed event', () => {
      const url = EventTypes.getSapDocumentationUrl('jdk.ExecutionSample');
      expect(url).toBe('https://sap.github.io/jfrevents/25.html?search=ExecutionSample');
    });

    it('returns null for non-jdk event', () => {
      expect(EventTypes.getSapDocumentationUrl('profiler.WallClockSample')).toBeNull();
    });

    it('returns null for null/empty', () => {
      expect(EventTypes.getSapDocumentationUrl('')).toBeNull();
      expect(EventTypes.getSapDocumentationUrl(null as any)).toBeNull();
    });
  });
});

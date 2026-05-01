import JavaMethodParser from './JavaMethodParser';

describe('JavaMethodParser', () => {
  describe('parse', () => {
    it('returns null for empty/null input', () => {
      expect(JavaMethodParser.parse('')).toBeNull();
      expect(JavaMethodParser.parse(null as any)).toBeNull();
    });

    it('parses standard method with package, class, and method', () => {
      const result = JavaMethodParser.parse('com.example.MyClass.myMethod(String, int)');
      expect(result).toEqual({
        packageName: 'com.example',
        className: 'MyClass',
        methodName: 'myMethod(String, int)',
        shortForm: 'MyClass.myMethod(String, int)'
      });
    });

    it('parses method with hash separator', () => {
      const result = JavaMethodParser.parse('org.apache.MyClass#process');
      expect(result).toEqual({
        packageName: 'org.apache',
        className: 'MyClass',
        methodName: 'process',
        shortForm: 'MyClass.process'
      });
    });

    it('parses inner classes', () => {
      const result = JavaMethodParser.parse('com.example.Outer.Inner.method');
      expect(result).toEqual({
        packageName: 'com.example',
        className: 'Outer.Inner',
        methodName: 'method',
        shortForm: 'Outer.Inner.method'
      });
    });

    it('parses method without package (single class)', () => {
      const result = JavaMethodParser.parse('MyClass.method');
      expect(result).toEqual({
        packageName: null,
        className: 'MyClass',
        methodName: 'method',
        shortForm: 'MyClass.method'
      });
    });

    it('handles single word (no dots) as full method name', () => {
      const result = JavaMethodParser.parse('something');
      expect(result).toEqual({
        packageName: null,
        className: '',
        methodName: 'something',
        shortForm: 'something'
      });
    });

    it('parses lambda frames', () => {
      const result = JavaMethodParser.parse('com.example.Foo$$Lambda.0x123.run');
      expect(result).not.toBeNull();
      expect(result!.packageName).toBe('com.example');
      expect(result!.methodName).toBe('run');
    });

    it('handles method without arguments', () => {
      const result = JavaMethodParser.parse('com.example.MyClass.doWork');
      expect(result).toEqual({
        packageName: 'com.example',
        className: 'MyClass',
        methodName: 'doWork',
        shortForm: 'MyClass.doWork'
      });
    });

    it('handles all-lowercase parts (assumes last is class)', () => {
      const result = JavaMethodParser.parse('org.example.utils.helper.run');
      expect(result).toEqual({
        packageName: 'org.example.utils',
        className: 'helper',
        methodName: 'run',
        shortForm: 'helper.run'
      });
    });
  });

  describe('isJavaFrame', () => {
    it('returns true for known Java frame types', () => {
      expect(JavaMethodParser.isJavaFrame('JIT_COMPILED')).toBe(true);
      expect(JavaMethodParser.isJavaFrame('INLINED')).toBe(true);
      expect(JavaMethodParser.isJavaFrame('INTERPRETED')).toBe(true);
      expect(JavaMethodParser.isJavaFrame('C1_COMPILED')).toBe(true);
    });

    it('returns false for non-Java frame types', () => {
      expect(JavaMethodParser.isJavaFrame('NATIVE')).toBe(false);
      expect(JavaMethodParser.isJavaFrame('KERNEL')).toBe(false);
      expect(JavaMethodParser.isJavaFrame('CPP')).toBe(false);
      expect(JavaMethodParser.isJavaFrame('')).toBe(false);
    });
  });
});

const JDK_PACKAGE_PREFIXES = ['java.', 'jdk.', 'sun.'];
const JDK_PACKAGE_ROOTS = new Set(['java', 'jdk', 'sun']);

export function isJdkPackage(pkg: string | null | undefined): boolean {
  if (!pkg) return false;
  if (JDK_PACKAGE_ROOTS.has(pkg)) return true;
  return JDK_PACKAGE_PREFIXES.some((prefix) => pkg.startsWith(prefix));
}

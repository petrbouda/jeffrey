export type LeakHintSeverity = 'HIGH' | 'MEDIUM' | 'LOW';

export default interface LeakHintFinding {
  severity: LeakHintSeverity;
  ruleId: string;
  title: string;
  details: string;
}

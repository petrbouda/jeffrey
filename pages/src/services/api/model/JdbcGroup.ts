import JdbcStatementNameStats from "@/services/profile/custom/jdbc/JdbcStatementNameStats.ts";

export default class JdbcGroup {
    constructor(
        public group: string,
        public count: number,
        public totalExecutionTime: number,
        public totalRowsProcessed: number,
        public maxExecutionTime: number,
        public p99ExecutionTime: number,
        public p95ExecutionTime: number,
        public errorCount: number,
        public statementNames: JdbcStatementNameStats[],
    ) {}
}

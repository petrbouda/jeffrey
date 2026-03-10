/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package pbouda.jeffrey.shared.turso;

import java.sql.*;

/**
 * Minimal {@link DatabaseMetaData} implementation for libsql.
 *
 * <p>Reports product name as {@code "SQLite"} for Flyway compatibility —
 * Flyway uses the product name to select the correct SQL dialect.
 */
public class LibSqlDatabaseMetaData implements DatabaseMetaData {

    private final LibSqlConnection connection;

    LibSqlDatabaseMetaData(LibSqlConnection connection) {
        this.connection = connection;
    }

    @Override
    public String getDatabaseProductName() {
        return "SQLite";
    }

    @Override
    public String getDatabaseProductVersion() {
        return "3.45.0-libsql";
    }

    @Override
    public int getDatabaseMajorVersion() {
        return 3;
    }

    @Override
    public int getDatabaseMinorVersion() {
        return 45;
    }

    @Override
    public String getDriverName() {
        return "LibSQL Panama JDBC";
    }

    @Override
    public String getDriverVersion() {
        return "1.0";
    }

    @Override
    public int getDriverMajorVersion() {
        return 1;
    }

    @Override
    public int getDriverMinorVersion() {
        return 0;
    }

    @Override
    public int getJDBCMajorVersion() {
        return 4;
    }

    @Override
    public int getJDBCMinorVersion() {
        return 2;
    }

    @Override
    public String getURL() {
        return "jdbc:libsql:embedded";
    }

    @Override
    public String getUserName() {
        return "";
    }

    @Override
    public Connection getConnection() {
        return connection;
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public String getIdentifierQuoteString() {
        return "\"";
    }

    @Override
    public String getCatalogSeparator() {
        return ".";
    }

    @Override
    public String getCatalogTerm() {
        return "catalog";
    }

    @Override
    public String getSchemaTerm() {
        return "schema";
    }

    @Override
    public String getProcedureTerm() {
        return "procedure";
    }

    @Override
    public boolean supportsMixedCaseIdentifiers() {
        return true;
    }

    @Override
    public boolean supportsMixedCaseQuotedIdentifiers() {
        return true;
    }

    @Override
    public boolean storesUpperCaseIdentifiers() {
        return false;
    }

    @Override
    public boolean storesLowerCaseIdentifiers() {
        return false;
    }

    @Override
    public boolean storesMixedCaseIdentifiers() {
        return true;
    }

    @Override
    public boolean storesUpperCaseQuotedIdentifiers() {
        return false;
    }

    @Override
    public boolean storesLowerCaseQuotedIdentifiers() {
        return false;
    }

    @Override
    public boolean storesMixedCaseQuotedIdentifiers() {
        return true;
    }

    @Override
    public boolean supportsAlterTableWithAddColumn() {
        return true;
    }

    @Override
    public boolean supportsAlterTableWithDropColumn() {
        return true;
    }

    @Override
    public boolean supportsTransactions() {
        return true;
    }

    @Override
    public int getDefaultTransactionIsolation() {
        return Connection.TRANSACTION_SERIALIZABLE;
    }

    @Override
    public boolean supportsTransactionIsolationLevel(int level) {
        return level == Connection.TRANSACTION_SERIALIZABLE;
    }

    @Override
    public boolean supportsDataDefinitionAndDataManipulationTransactions() {
        return true;
    }

    @Override
    public boolean supportsDataManipulationTransactionsOnly() {
        return false;
    }

    @Override
    public boolean dataDefinitionCausesTransactionCommit() {
        return false;
    }

    @Override
    public boolean dataDefinitionIgnoredInTransactions() {
        return false;
    }

    @Override
    public ResultSet getTables(String catalog, String schemaPattern, String tableNamePattern, String[] types)
            throws SQLException {
        // Query sqlite_master for tables — this is what Flyway uses
        String sql = "SELECT '' AS TABLE_CAT, '' AS TABLE_SCHEM, name AS TABLE_NAME, "
                + "type AS TABLE_TYPE, '' AS REMARKS "
                + "FROM sqlite_master WHERE type IN ('table', 'view') ORDER BY name";
        return connection.prepareStatement(sql).executeQuery();
    }

    @Override
    public ResultSet getSchemas() throws SQLException {
        String sql = "SELECT '' AS TABLE_SCHEM, '' AS TABLE_CATALOG LIMIT 0";
        return connection.prepareStatement(sql).executeQuery();
    }

    @Override
    public ResultSet getCatalogs() throws SQLException {
        String sql = "SELECT '' AS TABLE_CAT LIMIT 0";
        return connection.prepareStatement(sql).executeQuery();
    }

    @Override
    public ResultSet getColumns(String catalog, String schemaPattern, String tableNamePattern,
                                String columnNamePattern) throws SQLException {
        // Flyway uses this for column inspection
        String sql = "SELECT '' AS TABLE_CAT, '' AS TABLE_SCHEM, '' AS TABLE_NAME, "
                + "'' AS COLUMN_NAME, 0 AS DATA_TYPE, '' AS TYPE_NAME, 0 AS COLUMN_SIZE, "
                + "0 AS BUFFER_LENGTH, 0 AS DECIMAL_DIGITS, 0 AS NUM_PREC_RADIX, "
                + "1 AS NULLABLE, '' AS REMARKS, '' AS COLUMN_DEF, 0 AS SQL_DATA_TYPE, "
                + "0 AS SQL_DATETIME_SUB, 0 AS CHAR_OCTET_LENGTH, 0 AS ORDINAL_POSITION, "
                + "'YES' AS IS_NULLABLE LIMIT 0";
        return connection.prepareStatement(sql).executeQuery();
    }

    @Override
    public ResultSet getPrimaryKeys(String catalog, String schema, String table) throws SQLException {
        String sql = "SELECT '' AS TABLE_CAT, '' AS TABLE_SCHEM, '' AS TABLE_NAME, "
                + "'' AS COLUMN_NAME, 0 AS KEY_SEQ, '' AS PK_NAME LIMIT 0";
        return connection.prepareStatement(sql).executeQuery();
    }

    @Override
    public ResultSet getTypeInfo() throws SQLException {
        String sql = "SELECT '' AS TYPE_NAME, 0 AS DATA_TYPE, 0 AS PRECISION, '' AS LITERAL_PREFIX, "
                + "'' AS LITERAL_SUFFIX, '' AS CREATE_PARAMS, 1 AS NULLABLE, 0 AS CASE_SENSITIVE, "
                + "3 AS SEARCHABLE, 0 AS UNSIGNED_ATTRIBUTE, 0 AS FIXED_PREC_SCALE, "
                + "0 AS AUTO_INCREMENT, '' AS LOCAL_TYPE_NAME, 0 AS MINIMUM_SCALE, "
                + "0 AS MAXIMUM_SCALE, 0 AS SQL_DATA_TYPE, 0 AS SQL_DATETIME_SUB, "
                + "10 AS NUM_PREC_RADIX LIMIT 0";
        return connection.prepareStatement(sql).executeQuery();
    }

    // ──────────────────────────────────────────────────────────────────────
    // Capabilities (mostly defaults for SQLite)
    // ──────────────────────────────────────────────────────────────────────

    @Override public boolean allProceduresAreCallable() { return false; }
    @Override public boolean allTablesAreSelectable() { return true; }
    @Override public boolean nullsAreSortedHigh() { return false; }
    @Override public boolean nullsAreSortedLow() { return true; }
    @Override public boolean nullsAreSortedAtStart() { return false; }
    @Override public boolean nullsAreSortedAtEnd() { return false; }
    @Override public String getSQLKeywords() { return ""; }
    @Override public String getNumericFunctions() { return ""; }
    @Override public String getStringFunctions() { return ""; }
    @Override public String getSystemFunctions() { return ""; }
    @Override public String getTimeDateFunctions() { return ""; }
    @Override public String getSearchStringEscape() { return "\\"; }
    @Override public String getExtraNameCharacters() { return ""; }
    @Override public boolean supportsColumnAliasing() { return true; }
    @Override public boolean nullPlusNonNullIsNull() { return true; }
    @Override public boolean supportsConvert() { return false; }
    @Override public boolean supportsConvert(int fromType, int toType) { return false; }
    @Override public boolean supportsTableCorrelationNames() { return true; }
    @Override public boolean supportsDifferentTableCorrelationNames() { return false; }
    @Override public boolean supportsExpressionsInOrderBy() { return true; }
    @Override public boolean supportsOrderByUnrelated() { return true; }
    @Override public boolean supportsGroupBy() { return true; }
    @Override public boolean supportsGroupByUnrelated() { return true; }
    @Override public boolean supportsGroupByBeyondSelect() { return true; }
    @Override public boolean supportsLikeEscapeClause() { return true; }
    @Override public boolean supportsMultipleResultSets() { return false; }
    @Override public boolean supportsMultipleTransactions() { return true; }
    @Override public boolean supportsNonNullableColumns() { return true; }
    @Override public boolean supportsMinimumSQLGrammar() { return true; }
    @Override public boolean supportsCoreSQLGrammar() { return true; }
    @Override public boolean supportsExtendedSQLGrammar() { return false; }
    @Override public boolean supportsANSI92EntryLevelSQL() { return true; }
    @Override public boolean supportsANSI92IntermediateSQL() { return false; }
    @Override public boolean supportsANSI92FullSQL() { return false; }
    @Override public boolean supportsIntegrityEnhancementFacility() { return false; }
    @Override public boolean supportsOuterJoins() { return true; }
    @Override public boolean supportsFullOuterJoins() { return false; }
    @Override public boolean supportsLimitedOuterJoins() { return true; }
    @Override public boolean isCatalogAtStart() { return true; }
    @Override public boolean supportsSchemasInDataManipulation() { return false; }
    @Override public boolean supportsSchemasInProcedureCalls() { return false; }
    @Override public boolean supportsSchemasInTableDefinitions() { return false; }
    @Override public boolean supportsSchemasInIndexDefinitions() { return false; }
    @Override public boolean supportsSchemasInPrivilegeDefinitions() { return false; }
    @Override public boolean supportsCatalogsInDataManipulation() { return false; }
    @Override public boolean supportsCatalogsInProcedureCalls() { return false; }
    @Override public boolean supportsCatalogsInTableDefinitions() { return false; }
    @Override public boolean supportsCatalogsInIndexDefinitions() { return false; }
    @Override public boolean supportsCatalogsInPrivilegeDefinitions() { return false; }
    @Override public boolean supportsPositionedDelete() { return false; }
    @Override public boolean supportsPositionedUpdate() { return false; }
    @Override public boolean supportsSelectForUpdate() { return false; }
    @Override public boolean supportsStoredProcedures() { return false; }
    @Override public boolean supportsSubqueriesInComparisons() { return true; }
    @Override public boolean supportsSubqueriesInExists() { return true; }
    @Override public boolean supportsSubqueriesInIns() { return true; }
    @Override public boolean supportsSubqueriesInQuantifieds() { return true; }
    @Override public boolean supportsCorrelatedSubqueries() { return true; }
    @Override public boolean supportsUnion() { return true; }
    @Override public boolean supportsUnionAll() { return true; }
    @Override public boolean supportsOpenCursorsAcrossCommit() { return false; }
    @Override public boolean supportsOpenCursorsAcrossRollback() { return false; }
    @Override public boolean supportsOpenStatementsAcrossCommit() { return false; }
    @Override public boolean supportsOpenStatementsAcrossRollback() { return false; }
    @Override public int getMaxBinaryLiteralLength() { return 0; }
    @Override public int getMaxCharLiteralLength() { return 0; }
    @Override public int getMaxColumnNameLength() { return 0; }
    @Override public int getMaxColumnsInGroupBy() { return 0; }
    @Override public int getMaxColumnsInIndex() { return 0; }
    @Override public int getMaxColumnsInOrderBy() { return 0; }
    @Override public int getMaxColumnsInSelect() { return 0; }
    @Override public int getMaxColumnsInTable() { return 0; }
    @Override public int getMaxConnections() { return 0; }
    @Override public int getMaxCursorNameLength() { return 0; }
    @Override public int getMaxIndexLength() { return 0; }
    @Override public int getMaxSchemaNameLength() { return 0; }
    @Override public int getMaxProcedureNameLength() { return 0; }
    @Override public int getMaxCatalogNameLength() { return 0; }
    @Override public int getMaxRowSize() { return 0; }
    @Override public boolean doesMaxRowSizeIncludeBlobs() { return true; }
    @Override public int getMaxStatementLength() { return 0; }
    @Override public int getMaxStatements() { return 0; }
    @Override public int getMaxTableNameLength() { return 0; }
    @Override public int getMaxTablesInSelect() { return 0; }
    @Override public int getMaxUserNameLength() { return 0; }
    @Override public boolean supportsMultipleOpenResults() { return false; }
    @Override public boolean supportsGetGeneratedKeys() { return false; }
    @Override public boolean supportsResultSetHoldability(int holdability) { return holdability == ResultSet.HOLD_CURSORS_OVER_COMMIT; }
    @Override public int getResultSetHoldability() { return ResultSet.HOLD_CURSORS_OVER_COMMIT; }
    @Override public int getSQLStateType() { return sqlStateSQL; }
    @Override public boolean locatorsUpdateCopy() { return false; }
    @Override public boolean supportsStatementPooling() { return false; }
    @Override public RowIdLifetime getRowIdLifetime() { return RowIdLifetime.ROWID_VALID_FOREVER; }
    @Override public boolean supportsStoredFunctionsUsingCallSyntax() { return false; }
    @Override public boolean autoCommitFailureClosesAllResultSets() { return false; }
    @Override public boolean generatedKeyAlwaysReturned() { return false; }
    @Override public boolean supportsResultSetType(int type) { return type == ResultSet.TYPE_FORWARD_ONLY; }
    @Override public boolean supportsResultSetConcurrency(int type, int concurrency) { return type == ResultSet.TYPE_FORWARD_ONLY && concurrency == ResultSet.CONCUR_READ_ONLY; }
    @Override public boolean ownUpdatesAreVisible(int type) { return false; }
    @Override public boolean ownDeletesAreVisible(int type) { return false; }
    @Override public boolean ownInsertsAreVisible(int type) { return false; }
    @Override public boolean othersUpdatesAreVisible(int type) { return false; }
    @Override public boolean othersDeletesAreVisible(int type) { return false; }
    @Override public boolean othersInsertsAreVisible(int type) { return false; }
    @Override public boolean updatesAreDetected(int type) { return false; }
    @Override public boolean deletesAreDetected(int type) { return false; }
    @Override public boolean insertsAreDetected(int type) { return false; }
    @Override public boolean supportsBatchUpdates() { return true; }
    @Override public boolean supportsSavepoints() { return false; }
    @Override public boolean supportsNamedParameters() { return true; }

    // ──────────────────────────────────────────────────────────────────────
    // Methods returning empty ResultSets
    // ──────────────────────────────────────────────────────────────────────

    @Override public ResultSet getProcedures(String catalog, String schemaPattern, String procedureNamePattern) throws SQLException {
        return connection.prepareStatement("SELECT 1 LIMIT 0").executeQuery();
    }
    @Override public ResultSet getProcedureColumns(String catalog, String schemaPattern, String procedureNamePattern, String columnNamePattern) throws SQLException {
        return connection.prepareStatement("SELECT 1 LIMIT 0").executeQuery();
    }
    @Override public ResultSet getTableTypes() throws SQLException {
        return connection.prepareStatement("SELECT 'TABLE' AS TABLE_TYPE UNION SELECT 'VIEW'").executeQuery();
    }
    @Override public ResultSet getColumnPrivileges(String catalog, String schema, String table, String columnNamePattern) throws SQLException {
        return connection.prepareStatement("SELECT 1 LIMIT 0").executeQuery();
    }
    @Override public ResultSet getTablePrivileges(String catalog, String schemaPattern, String tableNamePattern) throws SQLException {
        return connection.prepareStatement("SELECT 1 LIMIT 0").executeQuery();
    }
    @Override public ResultSet getBestRowIdentifier(String catalog, String schema, String table, int scope, boolean nullable) throws SQLException {
        return connection.prepareStatement("SELECT 1 LIMIT 0").executeQuery();
    }
    @Override public ResultSet getVersionColumns(String catalog, String schema, String table) throws SQLException {
        return connection.prepareStatement("SELECT 1 LIMIT 0").executeQuery();
    }
    @Override public ResultSet getImportedKeys(String catalog, String schema, String table) throws SQLException {
        return connection.prepareStatement("SELECT 1 LIMIT 0").executeQuery();
    }
    @Override public ResultSet getExportedKeys(String catalog, String schema, String table) throws SQLException {
        return connection.prepareStatement("SELECT 1 LIMIT 0").executeQuery();
    }
    @Override public ResultSet getCrossReference(String parentCatalog, String parentSchema, String parentTable, String foreignCatalog, String foreignSchema, String foreignTable) throws SQLException {
        return connection.prepareStatement("SELECT 1 LIMIT 0").executeQuery();
    }
    @Override public ResultSet getIndexInfo(String catalog, String schema, String table, boolean unique, boolean approximate) throws SQLException {
        return connection.prepareStatement("SELECT 1 LIMIT 0").executeQuery();
    }
    @Override public ResultSet getUDTs(String catalog, String schemaPattern, String typeNamePattern, int[] types) throws SQLException {
        return connection.prepareStatement("SELECT 1 LIMIT 0").executeQuery();
    }
    @Override public ResultSet getSuperTypes(String catalog, String schemaPattern, String typeNamePattern) throws SQLException {
        return connection.prepareStatement("SELECT 1 LIMIT 0").executeQuery();
    }
    @Override public ResultSet getSuperTables(String catalog, String schemaPattern, String tableNamePattern) throws SQLException {
        return connection.prepareStatement("SELECT 1 LIMIT 0").executeQuery();
    }
    @Override public ResultSet getAttributes(String catalog, String schemaPattern, String typeNamePattern, String attributeNamePattern) throws SQLException {
        return connection.prepareStatement("SELECT 1 LIMIT 0").executeQuery();
    }
    @Override public ResultSet getClientInfoProperties() throws SQLException {
        return connection.prepareStatement("SELECT 1 LIMIT 0").executeQuery();
    }
    @Override public ResultSet getFunctions(String catalog, String schemaPattern, String functionNamePattern) throws SQLException {
        return connection.prepareStatement("SELECT 1 LIMIT 0").executeQuery();
    }
    @Override public ResultSet getFunctionColumns(String catalog, String schemaPattern, String functionNamePattern, String columnNamePattern) throws SQLException {
        return connection.prepareStatement("SELECT 1 LIMIT 0").executeQuery();
    }
    @Override public ResultSet getPseudoColumns(String catalog, String schemaPattern, String tableNamePattern, String columnNamePattern) throws SQLException {
        return connection.prepareStatement("SELECT 1 LIMIT 0").executeQuery();
    }
    @Override public long getMaxLogicalLobSize() { return 0; }
    @Override public boolean supportsRefCursors() { return false; }
    @Override public boolean supportsSharding() { return false; }
    @Override public boolean usesLocalFiles() { return true; }
    @Override public boolean usesLocalFilePerTable() { return false; }

    @Override
    public ResultSet getSchemas(String catalog, String schemaPattern) throws SQLException {
        return getSchemas();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isAssignableFrom(getClass())) {
            return iface.cast(this);
        }
        throw new SQLException("Cannot unwrap to " + iface.getName());
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        return iface.isAssignableFrom(getClass());
    }
}

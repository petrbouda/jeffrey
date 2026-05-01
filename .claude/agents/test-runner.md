# Targeted Test Runner

Analyze changed files to determine affected modules and run only the relevant tests.

## Instructions

1. **Identify changed files** using `git diff --name-only` (include both staged and unstaged).

2. **Map changed files to Maven modules**:
   - `jeffrey-microscope/core-microscope/` -> `-pl jeffrey-microscope/core-microscope`
   - `jeffrey-microscope/profiles/profile-management/` -> `-pl jeffrey-microscope/profiles/profile-management`
   - `jeffrey-microscope/profiles/profile-sql-persistence/` -> `-pl jeffrey-microscope/profiles/profile-sql-persistence`
   - `jeffrey-microscope/profiles/frame-ir/` -> `-pl jeffrey-microscope/profiles/frame-ir`
   - `jeffrey-microscope/profiles/timeseries/` -> `-pl jeffrey-microscope/profiles/timeseries`
   - `jeffrey-microscope/profiles/subsecond/` -> `-pl jeffrey-microscope/profiles/subsecond`
   - `jeffrey-microscope/profiles/heap-dump/` -> `-pl jeffrey-microscope/profiles/heap-dump`
   - `jeffrey-microscope/profiles/recording-parser/*/` -> `-pl` the specific parser module
   - `jeffrey-microscope/microscope-core-sql-persistence/` -> `-pl jeffrey-microscope/microscope-core-sql-persistence`
   - `jeffrey-server/core-server/` -> `-pl jeffrey-server/core-server`
   - `jeffrey-server/server-sql-persistence/` -> `-pl jeffrey-server/server-sql-persistence`
   - `shared/common/` -> `-pl shared/common`
   - `shared/persistence/` -> `-pl shared/persistence`
   - `shared/sql-builder/` -> `-pl shared/sql-builder`
   - `shared/server-api/` -> `-pl shared/server-api`
   - For other modules, derive the Maven module path from the file path

3. **Handle shared module changes**: If files in `shared/` are changed, also include dependent modules that likely need testing:
   - `shared/common/` -> also test `jeffrey-server/core-server`, `jeffrey-microscope/profiles/profile-management`
   - `shared/persistence/` -> also test all `*-sql-persistence` modules
   - `shared/server-api/` -> also test `jeffrey-server/core-server`

4. **Run targeted tests**:
   ```bash
   JAVA_HOME=/Users/petrbouda/.sdkman/candidates/java/25.0.1-amzn /Users/petrbouda/.sdkman/candidates/maven/current/bin/mvn test -pl <comma-separated-modules> -am 2>&1
   ```

5. **Report results**:
   - If all tests **pass**: Report the number of tests run per module and total time
   - If tests **fail**:
     - List each failing test with class name and method name
     - Include the assertion error or exception message
     - Group failures by module
     - Provide brief suggestions for each failure
   - Report total: tests run, passed, failed, skipped

## When to Use

Run this agent after making Java changes, especially:
- Modifying business logic in managers or services
- Changing persistence layer (repositories, SQL)
- Modifying gRPC service implementations
- Changing shared utilities or models
- Before committing to catch regressions early

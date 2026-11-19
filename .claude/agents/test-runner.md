---
name: test-runner
description: Determines which Maven modules and frontend packages a change affects and runs only their tests. Use to verify a change without running the whole reactor.
tools: Read, Grep, Glob, Bash
model: inherit
---

# Targeted Test Runner

Analyze changed files to determine affected modules and run only the relevant tests.

## Instructions

1. **Identify changed files** using `git diff --name-only` (include both staged and unstaged).

2. **Map changed files to Maven modules**:
   - `jeffrey-microscope/core-microscope/` -> `-pl jeffrey-microscope/core-microscope`
   - `volume-scheduler/` -> `-pl volume-scheduler`
   - `jeffrey-microscope/microscope-common/` -> `-pl jeffrey-microscope/microscope-common`
   - `shared/volume/` -> `-pl shared/volume`
   - For other modules, derive the Maven module path from the file path

3. **Handle shared module changes**: If files in `shared/` are changed, also include dependent modules that likely need testing:
   - `shared/volume/` -> also test `volume-scheduler`, `jeffrey-provisioner`, `jeffrey-microscope/microscope-common`, `jeffrey-microscope/connectors/volume-connector`, `jeffrey-microscope/connectors/azure-files-connector` (and `jeffrey-microscope/core-microscope` when `VolumeWriter` changes)

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
- Modifying the volume scheduler's jobs or the volume layout
- Changing shared utilities or models
- Before committing to catch regressions early

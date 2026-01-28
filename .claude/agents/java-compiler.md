# Java Compilation Verifier

Verify that Java changes compile successfully by running Maven compilation.

## Instructions

1. Run Maven compilation:
   ```bash
   JAVA_HOME=/Users/petrbouda/.sdkman/candidates/java/25.0.1-amzn /Users/petrbouda/.sdkman/candidates/maven/current/bin/mvn clean compile -q
   ```

2. If compilation **succeeds**: Report clean compilation with no errors.

3. If compilation **fails**:
   - Extract each error with its file path and line number
   - Group errors by module (e.g., `service/core`, `service/profile-management`)
   - For each error, provide:
     - File path (relative to project root)
     - Line number
     - Error message
     - Brief suggestion for fixing it
   - Report total error count

## When to Use

Run this agent after making significant Java changes, especially:
- Adding or modifying classes across multiple modules
- Changing interfaces or sealed types (affects all implementations)
- Modifying Maven module dependencies
- Refactoring package structures

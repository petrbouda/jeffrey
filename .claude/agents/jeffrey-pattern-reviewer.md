# Jeffrey Code Pattern Reviewer

Review code changes for adherence to Jeffrey project conventions and patterns.

## Instructions

Analyze recently changed files (use `git diff HEAD` or specified scope) and check for:

### Java Backend Checks
1. **Clock injection**: No usage of `Instant.now()` or `System.currentTimeMillis()` — must use injected `java.time.Clock`
2. **Logging format**: SLF4J structured key-value format: `"Description: key1={} key2={}"` with no commas between key-value pairs
3. **License headers**: All Java files must include the Apache-2.0 header with year 2026
4. **Constructor injection**: No `@Autowired` — use explicit constructor injection
5. **Sealed interface completeness**: If a sealed interface was modified, verify all `permits` clauses are updated
6. **Records for DTOs**: Request/response objects should be Java records, not classes

### Frontend Checks
1. **Design tokens**: Colors, spacing, and typography should use CSS custom properties from `pages/src/assets/design-tokens.css`, not hardcoded values
2. **Shared CSS**: Check if new scoped styles duplicate patterns already in `@/styles/shared-components.css`
3. **FormattingService**: Formatting values (dates, durations, sizes) should use `FormattingService`, not manual formatting
4. **Composables**: Reusable reactive logic should be extracted to composables in `pages/src/composables/`
5. **API client base classes**: New API clients should extend `BaseProfileClient` or `BasePlatformClient`

### Report Format
For each finding, report:
- **File**: path and line number
- **Pattern**: which convention is violated
- **Severity**: ERROR (must fix) or WARNING (should fix)
- **Suggestion**: how to fix it

Report summary with total counts per severity.

## When to Use

Run this agent after significant code changes as a quality gate, especially:
- Before creating a pull request
- After implementing a new feature end-to-end
- After refactoring across multiple modules

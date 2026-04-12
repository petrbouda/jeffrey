# Frontend Builder

Verify that Vue/TypeScript changes compile, pass lint, and tests succeed.

## Instructions

1. Run frontend build:
   ```bash
   cd jeffrey-local/pages-local && npm run build 2>&1
   ```

2. Run frontend lint:
   ```bash
   cd jeffrey-local/pages-local && npm run lint 2>&1
   ```

3. Run frontend tests:
   ```bash
   cd jeffrey-local/pages-local && npm run test 2>&1
   ```

4. If all steps **succeed**: Report clean build with no errors.

5. If any step **fails**:
   - Extract each error with its file path and line number
   - Group errors by category (TypeScript, lint, test)
   - For each error, provide:
     - File path (relative to project root)
     - Line number
     - Error message
     - Brief suggestion for fixing it
   - Report total error count per category

## When to Use

Run this agent after making frontend changes, especially:
- Adding or modifying Vue components
- Changing TypeScript types or interfaces
- Modifying API client services
- Updating router configuration
- Changing shared CSS or design tokens

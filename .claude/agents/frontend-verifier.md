# Frontend Build + Lint Verifier

Verify that frontend changes compile and pass linting.

## Instructions

1. Run frontend build:
   ```bash
   cd pages && npm run build
   ```

2. Run frontend linting:
   ```bash
   cd pages && npm run lint
   ```

3. If both **succeed**: Report clean build with no errors.

4. If either **fails**:
   - Extract each error with its file path and line number
   - Group errors by type:
     - **Build errors**: TypeScript compilation, missing imports, type mismatches
     - **Lint errors**: ESLint/Prettier formatting, unused variables, style violations
   - For each error, provide:
     - File path (relative to project root)
     - Line number
     - Error message
     - Brief suggestion for fixing it
   - Report total error count per category

## When to Use

Run this agent after modifying:
- Vue components (`.vue` files)
- TypeScript files (`.ts` files)
- Router configuration
- CSS/SCSS files
- API clients or model types

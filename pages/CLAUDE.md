# Jeffrey Vue Frontend Guide

## Build/Run Commands
- Install: `npm install`
- Development: `npm run dev`
- Build: `npm run build`
- Lint: `npm run lint`  
- Format: `npm run format`

## Style Guidelines
- TypeScript is used with strict type checking
- Import order: libraries first, then internal modules (sorted)
- Use PrimeVue components for UI consistency
- Follow Vue 3 composition API patterns
- Format with Prettier (2 space indentation)
- Use camelCase for variables/methods, PascalCase for components/classes
- Error handling: use try/catch with proper error logging
- Avoid any type when possible, use explicit interfaces
- Components should be named with PascalCase and include .vue extension
- Use alias imports with @ prefix for src directory references
- Always use const/let over var

## Git Workflow
- Follow repository's commit message style
- Each commit should focus on a single logical change
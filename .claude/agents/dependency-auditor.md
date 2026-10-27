# Dependency Auditor

Check dependency health across the multi-module Maven and NPM project.

## Instructions

### Backend (Maven)

1. **Check for outdated dependencies**:
   ```bash
   JAVA_HOME=/Users/petrbouda/.sdkman/candidates/java/25.0.1-amzn \
     /Users/petrbouda/.sdkman/candidates/maven/current/bin/mvn \
     versions:display-dependency-updates -pl pom.xml -N 2>/dev/null | grep -- '->'
   ```

2. **Check for unused/undeclared dependencies** (optional, can be slow):
   ```bash
   JAVA_HOME=/Users/petrbouda/.sdkman/candidates/java/25.0.1-amzn \
     /Users/petrbouda/.sdkman/candidates/maven/current/bin/mvn \
     dependency:analyze -pl pom.xml 2>/dev/null | grep -E '(Used undeclared|Unused declared)'
   ```

3. **Check version consistency**: Read the root `pom.xml` and verify that:
   - All dependency versions are managed in `<dependencyManagement>` or `<properties>`
   - No child modules override managed versions without good reason
   - Spring Boot, gRPC, and DuckDB versions are consistent across modules

### Frontend (NPM)

4. **Check for vulnerabilities**:
   ```bash
   cd jeffrey-microscope/pages-microscope && npm audit --omit=dev 2>/dev/null
   ```

5. **Check for outdated packages**:
   ```bash
   cd jeffrey-microscope/pages-microscope && npm outdated 2>/dev/null
   ```

### Report Format

Group findings by category:

```
## Maven Dependencies
### Outdated (X found)
- groupId:artifactId current → latest [severity]

### Version Consistency Issues (X found)
- module: overrides managed version of X

## NPM Dependencies
### Vulnerabilities (X found)
- package: severity - description

### Outdated (X found)
- package: current → latest
```

Severity levels:
- **CRITICAL**: Known security vulnerabilities (CVEs)
- **HIGH**: Major version updates available, potential breaking changes
- **MEDIUM**: Minor version updates available
- **LOW**: Patch updates available
- **INFO**: Informational (e.g., pre-release versions in use like Spring AI 2.0.0-M3)

## When to Use

Run this agent:
- Periodically (monthly) to check dependency health
- Before major releases
- When investigating build or runtime issues that might be version-related
- After upgrading a major dependency to check for cascading impacts

# Jeffrey - JFR Analyst Project

## Project Overview
Jeffrey is a JFR (Java Flight Recorder) analysis tool that specializes in visualizing JFR events using various types of graphs. The project helps developers profile Java applications and identify performance bottlenecks to optimize code for better speed and resource consumption.

## Architecture
This is a full-stack application with:
- **Backend**: Java Spring Boot REST API
- **Frontend**: Vue 3 SPA with TypeScript
- **Build System**: Maven (Java) + Vite (Frontend)
- **Database**: DuckDB for persistence

### Backend Domain Architecture

The backend is organized into two main domains:

```
┌─────────────────────────────────────────────────────────────────┐
│                     PLATFORM-MANAGEMENT                          │
│  Workspaces → Projects → Recordings → Profiles List              │
│  + Sessions, Scheduling                                          │
│                                                                  │
│  Module: service/platform-management                             │
└──────────────────────────────┬──────────────────────────────────┘
                               │ triggers profile creation/analysis
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                       PROFILE DOMAIN                             │
│                                                                  │
│  ┌───────────────────────┐       ┌───────────────────────────┐  │
│  │    profile-parser     │       │   profile-management      │  │
│  │                       │       │                           │  │
│  │  Path/InputStream     │ ProfileInfo                       │  │
│  │        ↓              │──────>│  ProfileManager           │  │
│  │  Parse JFR → Store DB │       │  Analysis features        │  │
│  │        ↓              │       │  (Flamegraph, Timeseries,  │  │
│  │  Returns ProfileInfo  │       │   Guardian, GC, Threads)  │  │
│  │                       │       │                           │  │
│  │  Module: profile-parser       │  Module: profile-management│  │
│  └───────────────────────┘       └───────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

**Platform Management** (`service/platform-management`):
- Manages workspaces, projects, recordings, and profile listings
- Handles sessions and file uploads
- Contains scheduling jobs for workspace/project lifecycle
- REST resources: `/api/workspaces/**`, `/api/projects/**`
- Triggers profile creation via profile-parser

**Profile Domain** - Two sibling modules:

- **Profile Parser** (`service/profile-parser`):
  - Parses JFR files from Path or InputStream
  - Extracts events and stores them to DuckDB
  - Returns `ProfileInfo` as the result
  - Pure library module (no web dependencies)

- **Profile Management** (`service/profile-management`):
  - All profile analysis features after a profile is selected
  - ProfileManager with sub-managers (Flamegraph, Timeseries, Guardian, etc.)
  - ProfileManagerFactoryRegistry groups related factories
  - REST resources: `/api/profiles/**`

**Core** (`service/core`):
- Thin bootstrap layer with Spring Boot application
- Application configuration and exception handling
- Wires all modules together

## Technology Stack

### Backend (Java)
- **Java**: Version 25
- **Spring Boot**: Web framework with Jersey for REST APIs
- **Maven**: Build tool and dependency management
- **SQLite**: Database with custom persistence layer
- **Logging**: SLF4J with Logback

### Frontend (Vue 3)
- **Vue 3**: Modern reactive framework with Composition API
- **TypeScript**: Type-safe JavaScript
- **Vite**: Build tool and dev server
- **Vue Router**: Client-side routing
- **ApexCharts**: Data visualization library
- **Bootstrap 5**: CSS framework with custom styling
- **Axios**: HTTP client for API communication

## Project Structure

```
jeffrey/
├── service/                        # Java backend services
│   ├── core/                       # Spring Boot bootstrap, configuration
│   ├── platform-management/        # Workspace/Project/Recording management
│   ├── profile-parser/             # JFR parsing and profile creation
│   ├── profile-management/         # Profile analysis domain
│   ├── scheduler/                  # Scheduling infrastructure
│   ├── providers/                  # Data providers
│   │   ├── provider-api/           # Provider interfaces
│   │   ├── duckdb-persistence/     # DuckDB implementation
│   │   ├── common-sql-persistence/ # Shared SQL utilities
│   │   ├── recording-storage-api/  # Storage interfaces
│   │   └── filesystem-recording-storage/
│   ├── jfr-repository-parser/      # JFR repository parsing (streaming-repo)
│   ├── recording-parser/           # JFR parsing
│   │   ├── jfr-parser-api/         # Parser interfaces
│   │   ├── jdk-jfr-parser/         # JDK-based parser
│   │   └── db-jfr-parser/          # Database-based parser
│   ├── flamegraph/                 # Flame graph generation
│   ├── timeseries/                 # Time series analysis
│   ├── subsecond/                  # Sub-second analysis
│   ├── profile-guardian/           # Profile validation
│   ├── profile-thread/             # Thread analysis
│   ├── frame-ir/                   # Frame intermediate representation
│   ├── common/                     # Shared utilities
│   ├── common-model/               # Shared data models
│   ├── sql-builder/                # SQL query building
│   └── tools/                      # JDK tooling utilities
├── pages/                          # Vue.js frontend application
│   ├── src/
│   │   ├── components/             # Reusable Vue components
│   │   ├── services/               # API clients and utilities
│   │   ├── views/                  # Page components
│   │   └── ...
│   └── package.json
├── build/                          # Build configurations
├── docker/                         # Docker configurations
└── pom.xml                         # Root Maven configuration
```

## Code Style and Conventions

### Java Backend
- **Package Structure**: `pbouda.jeffrey.*` with feature-based organization
- **Naming**: PascalCase for classes, camelCase for methods/fields
- **Architecture**: Manager pattern with service layer separation
- **Copyright Headers**: All Java files must include the AGPL license header with the current year (2025):
  ```java
  /*
   * Jeffrey
   * Copyright (C) 2025 Petr Bouda
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
  ```
- **Error Handling**: Custom exceptions with proper HTTP status mapping
- **Logging**: Use SLF4J with structured key-value format:
  - Pattern: `"Description of what happened: key1={} key2={} key3={}"`
  - No commas between key-value pairs
  - Example: `LOG.warn("Chunk extends beyond file, truncating: chunkIndex={} position={} claimedSize={}", index, pos, size)`
- **Time Handling**: Always use `java.time.Clock` instead of `Instant.now()` or `System.currentTimeMillis()`:
  - Inject `Clock` as a constructor parameter for testability
  - Use `clock.instant()` to get the current time
  - Example: `private final Clock clock; ... Instant now = clock.instant();`
  - This allows tests to use fixed time via `Clock.fixed()` for deterministic behavior

### Frontend (Vue/TypeScript)
- **Components**: PascalCase for component names
- **Composition API**: Preferred over Options API
- **TypeScript**: Strict typing with interfaces for API models
- **Styling**: Use shared CSS files first, then scoped CSS for component-specific styles
  - **Shared CSS files** (import via `import '@/styles/...'` or `@import` in SCSS):
    - `@/styles/shared-components.css` - Common UI patterns (search-container, cards, buttons, loading/empty states)
    - `@/assets/_sidebar-menu.scss` - Sidebar navigation styles (nav-item, nav-submenu, disabled-feature)
  - Always check shared CSS files before adding new scoped styles
  - Add commonly reused styles to shared files to avoid duplication
- **File Organization**: Feature-based grouping with shared components
- Formatting values use FormattingService, which provides consistent formatting across the application, propose a new function if you miss something
- Always try to use Vue Components first. If you need create a new to deduplicate code, suggest it and create it.

### Build Commands
- **Java Version**: `sdk use java 25.0.1-amzn`
- **Backend**: `mvn clean install` (Maven)
- **Frontend**: `npm run dev` (development), `npm run build` (production)
- **Linting**: `npm run lint` (ESLint + Prettier)

## API Structure
- RESTful endpoints under `/api/`
- Implemented using Spring Boot with Jersey located in `service/core/src/main/resources`
- JSON data exchange format
- Multi-part file uploads for JFR files

## Development Workflow
1. Backend development in Java with Spring Boot
2. Frontend development with Vue 3 and TypeScript
3. Integration through REST APIs
4. Docker containerization for deployment
5. Maven for Java build management, npm for frontend dependencies

## Testing
- JUnit 5 tests, with nested JUnit classes to group logical parts 
- Mockito for mocking dependencies
- Use `java.time.Clock` instead of real timestamps to fix time

## SQLite MCP Servers
- You can use MCP Server to connect to SQLite database to get information about the current data

### Structure of the Database
- Data is structured to be split into Projects and Profiles inside the projects
- All other concrete data are referenced using Profile IDs
- `profile_id` gathers all data related to a specific profile

### Database Schema
- read database schema from `service/providers/duckdb-persistence/src/main/resources/db/migration/V001__init.sql`
- JFR Event Types reference: https://sap.github.io/jfrevents/ (select Java version for event details)
- JSONB `fields` column in the `events` table contains event-specific data with the following structure based on JFR event types:

#### Flight Recorder Events
**DumpReason** (Category: Flight Recorder)
- reason (string): Reason for writing recording data
- recordingId (int): Recording ID

**DataLoss** (Category: Flight Recorder)
- amount (ulong): Lost data in bytes
- total (ulong): Total lost data for thread

**Flush** (Category: Flight Recorder)
- flushId (ulong)
- elements (ulong)
- size (ulong)

**ActiveRecording** (Category: Flight Recorder)
- id (long)
- name (string)
- destination (string)
- disk (boolean)
- maxAge (long)
- flushInterval (long)
- maxSize (long)
- recordingStart (long)
- recordingDuration (long)

**ActiveSetting** (Category: Flight Recorder)
- id (long)
- name (string)
- value (string)

#### JVM Events
**JVMInformation**
- jvmName (string)
- jvmVersion (string)
- jvmArguments (string)
- jvmFlags (string)
- javaArguments (string)
- jvmStartTime (long)
- pid (long)

**InitialSystemProperty**
- key (string)
- value (string)

**ClassLoad**
- loadedClass (Class): Class being loaded
- definingClassLoader (ClassLoader): ClassLoader defining the class
- initiatingClassLoader (ClassLoader): ClassLoader initiating the loading

**ClassDefine**
- definedClass (Class): Class being defined
- definingClassLoader (ClassLoader): ClassLoader defining the class

**ClassRedefinition**
- redefinedClass (Class): Class being redefined
- classModificationCount (int): Number of times class has been modified
- redefinitionId (long): Unique ID for this redefinition

**RedefineClasses**
- classCount (int): Number of classes being redefined
- redefinitionId (long): Unique ID for this redefinition

**RetransformClasses**
- classCount (int): Number of classes being retransformed
- redefinitionId (long): Unique ID for this retransformation

**ClassUnload**
- unloadedClass (Class): Class being unloaded
- definingClassLoader (ClassLoader): ClassLoader that defined the class

#### Compiler Events
**JITRestart**
- freedMemory (ulong): Amount of memory freed
- codeCache (ulong): Code cache size after restart

**Compilation**
- compileId (uint): Compilation ID
- compiler (string): Compiler name
- method (Method): Method being compiled
- compileLevel (ushort): Compilation level
- succeded (boolean): Whether compilation succeeded
- isOsr (boolean): Whether it's an OSR compilation
- codeSize (ulong): Size of generated code
- inlinedBytes (ulong): Bytes inlined

**CodeCacheFull**
- codeBlobType (string): Type of code blob
- startAddress (ulong): Start address of code cache
- commitedTopAddress (ulong): Committed top address
- reservedTopAddress (ulong): Reserved top address
- entryCount (int): Number of entries
- methodCount (int): Number of methods
- adaptorCount (int): Number of adaptors
- unallocatedCapacity (ulong): Unallocated capacity
- fullCount (int): Number of times cache was full

**CodeCacheStatistics**
- codeBlobType (string): Type of code blob
- startAddress (ulong): Start address
- reservedTopAddress (ulong): Reserved top address
- entryCount (int): Number of entries
- methodCount (int): Number of methods
- adaptorCount (int): Number of adaptors
- unallocatedCapacity (ulong): Unallocated capacity
- fullCount (int): Full count

**CodeCacheConfiguration**
- initialSize (ulong): Initial size
- reservedSize (ulong): Reserved size
- nonNMethodSize (ulong): Non-method size
- profiledSize (ulong): Profiled size
- nonProfiledSize (ulong): Non-profiled size
- expansionSize (ulong): Expansion size
- minBlockLength (ulong): Minimum block length
- startAddress (ulong): Start address
- reservedTopAddress (ulong): Reserved top address

### Field Data Types
- **string**: Text data
- **int**: 32-bit integer
- **long**: 64-bit integer
- **ulong**: Unsigned 64-bit integer
- **uint**: Unsigned 32-bit integer
- **ushort**: Unsigned 16-bit integer
- **boolean**: True/false value
- **Class**: JVM class reference
- **ClassLoader**: JVM class loader reference
- **Method**: JVM method reference

### JSONB Structure Example
```json
{
  "reason": "Shutdown",
  "recordingId": 1,
  "compileId": 12345,
  "method": "com.example.MyClass.myMethod()",
  "succeded": true,
  "codeSize": 1024
}
```

## Documentation Sync (Jeffrey Pages)

When modifying code, keep the corresponding documentation pages in `jeffrey-pages/` up to date. The docs are organized by domain:

| Code module | Documentation pages |
|---|---|
| `service/platform-management` | `jeffrey-pages/src/views/docs/platform/` — workspaces, projects, recordings, sessions, scheduler, profiler settings, alerts |
| `service/profile-management` | `jeffrey-pages/src/views/docs/profiles/` — visualization, application analysis, JVM internals, heap dump analysis |
| `cli/` | `jeffrey-pages/src/views/docs/cli/` — CLI overview, configuration, directory structure, generated output |
| `service/core` (config) | `jeffrey-pages/src/views/docs/configuration/` — app properties, advanced properties, secrets |
| Architecture changes | `jeffrey-pages/src/views/docs/architecture/` — overview, public API, storage |
| Deployment changes | `jeffrey-pages/src/views/docs/deployments/` — JAR, container, live recording |

## License
GNU Affero General Public License v3.0 (AGPL-3.0)

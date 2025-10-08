# Jeffrey - JFR Analyst Project

## Project Overview
Jeffrey is a JFR (Java Flight Recorder) analysis tool that specializes in visualizing JFR events using various types of graphs. The project helps developers profile Java applications and identify performance bottlenecks to optimize code for better speed and resource consumption.

## Architecture
This is a full-stack application with:
- **Backend**: Java Spring Boot REST API
- **Frontend**: Vue 3 SPA with TypeScript
- **Build System**: Maven (Java) + Vite (Frontend)
- **Database**: SQLite for persistence

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
├── service/                    # Java backend services
│   ├── core/                  # Main Spring Boot application
│   ├── providers/             # Data providers and parsers
│   ├── common/                # Shared utilities
│   └── ...                    # Other service modules
├── pages/                     # Vue.js frontend application
│   ├── src/
│   │   ├── components/        # Reusable Vue components
│   │   ├── services/          # API clients and utilities
│   │   ├── views/             # Page components
│   │   └── ...
│   └── package.json
├── build/                     # Build configurations
├── docker/                    # Docker configurations
└── pom.xml                    # Root Maven configuration
```

## Code Style and Conventions

### Java Backend
- **Package Structure**: `pbouda.jeffrey.*` with feature-based organization
- **Naming**: PascalCase for classes, camelCase for methods/fields
- **Architecture**: Manager pattern with service layer separation
- **Comments**: AGPL license headers on all files
- **Error Handling**: Custom exceptions with proper HTTP status mapping

### Frontend (Vue/TypeScript)
- **Components**: PascalCase for component names
- **Composition API**: Preferred over Options API
- **TypeScript**: Strict typing with interfaces for API models
- **Styling**: Scoped CSS with SCSS preprocessing
- **File Organization**: Feature-based grouping with shared components
- Formatting values use FormattingService, which provides consistent formatting across the application, propose a new function if you miss something
- Always try to use Vue Components first. If you need create a new to deduplicate code, suggest it and create it.

### Build Commands
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
- read database schema from `service/providers/sqlite-persistence/src/main/resources/db/migration/V001__init.sql`
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

## License
GNU Affero General Public License v3.0 (AGPL-3.0)

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
- **Java**: Version 24
- **Spring Boot**: Web framework with Jersey for REST APIs
- **Maven**: Build tool and dependency management
- **SQLite**: Database with custom persistence layer
- **JFR Parser**: Custom JFR event parsing and analysis
- **Logging**: SLF4J with Logback

### Frontend (Vue 3)
- **Vue 3**: Modern reactive framework with Composition API
- **TypeScript**: Type-safe JavaScript
- **Vite**: Build tool and dev server
- **Vue Router**: Client-side routing
- **ApexCharts**: Data visualization library
- **Bootstrap 5**: CSS framework with custom styling
- **Axios**: HTTP client for API communication

### Development Tools
- **ESLint**: JavaScript/TypeScript linting
- **Prettier**: Code formatting
- **Sass**: CSS preprocessing

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

## Key Features
- **JFR Event Analysis**: Parse and analyze Java Flight Recorder files
- **Flamegraph Visualization**: Interactive flame graphs for performance profiling
- **Thread Analysis**: Thread statistics and timeline visualization
- **Memory Profiling**: Heap memory analysis and garbage collection insights
- **HTTP/JDBC Monitoring**: Application-level performance metrics
- **Dashboard**: Comprehensive performance dashboard with multiple chart types

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
- JSON data exchange format
- Multi-part file uploads for JFR files
- WebSocket support for real-time updates

## Development Workflow
1. Backend development in Java with Spring Boot
2. Frontend development with Vue 3 and TypeScript
3. Integration through REST APIs
4. Docker containerization for deployment
5. Maven for Java build management, npm for frontend dependencies

## Testing
- Backend: JUnit tests (look for test structure in service modules)
- Frontend: Development server with hot reloading
- Integration: API testing through frontend client integration

## SQLite MCP Servers
- You can use MCP Server to connect to SQLite database to get information about the current data

### Structure of the Database
- Data is structured to be split into Projects and Profiles inside the projects
- All other concrete data are referenced using Profile IDs
- `profile_id` gathers all data related to a specific profile

### Database Schema

#### Core Tables
**projects** (project_id PK)
- project_id (TEXT, PK) - Unique project identifier
- project_name (TEXT) - Human-readable project name
- created_at (INTEGER) - Creation timestamp
- graph_visualization (TEXT) - Default visualization type

**profiles** (profile_id PK)
- profile_id (TEXT, PK) - Unique profile identifier
- project_id (TEXT) - Foreign key to projects
- profile_name (TEXT) - Human-readable profile name
- event_source (TEXT) - Source of profiling data
- event_fields_setting (TEXT) - Configuration for event fields
- created_at (INTEGER) - Creation timestamp
- recording_id (TEXT) - Associated recording identifier
- recording_started_at (INTEGER) - Recording start timestamp
- recording_finished_at (INTEGER) - Recording end timestamp
- initialized_at (INTEGER) - Initialization timestamp
- enabled_at (INTEGER) - Enablement timestamp

**recordings** (project_id, id composite PK)
- project_id (TEXT, PK) - Foreign key to projects
- id (TEXT, PK) - Recording identifier
- recording_name (TEXT) - Human-readable recording name
- folder_id (TEXT) - Optional folder organization
- event_source (TEXT) - Source of recording data
- created_at (INTEGER) - Creation timestamp
- recording_started_at (INTEGER) - Recording start timestamp
- recording_finished_at (INTEGER) - Recording end timestamp

**recording_files** (project_id, id composite PK)
- project_id (TEXT, PK) - Foreign key to projects
- recording_id (TEXT) - Foreign key to recordings
- id (TEXT, PK) - File identifier
- filename (TEXT) - Original filename
- supported_type (TEXT) - File type (JFR, etc.)
- uploaded_at (INTEGER) - Upload timestamp
- size_in_bytes (INTEGER) - File size

#### Event Data Tables
**event_types** (profile_id, name composite PK)
- profile_id (TEXT, PK) - Foreign key to profiles
- name (TEXT, PK) - Event type name (e.g., jdk.GCPhaseParallel)
- label (TEXT) - Human-readable label
- type_id (INTEGER) - Internal type identifier
- description (TEXT) - Event type description
- categories (TEXT) - Event categories
- source (TEXT) - Event source
- subtype (TEXT) - Event subtype
- samples (INTEGER) - Number of samples
- weight (INTEGER) - Weight value
- has_stacktrace (BOOLEAN) - Whether events have stacktraces
- calculated (BOOLEAN) - Whether type is calculated
- extras (TEXT) - Additional metadata
- settings (TEXT) - Type-specific settings
- columns (TEXT) - Available columns

**events** (profile_id, event_id composite PK)
- profile_id (TEXT, PK) - Foreign key to profiles
- event_id (INTEGER, PK) - Event identifier
- event_type (TEXT) - Type of event
- start_timestamp (INTEGER) - Event start time
- start_timestamp_from_beginning (INTEGER) - Relative start time
- end_timestamp (INTEGER) - Event end time
- end_timestamp_from_beginning (INTEGER) - Relative end time
- duration (INTEGER) - Event duration
- samples (INTEGER) - Number of samples
- weight (INTEGER) - Event weight
- weight_entity (TEXT) - Weight unit
- stacktrace_id (INTEGER) - Associated stacktrace
- thread_id (INTEGER) - Associated thread
- fields (JSONB) - Event-specific data

#### Thread and Stacktrace Tables
**threads** (profile_id, thread_id composite PK)
- profile_id (TEXT, PK) - Foreign key to profiles
- thread_id (TEXT, PK) - Thread identifier
- name (TEXT) - Thread name
- os_id (INTEGER) - Operating system thread ID
- java_id (INTEGER) - Java thread ID
- is_virtual (BOOLEAN) - Whether thread is virtual

**stacktraces** (profile_id, stacktrace_id composite PK)
- profile_id (TEXT, PK) - Foreign key to profiles
- stacktrace_id (INTEGER, PK) - Stacktrace identifier
- type_id (INTEGER) - Stacktrace type
- frames (TEXT) - Stack frames data

**stacktrace_tags** (profile_id, stacktrace_id, tag_id composite PK)
- profile_id (TEXT, PK) - Foreign key to profiles
- stacktrace_id (INTEGER, PK) - Foreign key to stacktraces
- tag_id (INTEGER, PK) - Tag identifier

#### Storage Tables
**saved_graphs** (profile_id, id composite PK)
- profile_id (TEXT, PK) - Foreign key to profiles
- id (TEXT, PK) - Graph identifier
- name (TEXT) - Graph name
- params (BLOB) - Graph parameters
- content (BLOB) - Graph content
- created_at (INTEGER) - Creation timestamp

**cache** (profile_id, key composite PK)
- profile_id (TEXT, PK) - Foreign key to profiles
- key (TEXT, PK) - Cache key
- content (BLOB) - Cached content

#### System Tables
**flyway_schema_history** - Database migration history
**external_project_links** - External project references
**schedulers** - Scheduled task configuration
**recording_folders** - Recording folder organization

## JFR Event Types and Fields Structure

### Event Categories and Field Types
The JSONB `fields` column in the `events` table contains event-specific data with the following structure based on JFR event types:

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

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

## License
GNU Affero General Public License v3.0 (AGPL-3.0)
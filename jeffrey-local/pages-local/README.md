# Jeffrey Admin UI

A modern administrative user interface for Jeffrey - a tool for collecting, parsing, and analyzing JDK Flight Recorder files.

## Features

- Modern responsive UI built with Vue 3 and TypeScript
- Project management
- Profile management
- Recording analysis
- Repository statistics
- Background job monitoring
- Settings configuration

## Tech Stack

- Vue 3
- TypeScript
- Bootstrap 5
- Vue Router
- ApexCharts for visualization
- Konva for canvas-based graphics

## Project Structure

```
jeffrey-admin/
├── public/              # Static assets
├── src/
│   ├── assets/          # Styles and other assets
│   ├── components/      # Reusable Vue components
│   ├── layout/          # Layout components
│   ├── router/          # Vue Router configuration
│   ├── services/        # API and service classes
│   ├── types/           # TypeScript interfaces and types
│   ├── views/           # Page components
│   ├── App.vue          # Root component
│   └── main.ts          # Application entry point
├── index.html           # HTML template
├── package.json         # Dependencies and scripts
├── tsconfig.json        # TypeScript configuration
└── vite.config.ts       # Vite configuration
```

## Getting Started

### Prerequisites

- Node.js (v16 or higher)
- npm or yarn

### Installation

```bash
# Install dependencies
npm install

# Start development server
npm run dev

# Build for production
npm run build

# Preview production build
npm run preview
```

## Usage

1. Create a new project
2. Upload JDK Flight Recorder files to create profiles
3. Analyze profiles to generate flamegraphs
4. View and compare performance metrics

## License

This project is licensed under the GNU Affero General Public License v3.0.
import { Project } from '@/types';
import ApiClient from './ApiClient';

// Fallback mock data in case API fails
const mockProjects: Project[] = [
  { 
    id: '1', 
    name: 'Production App', 
    createdAt: '2025-03-15T12:00:00', 
    profileCount: 5, 
    recordingCount: 12, 
    alertCount: 3, 
    sourceType: 'JDK',
    latestRecordingAt: '2025-03-25T14:32:17',
    latestProfileAt: '2025-03-26T09:45:33'
  },
  { 
    id: '2', 
    name: 'Development Server', 
    createdAt: '2025-03-10T09:30:00', 
    profileCount: 2, 
    recordingCount: 8, 
    alertCount: 0, 
    sourceType: 'ASPROF',
    latestRecordingAt: '2025-03-22T08:15:43',
    latestProfileAt: '2025-03-23T16:20:05'
  },
  { 
    id: '3', 
    name: 'Benchmark Tests', 
    createdAt: '2025-03-05T15:45:00', 
    profileCount: 7, 
    recordingCount: 15, 
    alertCount: 5, 
    sourceType: 'JDK',
    latestRecordingAt: '2025-03-24T11:05:22',
    latestProfileAt: '2025-03-25T13:12:51'
  }
];

export default class ProjectService {
  /**
   * Get all projects
   */
  static async list(): Promise<Project[]> {
    try {
      // Fetch projects from API
      return await ApiClient.getMockProjects();
    } catch (error) {
      console.error('Error fetching projects, using fallback data:', error);
      // Return mock data as fallback if API fails
      return [...mockProjects];
    }
  }
  
  /**
   * Get a project by ID
   */
  static async get(id: string): Promise<Project> {
    // Simulate API delay
    await new Promise(resolve => setTimeout(resolve, 200));
    
    const project = mockProjects.find(p => p.id === id);
    if (!project) {
      throw new Error(`Project with ID ${id} not found`);
    }
    
    return { ...project };
  }
  
  /**
   * Create a new project
   */
  static async create(name: string): Promise<Project> {
    // Simulate API delay
    await new Promise(resolve => setTimeout(resolve, 500));
    
    if (!name || name.trim() === '') {
      throw new Error('Project name cannot be empty');
    }
    
    const newProject: Project = {
      id: `proj-${Date.now()}`,
      name,
      createdAt: new Date().toISOString(),
      profileCount: 0,
      recordingCount: 0,
      alertCount: 0,
      sourceType: Math.random() > 0.5 ? 'JDK' : 'ASPROF', // Randomly assign a source type for new projects
      latestRecordingAt: null, // No recordings yet
      latestProfileAt: null // No profiles yet
    };
    
    mockProjects.unshift(newProject);
    return { ...newProject };
  }
  
  /**
   * Update a project
   */
  static async update(id: string, data: Partial<Project>): Promise<Project> {
    // Simulate API delay
    await new Promise(resolve => setTimeout(resolve, 400));
    
    const projectIndex = mockProjects.findIndex(p => p.id === id);
    if (projectIndex === -1) {
      throw new Error(`Project with ID ${id} not found`);
    }
    
    const updatedProject = {
      ...mockProjects[projectIndex],
      ...data
    };
    
    mockProjects[projectIndex] = updatedProject;
    return { ...updatedProject };
  }
  
  /**
   * Delete a project
   */
  static async delete(id: string): Promise<void> {
    // Simulate API delay
    await new Promise(resolve => setTimeout(resolve, 300));
    
    const projectIndex = mockProjects.findIndex(p => p.id === id);
    if (projectIndex === -1) {
      throw new Error(`Project with ID ${id} not found`);
    }
    
    mockProjects.splice(projectIndex, 1);
  }
}
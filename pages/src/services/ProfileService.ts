import { Profile } from '@/types';

// Mock data generator function to generate profiles for any project ID
const generateMockProfiles = (projectId: string): Profile[] => [
  { 
    id: `${projectId}-1`, 
    name: 'Application Startup', 
    createdAt: '2025-03-20T10:15:00', 
    updatedAt: '2025-03-20T10:15:00',
    enabled: true,
    description: 'Profile of application startup sequence',
    size: 24500000, // 24.5 MB
    duration: 300, // 5 minutes
    metadata: {
      jvmVersion: 'OpenJDK 64-Bit Server VM (17.0.8+9)',
      javaVersion: 'Java(TM) SE Runtime Environment (17.0.8+9)',
      os: 'Linux 5.15.0-100-generic (x86_64)'
    }
  },
  { 
    id: `${projectId}-2`, 
    name: 'Memory Analysis', 
    createdAt: '2025-03-18T14:30:00', 
    updatedAt: '2025-03-18T14:30:00',
    enabled: true,
    description: 'Memory usage analysis during high load',
    size: 18200000, // 18.2 MB
    duration: 420, // 7 minutes
    metadata: {
      jvmVersion: 'OpenJDK 64-Bit Server VM (17.0.8+9)',
      javaVersion: 'Java(TM) SE Runtime Environment (17.0.8+9)',
      os: 'Linux 5.15.0-100-generic (x86_64)'
    }
  },
  { 
    id: `${projectId}-3`, 
    name: 'API Performance', 
    createdAt: '2025-03-15T09:45:00', 
    updatedAt: '2025-03-15T09:45:00',
    enabled: false,
    description: 'Profiling of API endpoint performance',
    size: 15700000, // 15.7 MB
    duration: 180, // 3 minutes
    metadata: {
      jvmVersion: 'OpenJDK 64-Bit Server VM (17.0.8+9)',
      javaVersion: 'Java(TM) SE Runtime Environment (17.0.8+9)',
      os: 'Linux 5.15.0-100-generic (x86_64)'
    }
  },
  { 
    id: `${projectId}-4`, 
    name: 'Database Connections', 
    createdAt: '2025-03-12T16:20:00', 
    updatedAt: '2025-03-12T16:20:00',
    enabled: true,
    description: 'Database connection pool and query execution profiling',
    size: 22300000, // 22.3 MB
    duration: 360, // 6 minutes
    metadata: {
      jvmVersion: 'OpenJDK 64-Bit Server VM (17.0.8+9)',
      javaVersion: 'Java(TM) SE Runtime Environment (17.0.8+9)',
      os: 'Linux 5.15.0-100-generic (x86_64)'
    }
  }
];

// Mock profiles data storage (by project)
const mockProfilesMap = new Map<string, Profile[]>();

export default class ProfileService {
  private projectId: string;
  
  constructor(projectId: string) {
    this.projectId = projectId;
    
    // Initialize with mock data if not present
    if (!mockProfilesMap.has(projectId)) {
      mockProfilesMap.set(projectId, generateMockProfiles(projectId));
    }
  }
  
  /**
   * Get all profiles for the project
   */
  async list(): Promise<Profile[]> {
    // Simulate API delay
    await new Promise(resolve => setTimeout(resolve, 300));
    
    const profiles = mockProfilesMap.get(this.projectId) || [];
    return [...profiles];
  }
  
  /**
   * Get a profile by ID
   */
  async get(id: string): Promise<Profile> {
    // Simulate API delay
    await new Promise(resolve => setTimeout(resolve, 200));
    
    const profiles = mockProfilesMap.get(this.projectId) || [];
    const profile = profiles.find(p => p.id === id);
    
    if (!profile) {
      throw new Error(`Profile with ID ${id} not found`);
    }
    
    return { ...profile };
  }
  
  /**
   * Create a new profile
   */
  async create(name: string, fileInput?: File): Promise<Profile> {
    // Simulate API delay
    await new Promise(resolve => setTimeout(resolve, 700));
    
    if (!name || name.trim() === '') {
      throw new Error('Profile name cannot be empty');
    }
    
    const profiles = mockProfilesMap.get(this.projectId) || [];
    
    const newProfile: Profile = {
      id: `${this.projectId}-${Date.now()}`,
      name,
      createdAt: new Date().toISOString(),
      enabled: false // Initially not enabled while "processing"
    };
    
    // Add to the project's profiles
    mockProfilesMap.set(this.projectId, [newProfile, ...profiles]);
    
    // Simulate delayed enabling of the profile
    setTimeout(() => {
      const profiles = mockProfilesMap.get(this.projectId) || [];
      const profileIndex = profiles.findIndex(p => p.id === newProfile.id);
      
      if (profileIndex !== -1) {
        profiles[profileIndex].enabled = true;
        mockProfilesMap.set(this.projectId, profiles);
      }
    }, 5000);
    
    return { ...newProfile };
  }
  
  /**
   * Update a profile
   */
  async update(id: string, data: Partial<Profile>): Promise<Profile> {
    // Simulate API delay
    await new Promise(resolve => setTimeout(resolve, 400));
    
    const profiles = mockProfilesMap.get(this.projectId) || [];
    const profileIndex = profiles.findIndex(p => p.id === id);
    
    if (profileIndex === -1) {
      throw new Error(`Profile with ID ${id} not found`);
    }
    
    const updatedProfile = {
      ...profiles[profileIndex],
      ...data
    };
    
    profiles[profileIndex] = updatedProfile;
    mockProfilesMap.set(this.projectId, profiles);
    
    return { ...updatedProfile };
  }
  
  /**
   * Delete a profile
   */
  async delete(id: string): Promise<void> {
    // Simulate API delay
    await new Promise(resolve => setTimeout(resolve, 300));
    
    const profiles = mockProfilesMap.get(this.projectId) || [];
    const filteredProfiles = profiles.filter(p => p.id !== id);
    
    if (filteredProfiles.length === profiles.length) {
      throw new Error(`Profile with ID ${id} not found`);
    }
    
    mockProfilesMap.set(this.projectId, filteredProfiles);
  }
}
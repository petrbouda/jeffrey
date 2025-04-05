

// Mock profiles data storage (by project)
import Profile from "@/services/model/Profile.ts";

const mockProfilesMap = new Map<string, Profile[]>();

export default class ProfileService {
  private projectId: string;
  
  /**
   * Get a profile by ID
   */
  async get(id: string): Promise<Profile> {
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

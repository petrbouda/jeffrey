import { Recording, Folder } from '@/types';

// Mock data generator function to generate recordings for any project ID
const generateMockRecordings = (projectId: string): Recording[] => {
  // Define folders first
  const folder1: Folder = {
    folder_id: `${projectId}-folder-1`,
    folder_name: 'Production Tests'
  };
  
  const folder2: Folder = {
    folder_id: `${projectId}-folder-2`,
    folder_name: 'Diagnostics'
  };
  
  return [
    { 
      id: `${projectId}-rec-1`, 
      name: 'production-server.jfr', 
      size: 24500000, // 24.5 MB
      duration: 615, // 10m 15s
      recordedAt: '2025-03-26T10:15:00',
      hasProfile: false,
      folder: folder1
    },
    { 
      id: `${projectId}-rec-2`, 
      name: 'benchmark-test.jfr', 
      size: 12300000, // 12.3 MB
      duration: 330, // 5m 30s
      recordedAt: '2025-03-25T14:30:00',
      hasProfile: false,
      folder: folder1
    },
    { 
      id: `${projectId}-rec-3`, 
      name: 'memory-leak-analysis.jfr', 
      size: 18500000, // 18.5 MB
      duration: 480, // 8m
      recordedAt: '2025-03-24T09:20:00',
      hasProfile: true,
      folder: folder2
    },
    { 
      id: `${projectId}-rec-4`, 
      name: 'api-load-test.jfr', 
      size: 15200000, // 15.2 MB
      duration: 420, // 7m
      recordedAt: '2025-03-23T16:45:00',
      hasProfile: false,
      folder: null
    }
  ];
};

// Mock recordings data storage (by project)
const mockRecordingsMap = new Map<string, Recording[]>();

export default class RecordingService {
  private projectId: string;
  
  constructor(projectId: string) {
    this.projectId = projectId;
    
    // Initialize with mock data if not present
    if (!mockRecordingsMap.has(projectId)) {
      mockRecordingsMap.set(projectId, generateMockRecordings(projectId));
    }
  }
  
  /**
   * Get all recordings for the project
   */
  async list(): Promise<Recording[]> {
    // Simulate API delay
    await new Promise(resolve => setTimeout(resolve, 300));
    
    const recordings = mockRecordingsMap.get(this.projectId) || [];
    return [...recordings];
  }
  
  /**
   * Get a recording by ID
   */
  async get(id: string): Promise<Recording> {
    // Simulate API delay
    await new Promise(resolve => setTimeout(resolve, 200));
    
    const recordings = mockRecordingsMap.get(this.projectId) || [];
    const recording = recordings.find(r => r.id === id);
    
    if (!recording) {
      throw new Error(`Recording with ID ${id} not found`);
    }
    
    return { ...recording };
  }
  
  /**
   * Delete a recording or all recordings in a folder
   */
  async delete(id: string, isFolder?: boolean): Promise<void> {
    // Simulate API delay
    await new Promise(resolve => setTimeout(resolve, 300));
    
    const recordings = mockRecordingsMap.get(this.projectId) || [];
    const recordingToDelete = recordings.find(r => r.id === id);
    
    if (!recordingToDelete) {
      throw new Error(`Recording with ID ${id} not found`);
    }
    
    // If we're deleting a folder, we need to delete all recordings in the folder too
    if (isFolder) {
      // Get the folder ID which is the recording's folder_id
      const folderId = id;
      const filteredRecordings = recordings.filter(r => 
        !r.folder || r.folder.folder_id !== folderId
      );
      
      mockRecordingsMap.set(this.projectId, filteredRecordings);
    } else {
      // Just delete the single recording
      const filteredRecordings = recordings.filter(r => r.id !== id);
      mockRecordingsMap.set(this.projectId, filteredRecordings);
    }
  }
  
  /**
   * Upload a recording
   */
  async upload(file: File, folderName?: string): Promise<Recording> {
    // Simulate API delay
    await new Promise(resolve => setTimeout(resolve, 700));
    
    const recordings = mockRecordingsMap.get(this.projectId) || [];
    
    // Find the folder if specified
    let folderObj: Folder | null = null;
    if (folderName) {
      // Look for recordings that have this folder name
      const existingRecording = recordings.find(r => 
        r.folder && r.folder.folder_name === folderName
      );
      
      if (existingRecording && existingRecording.folder) {
        // Use the existing folder object
        folderObj = existingRecording.folder;
      } else {
        // Create a new folder object
        folderObj = {
          folder_id: `${this.projectId}-folder-${Date.now()}`,
          folder_name: folderName
        };
      }
    }
    
    const newRecording: Recording = {
      id: `${this.projectId}-rec-${Date.now()}`,
      name: file.name,
      size: file.size,
      duration: Math.floor(Math.random() * 600) + 60, // Random duration between 1-10 minutes
      recordedAt: new Date().toISOString(),
      hasProfile: false,
      folder: folderObj
    };
    
    // Add to the project's recordings
    mockRecordingsMap.set(this.projectId, [newRecording, ...recordings]);
    
    return { ...newRecording };
  }
  
  /**
   * Create a new folder
   */
  async createFolder(folderName: string): Promise<Folder> {
    // Simulate API delay
    await new Promise(resolve => setTimeout(resolve, 300));
    
    const recordings = mockRecordingsMap.get(this.projectId) || [];
    
    // Check if folder already exists by checking if any recording has a folder with this name
    const folderExists = recordings.some(r => 
      r.folder && r.folder.folder_name === folderName
    );
    
    if (folderExists) {
      throw new Error(`Folder ${folderName} already exists`);
    }
    
    const folderId = `${this.projectId}-folder-${Date.now()}`;
    
    const newFolder: Folder = {
      folder_id: folderId,
      folder_name: folderName
    };
    
    // We don't add the folder to recordings anymore, since folders are just virtual
    // They're represented by the 'folder' field in recordings
    
    return newFolder;
  }
  
  /**
   * Mark a recording as having a profile
   */
  async markHasProfile(id: string): Promise<Recording> {
    // Simulate API delay
    await new Promise(resolve => setTimeout(resolve, 200));
    
    const recordings = mockRecordingsMap.get(this.projectId) || [];
    const recordingIndex = recordings.findIndex(r => r.id === id);
    
    if (recordingIndex === -1) {
      throw new Error(`Recording with ID ${id} not found`);
    }
    
    recordings[recordingIndex].hasProfile = true;
    mockRecordingsMap.set(this.projectId, recordings);
    
    return { ...recordings[recordingIndex] };
  }
}

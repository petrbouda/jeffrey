import { Recording, Folder } from '@/types';

// Mock data generator function to generate recordings for any project ID
const generateMockRecordings = (projectId: string): Recording[] => [
  { 
    id: `${projectId}-rec-1`, 
    name: 'application-startup.jfr', 
    uploadedAt: '2025-03-25T10:15:00',
    sizeInBytes: 14800000, // 14.8 MB
    durationInMillis: 180000, // 3 minutes in milliseconds
    hasProfile: true,
    folder: null
  },
  { 
    id: `${projectId}-rec-2`, 
    name: 'memory-analysis.jfr', 
    uploadedAt: '2025-03-23T14:30:00',
    sizeInBytes: 18200000, // 18.2 MB
    durationInMillis: 300000, // 5 minutes in milliseconds
    hasProfile: false,
    folder: {
      folder_id: 'performance-tests',
      folder_name: 'Performance Tests'
    }
  },
  { 
    id: `${projectId}-rec-3`, 
    name: 'api-benchmark.jfr', 
    uploadedAt: '2025-03-20T09:45:00',
    sizeInBytes: 11500000, // 11.5 MB
    durationInMillis: 120000, // 2 minutes in milliseconds
    hasProfile: true,
    folder: {
      folder_id: 'performance-tests',
      folder_name: 'Performance Tests'
    }
  },
  { 
    id: `${projectId}-rec-4`, 
    name: 'database-connections.jfr', 
    uploadedAt: '2025-03-18T16:20:00',
    sizeInBytes: 9700000, // 9.7 MB
    durationInMillis: 360000, // 6 minutes in milliseconds
    hasProfile: true,
    folder: null
  },
  { 
    id: `${projectId}-rec-5`, 
    name: 'connection-pool.jfr', 
    uploadedAt: '2025-03-17T11:05:00',
    sizeInBytes: 8300000, // 8.3 MB
    durationInMillis: 240000, // 4 minutes in milliseconds
    hasProfile: false,
    folder: {
      folder_id: 'database',
      folder_name: 'Database'
    }
  },
  { 
    id: `${projectId}-rec-6`, 
    name: 'query-execution.jfr', 
    uploadedAt: '2025-03-16T13:40:00',
    sizeInBytes: 7500000, // 7.5 MB
    durationInMillis: 180000, // 3 minutes in milliseconds
    hasProfile: false,
    folder: {
      folder_id: 'database',
      folder_name: 'Database'
    }
  },
  { 
    id: `${projectId}-rec-7`, 
    name: 'garbage-collection.jfr', 
    uploadedAt: '2025-03-15T09:10:00',
    sizeInBytes: 12500000, // 12.5 MB
    durationInMillis: 420000, // 7 minutes in milliseconds
    hasProfile: true,
    folder: {
      folder_id: 'jvm-metrics',
      folder_name: 'JVM Metrics'
    }
  },
  { 
    id: `${projectId}-rec-8`, 
    name: 'thread-dump.jfr', 
    uploadedAt: '2025-03-14T15:30:00',
    sizeInBytes: 5200000, // 5.2 MB
    durationInMillis: 60000, // 1 minute in milliseconds
    hasProfile: false,
    folder: {
      folder_id: 'jvm-metrics',
      folder_name: 'JVM Metrics'
    }
  }
];

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
    await new Promise(resolve => setTimeout(resolve, 200));
    
    const recordings = mockRecordingsMap.get(this.projectId) || [];
    const recording = recordings.find(r => r.id === id);
    
    if (!recording) {
      throw new Error(`Recording with ID ${id} not found`);
    }
    
    return { ...recording };
  }
  
  /**
   * Upload a new recording
   */
  async upload(file: File, folder: Folder | null = null): Promise<Recording> {
    // Simulate API delay
    await new Promise(resolve => setTimeout(resolve, 700));
    
    if (!file) {
      throw new Error('File cannot be empty');
    }
    
    const recordings = mockRecordingsMap.get(this.projectId) || [];
    
    // Create a new recording
    const newRecording: Recording = {
      id: `${this.projectId}-rec-${Date.now()}`,
      name: file.name,
      uploadedAt: new Date().toISOString(),
      sizeInBytes: file.size,
      durationInMillis: Math.floor(Math.random() * 300000) + 60000, // Random duration between 1-6 minutes
      hasProfile: false,
      folder: folder
    };
    
    // Add to the project's recordings
    mockRecordingsMap.set(this.projectId, [newRecording, ...recordings]);
    
    return { ...newRecording };
  }
  
  /**
   * Delete a recording
   */
  async delete(id: string): Promise<void> {
    // Simulate API delay
    await new Promise(resolve => setTimeout(resolve, 300));
    
    const recordings = mockRecordingsMap.get(this.projectId) || [];
    const filteredRecordings = recordings.filter(r => r.id !== id);
    
    if (filteredRecordings.length === recordings.length) {
      throw new Error(`Recording with ID ${id} not found`);
    }
    
    mockRecordingsMap.set(this.projectId, filteredRecordings);
  }
  
  /**
   * Delete a folder and all its recordings
   */
  async deleteFolder(folderId: string): Promise<void> {
    // Simulate API delay
    await new Promise(resolve => setTimeout(resolve, 400));
    
    const recordings = mockRecordingsMap.get(this.projectId) || [];
    const filteredRecordings = recordings.filter(r => !r.folder || r.folder.folder_id !== folderId);
    
    if (filteredRecordings.length === recordings.length) {
      throw new Error(`Folder with ID ${folderId} not found or empty`);
    }
    
    mockRecordingsMap.set(this.projectId, filteredRecordings);
  }
  
  /**
   * Create a new folder
   */
  async createFolder(folderName: string): Promise<Folder> {
    // Simulate API delay
    await new Promise(resolve => setTimeout(resolve, 300));
    
    if (!folderName || folderName.trim() === '') {
      throw new Error('Folder name cannot be empty');
    }
    
    // Create a new folder
    const newFolder: Folder = {
      folder_id: folderName.toLowerCase().replace(/[^a-z0-9-]/g, '-'),
      folder_name: folderName
    };
    
    return newFolder;
  }
  
  /**
   * Move a recording to a folder
   */
  async moveToFolder(recordingId: string, folder: Folder | null): Promise<Recording> {
    // Simulate API delay
    await new Promise(resolve => setTimeout(resolve, 400));
    
    const recordings = mockRecordingsMap.get(this.projectId) || [];
    const recordingIndex = recordings.findIndex(r => r.id === recordingId);
    
    if (recordingIndex === -1) {
      throw new Error(`Recording with ID ${recordingId} not found`);
    }
    
    // Update the recording
    recordings[recordingIndex] = {
      ...recordings[recordingIndex],
      folder: folder
    };
    
    mockRecordingsMap.set(this.projectId, recordings);
    
    return { ...recordings[recordingIndex] };
  }
}

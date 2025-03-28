import { Recording } from '@/types';

// Mock data generator function to generate recordings for any project ID
const generateMockRecordings = (projectId: string): Recording[] => [
  { 
    id: `${projectId}-rec-1`, 
    name: 'production-server.jfr', 
    size: 24500000, // 24.5 MB
    duration: 615, // 10m 15s
    recordedAt: '2025-03-26T10:15:00',
    path: '/recordings/production-server.jfr',
    hasProfile: false
  },
  { 
    id: `${projectId}-rec-2`, 
    name: 'benchmark-test.jfr', 
    size: 12300000, // 12.3 MB
    duration: 330, // 5m 30s
    recordedAt: '2025-03-25T14:30:00',
    path: '/recordings/benchmark-test.jfr',
    hasProfile: false
  },
  { 
    id: `${projectId}-rec-3`, 
    name: 'memory-leak-analysis.jfr', 
    size: 18500000, // 18.5 MB
    duration: 480, // 8m
    recordedAt: '2025-03-24T09:20:00',
    path: '/recordings/memory-leak-analysis.jfr',
    hasProfile: true
  },
  { 
    id: `${projectId}-rec-4`, 
    name: 'api-load-test.jfr', 
    size: 15200000, // 15.2 MB
    duration: 420, // 7m
    recordedAt: '2025-03-23T16:45:00',
    path: '/recordings/api-load-test.jfr',
    hasProfile: false
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
   * Upload a recording
   */
  async upload(file: File, folderPath?: string): Promise<Recording> {
    // Simulate API delay
    await new Promise(resolve => setTimeout(resolve, 700));
    
    const recordings = mockRecordingsMap.get(this.projectId) || [];
    
    const newRecording: Recording = {
      id: `${this.projectId}-rec-${Date.now()}`,
      name: file.name,
      size: file.size,
      duration: Math.floor(Math.random() * 600) + 60, // Random duration between 1-10 minutes
      recordedAt: new Date().toISOString(),
      path: folderPath ? `${folderPath}/${file.name}` : `/recordings/${file.name}`,
      hasProfile: false
    };
    
    // Add to the project's recordings
    mockRecordingsMap.set(this.projectId, [newRecording, ...recordings]);
    
    return { ...newRecording };
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
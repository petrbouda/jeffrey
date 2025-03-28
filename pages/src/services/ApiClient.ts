import axios from 'axios';
import { Project } from '@/types';

// Create an axios instance with common configuration
const apiClient = axios.create({
  baseURL: 'http://localhost:8585/api',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
    'Accept': 'application/json'
  }
});

// Add response interceptor for error handling
apiClient.interceptors.response.use(
  response => response,
  error => {
    console.error('API Error:', error);
    return Promise.reject(error);
  }
);

/**
 * API methods for interacting with the backend
 */
export default {
  /**
   * Fetch mock projects from the server
   */
  async getMockProjects(): Promise<Project[]> {
    try {
      const response = await apiClient.get('/projects/mock-data');
      
      // Transform the response data to match our Project interface
      return response.data.map((item: any) => ({
        id: item.id,
        name: item.name,
        createdAt: item.createdAt,
        profileCount: item.profileCount,
        recordingCount: item.recordingCount,
        alertCount: item.alertCount,
        sourceType: item.sourceType as 'JDK' | 'ASPROF',
        latestRecordingAt: item.latestRecordingAt,
        latestProfileAt: item.latestProfileAt
      }));
    } catch (error) {
      console.error('Failed to fetch mock projects:', error);
      throw error;
    }
  }
};

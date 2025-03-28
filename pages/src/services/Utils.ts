/**
 * Utility functions for the Jeffrey Admin UI
 */
export default class Utils {
  /**
   * Format a date string to a readable format
   * @param dateString ISO date string
   * @param includeTime Whether to include time in the output
   */
  static formatDate(dateString: string, includeTime = false): string {
    if (!dateString) return '';
    
    const date = new Date(dateString);
    const options: Intl.DateTimeFormatOptions = {
      year: 'numeric',
      month: 'short',
      day: 'numeric'
    };
    
    if (includeTime) {
      options.hour = '2-digit';
      options.minute = '2-digit';
    }
    
    return date.toLocaleDateString('en-US', options);
  }
  
  /**
   * Format file size in bytes to a human-readable format
   * @param bytes File size in bytes
   * @param decimals Number of decimal places
   */
  static formatFileSize(bytes: number, decimals = 2): string {
    if (bytes === 0) return '0 Bytes';
    
    const k = 1024;
    const dm = decimals < 0 ? 0 : decimals;
    const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB', 'PB', 'EB', 'ZB', 'YB'];
    
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    
    return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + ' ' + sizes[i];
  }
  
  /**
   * Format a duration in seconds to a human-readable format
   * @param seconds Duration in seconds
   */
  static formatDuration(seconds: number): string {
    if (seconds < 60) {
      return `${seconds.toFixed(0)}s`;
    }
    
    const minutes = Math.floor(seconds / 60);
    const remainingSeconds = seconds % 60;
    
    if (minutes < 60) {
      return `${minutes}m ${remainingSeconds.toFixed(0)}s`;
    }
    
    const hours = Math.floor(minutes / 60);
    const remainingMinutes = minutes % 60;
    
    return `${hours}h ${remainingMinutes}m ${remainingSeconds.toFixed(0)}s`;
  }
  
  /**
   * Truncate text to a maximum length and add ellipsis if needed
   * @param text The text to truncate
   * @param maxLength Maximum length
   */
  static truncateText(text: string, maxLength: number): string {
    if (!text || text.length <= maxLength) return text;
    
    return text.substring(0, maxLength) + '...';
  }
  
  /**
   * Generate a random ID (for testing/mock purposes)
   * @param prefix Optional prefix for the ID
   */
  static generateId(prefix = ''): string {
    return prefix + Math.random().toString(36).substring(2, 9);
  }
}
/**
 * A service to manage Bootstrap toasts
 */
export class ToastService {
  /**
   * Show a toast notification
   * @param id The ID of the toast element
   * @param message The message to display (optional, only if you want to update the content)
   */
  static show(id: string, message?: string): void {
    const toastEl = document.getElementById(id);
    
    if (!toastEl) {
      console.warn(`Toast element with ID '${id}' not found`);
      return;
    }
    
    // Update toast message if provided
    if (message) {
      const toastBody = toastEl.querySelector('.toast-body');
      if (toastBody) {
        toastBody.textContent = message;
      }
    }
    
    // Show the toast
    if (window.bootstrap && window.bootstrap.Toast) {
      const toast = new window.bootstrap.Toast(toastEl);
      toast.show();
    } else {
      console.warn('Bootstrap Toast component not found');
    }
  }
  
  /**
   * Show a success toast message
   */
  static success(summary: string, detail?: string): void {
    console.log(`%c✅ ${summary}`, 'color: green; font-weight: bold');
    if (detail) {
      console.log(`   ${detail}`);
    }
    
    // In a real implementation, this would create and show a Bootstrap toast
    // with success styling
  }
  
  /**
   * Show an information toast message
   */
  static info(summary: string, detail?: string): void {
    console.log(`%cℹ️ ${summary}`, 'color: blue; font-weight: bold');
    if (detail) {
      console.log(`   ${detail}`);
    }
    
    // In a real implementation, this would create and show a Bootstrap toast
    // with info styling
  }
  
  /**
   * Show a warning toast message
   */
  static warn(summary: string, detail?: string): void {
    console.log(`%c⚠️ ${summary}`, 'color: orange; font-weight: bold');
    if (detail) {
      console.log(`   ${detail}`);
    }
    
    // In a real implementation, this would create and show a Bootstrap toast
    // with warning styling
  }
  
  /**
   * Show an error toast message
   */
  static error(summary: string, detail?: string): void {
    console.log(`%c❌ ${summary}`, 'color: red; font-weight: bold');
    if (detail) {
      console.log(`   ${detail}`);
    }
    
    // In a real implementation, this would create and show a Bootstrap toast
    // with error styling
  }
}

export default ToastService;
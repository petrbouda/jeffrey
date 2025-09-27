/**
 * A service to manage Bootstrap toasts for modern notifications
 */
export class ToastService {
  private static readonly AUTO_HIDE_DELAY = 3000; // 3 seconds

  /**
   * Create a toast element styled like repository rows
   */
  private static createToast(summary: string, detail: string | undefined, toastClass: string): HTMLElement {
    const id = `toast-${Date.now()}`;
    const toast = document.createElement('div');
    toast.id = id;
    toast.className = `toast ${toastClass}`;
    toast.setAttribute('role', 'alert');
    toast.setAttribute('aria-live', 'assertive');
    toast.setAttribute('aria-atomic', 'true');
    
    // Create toast header
    const header = document.createElement('div');
    header.className = 'toast-header';
    
    // Get icon based on toast type
    let icon = '';
    switch (toastClass) {
      case 'toast-success': icon = '✓'; break;
      case 'toast-info': icon = 'ⓘ'; break;
      case 'toast-warning': icon = '⚠'; break;
      case 'toast-danger': icon = '✕'; break;
    }
    
    // Add title with optional icon
    const title = document.createElement('strong');
    title.className = 'me-auto';
    
    if (icon) {
      const iconSpan = document.createElement('span');
      iconSpan.className = 'toast-icon';
      iconSpan.textContent = icon;
      
      title.appendChild(iconSpan);
      title.appendChild(document.createTextNode(' ' + summary));
    } else {
      title.textContent = summary;
    }
    
    header.appendChild(title);
    
    // Add close button
    const closeBtn = document.createElement('button');
    closeBtn.type = 'button';
    closeBtn.className = 'btn-close';
    closeBtn.setAttribute('data-bs-dismiss', 'toast');
    closeBtn.setAttribute('aria-label', 'Close');
    header.appendChild(closeBtn);
    
    toast.appendChild(header);
    
    // Add body if detail is provided
    if (detail) {
      const body = document.createElement('div');
      body.className = 'toast-body';
      body.textContent = detail;
      toast.appendChild(body);
    }
    
    return toast;
  }

  /**
   * Show a toast notification
   */
  private static showToast(summary: string, detail: string | undefined, toastClass: string): void {
    // Get or create container
    let container = document.getElementById('toast-container');
    if (!container) {
      container = document.createElement('div');
      container.id = 'toast-container';
      container.className = 'toast-container';
      document.body.appendChild(container);
    }
    
    // Create toast
    const toast = this.createToast(summary, detail, toastClass);
    container.appendChild(toast);
    
    // Initialize toast with Bootstrap
    import('bootstrap').then((bootstrap) => {
      const bsToast = new bootstrap.Toast(toast, {
        autohide: true,
        delay: this.AUTO_HIDE_DELAY,
        animation: true
      });
      
      // Add event listener for remove from DOM after hiding
      toast.addEventListener('hidden.bs.toast', () => {
        setTimeout(() => {
          toast.remove();
        }, 100); // Small delay to allow for animation completion
      });
      
      // Show the toast
      bsToast.show();
    }).catch(() => {
      console.error('Failed to load Bootstrap for toast notifications');
      // Fallback - simple display and remove after delay
      toast.style.display = 'block';
      setTimeout(() => {
        toast.classList.add('hide');
        setTimeout(() => toast.remove(), 100);
      }, this.AUTO_HIDE_DELAY);
    });
  }

  /**
   * Show a success toast message
   */
  static success(summary: string, detail?: string): void {
    console.log(`%c✓ ${summary}`, 'color: #28a745; font-weight: bold');
    if (detail) {
      console.log(`   ${detail}`);
    }
    
    this.showToast(summary, detail, 'toast-success');
  }
  
  /**
   * Show an information toast message
   */
  static info(summary: string, detail?: string): void {
    console.log(`%cⓘ ${summary}`, 'color: #17a2b8; font-weight: bold');
    if (detail) {
      console.log(`   ${detail}`);
    }
    
    this.showToast(summary, detail, 'toast-info');
  }
  
  /**
   * Show a warning toast message
   */
  static warn(summary: string, detail?: string): void {
    console.log(`%c⚠ ${summary}`, 'color: #ffc107; font-weight: bold');
    if (detail) {
      console.log(`   ${detail}`);
    }
    
    this.showToast(summary, detail, 'toast-warning');
  }
  
  /**
   * Show an error toast message
   */
  static error(summary: string, detail?: string): void {
    console.log(`%c✕ ${summary}`, 'color: #dc3545; font-weight: bold');
    if (detail) {
      console.log(`   ${detail}`);
    }
    
    this.showToast(summary, detail, 'toast-danger');
  }
}

export default ToastService;

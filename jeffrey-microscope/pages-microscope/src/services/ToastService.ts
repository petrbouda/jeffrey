/**
 * Toast notification service — accent bar style with progress countdown.
 */
export class ToastService {
  private static readonly AUTO_HIDE_DELAY = 3000;

  private static readonly ICONS: Record<string, string> = {
    'toast-success': '✓',
    'toast-info': 'ⓘ',
    'toast-warning': '⚠',
    'toast-danger': '✕',
  };

  private static createToast(
    summary: string,
    detail: string | undefined,
    toastClass: string
  ): HTMLElement {
    const id = `toast-${Date.now()}`;
    const toast = document.createElement('div');
    toast.id = id;
    toast.className = `toast ${toastClass}`;
    toast.setAttribute('role', 'alert');
    toast.setAttribute('aria-live', 'assertive');
    toast.setAttribute('aria-atomic', 'true');

    // Accent bar
    const accent = document.createElement('div');
    accent.className = 'toast-accent';
    toast.appendChild(accent);

    // Content wrapper
    const content = document.createElement('div');
    content.className = 'toast-content';

    // Icon
    const icon = document.createElement('div');
    icon.className = 'toast-icon';
    icon.textContent = this.ICONS[toastClass] ?? '';
    content.appendChild(icon);

    // Text block
    const text = document.createElement('div');
    text.className = 'toast-text';

    const title = document.createElement('div');
    title.className = 'toast-title';
    title.textContent = summary;
    text.appendChild(title);

    if (detail) {
      const detailEl = document.createElement('div');
      detailEl.className = 'toast-detail';
      detailEl.textContent = detail;
      text.appendChild(detailEl);
    }

    content.appendChild(text);

    // Close button
    const closeBtn = document.createElement('button');
    closeBtn.className = 'toast-close';
    closeBtn.setAttribute('aria-label', 'Close');
    closeBtn.innerHTML = '&times;';
    content.appendChild(closeBtn);

    toast.appendChild(content);

    // Progress bar
    const progress = document.createElement('div');
    progress.className = 'toast-progress';
    const fill = document.createElement('div');
    fill.className = 'toast-progress-fill';
    progress.appendChild(fill);
    toast.appendChild(progress);

    return toast;
  }

  private static showToast(
    summary: string,
    detail: string | undefined,
    toastClass: string,
    duration?: number,
  ): void {
    let container = document.getElementById('toast-container');
    if (!container) {
      container = document.createElement('div');
      container.id = 'toast-container';
      container.className = 'toast-container';
      document.body.appendChild(container);
    }

    const toast = this.createToast(summary, detail, toastClass);
    container.appendChild(toast);

    // Close button handler
    const closeBtn = toast.querySelector('.toast-close');
    if (closeBtn) {
      closeBtn.addEventListener('click', () => dismissToast(toast));
    }

    // Auto-hide after delay
    const delay = duration ?? this.AUTO_HIDE_DELAY;
    const timer = setTimeout(() => dismissToast(toast), delay);

    function dismissToast(el: HTMLElement) {
      clearTimeout(timer);
      el.classList.add('hide');
      setTimeout(() => el.remove(), 300);
    }
  }

  static success(summary: string, detail?: string, duration?: number): void {
    console.log(`%c✓ ${summary}`, 'color: #00d27a; font-weight: bold');
    if (detail) console.log(`   ${detail}`);
    this.showToast(summary, detail, 'toast-success', duration);
  }

  static info(summary: string, detail?: string, duration?: number): void {
    console.log(`%cⓘ ${summary}`, 'color: #0ea5e9; font-weight: bold');
    if (detail) console.log(`   ${detail}`);
    this.showToast(summary, detail, 'toast-info', duration);
  }

  static warn(summary: string, detail?: string, duration?: number): void {
    console.log(`%c⚠ ${summary}`, 'color: #f5803e; font-weight: bold');
    if (detail) console.log(`   ${detail}`);
    this.showToast(summary, detail, 'toast-warning', duration);
  }

  static error(summary: string, detail?: string, duration?: number): void {
    console.log(`%c✕ ${summary}`, 'color: #e63757; font-weight: bold');
    if (detail) console.log(`   ${detail}`);
    this.showToast(summary, detail, 'toast-danger', duration);
  }
}

export default ToastService;

// Global type declarations

declare global {
  interface Window {
    bootstrap: {
      Toast: any;
      Tooltip: any;
      Popover: any;
      Modal: any;
    };
  }
}

export {};
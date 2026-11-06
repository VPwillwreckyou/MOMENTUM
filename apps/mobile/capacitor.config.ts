import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.momentum.app',
  appName: 'MOMENTUM',
  webDir: '../web/dist',
  backgroundColor: '#070a10',
  server: {
    androidScheme: 'https',
    iosScheme: 'capacitor',
  },
  android: {
    allowMixedContent: false,
  },
  ios: {
    // Safe areas are handled in CSS via env(safe-area-inset-*).
    contentInset: 'never',
  },
  plugins: {
    Keyboard: {
      // Resize the webview so form fields and sheet footers stay above the keyboard.
      resize: 'native',
      resizeOnFullScreen: true,
    },
  },
};

export default config;

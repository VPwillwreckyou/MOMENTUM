import { useEffect, useState } from 'react';

interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

let deferredPrompt: BeforeInstallPromptEvent | null = null;

export function useInstallPrompt(): {
  canInstall: boolean;
  promptInstall: () => Promise<boolean>;
  dismiss: () => void;
} {
  const [canInstall, setCanInstall] = useState<boolean>(deferredPrompt !== null);

  useEffect(() => {
    const handler = (event: Event) => {
      event.preventDefault();
      deferredPrompt = event as BeforeInstallPromptEvent;
      setCanInstall(true);
    };
    window.addEventListener('beforeinstallprompt', handler);
    return () => window.removeEventListener('beforeinstallprompt', handler);
  }, []);

  const promptInstall = async (): Promise<boolean> => {
    if (!deferredPrompt) return false;
    await deferredPrompt.prompt();
    const choice = await deferredPrompt.userChoice;
    deferredPrompt = null;
    setCanInstall(false);
    return choice.outcome === 'accepted';
  };

  const dismiss = (): void => {
    deferredPrompt = null;
    setCanInstall(false);
  };

  return { canInstall, promptInstall, dismiss };
}

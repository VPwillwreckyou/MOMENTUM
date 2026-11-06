import { Component, type ErrorInfo, type ReactNode } from 'react';
import { LogoMark } from './ui/Logo';
import { Button } from './ui/Button';

interface State {
  hasError: boolean;
  message: string;
}

export class ErrorBoundary extends Component<{ children: ReactNode }, State> {
  state: State = { hasError: false, message: '' };

  static getDerivedStateFromError(error: unknown): State {
    return { hasError: true, message: error instanceof Error ? error.message : 'An unexpected error occurred.' };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('MOMENTUM interface error', error, info.componentStack);
  }

  render() {
    if (!this.state.hasError) return this.props.children;
    return (
      <div className="app-error">
        <div className="app-error__card">
          <LogoMark size={44} />
          <span className="eyebrow">Interface interrupted</span>
          <h1>Something broke on the surface.</h1>
          <p>
            Your goals are stored separately and should be intact. Reload the interface to reconnect. If the problem repeats,
            export a backup from a working build before clearing browser data.
          </p>
          <code>{this.state.message}</code>
          <div className="app-error__actions">
            <Button variant="primary" onClick={() => window.location.reload()}>
              Reload interface
            </Button>
            <Button variant="ghost" onClick={() => window.location.assign('/')}>
              Go to overview
            </Button>
          </div>
        </div>
      </div>
    );
  }
}

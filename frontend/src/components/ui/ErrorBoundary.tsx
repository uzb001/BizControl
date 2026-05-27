'use client';

import React from 'react';
import { AlertTriangle, RotateCcw } from 'lucide-react';

interface Props {
  children: React.ReactNode;
  /** Optional custom fallback. Receives the error and a reset callback. */
  fallback?: (error: Error, reset: () => void) => React.ReactNode;
  /** Optional label to identify which area failed (for logging). */
  area?: string;
}

interface State {
  error: Error | null;
}

/**
 * Generic React error boundary. Catches render/runtime errors in its subtree so
 * a single broken widget can never take down the whole page with the white
 * "Application error" screen. Used to wrap dashboard widgets and other app areas.
 */
export default class ErrorBoundary extends React.Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { error: null };
  }

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: React.ErrorInfo) {
    // Surface to the console for debugging; never rethrow.
    // eslint-disable-next-line no-console
    console.error(`[ErrorBoundary${this.props.area ? ` · ${this.props.area}` : ''}]`, error, info?.componentStack);
  }

  reset = () => this.setState({ error: null });

  render() {
    const { error } = this.state;
    if (error) {
      if (this.props.fallback) return this.props.fallback(error, this.reset);
      return (
        <div className="card p-6 flex flex-col items-center justify-center text-center gap-3">
          <div className="w-11 h-11 rounded-full bg-red-50 dark:bg-red-500/10 flex items-center justify-center">
            <AlertTriangle size={20} className="text-red-500" />
          </div>
          <div>
            <p className="text-sm font-semibold" style={{ color: 'rgb(var(--color-text-primary))' }}>
              Something went wrong here
            </p>
            <p className="text-xs mt-1" style={{ color: 'rgb(var(--color-text-muted))' }}>
              This section could not be displayed. The rest of the page still works.
            </p>
          </div>
          <button onClick={this.reset} className="btn-secondary btn-sm gap-1.5">
            <RotateCcw size={13} /> Try again
          </button>
        </div>
      );
    }
    return this.props.children;
  }
}

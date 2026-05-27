import React from 'react';
import { Inbox } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';

interface EmptyStateProps {
  /** Pass a Lucide icon component for a premium look. Legacy emoji strings are
   *  ignored and replaced with a refined default icon. */
  icon?: LucideIcon | React.ReactNode | string;
  title: string;
  description?: string;
  action?: React.ReactNode;
}

export default function EmptyState({ icon, title, description, action }: EmptyStateProps) {
  // Render an icon safely. Lucide icons are forwardRef components (typeof === 'object'),
  // so we must NOT render them as a raw child. Distinguish: rendered element vs component type vs string.
  let iconNode: React.ReactNode;
  if (icon == null || typeof icon === 'string') {
    iconNode = <Inbox size={26} strokeWidth={1.75} />;          // default (ignore legacy emoji strings)
  } else if (React.isValidElement(icon)) {
    iconNode = icon;                                            // already an element, e.g. <Foo/>
  } else {
    const Icon = icon as LucideIcon;                           // a component type (function OR forwardRef)
    iconNode = <Icon size={26} strokeWidth={1.75} />;
  }

  return (
    <div className="flex flex-col items-center justify-center py-16 px-4 text-center animate-fade-in-up">
      <div
        className="w-14 h-14 rounded-2xl flex items-center justify-center mb-4"
        style={{
          background: 'rgb(var(--color-primary) / 0.08)',
          color: 'rgb(var(--color-primary))',
          border: '1px solid rgb(var(--color-primary) / 0.15)',
        }}
      >
        {iconNode}
      </div>
      <h3 className="text-base font-semibold mb-1" style={{ color: 'rgb(var(--color-text-primary))' }}>
        {title}
      </h3>
      {description && (
        <p className="text-sm mb-5 max-w-xs" style={{ color: 'rgb(var(--color-text-muted))' }}>
          {description}
        </p>
      )}
      {action}
    </div>
  );
}

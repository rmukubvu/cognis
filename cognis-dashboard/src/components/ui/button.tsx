import type { ButtonHTMLAttributes } from 'react';
import { cn } from '../../lib/utils';

type ButtonVariant = 'default' | 'outline';

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant;
};

export function Button({ className, variant = 'default', ...props }: ButtonProps) {
  return <button className={cn('ui-button', `ui-button--${variant}`, className)} {...props} />;
}

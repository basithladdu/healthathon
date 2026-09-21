'use client';

import Link from 'next/link';
import type { ComponentProps } from 'react';
import { CARE_ROUTES, type CareView } from './care-routes';

export function CareRouteLink({ view, onOpen, ...props }: Omit<ComponentProps<typeof Link>, 'href' | 'onClick'> & { view: CareView; onOpen?: () => void }) {
  return <Link {...props} href={CARE_ROUTES[view]} onClick={(event) => {
    if (onOpen && event.button === 0 && !event.metaKey && !event.ctrlKey && !event.shiftKey && !event.altKey) {
      event.preventDefault();
      onOpen();
    }
  }} />;
}

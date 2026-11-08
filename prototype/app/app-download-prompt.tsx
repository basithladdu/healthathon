'use client';

import React from 'react';
import { usePathname } from 'next/navigation';
import { AppDownloadDialog } from './landing-page';

const PLAY_URL = 'https://play.google.com/store/apps/details?id=in.wedevit.sahara';

// The one-time "Get the Android app" dialog on every web page, and a small Play
// button in the corner of the sign-in page (kept off inner pages so it never covers buttons). Hidden inside the Android app's WebView and for ?app=1.
export function AppDownloadPrompt(): React.JSX.Element | null {
  const pathname = usePathname();
  const [web, setWeb] = React.useState(false);
  React.useEffect(() => {
    try {
      if (new URLSearchParams(window.location.search).get('app') === '1') return;
      if (/;\s*wv\)|Sahara-Android/i.test(navigator.userAgent)) return;
    } catch { /* ignore */ }
    setWeb(true);
  }, []);
  if (!web) return null;
  return (
    <>
      <AppDownloadDialog />
      {pathname === '/' ? <a className="sahara-get-app" href={PLAY_URL} target="_blank" rel="noopener" aria-label="Get the Sahara app on Google Play">
        <svg width="18" height="20" viewBox="0 0 24 26" aria-hidden="true" focusable="false">
          <path d="M2 1.2v23.6L13.2 13z" fill="#00d3ff" />
          <path d="M13.2 13 17 9.2 3.6.6C3 .3 2.4.5 2 1.2z" fill="#00f076" />
          <path d="M13.2 13 2 24.8c.4.7 1 .9 1.6.6L17 16.8z" fill="#ff3a44" />
          <path d="M17 9.2 13.2 13 17 16.8l4.4-2.5c1.2-.7 1.2-2 0-2.6z" fill="#ffd400" />
        </svg>
        <span>Get the app</span>
      </a> : null}
    </>
  );
}

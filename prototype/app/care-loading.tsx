import './care-loading.css';

export function CareLoading({ hindi = false, className = '' }: { hindi?: boolean; className?: string }) {
  return <div className={`care-loading ${className}`.trim()} lang={hindi ? 'hi' : 'en'} role="status" aria-live="polite" aria-busy="true">
    <div className="care-loading-content">
      <div className="care-loading-art" aria-hidden="true">
        <span className="care-loading-halo" />
        <span className="care-loading-sun" />
        <span className="care-loading-leaf" />
        <svg className="care-loading-mark" viewBox="0 0 100 100" fill="none">
          <rect x="5" y="9" width="90" height="90" rx="30" fill="#175E54" opacity=".16" />
          <rect x="5" y="2" width="90" height="90" rx="30" fill="#267E72" />
          <path d="M50 73C39 65 24 52 24 39a15 15 0 0 1 26-10 15 15 0 0 1 26 10c0 13-15 26-26 34Z" fill="#FFE0A0" />
          <path d="M50 73V46m0 13-9-8m9 2 8-7" stroke="#267E72" strokeWidth="4" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
        <span className="care-loading-petal" />
      </div>
      <p className="care-loading-name">Saanthvana</p>
      <div className="care-loading-dots" aria-hidden="true"><span /><span /><span /></div>
      <span className="care-loading-status">{hindi ? 'आपकी देखभाल खुल रही है' : 'Opening your care'}</span>
    </div>
  </div>;
}

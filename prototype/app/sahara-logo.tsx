import Image from 'next/image';

export function SaharaLogo({ className, priority = false }: { className?: string; priority?: boolean }) {
  return (
    <Image
      src="/sahara-community-logo.png"
      alt=""
      aria-hidden="true"
      width={512}
      height={512}
      sizes="64px"
      className={className}
      priority={priority}
    />
  );
}

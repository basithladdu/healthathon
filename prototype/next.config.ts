import type { NextConfig } from 'next';

const nextConfig: NextConfig = {
  allowedDevOrigins: ['127.0.0.1'],
  async headers() {
    return [{
      source: '/android-account',
      headers: [
        { key: 'X-Frame-Options', value: 'DENY' },
        { key: 'Content-Security-Policy', value: "frame-ancestors 'none'" },
        { key: 'Referrer-Policy', value: 'no-referrer' },
        { key: 'Cache-Control', value: 'private, no-store, max-age=0' },
      ],
    }];
  },
  async redirects() {
    return [{
      source: '/:path*',
      has: [{ type: 'host', value: 'saathi.wedevit.in' }],
      destination: 'https://continuity-loop-healthathon.vercel.app/:path*',
      permanent: true,
    }];
  },
};

export default nextConfig;

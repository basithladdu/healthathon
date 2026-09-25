import type { NextConfig } from 'next';

const nextConfig: NextConfig = {
  allowedDevOrigins: ['127.0.0.1'],
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

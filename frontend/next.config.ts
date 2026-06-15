import type { NextConfig } from "next";

const PERSPECTIVE_PACKAGES = [
  "@perspective-dev/client",
  "@perspective-dev/server",
  "@perspective-dev/viewer",
  "@perspective-dev/viewer-charts",
  "@perspective-dev/viewer-datagrid",
];

const nextConfig: NextConfig = {
  output: "standalone",
  transpilePackages: PERSPECTIVE_PACKAGES,
  webpack: (config) => {
    config.module.rules.push({
      test: /perspective-(server|js|viewer)\.wasm$/,
      type: "asset/resource",
    });
    config.resolve = config.resolve ?? {};
    config.resolve.extensionAlias = {
      ...config.resolve.extensionAlias,
      ".js": [".ts", ".js"],
    };
    return config;
  },
  async headers() {
    return [
      {
        source: "/(.*)",
        headers: [
          { key: "X-Frame-Options", value: "DENY" },
          { key: "X-Content-Type-Options", value: "nosniff" },
          { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
        ],
      },
    ];
  },
};

export default nextConfig;

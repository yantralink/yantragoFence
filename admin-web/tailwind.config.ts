import type { Config } from 'tailwindcss';

const config: Config = {
  content: [
    './src/pages/**/*.{js,ts,jsx,tsx,mdx}',
    './src/components/**/*.{js,ts,jsx,tsx,mdx}',
    './src/app/**/*.{js,ts,jsx,tsx,mdx}',
  ],
  theme: {
    extend: {
      colors: {
        primary: {
          DEFAULT: '#1a73e8',
          dark: '#0d47a1',
        },
        status: {
          online: '#4caf50',
          offline: '#9e9e9e',
          warning: '#ff9800',
          error: '#f44336',
          fault: '#e91e63',
        },
      },
    },
  },
  plugins: [],
};

export default config;

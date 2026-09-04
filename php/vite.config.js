import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';

export default defineConfig({
    base: '/build/',
    server: {
        host: '127.0.0.1', // Avoid IPv6 [::1] so CSP script-src matches without parsing issues
    },
    resolve: {
        alias: {
            '@': path.resolve(__dirname, './resources/js'),
        },
    },
    build: {
        outDir: '../java-backend/src/main/resources/static/build',
        emptyOutDir: true,
        manifest: 'manifest.json',
        rollupOptions: {
            input: 'resources/js/app.jsx',
        },
    },
    plugins: [
        react(),
    ],
});

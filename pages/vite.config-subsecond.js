import {fileURLToPath, URL} from 'node:url';

import {defineConfig} from 'vite';
import vue from '@vitejs/plugin-vue';
import {viteSingleFile} from "vite-plugin-singlefile";

// https://vitejs.dev/config/
export default defineConfig(() => {
    return {
        plugins: [vue(), viteSingleFile()],
        resolve: {
            alias: {
                '@': fileURLToPath(new URL('./src', import.meta.url)),
                '@public': fileURLToPath(new URL('./public', import.meta.url)),
            }
        },
        build: {
            rollupOptions: {
                input: {
                    app: './subsecond.html',
                },
            },
            outDir: './target/dist/subsecond'
        }
    };
});

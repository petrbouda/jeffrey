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
                '@': fileURLToPath(new URL('./src', import.meta.url))
            }
        },
        build: {
            rollupOptions: {
                input: {
                    app: './flamegraph.html',
                },
            },
            outDir: './target/dist/flame'
        }
    };
});
// ,
// {
//     name: 'index-html-build-replacement',
//         async transformIndexHtml() {
//     return await fs.readFileSync('./flamegraph.html', 'utf8')
// }
// }

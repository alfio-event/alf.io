import { defineConfig } from "vite";
import { viteStaticCopy } from 'vite-plugin-static-copy'


export default defineConfig({
    plugins:
    viteStaticCopy({
      targets: [
        {
          src: 'node_modules/@shoelace-style/shoelace/dist/assets/icons/*.svg',
          dest: 'shoelace/assets/icons'
        }
      ]
    }),
    build: {
        // generate .vite/manifest.json in outDir
        manifest: true,
        rollupOptions: {
            // overwrite default .html entry
            input: 'src/main.ts',
        },
    },
    server: {
        origin: 'http://127.0.0.1:8080',
    },
});
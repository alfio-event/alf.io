import { defineConfig } from 'vitest/config';

// Unit tests for the browser-independent modules (e.g. the router's URL functions). Pages are covered by the E2E tests.
export default defineConfig({
    test: {
        include: ['src/**/*.test.ts'],
        environment: 'node',
        // the English texts of the admin, see service/i18n.ts
        setupFiles: ['src/test-setup.ts'],
    },
});

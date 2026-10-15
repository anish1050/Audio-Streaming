import { fileURLToPath } from 'node:url'
import { defineConfig } from 'vitest/config'

const src = (dir: string) => fileURLToPath(new URL(`./src/${dir}`, import.meta.url))

export default defineConfig({
  test: {
    globalSetup: ['./tests/global-setup.ts'],
    include: ['tests/**/*.test.ts'],
    testTimeout: 30_000,
    hookTimeout: 60_000,
    fileParallelism: false,
  },
  resolve: {
    alias: {
      '#helpers': src('helpers'),
      '#middleware': src('middleware'),
      '#routes': src('routes'),
      '#types': src('types'),
    },
  },
})

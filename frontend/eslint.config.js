import js from '@eslint/js';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import globals from 'globals';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  // 생성물·빌드 산출물은 검사하지 않는다
  //  - src/generated  : npm run gen:api 가 만든다 (손으로 고치지 않는다)
  //  - public/mockServiceWorker.js : npx msw init 이 만든다
  { ignores: ['dist', 'src/generated', 'public/mockServiceWorker.js', 'prototype/**'] },
  {
    extends: [js.configs.recommended, ...tseslint.configs.recommended],
    files: ['**/*.{ts,tsx}'],
    languageOptions: {
      ecmaVersion: 2022,
      globals: globals.browser,
    },
    plugins: {
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],
      // CLAUDE.md §5 — any 금지. 경고가 아니라 오류로 둬 CI 에서 걸리게 한다
      '@typescript-eslint/no-explicit-any': 'error',
      // 쓰지 않는 변수는 _ 로 시작할 때만 허용
      '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_' }],
    },
  },
);

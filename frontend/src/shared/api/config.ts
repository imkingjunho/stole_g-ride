export const isMockMode = import.meta.env.VITE_API_MODE !== 'real';
export const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(
  /\/$/,
  '',
);

// Proxy del servidor de desarrollo hacia el backend de ejemplo (BACKEND_URL, por defecto :8080).
export default {
  '/api': {
    target: process.env.BACKEND_URL ?? 'http://localhost:8080',
    secure: false,
  },
};

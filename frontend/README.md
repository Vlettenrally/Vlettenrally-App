# Vlettenrally-App - Vlettenrally

React + TypeScript + Vite application for rally participants.

## Development

### Quick Start

```bash
npm install
npm run dev
```

### Mock Mode (Recommended for Frontend Development)

The app supports a **mock mode** that provides pre-filled data without needing the backend running. This is ideal for frontend development and testing.

**Enable mock mode** by toggling the value in the committed `.env.development`
file (Vite loads it automatically in dev — no need to create a new `.env` file):

```bash
# in frontend/.env.development
VITE_USE_MOCKS=true   # set to 'false' to use the real API instead
```

When mock mode is on, the app shows a launcher that lets you open the **admin**
or **player** interface (each in a new tab) without a backend or login.

You can also override it for a single run:

```bash
VITE_USE_MOCKS=true npm run dev
```

### Mock Data Includes

- **Team**: "De Watervlooien" (Boot 42) with 350 points
- **Rival Team**: "Windkracht Tien" (Boot 17)
- **Game**: Active rally "Vlettenrally 2026"
- **Checkpoints**: 5 checkpoints across Amsterdam
- **Messages**: Various message types (checkpoint, rival, achievement, rallydesk)
- **Achievements**: 4 completed checkpoints

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `VITE_USE_MOCKS` | Enable mock data mode | `false` |
| `VITE_API_URL` | Backend API URL | `http://localhost:8080/api/v1` |
| `VITE_WS_URL` | WebSocket URL | `ws://localhost:8080/ws` |

---

## Original Vite Template Info

This template provides a minimal setup to get React working in Vite with HMR and some ESLint rules.

Currently, two official plugins are available:

- [@vitejs/plugin-react](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react) uses [Babel](https://babeljs.io/) (or [oxc](https://oxc.rs) when used in [rolldown-vite](https://vite.dev/guide/rolldown)) for Fast Refresh
- [@vitejs/plugin-react-swc](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react-swc) uses [SWC](https://swc.rs/) for Fast Refresh

## React Compiler

The React Compiler is not enabled on this template because of its impact on dev & build performances. To add it, see [this documentation](https://react.dev/learn/react-compiler/installation).

## Expanding the ESLint configuration

If you are developing a production application, we recommend updating the configuration to enable type-aware lint rules:

```js
export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      // Other configs...

      // Remove tseslint.configs.recommended and replace with this
      tseslint.configs.recommendedTypeChecked,
      // Alternatively, use this for stricter rules
      tseslint.configs.strictTypeChecked,
      // Optionally, add this for stylistic rules
      tseslint.configs.stylisticTypeChecked,

      // Other configs...
    ],
    languageOptions: {
      parserOptions: {
        project: ['./tsconfig.node.json', './tsconfig.app.json'],
        tsconfigRootDir: import.meta.dirname,
      },
      // other options...
    },
  },
])
```

You can also install [eslint-plugin-react-x](https://github.com/Rel1cx/eslint-react/tree/main/packages/plugins/eslint-plugin-react-x) and [eslint-plugin-react-dom](https://github.com/Rel1cx/eslint-react/tree/main/packages/plugins/eslint-plugin-react-dom) for React-specific lint rules:

```js
// eslint.config.js
import reactX from 'eslint-plugin-react-x'
import reactDom from 'eslint-plugin-react-dom'

export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      // Other configs...
      // Enable lint rules for React
      reactX.configs['recommended-typescript'],
      // Enable lint rules for React DOM
      reactDom.configs.recommended,
    ],
    languageOptions: {
      parserOptions: {
        project: ['./tsconfig.node.json', './tsconfig.app.json'],
        tsconfigRootDir: import.meta.dirname,
      },
      // other options...
    },
  },
])
```

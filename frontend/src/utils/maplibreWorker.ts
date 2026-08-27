/**
 * Points maplibre-gl at a worker bundle Vite actually emits.
 *
 * v6 ships its web worker as a separate ESM file and locates it via
 * `import.meta.url`, which does not survive Vite's bundling — it ends up looking
 * for the worker next to the built chunk, where no such file exists. Without
 * this the map mounts and then renders nothing.
 *
 * `?worker&url` routes the file through Vite's worker pipeline, following its
 * import of the sibling `maplibre-gl-shared.mjs`, and hands back the URL of the
 * emitted chunk. Plain `?url` is not enough: it copies the worker verbatim
 * without that sibling, so the worker dies on its first import and no tiles load.
 */
import { setWorkerUrl } from 'maplibre-gl';
import workerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?worker&url';

setWorkerUrl(workerUrl);

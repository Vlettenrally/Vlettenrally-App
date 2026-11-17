# Notices

## Vlettenrally App

Copyright (C) 2026 Joost van Viegen and contributors.

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License, version 3, as published by the Free Software Foundation. It is distributed without any warranty. See [LICENSE](LICENSE) for the full text.

## Branding: not covered by the AGPL

The name "Vlettenrally" and the artwork listed below belong to the organisers of the Vlettenrally. They are included in this repository with permission so that the app builds and runs as deployed, but they are **not** licensed under the AGPL or any other open-source licence. All rights are reserved by their owner.

If you fork or redeploy this software for another event or organisation, replace these files with your own artwork and choose your own name.

- `frontend/public/logo.svg`
- `frontend/public/boat.svg`
- `frontend/public/icon-192x192.png`
- `frontend/public/icon-512x512.png`
- `frontend/public/icon-512x512-maskable.png`
- `frontend/public/apple-touch-icon.png`
- `frontend/public/favicon.ico`
- `frontend/public/favicon-32x32.png`
- `frontend/public/favicon-16x16.png`
- `frontend/public/notification-icon-192x192.png`
- `frontend/public/notification-badge-96x96.png`
- `frontend/scripts/source-app-icon.png`

The icon files are generated from `source-app-icon.png` by `frontend/scripts/generate-notification-assets.py`, so replacing that one image and re-running the script regenerates the set.

### Renaming

The name is shown to users in these places:

- `frontend/src/locales/nl.json` and `frontend/src/locales/en.json`: most visible text, including page titles, game status messages and image alt texts. Search for "Vlettenrally".
- `frontend/vite.config.ts`: `name`, `short_name` and `description` in the PWA manifest, shown when the app is installed.
- `frontend/index.html`: the browser tab `<title>`.
- `frontend/src/components/TeamLayout.tsx` and `frontend/src/pages/participant/Checkin.tsx`: the name as a header in the team layout and on the check-in screen.
- `frontend/src/App.tsx` and `frontend/src/service-worker.ts`: the fallback title for push notifications. `App.tsx` also contains the Dutch notification permission prompt, and its theme has the brand colours.
- `frontend/src/components/LocationPickerMap.tsx`: the `User-Agent` sent to the Nominatim place search.

Deployment settings name the production domain (`game.vlettenrally.nl`), the Firebase project and the container images. A redeploy replaces these with its own values anyway: see `frontend/.env.production`, `api/src/main/resources/application.properties` (CORS origins, pre-registration and Traccar URLs), `tileserver/config.yaml`, `kubernetes/` and `.github/workflows/`.

Internal identifiers such as the Java package `nl.vlettenrally`, the `vlettenrally.*` configuration keys, database and Kubernetes resource names, and browser storage keys are never shown to users and do not need to change. Renaming the browser storage keys would also make existing installs lose their saved device and check-in state.

## Fonts

No font files are stored in this repository. The frontend build bundles the following fonts from npm and writes their licence texts to `licenses/` in the build output (see `fontLicenses` in `frontend/vite.config.ts`). The licences of all other bundled packages are written to `THIRD-PARTY-LICENSES.txt`.

- **Poppins**, Copyright 2020 The Poppins Project Authors (https://github.com/itfoundry/Poppins). SIL Open Font License 1.1. Via `@fontsource/poppins`.
- **Roboto**, Copyright 2011 The Roboto Project Authors (https://github.com/googlefonts/roboto-classic). SIL Open Font License 1.1. Via `@fontsource/roboto`.

Map labels use **Noto Sans** (SIL Open Font License 1.1), loaded at runtime as glyph ranges from https://fonts.openmaptiles.org and not redistributed here.

## Icons

User-interface icons come from Material Icons via `@mui/icons-material` (MIT License).

## Map style

`frontend/public/map-style.json` is a reduced derivative of [OSM Liberty](https://github.com/maputnik/osm-liberty), which is itself derived from [OSM Bright from Mapbox Open Styles](https://github.com/mapbox/mapbox-gl-styles/blob/master/LICENSE.md).

The style JSON retains the BSD licence of OSM Bright:

> Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
>
> * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
> * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
> * Neither the name of Mapbox nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
>
> THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

The look and feel of the map design is derived from OSM Bright, which is licensed under the Creative Commons Attribution 3.0 licence.

The style renders vector tiles in the [OpenMapTiles](https://openmaptiles.org/) schema, whose design is licensed under CC-BY 4.0. The required credit, "© OpenMapTiles © OpenStreetMap contributors", is shown in the corner of every map in the app.

## Map data

Map data © [OpenStreetMap contributors](https://www.openstreetmap.org/copyright), available under the Open Database License (ODbL). The tiles are built with [Planetiler](https://github.com/onthegomap/planetiler) from [Geofabrik](https://download.geofabrik.de/) extracts; see `tileserver/README.md`. Place search uses the OpenStreetMap Nominatim service.

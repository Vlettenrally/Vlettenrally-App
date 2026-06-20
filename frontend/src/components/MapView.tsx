/**
 * MapView Component - Shared MapLibre GL map component
 *
 * Renders a MapLibre GL map with support for team location markers
 * and checkpoint markers. Uses self-hosted vector tiles via Martin.
 */
import { useEffect, useRef, useCallback } from 'react';
import maplibregl from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import { resolveMapStyle, DEFAULT_CENTER } from '../utils/mapStyle';
import { formatRelativeTime } from '../utils/dateTime';
import turfCircle from '@turf/circle';

export interface MapMarker {
  id: string;
  lat: number;
  lng: number;
  label: string;
  color: string;
  type: 'team' | 'checkpoint';
  /** Zone shape for checkpoint markers */
  zoneType?: 'circle' | 'polygon';
  radiusMeters?: number;
  /** Polygon vertices as [[lat, lng], ...] */
  polygonPoints?: number[][];
  /** Hidden from teams — admin maps render these in a muted style */
  hidden?: boolean;
  /** Team marker: most recent location is older than the staleness threshold — rendered dimmed */
  stale?: boolean;
  /** Team marker: ISO timestamp of the most recent location fix, shown in the popup */
  locationTime?: string;
}

export interface MapTrack {
  teamId: string;
  color: string;
  /** Points as [lng, lat] in time-ascending order */
  points: Array<[number, number]>;
}

interface MapViewProps {
  markers: MapMarker[];
  center?: [number, number]; // [lng, lat]
  zoom?: number;
  className?: string;
  /** When provided, replaces the browser GeolocateControl with a button that flies to this team location */
  teamLocation?: [number, number]; // [lng, lat]
  tracks?: MapTrack[];
  /** Team marker shape. 'circle' (default) for live tracking maps; 'pin' for single-location callouts. */
  markerStyle?: 'circle' | 'pin';
}


/** Teardrop-shaped pin SVG, anchored at its bottom tip. Colour is fixed and matches the default team marker. */
const PIN_SVG = `<svg xmlns="http://www.w3.org/2000/svg" width="32" height="44" viewBox="0 0 32 44">
  <path d="M16 1C7.7 1 1 7.7 1 16c0 11 15 26 15 26s15-15 15-26C31 7.7 24.3 1 16 1z" fill="#1976d2" stroke="#ffffff" stroke-width="2"/>
  <circle cx="16" cy="16" r="5.5" fill="#ffffff"/>
</svg>`;
const PIN_IMAGE_ID = 'team-pin';

export function MapView({ markers, center, zoom, className, teamLocation, tracks, markerStyle = 'circle' }: MapViewProps) {
  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<maplibregl.Map | null>(null);
  const markersSourceRef = useRef<boolean>(false);
  const teamLocationRef = useRef(teamLocation);
  teamLocationRef.current = teamLocation;

  const updateMarkers = useCallback(() => {
    const map = mapRef.current;
    if (!map || !markersSourceRef.current) return;

    const teamFeatures = markers
      .filter(m => m.type === 'team')
      .map(m => ({
        type: 'Feature' as const,
        geometry: {
          type: 'Point' as const,
          coordinates: [m.lng, m.lat],
        },
        properties: {
          id: m.id,
          label: m.label,
          color: m.color,
          stale: m.stale ?? false,
          locationTime: m.locationTime ?? '',
        },
      }));

    const checkpointFeatures = markers
      .filter(m => m.type === 'checkpoint')
      .map(m => ({
        type: 'Feature' as const,
        geometry: {
          type: 'Point' as const,
          coordinates: [m.lng, m.lat],
        },
        properties: {
          id: m.id,
          label: m.label,
          color: m.color,
          hidden: m.hidden ?? false,
        },
      }));

    const teamSource = map.getSource('team-markers') as maplibregl.GeoJSONSource;
    if (teamSource) {
      teamSource.setData({
        type: 'FeatureCollection',
        features: teamFeatures,
      });
    }

    const checkpointSource = map.getSource('checkpoint-markers') as maplibregl.GeoJSONSource;
    if (checkpointSource) {
      checkpointSource.setData({
        type: 'FeatureCollection',
        features: checkpointFeatures,
      });
    }

    // Build zone boundary features (circles → polygon approximation, polygons → polygon)
    const zoneFeatures = markers
      .filter(m => m.type === 'checkpoint')
      .map(m => {
        if (m.zoneType === 'polygon' && m.polygonPoints && m.polygonPoints.length >= 3) {
          const coords = m.polygonPoints.map(p => [p[1], p[0]]); // [lng, lat]
          coords.push(coords[0]); // close ring
          return {
            type: 'Feature' as const,
            geometry: {
              type: 'Polygon' as const,
              coordinates: [coords],
            },
            properties: { id: m.id, label: m.label, hidden: m.hidden ?? false },
          };
        }
        // Default: circle
        const radius = m.radiusMeters ?? 50;
        const circle = turfCircle([m.lng, m.lat], radius / 1000, { steps: 64, units: 'kilometers' });
        return {
          type: 'Feature' as const,
          geometry: circle.geometry,
          properties: { id: m.id, label: m.label, hidden: m.hidden ?? false },
        };
      });

    const zoneSource = map.getSource('checkpoint-zones') as maplibregl.GeoJSONSource;
    if (zoneSource) {
      zoneSource.setData({ type: 'FeatureCollection', features: zoneFeatures });
    }
  }, [markers]);

  const updateTracks = useCallback(() => {
    const map = mapRef.current;
    if (!map || !markersSourceRef.current) return;

    const trackFeatures = (tracks ?? [])
      .filter(t => t.points.length >= 2)
      .map(t => ({
        type: 'Feature' as const,
        geometry: {
          type: 'LineString' as const,
          coordinates: t.points,
        },
        properties: {
          teamId: t.teamId,
          color: t.color,
        },
      }));

    const trackSource = map.getSource('team-tracks') as maplibregl.GeoJSONSource | undefined;
    if (trackSource) {
      trackSource.setData({ type: 'FeatureCollection', features: trackFeatures });
    }
  }, [tracks]);

  useEffect(() => {
    if (!mapContainerRef.current) return;

    // Resolve map style: use remote style URL if configured, otherwise local style with self-hosted tiles
    const stylePromise = resolveMapStyle();

    stylePromise.then(style => {
        const map = new maplibregl.Map({
          container: mapContainerRef.current!,
          style: style,
          center: center || DEFAULT_CENTER,
          zoom: zoom || 7,
          maxZoom: 16,
          maxPitch: 0,
          dragRotate: false,
          pitchWithRotate: false,
          attributionControl: {},
        });

        map.touchZoomRotate.disableRotation();

        map.addControl(new maplibregl.NavigationControl({ showCompass: false }), 'top-right');

        if (teamLocation) {
          // Custom locate button that flies to the team's last known tracker location
          class TeamLocateControl implements maplibregl.IControl {
            private _container: HTMLDivElement | undefined;
            private _map: maplibregl.Map | undefined;

            onAdd(map: maplibregl.Map) {
              this._map = map;
              this._container = document.createElement('div');
              this._container.className = 'maplibregl-ctrl maplibregl-ctrl-group';

              const button = document.createElement('button');
              button.type = 'button';
              button.title = 'Ga naar teamlocatie';
              button.setAttribute('aria-label', 'Ga naar teamlocatie');
              button.innerHTML = '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="8"/><circle cx="12" cy="12" r="3"/><line x1="12" y1="2" x2="12" y2="4"/><line x1="12" y1="20" x2="12" y2="22"/><line x1="2" y1="12" x2="4" y2="12"/><line x1="20" y1="12" x2="22" y2="12"/></svg>';
              button.style.display = 'flex';
              button.style.alignItems = 'center';
              button.style.justifyContent = 'center';
              button.style.cursor = 'pointer';

              button.addEventListener('click', () => {
                if (teamLocationRef.current) {
                  this._map?.flyTo({ center: teamLocationRef.current, zoom: 14 });
                }
              });

              this._container.appendChild(button);
              return this._container;
            }

            onRemove() {
              this._container?.parentNode?.removeChild(this._container);
              this._map = undefined;
            }
          }

          map.addControl(new TeamLocateControl(), 'top-right');
        } else {
          // Admin / fallback: use browser geolocation
          map.addControl(new maplibregl.GeolocateControl({
            positionOptions: { enableHighAccuracy: true },
            trackUserLocation: true,
          }), 'top-right');
        }

        map.on('load', () => {
          // Add team-tracks first so the line renders BENEATH team and checkpoint markers
          map.addSource('team-tracks', {
            type: 'geojson',
            data: { type: 'FeatureCollection', features: [] },
          });

          map.addLayer({
            id: 'team-tracks-line',
            type: 'line',
            source: 'team-tracks',
            layout: {
              'line-cap': 'round',
              'line-join': 'round',
            },
            paint: {
              'line-color': ['get', 'color'],
              'line-width': 3,
              'line-opacity': 0.75,
            },
          });

          // Add team markers source and layer
          map.addSource('team-markers', {
            type: 'geojson',
            data: { type: 'FeatureCollection', features: [] },
          });

          if (markerStyle === 'pin') {
            // Render team markers as a teardrop pin. Image is loaded async; MapLibre
            // will start rendering features as soon as the image is registered.
            const img = new Image(32, 44);
            img.onload = () => {
              if (!map.hasImage(PIN_IMAGE_ID)) {
                map.addImage(PIN_IMAGE_ID, img);
              }
            };
            img.src = `data:image/svg+xml;base64,${btoa(PIN_SVG)}`;

            map.addLayer({
              id: 'team-markers-pin',
              type: 'symbol',
              source: 'team-markers',
              layout: {
                'icon-image': PIN_IMAGE_ID,
                'icon-size': 0.9,
                'icon-anchor': 'bottom',
                'icon-allow-overlap': true,
                'icon-ignore-placement': true,
              },
            });

            map.addLayer({
              id: 'team-markers-label',
              type: 'symbol',
              source: 'team-markers',
              layout: {
                'text-field': ['get', 'label'],
                'text-font': ['Noto Sans Bold'],
                'text-size': 12,
                'text-offset': [0, -3.2],
                'text-anchor': 'bottom',
                'text-max-width': 8,
              },
              paint: {
                'text-color': '#222222',
                'text-halo-color': '#ffffff',
                'text-halo-width': 2,
              },
            });
          } else {
            map.addLayer({
              id: 'team-markers-circle',
              type: 'circle',
              source: 'team-markers',
              paint: {
                'circle-radius': 8,
                'circle-color': ['get', 'color'],
                'circle-stroke-width': 2,
                'circle-stroke-color': '#ffffff',
                // Dim teams whose latest location is stale (older than the threshold)
                'circle-opacity': ['case', ['get', 'stale'], 0.35, 1],
                'circle-stroke-opacity': ['case', ['get', 'stale'], 0.35, 1],
              },
            });

            map.addLayer({
              id: 'team-markers-label',
              type: 'symbol',
              source: 'team-markers',
              layout: {
                'text-field': ['get', 'label'],
                'text-font': ['Noto Sans Regular'],
                'text-size': 11,
                'text-offset': [0, 1.8],
                'text-anchor': 'top',
                'text-max-width': 8,
              },
              paint: {
                'text-color': '#333333',
                'text-halo-color': '#ffffff',
                'text-halo-width': 1.5,
                'text-opacity': ['case', ['get', 'stale'], 0.45, 1],
              },
            });
          }

          // Add checkpoint zone shapes (rendered BEFORE point markers so they appear underneath)
          map.addSource('checkpoint-zones', {
            type: 'geojson',
            data: { type: 'FeatureCollection', features: [] },
          });

          map.addLayer({
            id: 'checkpoint-zones-fill',
            type: 'fill',
            source: 'checkpoint-zones',
            paint: {
              'fill-color': ['case', ['get', 'hidden'], '#9e9e9e', '#ff9800'],
              'fill-opacity': 0.15,
            },
          });

          map.addLayer({
            id: 'checkpoint-zones-outline',
            type: 'line',
            source: 'checkpoint-zones',
            paint: {
              'line-color': ['case', ['get', 'hidden'], '#9e9e9e', '#ff9800'],
              'line-width': 2,
              'line-opacity': 0.6,
              'line-dasharray': [3, 2],
            },
          });

          // Add checkpoint markers source and layer
          map.addSource('checkpoint-markers', {
            type: 'geojson',
            data: { type: 'FeatureCollection', features: [] },
          });

          map.addLayer({
            id: 'checkpoint-markers-circle',
            type: 'circle',
            source: 'checkpoint-markers',
            paint: {
              'circle-radius': 10,
              'circle-color': ['case', ['get', 'hidden'], '#9e9e9e', '#ff9800'],
              'circle-stroke-width': 3,
              'circle-stroke-color': '#ffffff',
              'circle-opacity': 0.9,
            },
          });

          map.addLayer({
            id: 'checkpoint-markers-label',
            type: 'symbol',
            source: 'checkpoint-markers',
            layout: {
              'text-field': ['get', 'label'],
              'text-font': ['Noto Sans Bold'],
              'text-size': 12,
              'text-offset': [0, 2],
              'text-anchor': 'top',
              'text-max-width': 8,
            },
            paint: {
              'text-color': '#e65100',
              'text-halo-color': '#ffffff',
              'text-halo-width': 2,
            },
          });

          markersSourceRef.current = true;
          updateMarkers();
          updateTracks();
        });

        // Popup on team marker click (works for both circle and pin layers)
        const teamMarkerLayerId = markerStyle === 'pin' ? 'team-markers-pin' : 'team-markers-circle';
        map.on('click', teamMarkerLayerId, (e) => {
          if (!e.features || e.features.length === 0) return;
          const feature = e.features[0];
          const coords = (feature.geometry as GeoJSON.Point).coordinates.slice() as [number, number];
          const label = feature.properties?.label || '';
          const locationTime = feature.properties?.locationTime || '';
          const age = locationTime ? formatRelativeTime(locationTime) : '';

          new maplibregl.Popup({ offset: markerStyle === 'pin' ? 32 : 12, closeButton: false })
            .setLngLat(coords)
            .setHTML(age ? `<strong>${label}</strong><br/>${age}` : `<strong>${label}</strong>`)
            .addTo(map);
        });

        // Popup on checkpoint marker click
        map.on('click', 'checkpoint-markers-circle', (e) => {
          if (!e.features || e.features.length === 0) return;
          const feature = e.features[0];
          const coords = (feature.geometry as GeoJSON.Point).coordinates.slice() as [number, number];
          const label = feature.properties?.label || '';

          new maplibregl.Popup({ offset: 14, closeButton: false })
            .setLngLat(coords)
            .setHTML(`<strong>📍 ${label}</strong>`)
            .addTo(map);
        });

        // Cursor change on hover
        map.on('mouseenter', teamMarkerLayerId, () => { map.getCanvas().style.cursor = 'pointer'; });
        map.on('mouseleave', teamMarkerLayerId, () => { map.getCanvas().style.cursor = ''; });
        map.on('mouseenter', 'checkpoint-markers-circle', () => { map.getCanvas().style.cursor = 'pointer'; });
        map.on('mouseleave', 'checkpoint-markers-circle', () => { map.getCanvas().style.cursor = ''; });

        mapRef.current = map;
      })
      .catch(err => {
        console.error('Failed to load map style:', err);
      });

    return () => {
      markersSourceRef.current = false;
      if (mapRef.current) {
        mapRef.current.remove();
        mapRef.current = null;
      }
    };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [center, zoom]);

  // Update markers when they change
  useEffect(() => {
    updateMarkers();
  }, [updateMarkers]);

  useEffect(() => {
    updateTracks();
  }, [updateTracks]);

  return (
    <div
      ref={mapContainerRef}
      className={className}
      style={{ width: '100%', height: '100%', minHeight: 400 }}
    />
  );
}

export default MapView;

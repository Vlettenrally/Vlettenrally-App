/**
 * MapView Component - Shared MapLibre GL map component
 *
 * Renders a MapLibre GL map with support for team location markers
 * and checkpoint markers. Uses self-hosted vector tiles via Martin.
 */
import { useEffect, useRef, useCallback } from 'react';
import maplibregl from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import { config } from '../config';

export interface MapMarker {
  id: string;
  lat: number;
  lng: number;
  label: string;
  color: string;
  type: 'team' | 'checkpoint';
}

interface MapViewProps {
  markers: MapMarker[];
  center?: [number, number]; // [lng, lat]
  zoom?: number;
  className?: string;
  /** When provided, replaces the browser GeolocateControl with a button that flies to this team location */
  teamLocation?: [number, number]; // [lng, lat]
}


export function MapView({ markers, center, zoom, className, teamLocation }: MapViewProps) {
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
  }, [markers]);

  useEffect(() => {
    if (!mapContainerRef.current) return;

    // Resolve map style: use remote style URL if configured, otherwise local style with self-hosted tiles
    const stylePromise = config.mapStyleUrl
      ? Promise.resolve(config.mapStyleUrl)
      : fetch('/map-style.json')
          .then(res => res.json())
          .then(style => {
            // Resolve to absolute URL — MapLibre web workers can't handle relative URLs
            const tileUrl = config.tileUrl.startsWith('http')
              ? config.tileUrl
              : `${window.location.origin}${config.tileUrl}`;
            style.sources.openmaptiles.tiles = [tileUrl];
            return style;
          });

    stylePromise.then(style => {
        const map = new maplibregl.Map({
          container: mapContainerRef.current!,
          style: style,
          center: center || [5.2913, 52.1326], // Default: center of Netherlands
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
          // Add team markers source and layer
          map.addSource('team-markers', {
            type: 'geojson',
            data: { type: 'FeatureCollection', features: [] },
          });

          map.addLayer({
            id: 'team-markers-circle',
            type: 'circle',
            source: 'team-markers',
            paint: {
              'circle-radius': 8,
              'circle-color': ['get', 'color'],
              'circle-stroke-width': 2,
              'circle-stroke-color': '#ffffff',
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
              'circle-color': '#ff9800',
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
        });

        // Popup on team marker click
        map.on('click', 'team-markers-circle', (e) => {
          if (!e.features || e.features.length === 0) return;
          const feature = e.features[0];
          const coords = (feature.geometry as GeoJSON.Point).coordinates.slice() as [number, number];
          const label = feature.properties?.label || '';

          new maplibregl.Popup({ offset: 12, closeButton: false })
            .setLngLat(coords)
            .setHTML(`<strong>${label}</strong>`)
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
        map.on('mouseenter', 'team-markers-circle', () => { map.getCanvas().style.cursor = 'pointer'; });
        map.on('mouseleave', 'team-markers-circle', () => { map.getCanvas().style.cursor = ''; });
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

  return (
    <div
      ref={mapContainerRef}
      className={className}
      style={{ width: '100%', height: '100%', minHeight: 400 }}
    />
  );
}

export default MapView;

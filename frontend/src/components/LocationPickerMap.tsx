/**
 * LocationPickerMap — interactive map for picking a location.
 *
 * Features:
 *  - Click the map to place / move a draggable marker (circle mode)
 *  - Click to add polygon vertices, double-click to close (polygon mode)
 *  - Search bar that accepts "lat, lng" coordinates or a place name (via Nominatim)
 *  - Shows selected coordinates as helper text
 *  - Renders zone shape preview (circle or polygon)
 *  - Reuses the shared map style resolution used by MapView
 */
import {useEffect, useRef, useState, useCallback } from 'react';
import {
  Box,
  TextField,
  InputAdornment,
  IconButton,
  Typography,
  CircularProgress,
} from '@mui/material';
import { Search as SearchIcon, Clear as ClearIcon } from '@mui/icons-material';
import maplibregl from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import { resolveMapStyle, DEFAULT_CENTER, DEFAULT_ZOOM } from '../utils/mapStyle';
import turfCircle from '@turf/circle';
import { useLatestRef } from '../hooks/useLatestRef';

interface LocationPickerMapProps {
  /** Current value — null means nothing selected yet */
  value: { lat: number; lng: number } | null;
  /** Called when the user picks / drags a location */
  onChange: (lat: number, lng: number) => void;
  /** Map container height (default 350, smaller on mobile) */
  height?: number;
  /** Zone type for preview rendering */
  zoneType?: 'circle' | 'polygon';
  /** Radius in meters for circle zones */
  radiusMeters?: number;
  /** Polygon points as [[lat, lng], ...] for polygon zones */
  polygonPoints?: number[][];
  /** Called when polygon points change (user adds/edits vertices) */
  onPolygonPointsChange?: (points: number[][]) => void;
}

/** Try to parse a string as "lat, lng" */
function parseCoordinates(input: string): { lat: number; lng: number } | null {
  const parts = input
    .replace(/[;|/]/g, ',')
    .split(/[\s,]+/)
    .map((s) => s.trim())
    .filter(Boolean);

  if (parts.length === 2) {
    const lat = parseFloat(parts[0]);
    const lng = parseFloat(parts[1]);
    if (!isNaN(lat) && !isNaN(lng) && lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180) {
      return { lat, lng };
    }
  }
  return null;
}

/** Geocode a place name via Nominatim (free, 1 req/s) */
async function geocode(query: string): Promise<{ lat: number; lng: number } | null> {
  try {
    const url = `https://nominatim.openstreetmap.org/search?format=json&limit=1&q=${encodeURIComponent(query)}`;
    const res = await fetch(url, {
      headers: { 'Accept-Language': 'nl,en', 'User-Agent': 'VlettenrallyApp/1.0' },
    });
    const data = await res.json();
    if (Array.isArray(data) && data.length > 0) {
      const { lat, lon } = data[0];
      return { lat: parseFloat(lat), lng: parseFloat(lon) };
    }
  } catch (err) {
    console.error('Geocoding failed:', err);
  }
  return null;
}

/** Compute the centroid of a polygon given its vertices [[lat,lng], ...] */
function computeCentroid(points: number[][]): { lat: number; lng: number } {
  const n = points.length;
  if (n === 0) return { lat: 0, lng: 0 };
  const sumLat = points.reduce((s, p) => s + p[0], 0);
  const sumLng = points.reduce((s, p) => s + p[1], 0);
  return { lat: sumLat / n, lng: sumLng / n };
}

export default function LocationPickerMap({
  value,
  onChange,
  height = 350,
  zoneType = 'circle',
  radiusMeters = 50,
  polygonPoints,
  onPolygonPointsChange,
}: LocationPickerMapProps): React.JSX.Element {
  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<maplibregl.Map | null>(null);
  const markerRef = useRef<maplibregl.Marker | null>(null);
  const zoneSourceReady = useRef(false);

  const [searchQuery, setSearchQuery] = useState('');
  const [searching, setSearching] = useState(false);
  const [searchError, setSearchError] = useState<string | null>(null);

  // Stable refs so the map event handlers always see the latest callbacks/values
  const onChangeRef = useLatestRef(onChange);
  const onPolygonPointsChangeRef = useLatestRef(onPolygonPointsChange);
  const zoneTypeRef = useLatestRef(zoneType);
  const radiusMetersRef = useLatestRef(radiusMeters);
  const polygonPointsRef = useLatestRef(polygonPoints);

  // Track the last-placed marker position so zone shape updates use it immediately
  const lastPlacedRef = useRef<{ lat: number; lng: number } | null>(value);

  // Polygon vertex markers (small dots for each polygon vertex)
  const vertexMarkersRef = useRef<maplibregl.Marker[]>([]);

  // Track whether polygon is currently being built (not yet closed)
  const polygonBuildingRef = useRef<number[][]>([]);
  const isPolygonClosedRef = useRef(false);

  /** Draw the zone shape overlay on the map using the freshest coordinates */
  const drawZoneShape = useCallback((
    overrideCenter?: { lat: number; lng: number } | null,
    overridePolygonPoints?: number[][] | null,
  ) => {
    const map = mapRef.current;
    if (!map || !zoneSourceReady.current) return;

    const source = map.getSource('zone-preview') as maplibregl.GeoJSONSource;
    if (!source) return;

    const zt = zoneTypeRef.current;
    const rm = radiusMetersRef.current;
    const pp = overridePolygonPoints !== undefined ? overridePolygonPoints : polygonPointsRef.current;
    const center = overrideCenter !== undefined ? overrideCenter : lastPlacedRef.current;

    let feature: GeoJSON.Feature | null = null;

    if (zt === 'polygon' && pp && pp.length >= 3) {
      const coords = pp.map(p => [p[1], p[0]]); // [lng, lat]
      coords.push(coords[0]); // close ring
      feature = {
        type: 'Feature',
        geometry: {
          type: 'Polygon',
          coordinates: [coords],
        },
        properties: {},
      };
    } else if (zt === 'polygon' && pp && pp.length >= 2) {
      // While building: show a line
      const coords = pp.map(p => [p[1], p[0]]);
      feature = {
        type: 'Feature',
        geometry: {
          type: 'LineString',
          coordinates: coords,
        },
        properties: {},
      };
    } else if (zt === 'circle' && center) {
      const circle = turfCircle([center.lng, center.lat], rm / 1000, { steps: 64, units: 'kilometers' });
      feature = {
        type: 'Feature',
        geometry: circle.geometry,
        properties: {},
      };
    }

    source.setData({
      type: 'FeatureCollection',
      features: feature ? [feature] : [],
    });
  }, [polygonPointsRef, radiusMetersRef, zoneTypeRef]);

  // Flag to suppress click events during/after marker drags
  const isDraggingRef = useRef(false);

  /** Update vertex markers on the map for polygon mode (draggable) */
  const updateVertexMarkers = useCallback((points: number[][]) => {
    const map = mapRef.current;
    if (!map) return;

    // Remove existing vertex markers
    vertexMarkersRef.current.forEach(m => m.remove());
    vertexMarkersRef.current = [];

    // Create new draggable vertex markers
    points.forEach((p, index) => {
      const el = document.createElement('div');
      el.style.width = '12px';
      el.style.height = '12px';
      el.style.borderRadius = '50%';
      el.style.backgroundColor = '#ff9800';
      el.style.border = '2px solid white';
      el.style.boxShadow = '0 0 4px rgba(0,0,0,0.3)';
      el.style.cursor = 'grab';

      const marker = new maplibregl.Marker({ element: el, draggable: true })
        .setLngLat([p[1], p[0]])
        .addTo(map);

      marker.on('dragstart', () => {
        isDraggingRef.current = true;
      });

      // Live preview while dragging a vertex
      marker.on('drag', () => {
        const lngLat = marker.getLngLat();
        const pts = polygonPointsRef.current ? [...polygonPointsRef.current] : [];
        if (index < pts.length) {
          pts[index] = [lngLat.lat, lngLat.lng];
          drawZoneShape(null, pts);
        }
      });

      // Finalize on drag end — update the polygon and sync to form
      marker.on('dragend', () => {
        // Keep flag true briefly to suppress the click event that follows dragend
        setTimeout(() => { isDraggingRef.current = false; }, 300);

        const lngLat = marker.getLngLat();
        const pts = polygonPointsRef.current ? [...polygonPointsRef.current] : [];
        if (index < pts.length) {
          pts[index] = [lngLat.lat, lngLat.lng];
          polygonPointsRef.current = pts;
          drawZoneShape(null, pts);

          // Update centroid marker position
          if (pts.length >= 3) {
            const centroid = computeCentroid(pts);
            if (markerRef.current) {
              markerRef.current.setLngLat([centroid.lng, centroid.lat]);
            }
            lastPlacedRef.current = centroid;
          }

          // Sync to form
          onPolygonPointsChangeRef.current?.(pts);
        }
      });

      vertexMarkersRef.current.push(marker);
    });
  }, [drawZoneShape, onPolygonPointsChangeRef, polygonPointsRef]);

  /** Place or move the center marker and notify parent */
  const placeMarker = useCallback((lat: number, lng: number, flyTo = false) => {
    const map = mapRef.current;
    if (!map) return;

    if (markerRef.current) {
      markerRef.current.setLngLat([lng, lat]);
    } else {
      const marker = new maplibregl.Marker({ color: '#ff9800', draggable: true })
        .setLngLat([lng, lat])
        .addTo(map);

      // Snapshot the center + polygon at drag start so we can compute deltas
      let dragOrigin: { lat: number; lng: number } | null = null;
      let dragOriginPolygon: number[][] | null = null;

      marker.on('dragstart', () => {
        isDraggingRef.current = true;
        dragOrigin = lastPlacedRef.current ? { ...lastPlacedRef.current } : null;
        dragOriginPolygon = polygonPointsRef.current ? [...polygonPointsRef.current] : null;
      });

      // Live preview: shift polygon while dragging
      marker.on('drag', () => {
        const lngLat = marker.getLngLat();
        const cur = { lat: lngLat.lat, lng: lngLat.lng };

        if (zoneTypeRef.current === 'polygon' && dragOriginPolygon && dragOrigin) {
          const dLat = cur.lat - dragOrigin.lat;
          const dLng = cur.lng - dragOrigin.lng;
          const shifted = dragOriginPolygon.map(p => [p[0] + dLat, p[1] + dLng]);
          drawZoneShape(cur, shifted);
          updateVertexMarkers(shifted);
        } else {
          drawZoneShape(cur);
        }
      });

      // Finalize on drag end
      marker.on('dragend', () => {
        setTimeout(() => { isDraggingRef.current = false; }, 300);

        const lngLat = marker.getLngLat();
        const newCenter = { lat: lngLat.lat, lng: lngLat.lng };

        if (zoneTypeRef.current === 'polygon' && dragOriginPolygon && dragOrigin) {
          const dLat = newCenter.lat - dragOrigin.lat;
          const dLng = newCenter.lng - dragOrigin.lng;
          const shifted = dragOriginPolygon.map(p => [p[0] + dLat, p[1] + dLng]);
          polygonPointsRef.current = shifted;
          onPolygonPointsChangeRef.current?.(shifted);
          lastPlacedRef.current = newCenter;
          onChangeRef.current(newCenter.lat, newCenter.lng);
          drawZoneShape(newCenter, shifted);
          updateVertexMarkers(shifted);
        } else {
          lastPlacedRef.current = newCenter;
          onChangeRef.current(newCenter.lat, newCenter.lng);
          drawZoneShape(newCenter);
        }

        dragOrigin = null;
        dragOriginPolygon = null;

        // Smoothly pan so the zone is fully visible
        if (zoneTypeRef.current === 'polygon' && polygonPointsRef.current && polygonPointsRef.current.length >= 2) {
          const lats = polygonPointsRef.current.map(p => p[0]);
          const lngs = polygonPointsRef.current.map(p => p[1]);
          map.fitBounds(
            [[Math.min(...lngs), Math.min(...lats)], [Math.max(...lngs), Math.max(...lats)]],
            { padding: 60, maxZoom: map.getZoom(), duration: 400 },
          );
        } else {
          map.easeTo({ center: [lngLat.lng, lngLat.lat], duration: 300 });
        }
      });

      markerRef.current = marker;
    }

    if (flyTo) {
      map.flyTo({ center: [lng, lat], zoom: Math.max(map.getZoom(), 14) });
    }

    lastPlacedRef.current = { lat, lng };
    onChangeRef.current(lat, lng);
    // Immediately redraw the zone shape with the new coordinates
    drawZoneShape({ lat, lng });
  }, [drawZoneShape, updateVertexMarkers, onChangeRef, onPolygonPointsChangeRef, polygonPointsRef, zoneTypeRef]);

  // Initialize map
  useEffect(() => {
    if (!mapContainerRef.current) return;

    resolveMapStyle().then((style) => {
      const initialCenter: [number, number] = value
        ? [value.lng, value.lat]
        : DEFAULT_CENTER;
      const initialZoom = value ? 14 : DEFAULT_ZOOM;

      const map = new maplibregl.Map({
        container: mapContainerRef.current!,
        style,
        center: initialCenter,
        zoom: initialZoom,
        maxZoom: 18,
        maxPitch: 0,
        dragRotate: false,
        pitchWithRotate: false,
        attributionControl: {},
      });

      map.touchZoomRotate.disableRotation();
      map.addControl(new maplibregl.NavigationControl({ showCompass: false }), 'top-right');

      map.on('load', () => {
        // Zone preview source and layers
        map.addSource('zone-preview', {
          type: 'geojson',
          data: { type: 'FeatureCollection', features: [] },
        });

        map.addLayer({
          id: 'zone-preview-fill',
          type: 'fill',
          source: 'zone-preview',
          paint: {
            'fill-color': '#ff9800',
            'fill-opacity': 0.15,
          },
        });

        map.addLayer({
          id: 'zone-preview-outline',
          type: 'line',
          source: 'zone-preview',
          paint: {
            'line-color': '#ff9800',
            'line-width': 2,
            'line-opacity': 0.6,
            'line-dasharray': [3, 2],
          },
        });

        zoneSourceReady.current = true;

        // Place initial marker if value is set
        if (value) {
          placeMarker(value.lat, value.lng);
        }
        // Show initial polygon vertex markers and fit bounds to polygon
        if (polygonPointsRef.current && polygonPointsRef.current.length > 0) {
          updateVertexMarkers(polygonPointsRef.current);
          isPolygonClosedRef.current = true;

          // Fit the map to the polygon extent so the whole shape is visible
          if (polygonPointsRef.current.length >= 2) {
            const lats = polygonPointsRef.current.map(p => p[0]);
            const lngs = polygonPointsRef.current.map(p => p[1]);
            map.fitBounds(
              [[Math.min(...lngs), Math.min(...lats)], [Math.max(...lngs), Math.max(...lats)]],
              { padding: 60, maxZoom: 17, duration: 0 },
            );
          }
        }
        drawZoneShape();
      });

      // ── Click handler ──
      map.on('click', (e) => {
        // Suppress clicks that follow marker drags
        if (isDraggingRef.current) return;

        if (zoneTypeRef.current === 'polygon') {
          // Polygon mode: add vertex on click
          if (isPolygonClosedRef.current) {
            // Polygon already closed — ignore clicks (user must clear first)
            return;
          }
          const newPoint: number[] = [e.lngLat.lat, e.lngLat.lng];
          polygonBuildingRef.current = [...polygonBuildingRef.current, newPoint];
          const pts = polygonBuildingRef.current;
          updateVertexMarkers(pts);
          drawZoneShape(null, pts);

          // Sync every click to the form so the point list stays up to date
          polygonPointsRef.current = pts;
          onPolygonPointsChangeRef.current?.(pts);
        } else {
          // Circle mode: place marker
          placeMarker(e.lngLat.lat, e.lngLat.lng);
        }
      });

      // ── Double-click handler (polygon mode: close polygon) ──
      map.on('dblclick', (e) => {
        if (zoneTypeRef.current !== 'polygon') return;
        if (isPolygonClosedRef.current) return;

        e.preventDefault();

        const pts = polygonBuildingRef.current;
        if (pts.length < 3) return;

        // Close the polygon — place center marker and stop accepting new clicks
        isPolygonClosedRef.current = true;
        polygonPointsRef.current = pts;
        onPolygonPointsChangeRef.current?.(pts);

        // Place marker at centroid
        const centroid = computeCentroid(pts);
        placeMarker(centroid.lat, centroid.lng);
        drawZoneShape(centroid, pts);
        updateVertexMarkers(pts);

        // Fit map to show the entire polygon
        const lats = pts.map(p => p[0]);
        const lngs = pts.map(p => p[1]);
        map.fitBounds(
          [[Math.min(...lngs), Math.min(...lats)], [Math.max(...lngs), Math.max(...lats)]],
          { padding: 60, maxZoom: 17, duration: 400 },
        );

        // Reset building state
        polygonBuildingRef.current = [];
      });

      // Crosshair cursor to indicate the map is clickable
      map.getCanvas().style.cursor = 'crosshair';

      mapRef.current = map;
    }).catch((err) => {
      console.error('Failed to load map style:', err);
    });

    return () => {
      zoneSourceReady.current = false;
      polygonBuildingRef.current = [];
      isPolygonClosedRef.current = false;
      vertexMarkersRef.current.forEach(m => m.remove());
      vertexMarkersRef.current = [];
      if (mapRef.current) {
        markerRef.current?.remove();
        markerRef.current = null;
        mapRef.current.remove();
        mapRef.current = null;
      }
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Sync external value changes (e.g. when dialog resets)
  useEffect(() => {
    if (!mapRef.current) return;
    if (value) {
      // Skip if the marker is already at this position (internal drag / click).
      const last = lastPlacedRef.current;
      if (
        last &&
        Math.abs(last.lat - value.lat) < 1e-9 &&
        Math.abs(last.lng - value.lng) < 1e-9
      ) {
        return;
      }
      lastPlacedRef.current = value;
      placeMarker(value.lat, value.lng, true);
    } else if (markerRef.current) {
      markerRef.current.remove();
      markerRef.current = null;
      lastPlacedRef.current = null;
      drawZoneShape(null, null);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value?.lat, value?.lng]);

  // Redraw zone shape when zoneType, radius, or polygonPoints change from parent
  useEffect(() => {
    if (polygonPoints && polygonPoints.length > 0) {
      // Check if this update came from the map's own click handler (building ref matches)
      const fromMapClick = polygonBuildingRef.current.length === polygonPoints.length
        && polygonBuildingRef.current.every((p, i) => p[0] === polygonPoints[i][0] && p[1] === polygonPoints[i][1]);

      if (!fromMapClick) {
        // Points changed externally (form input fields, add/remove buttons) — mark as closed
        isPolygonClosedRef.current = true;
        polygonBuildingRef.current = [];

        // Update center marker to centroid and fit map to show full polygon
        if (polygonPoints.length >= 3) {
          const centroid = computeCentroid(polygonPoints);
          if (mapRef.current) {
            placeMarker(centroid.lat, centroid.lng);
            // Fit bounds so the entire polygon is visible
            const lats = polygonPoints.map(p => p[0]);
            const lngs = polygonPoints.map(p => p[1]);
            mapRef.current.fitBounds(
              [[Math.min(...lngs), Math.min(...lats)], [Math.max(...lngs), Math.max(...lats)]],
              { padding: 60, maxZoom: 17, duration: 400 },
            );
          }
        }
      }

      updateVertexMarkers(polygonPoints);
    } else if (zoneType === 'polygon') {
      // Points were cleared
      isPolygonClosedRef.current = false;
      polygonBuildingRef.current = [];
      vertexMarkersRef.current.forEach(m => m.remove());
      vertexMarkersRef.current = [];
    }
    drawZoneShape();
  }, [zoneType, radiusMeters, polygonPoints, drawZoneShape, updateVertexMarkers, placeMarker]);

  const handleSearch = async () => {
    const q = searchQuery.trim();
    if (!q) return;

    setSearching(true);
    setSearchError(null);

    const coords = parseCoordinates(q);
    if (coords) {
      placeMarker(coords.lat, coords.lng, true);
      setSearching(false);
      return;
    }

    const result = await geocode(q);
    if (result) {
      placeMarker(result.lat, result.lng, true);
    } else {
      setSearchError('Locatie niet gevonden');
    }
    setSearching(false);
  };

  const handleClearLocation = () => {
    if (markerRef.current) {
      markerRef.current.remove();
      markerRef.current = null;
    }
    lastPlacedRef.current = null;
    polygonBuildingRef.current = [];
    isPolygonClosedRef.current = false;
    vertexMarkersRef.current.forEach(m => m.remove());
    vertexMarkersRef.current = [];
    onPolygonPointsChangeRef.current?.([]);
    drawZoneShape(null, null);
    mapRef.current?.flyTo({ center: DEFAULT_CENTER, zoom: DEFAULT_ZOOM });
  };

  return (
    <Box sx={{ mt: 1, mb: 1 }}>
      {/* Search bar */}
      <TextField
        placeholder="Zoek op coördinaten (52.13, 5.29) of plaatsnaam…"
        size="small"
        fullWidth
        value={searchQuery}
        onChange={(e) => {
          setSearchQuery(e.target.value);
          setSearchError(null);
        }}
        onKeyDown={(e) => {
          if (e.key === 'Enter') {
            e.preventDefault();
            handleSearch();
          }
        }}
        error={!!searchError}
        helperText={searchError}
        slotProps={{
          input: {
            startAdornment: (
              <InputAdornment position="start">
                <IconButton size="small" onClick={handleSearch} disabled={searching} edge="start">
                  {searching ? <CircularProgress size={18} /> : <SearchIcon />}
                </IconButton>
              </InputAdornment>
            ),
            endAdornment: searchQuery ? (
              <InputAdornment position="end">
                <IconButton size="small" onClick={() => { setSearchQuery(''); setSearchError(null); }} edge="end">
                  <ClearIcon fontSize="small" />
                </IconButton>
              </InputAdornment>
            ) : undefined,
          },
        }}
        sx={{ mb: 1 }}
      />

      {/* Map container */}
      <Box
        ref={mapContainerRef}
        sx={{
          width: '100%',
          height,
          borderRadius: 1,
          overflow: 'hidden',
          border: '1px solid',
          borderColor: 'divider',
        }}
      />

      {/* Selected coordinates display + clear button */}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mt: 0.5 }}>
        <Typography variant="caption" color="textSecondary" sx={{ fontFamily: 'monospace' }}>
          {value
            ? `📍 ${value.lat.toFixed(5)}, ${value.lng.toFixed(5)}`
            : zoneType === 'polygon'
              ? 'Klik op de kaart om punten toe te voegen (min. 3)'
              : 'Klik op de kaart om een locatie te selecteren'}
        </Typography>
        {value && (
          <Typography
            variant="caption"
            color="error"
            sx={{ cursor: 'pointer', textDecoration: 'underline', ml: 1 }}
            onClick={handleClearLocation}
          >
            Wissen
          </Typography>
        )}
      </Box>
    </Box>
  );
}



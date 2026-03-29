/**
 * LocationPickerMap — interactive map for picking a location.
 *
 * Features:
 *  - Click the map to place / move a draggable marker (circle mode)
 *  - Drag on the map to draw a rectangle (rectangle mode)
 *  - Search bar that accepts "lat, lng" coordinates or a place name (via Nominatim)
 *  - Shows selected coordinates as helper text
 *  - Renders zone shape preview (circle or rectangle)
 *  - Reuses the shared map style resolution used by MapView
 */
import { useEffect, useRef, useState, useCallback } from 'react';
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

interface Bounds {
  north: number;
  south: number;
  east: number;
  west: number;
}

interface LocationPickerMapProps {
  /** Current value — null means nothing selected yet */
  value: { lat: number; lng: number } | null;
  /** Called when the user picks / drags a location */
  onChange: (lat: number, lng: number) => void;
  /** Map container height (default 350, smaller on mobile) */
  height?: number;
  /** Zone type for preview rendering */
  zoneType?: 'circle' | 'rectangle';
  /** Radius in meters for circle zones */
  radiusMeters?: number;
  /** Bounds for rectangle zones */
  bounds?: Bounds;
  /** Called when rectangle bounds change (user draws/resizes) */
  onBoundsChange?: (bounds: Bounds) => void;
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

export default function LocationPickerMap({
  value,
  onChange,
  height = 350,
  zoneType = 'circle',
  radiusMeters = 50,
  bounds,
  onBoundsChange,
}: LocationPickerMapProps) {
  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<maplibregl.Map | null>(null);
  const markerRef = useRef<maplibregl.Marker | null>(null);
  const zoneSourceReady = useRef(false);

  const [searchQuery, setSearchQuery] = useState('');
  const [searching, setSearching] = useState(false);
  const [searchError, setSearchError] = useState<string | null>(null);

  // Stable refs so the map event handlers always see the latest callbacks/values
  const onChangeRef = useRef(onChange);
  onChangeRef.current = onChange;
  const onBoundsChangeRef = useRef(onBoundsChange);
  onBoundsChangeRef.current = onBoundsChange;
  const zoneTypeRef = useRef(zoneType);
  zoneTypeRef.current = zoneType;
  const radiusMetersRef = useRef(radiusMeters);
  radiusMetersRef.current = radiusMeters;
  const boundsRef = useRef(bounds);
  boundsRef.current = bounds;

  // Track the last-placed marker position so zone shape updates use it immediately
  // (avoids a React render round-trip where the circle would briefly use stale coords)
  const lastPlacedRef = useRef<{ lat: number; lng: number } | null>(value);

  // Rectangle drag state
  const dragStartRef = useRef<{ lat: number; lng: number } | null>(null);
  const isDraggingRectRef = useRef(false);

  /** Draw the zone shape overlay on the map using the freshest coordinates */
  const drawZoneShape = useCallback((
    overrideCenter?: { lat: number; lng: number } | null,
    overrideBounds?: Bounds | null,
  ) => {
    const map = mapRef.current;
    if (!map || !zoneSourceReady.current) return;

    const source = map.getSource('zone-preview') as maplibregl.GeoJSONSource;
    if (!source) return;

    const zt = zoneTypeRef.current;
    const rm = radiusMetersRef.current;
    const b = overrideBounds !== undefined ? overrideBounds : boundsRef.current;
    const center = overrideCenter !== undefined ? overrideCenter : lastPlacedRef.current;

    let feature: GeoJSON.Feature | null = null;

    if (zt === 'rectangle' && b) {
      feature = {
        type: 'Feature',
        geometry: {
          type: 'Polygon',
          coordinates: [[
            [b.west, b.north],
            [b.east, b.north],
            [b.east, b.south],
            [b.west, b.south],
            [b.west, b.north],
          ]],
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
  }, []);

  /** Place or move the marker and notify parent */
  const placeMarker = useCallback((lat: number, lng: number, flyTo = false) => {
    const map = mapRef.current;
    if (!map) return;

    if (markerRef.current) {
      markerRef.current.setLngLat([lng, lat]);
    } else {
      const marker = new maplibregl.Marker({ color: '#ff9800', draggable: true })
        .setLngLat([lng, lat])
        .addTo(map);

      // Snapshot the center + bounds at drag start so we can compute deltas
      let dragOrigin: { lat: number; lng: number } | null = null;
      let dragOriginBounds: Bounds | null = null;

      marker.on('dragstart', () => {
        dragOrigin = lastPlacedRef.current ? { ...lastPlacedRef.current } : null;
        dragOriginBounds = boundsRef.current ? { ...boundsRef.current } : null;
      });

      // Live preview: shift rectangle while dragging
      marker.on('drag', () => {
        const lngLat = marker.getLngLat();
        const cur = { lat: lngLat.lat, lng: lngLat.lng };

        if (zoneTypeRef.current === 'rectangle' && dragOriginBounds && dragOrigin) {
          const dLat = cur.lat - dragOrigin.lat;
          const dLng = cur.lng - dragOrigin.lng;
          const shifted: Bounds = {
            north: dragOriginBounds.north + dLat,
            south: dragOriginBounds.south + dLat,
            east: dragOriginBounds.east + dLng,
            west: dragOriginBounds.west + dLng,
          };
          drawZoneShape(cur, shifted);
        } else {
          drawZoneShape(cur);
        }
      });

      // Finalize on drag end
      marker.on('dragend', () => {
        const lngLat = marker.getLngLat();
        const newCenter = { lat: lngLat.lat, lng: lngLat.lng };

        if (zoneTypeRef.current === 'rectangle' && dragOriginBounds && dragOrigin) {
          const dLat = newCenter.lat - dragOrigin.lat;
          const dLng = newCenter.lng - dragOrigin.lng;
          const shifted: Bounds = {
            north: dragOriginBounds.north + dLat,
            south: dragOriginBounds.south + dLat,
            east: dragOriginBounds.east + dLng,
            west: dragOriginBounds.west + dLng,
          };
          boundsRef.current = shifted;
          onBoundsChangeRef.current?.(shifted);
          lastPlacedRef.current = newCenter;
          onChangeRef.current(newCenter.lat, newCenter.lng);
          drawZoneShape(newCenter, shifted);
        } else {
          lastPlacedRef.current = newCenter;
          onChangeRef.current(newCenter.lat, newCenter.lng);
          drawZoneShape(newCenter);
        }

        dragOrigin = null;
        dragOriginBounds = null;

        // Smoothly pan so the zone is centered / fully visible
        if (zoneTypeRef.current === 'rectangle' && boundsRef.current) {
          const b = boundsRef.current;
          map.fitBounds(
            [[b.west, b.south], [b.east, b.north]],
            { padding: 60, duration: 400, maxZoom: map.getZoom() },
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
  }, [drawZoneShape]);

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
        drawZoneShape();
      });

      // ── Click handler (circle mode: place marker) ──
      map.on('click', (e) => {
        // In rectangle mode, clicks are handled by the drag sequence
        if (zoneTypeRef.current === 'rectangle') return;
        placeMarker(e.lngLat.lat, e.lngLat.lng);
      });

      // ── Rectangle drag drawing ──
      // We listen on the canvas so we can intercept before MapLibre's pan handler.
      const canvas = map.getCanvas();

      const onMouseDown = (e: MouseEvent) => {
        if (zoneTypeRef.current !== 'rectangle') return;
        // Only left-click
        if (e.button !== 0) return;

        // Don't hijack the marker's own drag — check if the click target is
        // inside the marker element (or the marker itself).
        if (markerRef.current) {
          const markerEl = markerRef.current.getElement();
          if (markerEl && (markerEl === e.target || markerEl.contains(e.target as Node))) {
            return; // let MapLibre handle the marker drag
          }
        }

        e.preventDefault();
        const lngLat = map.unproject([e.offsetX, e.offsetY]);
        dragStartRef.current = { lat: lngLat.lat, lng: lngLat.lng };
        isDraggingRectRef.current = false;

        // Disable map panning while drawing
        map.dragPan.disable();
      };

      const onMouseMove = (e: MouseEvent) => {
        if (!dragStartRef.current) return;
        if (zoneTypeRef.current !== 'rectangle') return;

        isDraggingRectRef.current = true;
        const lngLat = map.unproject([e.offsetX, e.offsetY]);
        const c1 = dragStartRef.current;

        // Live preview of the rectangle during drag
        const liveBounds: Bounds = {
          north: Math.max(c1.lat, lngLat.lat),
          south: Math.min(c1.lat, lngLat.lat),
          east: Math.max(c1.lng, lngLat.lng),
          west: Math.min(c1.lng, lngLat.lng),
        };
        drawZoneShape(null, liveBounds);
      };

      const onMouseUp = (e: MouseEvent) => {
        map.dragPan.enable();

        if (!dragStartRef.current || zoneTypeRef.current !== 'rectangle') {
          dragStartRef.current = null;
          return;
        }

        const lngLat = map.unproject([e.offsetX, e.offsetY]);
        const c1 = dragStartRef.current;
        dragStartRef.current = null;

        if (!isDraggingRectRef.current) {
          // It was just a click, not a drag — place center marker
          isDraggingRectRef.current = false;
          placeMarker(lngLat.lat, lngLat.lng);
          return;
        }
        isDraggingRectRef.current = false;

        const newBounds: Bounds = {
          north: Math.max(c1.lat, lngLat.lat),
          south: Math.min(c1.lat, lngLat.lat),
          east: Math.max(c1.lng, lngLat.lng),
          west: Math.min(c1.lng, lngLat.lng),
        };

        // Place marker at the center of the rectangle
        const centerLat = (newBounds.north + newBounds.south) / 2;
        const centerLng = (newBounds.east + newBounds.west) / 2;
        placeMarker(centerLat, centerLng);
        onBoundsChangeRef.current?.(newBounds);
        drawZoneShape(null, newBounds);
      };

      canvas.addEventListener('mousedown', onMouseDown);
      canvas.addEventListener('mousemove', onMouseMove);
      canvas.addEventListener('mouseup', onMouseUp);

      // Crosshair cursor to indicate the map is clickable
      map.getCanvas().style.cursor = 'crosshair';

      mapRef.current = map;

      // Store cleanup references
      (map as unknown as Record<string, unknown>)._rectCleanup = () => {
        canvas.removeEventListener('mousedown', onMouseDown);
        canvas.removeEventListener('mousemove', onMouseMove);
        canvas.removeEventListener('mouseup', onMouseUp);
      };
    }).catch((err) => {
      console.error('Failed to load map style:', err);
    });

    return () => {
      zoneSourceReady.current = false;
      dragStartRef.current = null;
      if (mapRef.current) {
        const cleanup = (mapRef.current as unknown as Record<string, unknown>)._rectCleanup as (() => void) | undefined;
        cleanup?.();
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
      // This prevents an unwanted flyTo that would move the map mid-interaction
      // and desync the marker from the zone shape.
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

  // Redraw zone shape when zoneType, radius, or bounds change from parent
  useEffect(() => {
    drawZoneShape();
  }, [zoneType, radiusMeters, bounds, drawZoneShape]);

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
    dragStartRef.current = null;
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
        <Typography variant="caption" color="text.secondary" sx={{ fontFamily: 'monospace' }}>
          {value
            ? `📍 ${value.lat.toFixed(5)}, ${value.lng.toFixed(5)}`
            : zoneType === 'rectangle'
              ? 'Sleep op de kaart om een rechthoek te tekenen'
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


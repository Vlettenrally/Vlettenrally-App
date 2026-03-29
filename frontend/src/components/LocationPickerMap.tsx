/**
 * LocationPickerMap — interactive map for picking a location.
 *
 * Features:
 *  - Click the map to place / move a draggable marker
 *  - Search bar that accepts "lat, lng" coordinates or a place name (via Nominatim)
 *  - Shows selected coordinates as helper text
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

interface LocationPickerMapProps {
  /** Current value — null means nothing selected yet */
  value: { lat: number; lng: number } | null;
  /** Called when the user picks / drags a location */
  onChange: (lat: number, lng: number) => void;
  /** Map container height (default 350, smaller on mobile) */
  height?: number;
}

/** Try to parse a string as "lat, lng" */
function parseCoordinates(input: string): { lat: number; lng: number } | null {
  // Accept "52.1326, 5.2913" or "52.1326 5.2913"
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

export default function LocationPickerMap({ value, onChange, height = 350 }: LocationPickerMapProps) {
  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<maplibregl.Map | null>(null);
  const markerRef = useRef<maplibregl.Marker | null>(null);

  const [searchQuery, setSearchQuery] = useState('');
  const [searching, setSearching] = useState(false);
  const [searchError, setSearchError] = useState<string | null>(null);

  // Stable reference to onChange so we don't re-init the map when it changes
  const onChangeRef = useRef(onChange);
  onChangeRef.current = onChange;

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

      marker.on('dragend', () => {
        const lngLat = marker.getLngLat();
        onChangeRef.current(lngLat.lat, lngLat.lng);
      });

      markerRef.current = marker;
    }

    if (flyTo) {
      map.flyTo({ center: [lng, lat], zoom: Math.max(map.getZoom(), 14) });
    }

    onChangeRef.current(lat, lng);
  }, []);

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
        // Place initial marker if value is set
        if (value) {
          placeMarker(value.lat, value.lng);
        }
      });

      // Click to place marker
      map.on('click', (e) => {
        placeMarker(e.lngLat.lat, e.lngLat.lng);
      });

      // Crosshair cursor to indicate the map is clickable
      map.getCanvas().style.cursor = 'crosshair';

      mapRef.current = map;
    }).catch((err) => {
      console.error('Failed to load map style:', err);
    });

    return () => {
      if (markerRef.current) {
        markerRef.current.remove();
        markerRef.current = null;
      }
      if (mapRef.current) {
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
      placeMarker(value.lat, value.lng, true);
    } else if (markerRef.current) {
      markerRef.current.remove();
      markerRef.current = null;
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value?.lat, value?.lng]);

  const handleSearch = async () => {
    const q = searchQuery.trim();
    if (!q) return;

    setSearching(true);
    setSearchError(null);

    // Try parsing as coordinates first
    const coords = parseCoordinates(q);
    if (coords) {
      placeMarker(coords.lat, coords.lng, true);
      setSearching(false);
      return;
    }

    // Fall back to Nominatim geocoding
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
    // Reset to default view
    mapRef.current?.flyTo({ center: DEFAULT_CENTER, zoom: DEFAULT_ZOOM });
    // Notify parent with undefined-ish — caller handles this
    // We don't call onChange here because removing coordinates means no value;
    // we let the parent handle this by noticing value is null
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


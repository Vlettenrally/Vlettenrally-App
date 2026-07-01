export interface LatLng {
  lat: number;
  lng: number;
}

const EARTH_RADIUS_M = 6_371_000;
const KM_PER_DEG_LAT = 111.32;

function toRad(deg: number): number {
  return (deg * Math.PI) / 180;
}

/** Great-circle distance in metres between two points. */
export function haversineMeters(a: LatLng, b: LatLng): number {
  const dLat = toRad(b.lat - a.lat);
  const dLng = toRad(b.lng - a.lng);
  const lat1 = toRad(a.lat);
  const lat2 = toRad(b.lat);
  const h =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) ** 2;
  return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1, Math.sqrt(h)));
}

/** Straight-line (linear lat/lng) interpolation; negligible error over a few km. */
export function interpolate(a: LatLng, b: LatLng, frac: number): LatLng {
  const f = Math.max(0, Math.min(1, frac));
  return { lat: a.lat + (b.lat - a.lat) * f, lng: a.lng + (b.lng - a.lng) * f };
}

/**
 * Offset a point by (north, east) metres. Used for small GPS jitter, so the flat-earth
 * approximation is more than accurate enough.
 */
export function offsetMeters(p: LatLng, northM: number, eastM: number): LatLng {
  const dLat = northM / 1000 / KM_PER_DEG_LAT;
  const dLng = eastM / 1000 / (KM_PER_DEG_LAT * Math.cos(toRad(p.lat)));
  return { lat: p.lat + dLat, lng: p.lng + dLng };
}

/**
 * A closed loop through the given waypoints (last segment returns to the first) with the
 * cumulative segment lengths precomputed, so a position at any distance travelled is an O(log n)
 * / O(n) walk rather than re-summing every call.
 */
export class Route {
  readonly points: LatLng[];
  /** cumulative[i] = distance from points[0] to points[i] along the loop; last = full loop length. */
  private readonly cumulative: number[];
  readonly totalMeters: number;

  constructor(points: LatLng[]) {
    if (points.length < 2) {
      throw new Error("Route needs at least 2 points");
    }
    this.points = points;
    this.cumulative = [0];
    for (let i = 1; i <= points.length; i++) {
      const from = points[i - 1];
      const to = points[i % points.length]; // wrap to close the loop
      this.cumulative.push(this.cumulative[i - 1] + haversineMeters(from, to));
    }
    this.totalMeters = this.cumulative[this.cumulative.length - 1];
  }

  /** Position after travelling {@code distanceMeters} along the loop (wraps around). */
  positionAt(distanceMeters: number): LatLng {
    const d = ((distanceMeters % this.totalMeters) + this.totalMeters) % this.totalMeters;
    for (let i = 1; i < this.cumulative.length; i++) {
      if (d <= this.cumulative[i]) {
        const segStart = this.cumulative[i - 1];
        const segLen = this.cumulative[i] - segStart || 1;
        const frac = (d - segStart) / segLen;
        return interpolate(this.points[i - 1], this.points[i % this.points.length], frac);
      }
    }
    return this.points[0];
  }
}

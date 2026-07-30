/**
 * Pure payload → marker mapping for the public narrowcasting map.
 *
 * Kept out of the page component (like `pages/admin/trackUtils`) so it can be unit-tested without
 * mounting MapLibre.
 */
import type { MapMarker } from '../../components/MapView';
import type { PublicMapResponse } from '../../types/entities';
import { STALE_AFTER_MS, TEAM_COLOR } from '../admin/mapConstants';

/** Default zone orange, matching the participant and admin maps. */
export const ZONE_COLOR = '#ff9800';

/**
 * Build the map markers for one poll of the public map.
 *
 * Note what is absent: no marker ever carries `hidden`. Hidden zones are filtered server-side and
 * the field is not on the wire, so a public display has nothing to render even in the muted style
 * the admin map uses.
 */
export function toMarkers(data: PublicMapResponse, now: number): MapMarker[] {
  return [
    ...data.teams.map(team => {
      const ts = Date.parse(team.locationTime);
      return {
        id: team.teamId,
        lat: team.latitude,
        lng: team.longitude,
        label: team.teamName,
        color: TEAM_COLOR,
        type: 'team' as const,
        locationTime: team.locationTime,
        stale: !Number.isNaN(ts) && now - ts > STALE_AFTER_MS,
      };
    }),
    ...data.zones
      .filter(zone => zone.latitude != null && zone.longitude != null)
      .map(zone => ({
        id: zone.id,
        lat: zone.latitude!,
        lng: zone.longitude!,
        label: zone.name,
        color: zone.color ?? ZONE_COLOR,
        type: 'checkpoint' as const,
        zoneType: zone.zoneType,
        radiusMeters: zone.radiusMeters,
        polygonPoints: zone.polygonPoints,
        openingTime: zone.openingTime,
        closingTime: zone.closingTime,
      })),
  ];
}

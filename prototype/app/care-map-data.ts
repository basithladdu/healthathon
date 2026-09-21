import south from './care-map-points-south.json';
import east from './care-map-points-east.json';
import { CARE_CENTRES } from './care-directory-data';
import type { CareMapPoint } from './palliative-map';

export const DIRECTORY_MAP_POINTS: Record<string, CareMapPoint> = Object.fromEntries(
  [...south, ...east].flatMap((location) => {
    const centre = CARE_CENTRES.find((item) => item.id === location.id);
    if (!centre || location.coordinates.length !== 2 || !location.coordinates.every(Number.isFinite)) return [];
    const point: CareMapPoint = { id: centre.id, name: centre.name, coordinates: [location.coordinates[0], location.coordinates[1]], category: 'palliative' };
    return [[centre.id, point]];
  }),
);

'use client';

import { MapContainer, TileLayer, Marker, Popup } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
// SEC-15: marker icons are bundled via webpack imports — no runtime
// fetches to unpkg/CDNs for executable-adjacent resources.
import markerIcon2x from 'leaflet/dist/images/marker-icon-2x.png';
import markerIcon from 'leaflet/dist/images/marker-icon.png';
import markerShadow from 'leaflet/dist/images/marker-shadow.png';

interface MapPoint {
  id: string;
  name: string;
  lat: number;
  lng: number;
  status?: string;
}

interface MapViewProps {
  points: MapPoint[];
  center?: [number, number];
  zoom?: number;
}

// Fix default marker icon — bundled assets, same-origin URLs.
delete (L.Icon.Default.prototype as unknown as { _getIconUrl?: unknown })._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: markerIcon2x.src,
  iconUrl: markerIcon.src,
  shadowUrl: markerShadow.src,
});

function isValidCoordinate(lat: number, lng: number): boolean {
  return (
    Number.isFinite(lat) && Math.abs(lat) <= 90 &&
    Number.isFinite(lng) && Math.abs(lng) <= 180
  );
}

export function MapView({
  points,
  center = [12.97, 77.59],
  zoom = 12,
}: MapViewProps) {
  return (
    <div className="h-[500px] w-full overflow-hidden rounded-lg border border-gray-200">
      <MapContainer center={center} zoom={zoom} className="h-full w-full">
        <TileLayer
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
        />
        {points
          .filter((point) => isValidCoordinate(point.lat, point.lng))
          .map((point) => (
            <Marker key={point.id} position={[point.lat, point.lng]}>
              <Popup>
                <strong>{point.name}</strong>
                <br />
                Status: {point.status || 'Unknown'}
              </Popup>
            </Marker>
          ))}
      </MapContainer>
    </div>
  );
}

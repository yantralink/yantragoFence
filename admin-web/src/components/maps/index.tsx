'use client';

import { MapContainer, TileLayer, Marker, Popup } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';

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

// Fix default marker icon
delete (L.Icon.Default.prototype as unknown as { _getIconUrl?: unknown })._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
  iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
  shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
});

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
        {points.map((point) => (
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

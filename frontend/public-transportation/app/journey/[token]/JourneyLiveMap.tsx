'use client';

import 'leaflet/dist/leaflet.css';
import L from 'leaflet';
import { useEffect, useMemo, useRef } from 'react';
import { MapContainer, TileLayer, Polyline, useMap } from 'react-leaflet';
import { decodePolyline } from '../../../src/utils/polyline-decoder';
import { getModeStyle } from '../../../src/utils/mode-colors';
import type { TransitMode } from '../../../src/types';
import type { ShareState } from './JourneyLiveViewer';

const riderIcon = L.divIcon({
  className: '',
  html: '<div style="width:18px;height:18px;border-radius:50%;background:#38bdf8;border:3px solid #fff;box-shadow:0 0 12px #38bdf8"></div>',
  iconSize: [18, 18],
  iconAnchor: [9, 9],
});

/** Keeps the rider in view without fighting a viewer who panned away. */
function FollowRider({ position }: { position: { lat: number; lon: number } | null }) {
  const map = useMap();
  const userMoved = useRef(false);
  useEffect(() => {
    const onDrag = () => {
      userMoved.current = true;
    };
    map.on('dragstart', onDrag);
    return () => {
      map.off('dragstart', onDrag);
    };
  }, [map]);
  useEffect(() => {
    if (position && !userMoved.current) {
      map.panTo([position.lat, position.lon], { animate: true });
    }
  }, [map, position]);
  return null;
}

function RiderMarker({ position }: { position: { lat: number; lon: number } }) {
  const map = useMap();
  const marker = useRef<L.Marker | null>(null);
  useEffect(() => {
    if (!marker.current) {
      marker.current = L.marker([position.lat, position.lon], { icon: riderIcon }).addTo(map);
    } else {
      marker.current.setLatLng([position.lat, position.lon]);
    }
  }, [map, position]);
  useEffect(
    () => () => {
      marker.current?.remove();
      marker.current = null;
    },
    []
  );
  return null;
}

export default function JourneyLiveMap({ share }: { share: ShareState }) {
  const legs = useMemo(
    () =>
      share.legs.map((leg, index) => ({
        index,
        points: decodePolyline(leg.polyline) as [number, number][],
        style: getModeStyle(leg.mode as TransitMode, leg.routeColor ?? undefined),
      })),
    [share.legs]
  );

  const bounds = useMemo(() => {
    const all: [number, number][] = legs.flatMap((l) => l.points);
    if (share.position) all.push([share.position.lat, share.position.lon]);
    return all.length > 0 ? L.latLngBounds(all) : null;
  }, [legs, share.position]);

  const center: [number, number] = share.position
    ? [share.position.lat, share.position.lon]
    : legs[0]?.points[0] ?? [31.77, 35.21];

  return (
    <MapContainer
      center={center}
      zoom={13}
      style={{ width: '100%', height: '100%' }}
      bounds={bounds ?? undefined}
      attributionControl={false}
    >
      <TileLayer url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" />
      {legs.map((leg) => (
        <Polyline
          key={leg.index}
          positions={leg.points}
          pathOptions={{
            color: leg.style.color,
            weight: leg.index === share.progressLegIndex ? 6 : 4,
            opacity: leg.index < share.progressLegIndex ? 0.4 : 0.9,
            dashArray: leg.style.dashArray,
          }}
        />
      ))}
      {share.position && <RiderMarker position={share.position} />}
      <FollowRider position={share.position} />
    </MapContainer>
  );
}

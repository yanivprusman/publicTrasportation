import L from 'leaflet'

export const originIcon = L.divIcon({
  className: 'origin-marker',
  iconSize: [44, 44],
  iconAnchor: [22, 22],
  popupAnchor: [0, -24],
  html: `
    <style>
      @keyframes origin-ripple {
        0% { transform: scale(0.5); opacity: 0.7; }
        100% { transform: scale(2.2); opacity: 0; }
      }
      .origin-pulse-container {
        position: relative;
        width: 44px;
        height: 44px;
      }
      .origin-ripple-ring {
        position: absolute;
        top: 50%;
        left: 50%;
        width: 20px;
        height: 20px;
        margin-left: -10px;
        margin-top: -10px;
        border-radius: 50%;
        border: 2.5px solid #00bcd4;
        animation: origin-ripple 2.4s ease-out infinite;
        pointer-events: none;
      }
      .origin-ripple-ring:nth-child(2) {
        animation-delay: 0.8s;
      }
      .origin-ripple-ring:nth-child(3) {
        animation-delay: 1.6s;
      }
      .origin-core {
        position: absolute;
        top: 50%;
        left: 50%;
        width: 14px;
        height: 14px;
        margin-left: -7px;
        margin-top: -7px;
        border-radius: 50%;
        background: radial-gradient(circle at 40% 38%, #4dd0e1, #00838f);
        box-shadow: 0 0 6px 2px rgba(0, 188, 212, 0.45), inset 0 -2px 3px rgba(0, 0, 0, 0.15);
        border: 2px solid #fff;
      }
    </style>
    <div class="origin-pulse-container">
      <div class="origin-ripple-ring"></div>
      <div class="origin-ripple-ring"></div>
      <div class="origin-ripple-ring"></div>
      <div class="origin-core"></div>
    </div>`,
})

export const destinationIcon = L.divIcon({
  className: 'destination-marker',
  iconSize: [30, 40],
  iconAnchor: [15, 40],
  popupAnchor: [0, -42],
  html: `
    <style>
      .dest-gem-container {
        position: relative;
        width: 30px;
        height: 40px;
        filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.35));
      }
      .dest-diamond {
        position: absolute;
        top: 0;
        left: 50%;
        width: 22px;
        height: 22px;
        margin-left: -11px;
        transform: rotate(45deg);
        background: linear-gradient(135deg, #ffb74d 0%, #e65100 50%, #ff8a65 100%);
        border-radius: 3px;
        box-shadow: 0 0 10px 2px rgba(255, 152, 0, 0.3);
      }
      .dest-diamond-inner {
        position: absolute;
        top: 50%;
        left: 50%;
        width: 8px;
        height: 8px;
        margin-left: -4px;
        margin-top: -4px;
        background: rgba(255, 255, 255, 0.85);
        border-radius: 1px;
      }
      .dest-stem {
        position: absolute;
        bottom: 0;
        left: 50%;
        width: 3px;
        height: 18px;
        margin-left: -1.5px;
        background: linear-gradient(to bottom, #e65100, #bf360c);
        border-radius: 0 0 1.5px 1.5px;
      }
    </style>
    <div class="dest-gem-container">
      <div class="dest-diamond">
        <div class="dest-diamond-inner"></div>
      </div>
      <div class="dest-stem"></div>
    </div>`,
})

export const centerIcon = new L.Icon({
  iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-green.png',
  iconRetinaUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-green.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-shadow.png',
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
  shadowSize: [41, 41],
  className: 'center-marker'
})

const busIconCache = new Map<string, L.DivIcon>()

export const createBusIcon = (lineNumber: string): L.DivIcon => {
  const cached = busIconCache.get(lineNumber)
  if (cached) return cached

  const icon = L.divIcon({
    className: 'bus-marker-wrapper',
    iconSize: [40, 40],
    iconAnchor: [20, 20],
    popupAnchor: [0, -22],
    html: `
      <div class="bus-marker">
        <svg viewBox="0 0 40 40" width="40" height="40" xmlns="http://www.w3.org/2000/svg">
          <rect x="4" y="4" width="32" height="32" rx="6" fill="#1565C0" stroke="#0D47A1" stroke-width="1.5"/>
          <rect x="8" y="6" width="24" height="8" rx="2" fill="#90CAF9" opacity="0.6"/>
          <circle cx="12" cy="34" r="2.5" fill="#333"/>
          <circle cx="28" cy="34" r="2.5" fill="#333"/>
        </svg>
        <span class="bus-marker-number">${lineNumber}</span>
      </div>
    `,
  })

  busIconCache.set(lineNumber, icon)
  return icon
}

export const stopIcon = new L.Icon({
  iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-red.png',
  iconRetinaUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-red.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-shadow.png',
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
  shadowSize: [41, 41],
  className: 'stop-marker'
})

export const configureDefaultLeafletIcons = () => {
  delete (L.Icon.Default.prototype as unknown as Record<string, unknown>)._getIconUrl
  L.Icon.Default.mergeOptions({
    iconRetinaUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon-2x.png',
    iconUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon.png',
    shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-shadow.png',
  })
}

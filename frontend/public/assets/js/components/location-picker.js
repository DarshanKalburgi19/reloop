/*
 * Location picker: a Leaflet map bound to latitude/longitude inputs. Click the map (or drag the pin) to set the
 * coordinates; typing coordinates moves the pin; "Use my location" asks the browser once (never stored elsewhere).
 *
 *   RL.locationPicker.attach({map: el, lat: input, lng: input, button?: el})
 */
(function (window) {
  'use strict';

  const RL = window.RL = window.RL || {};
  const GUJARAT = [22.6, 72.2];

  async function attach(o) {
    const latIn = o.lat;
    const lngIn = o.lng;
    const has = () => latIn.value !== '' && lngIn.value !== '' && !Number.isNaN(Number(latIn.value)) && !Number.isNaN(Number(lngIn.value));
    const map = await RL.map.create(o.map, { center: has() ? [Number(latIn.value), Number(lngIn.value)] : GUJARAT, zoom: has() ? 14 : 7 });
    let marker = null;

    function place(lat, lng, pan) {
      if (!map) return;
      if (!marker) {
        marker = window.L.marker([lat, lng], { draggable: true, icon: RL.map.dot(window.L, '#1f5c45'), title: 'Workshop location' }).addTo(map);
        marker.on('dragend', () => {
          const p = marker.getLatLng();
          set(p.lat, p.lng, false);
        });
      } else {
        marker.setLatLng([lat, lng]);
      }
      if (pan) map.setView([lat, lng], Math.max(map.getZoom(), 14));
    }
    function set(lat, lng, pan) {
      latIn.value = Number(lat).toFixed(6);
      lngIn.value = Number(lng).toFixed(6);
      latIn.dispatchEvent(new Event('change', { bubbles: true }));
      place(lat, lng, pan);
    }

    if (map) {
      map.on('click', (ev) => set(ev.latlng.lat, ev.latlng.lng, false));
      if (has()) place(Number(latIn.value), Number(lngIn.value), false);
    }
    [latIn, lngIn].forEach((el) => el.addEventListener('input', () => { if (has()) place(Number(latIn.value), Number(lngIn.value), true); }));

    if (o.button) {
      o.button.addEventListener('click', () => {
        if (!navigator.geolocation) {
          RL.ui.toast('Your browser cannot share its location. Click the map instead.', 'warning');
          return;
        }
        const btn = o.button;
        btn.disabled = true;
        navigator.geolocation.getCurrentPosition((pos) => {
          btn.disabled = false;
          set(pos.coords.latitude, pos.coords.longitude, true);
          RL.ui.toast('Location set from your browser. Check the pin is on your workshop.', 'success');
        }, (err) => {
          btn.disabled = false;
          RL.ui.toast('Could not get your location (' + (err.message || 'permission denied') + '). Click the map instead.', 'warning');
        }, { enableHighAccuracy: true, timeout: 10000 });
      });
    }
    return { set, map };
  }

  RL.locationPicker = { attach };
})(window);

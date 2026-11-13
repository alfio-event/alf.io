import {fetchJson} from "./helpers.ts";

export interface GeolocationResult {
    latitude: string | null;
    longitude: string | null;
    // static map image URL, when a map provider is configured
    mapUrl: string | null;
    // IANA time zone of the coordinates, when it could be resolved
    timeZone: string | null;
}

interface MapProviderAndKeys {
    provider: 'GOOGLE' | 'HERE' | 'NONE';
    keys: Record<string, string>;
}

let googleMapsLoading: Promise<void> | null = null;

/**
 * Loads the Google Maps JS API once, resolving when the geocoder is ready
 */
function loadGoogleMaps(apiKey: string | null): Promise<void> {
    if (googleMapsLoading != null) {
        return googleMapsLoading;
    }
    googleMapsLoading = new Promise((resolve, reject) => {
        const callbackName = `alfioGoogleMapsLoaded${Date.now()}`;
        (window as unknown as Record<string, unknown>)[callbackName] = () => {
            delete (window as unknown as Record<string, unknown>)[callbackName];
            resolve();
        };
        const script = document.createElement('script');
        let keyParam = '';
        if (apiKey != null && apiKey !== '') {
            keyParam = `&key=${encodeURIComponent(apiKey)}`;
        }
        script.src = `https://maps.googleapis.com/maps/api/js?libraries=places&callback=${callbackName}${keyParam}`;
        script.onerror = () => {
            googleMapsLoading = null;
            reject(new Error('The map provider could not be loaded'));
        };
        document.head.appendChild(script);
    });
    return googleMapsLoading;
}

interface GoogleGeocoderResult {
    geometry: { location: { lat: () => number; lng: () => number } };
}

async function geocodeWithGoogle(location: string, apiKey: string | null): Promise<{ latitude: string; longitude: string }> {
    await loadGoogleMaps(apiKey);
    const g = (window as unknown as { google: { maps: { Geocoder: new () => { geocode: (req: { address: string }, cb: (results: GoogleGeocoderResult[], status: string) => void) => void } } } }).google;
    const geocoder = new g.maps.Geocoder();
    const results = await new Promise<GoogleGeocoderResult[]>((resolve, reject) => {
        geocoder.geocode({ address: location }, (res, status) => {
            if (status === 'OK' && res.length > 0) {
                resolve(res);
            } else {
                reject(new Error(`Geocoding failed: ${status}`));
            }
        });
    });
    return {
        latitude: `${results[0].geometry.location.lat()}`,
        longitude: `${results[0].geometry.location.lng()}`,
    };
}

async function geocodeWithHere(location: string, apiKey: string): Promise<{ latitude: string; longitude: string }> {
    const response = await fetch(`https://geocoder.ls.hereapi.com/6.2/geocode.json?apiKey=${encodeURIComponent(apiKey)}&searchtext=${encodeURIComponent(location)}`);
    if (!response.ok) {
        throw new Error(`Geocoding failed: ${response.status}`);
    }
    const data = await response.json();
    const result = data?.Response?.View?.[0]?.Result?.[0]?.Location?.DisplayPosition;
    if (result == null) {
        throw new Error('Geocoding returned no result');
    }
    return { latitude: `${result.Latitude}`, longitude: `${result.Longitude}` };
}

export class LocationService {
    static timezones(): Promise<string[]> {
        return fetchJson('/admin/api/location/timezones');
    }

    static timezone(latitude: string, longitude: string): Promise<string> {
        return fetchJson(`/admin/api/location/timezone?lat=${encodeURIComponent(latitude)}&lng=${encodeURIComponent(longitude)}`);
    }

    /**
     * URL of the static map image for the coordinates, as served by the backend
     */
    static async staticMapImage(latitude: string, longitude: string): Promise<string> {
        return await fetchJson(`/admin/api/location/static-map-image?lat=${encodeURIComponent(latitude)}&lng=${encodeURIComponent(longitude)}`);
    }

    static mapProviderClientApiKey(): Promise<MapProviderAndKeys> {
        return fetchJson('/admin/api/location/map-provider-client-api-key');
    }

    /**
     * Geocodes a free-text address in the browser, as the AngularJS admin does: with the
     * configured provider (Google Maps JS API or HERE), then enriches the coordinates with
     * the static map image and the time zone. Without a configured provider, resolves with
     * null coordinates
     */
    static async geolocate(location: string): Promise<GeolocationResult> {
        const provider = await LocationService.mapProviderClientApiKey();
        let latitude: string | null = null;
        let longitude: string | null = null;
        if (provider.provider === 'GOOGLE') {
            const coordinates = await geocodeWithGoogle(location, provider.keys['MAPS_CLIENT_API_KEY'] ?? null);
            latitude = coordinates.latitude;
            longitude = coordinates.longitude;
        } else if (provider.provider === 'HERE') {
            const coordinates = await geocodeWithHere(location, provider.keys['MAPS_HERE_API_KEY'] ?? '');
            latitude = coordinates.latitude;
            longitude = coordinates.longitude;
        }
        if (latitude == null || longitude == null) {
            return { latitude: null, longitude: null, mapUrl: null, timeZone: null };
        }
        const [mapUrl, timeZone] = await Promise.all([
            LocationService.staticMapImage(latitude, longitude).catch(() => null),
            LocationService.timezone(latitude, longitude).catch(() => null),
        ]);
        return { latitude, longitude, mapUrl, timeZone };
    }
}

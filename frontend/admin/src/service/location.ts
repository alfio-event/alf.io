import {fetchJson} from "./helpers.ts";

export class LocationService {
    static timezones(): Promise<string[]> {
        return fetchJson('/admin/api/location/timezones');
    }
}

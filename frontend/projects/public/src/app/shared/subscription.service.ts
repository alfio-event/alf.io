import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import type { Observable } from 'rxjs';
import { shareReplay } from 'rxjs/operators';
import type { SearchParams } from '../model/search-params';
import type {
    BasicSubscriptionInfo,
    SubscriptionInfo,
} from '../model/subscription';
import type { ValidatedResponse } from '../model/validated-response';

@Injectable({
    providedIn: 'root',
})
export class SubscriptionService {
    private subscriptionCache: { [key: string]: Observable<SubscriptionInfo> } =
        {};

    constructor(private http: HttpClient) {}

    getSubscriptions(
        searchParams?: SearchParams,
    ): Observable<BasicSubscriptionInfo[]> {
        const params = searchParams?.toHttpParams();
        return this.http.get<BasicSubscriptionInfo[]>(
            '/api/v2/public/subscriptions',
            {
                responseType: 'json',
                params,
            },
        );
    }

    getSubscriptionById(id: string): Observable<SubscriptionInfo> {
        if (!this.subscriptionCache[id]) {
            this.subscriptionCache[id] = this.http
                .get<SubscriptionInfo>(`/api/v2/public/subscription/${id}`)
                .pipe(shareReplay(1));
            setTimeout(() => {
                delete this.subscriptionCache[id];
            }, 60000 * 20); // clean up cache after 20 minutes
        }
        return this.subscriptionCache[id];
    }

    reserve(id: string): Observable<ValidatedResponse<string>> {
        return this.http.post<ValidatedResponse<string>>(
            `/api/v2/public/subscription/${id}`,
            {},
        );
    }
}

export function getLocalizedContent(
    container: { [k: string]: string },
    currentLang: string,
) {
    const localizedContent = container[currentLang];
    if (localizedContent != null) {
        return localizedContent;
    }
    return container[Object.keys(container)[0]];
}

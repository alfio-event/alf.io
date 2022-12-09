import { HttpClient } from "@angular/common/http";
import { TranslateLoader } from "@ngx-translate/core";
import { Observable, shareReplay } from "rxjs";

const translationCache: {[key: string]: Observable<any>} = {};

export class CustomLoader implements TranslateLoader {

  constructor(private http: HttpClient) {
  }

  getTranslation(lang: string): Observable<any> {
    if (!translationCache[lang]) {
        translationCache[lang] = this.http.get(`/api/v2/admin/i18n/bundle/${lang}`).pipe(shareReplay(1));
    }
    return translationCache[lang];
  }
}

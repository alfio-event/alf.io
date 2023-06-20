import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { MailInfo } from '../app/model/mail';
import { Observable } from 'rxjs';
import { HttpParams } from '@angular/common/http';

@Injectable({
  providedIn: 'root',
})
export class MailService {
  constructor(private httpClient: HttpClient) {}

  getMails(
    eventShortName: string,
    page: number,
    search: string
  ): Observable<any> {
    const params = {
      params: new HttpParams().set('page', page).set('search', search),
    };

    return this.httpClient.get<any>(
      `admin/api/event/${eventShortName}/email/`,
      params
    );
  }
}

// http://localhost:8080/admin/api/event/lacasadepapel/email/?page=0&search=

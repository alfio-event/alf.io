import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Mail } from '../app/model/mail';
import { Observable } from 'rxjs';
import { HttpParams } from '@angular/common/http';

@Injectable({
  providedIn: 'root',
})
export class MailService {
  constructor(private httpClient: HttpClient) {}

  getMailInfo(
    eventShortName: string,
    page: number,
    search: string
  ): Observable<Mail> {
    const params = {
      params: new HttpParams().set('page', page).set('search', search),
    };

    return this.httpClient.get<Mail>(
      `admin/api/event/${eventShortName}/email/`,
      params
    );
  }
}

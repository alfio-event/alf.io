import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Mail, MailLog } from '../app/model/mail';
import { Observable } from 'rxjs';
import { HttpParams } from '@angular/common/http';

@Injectable({
  providedIn: 'root',
})
export class MailService {
  constructor(private httpClient: HttpClient) {}

  getAllMails(
    eventShortName: string,
    page: number,
    search: string
  ): Observable<MailLog> {
    const params = {
      params: new HttpParams().set('page', page).set('search', search),
    };

    return this.httpClient.get<MailLog>(
      `admin/api/event/${eventShortName}/email/`,
      params
    );
  }

  getMail(eventShortName: string, emailId: string): Observable<Mail> {
    return this.httpClient.get<Mail>(
      `admin/api/event/${eventShortName}/email/${emailId}`
    );
  }
}

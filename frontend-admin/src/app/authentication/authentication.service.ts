import {HttpClient, HttpHeaders} from "@angular/common/http";
import {AuthenticationResult, Credentials} from "./authentication.model";
import {catchError, map, Observable, of} from "rxjs";
import {Injectable} from "@angular/core";

@Injectable()
export class AuthenticationService {
  constructor(private httpClient: HttpClient) {
  }

  public authenticate(credentials: Credentials): Observable<AuthenticationResult> {
    const body = new URLSearchParams();
    body.set('username', credentials.username);
    body.set('password', credentials.password);
    body.set('_csrf', credentials._csrf || '');
    return this.httpClient.post('/authenticate', body, {
      headers: new HttpHeaders().set('Content-type', 'application/x-www-form-urlencoded'),
      observe: "response"
    }).pipe(
        map(() => {
          return { success: true };
        }),
        catchError(err => {
          // FIXME temporary until we refactor the authorization to Spring 6.
          // the form now redirects to
          return of({
            success: err.url === 'http://localhost:8080/authentication',
            error: err?.toString()
          })
        })
      )
  }
}

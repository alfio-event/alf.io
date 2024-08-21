import {Injectable, isDevMode} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {ANONYMOUS, AuthenticationStatus, ClientRedirect, PurchaseContextWithReservation, User} from '../model/user';
import {BehaviorSubject, Observable, of} from 'rxjs';
import {map, mergeMap, tap} from 'rxjs/operators';
import {ValidatedResponse} from '../model/validated-response';

@Injectable({ providedIn: 'root' })
export class UserService {

  private authStatusSubject = new BehaviorSubject<AuthenticationStatus>({ enabled: false });
  private latestValue?: User;

  constructor(private http: HttpClient) {
  }

  initAuthenticationStatus(): Promise<boolean> {
    return new Promise<boolean>((resolve) => this.loadUserStatus()
      .subscribe({
        next: result => {
          this.authStatusSubject.next(result);
          resolve(true);
        },
        error: () => {
          this.authStatusSubject.next({ enabled: false });
          resolve(true); // we resolve the promise anyway
        }
      }));
  }

  updateAuthenticationStatus(enabled: boolean): void {
    if (isDevMode()) {
      console.log('authentication enabled', enabled);
    }
    const payload: AuthenticationStatus = enabled ? {enabled, user: this.latestValue} : {enabled: false};
    this.authStatusSubject.next(payload);
  }

  private loadUserStatus(): Observable<{enabled: boolean, user?: User}> {

    const preloaded = document.querySelector('meta[name=authentication-enabled]');

    const enabled$ = preloaded
      ? of(preloaded.getAttribute('content') === 'true')
      : this.http.get<boolean>('/api/v2/public/user/authentication-enabled');

    return enabled$.pipe(mergeMap(enabled => {
          if (enabled) {
            return this.getUserIdentity().pipe(map(user => ({enabled, user})));
          }
          return of({enabled, user: undefined});
        }),
        tap(status => {
            if (status.user != null) {
                this.latestValue = status.user;
            }
        })
      );
  }

  public getUserIdentity(): Observable<User> {
    return this.http.get<User>('/api/v2/public/user/me', { observe: 'response' })
      .pipe(map(response => {
        if (response.status === 204) {
          return ANONYMOUS;
        } else {
          return response.body;
        }
      }));
  }

  logout(): Observable<ClientRedirect> {
    return this.http.post<ClientRedirect>('/api/v2/public/user/logout', {})
      .pipe(tap(() => {
        this.authStatusSubject.next({ enabled: true });
      }));
  }

  getOrders(): Observable<Array<PurchaseContextWithReservation>> {
    return this.http.get<Array<PurchaseContextWithReservation>>('/api/v2/public/user/reservations');
  }

  updateUser(user: any): Observable<ValidatedResponse<User>> {
    return this.http.post<ValidatedResponse<User>>('/api/v2/public/user/me', user);
  }

  get authenticationStatus(): Observable<AuthenticationStatus> {
    return this.authStatusSubject.asObservable();
  }

  deleteProfile(): Observable<ClientRedirect> {
    return this.http.delete<ClientRedirect>('/api/v2/public/user/me').pipe(
      tap(() => {
        this.authStatusSubject.next({ enabled: true });
      })
    );
  }
}

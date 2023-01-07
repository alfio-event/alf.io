import {Injectable, OnDestroy} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {ANONYMOUS, AuthenticationStatus, ClientRedirect, PurchaseContextWithReservation, User} from '../model/user';
import {BehaviorSubject, interval, Observable, of, Subscription} from 'rxjs';
import {map, mergeMap, tap} from 'rxjs/operators';
import {ValidatedResponse} from '../model/validated-response';

@Injectable({ providedIn: 'root' })
export class UserService implements OnDestroy {

  private authStatusSubject = new BehaviorSubject<AuthenticationStatus>({ enabled: false });
  private authStatusSubscription?: Subscription;
  private authEnabled = false;

  constructor(private http: HttpClient) {
  }

  initAuthenticationStatus(): Promise<boolean> {
    return new Promise<boolean>((resolve) => this.loadUserStatus()
      .subscribe(result => {
        this.authStatusSubject.next(result);
        resolve(true);
      }, () => {
        this.authStatusSubject.next({ enabled: false });
        resolve(true); // we resolve the promise anyway
      }));
  }

  private loadUserStatus(): Observable<{enabled: boolean, user?: User}> {
    return this.http.get<boolean>('/api/v2/public/user/authentication-enabled')
      .pipe(mergeMap(enabled => {
          if (enabled) {
            return this.getUserIdentity().pipe(map(user => ({enabled, user})));
          }
          return of({enabled, user: undefined});
        }),
        tap(status => this.authEnabled = status.enabled)
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

  ngOnDestroy(): void {
    this.authStatusSubscription?.unsubscribe();
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

  private startPolling(): void {
    this.authStatusSubscription = interval(30_000).pipe(
      mergeMap(() => this.loadUserStatus())
    ).subscribe(() => {});
  }

  get authenticationStatus(): Observable<AuthenticationStatus> {
    if (this.authStatusSubscription == null) {
      this.startPolling();
    }
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

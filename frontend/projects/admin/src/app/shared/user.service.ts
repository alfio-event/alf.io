import {Injectable} from "@angular/core";
import {HttpClient} from "@angular/common/http";
import {catchError, map, Observable, of} from "rxjs";
import {User, UserInfo} from "../model/user";

@Injectable()
export class UserService {
  constructor(private httpClient: HttpClient) {
  }

  public checkUserLoggedIn(): Observable<boolean> {
    return this.httpClient.get<{authenticated: boolean}>('/authentication-status', {
      observe: "response"
    }).pipe(
      map(resp => {
        return resp.status === 200
          && (resp.body?.authenticated || false);
      }),
      catchError((err) => {
        console.log('error!', err);
        return of(false);
      })
    )
  }

  getCurrent(): Observable<UserInfo> {
    return this.httpClient.get<UserInfo>('/admin/api/users/current');
  }

  getAllUsers(): Observable<User[]> {
    return this.httpClient.get<User[]>('/admin/api/users');
  }
}

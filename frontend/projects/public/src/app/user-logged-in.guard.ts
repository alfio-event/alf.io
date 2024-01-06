import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import {Injectable} from '@angular/core';
import {Observable} from 'rxjs';
import {UserService} from './shared/user.service';
import {first, map} from 'rxjs/operators';
import {ANONYMOUS} from './model/user';

@Injectable({
  providedIn: 'root'
})
export class UserLoggedInGuard  {

  constructor(private userService: UserService, private router: Router) {}

  canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<boolean | UrlTree> {
    return this.userService.authenticationStatus
      .pipe(
        first(),
        map(status => {
          if (status.enabled && status.user !== ANONYMOUS) {
            return true;
          }
          return this.router.parseUrl('');
        })
      );
  }
}

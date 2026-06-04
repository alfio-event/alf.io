import { Injectable } from '@angular/core';
import type {
    ActivatedRouteSnapshot,
    Router,
    RouterStateSnapshot,
    UrlTree,
} from '@angular/router';
import type { Observable } from 'rxjs';
import { first, map } from 'rxjs/operators';
import { ANONYMOUS } from './model/user';
import type { UserService } from './shared/user.service';

@Injectable({
    providedIn: 'root',
})
export class UserLoggedInGuard {
    constructor(
        private userService: UserService,
        private router: Router,
    ) {}

    canActivate(
        route: ActivatedRouteSnapshot,
        state: RouterStateSnapshot,
    ): Observable<boolean | UrlTree> {
        return this.userService.authenticationStatus.pipe(
            first(),
            map((status) => {
                if (status.enabled && status.user !== ANONYMOUS) {
                    return true;
                }
                return this.router.parseUrl('');
            }),
        );
    }
}

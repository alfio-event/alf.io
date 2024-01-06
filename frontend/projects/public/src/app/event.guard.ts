import {Injectable} from '@angular/core';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import {Observable, of} from 'rxjs';
import {EventService} from './shared/event.service';
import {catchError, map, tap} from 'rxjs/operators';
import {handleCustomCss} from './shared/custom-css-helper';

@Injectable({
  providedIn: 'root'
})
export class EventGuard  {

  constructor(private eventService: EventService, private router: Router) {
  }

  canActivate(next: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<boolean | UrlTree> {
    const eventShortName = next.params['eventShortName'];
    return this.eventService.getEvent(eventShortName)
      .pipe(
        tap((e) => handleCustomCss(e)),
        catchError(e => of(this.router.parseUrl(''))),
        map(e => e instanceof UrlTree ? e : true)
      );
  }
}

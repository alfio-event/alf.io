import {ActivatedRouteSnapshot, RouterStateSnapshot} from '@angular/router';
import {removeAllCustomEventCss} from './shared/custom-css-helper';

export function removeEventCss(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): boolean {
  removeAllCustomEventCss();
  return true;
}

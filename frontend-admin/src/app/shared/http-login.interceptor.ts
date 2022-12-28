import {HttpEvent, HttpHandler, HttpInterceptor, HttpRequest} from "@angular/common/http";
import {catchError, Observable, throwError} from "rxjs";
import {Inject, Injectable, Injector, INJECTOR} from "@angular/core";
import {environment} from "../../environments/environment";
import {Router} from "@angular/router";

@Injectable()
export class HttpLoginInterceptor implements HttpInterceptor {

  constructor(@Inject(INJECTOR) private injector: Injector) {
  }

  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    // set X-Requested-With to XMLHttpRequest to match checks on the backend
    const clonedRequest = req.clone({
      headers: req.headers.set("X-Requested-With", "XMLHttpRequest")
    });
    return next.handle(clonedRequest)
      .pipe(catchError(err => {
        if (err.status === 401) {
          // user authentication is not valid anymore.
          // let's redirect it to the authentication resource
          redirectToLogin(this.injector.get(Router)).then(() => {})
        }
        return throwError(() => err);
      }));
  }

}

export function redirectToLogin(router: Router): Promise<boolean> {
  if (environment.production) {
    // reload the current location to trigger server-side authentication
    window.location.reload();
  } else {
    // dev mode: redirect to the /authentication local resource
    return router.navigate(['./authentication']);
  }
  return Promise.resolve(true);
}

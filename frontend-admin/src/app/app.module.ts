import {APP_INITIALIZER, NgModule} from '@angular/core';
import {BrowserModule} from '@angular/platform-browser';

import {AppRoutingModule} from './app-routing.module';
import {AppComponent} from './app.component';
import {AuthenticationModule} from "./authentication/authentication.module";
import {HTTP_INTERCEPTORS, HttpClientModule, HttpClientXsrfModule, HttpXsrfTokenExtractor} from "@angular/common/http";
import {AuthTokenInterceptor, DOMGidExtractor, DOMXsrfTokenExtractor} from "./shared/xsrf";
import {UserService} from "./shared/user.service";
import {Router} from "@angular/router";
import {firstValueFrom} from "rxjs";

export function RedirectToLoginIfNeeded(userService: UserService, router: Router): () => Promise<boolean> {
  return () => {
    return firstValueFrom(userService.checkUserLoggedIn())
      .then(loggedIn => {
        if (!loggedIn) {
          return router.navigate(['/authentication']);
        }
        return router.navigate(['/dashboard']);
      });
  };
}

@NgModule({
  declarations: [
    AppComponent
  ],
  imports: [
    BrowserModule,
    AppRoutingModule,
    AuthenticationModule,
    HttpClientModule,
    HttpClientXsrfModule.withOptions({
      cookieName: 'XSRF-TOKEN',
      headerName: 'X-CSRF-TOKEN',
    })
  ],
  providers: [
    { provide: APP_INITIALIZER, useFactory: RedirectToLoginIfNeeded, deps: [UserService, Router], multi: true},
    { provide: HTTP_INTERCEPTORS, useClass: AuthTokenInterceptor, multi: true },
    { provide: HttpXsrfTokenExtractor, useClass: DOMXsrfTokenExtractor },
    DOMGidExtractor,
    DOMXsrfTokenExtractor,
    UserService
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }

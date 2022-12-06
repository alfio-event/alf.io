import {APP_INITIALIZER, NgModule} from '@angular/core';
import {BrowserModule} from '@angular/platform-browser';
import {provideSvgIcons} from '@ngneat/svg-icon';
import {AppRoutingModule} from './app-routing.module';
import {AppComponent} from './app.component';
import {AuthenticationModule} from "./authentication/authentication.module";
import {HTTP_INTERCEPTORS, HttpClientModule, HttpClientXsrfModule, HttpXsrfTokenExtractor} from "@angular/common/http";
import {AuthTokenInterceptor, DOMGidExtractor, DOMXsrfTokenExtractor} from "./shared/xsrf";
import {UserService} from "./shared/user.service";
import {Router} from "@angular/router";
import {firstValueFrom} from "rxjs";
import { SvgIconComponent } from '@ngneat/svg-icon';
import {OrganizationService} from './shared/organization.service';
import {MissingOrgComponent} from './missing-org/missing-org.component';
import { ICONS } from './shared/icons';

export function RedirectToLoginIfNeeded(userService: UserService, router: Router): () => Promise<boolean> {
  return async () => {
    const loggedIn = await firstValueFrom(userService.checkUserLoggedIn())
    if (!loggedIn) {
      return router.navigate(['/authentication']);
    }
    return true;
  };
}

@NgModule({
  declarations: [
    AppComponent,
    MissingOrgComponent
  ],
  imports: [
    BrowserModule,
    AppRoutingModule,
    AuthenticationModule,
    HttpClientModule,
    HttpClientXsrfModule.withOptions({
      cookieName: 'XSRF-TOKEN',
      headerName: 'X-CSRF-TOKEN',
    }),
    SvgIconComponent
  ],
  providers: [
    { provide: APP_INITIALIZER, useFactory: RedirectToLoginIfNeeded, deps: [UserService, Router], multi: true},
    { provide: HTTP_INTERCEPTORS, useClass: AuthTokenInterceptor, multi: true },
    { provide: HttpXsrfTokenExtractor, useClass: DOMXsrfTokenExtractor },
    provideSvgIcons(ICONS),
    DOMGidExtractor,
    DOMXsrfTokenExtractor,
    UserService,
    OrganizationService,
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }

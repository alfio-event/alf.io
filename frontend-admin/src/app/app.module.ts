import {APP_INITIALIZER, NgModule} from '@angular/core';
import {BrowserModule} from '@angular/platform-browser';
import {provideSvgIconsConfig, SvgIconComponent} from '@ngneat/svg-icon';
import {AppRoutingModule} from './app-routing.module';
import {AppComponent} from './app.component';
import {AuthenticationModule} from "./authentication/authentication.module";
import {
  HTTP_INTERCEPTORS,
  HttpClient,
  HttpClientModule,
  HttpClientXsrfModule,
  HttpXsrfTokenExtractor
} from "@angular/common/http";
import {AuthTokenInterceptor, DOMGidExtractor, DOMXsrfTokenExtractor} from "./shared/xsrf";
import {UserService} from "./shared/user.service";
import {Router} from "@angular/router";
import {firstValueFrom} from "rxjs";
import {OrganizationService} from './shared/organization.service';
import {MissingOrgComponent} from './missing-org/missing-org.component';
import {ICON_CONFIG} from './shared/icons';
import {TranslateLoader, TranslateModule} from '@ngx-translate/core';
import {CustomLoader} from './shared/i18n.service';
import {OrgSelectorComponent} from './org-selector/org-selector.component';
import {NgbDropdownModule} from "@ng-bootstrap/ng-bootstrap";
import {SectionDashboardComponent} from "./shared/section-dashboard/section-dashboard.component";
import {SharedModule} from "./shared/shared.module";
import {environment} from "../environments/environment";

/**
 * This function should only work
 * @param userService
 * @param router
 * @constructor
 */
export function RedirectToLoginIfNeeded(userService: UserService, router: Router): () => Promise<boolean> {
  return async () => {
    const loggedIn = await firstValueFrom(userService.checkUserLoggedIn())
    if (!loggedIn) {
      if (environment.production) {
        // reload the current location to trigger server-side authentication
        window.location.reload();
      } else {
        // dev mode: redirect to the /authentication local resource
        return router.navigate(['/authentication']);
      }
    }
    return true;
  };
}

// AoT requires an exported function for factories
export function HttpLoaderFactory(http: HttpClient) {
  return new CustomLoader(http);
}

@NgModule({
  declarations: [
    AppComponent,
    MissingOrgComponent,
    OrgSelectorComponent,
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
    SvgIconComponent,
    TranslateModule.forRoot({
      loader: {
        provide: TranslateLoader,
        useFactory: HttpLoaderFactory,
        deps: [HttpClient]
      }
    }),
    NgbDropdownModule,
    SharedModule,
  ],
  providers: [
    {provide: APP_INITIALIZER, useFactory: RedirectToLoginIfNeeded, deps: [UserService, Router], multi: true},
    {provide: HTTP_INTERCEPTORS, useClass: AuthTokenInterceptor, multi: true},
    {provide: HttpXsrfTokenExtractor, useClass: DOMXsrfTokenExtractor},
    provideSvgIconsConfig(ICON_CONFIG),
    DOMGidExtractor,
    DOMXsrfTokenExtractor,
    UserService,
    OrganizationService,
  ],
  exports: [
    SectionDashboardComponent
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }

import {APP_INITIALIZER, NgModule} from '@angular/core';
import {BrowserModule} from '@angular/platform-browser';
import {provideSvgIconsConfig, SvgIconComponent} from '@ngneat/svg-icon';
import {AppRoutingModule} from './app-routing.module';
import {AppComponent} from './app.component';
import {AuthenticationModule} from "./authentication/authentication.module";
import {HTTP_INTERCEPTORS, HttpClient, HttpClientModule, HttpClientXsrfModule} from "@angular/common/http";
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
import {HttpLoginInterceptor, redirectToLogin} from "./shared/http-login.interceptor";
import {AlfioCommonModule} from "common";

export function RedirectToLoginIfNeeded(userService: UserService, router: Router): () => Promise<boolean> {
  return async () => {
    const loggedIn = await firstValueFrom(userService.checkUserLoggedIn())
    if (!loggedIn) {
      return redirectToLogin(router);
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
    AlfioCommonModule
  ],
  providers: [
    {provide: APP_INITIALIZER, useFactory: RedirectToLoginIfNeeded, deps: [UserService, Router], multi: true},
    {provide: HTTP_INTERCEPTORS, useClass: HttpLoginInterceptor, multi: true},
    provideSvgIconsConfig(ICON_CONFIG),
    UserService,
    OrganizationService,
  ],
  exports: [
    SectionDashboardComponent
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }

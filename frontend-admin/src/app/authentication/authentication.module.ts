import {NgModule} from "@angular/core";
import {AuthenticationComponent} from "./authentication.component";
import {ReactiveFormsModule} from "@angular/forms";
import {AuthenticationService} from "./authentication.service";
import {RouterModule} from "@angular/router";
import {HttpClientModule} from "@angular/common/http";

@NgModule({
  imports: [
    ReactiveFormsModule,
    HttpClientModule,
    RouterModule.forChild([
      {
        path: '',
        component: AuthenticationComponent
      }
    ])
  ],
  declarations: [
    AuthenticationComponent
  ],
  providers: [
    AuthenticationService
  ]
})
export class AuthenticationModule {}

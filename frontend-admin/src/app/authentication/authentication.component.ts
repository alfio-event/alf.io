import {Component} from "@angular/core";
import {AbstractControl, FormGroup, NonNullableFormBuilder} from "@angular/forms";
import {AuthenticationService} from "./authentication.service";
import {Router} from "@angular/router";
import {DOMXsrfTokenExtractor} from "../shared/xsrf";

interface LoginForm {
  username: AbstractControl<string>;
  password: AbstractControl<string>;
}

@Component({
  selector: 'app-authentication',
  templateUrl: './authentication.component.html'
})
export class AuthenticationComponent {
  formGroup: FormGroup<LoginForm>;

  constructor(formBuilder: NonNullableFormBuilder,
              private authenticationService: AuthenticationService,
              private router: Router,
              private xsrfProvider: DOMXsrfTokenExtractor) {
    this.formGroup = formBuilder.group<LoginForm>({
      username: formBuilder.control(''),
      password: formBuilder.control('')
    });
  }

  submit(): void {
    this.authenticationService.authenticate({
      username: this.formGroup.value.username || '',
      password: this.formGroup.value.password || '',
      _csrf: this.xsrfProvider.getToken()
    }).subscribe(result => {
      if (result.success) {
        this.router.navigate(['/']);
      } else {
        console.log('error', result.errorMessage);
      }
    })
  }

}

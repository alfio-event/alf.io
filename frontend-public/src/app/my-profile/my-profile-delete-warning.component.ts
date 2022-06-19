import {Component} from '@angular/core';
import {NgbActiveModal} from '@ng-bootstrap/ng-bootstrap';
import {FormBuilder, FormGroup, Validators} from '@angular/forms';

@Component({
  selector: 'app-my-profile-delete-warning',
  templateUrl: './my-profile-delete-warning.component.html'
})
export class MyProfileDeleteWarningComponent {

  form: FormGroup;

  constructor(public activeModal: NgbActiveModal, builder: FormBuilder) {
    this.form = builder.group({
      acknowledge: builder.control(false, Validators.required)
    });
  }

  confirm(): void {
    if (this.form.valid) {
      console.log('closing');
      this.activeModal.close('ok');
    }
  }
}

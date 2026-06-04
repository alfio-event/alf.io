import { Component } from "@angular/core";
import {
  type UntypedFormBuilder,
  type UntypedFormGroup,
  Validators,
} from "@angular/forms";
import type { NgbActiveModal } from "@ng-bootstrap/ng-bootstrap";

@Component({
  selector: "app-my-profile-delete-warning",
  templateUrl: "./my-profile-delete-warning.component.html",
})
export class MyProfileDeleteWarningComponent {
  form: UntypedFormGroup;

  constructor(
    public activeModal: NgbActiveModal,
    builder: UntypedFormBuilder,
  ) {
    this.form = builder.group({
      acknowledge: builder.control(false, Validators.required),
    });
  }

  confirm(): void {
    if (this.form.valid) {
      console.log("closing");
      this.activeModal.close("ok");
    }
  }
}

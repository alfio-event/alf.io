import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { OrganizationService } from '../shared/organization.service';
import { UserService } from '../shared/user.service';
import { Observable } from 'rxjs';
import { Organization } from '../model/organization';
import { Role } from '../model/role';

@Component({
  selector: 'app-user-system-edit',
  templateUrl: './user-system-edit.component.html',
  styleUrls: ['./user-system-edit.component.scss'],
})
export class UserSystemEditComponent implements OnInit {
  public userForm: FormGroup;
  public organizations$?: Observable<Organization[]>;
  public roles$? : Observable<Role[]>;
  constructor(
    formBuilder: FormBuilder,
    private readonly organizationService: OrganizationService,
    private readonly userService: UserService
  ) {
    this.userForm = formBuilder.group({
      target: null,
      organizationId: [null, Validators.required],
      role: [null, Validators.required],
      username: [null, Validators.required],
      firstName: [null, Validators.required],
      lastName: [null, Validators.required],
      emailAddress: [null, Validators.required],
    });
  }

  get userName() {
    return this.userForm.get('username');
  }
  get userLastName() {
    return this.userForm.get('lastName');
  }
  get userFirstName() {
    return this.userForm.get('firstName');
  }

  get userMail() {
    return this.userForm.get('emailAddress');
  }

  ngOnInit(): void {
    this.organizations$ = this.organizationService.getOrganizations();
    this.roles$ = this.userService.getAllRoles();
  }

  save(): void {
    console.log(this.userForm.value);
  }
}

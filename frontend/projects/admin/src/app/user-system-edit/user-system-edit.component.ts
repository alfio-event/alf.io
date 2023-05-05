import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { OrganizationService } from '../shared/organization.service';
import { UserService } from '../shared/user.service';
import { Observable, map, of } from 'rxjs';
import { Organization } from '../model/organization';
import { Role } from '../model/role';
import { ActivatedRoute, Router } from '@angular/router';
import { RoleType, User } from '../model/user';

@Component({
  selector: 'app-user-system-edit',
  templateUrl: './user-system-edit.component.html',
  styleUrls: ['./user-system-edit.component.scss'],
})
export class UserSystemEditComponent implements OnInit {
  public userId: string | null = null;
  public userForm: FormGroup;
  public organizations$?: Observable<Organization[]>;
  public roles$?: Observable<Role[]>;
  public editMode: boolean | undefined;
  public user$: Observable<User | null> = of();
  constructor(
    private readonly route: ActivatedRoute,
    formBuilder: FormBuilder,
    private readonly organizationService: OrganizationService,
    private readonly userService: UserService,
    private readonly router: Router
  ) {
    this.userForm = formBuilder.group({
      target: null,
      id: [null],
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
    this.userId = this.route.snapshot.paramMap.get('userId');

    this.organizations$ = this.organizationService.getOrganizations();
    this.roles$ = this.userService.getAllRoles().pipe(
      map((roles) => {
        return roles.filter((role) => role.target.includes('USER'));
      })
    );

    if (this.userId !== null) {
      this.editMode = true;
      this.user$ = this.userService.getUser(this.userId);
      this.user$.subscribe((user) => {
        if (user) this.userForm.patchValue(user);
      });
    } else {
      this.editMode = false;
    }
  }

  save(): void {
    let result: Observable<any>;
    if (this.editMode) {
      result = this.userService.update(this.userForm.value);
    } else {
      result = this.userService.create(this.userForm.value);
    }
    result.subscribe((res) => {
      if (res === 'OK') {
        this.router.navigate(['/access-control/users']);
      }
    });
  }


}

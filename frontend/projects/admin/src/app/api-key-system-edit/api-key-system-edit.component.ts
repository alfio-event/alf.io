import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Organization } from '../model/organization';
import { Observable, map, of } from 'rxjs';
import { User } from '../model/user';
import { OrganizationService } from '../shared/organization.service';
import { UserService } from '../shared/user.service';
import { ActivatedRoute, Router } from '@angular/router';
import { Role } from '../model/role';

@Component({
  selector: 'app-api-key-system-edit',
  templateUrl: './api-key-system-edit.component.html',
  styleUrls: ['./api-key-system-edit.component.scss'],
})
export class ApiKeySystemEditComponent implements OnInit {
  public userForm: FormGroup;
  public editMode: boolean | undefined;
  public organizations$?: Observable<Organization[]>;
  public user$: Observable<User | null> = of();
  public roles$?: Observable<Role[]>;
  public userId: string | null = null;

  constructor(
    formBuilder: FormBuilder,
    private readonly organizationService: OrganizationService,
    private readonly userService: UserService,
    private readonly router: Router,
    private readonly route: ActivatedRoute
  ) {
    this.userForm = formBuilder.group({
      id: [null],
      organizationId: [null, Validators.required],
      role: [null, Validators.required],
      description: [null],
      username: [null],
      lastName: [null],
      firstName: [null],
      emailAddress: [null],
      type: ['API_KEY'],
    });
  }
  get organizationDescription() {
    return this.userForm.get('description');
  }

  get organizationName() {
    return this.userForm.get('organizationId');
  }

  get role() {
    return this.userForm.get('role');
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
        console.log(user);
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
      this.router.navigate(['/access-control/api-keys']);
    });
  }
}

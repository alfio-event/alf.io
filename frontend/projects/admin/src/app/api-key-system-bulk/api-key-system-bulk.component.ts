import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Organization } from '../model/organization';
import { Observable, map } from 'rxjs';
import { Role } from '../model/role';
import { OrganizationService } from '../shared/organization.service';
import { UserService } from '../shared/user.service';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';

@Component({
  selector: 'app-api-key-system-bulk',
  templateUrl: './api-key-system-bulk.component.html',
  styleUrls: ['./api-key-system-bulk.component.scss'],
})
export class ApiKeySystemBulkComponent implements OnInit {
  public organizations$?: Observable<Organization[]>;
  public roles$?: Observable<Role[]>;
  public bulkForm: FormGroup;

  constructor(
    private readonly route: ActivatedRoute,
    private readonly organizationService: OrganizationService,
    private readonly userService: UserService,
    private readonly router: Router,
    formBuilder: FormBuilder
  ) {
    this.bulkForm = formBuilder.group({
      organizationId: [null, Validators.required],
      role: [null, Validators.required],
      descriptions: [[]],
    });
  }

  get bulkDescriptions(): string[] {
    return this.bulkForm.value.descriptions;
  }

  get userOrganizationId(){return this.bulkForm.get('organizationId')}
  get userRole(){return this.bulkForm.get('role')}

  ngOnInit(): void {
    this.organizations$ = this.organizationService.getOrganizations();
    this.roles$ = this.userService.getAllRoles();
  }

  save(): void {
    this.userService.createApiBulk(this.bulkForm.value).subscribe((res) => {
      if (res === 'OK') {
        this.router.navigate(['/access-control/api-keys']);
      }
    });
  }

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input && input.files) {
      const file: File = input.files[0];
      file.text().then((res) => {
        const descriptions = res
          .split('\n')
          .map((es) => es.trim())
          .filter((es) => es !== null && es.length > 0);
        this.bulkForm.get('descriptions')?.patchValue(descriptions);
      });
    }
  }
}

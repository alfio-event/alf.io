import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { OrganizationService } from '../shared/organization.service';
import { Observable, of } from 'rxjs';
import { Organization } from '../model/organization';
import { ConfigurationService } from '../shared/configuration.service';
import { InstanceSetting } from '../model/instance-settings';
import { FormBuilder, FormGroup } from '@angular/forms';
import { Validators } from '@angular/forms';

@Component({
  selector: 'app-organization-edit',
  templateUrl: './organization-edit.component.html',
  styleUrls: ['./organization-edit.component.scss'],
})
export class OrganizationEditComponent implements OnInit {
  public organizationId: string | null = null;
  public organization$: Observable<Organization | null> = of();
  public editMode: boolean | undefined;
  public instanceSetting$: Observable<InstanceSetting> = of();
  public organizationForm: FormGroup;

  constructor(
    private readonly route: ActivatedRoute,
    private readonly organizationService: OrganizationService,
    private readonly configurationService: ConfigurationService,
    formBuilder: FormBuilder,
    private readonly router: Router
  ) {
    this.organizationForm = formBuilder.group({
      id: [null],
      name: [null, Validators.required],
      email: [null, Validators.required],
      description: [null, Validators.required],
      slug: [],
      externalId: [],
    });
  }

  ngOnInit(): void {
    this.organizationId = this.route.snapshot.paramMap.get('organizationId');
    this.instanceSetting$ = this.configurationService.loadInstanceSetting();

    if (this.organizationId !== null) {
      this.editMode = true;
      this.organization$ = this.organizationService.getOrganization(
        this.organizationId
      );
      this.organization$.subscribe((org) => {
        if (org) this.organizationForm.patchValue(org);
      });
    } else {
      this.editMode = false;
    }
  }

  save(): void {
    const action = this.editMode
      ? this.organizationService.update
      : this.organizationService.create;

    action(this.organizationForm.value).subscribe((result) => {
      if (result === 'OK') {
        this.router.navigate(['/organizations']);
      }
    });
  }
}

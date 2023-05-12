import { Component, OnInit } from '@angular/core';
import { UserService } from '../shared/user.service';
import { Observable, map, of, shareReplay } from 'rxjs';
import { RoleType, User } from '../model/user';
import { Organization } from '../model/organization';
import { Role } from '../model/role';

@Component({
  selector: 'app-api-key-system',
  templateUrl: './api-key-system.component.html',
  styleUrls: ['./api-key-system.component.scss'],
})
export class ApiKeySystemComponent implements OnInit {
  public users$?: Observable<User[]>;
  public organizations$?: Observable<Organization[]>;
  public roles$: Observable<Map<RoleType, Role>> = of();
  public selectedOrganization?: Organization;

  constructor(private readonly userService: UserService) {}

  ngOnInit(): void {
    this.loadApiKey();
    this.roles$ = this.userService.getAllRoles().pipe(
      map((roles) => {
        const map: Map<RoleType, Role> = new Map();
        roles.forEach((role) => {
          map.set(role.role, role);
        });
        return map;
      }),
      shareReplay(1)
    );
  }

  private loadApiKey() {
    this.users$ = this.userService.getAllApiKey();
    this.users$.subscribe((users) => {
      const orgIdToOrg = new Map<number, Organization>();
      users.forEach((user) => {
        user.memberOf.forEach((org) => {
          orgIdToOrg.set(org.id, org);
        });
      });

      const organizations = Array.from(orgIdToOrg.values());
      this.organizations$ = of(organizations);
    });
  }

  roleDescription(role: RoleType): Observable<string | undefined> {
    return this.roles$.pipe(map((roles) => roles.get(role)?.description));
  }

  enable(user: User) {
    this.userService.enable(user, !user.enabled).subscribe((result) => {
      this.loadApiKey();
    });
  }

  deleteUserApikey(user: User) {
    if (
      window.confirm(
        `The apikey ${user.username} will be deleted. Are you sure?`
      )
    ) {
      this.userService.deleteUser(user).subscribe((result) => {
        this.loadApiKey();
      });
    }
  }

  downloadAllApiKeys(orgId: number) {
    window.open(`/admin/api/api-keys/organization/${orgId}/all`);
  }
}

import { Pipe, PipeTransform } from '@angular/core';
import { User } from '../model/user';
import { Organization } from '../model/organization';

@Pipe({
  name: 'usersFilterOrg',
  pure: true,
})
export class UsersFilterOrgPipe implements PipeTransform {
  transform(
    value: User[] | null,
    org: Organization | undefined
  ): User[] | null {
    if (!value) {
      return value;
    }
    return value.filter((user) => {
      if (!org) {
        return true;
      }
      return user.memberOf.some(
        (userOrganization) => userOrganization.id === org.id
      );
    });
  }
}

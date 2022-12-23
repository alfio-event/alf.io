import {Pipe, PipeTransform} from "@angular/core";
import {UserInfo} from "../model/user";

@Pipe({
  name: 'userName',
  pure: true
})
export class UserNamePipe implements PipeTransform {
  transform(user: UserInfo | null): string {
    if (user != null) {
      return user.firstName + " " + user.lastName;
    }
    return "";
  }

}

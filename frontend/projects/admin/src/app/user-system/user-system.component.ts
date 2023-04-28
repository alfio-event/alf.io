import { Component, OnInit } from '@angular/core';
import { Observable } from 'rxjs';
import { UserService } from '../shared/user.service';
import { User } from '../model/user';

@Component({
  selector: 'app-user-system',
  templateUrl: './user-system.component.html',
  styleUrls: ['./user-system.component.scss'],
})
export class UserSystemComponent implements OnInit {
  public users$?: Observable<User[]>;

  constructor(private readonly userService: UserService) {}

  ngOnInit(): void {
    this.users$ = this.userService.getAllUsers();
  }

  enable(user : User){
    this.userService.enable(user, !user.enabled).subscribe((result) => {
      this.users$ = this.userService.getAllUsers();
    });
  }

  deleteUser(user : User) {
    if (window.confirm(`The user ${user.username} will be deleted. Are you sure?`)){
      this.userService.deleteUser(user).subscribe((result) => {
        this.users$ = this.userService.getAllUsers();
      })
    }
  }
}

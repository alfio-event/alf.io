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
}

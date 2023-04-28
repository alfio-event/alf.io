import { Component, OnInit } from '@angular/core';
import { Observable } from 'rxjs';
import { UserService } from '../shared/user.service';

@Component({
  selector: 'app-user-system',
  templateUrl: './user-system.component.html',
  styleUrls: ['./user-system.component.scss'],
})
export class UserSystemComponent implements OnInit {
  public users$?: Observable<any[]>;
  constructor(private readonly userService: UserService) {}

  ngOnInit(): void {
    this.users$ = this.userService.getAllUsers();
  }
}

import { Component, OnInit } from '@angular/core';

import { EventService } from '../core/services/event.service';
import { UserService } from '../core/services/user.service';

@Component({
  selector: 'app-signout',
  template: ``,
})
export class SignOutComponent implements OnInit {
  constructor(
    private userService: UserService,
    protected events: EventService,
  ) {}

  ngOnInit() {
    this.userService.signout();
  }
}

import { Component } from '@angular/core';

@Component({
  selector: 'user-context',
  template: `
    <div class="group">
      <button type="button"
        class="btn btn-primary btn-block"
        [routerLink]="['/desk', 'users', 'manage', 'create']"
        routerLinkActive #userCreateActive="routerLinkActive"
        [ngClass]="{'btn-alt': !userCreateActive.isActive}"
      >Create a New User</button>
    </div>

    <div class="menu">
      <ul class="nav nav-stacked">
        <li [routerLinkActive]="['active']">
          <a [routerLink]="['/desk', 'users', 'manage']">
            <i class="glyphicon glyphicon-user"></i>
            <span>Users</span>
          </a>
        </li>
      </ul>
    </div>
  `,
})
export class UserContextComponent {
  constructor(
  ) {
  }
}

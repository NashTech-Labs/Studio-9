import { Component } from '@angular/core';

import { NotificationService } from '../core/services/notification.service';

@Component({
  selector: 'notification-list',
  template: `
    <div *ngIf="notifications.data" id="layout-notifications">
      <div *ngFor="let item of notifications.data">
        <app-notification [item]="item"></app-notification>
      </div>
    </div>
  `,
})
export class NotificationListComponent {

  constructor(private _notifications: NotificationService) {
  }

  get notifications() {
    return this._notifications;
  }
}


import { Component, Input } from '@angular/core';

import config from '../config';
import { INotification } from '../core/interfaces/notification.interface';
import { NotificationService } from '../core/services/notification.service';

@Component({
  selector: 'app-notification',
  template: `
    <div (mouseenter)="mouseEnter()" (mouseleave)="mouseLeave()" [ngSwitch]="item.type"
      [ngClass]="'alert alert-' + (item.data.level || 'info')">
      <button type="button" class="close"
        (click)="dismiss()"
        aria-label="Close"
      ><span aria-hidden="true">&times;</span></button>

      <ng-template [ngSwitchCase]="config.notification.type.values.FILE_UPLOAD">
        <div *ngIf="item.data.file"><strong>{{item.data.file.name}}</strong>:</div>
        <div *ngIf="item.data.files"><strong>Multiple Files Upload</strong>:</div>
        <div *ngIf="item.data.html" [innerHTML]="item.data.html"></div>
        <div *ngIf="item.data.text">{{item.data.text}}</div>
        <progress class="progress progress-striped" [value]="item.data.progress" max="100"></progress>
      </ng-template>

      <ng-template ngSwitchDefault>
        <div *ngIf="item.data.html" [innerHTML]="item.data.html"></div>
        <div *ngIf="item.data.text">{{item.data.text}}</div>
      </ng-template>
    </div>
  `,
})
export class NotificationComponent {
  @Input() item: INotification;
  readonly config = config;

  private pausedDate: number;
  private prevTimeout: number;

  constructor(readonly notifications: NotificationService) {}

  dismiss() {
    if (this.item.data.cancel) {
      if (this.item.data.canCancel) {
        this.item.data.cancel();
        this.notifications.delete(this.item.id);
      }
    } else {
      this.notifications.delete(this.item.id);
    }
  }

  mouseEnter() {
    if (this.item.options.timeout && this.item.options.pauseOnHover) {
      this.pausedDate = Date.now();
      this.prevTimeout = this.item.options.timeout;
      this.item.options.timeout = 0;
    }
  }

  mouseLeave() {
    if (this.item.options.pauseOnHover && this.pausedDate && this.prevTimeout) {
      this.item.options.timeout = Date.now() - this.pausedDate + this.prevTimeout;
    }
  }
}


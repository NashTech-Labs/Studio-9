import { Component, ViewChild } from '@angular/core';

import config from '../config';
import { Feature } from '../core/interfaces/feature-toggle.interface';
import { IUser } from '../core/interfaces/user.interface';
import { EventService, IEvent } from '../core/services/event.service';
import { UserService } from '../core/services/user.service';
import { UserRole } from '../users/user.interfaces';

import { MyPasswordChangeModalComponent } from './my-password-change-modal.component';

@Component({
  selector: 'app-layout-desk-header',
  template: `
  <nav class="navbar navbar-default nav-header brand-header" role="navigation">
    <div class="navbar-collapse navbar-ex1-collapse">
      <ul class="nav navbar-nav navbar-left">
        <li [routerLinkActive]="['active']">
          <a [routerLink]="['/desk', 'library']">Library</a>
        </li>
        <li [class.active]="composeSection.isActive">
          <a [routerLink]="['/desk', 'pipelines']">Compose</a>
        </li>
        <li *featureToggle="'${Feature.EXPERIMENTS_MODULE}'" [routerLinkActive]="['active']">
          <a [routerLink]="['/desk', 'experiments']">Lab</a>
        </li>
        <li *featureToggle="'${Feature.PLAY_MODULE}'" [routerLinkActive]="['active']">
          <a [routerLink]="['/desk', 'play']">Play</a>
        </li>
        <li *mocksOnly="true" [routerLinkActive]="['active']">
          <a [routerLink]="['/desk', 'deploy']">Deploy</a>
        </li>
        <li [routerLinkActive]="['active']">
          <a [routerLink]="['/desk', 'develop']">Code</a>
        </li>
        <li [class.active]="analyticsSection.isActive">
          <a [routerLink]="['/desk', 'albums']">
            Visualization
          </a>
        </li>
<!--
        <li *mocksOnly="true" [routerLinkActive]="['active']">
          <a [routerLink]="['/desk', 'diaa']">DIAA</a>
        </li>
        <li *mocksOnly="true" [routerLinkActive]="['active']">
          <a [routerLink]="['/desk', 'optimization']">Optimize</a>
        </li>
-->
        <li [routerLinkActive]="['active']" *ngIf="user.getUser() | apply: _userHasRole: userRole.ADMIN">
          <a [routerLink]="['/desk', 'users']">Admin</a>
        </li>
      </ul>
      <ul class="nav navbar-nav navbar-right">
        <li class="nav-item" [ngClass]="{disabled: true}">
          <a class="nav-link">
            <i class="iconapp iconapp-bell"></i>
            <span class="badge">--</span>
          </a>
        </li>
        <li class="nav-item" [routerLinkActive]="['active']">
          <a [routerLink]="['/desk', 'processes']" class="nav-link" title="Jobs">
            <i class="iconapp iconapp-clock"></i>
          </a>
        </li>
        <li class="nav-item" [ngClass]="{disabled: true}">
          <a class="nav-link">
            <i class="iconapp iconapp-dashboard"></i>
          </a>
        </li>
        <li class="dropdown" dropdown>
          <a class="nav-link" data-toggle="dropdown" aria-haspopup="true" aria-expanded="true">
            <span style="padding-right: 20px;">{{(user.user | async).firstName}}</span>
            <span class="glyphicon glyphicon-option-horizontal"></span>
          </a>

          <ul class="dropdown-menu">
            <li><a (click)="passwordModal.show();" class="link">Change password</a></li>
            <li role="separator" class="divider"></li>
            <li><a [routerLink]="config.routes.signout" class="link">Logout</a></li>
            <li *mocksOnly="true"><a (click)="cleanMocks()" class="link">Reset fixtures</a></li>
          </ul>
        </li>
      </ul>
    </div>
  </nav>
  <nav class="navbar navbar-default nav-header brand-header navbar-conditional" role="navigation" [routerLinkActive]="['active']">
    <div class="navbar-collapse navbar-ex1-collapse">
      <ul class="nav navbar-nav navbar-conditional navbar-left" [routerLinkActive]="['active']" #composeSection="routerLinkActive">
        <li [routerLinkActive]="['active']">
          <a [routerLink]="['/desk', 'pipelines']">Canvas</a>
        </li>
        <li *mocksOnly="true" [routerLinkActive]="['active']">
          <a [routerLink]="['/desk', 'flows']">Worksheet</a>
        </li>
      </ul>
      <ul class="nav navbar-nav navbar-conditional navbar-left" [routerLinkActive]="['active']" #analyticsSection="routerLinkActive">
        <li [routerLinkActive]="['active']">
          <a [routerLink]="['/desk', 'albums']">CV Preparation</a>
        </li>
        <li *mocksOnly="true" [routerLinkActive]="['active']">
          <a [routerLink]="['/desk', 'visualize', 'dashboards']">Analytics</a>
        </li>
        <li *mocksOnly="true" [routerLinkActive]="['active']">
          <a [routerLink]="['/desk', 'visualize', 'geospatial']">Geospatial</a>
        </li>
      </ul>
    </div>
  </nav>
  <my-password-change-modal #passwordModal></my-password-change-modal>
  `,
})

export class LayoutDeskHeaderComponent {
  readonly config = config;
  readonly userRole = UserRole;

  @ViewChild('passwordModal') public passwordModal: MyPasswordChangeModalComponent;

  constructor(
    readonly user: UserService,
    private events: EventService,
  ) {
  }

  cleanMocks() {
    this.events.emit(IEvent.Type.CLEAN_MOCKS_REQUEST);
  }

  _userHasRole(user: IUser, role: UserRole) {
    return user.role === role;
  }
}


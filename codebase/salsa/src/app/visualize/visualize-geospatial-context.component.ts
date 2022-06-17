import { Component } from '@angular/core';
import { FormControl } from '@angular/forms';

import config from '../config';

import { DashboardService } from './dashboard.service';

@Component({
  selector: 'app-visualize-geospatial-context',
  template: `
    <div class="group">
      <button type="button"
        class="btn btn-primary btn-alt btn-block"
        [routerLink]="['/desk/visualize/geospatial']"
      >Create New Query
      </button>
    </div>

    <core-project-context></core-project-context>

    <!-- Fix this as soon as queries entities implemented -->
    <div class="menu like-side-asset-list">
      <ul class="nav nav-stacked">
        <li class="has-submenu" [ngClass]="{'open app-spinner-box': isOpen}">
          <a (click)="isOpen = !isOpen">
            <i class="glyphicon glyphicon-globe"></i>
            <span>Queries</span>
          </a>
          <ul class="nav nav-pills submenu with-dropdown">
            <li>
              <app-input [control]="searchControl"
                [iconBefore]="'glyphicon-search'"
                [iconAfter]="'glyphicon-remove'"
                (iconAfterClick)="searchControl.setValue('')"
              ></app-input>
            </li>
            <li><a>No Items</a></li>
          </ul>
        </li>
      </ul>
    </div>
  `,
})
export class VisualizeGeospatialContextComponent {
  readonly config = config;

  readonly searchControl = new FormControl();
  isOpen: boolean = true;

  constructor(
    readonly dashboards: DashboardService,
  ) {}
}


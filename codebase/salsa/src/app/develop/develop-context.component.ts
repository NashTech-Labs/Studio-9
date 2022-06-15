import { Component, OnDestroy, OnInit } from '@angular/core';

import config from '../config';
import { IEvent } from '../core/services/event.service';

import { S9ProjectService } from './s9-project.service';

@Component({
  selector: 'develop-context',
  template: `
    <div class="group">
      <button type="button"
        class="btn btn-primary btn-block"
        [routerLink]="['/desk', 'develop', 'projects', 'create']"
        routerLinkActive #projectsCreateActive="routerLinkActive"
        [ngClass]="{'btn-alt': !projectsCreateActive.isActive}"
      >Create S9 Project</button>
    </div>

    <core-project-context></core-project-context>

    <side-asset-list
      [caption]="'S9 Projects'"
      [baseRoute]="['/desk', 'develop', 'projects']"
      [service]="s9Projects"
      [statusesDefinition]="config.s9Project.status"
      [icon]="'glyphicon glyphicon-picture'"
      [reloadOn]="['${IEvent.Type.UPDATE_S9_PROJECT_LIST}']"
      [actions]="{'packages': 'Packages', 'files': 'Files'}"
    ></side-asset-list>

    <div class="menu">
      <ul class="nav nav-stacked">
        <li [routerLinkActive]="['active']">
          <a [routerLink]="['/desk', 'develop', 'packages']">
            <i class="glyphicon glyphicon-th-large"></i>
            <span>Packages list</span>
          </a>
        </li>
      </ul>
    </div>
  `,
})
export class DevelopContextComponent implements OnInit, OnDestroy {
  readonly config = config;

  constructor(
    protected s9Projects: S9ProjectService,
  ) {
  }

  ngOnInit() {
  }

  ngOnDestroy() {
  }
}

import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';

import * as _ from 'lodash';
import { Subscription } from 'rxjs/Subscription';

import config from '../../config';
import { ProjectContext } from '../../library/project.context';
import { ProjectService } from '../../library/project.service';
import { ReactiveLoader } from '../../utils/reactive-loader';
import { IAsset, IBackendList, TObjectId } from '../interfaces/common.interface';
import { Feature } from '../interfaces/feature-toggle.interface';
import { IProject, IProjectFolder } from '../interfaces/project.interface';
import { EventService, IEvent } from '../services/event.service';

import { ProjectContextModalComponent } from './project-context-modal.component';

@Component({
  selector: 'core-project-context',
  template: `
    <div *featureToggle="'${Feature.LIBRARY_PROJECTS}'" class="project-context-wrapper">
      <label>Project:</label>
      <a class="btn btn-link"
        (click)="onProjectClick()"
      >{{selectedProjectId | apply: projectName: projects}}</a>
      <div class="breadcrumb-folders" core-project-breadcrumbs [selectedFolderId]="selectedFolderId">
        <ul *ngIf="selectedProjectId && (projects | apply: _getFolders: selectedProjectId).length"
          class="breadcrumb wrapped-breadcrumb">
          <li><a (click)="selectFolder(null)"><i class="fa fa-home" title="root"></i></a></li>
          <li *ngIf="(projects | apply: _getFolders: selectedProjectId | apply: _getSubFolders: null).length"
            class="dropdown">
            <a class="dropdown-toggle" data-toggle="dropdown"><span class="chevron right"></span></a>
            <ul class="dropdown-menu to-body">
              <li *ngFor="let folder of projects | apply: _getFolders: selectedProjectId | apply: _getSubFolders: null">
                <a (click)="selectFolder(folder.id)">{{folder.path | apply: folderBasename}}</a>
              </li>
            </ul>
          </li>
          <ng-template [ngIf]="selectedFolderId && projects">
            <ng-template ngFor let-folder let-last="last"
              [ngForOf]="projects | apply: getCurrentPath: selectedProjectId: selectedFolderId">
              <li class="breadcrumb-link" (click)="!last && selectFolder(folder.id)">
                <a [title]="folder.path | apply: folderBasename">
                  {{folder.path | apply: folderBasename}}
                </a>
              </li>
              <li *ngIf="(projects | apply: _getFolders: selectedProjectId | apply: _getSubFolders: folder.id).length"
                class="dropdown">
                <a class="dropdown-toggle" data-toggle="dropdown"><span class="chevron right"></span></a>
                <ul class="dropdown-menu to-body">
                  <li
                    *ngFor="let folder of projects | apply: _getFolders: selectedProjectId | apply: _getSubFolders: folder.id">
                    <a (click)="selectFolder(folder.id)">{{folder.path | apply: folderBasename}}</a>
                  </li>
                </ul>
              </li>
            </ng-template>
          </ng-template>
        </ul>
      </div>
    </div>
    <core-project-context-modal #projectContextModal></core-project-context-modal>
  `,
})
export class ProjectContextComponent implements OnInit, OnDestroy {

  selectedProjectId: TObjectId = null;
  selectedFolderId: TObjectId = null;

  projects: IProject[] = [];
  readonly _loader: ReactiveLoader<IBackendList<IProject>, any>;
  private eventsSubscription: Subscription;
  private projectSubscription: Subscription;
  @ViewChild('projectContextModal') private projectContextModal: ProjectContextModalComponent;

  constructor(
    private projectService: ProjectService,
    private eventService: EventService,
    private projectContext: ProjectContext,
  ) {
    this._loader = new ReactiveLoader(() => this.projectService.list());
    this._loader.subscribe((result: IBackendList<IProject>) => {
      this.projects = result.data;
    });
  }

  readonly _getFolders = function (projects: IProject[], projectId: TObjectId): IProjectFolder[] {
    return projects.filter(_ => _.id === projectId).map(_ => _.folders)[0] || [];
  };

  readonly _getSubFolders = function (folders: IProjectFolder[], folderId: TObjectId): IProjectFolder[] {
    const folder = folderId ? folders.find(folder => folder.id === folderId) : null;

    return _.sortBy(folders.filter(currentFolder => {
      const pathSplitLength = currentFolder.path.split('/').length;
      return folder
        ? currentFolder.path.includes(folder.path) && currentFolder.id !== folder.id && pathSplitLength === folder.path.split('/').length + 1
        : pathSplitLength === 1;
    }), 'path');
  };

  readonly getCurrentPath = function (projects: IProject[], selectedProject: TObjectId, selectedFolderId: TObjectId): IProjectFolder[] {
    const folders: IProjectFolder[] = (projects || [])
      .filter(project => project.id === selectedProject).map(folder => folder.folders)[0] || [];

    const folder = folders.find(folder => folder.id === selectedFolderId);
    if (!folder) {
      return [];
    }
    const paths = folder.path.split('/').reduce((acc, row, i) => {
      if (i === 0) {
        acc.push(row);
      } else {
        acc.push(acc[i - 1] + '/' + row);
      }
      return acc;
    }, []);
    return _.sortBy(folders.filter(folder => {
      return paths.includes(folder.path);
    }), 'path');
  };

  readonly projectName = function (projectId: TObjectId, projects: IProject[]): string {
    const project = projects.find(project => project.id === projectId);
    return project ? project.name : '--none selected--';
  };

  readonly folderBasename = function (path: string): string {
    if (!path) {
      return '';
    }
    const parts = path.split('/');
    return parts[parts.length - 1];
  };

  ngOnInit(): void {
    this.projectSubscription = this.projectContext.value.subscribe(([projectId, folderId]) => {
      this.selectedProjectId = projectId;
      this.selectedFolderId = folderId;
    });

    this._loader.load();
    this.eventsSubscription = this.eventService.subscribe(event => {
      if (event.type === IEvent.Type.UPDATE_PROJECT_LIST) {
        this._loader.load();
      }
      if (event.type === IEvent.Type.UPDATE_PROJECT_ASSETS) {
        const [projectId, folderId] = this.projectContext.get();
        if (event.data === projectId) {
          this.projectContext.set(projectId, folderId);
        }
      }
      // TODO: here come the hacks
      if (this.selectedProjectId) {
        const itemEventMatch = event.type.match(/CREATE_(\w+)/);
        if (itemEventMatch) {
          const assetType = <IAsset.Type> itemEventMatch[1];
          if (config.asset.list.includes(assetType)) {
            const project = this.projects.find(project => project.id === this.selectedProjectId);
            this.projectService.linkAssets(assetType, project, [event.data], this.selectedFolderId);
          }
        }
      }
    });
  }

  ngOnDestroy(): void {
    this.projectSubscription && this.projectSubscription.unsubscribe();
    this.eventsSubscription && this.eventsSubscription.unsubscribe();
  }

  selectFolder(folderId: TObjectId): void {
    this.selectedFolderId = folderId;
    this.projectContext.set(this.selectedProjectId, this.selectedFolderId);
  }

  onProjectClick() {
    this.projectContextModal.open();
  }
}

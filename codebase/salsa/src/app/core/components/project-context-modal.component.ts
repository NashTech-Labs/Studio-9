import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { FormControl, Validators } from '@angular/forms';

import { Observable } from 'rxjs/Observable';
import { Subscription } from 'rxjs/Subscription';

import { ModalComponent } from '../../core-ui/components/modal.component';
import { ModalService } from '../../core-ui/services/modal.service';
import { ProjectContext } from '../../library/project.context';
import { ProjectService } from '../../library/project.service';
import { ActivityObserver } from '../../utils/activity-observer';
import { ReactiveLoader } from '../../utils/reactive-loader';
import { WithProjectSelectionHelpers } from '../core.helpers';
import { IBackendList, TObjectId } from '../interfaces/common.interface';
import { IProject, IProjectFolder } from '../interfaces/project.interface';
import { EventService, IEvent } from '../services/event.service';

enum ProjectModalState {
  SELECT_PROJECT = 0,
  CREATE_PROJECT = 1,
  EDIT_PROJECT = 2,
  CREATE_FOLDER = 3,
}

@Component({
  selector: 'core-project-context-modal',
  template: `
    <app-modal #modal
      [caption]="'Select project'"
      [buttons]="[{ 'class': 'btn-primary', disabled: (projectModalState !== projectModalStates.SELECT_PROJECT) || _activityObserver.isActive, title: 'Ok' }]"
      (buttonClick)="setContext()"
    >
      <app-spinner [visibility]="!projects"></app-spinner>
      <div *ngIf="projects" [ngSwitch]="projectModalState">
        <div *ngSwitchCase="projectModalStates.CREATE_PROJECT" class="row">
          <div class="col-md-8">
            <app-input
              label="New Project Name"
              [control]="projectName"></app-input>
          </div>
          <div class="col-md-4">
            <div class="btn-group pull-right" role="group">
              <button
                type="button"
                class="btn btn-default"
                (click)="setProjectModalState(projectModalStates.SELECT_PROJECT)"
              ><i class="glyphicon glyphicon-backward"></i></button>
              <button
                type="button"
                class="btn btn-default"
                (click)="submitCreation()"
                [disabled]="_activityObserver.active | async"
              ><i class="glyphicon glyphicon-ok"></i></button>
            </div>
          </div>
        </div>

        <div *ngSwitchCase="projectModalStates.EDIT_PROJECT" class="row">
          <div class="col-md-8">
            <app-input
              label="Edit Project Name"
              [control]="projectName"
            ></app-input>
          </div>
          <div class="col-md-4">
            <div class="btn-group pull-right" role="group">
              <button
                type="button"
                class="btn btn-default"
                title="Cancel"
                (click)="setProjectModalState(projectModalStates.SELECT_PROJECT)"
              ><i class="glyphicon glyphicon-backward"></i></button>
              <button
                type="button"
                class="btn btn-default"
                title="Create project"
                (click)="submitEditing()"
                [disabled]="_activityObserver.active | async"
              ><i class="glyphicon glyphicon-ok"></i></button>
            </div>
          </div>
        </div>

        <div *ngIf="projectModalState === projectModalStates.CREATE_FOLDER" class="row">
          <div class="col-md-8">
            <app-select
              label="Parent Folder"
              [(value)]="selectedFolderId"
              [options]="projects | apply: prepareFolderOptions: selectedProjectId"
              [allowNull]="true"
              nullSelectedText="[root]"
            ></app-select>
          </div>
          <div class="col-md-8">
            <app-input
              label="New Folder Name"
              [control]="folderName"
            ></app-input>
          </div>
          <div class="col-md-4">
            <div class="btn-group pull-right" role="group">
              <button
                type="button"
                class="btn btn-default"
                title="Cancel"
                (click)="setProjectModalState(projectModalStates.SELECT_PROJECT)"
              ><i class="glyphicon glyphicon-backward"></i></button>
              <button
                type="button"
                class="btn btn-default"
                title="Create folder"
                (click)="createFolder()"
              ><i class="glyphicon glyphicon-ok"></i></button>
            </div>
          </div>
        </div>

        <ng-template [ngSwitchCase]="projectModalStates.SELECT_PROJECT">
          <div class="row">
            <div class="col-md-8">
              <app-select
                label="Project"
                [value]="selectedProjectId"
                (valueChange)="selectProject($event)"
                [options]="projects | apply: prepareProjectOptions"
                [allowNull]="true"
              ></app-select>
            </div>
            <div class="col-md-4">
              <div class="btn-group pull-right" role="group">
                <button
                  type="button"
                  class="btn btn-default"
                  title="Create new project"
                  (click)="setProjectModalState(projectModalStates.CREATE_PROJECT)"
                ><i class="glyphicon glyphicon-plus"></i></button>
                <button
                  type="button"
                  class="btn btn-default"
                  title="Rename project"
                  (click)="setProjectModalState(projectModalStates.EDIT_PROJECT)"
                  [disabled]="!selectedProjectId || (_activityObserver.active | async)"
                ><i class="glyphicon glyphicon-pencil"></i></button>
                <button
                  type="button"
                  class="btn btn-danger"
                  title="Delete project"
                  (click)="confirm('Are you sure want to delete this project?', deleteProject)"
                  [disabled]="!selectedProjectId || (_activityObserver.active | async)"
                ><i class="glyphicon glyphicon-remove"></i></button>
              </div>
            </div>
          </div>

          <div *ngIf="selectedProjectId" class="row">
            <div class="col-md-8">
              <app-select
                label="Folder"
                [(value)]="selectedFolderId"
                [options]="projects | apply: prepareFolderOptions: selectedProjectId"
                [allowNull]="true"
                nullSelectedText="[root]"
              ></app-select>
            </div>
            <div class="col-md-4">
              <div class="btn-group pull-right" role="group">
                <button
                  type="button"
                  class="btn btn-default"
                  title="Add folder"
                  (click)="startFolderCreation()"
                  [disabled]="_activityObserver.active | async"
                ><i class="glyphicon glyphicon-plus"></i></button>
                <button
                  type="button"
                  class="btn btn-danger"
                  title="Delete folder"
                  (click)="confirm('Are you sure want to delete this folder?', deleteFolder)"
                  [disabled]="!selectedFolderId || (_activityObserver.active | async)"
                ><i class="glyphicon glyphicon-remove"></i></button>
              </div>
            </div>
          </div>
        </ng-template>
      </div>
    </app-modal>`,
})

export class ProjectContextModalComponent extends WithProjectSelectionHelpers implements OnInit, OnDestroy {
  readonly projectModalStates = ProjectModalState;
  readonly _loader: ReactiveLoader<IBackendList<IProject>, any>;
  readonly _activityObserver = new ActivityObserver();
  projectName = new FormControl(null, Validators.required);
  folderName = new FormControl(null, Validators.required);
  projectModalState = ProjectModalState.SELECT_PROJECT;
  projects: IProject[] = [];
  selectedProjectId: TObjectId = null;
  selectedFolderId: TObjectId = null;
  private lastSelectedProjectId: TObjectId = null;
  private lastSelectedFolderId: TObjectId = null;
  private eventsSubscription: Subscription;
  private projectSubscription: Subscription;
  @ViewChild('modal') private modal: ModalComponent;

  constructor(
    private projectService: ProjectService,
    private eventService: EventService,
    private modalService: ModalService,
    private projectContext: ProjectContext,
  ) {
    super();
    this._loader = new ReactiveLoader(() => this.projectService.list());
    this._loader.subscribe((result: IBackendList<IProject>) => {
      this.projects = result.data;
    });
  }

  setProjectModalState(modalState: ProjectModalState) {
    this.projectModalState = modalState;
    if (modalState === ProjectModalState.EDIT_PROJECT) {
      const project = this.getProject();
      this.projectName.setValue(project ? project.name : '');
    } else {
      this.projectName.setValue('');
      this.projectName.markAsPristine();
    }
  }

  open(): Observable<[TObjectId, TObjectId]> {
    this.setProjectModalState(ProjectModalState.SELECT_PROJECT);
    this.selectedProjectId = this.lastSelectedProjectId;
    this.selectedFolderId = this.lastSelectedFolderId;
    return this.modal.show().map((): [TObjectId, TObjectId] => this.projectContext.get());
  }

  selectProject(projectId: TObjectId): void {
    this.selectedProjectId = projectId;
    this.selectedFolderId = null;
  }

  ngOnInit(): void {
    this.projectSubscription = this.projectContext.value.subscribe(([projectId, folderId]) => {
      this.lastSelectedProjectId = projectId;
      this.lastSelectedFolderId = folderId;
    });

    this._loader.load();
    this.eventsSubscription = this.eventService.subscribe(event => {
      if (event.type === IEvent.Type.UPDATE_PROJECT_LIST) {
        this.setProjectModalState(ProjectModalState.SELECT_PROJECT);
        this._loader.load();
      }
      if (event.type === IEvent.Type.UPDATE_PROJECT_ASSETS) {
        const [projectId, folderId] = this.projectContext.get();
        if (event.data === projectId) {
          this.projectContext.set(projectId, folderId);
        }
      }
    });
  }

  ngOnDestroy(): void {
    this.projectSubscription && this.projectSubscription.unsubscribe();
    this.eventsSubscription && this.eventsSubscription.unsubscribe();
  }

  submitCreation() {
    this.projectService.create({name: this.projectName.value}).subscribe((project: IProject) => {
      this.selectedProjectId = project.id;
    });
  }

  submitEditing() {
    this._activityObserver.observe(
      this.projectService.update(this.selectedProjectId, {name: this.projectName.value}),
    );
  }

  setContext() {
    this.projectContext.set(this.selectedProjectId, this.selectedFolderId);
    this.modal.hide();
  }

  createFolder() {
    const parentFolder: (IProjectFolder | null) = this.projects
      .filter(_ => _.id === this.selectedProjectId)
      .reduce((acc: IProjectFolder[], project) => acc.concat(project.folders), [])
      .filter(_ => _.id === this.selectedFolderId)[0];

    const folderName = this.folderName.value;
    const folderPath = parentFolder
      ? `${parentFolder.path}/${folderName}`
      : folderName;

    this._activityObserver.observe(
      this.projectService.createFolder(this.selectedProjectId, folderPath),
    ).subscribe((folder) => {
      this.selectedFolderId = folder.id;
      this.setProjectModalState(ProjectModalState.SELECT_PROJECT);
    });
  }

  deleteFolder = () => {
    this._activityObserver.observe(
      this.projectService.deleteFolder(this.selectedProjectId, this.selectedFolderId),
    ).subscribe(() => {
      this.selectedFolderId = null;
    });
  };

  deleteProject = () => {
    const project = this.getProject();
    if (!project) {
      return;
    }
    this._activityObserver.observe(this.projectService.delete(project)).subscribe(() => {
      this.selectedProjectId = null;
    });
  };

  startFolderCreation() {
    this.setProjectModalState(ProjectModalState.CREATE_FOLDER);
    this.folderName.setValue('');
    this.folderName.markAsPristine();
  }

  confirm(message: string, action: () => void) {
    this.modalService.confirm(message).subscribe(confirmed => confirmed && action());
  }

  private getProject() {
    return this.projects.find(project => project.id === this.selectedProjectId);
  }
}

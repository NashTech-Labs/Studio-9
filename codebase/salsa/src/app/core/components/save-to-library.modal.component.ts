import { Component, Input, OnDestroy, OnInit, Optional, ViewChild } from '@angular/core';
import { FormControl, Validators } from '@angular/forms';

import { Observable } from 'rxjs/Observable';
import { Subscription } from 'rxjs/Subscription';

import config from '../../config';
import { ModalComponent } from '../../core-ui/components/modal.component';
import { ProjectContext } from '../../library/project.context';
import { ProjectService } from '../../library/project.service';
import { ActivityObserver } from '../../utils/activity-observer';
import { AppFormGroup } from '../../utils/forms';
import { ReactiveLoader } from '../../utils/reactive-loader';
import { WithProjectSelectionHelpers } from '../core.helpers';
import { IAsset, IAssetService, IBackendList } from '../interfaces/common.interface';
import { Feature } from '../interfaces/feature-toggle.interface';
import { IProject } from '../interfaces/project.interface';
import { EventService, IEvent } from '../services/event.service';

@Component({
  selector: 'save-to-library-modal',
  template: `
    <app-modal #modal
      caption="Save {{ config.asset.labels[service.assetType] }} to Library"
      [buttons]="[{ 'class': 'btn-primary', disabled: form.invalid, title: 'Save' }]"
      (buttonClick)="saveClick()"
    >
      <div class="form-group">
        <app-input [label]="'Name'" [control]="form.controls['name']"></app-input>
      </div>
      <ng-container *featureToggle="'${Feature.LIBRARY_PROJECTS}'">
        <div class="form-group">
          <app-select
            label="Project"
            [control]="form.controls.projectId"
            [options]="projects | apply: prepareProjectOptions"
            [allowNull]="true"
            nullSelectedText="-- No project --"
          ></app-select>
        </div>
        <div class="form-group" *ngIf="!!form.value.projectId">
          <app-select
            label="Folder"
            [control]="form.controls.folderId"
            [options]="projects | apply: prepareFolderOptions: form.value.projectId"
            [allowNull]="true"
            nullSelectedText="[root]"
          ></app-select>
        </div>
      </ng-container>
    </app-modal>
  `,
})
export class SaveToLibraryModalComponent<T extends IAsset> extends WithProjectSelectionHelpers implements OnInit, OnDestroy {
  @Input() service: IAssetService<T, any>;
  readonly config = config;
  form = new AppFormGroup({
    projectId: new FormControl(null),
    folderId: new FormControl(null),
    name: new FormControl(null, Validators.required),
  });
  public projects: IProject[] = [];
  readonly _savingObserver = new ActivityObserver();

  @ViewChild('modal') private modal: ModalComponent;
  private asset: T;
  private eventsSubscription: Subscription;
  private _projectsLoader: ReactiveLoader<IBackendList<IProject>, any>;

  constructor(
    private projectService: ProjectService,
    private eventService: EventService,
    @Optional() private projectContext: ProjectContext,
  ) {
    super();
    this._projectsLoader = new ReactiveLoader(() => this.projectService.list());
    this._projectsLoader.subscribe((result: IBackendList<IProject>) => {
      this.projects = result.data;
    });
  }

  ngOnInit() {
    this._projectsLoader.load();
    this.eventsSubscription = this.eventService.subscribe(event => {
      if (event.type === IEvent.Type.UPDATE_PROJECT_LIST) {
        this._projectsLoader.load();
      }
    });
  }

  open(asset: T) {
    const [currentProject, currentFolder] = this.projectContext
      ? this.projectContext.get()
      : [null, null];

    this.form.reset({
      projectId: currentProject,
      folderId: currentFolder,
      name: asset.name,
    });

    this.asset = asset;
    this.modal.show();
  }

  saveClick() {
    const project: IProject = this.projects.find(project => project.id === this.form.value.projectId);
    const observable: Observable<T> = this.service.save(this.asset.id, this.form.value).flatMap(asset => {
      return project
        ? this.projectService.linkAssets(this.service.assetType, project, [asset], this.form.value.folderId)
          .mapTo(asset)
        : Observable.of(asset);
    });

    this._savingObserver.observe(observable).subscribe(() => {
      this.modal.hide();
    });
  }

  ngOnDestroy() {
    if (this.eventsSubscription) {
      this.eventsSubscription.unsubscribe();
    }
    this._projectsLoader.complete();
  }
}

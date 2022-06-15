import { Component, EventEmitter, Output, ViewChild } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';

import { Observable } from 'rxjs/Observable';

import config from '../../config';
import { ModalComponent } from '../../core-ui/components/modal.component';
// TODO: remove circular modules dependency
import { ProjectService } from '../../library/project.service';
import { ActivityObserver } from '../../utils/activity-observer';
import { ReactiveLoader } from '../../utils/reactive-loader';
import { WithProjectSelectionHelpers } from '../core.helpers';
import { IAsset, IBackendList, TObjectId } from '../interfaces/common.interface';
import { IProject } from '../interfaces/project.interface';

@Component({
  selector: 'project-link-modal',
  template: `
    <app-modal #modal
      [captionPrefix]="'Add ' + config.asset.labelsPlural[assetType]+' to project '"
      [caption]="assetNames"
      [buttons]="[{
        'class': 'btn-primary',
        'title': 'Add',
        'disabled': projectIdControl.invalid || (savingObserver.active | async)
      }]"
      (buttonClick)="submit()"
    >
      <form>
        <div class="form-group">
          <app-select [label]="'Project'"
            [control]="projectIdControl"
            [options]="items?.data | apply: prepareProjectOptions"
          ></app-select>
        </div>
        <div class="form-group" *ngIf="projectIdControl.value !== '-1'">
          <app-select [label]="'Folder'"
            [control]="folderIdControl"
            [options]="items?.data | apply: prepareFolderOptions: projectIdControl.value"
            [allowNull]="true"
            nullSelectedText="[root]"
          ></app-select>
        </div>
        <div class="form-group" *ngIf="projectIdControl.value === '-1'">
          <app-input [label]="'Project Name'" [control]="projectForm['controls'].name"></app-input>
        </div>
      </form>
    </app-modal>
  `,
})


export class ProjectLinkModalComponent extends WithProjectSelectionHelpers {
  @Output() onComplete = new EventEmitter<boolean>();
  readonly config = config;
  projectIdControl: FormControl;
  folderIdControl: FormControl;
  projectForm: FormGroup;
  assets: IAsset[];
  assetType: IAsset.Type;
  assetNames: string;
  items: IBackendList<IProject>;
  readonly itemsLoader: ReactiveLoader<IBackendList<IProject>, any>;
  readonly savingObserver = new ActivityObserver();

  @ViewChild('modal') private modal: ModalComponent;

  constructor(
    private projects: ProjectService,
  ) {
    super();
    this.projectIdControl = new FormControl('-1', Validators.required);
    this.folderIdControl = new FormControl(null);
    this.projectForm = new FormGroup({
      name: new FormControl('', Validators.required),
    });

    this.itemsLoader = new ReactiveLoader(() => this._loadList());
    this.itemsLoader.subscribe((_: IBackendList<IProject>) => {
      this.items = _;
      this.items.data.unshift({
        id: '-1',
        name: 'Create New Project',
        ownerId: null,
        created: new Date().toISOString(),
        updated: new Date().toISOString(),
        folders: [],
      });
    });
  }

  open(assetType: IAsset.Type, assets: IAsset[]): Observable<void> {
    this.assetType = assetType;
    this.assets = [];
    this.assetNames = '';
    let assetNames = [];
    this.assets = assets;
    this.assets.forEach((asset: IAsset) => assetNames.push(asset.name));
    this.assetNames = assetNames.join(', ');

    this.itemsLoader.load();

    return this.modal.show();
  }

  submit() {
    const projectObservable: Observable<IProject> = (this.projectIdControl.value === '-1')
      ? this.projects.create(this.projectForm.value)
      : Observable.of(this.items.data.find((_) => _.id === this.projectIdControl.value));

    const observable = projectObservable.flatMap((project: IProject) => this.addToProject(
      project,
      project.folders.map(_ => _.id).filter(_ => _ === this.folderIdControl.value)[0],
    ));

    this.savingObserver.observe(observable).subscribe(() => {
      this.modal.hide();
      this.onComplete.emit(true);
    });
  }

  private addToProject(project?: IProject, folderId?: TObjectId): Observable<boolean> {
    if (!project) {
      return Observable.of(false);
    }
    const observable = this.projects.linkAssets<IAsset>(this.assetType, project, this.assets, folderId);

    return observable.mapTo(true);
  }

  private _loadList(): Observable<IBackendList<IProject>> {
    return this.projects.list();
  }
}

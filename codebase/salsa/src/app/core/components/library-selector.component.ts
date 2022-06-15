import {
  Component,
  EventEmitter,
  Inject,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
  Optional,
  Output,
  SimpleChanges,
  ViewChild,
} from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';

import * as _ from 'lodash';
import 'rxjs/add/observable/of';
import { Observable } from 'rxjs/Observable';
import { forkJoin } from 'rxjs/observable/forkJoin';
import { Subscription } from 'rxjs/Subscription';

import config from '../../config';
import { ModalComponent } from '../../core-ui/components/modal.component';
import { LIBRARY_SECTIONS, LibrarySectionDefinition } from '../../library/library.interface';
import { ProjectContext } from '../../library/project.context';
import { ProjectService } from '../../library/project.service';
import { trainConfig } from '../../train/train.config';
import { ReactiveLoader } from '../../utils/reactive-loader';
import { WithProjectSelectionHelpers } from '../core.helpers';
import { IAsset, IAssetListRequest, IBackendList, TObjectId } from '../interfaces/common.interface';
import { IProject } from '../interfaces/project.interface';
import { EventService, IEvent } from '../services/event.service';
import { FeatureToggleService } from '../services/feature-toggle.service';

export interface LibrarySelectorValue {
  id: TObjectId;
  entity: IAsset.Type;
  object?: IAsset;
}

export interface LibrarySelectorCustomLoader {
  name: string;
  entity: IAsset.Type;
  list: (request: IAssetListRequest) => Observable<IBackendList<IAsset>>;
}

type IItemListPartial = IBackendList<IAsset>;

@Component({
  selector: 'library-selector',
  template: `
    <app-input *ngIf="!hidden"
      prevent-default-click
      [label]="inputLabel"
      [helpText]="helpText"
      [iconAfter]="(value?.id && allowReset && !disabled) ? 'glyphicon-remove' : 'glyphicon-list'"
      [disabled]="disabled"
      [value]="nameLoader.loaded ? name : 'Loading...'"
      (iconAfterClick)="onIconAfterClick($event)"
      (click)="show()"
      (focus)="onFocus()"
      (keyDown)="preventType($event)"
    ></app-input>

    <app-modal #modal [caption]="caption" [sizeClass]="config.modal.size.LARGE">
      <div class="row" *ngIf="projects.length">
        <div class="col-sm-6">
          <app-select [label]="'Project'"
            [value]="selectedProjectId"
            (valueChange)="selectProject($event)"
            [options]="projects | apply: prepareProjectOptions"
            [allowNull]="true"
            nullSelectedText="[all assets]"
          ></app-select>
        </div>
        <div class="col-sm-6" *ngIf="selectedProjectId">
          <app-select [label]="'Folder'"
            [value]="selectedFolderId"
            (valueChange)="selectFolder($event)"
            [options]="projects | apply: prepareFolderOptions: selectedProjectId"
            [allowNull]="true"
            nullSelectedText="[root]"
          ></app-select>
        </div>
      </div>
      <div class="row">
        <nav class="col-sm-6 tabpanel">
          <ul class="nav nav-tabs" role="tablist">
            <li *ngFor="let item of available" class="nav-item" [ngClass]="{'active': entity === item}">
              <a class="nav-link link"
                (click)="setEntity(item)"
                [ngClass]="entity === item ? 'link-colorless font-weight-medium' : 'text-muted'">
                {{config.asset.labelsPlural[item]}}</a>
            </li>
          </ul>
        </nav>
        <div class="col-sm-6">
          <div class="form-inline pull-right">
            <app-input [control]="searchControl"
              [iconBefore]="'glyphicon-search'"
              [iconAfter]="'glyphicon-remove'"
              (iconAfterClick)="searchControl.setValue('')"></app-input>
          </div>
        </div>
      </div>

      <form [formGroup]="form">
        <div style="position: relative; min-height: 300px;" class="tab-pane">
          <app-spinner [visibility]="itemsDataLoading | async"></app-spinner>
          <div *ngIf="items && !(itemsDataLoading | async)" class="row">
            <div class="col-md-12">
              <div class="p0 form-control brand-control">
                <div class="row" *ngVar="customLoaders | apply: _filterLoaders: entity; let loaders = value">
                  <div class="col-xs-6" *ngIf="loaders.length">
                    <div class="btn-group btn-toggle">
                      <button class="btn btn-xs btn-default"
                        (click)="setCustomLoader(null)"
                        [ngClass]="{'btn-success active': !_customLoader}"
                      >Library</button>
                      <button class="btn btn-xs"
                        *ngFor="let loader of loaders"
                        (click)="setCustomLoader(loader)"
                        [ngClass]="{'btn-success active': _customLoader === loader}"
                      >{{loader.name}}</button>
                    </div>
                  </div>
                  <div class="col-xs-6"
                    [ngClass]="{'col-xs-offset-6': !loaders.length}"
                  >
                    <div class="pull-right">
                      {{items.count || 0 | pluralize: ({
                        other: '{} '+config.asset.labelsPlural[entity],
                        '0': 'No '+config.asset.labelsPlural[entity],
                        '1': '1 '+config.asset.labels[entity]
                      })}}
                    </div>
                    <div class="pull-right">
                      <app-pagination class="pull-right"
                        [page]="form.controls['page']"
                        [pageSize]="form.controls['page_size']"
                        [currentPageSize]="items.data.length"
                        [rowsCount]="items.count">
                      </app-pagination>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
          <!-- START items LISTS -->
          <div *ngIf="items && !(itemsDataLoading | async)" class="table-scroll" [adaptiveHeight]="{minHeight: 450, pageMargin: 150}">
            <table class="table table-bordered">
              <thead>
              <tr style="white-space: nowrap;">
                <th
                  [grid-sort]="{ alias: 'name' }"
                  [grid-sort-control]="form.controls['order']"
                  style="width: 50%"
                >Name</th>
                <th *ngFor="let item of entity | apply: _getColumns"
                  [grid-sort]="item"
                  [grid-sort-control]="form.controls['order']"
                  [attr.style]="item.style | safeStyle"
                >
                  {{item.name}}
                </th>
                <th
                  [grid-sort]="{ alias: 'updated', reverse: true }"
                  [grid-sort-control]="form.controls['order']"
                  style="width: 20%"
                >Last modified</th>
              </tr>
              </thead>
              <tbody>
              <tr *ngFor="let item of items.data">
                <td *ngVar="item | apply: _checkValidity: entity: itemFilter; let valid = value"
                >
                  <div *ngIf="valid">
                    <a class="link" title="{{item.name}}"
                      (click)="setValue(item)">{{item.name}}</a></div>
                  <div *ngIf="!valid">
                    <span class="ellipsis" title="{{item.name}}">{{item.name}}</span></div>
                </td>
                <td *ngFor="let column of entity | apply: _getColumns">
                  {{item | apply: column.get: asyncData[entity]}}
                </td>
                <td>
                  <div class="text-muted">{{item.updated | date:'M/d/y'}}</div>
                </td>
              </tr>
              </tbody>
            </table>
          </div>
        </div>
        <!-- END TABLE | FLOW | MODEL LISTS-->
      </form>
    </app-modal>`,
})
export class LibrarySelectorComponent extends WithProjectSelectionHelpers implements OnChanges, OnInit, OnDestroy {
  readonly config = config;
  readonly trainConfig = trainConfig;
  @Input() value: LibrarySelectorValue;
  @Input() inputLabel: string = null;
  @Input() caption: string = 'Select Dataset';
  @Input() helpText: string;
  @Input() available: IAsset.Type[] = [IAsset.Type.TABLE];
  @Input() disabled: boolean = false;
  @Input() hidden: boolean = false;
  @Input() allowReset: boolean = false;
  @Input() itemFilter: (_: LibrarySelectorValue) => boolean;
  @Input() customLoaders: LibrarySelectorCustomLoader[] = [];
  @Output() valueChange = new EventEmitter<LibrarySelectorValue>();
  @Output() valueObjectLoaded = new EventEmitter<IAsset>();

  name: string;
  entity: IAsset.Type = IAsset.Type.TABLE;
  form: FormGroup;
  searchControl: FormControl;
  items: IItemListPartial;
  asyncData: {[assetType: string]: any} = {};

  selectedProjectId: TObjectId = null;
  selectedFolderId: TObjectId = null;

  projects: IProject[] = [];
  readonly _projectsLoader: ReactiveLoader<IBackendList<IProject>, any>;
  readonly itemsLoader: ReactiveLoader<IItemListPartial, any>;
  readonly nameLoader: ReactiveLoader<string, any>;
  readonly asyncDataLoader: ReactiveLoader<{[assetType: string]: any}, any>;
  readonly itemsDataLoading: Observable<boolean>;
  readonly sections: {[assetType: string]: LibrarySectionDefinition<IAsset>} = {};

  protected _customLoader: LibrarySelectorCustomLoader | null;

  private eventsSubscription: Subscription;

  @ViewChild('modal') private modal: ModalComponent;

  constructor(
    private featureService: FeatureToggleService,
    private projectService: ProjectService,
    private eventService: EventService,
    @Inject(LIBRARY_SECTIONS) sections: LibrarySectionDefinition<IAsset>[],
    @Optional() private projectContext: ProjectContext,
  ) {
    super();
    this.sections = _.keyBy(sections, _ => _.assetType);

    this.searchControl = new FormControl('');
    this.form = new FormGroup({
      scope: new FormControl(config.library.scope.values.PERSONAL),
      search: new FormControl(''),
      order: new FormControl('-updated'),
      page: new FormControl(1),
      page_size: new FormControl(100),
    });
    this.searchControl.valueChanges.debounceTime(500).subscribe(search => {
      this.form.patchValue({
        search,
        page: 1,
      });
    });
    this.form.valueChanges.subscribe(() => {
      this.itemsLoader.load();
    });

    // items list loader
    this.itemsLoader = new ReactiveLoader(() => this._loadList());
    this.itemsLoader.subscribe(_ => {
      this.items = _;
    });

    // selected item name loader
    this.nameLoader = new ReactiveLoader(() => this._loadSelectedName());
    this.nameLoader.subscribe(_ => {
      this.name = _;
    });

    // projects
    this._projectsLoader = new ReactiveLoader(() => this.projectService.list());
    this._projectsLoader.subscribe((_: IBackendList<IProject>) => {
      this.projects = _.data;
    });

    // async data loader
    this.asyncDataLoader = new ReactiveLoader(() => this._loadAsyncData());
    this.asyncDataLoader.subscribe(_ => {
      this.asyncData = {..._};
    });

    this.itemsDataLoading = Observable.combineLatest(
      this.itemsLoader.active,
      this.asyncDataLoader.active,
      (a, b) => a || b,
    );
  }

  ngOnInit() {
    this._projectsLoader.load();

    this.eventsSubscription = this.eventService.subscribe(event => {
      if (event.type === IEvent.Type.UPDATE_PROJECT_LIST) {
        this._projectsLoader.load();
      }
      if (event.type === IEvent.Type.UPDATE_PROJECT_ASSETS) {
        const [projectId] = this.projectContext.get();
        if (event.data === projectId) {
          this.itemsLoader.load();
        }
      }
    });
  }

  ngOnDestroy(): void {
    this.eventsSubscription && this.eventsSubscription.unsubscribe();
    this.asyncDataLoader.complete();
  }

  ngOnChanges(changes: SimpleChanges) {
    if ('available' in changes) {
      this.available = this.available.filter(asset => {
        const section = this.sections[asset];
        return !!section && this.featureService.areFeaturesEnabled(section.features);
      });
      if (!this.available.includes(this.entity)) {
        this.entity = this.available[0];
        this._customLoader = null;
        this.itemsLoader.load();
        this.nameLoader.load();
        if (this.value && this.value.entity !== this.entity) {
          window.setTimeout(() => this.setValue(null), 0);
        }
      }
      this.asyncDataLoader.load();
    } else if ('value' in changes) {
      this.nameLoader.load();
    } else if ('itemFilter' in changes) {
      if (this.itemFilter && this.value && this.value.object && !this._checkValidity(
        this.value.object,
        this.value.entity,
        this.itemFilter,
      )) {
        window.setTimeout(() => this.setValue(null), 0);
      }
    }
  }

  setCustomLoader(loader: LibrarySelectorCustomLoader | null) {
    this._customLoader = loader;
    this.itemsLoader.load();
  }

  setEntity(item: IAsset.Type): void {
    this.entity = item;
    this._customLoader = null;
    this.itemsLoader.load();
  }

  show() {
    if (!this.disabled) {
      if (this.projectContext) {
        const [projectId, folderId] = this.projectContext.get();
        this.selectedProjectId = projectId;
        this.selectedFolderId = folderId;
      }
      this.modal.show();
      this.itemsLoader.load();
    }
  }

  onIconAfterClick(event) {
    if (this.allowReset && !this.disabled) {
      this.setValue(null);
    }
    event.stopPropagation && event.stopPropagation();
  }

  onFocus() {
    this.show();
  }

  preventType(event: any) {
    this.show();
    event.preventDefault();
  }

  selectProject(projectId: TObjectId): void {
    this.selectedProjectId = projectId;
    this.selectedFolderId = null;
    this.itemsLoader.load();
  }

  selectFolder(folderId: TObjectId): void {
    this.selectedFolderId = folderId;
    this.itemsLoader.load();
  }

  protected setValue(item: IAsset): void {
    this.value = item ? {
      id: <TObjectId> item.id,
      entity: this.entity,
      object: item,
    } : null;
    this.valueChange.emit(this.value);
    this.nameLoader.load();
    this.modal.hide();
  }

  protected _checkValidity = (
    item: IAsset,
    entityType: IAsset.Type,
    checkCallback: (_: LibrarySelectorValue) => boolean,
  ) => {
    const section = this._getSection(entityType);

    return (!section.completeStatus || item.status === section.completeStatus) && (!checkCallback || checkCallback({
      id: item.id,
      entity: entityType,
      object: item,
    }));
  };

  protected _getColumns = (entityType: IAsset.Type = this.entity) => {
    const section = this.sections[entityType];

    return section ? section.selectorColumns : [];
  };

  protected _filterLoaders = function(loaders: LibrarySelectorCustomLoader[], entity: IAsset.Type) {
    return loaders.filter(_ => _.entity === entity);
  };

  private _loadList(): Observable<IItemListPartial> {
    let listRequest: IAssetListRequest = {
      ...this.form.value,
      projectId: this.selectedProjectId,
      folderId: this.selectedFolderId,
    };

    if (this._customLoader && this._customLoader.entity === this.entity) {
      return this._customLoader.list(listRequest);
    }

    return this._getSection().service.list(listRequest);
  }

  private _loadSelectedName(): Observable<string> {
    if (this.value && this.value.object) {
      return Observable.of(this.value.object.name);
    } else if (this.value && this.value.id) {
      const observable = this._getSection(this.value.entity).service.get(this.value.id);
      const subscription = observable.subscribe(object => {
        this.value.object = object;
        this.valueObjectLoaded.emit(object);
        subscription.unsubscribe();
      });
      return observable.map(_ => _.name);
    } else {
      return Observable.of('-Select-');
    }
  }

  private _loadAsyncData(): Observable<any> {
    const observables = Object.values(this.sections)
      .filter(_ => this.available.includes(_.assetType) && _.loadAsyncData)
      .map(section => {
        return section.loadAsyncData().map(data => {
          return {[section.assetType]: data};
        });
      });

    if (!observables.length) {
      return Observable.of({});
    }

    return forkJoin(observables)
      .map(data => {
        return data.reduce((acc, item) => Object.assign(acc, item), {});
      });
  }

  private _getSection(entityType: IAsset.Type = this.entity) {
    const section = this.sections[entityType];
    if (!section) {
      throw new Error('Unsupported entity type: ' + entityType);
    }
    return section;
  }
}

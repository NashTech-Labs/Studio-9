import { Component, Input, OnChanges, OnDestroy, OnInit, Optional, SimpleChanges, ViewChild } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';

import 'rxjs/add/operator/mapTo';
import { Observable } from 'rxjs/Observable';
import { Subscription } from 'rxjs/Subscription';

import config from '../config';
import { IColumnSortConfig } from '../core-ui/directives/grid-sort.directive';
import { AssetOperationsComponent } from '../core/components/asset-operations.component';
import { CloneModalComponent } from '../core/components/clone-modal.component';
import { IAsset, IAssetListRequest, IBackendList, IListRequest, TObjectId } from '../core/interfaces/common.interface';
import { IProject } from '../core/interfaces/project.interface';
import { IUser, IUsersMap } from '../core/interfaces/user.interface';
import { EventService, IEvent } from '../core/services/event.service';
import { SharedResourceService } from '../core/services/shared-resource.service';
import { UserService } from '../core/services/user.service';
import { ReactiveLoader } from '../utils/reactive-loader';

import { LibrarySectionDefinition } from './library.interface';
import { ProjectContext } from './project.context';
import { ProjectService } from './project.service';

// TODO: synchronize dropdown ops with asset-operations.component (acl and etc.)

@Component({
  selector: 'library-item-list',
  template: `
    <asset-operations #operations
      [type]="options.assetType"
      [(selectedItems)]="selectedItems"
      [project]="project">
      <form class="form" [formGroup]="form">
        <app-input [control]="searchControl"
          [iconBefore]="'glyphicon-search'"
          [iconAfter]="'glyphicon-remove'"
          (iconAfterClick)="searchControl.setValue('')"></app-input>
      </form>
    </asset-operations>
    <div class="row">
      <div class="col-md-12">
        <div class="p0 form-control brand-control">
          <div class="row">
            <div class="col-xs-offset-7 col-xs-5" *ngIf="itemsList">
              <div class="pull-right">
                {{itemsList.count || 0 | pluralize: ({
                  other: '{} '+config.asset.labelsPlural[options.assetType],
                  '0': 'No '+config.asset.labelsPlural[options.assetType],
                  '1': '1 '+config.asset.labels[options.assetType]
                })}}
              </div>
              <div class="pull-right">
                <app-pagination [page]="form.controls['page']"
                  [pageSize]="form.controls['page_size']"
                  [currentPageSize]="itemsList.data.length"
                  [rowsCount]="itemsList.count">
                </app-pagination>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
    <div class="table-scroll" [adaptiveHeight]="{minHeight: 450}">
      <app-spinner [visibility]="_itemsDataLoading | async"></app-spinner>
      <table *ngIf="itemsList && !(_itemsDataLoading | async)" class="table dataTable table-hover">
        <thead>
        <tr style="white-space: nowrap">
          <th style="width: 1%"></th>
          <th *ngFor="let item of baseColumns"
            [grid-sort]="item"
            [grid-sort-control]="form.controls['order']"
            [attr.style]="item.style | safeStyle"
          >
            {{item.name}}
          </th>
          <th *ngFor="let item of options.columns"
            [grid-sort]="item"
            [grid-sort-control]="form.controls['order']"
            [attr.style]="item.style | safeStyle"
          >
            {{item.name}}
          </th>
          <th class="text-right" style="width: 5%">Actions</th>
        </tr>
        </thead>
        <tbody>
        <tr *ngFor="let item of itemsList.data">
          <td>
            <app-check
              (checkedChange)="changeSelection(item, $event)"
              [checked]="isSelected(item) > -1"
              [name]="'selection' + item.id" [type]="'checkbox'"
              [value]="item"></app-check>
          </td>
          <td>
            <a [routerLink]="item | apply: _prepareLink: options.baseRoute">{{item.name}}</a>
          </td>
          <td class="text-center">
            <i class="dot"
              [asset-status]="item.status"
              [asset-status-styles]="options.statusesDefinition.styles"
              tooltip
              data-toggle="tooltip"
              [attr.data-original-title]="options.statusesDefinition.labels[item.status]"
            ></i>
          </td>
          <td>{{item.created | date:'M/d/y'}}</td>
          <td>{{item.updated | date:'M/d/y'}}</td>
          <td [ngSwitch]="item.ownerId">
            <span *ngSwitchCase="currentUser.id">Me</span>
            <span *ngSwitchDefault="">{{_sharedOwners[item.ownerId] | AppUserName:!_sharedOwnersLoader.loaded}}</span>
          </td>
          <td *ngFor="let column of options.columns">
            {{item | apply: column.get: asyncData}}
          </td>
          <td class="dropdown text-right text-muted" dropdown [dropdownContainer]="'.table-scroll'">
            <a class="nav-link link-colorless table-row-actions" data-toggle="dropdown" aria-haspopup="true" aria-expanded="true">
              <span class="glyphicon glyphicon-option-vertical"></span>
            </a>

            <ul class="dropdown-menu dropdown-menu-right">
              <li><a class="dropdown-item link"
                [routerLink]="item | apply: _prepareLink: options.baseRoute">
                Preview
              </a></li>

              <li *ngFor="let action of options.actions | keys"
                [routerLinkActive]="['active']"
              >
                <a class="dropdown-item link"
                  [routerLink]="item | apply: _prepareLink: options.baseRoute: action"
                >{{options.actions[action].name}}</a>
              </li>

              <ng-template [ngIf]="options.sharable">
                <li *ngIf="item.ownerId === currentUser.id"><a class="dropdown-item link"
                  (click)="share(item)">
                  Share
                </a></li>
              </ng-template>
              <li *ngIf="item | apply: _cloneAvailable: options"><a class="dropdown-item link"
                (click)="clone(item)">
                Clone
              </a></li>

              <li role="separator" class="divider"></li>
              <li><a
                (click)="deleteItem(item)"
                class="dropdown-item link">
                Trash
              </a></li>
              <li *ngIf="project"><a
                (click)="removeItemFromProject(item)"
                class="dropdown-item link">
                Remove
              </a></li>
            </ul>
          </td>
        </tr>
        </tbody>
      </table>
    </div>
  `,
})
export class LibraryItemListComponent<T extends IAsset & {status: string}> implements OnDestroy, OnChanges, OnInit, OnChanges {
  @Input() form: FormGroup;
  @Input() searchControl: FormControl;
  @Input() options: LibrarySectionDefinition<T>;

  project: IProject;

  itemsList: IBackendList<T>;
  selectedItems: T[] = [];
  readonly config = config;
  readonly _itemsLoader: ReactiveLoader<IBackendList<T>, any>;
  readonly _asyncDataLoader: ReactiveLoader<any, any>;
  readonly _itemsDataLoading: Observable<boolean>;

  readonly baseColumns: IColumnSortConfig[] = [
    { name: 'Name', alias: 'name' },
    { name: 'Status', style: 'width: 5%; text-align: center' },
    { name: 'Created', alias: 'created', style: 'width: 12%', reverse: true },
    { name: 'Modified', alias: 'updated', style: 'width: 12%', reverse: true },
    { name: 'Owner', style: 'width: 12%' },
  ];

  asyncData: any;

  protected currentUser: IUser;
  protected _sharedOwners: IUsersMap = {};

  @ViewChild('operations') private operations: AssetOperationsComponent;

  private formSubscription: Subscription;
  private eventSubscription: Subscription;
  private userSubscription: Subscription;
  private projectContextSubscription: Subscription;

  private _sharedOwnersLoader: ReactiveLoader<IUsersMap, T[]>;

  constructor(
    private projects: ProjectService,
    private events: EventService,
    private shares: SharedResourceService,
    private users: UserService,
    @Optional() private projectContext: ProjectContext,
  ) {
    this.currentUser = this.users.getUser();
    this.userSubscription = this.users.user.subscribe(user => this.currentUser = user);

    this._sharedOwnersLoader = new ReactiveLoader((items) => this._loadSharedOwners(items));
    this._sharedOwnersLoader.subscribe(_ => {
      Object.assign(this._sharedOwners, _);
    });
    this._itemsLoader = new ReactiveLoader(() => {
      const formValue: IListRequest = this.form.value;
      let listRequest: IAssetListRequest = {
        search: formValue.search,
        order: formValue.order,
        page: formValue.page,
        page_size: formValue.page_size,
        scope: formValue.scope,
      };

      if (this.projectContext) {
        const [projectId, folderId] = this.projectContext.get();
        listRequest = {...listRequest, projectId, folderId};
      }

      return this._loadItems(listRequest).flatMap((items: IBackendList<T>) => {
        return this._checkSharedItems(items.data).mapTo(items);
      });
    });
    this._itemsLoader.subscribe((data) => {
      this.itemsList = data;
      const oldSelectedIds = this.selectedItems.map(_ => _.id);
      this.selectedItems = data.data.filter(_ => oldSelectedIds.includes(_.id));
    });

    // async data loader
    this._asyncDataLoader = new ReactiveLoader(() => this._loadAsyncData());
    this._asyncDataLoader.subscribe(_ => {
      this.asyncData = _;
    });

    this._itemsDataLoading = Observable.combineLatest(
      this._itemsLoader.active,
      this._asyncDataLoader.active,
      (a, b) => a || b,
    );
  }

  ngOnInit() {
    this._itemsLoader.load();

    this.eventSubscription = this.events.subscribe((event) => {
      if (
        event.type === this.options.reloadOn ||
        (this.project && event.type === IEvent.Type.UPDATE_PROJECT_ASSETS && event.data === this.project.id)
      ) {
        this._itemsLoader.load();
      }
    });

    this.formSubscription = this.form.valueChanges.debounceTime(100)
      .subscribe(() => this._itemsLoader.load());

    if (this.projectContext) {
      this.projectContextSubscription = this.projectContext.value
        .do(() => this._itemsLoader.load())
        .map(([projectId, _folderId]: TObjectId[]) => projectId)
        .distinctUntilChanged()
        .flatMap(projectId => {
          return projectId
            ? this.projects.get(projectId)
            : Observable.of(null);
        })
        .subscribe(project => {
          this.project = project;
        });
    }
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes.options) {
      this.itemsList = undefined;
      this.selectedItems = [];
      this._asyncDataLoader.load();
    }
  }

  ngOnDestroy() {
    this.projectContextSubscription && this.projectContextSubscription.unsubscribe();
    this.formSubscription && this.formSubscription.unsubscribe();
    this.eventSubscription && this.eventSubscription.unsubscribe();
    this.userSubscription.unsubscribe();
    this._asyncDataLoader.complete();
  }

  share(item: T) {
    this.operations.shareModal.open(this.options.assetType, [item]);
  }

  clone(item: T) {
    this.operations.cloneModal.open(this.options.assetType, item.id, item.name);
  }

  deleteItem(item: T) {
    this.operations.trash([item]);
  }

  removeItemFromProject(item: T) {
    this.projects.unlinkAssets<T>(this.options.assetType, this.project, [item]);
  }

  changeSelection(item: T, checked: boolean) {
    let i = this.isSelected(item);
    if (checked) {
      i >= 0 || (<any[]> this.selectedItems).push(item);
    } else {
      i >= 0 && (<any[]> this.selectedItems).splice(i, 1);
    }
    this.selectedItems = [...this.selectedItems];
  }

  isSelected(currentItem: T): number {
    return (<any[]> this.selectedItems).findIndex(item => item.id === currentItem.id);
  }

  _prepareLink = function(item: T, baseRoute: string[], action?: string) {
    const link = [...(baseRoute || []), item.id];

    if (action) {
      link.push(action);
    }

    return link;
  };

  _cloneAvailable = function(_item: T, options: LibrarySectionDefinition<T>) {
    return CloneModalComponent.canClone(options.assetType);
  };

  private _loadItems(params?: IAssetListRequest): Observable<IBackendList<T>> {
    return this.options.service.list(params);
  }

  private _loadSharedOwners(items: T[]): Observable<IUsersMap> {
    const observables = items
      .filter(item => {
        return !this._sharedOwners.hasOwnProperty(item.ownerId);
      })
      .map(item => {
        return this.shares.getSharedAssetOwner(this.options.assetType, <TObjectId> item.id);
      });

    if (!observables.length) {
      return Observable.of({});
    }

    return Observable.merge(...observables).reduce((acc, item: IUser) => {
      acc[item.id] = item;
      return acc;
    }, {});
  }

  private _checkSharedItems(data: T[]): Observable<boolean> {
    const sharedItems = data.filter(_ => {
      return _.ownerId !== this.currentUser.id;
    });
    if (sharedItems.length) {
      return this.shares
        .ensureSharedAccessLoaded(this.options.assetType, sharedItems.map(_ => _.id))
        .do(() => this._sharedOwnersLoader.load(sharedItems));
    }
    return Observable.of(true);
  }

  private _loadAsyncData(): Observable<any> {
    if (!this.options.loadAsyncData) {
      return Observable.of(null);
    }
    return this.options.loadAsyncData();
  }
}

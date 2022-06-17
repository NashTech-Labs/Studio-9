import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { FormControl } from '@angular/forms';

import { Observable } from 'rxjs/Observable';
import { Subscription } from 'rxjs/Subscription';

import config from '../config';
import { AppSelectOptionData } from '../core-ui/components/app-select.component';
import { IBackendList } from '../core/interfaces/common.interface';
import { UserService } from '../core/services/user.service';
import { AppFormGroup } from '../utils/forms';
import { ReactiveLoader } from '../utils/reactive-loader';

import { UserDeleteModalComponent } from './user-delete-modal';
import { UserManagementService as UserManagementService } from './user-management.service';
import { IUMUser, IUserSearchParams, UserStatus } from './user.interfaces';

enum SearchModes {
  FIRST_NAME = 'FIRST_NAME',
  LAST_NAME = 'LAST_NAME',
}

@Component({
  selector: 'user-list',
  template: `
    <app-spinner [visibility]="_itemsLoader.active | async"></app-spinner>

    <user-operations
      [(selectedItems)]="selectedItems"
      (deactivated)="onBulkActionComplete($event)"
      (activated)="onBulkActionComplete($event)"
      (deleted)="onBulkActionComplete($event)"
    ></user-operations>

    <div *ngIf="paginationForm" class="pt15">
      <form class="form">
        <div class="row">
          <div class="col-md-5 col-lg-4 col-md-push-7 col-lg-push-8">
            <app-select
              [label]="'Search Mode'"
              [options]="searchModeOptions"
              [control]="searchForm.controls['searchMode']"
              (valueChange)="onSearchModeChange()"
            ></app-select>
          </div>
          <div class="col-md-7 col-lg-8 col-md-pull-5 col-lg-pull-4"
            [ngSwitch]="searchForm.controls['searchMode'].value">
            <app-input *ngSwitchDefault=""
              [control]="searchForm.controls['firstName']"
              [iconBefore]="'glyphicon-search'"
              [iconAfter]="'glyphicon-remove'"
              (iconAfterClick)="searchForm.controls['firstName'].setValue('')"
            ></app-input>
            <app-input *ngSwitchCase="SearchModes.LAST_NAME"
              [control]="searchForm.controls['lastName']"
              [iconBefore]="'glyphicon-search'"
              [iconAfter]="'glyphicon-remove'"
              (iconAfterClick)="searchForm.controls['lastName'].setValue('')"
            ></app-input>
          </div>
        </div>
      </form>

      <div class="row">
        <div class="col-xs-12">
          <div class="p0 form-control brand-control">
            <div class="row">
              <div class="col-xs-12" *ngIf="!!itemsList">
                <div class="pull-right">
                  {{(itemsList.count || 0) | pluralize:({other: '{} users', '0': 'no users', '1': '{} user'})}}
                </div>
                <div class="pull-right">
                  <app-pagination [page]="paginationForm.controls['page']"
                    [pageSize]="paginationForm.controls['page_size']"
                    [currentPageSize]="itemsList.data.length"
                    [rowsCount]="itemsList.count"
                  ></app-pagination>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <ng-container *ngIf="!(_itemsLoader.active | async)">
      <div class="table-scroll" *ngIf="itemsList">
        <table class="table dataTable table-hover">
          <thead>
          <tr style="white-space: nowrap">
            <th style="width: 1%"></th>
            <th *ngFor="let item of options?.columns"
              [grid-sort]="item"
              [grid-sort-control]="paginationForm.controls['order']"
              [attr.style]="item.style ? (item.style | safeStyle) : ''"
            >
              {{item.name}}
            </th>
            <th class="text-right">Actions</th>
          </tr>
          </thead>
          <tbody>
          <tr *ngFor="let item of itemsList.data">
            <td>
              <app-check
                (checkedChange)="changeSelection(item, $event)"
                [checked]="getSelectedItemIndex(item) > -1"
                [name]="'selection' + item.id" [type]="'checkbox'"
                [value]="item"></app-check>
            </td>
            <td>
              <a [routerLink]="item | apply: _getItemLink"
                [title]="item.username"
              >{{item.username}}</a>
            </td>
            <td class="ellipsis" [attr.style]="_getColumnStyle('firstName') | safeStyle">
              <a [routerLink]="item | apply: _getItemLink"
                [title]="item.firstName"
              >{{item.firstName}}</a>
            </td>
            <td class="ellipsis" [attr.style]="_getColumnStyle('lastName') | safeStyle">
              <a [routerLink]="item | apply: _getItemLink"
                [title]="item.lastName"
              >{{item.lastName}}</a>
            </td>
            <td class="ellipsis" [attr.style]="_getColumnStyle('email') | safeStyle">
                <span [title]="item.email">
                  {{item.email}}
                </span>
            </td>
            <td>{{item | apply: _getRoleLabel: config.user.role.labels}}</td>
            <td>
                <span [ngClass]="item | apply: _getItemStatusClass">
                  {{item | apply: _getItemStatus: config: umService}}
                </span>
            </td>
            <td>
                <span title="{{item.created | date:'M/d/y HH:mm'}}">
                  {{item.created | date:'M/d/y'}}
                </span>
            </td>
            <td>
                <span title="{{item.updated | date:'M/d/y HH:mm'}}">
                  {{item.updated | date:'M/d/y'}}
                </span>
            </td>
            <td class="text-muted">
              <div class="dropdown text-right" dropdown [dropdownContainer]="'.table-scroll'">
                <a
                  class="nav-link link-colorlesstable-row-actions"
                  data-toggle="dropdown"
                  aria-haspopup="true"
                  aria-expanded="true"
                >
                  <span class="glyphicon glyphicon-option-vertical"></span>
                </a>

                <ul class="dropdown-menu dropdown-menu-right">
                  <li>
                    <a class="dropdown-item link" [routerLink]="item | apply: _getItemLink">
                      View/Edit
                    </a>
                  </li>
                  <li>
                    <a class="dropdown-item link"
                      *ngIf="item | apply: canToggleUserActivationStatus"
                      (click)="toggleUserActivationStatus(item)"
                    >
                      {{item.status === UserStatus.ACTIVE ? 'Deactivate' : 'Activate'}}
                    </a>
                  </li>
                  <li>
                    <a class="dropdown-item link"
                      *ngIf="userService.getUser().id !== item.id"
                      (click)="onClickDeleteItem(item)"
                    >
                      Trash
                    </a>
                  </li>
                </ul>
              </div>
            </td>
          </tr>
          </tbody>
        </table>
      </div>

      <p *ngIf="!itemsList">
        No items to display.
      </p>
    </ng-container>
    <user-delete-modal #confirmDeleteModal
      [selectedItems]="selectedItems"
      (deleted)="onDeleted()"
    ></user-delete-modal>
  `,
})
export class UserListComponent implements OnInit, OnDestroy {
  config = config;
  SearchModes = SearchModes;
  UserStatus = UserStatus;

  paginationForm: AppFormGroup<{page: FormControl, page_size: FormControl, order: FormControl}>;
  searchForm: AppFormGroup<{searchMode: FormControl, firstName: FormControl, lastName: FormControl}>;

  selectedItems: IUMUser[] = [];

  options: {
    columns: {
      name: string;
      alias?: string;
      style?: string;
    }[];
  };

  searchModeOptions = AppSelectOptionData.fromList(
    [SearchModes.FIRST_NAME, SearchModes.LAST_NAME],
    ['First Name', 'Last Name'],
  );

  readonly _itemsLoader: ReactiveLoader<IBackendList<IUMUser>, void>;
  itemsList: IBackendList<IUMUser>;

  @ViewChild('confirmDeleteModal') confirmDeleteModal: UserDeleteModalComponent;

  private _subscriptions: Subscription[] = [];

  constructor(
    public umService: UserManagementService,
    public userService: UserService,
  ) {
    this._itemsLoader = new ReactiveLoader(() => {
      return umService.list(this._prepareRequestParams());
    });

    this._itemsLoader.subscribe(this.onLoaded.bind(this));

    this.options = {
      columns: this._getColumns(),
    };

    this._initForms();
  }

  ngOnInit(): void {
    this._itemsLoader.load();
  }

  ngOnDestroy(): void {
    this._subscriptions.forEach(_ => _.unsubscribe());
  }

  onSearchModeChange(): void {
    const controls = this.searchForm.controls;
    controls['firstName'].reset('');
    controls['lastName'].reset('');
  }

  onLoaded(users: IBackendList<IUMUser>) {
    this.itemsList = users;

    this.selectedItems = this.selectedItems
      .reduce((acc, _) => {
        const user = users.data.find(user => _.id === user.id);
        if (user) {
          acc.push(user);
        }
        return acc;
      }, []);
  }

  onDeleted() {
    this.paginationForm.patchValue({page: 1});
    this._itemsLoader.load();
  }

  canToggleUserActivationStatus = (item: IUMUser): boolean => {
    return this.userService.getUser().id !== item.id;
  };

  toggleUserActivationStatus(item: IUMUser): void {
    const observable = (item.status === UserStatus.ACTIVE)
      ? this.umService.deactivateUser(item)
      : this.umService.activateUser(item);

    observable.subscribe(() => {
      this._itemsLoader.load();
    });
  }

  onClickDeleteItem(item: IUMUser) {
    this.selectedItems = [item];
    this.confirmDeleteModal.show();
  }

  changeSelection(item: IUMUser, checked: boolean): void {
    let i = this.getSelectedItemIndex(item);
    if (checked) {
      i > -1 || this.selectedItems.push(item);
    } else {
      i > -1 && this.selectedItems.splice(i, 1);
    }
    this.selectedItems = [...this.selectedItems];
  }

  getSelectedItemIndex(currentItem: IUMUser): number {
    return this.selectedItems.findIndex(item => item.id === currentItem.id);
  }

  onBulkActionComplete(successItems: IUMUser[]): void {
    if (successItems.length) {
      this._itemsLoader.load();
    }
  }

  _getItemLink = (item: IUMUser): string[] => {
    return [item.id];
  };

  _getItemStatusClass(item: IUMUser): {[key: string]: boolean} {
    return {
      'label': true,
      'text-capitalize': true,
      'label-success': item.status === UserStatus.ACTIVE,
      'label-default': item.status === UserStatus.INACTIVE,
      'label-danger': item.status === UserStatus.DEACTIVATED,
    };
  }

  _getItemStatus(item: IUMUser, config, umService: UserManagementService): string {
    return config.user.status.labels[item.status];
  }

  _getRoleLabel(item: IUMUser, labels: {[key: string]: string}): string {
    return item.role in labels ? labels[item.role] : item.role;
  }

  _getColumnStyle(alias: string): string {
    const column = this.options.columns.find(_ => _.alias === alias);
    return column ? column.style : '';
  }

  private _initForms(): void {
    this.paginationForm = new AppFormGroup({
      page: new FormControl(1),
      page_size: new FormControl(config.defaultPageSize),
      order: new FormControl('-created'),
    });

    const searchForm = new AppFormGroup({
      searchMode: new FormControl(''),
      firstName: new FormControl(''),
      lastName: new FormControl(''),
    });

    this._subscriptions = [];

    this.searchForm = searchForm;
    this.onSearchModeChange();

    this._subscriptions.push(
      Observable.merge(
        this.paginationForm.valueChanges,
        this.searchForm.valueChanges,
      )
        .debounceTime(100)
        .subscribe(() => {
          if (this.searchForm.valid) {
            this._itemsLoader.load();
          }
        }),
    );
  }

  private _getColumns() {
    return [
      { name: 'Username', alias: 'username' },
      { name: 'First Name', alias: 'firstName', style: 'max-width: 150px' },
      { name: 'Last Name', alias: 'lastName', style: 'max-width: 150px' },
      { name: 'Email', alias: 'email', style: 'max-width: 300px' },
      { name: 'Role' },
      { name: 'Status' },
      { name: 'Created', alias: 'created' },
      { name: 'Updated', alias: 'updated' },
    ];
  }

  private _prepareRequestParams(): IUserSearchParams {
    const pagination = this.paginationForm.controls;
    const search = this.searchForm.controls;

    const params = {
      page: pagination['page'].value,
      page_size: pagination['page_size'].value,
      order: pagination['order'].value,
    };

    if (search['searchMode'].value === SearchModes.FIRST_NAME && search['firstName'].value) {
      params['firstName'] = search['firstName'].value;
    }

    if (search['searchMode'].value === SearchModes.LAST_NAME && search['lastName'].value) {
      params['lastName'] = search['lastName'].value;
    }

    return params;
  }
}

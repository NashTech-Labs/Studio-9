import { Component, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges, ViewChild } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';

import { Observable } from 'rxjs/Observable';
import { Subscription } from 'rxjs/Subscription';

import config from '../config';
import { IBackendList } from '../core/interfaces/common.interface';
import { IProcess } from '../core/interfaces/process.interface';
import { EventService, IEvent } from '../core/services/event.service';
import { UserService } from '../core/services/user.service';
import { ReactiveLoader } from '../utils/reactive-loader';

import { PackageDetailsModalComponent } from './package-details-modal.component';
import { IPackage, IPackageSearchParams } from './package.interfaces';
import { PackageService } from './package.service';
import { PublishS9ProjectModalComponent } from './publish-s9-project-modal.component';


@Component({
  selector: 'packages-list',
  template: `
    <app-spinner [visibility]="(isLoading$ | async)"></app-spinner>

    <div *ngIf="paginationForm" class="pt15">
      <div class="row">
        <div class="col-xs-12">
          <div class="p0 form-control brand-control">
            <div class="row">
              <div class="col-xs-12">
                <div class="pull-right">
                  {{(itemsList?.count || 0) | pluralize:({
                  other: '{} packages',
                  '0': 'no packages',
                  '1': '{} package'
                })}}
                </div>
                <div class="pull-right">
                  <app-pagination [page]="paginationForm.controls['page']"
                    [pageSize]="paginationForm.controls['page_size']"
                    [currentPageSize]="itemsList?.data.length"
                    [rowsCount]="itemsList?.count || 0"
                  ></app-pagination>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <ng-container *ngIf="!(isLoading$ | async)">
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
            <td
              class="ellipsis"
              [attr.style]="_getColumnStyle('name') | safeStyle"
              (click)="openItemDetails(item)"
            >
              <a class="link" title="{{item.name}}">{{item.name}}</a>
            </td>
            <td class="ellipsis" [attr.style]="_getColumnStyle('version') | safeStyle">
              <span title="{{item.version}}">{{item.version}}</span>
            </td>
            <td>
              <span title="{{item.created | date:'M/d/y HH:mm'}}">
                {{item.created | date:'M/d/y'}}
              </span>
            </td>
            <td>
              <span class="label text-capitalize" [ngClass]="item.isPublished ? 'label-success' : 'label-warning'">
                {{item.isPublished ? 'Published' : 'Not Published'}}
              </span>
            </td>
            <td class="text-muted">
              <div class="dropdown text-right"
                dropdown
                [dropdownContainer]="'.table-scroll'"
              >
                <a
                  class="nav-link link-colorless table-row-actions"
                  data-toggle="dropdown"
                  aria-haspopup="true"
                  aria-expanded="true"
                >
                  <span class="glyphicon glyphicon-option-vertical"></span>
                </a>

                <ul class="dropdown-menu dropdown-menu-right">
                  <li>
                    <a class="dropdown-item link" (click)="openItemDetails(item)">
                      Details
                    </a>
                  </li>
                  <li>
                    <a class="dropdown-item link" target="_blank" [href]="item.location">
                      Download
                    </a>
                  </li>
                  <li [ngClass]="{'disabled': item.isPublished}">
                    <a class="dropdown-item link" (click)="item.isPublished || publishPackage(item)">
                      Publish
                    </a>
                  </li>
                  <ng-container *ngIf="userService.getUser().id === item.ownerId">
                    <li role="separator" class="divider"></li>
                    <li [ngClass]="{'disabled': item.isPublished}">
                      <a class="dropdown-item link" (click)="item.isPublished || deletePackage(item)">
                        Trash
                      </a>
                    </li>
                  </ng-container>
                </ul>
              </div>
            </td>
          </tr>
          </tbody>
        </table>
      </div>

      <p *ngIf="!itemsList || !itemsList.count">
        No items to display.
      </p>

      <package-details-modal #detailsModal></package-details-modal>
      <publish-s9-project-modal #publishModal></publish-s9-project-modal>
    </ng-container>
  `,
})
export class PackagesListComponent implements OnChanges, OnDestroy {
  @Input() selectedItems: IPackage[] = [];
  @Input() searchParams: IPackageSearchParams = {};
  @Output() selectedItemsChange = new EventEmitter<IPackage[]>();
  @Output() onDelete = new EventEmitter<void>();

  config = config;
  paginationForm: FormGroup;
  options: {
    columns: {
      name: string;
      alias?: string;
      style?: string;
    }[];
  };

  itemsList: IBackendList<IPackage>;

  @ViewChild('detailsModal') detailsModal: PackageDetailsModalComponent;
  @ViewChild('publishModal') publishModal: PublishS9ProjectModalComponent;

  private readonly _itemsLoader: ReactiveLoader<IBackendList<IPackage>, any>;
  private _subscription = new Subscription();

  constructor(
    readonly userService: UserService,
    readonly eventService: EventService,
    packageService: PackageService,
  ) {
    this._itemsLoader = new ReactiveLoader((data: IPackageSearchParams) => {
      return packageService.list(data);
    });

    this._subscription.add(this._itemsLoader.subscribe(this.onLoaded.bind(this)));
    this._subscription.add(this.eventService.subscribe((event) => {
      if (event.type === IEvent.Type.DELETE_PACKAGE || event.type === IEvent.Type.PUBLISH_PACKAGE) {
        if (this.itemsList && this.itemsList.data.map(_ => _.id).includes(event.data.id)) {
          this._loadPackages();
        }
      }
      if (
        event.type === IEvent.Type.PROCESS_COMPLETED
        && event.data.jobType === IProcess.JobType.PROJECT_BUILD
        && (!this.searchParams.s9ProjectId || this.searchParams.s9ProjectId === event.data.targetId)
      ) {
        this._loadPackages();
      }
    }));

    this.options = {
      columns: this._getColumns(),
    };

    this.paginationForm = new FormGroup({
      page: new FormControl(1),
      page_size: new FormControl(config.defaultPageSize),
      order: new FormControl('-created'),
    });

    this._subscription.add(
      this.paginationForm
        .valueChanges.debounceTime(100)
        .subscribe(() => this._itemsLoader.load(this._prepareRequestParams())),
    );
  }

  get isLoading$(): Observable<boolean> {
    return this._itemsLoader.active;
  }

  ngOnChanges(changes: SimpleChanges): void {
    if ('searchParams' in changes) {
      this.paginationForm.patchValue({
        page: 1,
      });
      this._loadPackages();
    }
  }

  ngOnDestroy(): void {
    this._subscription.unsubscribe();
  }

  onLoaded(items: IBackendList<IPackage>): void {
    this.itemsList = items;
    this._updateSelectedItems(this.selectedItems.filter(_ => items.data.find(item => _.id === item.id)));
  }

  deletePackage(item: IPackage): void {
    this._updateSelectedItems([item]);
    this.onDelete.emit();
  }

  openItemDetails(item: IPackage) {
    this.detailsModal.open(item.id);
  }

  changeSelection(item: IPackage, checked: boolean): void {
    let i = this.getSelectedItemIndex(item);
    if (checked) {
      i > -1 || this.selectedItems.push(item);
    } else {
      i > -1 && this.selectedItems.splice(i, 1);
    }
    this._updateSelectedItems([...this.selectedItems]);
  }

  getSelectedItemIndex(currentItem: IPackage): number {
    return this.selectedItems.findIndex(item => item.id === currentItem.id);
  }

  _getColumnStyle(alias: string): string {
    const column = this.options.columns.find(_ => _.alias === alias);
    return column ? column.style : '';
  }

  publishPackage(item: IPackage) {
    this.publishModal.open(item);
  }

  private _loadPackages(): void {
    this._itemsLoader.load(this._prepareRequestParams());
  }

  private _getColumns(): ({ name: string; alias?: string })[] {
    return [
      { name: 'Name', alias: 'name' },
      { name: 'Version', alias: 'version' },
      { name: 'Created', alias: 'created' },
      { name: 'Status' },
    ];
  }

  private _prepareRequestParams(): IPackageSearchParams {
    const pagination = this.paginationForm.controls;

    return {
      ...this.searchParams,
      page: pagination['page'].value,
      page_size: pagination['page_size'].value,
      order: pagination['order'].value,
    };
  }

  private _updateSelectedItems(selectedItems: IPackage[]): void {
    this.selectedItems = selectedItems;
    this.selectedItemsChange.emit(this.selectedItems);
  }
}

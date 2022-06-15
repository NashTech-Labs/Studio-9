import { Component, OnChanges, OnDestroy, OnInit, SimpleChanges, ViewChild } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import * as moment from 'moment';
import { Subscription } from 'rxjs/Subscription';

import config from '../config';
import { AppSelectOptionData } from '../core-ui/components/app-select.component';
import { ModalComponent } from '../core-ui/components/modal.component';
import { ifMocks } from '../core/core.mocks-only';
import { IBackendList } from '../core/interfaces/common.interface';
import { IProcess } from '../core/interfaces/process.interface';
import { AssetURLService } from '../core/services/asset-url.service';
import { ProcessService } from '../core/services/process.service';
import { ReactiveLoader } from '../utils/reactive-loader';
import { AppValidators } from '../utils/validators';

import { IProcessSearchParams } from './process.interfaces';

enum SearchModes {
  JOB_TYPES = 'JOB_TYPES',
  STARTED = 'STARTED',
  COMPLETED = 'COMPLETED',
}

@Component({
  selector: 'processes-list',
  template: `
    <h3>Jobs</h3>

    <app-spinner [visibility]="_itemsLoader.active | async"></app-spinner>

    <div *ngIf="paginationForm">
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
            <app-select *ngSwitchCase="SearchModes.JOB_TYPES"
              [label]="'Job Types'"
              [options]="searchJobTypesOptions"
              [multiple]="true"
              [iconBefore]="'glyphicon-search'"
              [control]="searchForm.controls['jobTypes']"
            ></app-select>
            <app-datepicker *ngSwitchCase="SearchModes.STARTED"
              [label]="'Start Date'"
              [iconBefore]="'glyphicon-calendar'"
              [control]="searchForm.controls['started']"
            ></app-datepicker>
            <app-datepicker *ngSwitchCase="SearchModes.COMPLETED"
              [label]="'Completion Date'"
              [iconBefore]="'glyphicon-calendar'"
              [control]="searchForm.controls['completed']"
            ></app-datepicker>
          </div>
        </div>
      </form>

      <div class="row">
        <div class="col-xs-12">
          <div class="p0 form-control brand-control">
            <div class="row">
              <div class="col-xs-12" *ngIf="!!itemsList">
                <div class="pull-right">
                  {{(itemsList.count || 0) | pluralize:({
                  other: '{} Processes',
                  '0': 'no processes',
                  '1': '{} process'
                })}}
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
            <td
              *ngVar="item | apply: _getAssetURL as assetURL"
              [ngSwitch]="!!assetURL"
            >
              <a
                *ngSwitchCase="true"
                [routerLink]="assetURL"
              >{{config.asset.labels[item.target]}} #{{item.targetId}}</a>
              <ng-container
                *ngSwitchDefault
              >{{config.asset.labels[item.target]}} #{{item.targetId}}</ng-container>
            </td>
            <td>{{item | apply: _getJobTypeLabel}}</td>
            <td>
              <span [ngClass]="item | apply: _getItemStatusClass">
                <a *ngIf="hasErrorMessages(item)"
                  (click)="showDetails(item)"
                  title="Click to view error messages"
                >
                  {{item | apply: _getItemStatus: config}}
                </a>
                <ng-container *ngIf="!hasMenuItems(item)">
                  {{item | apply: _getItemStatus: config}}
                </ng-container>
              </span>
            </td>
            <td>{{item.progress | percent}}</td>
            <td>
              <span title="{{item.created | date:'M/d/y HH:mm'}}">
                {{item.created | date:'M/d/y'}}
              </span>
            </td>
            <td>
              <span title="{{item.started | date:'M/d/y HH:mm'}}">
                {{item.started | date:'M/d/y'}}
              </span>
            </td>
            <td>
              <span title="{{item.completed | date:'M/d/y HH:mm'}}">
                {{item.completed | date:'M/d/y'}}
              </span>
            </td>
            <td class="text-muted">
              <div *ngIf="hasMenuItems(item)"
                class="dropdown text-right"
                dropdown
                [dropdownContainer]="'.table-scroll'"
              >
                <a class="nav-link link-colorless table-row-actions" data-toggle="dropdown" aria-haspopup="true" aria-expanded="true">
                  <span class="glyphicon glyphicon-option-vertical"></span>
                </a>

                <ul class="dropdown-menu dropdown-menu-right">
                  <li *ngIf="hasErrorMessages(item)">
                    <a (click)="showDetails(item)" class="dropdown-item link">Show error messages</a>
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

    <app-modal #modal [caption]="'Process error messages'" [sizeClass]="config.modal.size.LARGE">
      <ng-container *ngIf="selectedItem">
        <dl class="dl-horizontal">
          <dt>Target:</dt>
          <dd
            *ngVar="selectedItem | apply: _getAssetURL as assetURL"
            [ngSwitch]="!!assetURL"
          >
            <a
              *ngSwitchCase="true"
              [href]="assetURL"
              (click)="navigateFromModal(selectedItem, $event)"
            >{{config.asset.labels[selectedItem.target]}} #{{selectedItem.targetId}}</a>
            <span
              *ngSwitchCase="false"
            >{{config.asset.labels[selectedItem.target]}} #{{selectedItem.targetId}}</span>
          </dd>

          <dt>Created:</dt>
          <dd>{{selectedItem.created | date: 'M/d/y HH:mm'}}</dd>

          <dt>Status:</dt>
          <dd>
            <span [ngClass]="selectedItem | apply: _getItemStatusClass">
              {{selectedItem | apply: _getItemStatus: config}}
            </span>
          </dd>
        </dl>

        <pre class="pre-scrollable">{{selectedItem.cause}}</pre>
      </ng-container>
    </app-modal>
  `,
})
export class ProcessesListComponent implements OnInit, OnChanges, OnDestroy {
  @ViewChild('modal') modal: ModalComponent;

  config = config;
  SearchModes = SearchModes;

  paginationForm: FormGroup;
  searchForm: FormGroup;

  selectedItem: IProcess;

  options: {
    columns: {
      name: string;
      alias?: string;
      style?: string;
    }[];
  };

  searchModeOptions = AppSelectOptionData.fromList(
    [SearchModes.JOB_TYPES, SearchModes.STARTED, SearchModes.COMPLETED],
    ['Job Type', 'Start Date', 'Completion Date'],
  );

  searchJobTypesOptions = AppSelectOptionData.fromList(
    config.process.job.type.list.filter(_ => ifMocks(true, !config.process.job.type.mockOnly[_])),
    config.process.job.type.labels,
  );

  readonly _itemsLoader: ReactiveLoader<IBackendList<IProcess>, any>;
  itemsList: IBackendList<IProcess>;

  private _subscriptions: Subscription[] = [];

  constructor(
    private _processService: ProcessService,
    private _assetURLService: AssetURLService,
    private _router: Router,
  ) {
    this._itemsLoader = new ReactiveLoader((data: IProcessSearchParams) => {
      return _processService.list(data);
    });

    this._itemsLoader.subscribe((list) => {
      this.itemsList = list;
    });

    this.options = {
      columns: this._getColumns(),
    };

    this._initForms();

    // todo: add polling
  }

  ngOnInit() {
    this._itemsLoader.load(this._prepareRequestParams());
  }

  ngOnChanges(changes: SimpleChanges) {
  }

  ngOnDestroy() {
    this._subscriptions.forEach(_ => _.unsubscribe());
  }

  get IProcess() {
    return IProcess;
  }

  hasErrorMessages(process: IProcess): boolean {
    return process.status === IProcess.Status.FAILED;
  }

  hasMenuItems(process: IProcess): boolean {
    return this.hasErrorMessages(process);
  }

  showDetails(item: IProcess): void {
    this.selectedItem = item;
    this.modal.show();
  }

  navigateFromModal(item: IProcess, event: MouseEvent): void {
    event.preventDefault();

    this.modal.onClosed.subscribe(() => {
      this._router.navigate(this._assetURLService.assetURL(item.target, item.targetId));
    });

    this.modal.hide();
  }

  onSearchModeChange(): void {
    const controls = this.searchForm.controls;

    controls['jobTypes'].reset([]);
    controls['started'].reset(null);
    controls['completed'].reset(null);
  }

  _getAssetURL = (
    item: IProcess,
  ): string => {
    const urlArray = this._assetURLService.assetURL(item.target, item.targetId);

    // returning string is a workaround for angular 5.0.3 bug it doesn't support event.preventDefault()
    // in click handler so we can't close modal window before redirect
    return urlArray ? this._router.createUrlTree(urlArray).toString() : null;
  };

  _getItemStatus(item: IProcess, config): string {
    return config.process.status.labels[item.status];
  }

  _getItemStatusClass(item: IProcess) {
    return {
      'label': true,
      'text-capitalize': true,
      'process-status': true,
      'label-danger': item.status === IProcess.Status.FAILED,
      'label-success': item.status === IProcess.Status.COMPLETED,
      'label-default': item.status === IProcess.Status.CANCELLED,
      'label-info': item.status === IProcess.Status.QUEUED,
      'label-warning': item.status === IProcess.Status.RUNNING,
    };
  }

  _getJobTypeLabel(item: IProcess): string {
    return config.process.job.type.labels[item.jobType] || item.jobType;
  }

  private _initForms() {
    this.paginationForm = new FormGroup({
      page: new FormControl(1),
      page_size: new FormControl(20),
      order: new FormControl('-created'),
    });

    const searchForm = new FormGroup({
      searchMode: new FormControl(SearchModes.JOB_TYPES),
      jobTypes: new FormControl([]),
      started: new FormControl(''),
      completed: new FormControl(''),
    });

    this._subscriptions = [
      AppValidators.crossValidate(
        searchForm.controls['searchMode'],
        [searchForm.controls['started']],
        (searchMode: SearchModes) => searchMode === SearchModes.STARTED ? AppValidators.date() : Validators.nullValidator,
      ),
      AppValidators.crossValidate(
        searchForm.controls['searchMode'],
        [searchForm.controls['completed']],
        (searchMode: SearchModes) => searchMode === SearchModes.COMPLETED ? AppValidators.date() : Validators.nullValidator,
      ),
    ];

    this.searchForm = searchForm;
    this.onSearchModeChange();

    this._subscriptions.push(
      this.searchForm.valueChanges.debounceTime(100).subscribe(() => {
        if (this.searchForm.valid) {
          this.paginationForm.patchValue({ page: 1 });
        }
      }),
      this.paginationForm.valueChanges.subscribe(() => {
        if (this.searchForm.valid) {
          this._itemsLoader.load(this._prepareRequestParams());
        }
      }),
    );
  }

  private _getColumns() {
    return [
      { name: 'Asset' },
      { name: 'Job Type' },
      { name: 'Status' },
      { name: 'Progress' },
      { name: 'Created', alias: 'created' },
      { name: 'Started', alias: 'started' },
      { name: 'Completed', alias: 'completed' },
    ];
  }

  private _prepareDateFilter(value: string): [string, string] {
    if (!value) {
      return null;
    }

    const from = moment(value, 'YYYY-MM-DD');
    const to = moment(from).add(1, 'days');

    return [from.toISOString(), to.toISOString()];
  }

  private _prepareRequestParams(): IProcessSearchParams {
    const pagination = this.paginationForm.controls;
    const search = this.searchForm.controls;

    const params = {
      page: pagination['page'].value,
      page_size: pagination['page_size'].value,
      order: pagination['order'].value,
    };

    if (search['searchMode'].value === SearchModes.JOB_TYPES) {
      const jobTypes: IProcess.JobType = search['jobTypes'].value || [];
      if (jobTypes.length) {
        params['jobTypes'] = jobTypes;
      }
    }

    if (search['searchMode'].value === SearchModes.STARTED) {
      const value = this._prepareDateFilter(search['started'].value);
      if (value) {
        params['processStarted'] = value;
      }
    }

    if (search['searchMode'].value === SearchModes.COMPLETED) {
      const value = this._prepareDateFilter(search['completed'].value);
      if (value) {
        params['processCompleted'] = value;
      }
    }

    return params;
  }
}


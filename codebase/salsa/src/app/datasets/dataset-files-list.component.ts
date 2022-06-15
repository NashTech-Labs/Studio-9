import {
  Component, EventEmitter,
  Input,
  OnChanges,
  OnDestroy,
  OnInit, Output,
  SimpleChanges,
} from '@angular/core';
import { FormControl } from '@angular/forms';

import { Observable } from 'rxjs/Observable';
import { debounceTime } from 'rxjs/operators/debounceTime';
import { filter } from 'rxjs/operators/filter';
import { map } from 'rxjs/operators/map';
import { merge } from 'rxjs/operators/merge';
import { takeUntil } from 'rxjs/operators/takeUntil';
import { Subject } from 'rxjs/Subject';
import { Subscription } from 'rxjs/Subscription';

import config from '../config';
import { ModalService } from '../core-ui/services/modal.service';
import { GridOptions } from '../core/core.interface';
import {
  IBackendList,
} from '../core/interfaces/common.interface';
import {
  EventService,
  IEvent,
} from '../core/services/event.service';
import { AppFormGroup } from '../utils/forms';
import { ReactiveLoader } from '../utils/reactive-loader';

import { BinaryDataset } from './dataset.interfaces';
import { BinaryDatasetService } from './dataset.service';

@Component({
  selector: 'dataset-files-list',
  template: `
    <div class="app-spinner-box">
      <app-spinner [visibility]="loader.active | async"></app-spinner>

      <ng-container *ngTemplateOutlet="paginationTemplate"></ng-container>

      <div *ngIf="loader.loaded">
        <ng-container *ngTemplateOutlet="fileListTemplate"></ng-container>
      </div>
    </div>

    <ng-template #paginationTemplate>
      <div class="row">
        <div class="col-xs-12">
          <div class="p0 form-control brand-control">
            <div class="row">
              <div class="col-xs-12" *ngIf="itemsList">
                <div class="pull-right">
                  {{(itemsList.count || 0) | pluralize:({other: '{} files', '0': 'no files', '1': '{} file'})}}
                </div>
                <div class="pull-right">
                  <app-pagination
                    [page]="paginationForm.controls['page']"
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
    </ng-template>

    <ng-template #fileListTemplate>
      <div class="table-scroll" *ngIf="itemsList">
        <table class="table dataTable regular-cursor table-hover">
          <thead>
            <tr style="white-space: nowrap">
              <th style="width: 1%">
                <app-check [checked]="selectedItems | apply: isSelectedAll: itemsList.data"
                  (checkedChange)="setSelectAll($event)"></app-check>
              </th>
              <th *ngFor="let column of options?.columns"
                [grid-sort]="column"
                [grid-sort-control]="paginationForm.controls['order']"
                [attr.style]="column.style ? (column.style | safeStyle) : ''"
              >
                {{column.name}}
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
                  [name]="'selection' + item.filename" [type]="'checkbox'"
                  [value]="item"></app-check>
              </td>
              <td class="ellipsis" [attr.style]="getColumnStyle('filename') | safeStyle">
                {{item.filename}}
              </td>
              <td class="ellipsis" [attr.style]="getColumnStyle('filesize') | safeStyle">
                {{item.filesize | formatBytes}}
              </td>
              <td>
                  <span title="{{item.modified | date:'M/d/y HH:mm'}}">
                    {{item.modified | date:'M/d/y'}}
                  </span>
              </td>
              <td class="text-muted">
                <div class="dropdown text-right"
                  dropdown
                  [dropdownContainer]="'.table-scroll'"
                >
                  <a class="nav-link link-colorless table-row-actions" data-toggle="dropdown" aria-haspopup="true" aria-expanded="true">
                    <span class="glyphicon glyphicon-option-vertical"></span>
                  </a>

                  <ul class="dropdown-menu dropdown-menu-right">
                    <li>
                      <a class="dropdown-item link"
                        (click)="onClickDownloadFile(item)"
                      >
                        Download
                      </a>
                    </li>
                    <li>
                      <a class="dropdown-item link"
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

      <p *ngIf="!itemsList || !itemsList.count">
        No items to display.
      </p>
    </ng-template>
  `,
})
export class DatasetFilesListComponent implements OnInit, OnDestroy, OnChanges {
  @Input() dataset: BinaryDataset;
  @Input() selectedItems: BinaryDataset.File[] = [];
  @Input() searchForm: AppFormGroup<{
    search: FormControl,
  }>;

  @Output() selectedItemsChange = new EventEmitter<BinaryDataset.File[]>();

  options: GridOptions = {
    columns: [
      { name: 'File Name', alias: 'filename', style: 'max-width: 300px' },
      { name: 'Size', alias: 'filesize' },
      { name: 'Modified', alias: 'modified' },
    ],
  };

  protected itemsList: IBackendList<BinaryDataset.File>;

  protected readonly paginationForm = new AppFormGroup({
    page: new FormControl(1),
    page_size: new FormControl(20),
    order: new FormControl('-modified'),
  });

  protected readonly loader: ReactiveLoader<IBackendList<BinaryDataset.File>, BinaryDataset.FileSearchParams>;

  private readonly _reloadOn = [
    IEvent.Type.UPDATE_DATASET_LIST,
    IEvent.Type.DELETE_DATASET_FILE,
  ];
  private readonly _destroy$: Subject<boolean> = new Subject<boolean>();
  private readonly _loaderSubscription: Subscription;

  constructor(
    private datasetService: BinaryDatasetService,
    private modalService: ModalService,
    events: EventService,
  ) {
    this.loader = new ReactiveLoader((requestParams: BinaryDataset.FileSearchParams): Observable<IBackendList<BinaryDataset.File>> => {
      return datasetService.listFiles(this.dataset.id, requestParams);
    });

    this._loaderSubscription = this.loader.subscribe(itemsList => {
      this.itemsList = itemsList;
    });

    events.observable.pipe(
      takeUntil(this._destroy$),
      map(event => event.type),
      filter(event => this._reloadOn.includes(event)),
    )
      .subscribe(() => {
        this.loader.load(this.paginationForm.value);
      });
  }

  ngOnInit(): void {
    if (!this.searchForm) {
      throw new Error('Search form was not provided');
    }

    this.searchForm.valueChanges
      .pipe(
        takeUntil(this._destroy$),
        merge(this.paginationForm.valueChanges),
        debounceTime(config.userInputDebounceTime),
      ).subscribe(() => {
        this.loader.load(this._getRequestData());
    });
  }

  ngOnDestroy(): void {
    this._destroy$.next(true);
    this._destroy$.complete();
    this._loaderSubscription && this._loaderSubscription.unsubscribe();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['dataset']) {
      this.dataset = changes['dataset'].currentValue;
      this.loader.load(this._getRequestData());
    }
  }

  protected getSelectedItemIndex(currentItem: BinaryDataset.File): number {
    return this.selectedItems.findIndex(item => item.filename === currentItem.filename);
  }

  protected onClickDownloadFile(file: BinaryDataset.File): void {
    this.datasetService.downloadFile(this.dataset.id, file.filepath);
  }

  protected changeSelection(file: BinaryDataset.File, add: boolean): void {
    const index = this.selectedItems.findIndex(_ => _.filename === file.filename);
    if (add && index !== -1) {
      return;
    }

    const selectedItems = [...this.selectedItems];
    if (add) {
      selectedItems.push(file);
    } else {
      selectedItems.splice(index, 1);
    }

    this._updateSelectedItems(selectedItems);
  }

  protected getColumnStyle(alias: string): string {
    const column = this.options.columns.find(_ => _.alias === alias);
    return column ? column.style : '';
  }

  protected onClickDeleteItem(item: BinaryDataset.File): void {
    this.modalService
      .confirm('Are you sure you want to delete the files selected?')
      .pipe(filter(_ => _))
      .subscribe(() => {
        this.datasetService.deleteFile(this.dataset.id, item.filename);
      });
  }

  protected isSelectedAll = function(selected: BinaryDataset.File[], all: BinaryDataset.File[]): boolean {
    if (!all.length) {
      return false;
    }

    const filtered = all.filter(a => selected.find(b => b.filename === a.filename));

    return filtered.length === all.length;
  };

  protected setSelectAll(selected: boolean): void {
    const items = this.itemsList.data.reduce(
      (acc, item) => {
        const index = acc.findIndex(file => file.filename === item.filename);
        if (index === -1 && selected) {
          acc.push(item);
        } else if (index > -1 && !selected) {
          acc.splice(index, 1);
        }
        return acc;
      },
      [...this.selectedItems],
    );

    this._updateSelectedItems(items);
  }

  private _getRequestData(): BinaryDataset.FileSearchParams {
    return { ...this.searchForm.value, ...this.paginationForm.value };
  }

  private _updateSelectedItems(selectedItems: BinaryDataset.File[]): void {
    this.selectedItems = [...selectedItems];
    this.selectedItemsChange.emit(this.selectedItems);
  }
}

import {
  Component,
  EventEmitter,
  Input,
  OnChanges,
  OnDestroy,
  Output,
  SimpleChanges,
} from '@angular/core';

import 'rxjs/add/observable/forkJoin';
import 'rxjs/add/operator/mapTo';
import { Observable } from 'rxjs/Observable';
import { filter } from 'rxjs/operators/filter';
import { mergeMap as flatMap } from 'rxjs/operators/mergeMap';
import { takeUntil } from 'rxjs/operators/takeUntil';
import { Subject } from 'rxjs/Subject';

import { ModalService } from '../core-ui/services/modal.service';
import { UserService } from '../core/services/user.service';
import { AclService } from '../services/acl.service';
import { ActivityObserver } from '../utils/activity-observer';

import { BinaryDataset } from './dataset.interfaces';
import { BinaryDatasetService } from './dataset.service';

@Component({
  selector: 'dataset-file-operations',
  template: `
    <div class="row row-flex pt5 pb5" style="align-items: flex-end;">
      <div class="col-xs-12 flex-static">
        <ul class="asset-btn-panel nav nav-pills">
          <li class="nav-item" [ngClass]="{'disabled': disabledTrash}">
            <a class="nav-link link-colorless" (click)="disabledTrash || trash()">
              <i class="imgaction imgaction-trash center-block"></i>
              <div>Trash</div>
            </a>
          </li>
          <li class="nav-item" [ngClass]="{'disabled': disabledDownload || (downloadObserver.active | async)}">
            <app-spinner
              [visibility]="downloadObserver.active | async"
              [height]="50"
            ></app-spinner>
            <a class="nav-link link-colorless" (click)="disabledDownload || downloadObserver.isActive || download()">
              <i class="imgaction imgaction-download center-block"></i>
              <div>download</div>
            </a>
          </li>
        </ul>
      </div>
      <div class="col-xs-12 flex-rubber visible-dropdown">
        <ng-content></ng-content>
      </div>
    </div>
  `,
})
export class DatasetFileOperationsComponent implements OnChanges, OnDestroy {
  @Input() dataset: BinaryDataset;
  @Input() selectedItems: BinaryDataset.File[] = [];

  @Output() selectedItemsChange = new EventEmitter<BinaryDataset.File[]>();

  disabledTrash: boolean = false;
  disabledDownload: boolean = true;

  protected readonly downloadObserver = new ActivityObserver();

  private readonly _destroy$ = new Subject<void>();

  constructor(
    private datasetService: BinaryDatasetService,
    private acl: AclService,
    private modals: ModalService,
    private userService: UserService,
  ) {
  }

  ngOnDestroy(): void {
    this._destroy$.next();
    this._destroy$.complete();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['selectedItems']) {
      this.selectedItems = this.selectedItems.filter(_ => _);
      let readOnly = !this.acl.canUpdateBinaryDataset(this.dataset);
      this.disabledTrash = this.selectedItems.length < 1 || readOnly;
      this.disabledDownload = this.selectedItems.length < 1;
    }
  }

  trash(): void {
    this.modals.confirm('Are you sure you want to delete the files selected?')
      .pipe(
        takeUntil(this._destroy$),
        filter(_ => _),
        flatMap(() => {
          const observables: Observable<void>[] = this.selectedItems
            .map((file: BinaryDataset.File): Observable<void> => {
              return this.datasetService.deleteFile(this.dataset.id, file.filename);
            });

          this.resetSelection();

          return Observable.forkJoin(observables);
        }),
      )
      .subscribe();
  }

  resetSelection(): void {
    this.selectedItems = [];
    this.selectedItemsChange.emit([]);
  }

  download(): void {
    if (this.selectedItems.length > 1) {
      this.downloadObserver.observe(this.datasetService.downloadSelectedDatasetFiles(
        this.dataset.id,
        this.selectedItems.map(item => item.filename),
        this.userService.token(),
      ));
    } else if (this.selectedItems.length === 1) {
      this.downloadObserver.observe(this.datasetService.downloadFile(
        this.dataset.id,
        this.selectedItems[0].filepath,
      ));
    } else {
      throw new Error('Nothing is selected');
    }
  }
}


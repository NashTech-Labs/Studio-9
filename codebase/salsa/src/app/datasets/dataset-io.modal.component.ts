import { OnDestroy, ViewChild } from '@angular/core';
import { AbstractControl, FormControl, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import { Observable } from 'rxjs/Observable';
import { takeUntil } from 'rxjs/operators/takeUntil';
import { tap } from 'rxjs/operators/tap';
import { Subject } from 'rxjs/Subject';

import config from '../config';
import { AppSelectOptionData } from '../core-ui/components/app-select.component';
import { ModalComponent } from '../core-ui/components/modal.component';
import { IBackendList, TObjectId } from '../core/interfaces/common.interface';
import { ActivityObserver } from '../utils/activity-observer';
import { AppFormGroup, EntityControlDeep } from '../utils/forms';
import { ReactiveLoader } from '../utils/reactive-loader';
import { AppValidators } from '../utils/validators';

import { BinaryDataset } from './dataset.interfaces';
import { BinaryDatasetService } from './dataset.service';

export const ZERO_ID = '-1';

export abstract class DatasetIOModalComponent implements OnDestroy {
  readonly config = config;
  datasetOptions: AppSelectOptionData[];
  datasetId: TObjectId = ZERO_ID;
  datasetIdLocked: boolean = false;
  readonly _savingObserver = new ActivityObserver();

  @ViewChild('modal') protected modal: ModalComponent;
  protected abstract form: AbstractControl;

  private datasetsLoader: ReactiveLoader<IBackendList<BinaryDataset>, any>;
  private readonly _destroy$: Subject<boolean> = new Subject<boolean>();

  protected constructor(
    protected datasetService: BinaryDatasetService,
    protected router: Router,
  ) {
    this.datasetsLoader = new ReactiveLoader(() => this._loadList());
    this.datasetsLoader.value
      .pipe(takeUntil(this._destroy$))
      .subscribe(_ => this._onDatasetsLoaded(_));

    this._prepareForm();

    this.setDatasetId(ZERO_ID);
  }

  setDatasetId(datasetId: TObjectId): void {
    this._setDatasetId(datasetId);
    this.datasetIdLocked = this.datasetId !== ZERO_ID;
  }

  ngOnDestroy(): void {
    this._destroy$.next(true);
    this._destroy$.complete();
  }

  protected _open(datasetId: TObjectId): Observable<void> {
    this.datasetOptions = null;
    this.datasetsLoader.load();
    this.form.reset();
    this.setDatasetId(datasetId);

    return this.modal.show();
  }

  protected abstract _prepareForm(): void;

  protected _prepareS3Form(): EntityControlDeep<BinaryDataset.S3IOReference> {
    const bucketIdControl = new FormControl(null);
    const regionControl = new FormControl(null);
    const bucketNameControl = new FormControl(null);
    const accessKeyControl = new FormControl(null);
    const secretKeyControl = new FormControl(null);
    const sessionTokenControl = new FormControl(null);

    const form = new AppFormGroup({
      s3Bucket: new AppFormGroup({
        AWSS3BucketId: bucketIdControl,
        AWSRegion: regionControl,
        AWSS3BucketName: bucketNameControl,
        AWSAccessKey: accessKeyControl,
        AWSSecretKey: secretKeyControl,
        AWSSessionToken: sessionTokenControl,
      }),
      path: new FormControl(null, Validators.required),
    });

    // s3 credentials
    AppValidators.crossValidate(
      bucketIdControl,
      [regionControl, bucketNameControl, accessKeyControl, secretKeyControl],
      (bucketId: string) => {
        return (!bucketId)
          ? Validators.required
          : Validators.nullValidator;
      },
    );

    return form;
  }

  protected _onDatasetsLoaded(datasetList: IBackendList<BinaryDataset>): void {
    this.datasetOptions = datasetList.data.map(dataset => {
      return {
        id: dataset.id,
        text: dataset.name,
        disabled: dataset.status && dataset.status !== BinaryDataset.Status.IDLE,
      };
    });
  }

  protected _loadList(): Observable<IBackendList<BinaryDataset>> {
    return this.datasetService.list({ page_size: 1000 });
  }

  protected _setDatasetId(datasetId: TObjectId): void {
    this.datasetId = datasetId || ZERO_ID;
  }

  protected action() {
    this._savingObserver.observe(this._action())
      .pipe(
        takeUntil(this._destroy$),
        tap((datasetId: TObjectId) => {
          this.modal.hide();
          this.router.navigate(['/desk', 'library', 'datasets', 'all', datasetId]);
        }),
      )
      .subscribe();
  }

  protected abstract _action(): Observable<TObjectId>;
}

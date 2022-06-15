import { Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output, SimpleChanges } from '@angular/core';
import { FormControl, Validators } from '@angular/forms';

import { ISubscription } from 'rxjs/Subscription';

import { AppSelectOptionData } from '../core-ui/components/app-select.component';
import { TObjectId } from '../core/interfaces/common.interface';
import { S3BucketService } from '../core/services/s3bucket.service';
import { ICVModel } from '../train/cv-model.interface';
import { CVModelService } from '../train/cv-model.service';
import { AppFormGroup } from '../utils/forms';
import { MiscUtils } from '../utils/misc';
import { ReactiveLoader } from '../utils/reactive-loader';

import { IOnlineTriggeredJob } from './online-job.interface';

@Component({
  selector: 'deploy-online-job-cv-options',
  template: `
    <app-spinner [visibility]="!bucketOptions"></app-spinner>
    <div class="row">
      <div class="col-md-6">
        <app-select
          [disabled]="!bucketOptions || !bucketOptions.length"
          [label]="'Input Bucket'"
          [control]="form.controls.inputBucketId"
          [options]="bucketOptions"
        ></app-select>
      </div>
      <div class="col-md-6">
        <app-input [label]="'Images Path'" [control]="form.controls.inputImagesPath"></app-input>
      </div>
    </div>
    <app-input *ngIf="!options"
      [label]="'Output Album Name'" [control]="form.controls.outputAlbumName"></app-input>

    <app-tabs [tabs]="options ? ['Model Info', 'Output'] : ['Model Info']" [(active)]="activeTab" ></app-tabs>
    <div class="tab-content brand-tab">
      <div role="tabpanel" class="tab-pane" [ngClass]="{'active': activeTab === 0}">
        <div class="panel">
          <div class="panel-body">
            <cv-model-view-embed
              [model]="model"
            ></cv-model-view-embed>
          </div>
        </div>
      </div>
      <div *ngIf="options" role="tabpanel" class="tab-pane" [ngClass]="{'active': activeTab === 1}">
        <album-view-embed
          [albumId]="options?.outputAlbumId"
          [poll]="true"
        ></album-view-embed>
      </div>
    </div>

  `,
})
export class OnlineJobCVOptionsComponent implements OnChanges, OnDestroy, OnInit {
  @Input() modelId: TObjectId;
  @Input() disabled: boolean = false;
  @Input() options: IOnlineTriggeredJob.CVModelOptions;
  @Output() valueChanges = new EventEmitter<[IOnlineTriggeredJob.CVModelCreateOptions, boolean]>();

  readonly form: AppFormGroup<{
    inputBucketId: FormControl;
    inputImagesPath: FormControl;
    outputAlbumName: FormControl;
  }>;
  readonly loader: ReactiveLoader<ICVModel, TObjectId>;
  bucketOptions: AppSelectOptionData[];

  protected model: ICVModel;
  protected activeTab = 0;
  private formSubscription: ISubscription;

  constructor(
    private bucketService: S3BucketService,
    private cvModels: CVModelService,
  ) {
    this.loader = new ReactiveLoader<ICVModel, TObjectId>(id => this.cvModels.get(id));

    this.loader.subscribe(model => {
      this.model = model;
    });

    this.bucketService.list().subscribe(buckets => {
      this.bucketOptions = buckets.data.map(_ => ({
        id: _.id,
        text: _.name,
      }));
    }, () => {
      this.bucketOptions = [];
    });

    this.form = new AppFormGroup({
      inputBucketId: new FormControl(null, Validators.required),
      inputImagesPath: new FormControl(null, Validators.required),
      outputAlbumName: new FormControl(null, Validators.required),
    });
  }

  ngOnInit() {
    this.formSubscription = this.form.valueChanges.subscribe(value => {
      this.valueChanges.emit([
        {type: IOnlineTriggeredJob.JobType.ONLINE_CV_PREDICTION, ...value},
        this.form.valid,
      ]);
    });
  }

  ngOnChanges(changes: SimpleChanges) {
    if (this.options) {
      MiscUtils.fillForm(this.form, this.options, this.disabled);
    }
    if ('modelId' in changes) {
      if (this.modelId) {
        this.loader.load(this.modelId);
      } else {
        this.model = undefined;
      }
    }
  }

  ngOnDestroy() {
    this.formSubscription && this.formSubscription.unsubscribe();
  }
}

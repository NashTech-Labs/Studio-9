import { Component, Input } from '@angular/core';
import { FormGroup } from '@angular/forms';

import { AppSelectOptionData } from '../../core-ui/components/app-select.component';
import { EntityControlDeep } from '../../utils/forms';
import { S3BucketReference } from '../core.interface';
import { S3BucketService } from '../services/s3bucket.service';

@Component({
  selector: 's3-bucket-form',
  template: `
    <app-spinner [visibility]="!buckets || !_awsRegions"></app-spinner>
    <ng-template [ngIf]="buckets && _awsRegions">
      <app-select
        [label]="'AWS S3 Bucket'"
        [control]="form.controls['AWSS3BucketId']"
        [options]="buckets"
        [allowNull]="true"
        [nullSelectedText]="'Custom'"
      ></app-select>
      <ng-template [ngIf]="!form.controls['AWSS3BucketId'].value">
        <app-input
          [label]="'AWS S3 Bucket Name'"
          [control]="form.controls['AWSS3BucketName']"
        ></app-input>
        <app-select
          [label]="'AWS Region'"
          [options]="_awsRegions"
          [control]="form.controls['AWSRegion']"
        ></app-select>
        <app-form-group [caption]="'AWS Access Credentials'">
          <p class="text-muted">
            We need AWS credentials to be able to pull from your bucket. Please give us at least 5 hours of
            expiration
            time.
          </p>
          <div class="alert alert-warning">
            <strong>WARNING!</strong>
            AWS Access Key, AWS Secret Key and AWS Session Token should be TEMPORARY credentials, we DON'T NEED
            YOUR
            MAIN CREDENTIALS.
            See <a target="_blank"
            href="http://docs.aws.amazon.com/IAM/latest/UserGuide/id_credentials_temp.html">link</a>
            for more
            information.
          </div>
          <app-input [label]="'AWS Access Key'" [control]="form.controls['AWSAccessKey']"></app-input>
          <app-input [label]="'AWS Secret Key'" [control]="form.controls['AWSSecretKey']"></app-input>
          <div class="form-group">
      <textarea [formControl]="form.controls['AWSSessionToken']"
        class="form-control"
        [placeholder]="'AWS Session Token'" style="resize: vertical"></textarea>
          </div>
        </app-form-group>
      </ng-template>
    </ng-template>
  `,
})
export class S3BucketFormComponent {
  @Input() form: FormGroup | EntityControlDeep<S3BucketReference.Custom & S3BucketReference.Predefined>;

  buckets: AppSelectOptionData[];

  _awsRegions: string[];

  constructor(
    private service: S3BucketService,
  ) {
    this.service.list().subscribe(buckets => {
      this.buckets = buckets.data.map(_ => ({
        id: _.id,
        text: _.name,
      }));
    }, () => {
      this.buckets = [];
    });
    this.service.listAWSRegions().subscribe(regions => {
      this._awsRegions = regions;
    });
  }
}

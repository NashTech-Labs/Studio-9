import { Component, ViewChild } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';

import 'rxjs/add/observable/merge';
import 'rxjs/add/observable/of';
import 'rxjs/add/operator/reduce';
import { Observable } from 'rxjs/Observable';

import config from '../../config';
import { ModalComponent } from '../../core-ui/components/modal.component';
import { ActivityObserver } from '../../utils/activity-observer';
import { ReactiveLoader } from '../../utils/reactive-loader';
import { AppValidators } from '../../utils/validators';
import { IAsset } from '../interfaces/common.interface';
import { ISharedResource } from '../interfaces/shared-resource.interface';
import { IUser, IUsersMap } from '../interfaces/user.interface';
import { SharedResourceService } from '../services/shared-resource.service';

type ShareRecipientsMap = IUsersMap;

@Component({
  selector: 'share-modal',
  template: `
    <app-modal #modal [captionPrefix]="'Share ' + config.asset.labels[assetType]"
      [caption]="(assets.length === 1 ) ? assets[0]?.name : 'Multiple Items'">
      <app-tabs [tabs]="['New recipient', 'Existing recipients']" [(active)]="activeTab"></app-tabs>
      <div class="tab-content">
        <div class="tab-pane" [ngClass]="{ 'active': activeTab === 0 }">
          <div class="panel">
            <div class="panel-body">
              <form [formGroup]="createForm" (ngSubmit)="createForm.valid && submitCreateForm()">
                <div class="form-group">
                  <p>Type users email you would like to share this asset with.</p>
                  <app-input [label]="'Email'" [control]="createForm['controls'].recipientEmail" [iconAfter]="'glyphicon-envelope'"></app-input>
                </div>
                <div class="form-group">
                  <button [disabled]="!createForm.valid || (_savingObserver.active | async)"
                    type="submit"
                    class="btn btn-primary pull-right">
                    Share&nbsp;<i class="glyphicon glyphicon-ok"></i>
                  </button>
                </div>
              </form>
            </div>
          </div>
        </div>
        <div class="tab-pane" [ngClass]="{ 'active': activeTab === 1 }">
          <div class="panel">
            <div class="panel-body" *ngIf="assets.length > 1">
              Existing recipients can not be shown for multiple assets selection
            </div>
            <div class="panel-body" *ngIf="assets.length === 1">
              <div class="form-group">
                <p>Below is a list of people you have shared this asset with. You may edit their access to your asset, share with additional users or unshare your asset all together.</p>
              </div>
              <div class="form-group" style="position: relative; min-height: 100px;"> <!-- place for spinner -->
                <app-spinner [visibility]="!sharesLoader.loaded || !recipientsLoader.loaded"></app-spinner>
                <!-- START LIST -->
                <div *ngIf="sharesLoader.loaded && recipientsLoader.loaded && shares.length"
                  class="row table-responsive table-responsive-overflow table-scroll">
                  <table class="table table-borderless-top">
                    <thead class="bg-faded">
                      <tr>
                        <th>Email</th>
                        <th>Name</th>
                        <th>Updated</th>
                        <th>Revoke</th>
                      </tr>
                    </thead>
                    <tbody>
                      <!-- from common tables --><!-- tables from flow -->
                      <tr *ngFor="let item of shares">
                        <td>
                          {{recipients[item.recipientId]?.email || item.recipientEmail}}
                        </td>
                        <td *ngIf="item.recipientId">
                          {{recipients[item.recipientId]?.firstName}}
                          {{recipients[item.recipientId]?.lastName}}
                        </td>
                        <td *ngIf="!item.recipientId">
                          Unknown
                        </td>
                        <td>
                          <div class="text-muted">{{item.updated | dateAgo | async}}</div>
                        </td>
                        <td>
                          <a class="btn btn-sm" (click)="removeShare(item)"><i class="glyphicon glyphicon-remove"></i></a>
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </div>
                <div *ngIf="sharesLoader.loaded && recipientsLoader.loaded && !shares.length">There are no recipients</div>
                <!-- END LIST -->
              </div>
            </div>
          </div>
        </div>
      </div>
    </app-modal>
  `,
})
export class ShareModalComponent {
  config = config;
  assetType: IAsset.Type;
  assets: IAsset[] = [];
  readonly _savingObserver = new ActivityObserver();
  activeTab: number = 0;
  createForm: FormGroup;
  shares: ISharedResource[];
  recipients: ShareRecipientsMap = {};
  readonly sharesLoader: ReactiveLoader<ISharedResource[], any>;
  readonly recipientsLoader: ReactiveLoader<ShareRecipientsMap, any>;
  @ViewChild('modal') private modal: ModalComponent;

  constructor(
    private shareService: SharedResourceService,
  ) {
    this.createForm = new FormGroup({
      recipientEmail: new FormControl('', [Validators.required, AppValidators.email]),
    });

    // shares list loader
    this.sharesLoader = new ReactiveLoader(_ => this._loadShares());
    this.sharesLoader.subscribe(_ => {
      this.shares = _;
      this.recipientsLoader.load();
    });

    this.recipientsLoader = new ReactiveLoader(_ => this._loadRecipients());
    this.recipientsLoader.subscribe(_ => {
      Object.assign(this.recipients, _);
    });
  }

  open(assetType: IAsset.Type, assets: IAsset[]) {
    this.assetType = assetType;
    this.assets = assets;
    this.activeTab = 0;
    this.sharesLoader.load();
    this.modal.show();
  }

  submitCreateForm() {
    this._savingObserver.observe(this.shareService.bulkShare<IAsset>(this.assetType, this.assets, this.createForm.controls['recipientEmail'].value)).subscribe(() => {
      this.createForm.reset({ recipientEmail: '' });
      this.activeTab = 1;
      this.sharesLoader.load();
    });
  }

  removeShare(item: ISharedResource) {
    this.shareService.delete(item).subscribe(() => {
      this.sharesLoader.load();
    });
  }

  private _loadShares(): Observable<ISharedResource[]> {
    if (this.assets.length === 1) {
      return this.shareService.list(Object.assign({}, {
        'asset_type': this.assetType,
        'asset_id': this.assets[0].id,
      })).map(_ => _.data);
    } else {
      return Observable.of([]);
    }
  }

  private _loadRecipients(): Observable<ShareRecipientsMap> {
    const observables = this.shares
      .filter(_ => {
        return _.recipientId && !this.recipients.hasOwnProperty(_.recipientId);
      })
      .map((share: ISharedResource): Observable<IUser> => {
        return this.shareService.recipient(share.id);
      });

    if (!observables.length) {
      return Observable.of({});
    }

    return Observable.merge(...observables).reduce((acc, item: IUser) => {
      acc[item.id] = item;
      return acc;
    }, {});
  }
}

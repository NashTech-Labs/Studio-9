import { Component, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges, ViewChild } from '@angular/core';

import { Subscription } from 'rxjs/Subscription';

import { UserService } from '../core/services/user.service';
import { bulkAction } from '../utils/observable';

import { UserDeleteModalComponent } from './user-delete-modal';
import { UserManagementService } from './user-management.service';
import { IUMUser, UserStatus } from './user.interfaces';

@Component({
  selector: 'user-operations',
  template: `
    <div class="operations-toolbar row row-flex pt5 pb5" style="align-items: flex-end; flex-wrap: wrap;">
      <div class="col-xs-12 flex-static">
        <!-- Common Buttons -->
        <ul class="asset-btn-panel nav nav-pills">
          <li class="nav-item"
            [ngClass]="{'disabled': disabledEdit}"
            [routerLinkActive]="['active']"
            [routerLinkActiveOptions]="{exact: true}"
            [ngSwitch]="selectedItems.length"
          >
            <a *ngSwitchCase="1" class="nav-link link-colorless"
              [routerLink]="editRoute()"
            >
              <i class="imgaction glyphicon glyphicon-pencil center-block"></i>
              <div>edit</div>
            </a>
            <a *ngSwitchDefault="" class="nav-link link-colorless">
              <i class="imgaction glyphicon glyphicon-pencil center-block"></i>
              <div>edit</div>
            </a>
          </li>
          <li class="nav-item"
            [ngClass]="{'disabled': disabledTrash}"
          >
            <a class="nav-link link-colorless"
              (click)="disabledTrash || trash()"
            >
              <i class="imgaction imgaction-trash center-block"></i>
              <div>Trash</div>
            </a>
          </li>
          <li class="nav-item"
            [ngClass]="{'disabled': disabledActivate}"
          >
            <a class="nav-link link-colorless"
              (click)="disabledActivate || activate()"
            >
              <i class="imgaction glyphicon glyphicon-ok center-block"></i>
              <div>Activate</div>
            </a>
          </li>
          <li class="nav-item"
            [ngClass]="{'disabled': disabledDeactivate}"
          >
            <a class="nav-link link-colorless"
              style="text-align: center"
              (click)="disabledDeactivate || deactivate()"
            >
              <i class="imgaction glyphicon glyphicon-ban-circle center-block"></i>
              <div>Deactivate</div>
            </a>
          </li>
        </ul>
        <!-- End Common Buttons -->
      </div>
      <div class="col-xs-12 flex-rubber visible-dropdown">
        <ng-content></ng-content>
      </div>
    </div>

    <user-delete-modal #confirmDeleteModal
      [selectedItems]="selectedItems"
      (deleted)="onDeleted($event)"
    ></user-delete-modal>
  `,
})
export class UserOperationsComponent implements OnChanges, OnDestroy {
  @Input() selectedItems: IUMUser[] = [];
  @Output() selectedItemsChange = new EventEmitter<IUMUser[]>();
  @Output() deleted = new EventEmitter<IUMUser[]>();
  @Output() activated = new EventEmitter<IUMUser[]>();
  @Output() deactivated = new EventEmitter<IUMUser[]>();

  disabledEdit: boolean = false;
  disabledTrash: boolean = false;
  disabledActivate: boolean = false;
  disabledDeactivate: boolean = false;

  @ViewChild('confirmDeleteModal') confirmDeleteModal: UserDeleteModalComponent;

  private _subscriptions: Subscription[] = [];

  constructor(
    public umService: UserManagementService,
    public userService: UserService,
  ) {
    this._updateButtonsAvailability();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['selectedItems'] && changes['selectedItems'].currentValue !== changes['selectedItems'].previousValue) {
      this._updateButtonsAvailability();
    }
  }

  ngOnDestroy(): void {
    this._subscriptions.forEach(_ => _.unsubscribe());
  }

  onDeleted(items: IUMUser[]): void {
    this.resetSelection();
    this.deleted.emit(items);
  }

  resetSelection(): void {
    this.selectedItemsChange.emit([]);
  }

  editRoute(): string[] {
    return ['/desk', 'users', 'manage', this.selectedItems[0].id];
  }

  trash(): void {
    this.confirmDeleteModal.show();
  }

  activate(): void {
    const observables = this.selectedItems
      .filter(_ => _.status !== UserStatus.ACTIVE)
      .map(_ => this.umService.activateUser(_));

    const subscription = bulkAction(observables).subscribe(items => {
      this.resetSelection();
      this.activated.emit(items.filter(_ => !!_));
    });
    this._subscriptions.push(subscription);
  }

  deactivate(): void {
    const observables = this.selectedItems
      .filter(_ => _.status === UserStatus.ACTIVE)
      .map(_ => this.umService.deactivateUser(_));

    const subscription = bulkAction(observables).subscribe(items => {
      this.resetSelection();
      this.deactivated.emit(items.filter(_ => !!_));
    });
    this._subscriptions.push(subscription);
  }

  private _updateButtonsAvailability(): void {
    const actionsDisabled = this.selectedItems.some(_ => {
      return this.userService.getUser().id === _.id;
    }) || !this.selectedItems.length;
    this.disabledEdit = this.selectedItems.length !== 1;
    this.disabledTrash = actionsDisabled;
    this.disabledActivate = actionsDisabled || this.selectedItems.some(_ => _.status === UserStatus.ACTIVE);
    this.disabledDeactivate = actionsDisabled || this.selectedItems.some(_ => _.status !== UserStatus.ACTIVE);
  }
}

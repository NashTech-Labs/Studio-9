import { Component, EventEmitter, Input, OnDestroy, Output, ViewChild } from '@angular/core';
import { FormGroup } from '@angular/forms';

import 'rxjs/add/operator/shareReplay';
import { Observable } from 'rxjs/Observable';
import { Subscription } from 'rxjs/Subscription';

import { AppSelectOptionData } from '../core-ui/components/app-select.component';
import { IModalButton } from '../core-ui/components/modal.component';
import { TObjectId } from '../core/interfaces/common.interface';
import { bulkAction } from '../utils/observable';

import { UserManagementService } from './user-management.service';
import { IUMUser } from './user.interfaces';

interface SearchableUser {
  id: TObjectId;
  displayName: string;
  user: IUMUser;
}

@Component({
  selector: 'user-delete-modal',
  template: `
    <app-modal #confirmDeleteModal
      [captionPrefix]="'Delete user' + (selectedItems.length > 1 ? 's' : '')"
      [caption]="selectedItems.length === 1 ? umService.getUserInformation(selectedItems[0]) : ''"
      [buttons]="[
        {class: 'btn-clear', title: 'Cancel', id: 'cancel'},
        {class: 'btn-apply', title: 'Confirm', id: 'confirm'}
      ]"
      (buttonClick)="onConfirmDelete(selectedItems, $event)"
    >
      <ng-container *ngIf="selectedItems.length === 1">
        <p>Are you sure you want to delete user: <strong>{{umService.getUserInformation(selectedItems[0])}}</strong></p>
      </ng-container>

      <ng-container *ngIf="selectedItems.length > 1">
        <p>Are you sure you want to delete these users:</p>
        <ul>
          <li *ngFor="let item of selectedItems"><strong>{{umService.getUserInformation(item)}}</strong></li>
        </ul>
      </ng-container>
      <ng-container>
        <app-spinner [visibility]="!userOptions.length"></app-spinner>
        <app-select
          [label]="'Transfer Ownership To'"
          [options]="userOptions | apply: prepareUserOptions: selectedItems"
          (valueChange)="onSelect($event)"
        >
        </app-select>
      </ng-container>
    </app-modal>
  `,
})
export class UserDeleteModalComponent implements OnDestroy {
  @Input() selectedItems: IUMUser[] = [];
  @ViewChild('confirmDeleteModal') confirmDeleteModal: UserDeleteModalComponent;
  @Output() deleted = new EventEmitter<IUMUser[]>();

  form: FormGroup;
  transferTo: SearchableUser;
  userOptions: SearchableUser[] = [];

  private _users: Observable<SearchableUser[]>;
  private _subscriptions: Subscription[] = [];

  constructor(
    public umService: UserManagementService,
  ) {
    this.getUsers().subscribe(_ => {
        this.userOptions = _;
    });
  }

  ngOnDestroy(): void {
    this._subscriptions.forEach(_ => _.unsubscribe());
  }

  getUsers(): Observable<SearchableUser[]> {
    if (!this._users) {
      this._users = this.umService
        .listAll()
        .map(list => list.map(_ => {
          return {
            id: _.id,
            displayName: this.umService.getUserInformation(_),
            user: _,
          };
        }))
        .shareReplay(1);
    }
    return this._users;
  }

  onSelect(match: SearchableUser) {
    this.transferTo = match;
  }

  prepareUserOptions = (users: SearchableUser[], selectedItems: IUMUser[]): AppSelectOptionData[] => {
    const nonSelectedUsers: SearchableUser[] = (users || [])
      .filter(_ => !selectedItems.find(selectedItem => _.id === selectedItem.id));
    return nonSelectedUsers.map(filteredUser => ({
      id: filteredUser.id,
      text: filteredUser.displayName,
    }));
  };

  onConfirmDelete(items: IUMUser[], button: IModalButton): void {
    if (button.id === 'confirm') {
      const observables = items.map(_ => {
        return this.umService.delete(_, this.transferTo ? this.transferTo.user : null);
      });
      const subscription = bulkAction(observables)
        .subscribe(deleteResults => {
          const actuallyDeletedIds = deleteResults.filter(_ => !!_).map(_ => _.id);
          this.deleted.emit(items.filter(_ => actuallyDeletedIds.includes(_.id)));
        });
      this._subscriptions.push(subscription);
    }
    this.hide();
  }

  show() {
    this._reset();
    this.confirmDeleteModal.show();
  }

  hide() {
    this.confirmDeleteModal.hide();
  }

  private _reset() {
    this.transferTo = null;
  }
}

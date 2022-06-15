import {
  Component,
  EventEmitter,
  Input,
  OnChanges,
  OnDestroy,
  Output,
  SimpleChanges,
  ViewChild,
} from '@angular/core';

import * as lodash from 'lodash';
import { Subscription } from 'rxjs/Subscription';

import {
  IModalButton,
  ModalComponent,
} from '../core-ui/components/modal.component';
import { TObjectId } from '../core/interfaces/common.interface';
import { UserService } from '../core/services/user.service';
import { bulkAction } from '../utils/observable';

import { IPackage } from './package.interfaces';
import { PackageService } from './package.service';

@Component({
  selector: 'package-operations',
  template: `
    <div class="operations-toolbar row row-flex pt5 pb5" style="align-items: flex-end; flex-wrap: wrap;">
      <div class="col-xs-12 flex-static">
        <!-- Common Buttons -->
        <ul class="asset-btn-panel nav nav-pills">
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
        </ul>
        <!-- End Common Buttons -->
      </div>
      <div class="col-xs-12 flex-rubber visible-dropdown">
        <ng-content></ng-content>
      </div>
    </div>

    <app-modal #confirmDeleteModal
      [captionPrefix]="'Delete package' + (selectedItems.length > 1 ? 's' : '')"
      [caption]="selectedItems.length === 1 ? selectedItems[0].name : ''"
      [buttons]="[
        {class: 'btn-clear', title: 'Cancel', id: 'cancel'},
        {class: 'btn-apply', title: 'Confirm', id: 'confirm'}
      ]"
      (buttonClick)="onConfirmDelete(selectedItems, $event)"
    >
      <ng-container *ngIf="selectedItems.length === 1">
        <p>Are you sure you want to delete S9 Project Package: <strong>{{(selectedItems[0].name)}} {{selectedItems[0].version ? '(' + selectedItems[0].version + ')' : ''}}</strong></p>
      </ng-container>

      <ng-container *ngIf="selectedItems.length > 1">
        <p>Are you sure you want to delete these S9 Project Packages:</p>
        <ul>
          <li *ngFor="let item of selectedItemsSorted"><strong>{{item.name}} {{item.version ? '(' + item.version + ')' : ''}} </strong></li>
        </ul>
      </ng-container>
    </app-modal>
  `,
})
export class PackageOperationsComponent implements OnChanges, OnDestroy {
  @Input() selectedItems: IPackage[] = [];
  @Output() selectedItemsChange = new EventEmitter<IPackage[]>();
  @Output() deleted = new EventEmitter<IPackage[]>();

  disabledTrash: boolean = false;
  selectedItemsSorted: IPackage[] = [];

  @ViewChild('confirmDeleteModal') confirmDeleteModal: ModalComponent;

  private _subscriptions: Subscription[] = [];

  constructor(
    public packageService: PackageService,
    public userService: UserService,
  ) {
    this._updateButtonsAvailability();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['selectedItems'] && changes['selectedItems'].currentValue !== changes['selectedItems'].previousValue) {
      this.selectedItemsSorted = lodash.sortBy(this.selectedItems, 'name');
      this._updateButtonsAvailability();
    }
  }

  ngOnDestroy(): void {
    this._subscriptions.forEach(_ => _.unsubscribe());
  }

  onDeleted(items: IPackage[]): void {
    this.resetSelection();
    this.deleted.emit(items);
  }

  resetSelection(): void {
    this.selectedItemsChange.emit([]);
  }

  trash(): void {
    this.confirmDeleteModal.show();
  }

  onConfirmDelete(items: IPackage[], button: IModalButton): void {
    if (button.id === 'confirm') {
      const observables = items.map(_ => this.packageService.delete(_));
      const subscription = bulkAction(observables)
        .subscribe(deletedIds => {
          const actuallyDeletedIds: TObjectId[] = deletedIds.filter(_ => !!_).map(_ => _.id);
          this.onDeleted(items.filter(_ => actuallyDeletedIds.includes(_.id)));
        });
      this._subscriptions.push(subscription);
    }
    this.confirmDeleteModal.hide();
  }

  private _updateButtonsAvailability(): void {
    const myUserId = this.userService.getUser().id;
    this.disabledTrash = this.selectedItems.length < 1
      || !!this.selectedItems.find(_ => _.isPublished || _.ownerId !== myUserId);
  }
}

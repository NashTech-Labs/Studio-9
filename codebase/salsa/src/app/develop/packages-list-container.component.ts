import { Component, OnDestroy, ViewChild } from '@angular/core';
import { FormControl } from '@angular/forms';

import { Subscription } from 'rxjs/Subscription';

import config from '../config';
import { UserService } from '../core/services/user.service';
import { AppFormGroup } from '../utils/forms';

import { PackageOperationsComponent } from './package-operations.component';
import { IPackage, IPackageSearchParams } from './package.interfaces';


@Component({
  selector: 'packages-list-container',
  template: `
    <package-operations
      #operations
      [(selectedItems)]="selectedItems"
      class="app-spinner-box"
    >
      <div *ngIf="searchForm" class="pt15">
        <form class="form">
          <div class="row">
            <div class="col-md-9 col-lg-9">
              <app-input
                [control]="searchForm.controls.name"
                [iconBefore]="'glyphicon-search'"
                [iconAfter]="'glyphicon-remove'"
                (iconAfterClick)="searchForm.controls.name.setValue('')"
              ></app-input>
            </div>
            <div class="col-md-3 col-lg-3">
              <app-check
                label="My packages"
                class="my-package"
                helpText="Show only my packages"
                [control]="searchForm.controls['showOnlyMyPackages']"
              ></app-check>
            </div>
          </div>
        </form>
      </div>
    </package-operations>
    <packages-list
      [(selectedItems)]="selectedItems"
      [searchParams]="searchParams"
      (onDelete)="onDeletePackage()"
    ></packages-list>
  `,
})
export class PackagesListContainerComponent implements OnDestroy {
  selectedItems: IPackage[] = [];
  searchParams: IPackageSearchParams = {};
  config = config;
  searchForm: AppFormGroup<{
    name: FormControl,
    showOnlyMyPackages: FormControl,
  }>;

  @ViewChild('operations') operationsBar: PackageOperationsComponent;

  private _subscription = new Subscription();

  constructor(
    private users: UserService,
  ) {
    this.searchForm = new AppFormGroup({
      name: new FormControl(''),
      showOnlyMyPackages: new FormControl(false),
    });
    this.onSearchModeChange();

    this._subscription.add(
      this.searchForm.valueChanges
        .debounceTime(100)
        .subscribe(() => {
          if (this.searchForm.valid) {
            this.searchParams = {
              search: this.searchForm.value['name'],
              ownerId: this.searchForm.value['showOnlyMyPackages'] ? this.users.getUser().id : null,
            };
          }
        }),
    );
  }

  ngOnDestroy(): void {
    this._subscription.unsubscribe();
  }

  onSearchModeChange(): void {
    this.searchForm.controls.name.reset('');
  }

  onDeletePackage(): void {
    this.operationsBar.trash();
  }
}

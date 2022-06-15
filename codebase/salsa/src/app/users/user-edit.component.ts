import { Component, HostListener, OnDestroy, OnInit } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Params, Router } from '@angular/router';

import { Observable } from 'rxjs/Observable';
import { Subscription } from 'rxjs/Subscription';

import config from '../config';
import { AppSelectOptionData } from '../core-ui/components/app-select.component';
import { HasUnsavedData } from '../core/core.interface';
import { TObjectId } from '../core/interfaces/common.interface';
import { UserService } from '../core/services/user.service';
import { ActivityObserver } from '../utils/activity-observer';
import { ReactiveLoader } from '../utils/reactive-loader';
import { AppValidators } from '../utils/validators';

import { UserManagementService } from './user-management.service';
import { IUMUser, IUserCreateRequest, IUserUpdateRequest, UserRole, UserStatus } from './user.interfaces';

interface FormLoaderData {
  user?: IUMUser;
}

@Component({
  selector: 'user-edit',
  template: `
    <app-spinner [visibility]="loader.active | async"></app-spinner>

    <div *ngIf="!(loader.active | async)">
      <user-operations
        *ngIf="user"
        [selectedItems]="user ? [user] : []"
        (deleted)="onDeleted()"
        (activated)="onActivated()"
        (deactivated)="onDeactivated()"
      ></user-operations>

      <h3 class="text-center" *ngIf="!user">Create User</h3>

      <form [formGroup]="form" (ngSubmit)="form.valid && onSubmit()">
        <div class="row">
          <div class="col-md-12">
            <dl class="user-details dl-horizontal aligned-left" *ngIf="user">
              <dt>Status:</dt>
              <dd>
                <span [ngClass]="user | apply: _getItemStatusClass">
                  {{user | apply: _getItemStatus: config: umService}}
                </span>
              </dd>

              <dt>Created:</dt>
              <dd>{{user.created | date:'M/d/y HH:mm'}}</dd>

              <dt>Updated:</dt>
              <dd>{{user.updated | date:'M/d/y HH:mm'}}</dd>
            </dl>
          </div>
        </div>
        <div class="row">
          <!-- Col 1 -->
          <div class="col-md-6 col-sm-12 col-md-push-6">
            <app-input
              [label]="'Username'"
              [control]="form.controls['username']"
              [autocompleteMode]="'off'"
              [iconAfter]="'glyphicon-user'"
            ></app-input>

            <ng-template [ngIf]="!user || (userService.getUser().id !== user.id)">
              <app-input
                [label]="'Password'"
                [type]="'password'"
                [control]="form.controls['password']"
                [autocompleteMode]="'new-password'"
                [iconAfter]="'glyphicon-asterisk'"
              ></app-input>

              <app-input
                [label]="'Confirm Password'"
                [type]="'password'"
                [control]="form.controls['password2']"
                [autocompleteMode]="'new-password'"
                [iconAfter]="'glyphicon-asterisk'"
              ></app-input>
            </ng-template>
          </div>

          <!-- Col 2 -->
          <div class="col-md-6 col-sm-12 col-md-pull-6">
            <app-input
              [label]="'First Name'"
              [control]="form.controls['firstName']"
              [iconAfter]="'glyphicon-user'"
            ></app-input>

            <app-input
              [label]="'Last Name'"
              [control]="form.controls['lastName']"
              [iconAfter]="'glyphicon-user'"
            ></app-input>

            <app-input
              [label]="'Email'"
              [control]="form.controls['email']"
              [iconAfter]="'glyphicon-envelope'"
            ></app-input>

            <app-select [label]="'Role'"
              [control]="form.controls['role']"
              [options]="UserRole | apply: _prepareRoleOptions"
            ></app-select>

          </div>
        </div>
        <div class="row">
          <div class="col-md-12">
            <div class="pull-right">
              <button class="btn btn-md btn-clear"
                *ngIf="!user"
                (click)="resetForm()">
                Clear
              </button>

              <button type="submit"
                [disabled]="form.invalid || form.disabled || form.pristine || ((savingObserver.active) | async)"
                [ngClass]="{
                  'btn': true,
                  'btn-md': true,
                  'btn-success': !user,
                  'btn-apply': user
                }"
              >
                {{user ? 'Update' : 'Create'}}
              </button>
            </div>
          </div>
        </div>
      </form>
    </div>
  `,
})
export class UserEditComponent implements OnInit, OnDestroy, HasUnsavedData {
  config = config;
  UserRole = UserRole;

  readonly loader: ReactiveLoader<FormLoaderData, TObjectId>;
  readonly savingObserver = new ActivityObserver();

  user: IUMUser;

  form: FormGroup;

  private _subscriptions: Subscription[] = [];
  private _formSubscriptions: Subscription[] = [];

  constructor(
    readonly userService: UserService,
    readonly umService: UserManagementService,
    private _router: Router,
    private _activatedRoute: ActivatedRoute,
  ) {
    this.loader = new ReactiveLoader((userId: TObjectId) => {
      return userId ? this._editFormLoader(userId) : this._createFormLoader();
    });

    const loaderSubscription = this.loader.subscribe(this._onPageDataLoaded.bind(this));
    this._subscriptions.push(loaderSubscription);
  }

  ngOnInit(): void {
    const paramsSubscription = this._activatedRoute.params.subscribe((params: Params) => {
      this.loader.load(params['userId']);
    });

    this._subscriptions.push(paramsSubscription);
  }

  ngOnDestroy(): void {
    this._subscriptions.forEach(_ => _.unsubscribe());
    this._formSubscriptions.forEach(_ => _.unsubscribe());
  }

  onSubmit(): void {
    const observable = this.user ? this._updateUser() : this._createUser();

    const subscription = this.savingObserver
      .observe(observable)
      .subscribe((user: IUMUser) => {
        if (this.user) {
          this.loader.load(user.id);
        } else {
          this.form.reset();
          this._router.navigate(['/desk', 'users', 'manage', user.id]);
        }
      });

    this._subscriptions.push(subscription);
  }

  onDeleted(): void {
    this._router.navigate(['/desk', 'users', 'manage']);
  }

  onActivated(): void {
    this.user && this.loader.load(this.user.id);
  }

  onDeactivated(): void {
    this.user && this.loader.load(this.user.id);
  }

  hasUnsavedData(): boolean {
    return this.form && this.form.dirty;
  }

  resetForm() {
    const data = this._getFormDefaultValues();
    Object.keys(data)
      .forEach(key => this.form.controls[key].reset(data[key]));
  }

  @HostListener('window:beforeunload', ['$event'])
  public onPageUnload($event: BeforeUnloadEvent) {
    if (this.hasUnsavedData()) {
      $event.returnValue = true;
    }
  }

  _getItemStatusClass(item: IUMUser): {[key: string]: boolean} {
    return {
      'label': true,
      'text-capitalize': true,
      'label-success': item.status === UserStatus.ACTIVE,
      'label-default': item.status === UserStatus.INACTIVE,
      'label-danger': item.status === UserStatus.DEACTIVATED,
    };
  }

  _getItemStatus(item: IUMUser, config, umService: UserManagementService): string {
    return config.user.status.labels[item.status];
  }

  _prepareRoleOptions() {
    const roles = config.user.role;
    return AppSelectOptionData.fromList(roles.list, roles.labels);
  }

  _canUpdateRole(): boolean {
    return !this.user || this.userService.getUser().id !== this.user.id;
  }

  private _editFormLoader(userId: TObjectId): Observable<FormLoaderData> {
    return this.umService
      .get(userId)
      .map((user) => {
        return { user };
      });
  }

  private _createFormLoader(): Observable<FormLoaderData> {
    return Observable.of({});
  }

  private _onPageDataLoaded(data: FormLoaderData): void {
    this.user = data.user;
    this._initForm();
  }

  private _getFormDefaultValues(): {[key: string]: string} {
    return {
      username: '',
      email: '',
      password: '',
      password2: '',
      firstName: '',
      lastName: '',
      role: UserRole.USER,
    };
  }

  private _initForm(): void {
    this._formSubscriptions.forEach(_ => _.unsubscribe());

    const data = this._getFormDefaultValues();

    const user = this.user;

    if (user) {
      data.username = user.username;
      data.email = user.email;
      data.password = '';
      data.password2 = '';
      data.firstName = user.firstName;
      data.lastName = user.lastName;
      data.role = user.role;
    }

    this.form = new FormGroup({
      username: new FormControl(data.username, Validators.required),
      firstName: new FormControl(data.firstName, Validators.required),
      lastName: new FormControl(data.lastName, Validators.required),
      email: new FormControl(data.email, [Validators.required, AppValidators.email]),
      password: new FormControl(data.password),
      password2: new FormControl(data.password),
      role: new FormControl(data.role, Validators.required),
    });

    if (!this._canUpdateRole()) {
      this.form.controls['role'].disable();
    }

    if (user) {
      this._setEditUserFormValidators();
    } else {
      this._setCreateUserFormValidators();
    }
  }

  private _setCreateUserFormValidators(): void {
    const password = this.form.controls['password'];
    const password2 = this.form.controls['password2'];

    password.setValidators([Validators.required, AppValidators.password]);

    this._formSubscriptions.push(
      AppValidators.crossValidate(
        password,
        [password2],
        (password) => Validators.compose([
          Validators.required,
          AppValidators.equals(password, 'Passwords don\'t match'),
          AppValidators.password,
        ]),
      ),
    );

    // re-validate password2 if password field value has changed
    this._formSubscriptions.push(password.valueChanges.subscribe(() => {
      password2.updateValueAndValidity();
    }));
  }

  private _setEditUserFormValidators(): void {
    const password = this.form.controls['password'];
    const password2 = this.form.controls['password2'];

    password.setValidators(AppValidators.password);

    this._formSubscriptions.push(AppValidators.crossValidate(
      password,
      [password2],
      (password) => Validators.compose([
        AppValidators.requiredIf(password),
        AppValidators.equals(password, 'Passwords don\'t match'),
        AppValidators.password,
      ]),
    ));
  }

  private _createUser(): Observable<IUMUser> {
    const controls = this.form.controls;

    const data: IUserCreateRequest = {
      username: controls['username'].value,
      email: controls['email'].value,
      firstName: controls['firstName'].value,
      lastName: controls['lastName'].value,
      role: controls['role'].value,
      password: controls['password'].value,
    };

    return this.umService.create(data);
  }

  private _updateUser(): Observable<IUMUser> {
    const controls = this.form.controls;

    const data: IUserUpdateRequest = {
      username: controls['username'].value,
      email: controls['email'].value,
      firstName: controls['firstName'].value,
      lastName: controls['lastName'].value,
    };

    if (this._canUpdateRole()) {
      data['role'] = controls['role'].value;
    }

    // Don't send password if it's empty
    if (controls['password'].value.length) {
      data['password'] = controls['password'].value;
    }

    let observable = this.umService.update(this.user.id, data);

    return observable;
  }
}

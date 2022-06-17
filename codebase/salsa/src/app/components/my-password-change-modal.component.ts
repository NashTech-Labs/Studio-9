import { Component, OnDestroy, ViewChild } from '@angular/core';
import { FormControl, Validators } from '@angular/forms';

import { Subscription } from 'rxjs/Subscription';

import { ModalComponent } from '../core-ui/components/modal.component';
import { UserService } from '../core/services/user.service';
import { ActivityObserver } from '../utils/activity-observer';
import { AppFormGroup } from '../utils/forms';
import { AppValidators } from '../utils/validators';

@Component({
  selector: 'my-password-change-modal',
  template: `
    <app-modal #modal
      title="Change password"
      [buttons]="[{'class': 'btn-primary', 'title': 'Save', 'disabled': !form.valid || (_savingObserver.active | async)}]"
      (buttonClick)="onPasswordSubmit()">
      <div class="panel">
        <div class="panel-body">
          <form [formGroup]="form" (ngSubmit)="form.valid && onPasswordSubmit()">
            <app-input
              [control]="form.controls.oldPassword"
              type="password"
              label="Old Password"
              iconAfter="glyphicon-asterisk"
            ></app-input>
            <app-input
              [control]="form.controls.newPassword"
              type="password"
              label="New Password"
              iconAfter="glyphicon-asterisk"
            ></app-input>
            <app-input
              [control]="form.controls.checkNewPassword"
              type="password"
              label="Confirm New Password"
              iconAfter="glyphicon-asterisk"
            ></app-input>
          </form>
          <div>
            Password should be minimum 10 characters and contain at least 3 out of 4 character types: uppercase letter,
            lowercase letter, special character, digit.
          </div>
        </div>
      </div>
    </app-modal>

  `,
})
export class MyPasswordChangeModalComponent implements OnDestroy {
  @ViewChild('modal') private modal: ModalComponent;
  private _savingObserver = new ActivityObserver();
  private form: AppFormGroup<{
    oldPassword: FormControl,
    newPassword: FormControl,
    checkNewPassword: FormControl,
  }>;
  private _formSubscriptions: Subscription[] = [];

  constructor(
    private _user: UserService,
  ) {
    // Experimental
    const newPassword = new FormControl('');
    const checkNewPassword = new FormControl('');

    newPassword.setValidators([Validators.required, AppValidators.password]);

    this._formSubscriptions.push(AppValidators.crossValidate(
      newPassword,
      [checkNewPassword],
      (password) => Validators.compose([
        AppValidators.requiredIf(password),
        AppValidators.equals(password, 'Passwords don\'t match'),
        AppValidators.password,
      ]),
    ));
    this.form = new AppFormGroup({
      oldPassword: new FormControl('', Validators.required),
      newPassword: newPassword,
      checkNewPassword: checkNewPassword,
    });

  }

  onPasswordSubmit() {
    const value = this.form.value;
    delete value['checkNewPassword'];
    this._savingObserver
      .observe(this._user.password(value))
      .subscribe(() => this.modal.hide());
  }

  ngOnDestroy(): void {
    this._formSubscriptions.forEach(_ => _.unsubscribe());
  }

  public show() {
    this.form.reset();
    this.modal.show();
  }
}

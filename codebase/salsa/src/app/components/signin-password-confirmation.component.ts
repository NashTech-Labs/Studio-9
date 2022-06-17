import { Component } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import config from '../config';
import { UserService } from '../core/services/user.service';
import { AppValidators } from '../utils/validators';

@Component({
  selector: 'app-signin-password-confirmation',
  template: `
    <app-layout-splash>
      <div class="row">
        <div class="col-sm-10 col-sm-offset-1 col-md-8 col-md-offset-2 col-lg-6 col-lg-offset-3">

            <h5 class="text-center">Password reset</h5>
            <form [formGroup]="passwordResetConfirmationForm" class="m-t-1">
              <div class="card">
                <ul class="list-group list-group-flush form-table">
                  <li class="list-group-item form-group">
                    {{passwordResetConfirmationForm.controls['email'].value}}
                  </li>
                  <li class="list-group-item">
                    <app-input label="New Password"
                      type="password"
                      [control]="passwordResetConfirmationForm.controls['newPassword']"
                      [iconAfter]="'glyphicon-asterisk'"></app-input>
                    <app-input label="Confirm New Password"
                      type="password"
                      [control]="passwordResetConfirmationForm.controls['newPassword2']"
                      [iconAfter]="'glyphicon-asterisk'"></app-input>
                  </li>
                </ul>
              </div>
              <div>
                Password should be minimum 10 characters and meet all following rules:
                <ul><li>at least 1 upper case character</li><li>at least 1 lower case character</li><li>at least 1 special character</li><li>at least 1 digit</li></ul>
              </div>
              <button
                type="button"
                (click)="onSubmit()"
                [disabled]="!passwordResetConfirmationForm.valid"
                class="btn btn-block btn-primary-outline">Update password</button>
            </form>
            <!--
            <div class="m-t-3 text-center">
              <a [routerLink]="[config.routes.signin]">Back to signin</a>.
            </div>
            -->
        </div>
      </div>
    </app-layout-splash>
  `,
})
export class SigninPasswordConfirmationComponent {
  passwordResetConfirmationForm: FormGroup;
  config = config;

  constructor(
    private router: Router,
    private route: ActivatedRoute,
    private user: UserService,
  ) {

    this.passwordResetConfirmationForm = new FormGroup({
      secretCode: new FormControl(this.route.snapshot.queryParams['secretCode']),
      email: new FormControl(this.route.snapshot.queryParams['email'], [Validators.required, AppValidators.email]),
      newPassword: new FormControl('', [Validators.required, AppValidators.password]),
      newPassword2: new FormControl('', [Validators.required, AppValidators.password, AppValidators.equal('newPassword')]),
    });
  }

  onSubmit() {
    this.user.passwordResetConfirmation(this.passwordResetConfirmationForm.value).subscribe(() => {
      this.router.navigate([config.routes.signin]);
    });
  }
}

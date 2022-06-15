import { Component } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import config from '../config';
import { UserService } from '../core/services/user.service';
import { AppValidators } from '../utils/validators';

@Component({
  selector: 'app-signin-password',
  template: `
    <app-layout-splash>
      <div class="row">
        <div class="col-sm-10 col-sm-offset-1 col-md-8 col-md-offset-2 col-lg-6 col-lg-offset-3">
          <h3 class="text-center">Why are you having trouble logging in?</h3>
          <div class="card">
            <form [formGroup]="passwordResetForm">
              <ul class="list-group list-group-flush form-table">
                <li class="list-group-item">
                  <div [formControlValidator]="passwordResetForm.controls['type']" class="form-group">
                    <app-check [label]="'I forgot my password'" [name]="'type'" [value]="'password'"
                      [type]="'radio'"
                      [control]="passwordResetForm.controls['type']"></app-check>
                    <app-check [label]="'I forgot my username'" [name]="'type'" [value]="'username'"
                        [type]="'radio'"
                      [control]="passwordResetForm.controls['type']"></app-check>
                  </div>
                </li>
                <li class="list-group-item">
                  <app-input [control]="passwordResetForm.controls['email']" [type]="'email'" [label]="'Email'"></app-input>
                  <button
                    (click)="onSubmit()"
                    [disabled]="!passwordResetForm.valid"
                    type="button"
                    class="btn btn-block btn-apply">
                    Restore access
                  </button>
                </li>
                <li class="list-group-item text-center">
                  <a [routerLink]="['/signup']">Sign up here</a>
                  &#8226;
                  <a [routerLink]="['/signin']">Sign in</a>
                </li>
              </ul>
            </form>
          </div>
        </div>
      </div>
    </app-layout-splash>
  `,
})
export class SigninPasswordComponent {
  passwordResetForm: FormGroup;
  config = config;

  constructor(
    private router: Router,
    private user: UserService,
  ) {

    this.passwordResetForm = new FormGroup({
      type: new FormControl('password', Validators.required),
      email: new FormControl('', [Validators.required, AppValidators.email]),
    });
  }

  onSubmit() {
    (
      (this.passwordResetForm.value.type === 'password')
        ? this.user.passwordReset(this.passwordResetForm.value.email)
        : this.user.usernameRemind(this.passwordResetForm.value.email)
    ).subscribe(() => {
      this.router.navigate([config.routes.signin]);
    });
  }
}

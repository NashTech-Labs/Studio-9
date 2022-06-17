import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { Subscription } from 'rxjs/Subscription';

import config from '../config';
import { IModalButton, ModalComponent } from '../core-ui/components/modal.component';
import { UserService } from '../core/services/user.service';
import { AppValidators } from '../utils/validators';

@Component({
  selector: 'app-signup',
  template: `
    <app-layout-splash>
      <div class="row">
        <div class="col-sm-10 col-sm-offset-1 col-md-8 col-md-offset-2 col-lg-6 col-lg-offset-3">
          <h3 class="text-center">Sign up</h3>
            <div class="card">
            <form [formGroup]="signupForm" (ngSubmit)="signupForm.valid && onSubmit()" autocomplete="off">
              <ul class="list-group list-group-flush">
                <!-- Yeah, minor trade-offs to highlight row with names -->
                <li class="list-group-item">
                  <app-input [control]="signupForm.controls['firstName']" [showErrors]="true" [type]="'text'"
                    [label]="'First name'" [iconAfter]="'glyphicon-user'"></app-input>
                  <app-input [control]="signupForm.controls['lastName']" [showErrors]="true" [type]="'text'"
                    [label]="'Last name'" [iconAfter]="'glyphicon-user'"></app-input>
                  <app-input [control]="signupForm.controls['username']" [showErrors]="true" [type]="'text'"
                    [label]="'Username'" [iconAfter]="'glyphicon-user'"></app-input>
                  <app-input [control]="signupForm.controls['email']" [readonly]="!!this.signInEmail" [showErrors]="true"
                    [type]="'email'" [label]="'Email'" [iconAfter]="'glyphicon-envelope'"></app-input>
                  <app-input [control]="signupForm.controls['password']" [showErrors]="true" [type]="'password'"
                    [label]="'Password'" [iconAfter]="'glyphicon-asterisk'"></app-input>
                  <app-input [control]="signupForm.controls['password2']" [showErrors]="true" [type]="'password'"
                    [label]="'Confirm password'" [iconAfter]="'glyphicon-asterisk'"></app-input>
                  <div [formControlValidator]="signupForm.controls['confirmation']" class="checkbox">
                    <label class="label-bare">
                      <input type="checkbox" formControlName="confirmation">
                      I accept
                    </label>
                    <a href="#" prevent-default-click (click)="termsModal.show()">terms and conditions</a>
                  </div>
                  <button (click)="signupForm.updateValueAndValidity();" type="submit"
                    [disabled]="signupForm.invalid || signupForm.disabled || signupForm.pristine"
                    class="btn btn-block btn-apply">Register
                  </button>
                </li>
                <li class="list-group-item text-center">
                  Already registered? Please <a [routerLink]="[config.routes.signin]">sign in</a>.
                </li>
              </ul>
            </form>
          </div>
        </div>
        <app-modal #termsModal
          [modalId]="'termsModal'"
          [caption]="'Knoldus Terms and Conditions'"
          [buttons]="termsModalButtons"
          [sizeClass]="config.modal.size.LARGE"
          (buttonClick)="onTermsButton($event)">
          <div class="panel">
            <div class="panel-body">
              <div class="embed-responsive embed-responsive-4by3">
                <iframe class="embed-responsive-item" src="assets/html/signup.terms.html"></iframe>
              </div>
            </div>
          </div>
        </app-modal>
      </div>
    </app-layout-splash>
  `,
})
export class SignupComponent implements OnInit, OnDestroy {
  signupForm: FormGroup;
  config = config;

  readonly termsModalButtons: (IModalButton & {agree: boolean})[] = [
    {agree: true, title: 'I Agree', 'class': 'btn-primary'},
    {agree: false, title: 'I Disagree'},
  ];
  signInEmail: string;

  private _formSubscriptions: Subscription[] = [];

  @ViewChild('termsModal') private termsModal: ModalComponent;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private user: UserService,
  ) {
    this.signupForm = new FormGroup({
      firstName: new FormControl('', Validators.required),
      lastName: new FormControl('', Validators.required),
      username: new FormControl('', Validators.required),
      email: new FormControl('', [Validators.required, AppValidators.email]), // We need both username and email for now. TODO validate username and email before registering.
      password: new FormControl('', [Validators.required, AppValidators.password]),
      password2: new FormControl('', [Validators.required, AppValidators.password]),
      confirmation: new FormControl(false, AppValidators.isTrue),
    });
    /* Getting existing password and confirm password controls from signup form to crossvalidate */
    const password = this.signupForm.controls['password'];
    const password2 = this.signupForm.controls['password2'];
    this._formSubscriptions.push(
      AppValidators.crossValidate(
        password,
        [password2],
        (password) => Validators.compose([AppValidators.equals(password, 'Passwords don\'t match'), Validators.required, AppValidators.password]),
      ),
    );
  }

  ngOnInit(): void {
    this.route.queryParams.forEach((params) => {
      this.signInEmail = params['email'];
      if (this.signInEmail) {
        this.signupForm.controls['email'].setValue(this.signInEmail);
      }
    });
  }

  onSubmit() {
    // TEMP TEMP TEMP
    this.user.signup(this.signupForm.value).subscribe((user: any) => {
      user && this.router.navigate([config.routes.signin]);
      // Gerald, do something...
    });
  }

  onTermsButton(button: { agree: boolean }) {
    this.signupForm.controls['confirmation'].setValue(button.agree);
    this.termsModal.hide();
  }

  ngOnDestroy(): void {
    this._formSubscriptions.forEach(_ => _.unsubscribe());
  }
}

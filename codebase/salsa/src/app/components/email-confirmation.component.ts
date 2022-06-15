import { Component, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { Subscription } from 'rxjs/Subscription';

import config from '../config';
import { AppHttpError } from '../core/services/http.service';
import { UserService } from '../core/services/user.service';

@Component({
  selector: 'email-activation',
  template: `
    <app-layout-splash>
      <div class="row">
        <div class="col-sm-10 col-sm-offset-1 col-md-8 col-md-offset-2 col-lg-6 col-lg-offset-3 app-spinner-box">

          <h3 class="text-center">Email Confirmation</h3>
          <div class="card">
            <app-spinner *ngIf="!_error"></app-spinner>
            <error-indicator
              *ngIf="_error"
              [caption]="'Email confirmation failed'"
              [message]="_error"
            ></error-indicator>
          </div>
        </div>
      </div>
    </app-layout-splash>
  `,
})
export class EmailConfirmationComponent implements OnDestroy {
  private _error: string = null;
  private _subscriptions: Subscription[] = [];

  constructor(
    private router: Router,
    private route: ActivatedRoute,
    private userService: UserService,
  ) {
    this._subscriptions.push(this.route.queryParams.subscribe(params => {
      if (params['activationCode'] && params['userId'] && params['orgId']) {
        const confirmationParams = {
          activationCode: params['activationCode'],
          userId: params['userId'],
          orgId: params['orgId'],
        };
        this._error = null;
        this.userService.confirmEmail(confirmationParams)
          .subscribe(
            () => {
              this.router.navigate([config.routes.signin]);
            },
            (error: AppHttpError) => {
              this._error = error.message || 'Unknown error';
            },
        );
      } else {
        this._error = 'Invalid confirmation URL';
      }
    }));
  }

  ngOnDestroy(): void {
    this._subscriptions.forEach(_ => _.unsubscribe());
  }
}

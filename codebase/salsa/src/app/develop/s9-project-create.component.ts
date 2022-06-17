import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import { Subscription } from 'rxjs/Subscription';

import { ActivityObserver } from '../utils/activity-observer';

import { IS9Project } from './s9-project.interfaces';
import { S9ProjectService } from './s9-project.service';

@Component({
  selector: 's9-project-create',
  template: `
    <app-spinner *ngIf="!form"></app-spinner>

    <div *ngIf="form" class="col-sm-10 col-sm-offset-1 col-md-8 col-md-offset-2 col-xl-6 col-xl-offset-3">
      <h3 class="text-center">Create S9 Project</h3>
      <form [formGroup]="form" (ngSubmit)="form.valid && onSubmit()">
        <app-input [label]="'Name'" [control]="form.controls['name']"></app-input>
        <app-description [control]="form.controls['description']" [editMode]="true"></app-description>
        <button type="submit"
          [disabled]="!form.valid || ((_savingObserver.active) | async)" class="btn btn-success pull-right">Create
        </button>
      </form>
    </div>
  `,
})
export class S9ProjectCreateComponent implements OnInit, OnDestroy {
  form: FormGroup;
  readonly _savingObserver: ActivityObserver = new ActivityObserver();
  private _subscriptions: Subscription[] = [];

  constructor(
    private _router: Router,
    private _s9Projects: S9ProjectService,
  ) {
    this.form = new FormGroup({
      name: new FormControl('', Validators.required),
      description: new FormControl(null),
    });
  }

  ngOnInit() {
  }

  ngOnDestroy() {
    this._subscriptions.forEach((_) => _.unsubscribe());
  }

  onSubmit() {
    const create$ = this._savingObserver
      .observe(this._s9Projects.create(this.form.value))
      .do((s9Project: IS9Project) => {
        this._router.navigate(['/desk', 'develop', 'projects', s9Project.id]);
      });

    this._subscriptions.push(create$.subscribe());
  }
}

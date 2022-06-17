import { Component, OnInit } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import 'rxjs/add/operator/do';

import { ActivityObserver } from '../utils/activity-observer';

import { FlowService } from './flow.service';

@Component({
  selector: 'flow-create',
  template: `
    <div class="col-sm-10 col-sm-offset-1 col-md-8 col-md-offset-2 col-xl-6 col-xl-offset-3">
      <h3 class="text-center">Create A New Flow</h3>
      <form [formGroup]="flowCreateForm" (ngSubmit)="flowCreateForm.valid && onSubmit()">
        <app-input [label]="'Name'" [control]="flowCreateForm.controls['name']"></app-input>
        <app-description [control]="flowCreateForm.controls['description']" [editMode]="true"></app-description>
        <button
          type="submit"
          class="btn btn-success pull-right"
          [disabled]="!flowCreateForm.valid || (_savingObserver.active | async)"
        >
          Create
        </button>
      </form>
    </div>
  `,
})
export class FlowCreateComponent implements OnInit {
  flowCreateForm: FormGroup;
  readonly _savingObserver = new ActivityObserver();

  constructor(
    private router: Router,
    private flows: FlowService,
  ) {
    this.flowCreateForm = new FormGroup({
      name: new FormControl('', Validators.required),
      description: new FormControl(''),
    });
  }

  ngOnInit() {
    // init data
    this.flows.view();
  }

  onSubmit() {
    this._savingObserver
      .observe(this.flows.create(this.flowCreateForm.value))
      .do(() => this.flows.getMyFlows())
      .subscribe((flow) => this.router.navigate(['/desk', 'flows', flow.id]));
  }
}


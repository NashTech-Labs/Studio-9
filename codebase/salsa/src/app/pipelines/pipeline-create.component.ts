import { Component } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import { ActivityObserver } from '../utils/activity-observer';

import { PipelineService } from './pipeline.service';

@Component({
  selector: 'pipeline-create',
  template: `
    <div class="col-sm-10 col-sm-offset-1 col-md-8 col-md-offset-2 col-xl-6 col-xl-offset-3">
      <h3 class="text-center">Create A New Pipeline</h3>
      <form [formGroup]="form" (ngSubmit)="form.valid && onSubmit()">
        <app-input label="Name" [control]="form.controls['name']"></app-input>
        <app-description [control]="form.controls['description']" [editMode]="true"></app-description>
        <button
          type="submit"
          class="btn btn-success pull-right"
          [disabled]="!form.valid || (_savingObserver.active | async)"
        >
          Create
        </button>
      </form>
    </div>
  `,
})
export class PipelineCreateComponent {
  form: FormGroup;
  readonly _savingObserver = new ActivityObserver();

  constructor(
    private router: Router,
    private pipelineService: PipelineService,
  ) {
    this.form = new FormGroup({
      name: new FormControl('', Validators.required),
      description: new FormControl(''),
    });
  }

  onSubmit() {
    this._savingObserver
      .observe(this.pipelineService.create(Object.assign(this.form.value, {'steps': []})))
      .subscribe((pipeline) => this.router.navigate(['/desk', 'pipelines', pipeline.id, 'edit']));
  }
}

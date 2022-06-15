import { Component, Host, OnInit } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';

import { AclService } from '../services/acl.service';
import { MiscUtils } from '../utils/misc';

import { FlowData } from './flow-layout.component';
import { IFlow } from './flow.interface';
import { FlowService } from './flow.service';

@Component({
  selector: 'flow-edit',
  template: `
    <div class="col-sm-10 col-sm-offset-1 col-md-8 col-md-offset-2 col-xl-6 col-xl-offset-3">
      <h3 class="text-center">Edit Flow</h3>
      <form [formGroup]="flowEditForm" (ngSubmit)="flowEditForm.valid && onSubmit()">
        <app-input [label]="'Name'" [control]="flowEditForm.controls['name']"></app-input>
        <app-description [control]="flowEditForm.controls['description']"></app-description>
        <button type="submit" [disabled]="!flowEditForm.valid" class="btn btn-success pull-right">Update</button>
      </form>
    </div>
  `,
})
export class FlowEditComponent implements OnInit {
  flowEditForm: FormGroup;

  constructor(
    @Host() private flowData: FlowData,
    private flows: FlowService,
    private acl: AclService,
  ) {
    this.flowEditForm = new FormGroup({
      id: new FormControl(), // hidden
      name: new FormControl('', Validators.required),
      description: new FormControl(''),
    });
  }

  ngOnInit() {
    // init data
    this.flowData.forEach((flow: IFlow) => {
      this.fillFlowForm(this.flowEditForm, flow);
    });
  }

  onSubmit() {
    this.flows.update(this.flowEditForm.value).subscribe(() => {
      this.flows.getMyFlows();
    });
  }

  private fillFlowForm(form: FormGroup, flow?: any) {
    let canUpdate = this.acl.canUpdateFlow(flow);
    MiscUtils.fillForm(form, flow, !canUpdate);
  }
}


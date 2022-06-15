import { Component, Host, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { FormGroup } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { Subscription } from 'rxjs/Subscription';

import config from '../config';
import { IModalButton, ModalComponent } from '../core-ui/components/modal.component';
import { IAsset } from '../core/interfaces/common.interface';
import { ProcessService } from '../core/services/process.service';
import { StorageService } from '../core/services/storage.service';
import { AclService } from '../services/acl.service';
import { ActivityObserver } from '../utils/activity-observer';
import { MiscUtils } from '../utils/misc';

import { FlowData } from './flow-layout.component';
import { IFlow, IFlowstep } from './flow.interface';
import { FlowService } from './flow.service';
import { FlowstepEditOptionsComponent } from './flowstep-edit-o.component';
import { FlowstepService } from './flowstep.service';

@Component({
  selector: 'flowstep-edit',
  template: `
    <ng-template [ngIf]="flowstepEditForm">
      <form class="pt15">
        <div class="row">
          <div class="col-md-6 col-sm-6">
            <app-input [label]="'Step Name'" [control]="flowstepEditForm.controls['name']"></app-input>
          </div>
          <div class="col-md-6 col-sm-6" *ngIf="!flowstepEditForm.controls['id'].value">
            <app-input [label]="'Output Table Name'" [control]="flowstepEditForm.controls['output']"></app-input>
          </div>
        </div>
        <flowstep-edit-form
          [flow]="flow"
          [form]="flowstepEditForm"></flowstep-edit-form>
      </form>
      <div class="tabpanel">
        <!-- Nav tabs -->
        <ul class="nav nav-tabs" role="tablist">
            <li class="nav-item" *ngFor="let control of flowstepEditForm.controls['input']['controls']; let i = index"
              role="presentation" [ngClass]="{'active': activeTableTab === i}">
                <a class="nav-link" (click)="activeTableTab = i">Input {{i+1}}</a>
            </li>
            <li class="nav-item" role="presentation" [ngClass]="{'active': activeTableTab === -1}" *ngIf="flowstepEditForm.controls['id'].value">
                <a class="nav-link" (click)="activeTableTab = -1">Output Table</a>
            </li>
            <li class="nav-item" role="presentation" [ngClass]="{'active': activeTableTab === -2}"
              *ngIf="flowstepEditForm.controls['id'].value && flowstepEditForm.controls['type'].value === config.flowstep.type.values.cluster">
                <a class="nav-link" (click)="activeTableTab = -2">Cluster Results Visualization</a>
            </li>
        </ul>
      </div>
      <!-- Tab panes -->
      <div class="flex-col" [adaptiveHeight]="{property: 'height', minHeight: 150, targetHeight: 500, pageMargin: flow.steps.length ? 70 : 15, trigger: flowstepEditForm.value}">
        <div class="flex-col" [hidden]="activeTableTab !== i"
          *ngFor="let control of flowstepEditForm.controls['input']['controls']; let i = index">
            <div *ngIf="!control.value" style="min-height: 10em;background: white;padding-top: 1px;">
              <h4 class="text-center text-muted text-light brand-tab">No Table Selected</h4>
            </div>

            <table-view-embed *ngIf="control.value"
              [id]="control.value"></table-view-embed>
        </div>
        <div class="flex-col" [hidden]="activeTableTab !== -1">
          <table-view-embed *ngIf="flowstepEditForm.controls['id'].value"
            [id]="flowstepEditForm.controls['output'].value"
            ></table-view-embed>
        </div>
        <clustering-results
            *ngIf="paramId && flowstepEditForm.value.type === config.flowstep.type.values.cluster && activeTableTab === -2"
            [flow]="flow"
            [flowstepId]="paramId"
            ></clustering-results>
      </div>
    </ng-template>
    <app-modal #confirmModal [caption]="'You have unsaved data for ' + paramType"
      [buttons]="[{'class': 'btn-clear', 'title': 'No'},{'class': 'btn-apply', 'title': 'Yes'}]"
      (buttonClick)="onConfirmation($event)">
      Would you like to continue with unsaved parameters?
    </app-modal>
  `,
  styles: [' .tabpanel {margin-top: 10px;}'],
})
export class FlowstepEditComponent implements OnInit, OnDestroy {
  config = config;
  paramId: string;
  paramType: IFlowstep.Type;

  flow: IFlow;

  flowstepEditForm: FormGroup;

  activeTableTab: number = 0;
  @ViewChild('confirmModal') private modal: ModalComponent;
  private oldTemplate: IFlowstep = null;
  private routeSubscription: Subscription;
  private flowSubscription: Subscription;
  private formSubscription: Subscription;
  private _savingObserver = new ActivityObserver();

  constructor(
    @Host() private flowData: FlowData,
    private route: ActivatedRoute,
    private router: Router,
    private flows: FlowService,
    private flowsteps: FlowstepService,
    private processes: ProcessService,
    private acl: AclService,
    private storage: StorageService,
  ) {
  }

  ngOnInit() {
    this.flowSubscription = this.flowData.subscribe(flow => {
      this.flow = flow;

      this.routeSubscription && this.routeSubscription.unsubscribe();

      if (!flow) return;
      this.routeSubscription = this.route.params.subscribe(params => {
        this.paramId = params['stepId'];

        const flowstep: IFlowstep = this.paramId
          ? this.flow.steps.find(_ => _.id === this.paramId)
          : null;

        this.paramType = flowstep ? flowstep['type'] : params['type'];

        if (!this.paramId && !this.paramType && this.flow.steps.length) {
          this.router.navigate(['/desk', 'flows', this.flow.id, 'steps', this.flow.steps[0].id]);
        } else {
          this.createFlowstepForm(flowstep);
          this.activeTableTab = this.paramId ? -1 : 0;

          this.oldTemplate = this.storage.get(this.paramType);
          if (this.oldTemplate && !this.paramId && this.paramType) {
            this.modal.show();
          }
        }
      });
    });
  }

  ngOnDestroy() {
    this.flowSubscription && this.flowSubscription.unsubscribe();
    this.routeSubscription && this.routeSubscription.unsubscribe();
    this.formSubscription && this.formSubscription.unsubscribe();
  }

  onConfirmation(button: IModalButton) {
    if (button.title === 'Yes') {
      this.createFlowstepForm(this.oldTemplate);
    } else {
      this.storage.remove(this.paramType);
    }
    this.modal.hide();
  }

  submit() {
    this.storage.remove(this.flowstepEditForm.value['type']);
    // experimental / skip server side validation for update
    if (this.flowstepEditForm.value['id']) {
      return this.update();
    }

    if (this.flowstepEditForm.value['type'] === config.flowstep.type.values.insert) {
      this._savingObserver.observe(this.flowsteps.sqlParsingInsert({
        expression: this.flowstepEditForm.value['options']['formula'],
        dataSetId: (this.flowstepEditForm.value['options']['table'] || { id: null }).id,
        newColumnName: this.flowstepEditForm.value['options']['name'],
      })).subscribe(() => {
        this.update();
      });

    } else {
      this.update();
    }
  }

  update() {
    const flow = this.flow;

    const observable = this.paramId
      ? this.flowsteps.update(flow.id, this.paramId, this.flowstepEditForm.value)
      : this.flowsteps.create(flow.id, this.flowstepEditForm.value);

    this._savingObserver.observe(observable).subscribe((flowstep: any) => {
      // TODO: remove querying for flow
      this.flows.get(flow.id).subscribe(() => {
        this.router.navigate(['/desk', 'flows', flow.id, 'steps', flowstep.id]);
      });

      if (flowstep.status === this.config.flowstep.status.values.RUNNING) {
        // TODO: temp. should subscribe to "flowstep" process, not "table" process
        this.processes.subscribeByTarget(flowstep.output, IAsset.Type.TABLE, () => {
          // would be great to get only steps, not the whole flow, but there is no choice
          this.flows.get(flow.id).subscribe(() => {
            this.router.navigate(['/desk', 'flows', flow.id, 'steps', flowstep.id]);

            // refresh flowstep
            flowstep = flow.steps.find(_ => _.id === flowstep.id);
            this.fillFlowstepForm(this.flowstepEditForm, flowstep, flow);
          });
        });
      }
    });
  }

  get saving() {
    return this._savingObserver.active;
  }

  private fillFlowstepForm(form: FormGroup, flowstep: IFlowstep, flow: IFlow | any) {
    let canUpdate = this.acl.canUpdateFlowstep(flowstep, flow);
    MiscUtils.fillForm(form, flowstep, !canUpdate);
  }

  private createFlowstepForm(flowstep?: IFlowstep) {
    const defaultName = 'Step' + (this.flow.steps.length + 1);

    this.formSubscription && this.formSubscription.unsubscribe();

      // create form
    this.flowstepEditForm = FlowstepEditOptionsComponent.prepareFlowstepForm(this.paramType, defaultName);

    // prefill form / create mode
    if (!this.paramId && this.paramType) {
      this.formSubscription = this.flowstepEditForm.valueChanges.filter(() => {
        return this.flowstepEditForm.dirty;
      }).debounceTime(50).distinctUntilChanged().subscribe(() => {
        this.storage.set(this.paramType, this.flowstepEditForm.value);
      });
    }

    // prefill form / edit mode
    if (flowstep) {
      this.fillFlowstepForm(this.flowstepEditForm, flowstep, this.flow);
    }
  }
}

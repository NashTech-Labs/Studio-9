import { Component, Host, OnInit } from '@angular/core';

import { FlowData } from './flow-layout.component';
import { IFlow } from './flow.interface';

@Component({
  selector: 'flow-info',
  template: `
    <div class="row" *ngIf="flow">
      <div class="col-sm-10 col-sm-offset-1 col-md-8 col-md-offset-2 col-xl-6 col-xl-offset-3">
        <h3 class="text-center">{{flow.name}}</h3>
        <div class="card">
          <table class="table table-bordered">
            <tr>
              <td class="text-center text-muted" width="50%">
                <div class="h3">{{flow.steps.length}}</div>
                <div>{{flow.steps.length || 0 | pluralize: 'Step' : 'Steps'}}</div>
              </td>
              <td class="text-center text-muted" width="50%">
                <div class="h3">{{(flow.tables || []).length}}</div>
                <div>{{(flow.tables || {}).length || 0 | pluralize: 'Table' : 'Tables'}}</div>
              </td>
            </tr>
          </table>
        </div>
        <div class="card m-t-2">
          <table class="table table-bordered">
            <tr>
              <td [routerLink]="['/desk', 'flows', flow.id, 'graph']" class="text-center link" width="33%">
                <a class="link">
                  <i class="imgaction imgaction-visualize center-block h3"></i>
                  <div style="color: #333">Visualize</div>
                </a>
              </td>
              <td [routerLink]="['/desk', 'flows', flow.id, 'edit']" class="text-center link" width="33%">
                <a class="link">
                  <i class="imgaction imgaction-preview center-block h3"></i>
                  <div style="color: #333">Edit</div>
                </a>
              </td>
            </tr>
          </table>
        </div>
        </div>
    </div>
  `,
})
export class FlowInfoComponent implements OnInit {
  flow: IFlow = null;

  constructor(
    @Host() private flowData: FlowData,
  ) {
  }

  ngOnInit() {
    this.flowData.forEach((flow: IFlow) => {
      this.flow = flow;
    });
  }
}


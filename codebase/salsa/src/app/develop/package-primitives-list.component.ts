import { Component, Input } from '@angular/core';

import config from '../config';

import { CVTLModelPrimitive } from './package.interfaces';
import { getParamsTooltip } from './package.utils';

@Component({
  selector: 'package-primitives-list',
  template: `
    <div class="table-scroll">
      <table class="table">
        <thead>
        <tr style="white-space: nowrap">
          <th>Name<br />Description</th>
          <th>Type</th>
          <th>Module Name<br />Class Name</th>
          <th>Parameters</th>
        </tr>
        </thead>
        <tbody>
        <tr *ngFor="let item of items">
          <td>
            <strong title="{{item.name}}">{{item.name}}</strong><br />
            <span title="{{item.description}}">{{item.description}}</span>
          </td>
          <td>
            {{config.primitives.operatorType.labels[item.operatorType]}}
          </td>
          <td class="ellipsis" style="max-width: 200px;">
            <strong title="{{item.moduleName}}">{{item.moduleName}}</strong><br />
            <span title="{{item.className}}">{{item.className}}</span>
          </td>
          <td>
            <ul class="params">
              <li *ngFor="let param of item?.params">
                <span [title]="param.name">{{param.name}}</span>
                <i
                  class="glyphicon glyphicon-question-sign"
                  tooltip
                  data-toggle="tooltip"
                  data-html="true"
                  data-placement="right"
                  [tooltipTitle]="param | apply: _getParamsTooltip">
                </i>
              </li>
            </ul>
          </td>
        </tr>
        <tr *ngIf="!items.length" class="text-center">
          <td colspan="5">
            No items to display.
          </td>
        </tr>
        </tbody>
      </table>
    </div>
  `,
  styles: [`
    .table-scroll tbody tr td {
      padding-left: 8px;
    }
    ul.params {
      padding-inline-start: 20px;
      margin: 0;
    }

    ul.params li {
      white-space: nowrap;
    }

    ul.params li span {
      display: inline-block;
      position: relative;
      top: 3px;
      max-width: 150px;
      text-overflow: ellipsis;
      white-space: nowrap;
      overflow: hidden;
    }
  `],
})
export class PackagePrimitivesListComponent {
  readonly config = config;
  @Input() items: CVTLModelPrimitive[];
  private _getParamsTooltip = getParamsTooltip;
}

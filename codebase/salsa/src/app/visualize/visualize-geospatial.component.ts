import { Component, HostBinding, OnInit } from '@angular/core';

import { AppSelectOptionData } from '../core-ui/components/app-select.component';
import { LibrarySelectorValue } from '../core/components/library-selector.component';
import { IAsset } from '../core/interfaces/common.interface';
import { ReactiveLoader } from '../utils/reactive-loader';

import { VisualizeDataService } from './visualize-data.service';
import { GeospatialDataRequest, GeospatialDataResponse } from './visualize.interface';

@Component({
  template: `
    <asset-operations [selectedItems]="[]"></asset-operations>
    <app-spinner [visibility]="_dataLoader.active | async"></app-spinner>
    <div class="row">
      <div class="col col-md-6">
        <app-select
          [label]="'Data Source'"
          [options]="_sourceOptions"
          [(value)]="_selectedSource"
        ></app-select>
      </div>
      <div class="col col-md-3">
        <app-check
          [(checked)]="_editingQuery"
          [label]="'Manual query'"
        ></app-check>
      </div>
      <div class="col col-md-3">
        <div class="pull-right">
          <button type="button"
            (click)="_fetchData()"
            class="btn btn-md btn-apply"
          >Submit</button>
        </div>
      </div>
    </div>
    <ng-container [ngSwitch]="_selectedSource">
      <ng-container *ngSwitchCase="'${GeospatialDataRequest.Mode.CV_PREDICTION}'">
        <div class="row" *ngIf="!_editingQuery">
          <div class="col col-lg-4">
            <app-form-group
              [caption]="'Experiment filter'"
            >
              <library-selector
                [inputLabel]="'Model filter'"
                [(value)]="_selectedModel"
                [available]="['${IAsset.Type.CV_MODEL}']"
                [allowReset]="true"
                (valueChange)="_updateGeospatialQuery()"
              ></library-selector>
              <library-selector
                [inputLabel]="'Album filter'"
                [(value)]="_selectedAlbum"
                [available]="['${IAsset.Type.ALBUM}']"
                [allowReset]="true"
                (valueChange)="_updateGeospatialQuery()"
              ></library-selector>
            </app-form-group>
          </div>
          <div class="col col-md-4">
            <app-form-group
              [caption]="'Prediction filter'"
            >
              <app-input
                [label]="'Target'"
                [(value)]="_targetFilter"
                (valueChange)="_updateGeospatialQuery()"
              ></app-input>
              <app-input
                [label]="'Min. confidence'"
                [type]="'number'"
                [(value)]="_minConfidence" [step]="0.01" [min]="0" [max]="1"
                (valueChange)="_updateGeospatialQuery()"
              ></app-input>
            </app-form-group>
          </div>
          <div class="col col-md-4">
            <app-form-group
              [caption]="'TOI filter'"
            >
              <app-input
                [label]="'Latitude'"
                [type]="'number'"
                [(value)]="_toiLatitude"
                (valueChange)="_updateGeospatialQuery()"
              ></app-input>
              <app-input
                [label]="'Longitude'"
                [type]="'number'"
                [(value)]="_toiLongitude"
                (valueChange)="_updateGeospatialQuery()"
              ></app-input>
              <app-input
                [label]="'Region radius'"
                [type]="'number'"
                [(value)]="_toiMaxDistance"
                (valueChange)="_updateGeospatialQuery()"
              ></app-input>
            </app-form-group>
          </div>
        </div>
        <app-form-group
          [caption]="'Query'"
        >
          <app-textarea
            [readonly]="!_editingQuery"
            [(value)]="_query"
            (iconAfterClick)="_editingQuery = !_editingQuery; _updateGeospatialQuery()"
          ></app-textarea>
        </app-form-group>
        <div *ngIf="_dataLoader.loaded && _data" class="brand-tab">
          <app-tabs
            [tabs]="['Data view', 'Globe view']"
            [(active)]="activeTab"
          ></app-tabs>

          <div *ngIf="_data && activeTab === 0" [adaptiveHeight]="{minHeight: 450}" class="table-scroll">
            <!--{{tableViewForm.value | json}}-->
            <table class="table table-dataset table-bordered">
              <thead style="border-bottom: 2px gray solid;">
                <tr>
                  <th class="text-center">filename</th>
                  <th class="text-center">label</th>
                  <th class="text-center">min_lat</th>
                  <th class="text-center">min_long</th>
                  <th class="text-center">max_lat</th>
                  <th class="text-center">max_long</th>
                  <th class="text-center">confidence</th>
                </tr>
              </thead>
              <tbody>
                <tr *ngFor="let row of _data">
                  <td>{{row[0]}}</td>
                  <td>{{row[1]}}</td>
                  <td>{{row[2] | number: '1.0-15'}}</td>
                  <td>{{row[3] | number: '1.0-15'}}</td>
                  <td>{{row[4] | number: '1.0-15'}}</td>
                  <td>{{row[5] | number: '1.0-15'}}</td>
                  <td>{{row[6] | number: '1.3-3'}}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <geospatial-globe-view *ngIf="_data && activeTab === 1"
            [data]="_data"
          ></geospatial-globe-view>
        </div>
      </ng-container>
    </ng-container>
  `,
})
export class VisualizeGeospatialComponent implements OnInit {
  @HostBinding('class') _cssClass = 'app-spinner-box';

  readonly _sourceOptions: AppSelectOptionData[] = AppSelectOptionData.fromList(
    [GeospatialDataRequest.Mode.CV_PREDICTION],
    ['CV Predictions'],
  );

  activeTab: number = 0;

  protected _editingQuery: boolean = false;

  protected _selectedSource: GeospatialDataRequest.Mode;
  protected _selectedModel: LibrarySelectorValue;
  protected _selectedAlbum: LibrarySelectorValue;
  protected _targetFilter: string;
  protected _minConfidence: number = 0.9;
  protected _toiLatitude: number = 14.945;
  protected _toiLongitude: number = -23.485;
  protected _toiMaxDistance: number = 2000;
  protected _query: string;
  protected _data: GeospatialDataResponse.Datum<GeospatialDataRequest.Mode.CV_PREDICTION>[];

  private _dataLoader: ReactiveLoader<GeospatialDataResponse, GeospatialDataRequest>;

  constructor(
    visualizeDataService: VisualizeDataService,
  ) {
    this._dataLoader = new ReactiveLoader(_ => visualizeDataService.fetchGeospatialData(_));

    this._dataLoader.subscribe(response => {
      this._data = response.data;
    });
  }

  ngOnInit(): void {
    this._updateGeospatialQuery();
  }

  protected _updateGeospatialQuery() {
    if (this._editingQuery) {
      return;
    }

    this._query = `
      SELECT * FROM predictions
      WHERE ST_DWithin(geom::geography, ST_GeogFromText('SRID=4326;Point(${this._toiLatitude} ${this._toiLongitude})'), ${this._toiMaxDistance})
      ${this._selectedModel ? 'AND model_id = ' + quote(this._selectedModel.id) : ''}
      ${this._selectedAlbum ? 'AND album_id = ' + quote(this._selectedAlbum.id) : ''}
      ${this._targetFilter ? 'AND target = ' + quote(this._targetFilter) : ''}
      ${typeof this._minConfidence === 'number' ? 'AND confidence >= ' + this._minConfidence.toString() : ''}
    `.replace(/\n\s*/g, '\n').trim();
  }

  protected _fetchData() {
    if (this._selectedSource) {
      this._dataLoader.load({
        mode: this._selectedSource,
        query: {
          where: this._query,
        },
      });
    } else {
      delete this._data;
    }
  }
}

function quote(s: string): string {
  return `'${s.replace('\'', '\'\'')}'`;
}

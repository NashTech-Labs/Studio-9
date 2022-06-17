import { OnChanges } from '@angular/core';
import { AbstractControl, FormArray, FormControl, FormGroup, ValidatorFn, Validators } from '@angular/forms';

import { Observable } from 'rxjs/Observable';
import { Subscription } from 'rxjs/Subscription';

import config from '../config';
import { LibrarySelectorCustomLoader } from '../core/components/library-selector.component';
import { IAsset, IAssetListRequest, IBackendList } from '../core/interfaces/common.interface';
import { ITable, ITableColumn } from '../tables/table.interface';
import { AppValidators } from '../utils/validators';

import { IFlow, IFlowstep } from './flow.interface';
import { FlowService } from './flow.service';

export abstract class FlowstepEditOptionsComponent implements OnChanges {
  config = config;
  abstract form: FormGroup;
  abstract flow: IFlow;

  readonly flowTablesLoader: LibrarySelectorCustomLoader = {
    name: 'Flow',
    entity: IAsset.Type.TABLE,
    list: (query: IAssetListRequest): Observable<IBackendList<ITable>> => {
      return this.flow
        ? this.flows.getTables(this.flow.id).map(list => {
          list.data = list.data.filter(_ => _.name.includes(query.search));
          list.count = list.data.length;

          return list;
        })
        : Observable.of({
          data: [],
          count: 0,
        });
    },
  };

  protected formSubscription: Subscription;
  protected type: string;

  constructor(protected flows: FlowService) {
  }

  ngOnChanges() {
    this._enableForm(this.form);
  }

  numericFilteringFn(column: ITableColumn) {
    return config.table.column.dataType.isNumeric[column.dataType];
  }

  protected _enableForm(form: FormGroup): void {
    let idControl = <FormControl> this.form.controls['id'];
    (<FormArray> form.controls['input']).controls.forEach((ctrl: FormControl) => {
      if (ctrl) {
        (idControl.value ? ctrl.disable() : ctrl.enable());
      }
    });
    // @todo: isn' there a proper way to make a for by elements?
    Object.keys((<FormGroup> form.controls['options']).controls).forEach((ctrlName: string) => {
      let ctrl = form.controls['options'].get(ctrlName);
      if (ctrl) {
        if (idControl.value) {
          ctrl.disable();
        } else {
          ctrl.enable();
        }
      }
    });
  }

  static prepareFlowstepForm(type: IFlowstep.Type, name?: string): FormGroup {
    return new FormGroup({
      id: new FormControl(), // hidden (for edit mode only)
      type: new FormControl(type, Validators.required), // add Validators.number
      name: new FormControl(name || '', Validators.required),
      input: FlowstepEditOptionsComponent.getFlowStepInputForm(type),
      output: new FormControl('', Validators.required),
      options: FlowstepEditOptionsComponent.getFlowStepOptionsForm(type),
    });
  }

  private static getFlowStepInputForm(type: IFlowstep.Type): FormArray {
    switch (type) {
      case IFlowstep.Type.join:
      case IFlowstep.Type.geojoin:
        return new FormArray([new FormControl(null, Validators.required), new FormControl(null, Validators.required)]);
      default:
        return new FormArray([new FormControl(null, Validators.required)]);
    }
  }

  private static getFlowStepOptionsForm(type: string): FormGroup {
    switch (type) {
      case config.flowstep.type.values.aggregate:
        return new FormGroup({
          name: new FormControl('', Validators.required), // new column name
          operator: new FormControl(null, Validators.required),
          operandColumn: new FormControl(null, Validators.required),
          groupByColumns: new FormControl([], Validators.required),
        });
      case config.flowstep.type.values.cluster:
        return new FormGroup({
          type: new FormControl(config.flowstep.option.cluster.type.values.K_MEANS, Validators.required),
          groups: new FormControl('1', Validators.compose([AppValidators.number, Validators.required])),
          iterations: new FormControl('1', Validators.compose([AppValidators.number, Validators.required])),
          columns: new FormControl([], Validators.required),
          passColumns: new FormArray([new FormGroup({
            newColumnName: new FormControl(null),
            columnName: new FormControl(null),
            tableReference: new FormControl(null),
            tmp: new FormControl(''), // tableReferrer+colName
          })]),
        });
      case config.flowstep.type.values.join:
        return new FormGroup({
          type: new FormControl(config.flowstep.option.join.type.values.NORMAL),
          leftPrefix: new FormControl('l_alias', AppValidators.notEqual('options.rightPrefix', 'Right Table Alias')),
          rightPrefix: new FormControl('r_alias', AppValidators.notEqual('options.leftPrefix', 'Left Table Alias')),
          columns: new FormArray([new FormGroup({
            from: new FormControl(null, Validators.required),
            to: new FormControl(null, Validators.required),
          })]),
          passColumns: new FormArray([new FormGroup({
            newColumnName: new FormControl(null),
            columnName: new FormControl(null),
            tableReference: new FormControl(null),
            tmp: new FormControl(''), // tableReferrer+colName
          })]),
        });
      case config.flowstep.type.values.filter:
        return new FormGroup({
          conditions: new FormArray([new FormGroup({
            column: new FormControl(null, Validators.required),
            operator: new FormControl(config.flowstep.option.filter.operator.values.LT, Validators.required),
            value: new FormControl(null, Validators.required),
            operatorGroup: new FormControl(config.flowstep.option.filter.operatorGroup.values.AND, Validators.required),
          })]),
          passColumns: new FormArray([new FormGroup({
            newColumnName: new FormControl(null),
            columnName: new FormControl(null),
            tableReference: new FormControl(null),
            tmp: new FormControl(''), // tableReferrer+colName
          })]),
        });
      case config.flowstep.type.values.insert:
        return new FormGroup({
          name: new FormControl('', Validators.required),
          formula: new FormControl('', Validators.required),
          table: new FormControl(), // required for insertValidation Request
          passColumns: new FormArray([new FormGroup({
            newColumnName: new FormControl(null),
            columnName: new FormControl(null),
            tableReference: new FormControl(null),
            tmp: new FormControl(''), // tableReferrer+colName
          })]),
        });
      // case config.flowstep.type.values.MAP: //@todo
      case config.flowstep.type.values.window:
        const aggregatorControl = new FormControl(config.flowstep.option.window.aggregator.list[0], Validators.required);
        const ifControlIsAvailable = (controlName: string, validator: ValidatorFn): ValidatorFn => {
          return (control: AbstractControl) => {
            const availableOptions = (config.flowstep.option.window.aggregator.options[aggregatorControl.value] || []);
            return (availableOptions.indexOf(controlName) > -1)
              ? validator(control)
              : null;
          };
        };
        const ifAggregatorIs = (aggregatorNames: string[], validator: ValidatorFn): ValidatorFn => {
          return (control: AbstractControl) => {
            return (aggregatorNames.indexOf(aggregatorControl.value) >= 0)
              ? validator(control)
              : null;
          };
        };

        const formGroup = new FormGroup({
          aggregator: aggregatorControl,
          newColName: new FormControl('', Validators.required), // new column name
          aggregatorArg: new FormControl(null, ifControlIsAvailable('aggregatorArg', Validators.required)),
          percentile: new FormControl(null, ifControlIsAvailable('percentile', Validators.compose([
            Validators.required,
            AppValidators.float,
            AppValidators.min(0),
            AppValidators.max(1),
          ]))),
          orderBy: new FormControl([]),
          partitionBy: new FormControl([]),
          windowLowerBound: new FormControl(null, AppValidators.number),
          windowUpperBound: new FormControl(null, AppValidators.number),
          isDesc: new FormControl(false),
          withinGroupExpression: new FormControl([], [
            ifAggregatorIs([config.flowstep.option.window.aggregator.values.PERCENTILE_CONT, config.flowstep.option.window.aggregator.values.PERCENTILE_DISC], Validators.compose([
              Validators.required,
              Validators.minLength(1),
              Validators.maxLength(1),
            ])),
          ]),
          listaggDelimiter: new FormControl(''),
          ntileGroupsCount: new FormControl(null, ifControlIsAvailable('ntileGroupsCount', Validators.compose([Validators.required, AppValidators.number]))),
          offset: new FormControl('1', [
              ifControlIsAvailable('offset', Validators.compose([
                AppValidators.number,
                AppValidators.min(0),
              ])),
              ifAggregatorIs([config.flowstep.option.window.aggregator.values.NTH_VALUE], Validators.compose([
                AppValidators.number,
                AppValidators.min(1),
              ])),
            ],
          ),
          ignoreNulls: new FormControl(false),
          respectNulls: new FormControl(true),
          passColumns: new FormArray([new FormGroup({
            newColumnName: new FormControl(null),
            columnName: new FormControl(null),
            tableReference: new FormControl(null),
            tmp: new FormControl(''), // tableReferrer+colName
          })]),
        });

        aggregatorControl.valueChanges.subscribe(() => {
          Object.keys(formGroup.controls).forEach((key) => {
            key === 'aggregator' || formGroup.controls[key].updateValueAndValidity();
          });
        });

        return formGroup;
      case config.flowstep.type.values.query:
        return new FormGroup({
          expression: new FormControl('', Validators.required),
          inputAliases: new FormArray([new FormControl('Table1', Validators.required)]),
        });
      case config.flowstep.type.values.map:
        return new FormGroup({
          changes: new FormArray([new FormGroup({
            name: new FormControl(null, Validators.required),
            value: new FormControl(null, Validators.required),
          })], Validators.minLength(1)),
          onlyRenamed: new FormControl(true, Validators.required),
        });
      case config.flowstep.type.values.geojoin:
        return new FormGroup({
          leftPrefix: new FormControl('l_alias', AppValidators.notEqual('options.rightPrefix', 'Right Table Alias')),
          rightPrefix: new FormControl('r_alias', AppValidators.notEqual('options.leftPrefix', 'Left Table Alias')),
          joinConditions: new FormArray([
            new FormGroup({
              left: new FormGroup({
                geoType: new FormControl(config.flowstep.option.geojoin.geometry.values.POINT, Validators.required),
                coordinates: new FormArray([
                  new FormGroup({
                    lat: new FormControl(null, Validators.required),
                    lon: new FormControl(null, Validators.required),
                  }),
                ], Validators.minLength(1)),
              }),
              right: new FormGroup({
                geoType: new FormControl(config.flowstep.option.geojoin.geometry.values.POINT, Validators.required),
                coordinates: new FormArray([
                  new FormGroup({
                    lat: new FormControl(null, Validators.required),
                    lon: new FormControl(null, Validators.required),
                  }),
                ], Validators.minLength(1)),
              }),
              relation: new FormGroup({
                relType: new FormControl(config.flowstep.option.geojoin.relation.values.ST_DISTANCE, Validators.required),
                operator: new FormControl(config.flowstep.option.geojoin.operator.values.LT, Validators.required),
                value: new FormControl(null, Validators.required),
                relationBase: new FormControl(config.flowstep.option.geojoin.relationBase.values.LEFT, Validators.required),
              }),
            }),
          ], Validators.minLength(1)),
          passColumns: new FormArray([new FormGroup({
            newColumnName: new FormControl(null),
            columnName: new FormControl(null),
            tableReference: new FormControl(null),
            tmp: new FormControl(''), // tableReferrer+colName
          })]),
        });
      default:
        return new FormGroup({});
    }
  }
}

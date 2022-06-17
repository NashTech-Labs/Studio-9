import { Injectable } from '@angular/core';

import * as _ from 'lodash';
import { Observable } from 'rxjs/Observable';

import { IObjectId, TObjectId } from '../core/interfaces/common.interface';
import { DataService } from '../core/services/data.service';
import { EventService, IEvent } from '../core/services/event.service';
import { AppHttp } from '../core/services/http.service';
import { NotificationService } from '../core/services/notification.service';

import { IBackendFlowstep, IClusteringResult, IFlowstep } from './flow.interface';

@Injectable()
export class FlowstepService extends DataService {

  constructor(
    protected http: AppHttp,
    protected events: EventService,
    private notifications: NotificationService,
  ) {
    super(events);
  }

  get(flowId: TObjectId, id: TObjectId): Observable<IFlowstep> {
    // GET '/flows/:flowId/steps/:id'
    const observable = this.http.get('flows/' + flowId + '/steps/' + id, null, {
      deserialize: FlowstepService._deserializeFlowstep,
    });

    return AppHttp.execute(observable,
      (data: IFlowstep) => {
        this._data.view = data;
        this._observables.view.next(data);
      },
    );
  }

  create(flowId: TObjectId, item: IFlowstep.Create): Observable<IFlowstep> {
    const data = _.cloneDeep(item);

    // POST '/flows/:flowId/steps'
    const observable = this.http.post('flows/' + flowId + '/steps', data, null, {
      serialize: FlowstepService._serializeFlowstep,
      deserialize: FlowstepService._deserializeFlowstep,
    });

    return AppHttp.execute(observable,
      (data: IFlowstep) => {
        this._data.edit = data;
        this._observables.edit.next(data);

        // RESERVED // this._data.list && this.list() // experimental // refresh the list
        this.events.emit(IEvent.Type.UPDATE_FLOW_STEPS, flowId);
        this.notifications.create('Flowstep successfully created: ' + data.name);
      },
    );
  }

  update(flowId: TObjectId, stepId: TObjectId, item: IFlowstep.Update): Observable<IFlowstep> {
    const data = ['name'].reduce((acc, prop) => {
      acc[prop] = item[prop];
      return acc;
    }, {});

    // PUT '/flows/:flowId/steps/:id'
    const observable = this.http.put('flows/' + flowId + '/steps/' + stepId, data, null, {
      deserialize: FlowstepService._deserializeFlowstep,
    });

    return AppHttp.execute(observable,
      (data: IFlowstep) => {
        this._data.view = data;
        this._observables.view.next(data);

        // RESERVED // this._data.list && this.list() // experimental // refresh the list
        this.events.emit(IEvent.Type.UPDATE_FLOW_STEPS, flowId);
        this.notifications.create('Flowstep updated: ' + data.name);
      },
    );
  }

  'delete'(flowId: TObjectId, item: IFlowstep): Observable<IObjectId> {
    // DELETE '/flows/:flowId/steps/:id'
    const observable = this.http.delete('flows/' + flowId + '/steps/' + item.id);

    return AppHttp.execute(observable,
      (data: IObjectId) => {
        // update view item, if needed
        if ((this._data.view || {}).id === data.id) {
          this._data.view = null;
          this._observables.view.next(this._data.view);
        }

        // RESERVED // this._data.list && this.list() // experimental // refresh the list
        this.events.emit(IEvent.Type.UPDATE_FLOW_STEPS, flowId);
        this.notifications.create('Flowstep deleted: ' + item.name);
      },
    );
  }

  // TODO / TEMP TEMP TEMP / upgrade endpoint and method
  sqlParsing(data) {
    // params: @data: {sql: sql, overwrite: true}
    return this.http.post('dataset/sql/validate', data);
  }

  // TODO / TEMP TEMP TEMP / upgrade endpoint and method
  sqlParsingInsert(data) {
    // params: @data: {expression: string, datasetId: string, newColumnName: string}
    return this.http.post('dataset/sql/validateinsert', data);
  }

  clusteringResults(flowId: TObjectId, flowstepId: TObjectId): Observable<IClusteringResult> {
    return this.http.get('flows/' + flowId + '/steps/' + flowstepId + '/clusters');
  }

  static _serializeFlowstep(flowstep: IFlowstep.Create): IBackendFlowstep.Create {
    return {
      name: flowstep.name,
      inputIDs: flowstep.input,
      outputName: flowstep.output,
      transformer: FlowstepService._serializeTransformer(flowstep),
    };
  }

  static _deserializeFlowstep(backendStep: IBackendFlowstep): IFlowstep {
    return {
      id: backendStep.id,
      name: backendStep.name,
      type: FlowstepService._deserializeTransformerType(backendStep.transformer),
      created: backendStep.created,
      updated: backendStep.updated,
      input: backendStep.input,
      output: backendStep.output,
      status: backendStep.status,
      options: FlowstepService._deserializeTransformer(backendStep.transformer),
    };
  }

  static _serializeJoinType(options: IFlowstep.JoinTransformer): IBackendFlowstep.Type {
    if (options.type === IFlowstep.JoinType.NORMAL) {
      return IBackendFlowstep.Type.JOIN;
    }
    return IBackendFlowstep.Type.FUZZYJOIN;
  }

  static _deserializeTransformerType(transformer: IBackendFlowstep.Transformer): IFlowstep.Type {
    switch (transformer.transformerType) {
      case IBackendFlowstep.Type.INSERT:
        return IFlowstep.Type.insert;
      case IBackendFlowstep.Type.FILTER:
        return IFlowstep.Type.filter;
      case IBackendFlowstep.Type.AGGREGATE:
        return IFlowstep.Type.aggregate;
      case IBackendFlowstep.Type.CLUSTER:
        return IFlowstep.Type.cluster;
      case IBackendFlowstep.Type.MAP:
        return IFlowstep.Type.map;
      case IBackendFlowstep.Type.QUERY:
        return IFlowstep.Type.query;
      case IBackendFlowstep.Type.WINDOW:
        return IFlowstep.Type.window;
      case IBackendFlowstep.Type.GEOJOIN:
        return IFlowstep.Type.geojoin;
      case IBackendFlowstep.Type.JOIN:
      case IBackendFlowstep.Type.FUZZYJOIN:
        return IFlowstep.Type.join;
      default:
        throw new Error('Unknown Transformer Type');
    }
  }

  static _serializeTransformer(flowstep: IFlowstep | IFlowstep.Create): IBackendFlowstep.Transformer {
    const passColumns = (<IFlowstep.InsertTransformer> flowstep.options).passColumns;
    switch (flowstep.type) {
      case IFlowstep.Type.insert:
        const insertOptions = <IFlowstep.InsertTransformer> flowstep.options;
        return <IBackendFlowstep.InsertTransformer> {
          transformerType: IBackendFlowstep.Type.INSERT,
          newColumnName: insertOptions.name,
          expression: insertOptions.formula,
          passColumns,
        };
      case IFlowstep.Type.aggregate:
        const aggregateOptions = <IFlowstep.AggregateTransformer> flowstep.options;
        return <IBackendFlowstep.AggregateTransformer> {
          transformerType: IBackendFlowstep.Type.AGGREGATE,
          aggInfo: {
            aggregateColumn: aggregateOptions.name,
            groupByColumns: aggregateOptions.groupByColumns,
            operandColumn: aggregateOptions.operandColumn,
            operator: aggregateOptions.operator,
          },
        };
      case IFlowstep.Type.join:
        const joinOptions = <IFlowstep.JoinTransformer> flowstep.options;
        return <IBackendFlowstep.JoinTransformer> {
          transformerType: FlowstepService._serializeJoinType(joinOptions),
          joinColumns: joinOptions.columns.map((item: IFlowstep.JoinCondition) => {
            return {
              leftColumnName: item.from,
              rightColumnName: item.to,
            };
          }),
          passColumns,
        };

      case IFlowstep.Type.cluster:
        const clusterOptions = <IFlowstep.ClusterTransformer> flowstep.options;
        return <IBackendFlowstep.ClusterTransformer> {
          transformerType: IBackendFlowstep.Type.CLUSTER,
          clusteringType: clusterOptions.type,
          numClusters: parseInt(clusterOptions.groups),
          numIterations: parseInt(clusterOptions.iterations),
          columns: clusterOptions.columns,
          passColumns,
        };

      case IFlowstep.Type.query:
        const queryOptions = <IFlowstep.QueryTransformer> flowstep.options;
        return <IBackendFlowstep.QueryTransformer> {
          transformerType: IBackendFlowstep.Type.QUERY,
          expression: queryOptions.expression,
          inputAliases: queryOptions.inputAliases,
        };

      case IFlowstep.Type.filter:
        const filterOptions = <IFlowstep.FilterTransformer> flowstep.options;
        return <IBackendFlowstep.FilterTransformer> {
          transformerType: IBackendFlowstep.Type.FILTER,
          filters: filterOptions.conditions.map((item: IFlowstep.FilterCondition) => {
            return {
              columnName: item.column,
              relationalOperator: item.operator,
              logicalOperator: item.operatorGroup,
            };
          }),
        };

      case IFlowstep.Type.window:
        const windowOptions = <IFlowstep.WindowTransformer> flowstep.options;

        if (['listagg', 'percentile_cont', 'percentile_disc'].indexOf(windowOptions.aggregator) > -1) {
          delete windowOptions.orderBy;
        } else {
          delete windowOptions.withinGroupExpression;
        }

        if (['first_value', 'last_value', 'lag', 'lead', 'nth_value'].indexOf(windowOptions.aggregator) === -1) {
          delete windowOptions.respectNulls;
          delete windowOptions.ignoreNulls;
        } else {
          if (windowOptions.ignoreNulls === false) {
            delete windowOptions.ignoreNulls;
          }
          if (windowOptions.respectNulls === false) {
            delete windowOptions.respectNulls;
          }
        }

        if (['lag', 'lead', 'nth_value'].indexOf(windowOptions.aggregator) === -1) {
          delete windowOptions.offset;
        }

        if (windowOptions.aggregator !== 'listagg') {
          delete windowOptions.listaggDelimiter;
        }

        if (windowOptions.aggregator === 'percentile_disc' || windowOptions.aggregator === 'percentile_cont') {
          delete windowOptions.aggregatorArg;
          const percentile = parseFloat(<string> windowOptions.percentile);
          if (isNaN(percentile)) {
            delete windowOptions.percentile;
          } else {
            windowOptions.percentile = percentile;
          }
        } else {
          delete windowOptions.percentile;
        }

        if (windowOptions.aggregator !== 'ntile') {
          delete windowOptions.ntileGroupsCount;
        } else {
          delete windowOptions.aggregatorArg;
          const ntileGroupsCount = parseInt(<string> windowOptions.ntileGroupsCount);
          if (isNaN(ntileGroupsCount)) {
            delete windowOptions.ntileGroupsCount;
          } else {
            windowOptions.ntileGroupsCount = ntileGroupsCount;
          }
        }

        if (['median', 'ratio_to_report'].indexOf(windowOptions.aggregator) > -1) {
          delete windowOptions.orderBy;
          delete windowOptions.isDesc;
        }

        const offset = parseInt(<string> windowOptions.offset);
        if (isNaN(offset)) {
          delete windowOptions.offset;
        } else {
          windowOptions.offset = offset;
        }
        const windowLowerBound = parseInt(<string> windowOptions.windowLowerBound);
        if (isNaN(windowLowerBound)) {
          delete windowOptions.windowLowerBound;
        } else {
          windowOptions.windowLowerBound = windowLowerBound;
        }
        const windowUpperBound = parseInt(<string> windowOptions.windowUpperBound);
        if (isNaN(windowUpperBound)) {
          delete windowOptions.windowUpperBound;
        } else {
          windowOptions.windowUpperBound = windowUpperBound;
        }
        return <IBackendFlowstep.WindowTransformer> Object.assign({ transformerType: IBackendFlowstep.Type.WINDOW },
          windowOptions, { passColumns });

      case IFlowstep.Type.map:
        return <IBackendFlowstep.MapTransformer> Object.assign({ transformerType: IBackendFlowstep.Type.MAP },
          flowstep.options);

      case IFlowstep.Type.geojoin:
        return <IBackendFlowstep.GeoJoinTransformer> Object.assign({ transformerType: IBackendFlowstep.Type.GEOJOIN },
          flowstep.options, { passColumns });

      default:
        throw new Error('Unknown flowstep type');
    }
  }

  static _deserializeTransformer(transformer: IBackendFlowstep.Transformer): IFlowstep.Transformer {
    const passColumns = (<IBackendFlowstep.InsertTransformer> transformer).passColumns;
    switch (transformer.transformerType) {
      case IBackendFlowstep.Type.INSERT:
        const insertTransformer = <IBackendFlowstep.InsertTransformer> transformer;
        return {
          name: insertTransformer.newColumnName,
          formula: insertTransformer.expression,
          passColumns,
        };
      case IBackendFlowstep.Type.AGGREGATE:
        const aggregateTransformer = <IBackendFlowstep.AggregateTransformer> transformer;
        return {
          name: aggregateTransformer.aggInfo.aggregateColumn,
          groupByColumns: aggregateTransformer.aggInfo.groupByColumns,
          operandColumn: aggregateTransformer.aggInfo.operandColumn,
          operator: aggregateTransformer.aggInfo.operator,
        };
      case IBackendFlowstep.Type.FILTER:
        const filterTransformer = <IBackendFlowstep.FilterTransformer> transformer;
        return {
          conditions: filterTransformer.filters.map(_ => {
            return {
              column: _.columnName,
              operator: _.relationalOperator,
              value: _.value,
              operatorGroup: _.logicalOperator,
            };
          }),
          passColumns,
        };
      case IBackendFlowstep.Type.MAP:
        return <IFlowstep.GeoJoinTransformer> _.omit(transformer, 'transformerType');
      case IBackendFlowstep.Type.CLUSTER:
        const clusterTransformer = <IBackendFlowstep.ClusterTransformer> transformer;
        return <IFlowstep.ClusterTransformer> {
          type: clusterTransformer.clusteringType,
          groups: `${clusterTransformer.numClusters}`,
          iterations: `${clusterTransformer.numIterations}`,
          columns: clusterTransformer.columns,
          passColumns,
        };
      case IBackendFlowstep.Type.WINDOW:
        return <IFlowstep.GeoJoinTransformer> Object.assign(_.omit(transformer, 'transformerType'), {
          passColumns,
        });
      case IBackendFlowstep.Type.QUERY:
        return <IFlowstep.GeoJoinTransformer> _.omit(transformer, 'transformerType');
      case IBackendFlowstep.Type.GEOJOIN:
        return <IFlowstep.GeoJoinTransformer> Object.assign(_.omit(transformer, 'transformerType'), {
          passColumns,
        });
      case IBackendFlowstep.Type.JOIN:
      case IBackendFlowstep.Type.FUZZYJOIN:
        const joinTransformer = <IBackendFlowstep.JoinTransformer> transformer;
        return <IFlowstep.JoinTransformer> {
          leftPrefix: joinTransformer.leftPrefix,
          rightPrefix: joinTransformer.rightPrefix,
          columns: joinTransformer.joinColumns.map(_ => {
            return {
              from: _.leftColumnName,
              to: _.rightColumnName,
            };
          }),
          passColumns,
        };
    }
  }
}

declare module '../core/services/event.service' {
  export namespace IEvent {
    export const enum Type {
      UPDATE_FLOW_STEPS = 'UPDATE_FLOW_STEPS',
    }
  }
}

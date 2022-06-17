import { Injectable, Injector } from '@angular/core';

import * as _ from 'lodash';
import { Observable } from 'rxjs/Observable';
import { forkJoin } from 'rxjs/observable/forkJoin';
import { of } from 'rxjs/observable/of';

import config from '../config';
import { IAsset, IBackendList, IListRequest } from '../core/interfaces/common.interface';
import { ParameterDefinition, ParameterValues } from '../core/interfaces/params.interface';
import { AssetService } from '../core/services/asset.service';
import { IEvent } from '../core/services/event.service';

import { Pipeline, PipelineCreate, PipelineDataType, PipelineOperator, PipelineUpdate } from './pipeline.interfaces';

@Injectable()
export class PipelineService extends AssetService<IAsset.Type.PIPELINE, Pipeline, PipelineCreate, PipelineUpdate> {

  protected readonly _createEventType: IEvent.Type = IEvent.Type.CREATE_PIPELINE;
  protected readonly _updateEventType: IEvent.Type = IEvent.Type.UPDATE_PIPELINE;
  protected readonly _deleteEventType: IEvent.Type = IEvent.Type.DELETE_PIPELINE;
  protected readonly _listUpdateEventType: IEvent.Type = IEvent.Type.UPDATE_PIPELINE_LIST;

  constructor(
    injector: Injector,
  ) {
    super(injector, IAsset.Type.PIPELINE);
  }

  listOperators(
    params: IListRequest = {
      page: 1,
      page_size: 1000,
    },
  ): Observable<IBackendList<PipelineOperator>> {
    const fixTypeMissingFields = (t: PipelineDataType): void => {
      if (this.isPipelineDataTypeComplex(t)) {
        if (t.parents) {
          t.parents.forEach(fixTypeMissingFields);
        } else {
          t.parents = [];
        }
        if (t.typeArguments) {
          t.typeArguments.forEach(fixTypeMissingFields);
        } else {
          t.typeArguments = [];
        }
      }
    };

    return this._http.get(`pipeline-operators`, params, {
      deserialize: (ops: IBackendList<PipelineOperator>) => {
        ops.data.forEach(op => {
          [...op.inputs, ...op.outputs].forEach(i => {
            fixTypeMissingFields(i.type);
          });
        });
        return ops;
      },
    });
  }

  listOperatorCategories(): Observable<PipelineOperator.Category[]> {
    return this._http.get('config/operator-categories');
  }

  canConnectIOs(output: PipelineOperator.Output, input: PipelineOperator.Input): boolean {
    return this.dataTypesAreCompatible(output.type, input.type, input.covariate);
  }

  isParameterAvailable = function(
    definition: ParameterDefinition,
    values: ParameterValues,
    pipelineParameters: {[key: string]: string} = null,
  ): boolean {
    const fixedDefinition = pipelineParameters
      ? {
        ...definition,
        conditions: _.omitBy(definition.conditions, (_c, paramName) => paramName in pipelineParameters),
      }
      : definition;

    return ParameterDefinition.isParameterAvailable(fixedDefinition, values);
  };

  // this is the same as baile.services.pipeline.PipelineValidator#dataTypesAreCompatible
  dataTypesAreCompatible(
    from: PipelineDataType,
    to: PipelineDataType,
    covariate: boolean,
  ): boolean {
    if (this.isPipelineDataTypeComplex(from) && this.isPipelineDataTypeComplex(to)) {
      const findCompatibleFrom = (from: PipelineDataType.Complex): PipelineDataType.Complex => {
        if (from.definition === to.definition) {
          return from;
        } else if (covariate) {
          return (from.parents || []).map(findCompatibleFrom).find(_ => !!_);
        } else {
          return null;
        }
      };

      const compatibleFrom = findCompatibleFrom(from);

      if (compatibleFrom) {
        const typeArgumentsFrom = compatibleFrom.typeArguments;
        const typeArgumentsTo = to.typeArguments;

        return typeArgumentsFrom.length === typeArgumentsTo.length &&
          _.zip(typeArgumentsFrom, typeArgumentsTo)
            .every(([typeArgumentFrom, typeArgumentTo]) => this.dataTypesAreCompatible(
              typeArgumentFrom,
              typeArgumentTo,
              covariate,
            ));
      } else {
        return false;
      }
    } else {
      return _.isEqual(from, to);
    }
  }

  isPipelineDataTypePrimitive(type: PipelineDataType): type is PipelineDataType.Primitive {
    return typeof type === 'string';
  }

  isPipelineDataTypeComplex(type: PipelineDataType): type is PipelineDataType.Complex {
    return typeof type !== 'string';
  }

  public listAllOperators(): Observable<PipelineOperator[]> {
    const fetcher = (page: number): Observable<IBackendList<PipelineOperator>> => {
      return this.listOperators({page: page, page_size: config.listAllChunkSize});
    };
    return fetcher(1).flatMap((firstResponse: IBackendList<PipelineOperator>) => {
      if (firstResponse.count <= firstResponse.data.length) {
        return of(firstResponse.data);
      } else {
        const pagesToFetch = Array(Math.ceil(firstResponse.count / config.listAllChunkSize - 1)).fill(0).map((_, i) => i + 2);
        const observables = pagesToFetch.map(fetcher);
        return forkJoin(observables).map((responses: IBackendList<PipelineOperator>[]) => {
          return [...firstResponse.data].concat(...responses.map(_ => _.data));
        });
      }
    });
  }
}

declare module '../core/services/event.service' {
  export namespace IEvent {
    export const enum Type {
      CREATE_PIPELINE = 'CREATE_PIPELINE',
      UPDATE_PIPELINE = 'UPDATE_PIPELINE',
      DELETE_PIPELINE = 'DELETE_PIPELINE',
      UPDATE_PIPELINE_LIST = 'UPDATE_PIPELINE_LIST',
    }
  }
}

import { Injectable, Injector } from '@angular/core';

import config from '../config';
import { IAsset, IAssetReference } from '../core/interfaces/common.interface';
import { AssetService } from '../core/services/asset.service';
import { IEvent } from '../core/services/event.service';

import {
  ExperimentType,
  IExperiment,
  IExperimentCreate,
  IExperimentFull,
  IExperimentUpdate,
} from './experiment.interfaces';

@Injectable()
export class ExperimentService
  extends AssetService<IAsset.Type.EXPERIMENT, IExperiment, IExperimentCreate, IExperimentUpdate, IExperimentFull> {

  private static _childAssetsExtractors: {[K in ExperimentType]?: (e: IExperimentFull) => IAssetReference[]} = {};

  protected readonly _createEventType = IEvent.Type.CREATE_EXPERIMENT;
  protected readonly _updateEventType = IEvent.Type.UPDATE_EXPERIMENT;
  protected readonly _deleteEventType = IEvent.Type.DELETE_EXPERIMENT;
  protected readonly _listUpdateEventType = IEvent.Type.UPDATE_EXPERIMENT_LIST;

  constructor(
    injector: Injector,
  ) {
    super(injector, IAsset.Type.EXPERIMENT);
  }

  protected _hasProcess(item: IExperiment): boolean {
    return config.experiments.status.hasProcess[item.status];
  }

  protected _getChildAssets(item: IExperimentFull): IAssetReference[] {
    return item.type in ExperimentService._childAssetsExtractors
      ? ExperimentService._childAssetsExtractors[item.type](item)
      : [];
  }

  static registerChildAssetsExtractor(experimentType: ExperimentType, f: (e: IExperimentFull) => IAssetReference[]) {
    ExperimentService._childAssetsExtractors[experimentType] = f;
  }
}

declare module '../core/services/event.service' {
  export namespace IEvent {
    export const enum Type {
      CREATE_EXPERIMENT = 'CREATE_EXPERIMENT',
      UPDATE_EXPERIMENT = 'UPDATE_EXPERIMENT',
      DELETE_EXPERIMENT = 'DELETE_EXPERIMENT',
      UPDATE_EXPERIMENT_LIST = 'UPDATE_EXPERIMENT_LIST',
    }
  }
}

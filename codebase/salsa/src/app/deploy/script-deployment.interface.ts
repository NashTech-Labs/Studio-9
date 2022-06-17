import { IAsset, TObjectId } from '../core/interfaces/common.interface';

export interface IScriptDeployment extends IAsset {
  status: IScriptDeployment.Status;
  mode: IScriptDeployment.Mode;
  hardwareMode: IScriptDeployment.HardwareMode;
  params: IScriptDeployment.Params;
}

export namespace IScriptDeployment {
  export enum Status {
    PREPARING = 'PREPARING',
    READY = 'READY',
  }

  export enum Mode {
    CV_PREDICTION = 'CV_PREDICTION',
    CV_3STL_DETECTION = 'CV_3STL_DETECTION',
  }

  export enum HardwareMode {
    CPU = 'CPU',
    CUDA = 'CUDA',
    INTEL_NCS2 = 'INTEL_NCS2',
  }

  export type Params = CV3STLDetectionParams;

  export interface CV3STLDetectionParams {
    localizerModelId: TObjectId;
    classifierModelId: TObjectId;
  }
}

export interface IScriptDeploymentCreate {
  name: string;
  description?: string;
  mode: IScriptDeployment.Mode;
  hardwareMode: IScriptDeployment.HardwareMode;
  params: IScriptDeployment.Params;
}

export interface IScriptDeploymentUpdate {
  name?: string;
  description?: string;
}

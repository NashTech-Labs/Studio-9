import { ParameterDefinition } from '../core/interfaces/params.interface';

export interface ICVArchitecture {
  id: string;
  name: string;
  packageName: string;
  packageVersion?: string;
  module: string;
  className: string;
  needsConsumer: boolean;
  params?: ParameterDefinition[];
}

export interface ICVModelUnit {
  id: string;
  name: string;
  packageName: string;
  packageVersion?: string;
  module: string;
  className: string;
  isNeural: boolean;
  params?: ParameterDefinition[];
}

export interface ICVClassifier extends ICVModelUnit {
}

export interface ICVDetector extends ICVModelUnit {
}

export interface ICVDecoder extends ICVModelUnit {
}

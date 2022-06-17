import { IAlbum, IAlbumAugmentParams } from '../albums/album.interface';
import { IAsset, TObjectId } from '../core/interfaces/common.interface';

import { IConfusionMatrix } from './train.interface';

export interface IAugmentationSummary {
  count: number;
  augmentation: IAlbumAugmentParams.Augmentation;
}

export interface ICVAugmentationOptions {
  augmentations: IAlbumAugmentParams.Augmentation[];
  bloatFactor: number;
  prepareSampleAlbum: boolean;
}

export interface ClassReference {
  packageId?: string;
  moduleName: string;
  className: string;
}

export interface ICVModel extends IAsset {
  experimentId?: TObjectId;
  status: ICVModel.Status;
  modelType: CVModelType;
  classes?: string[];
  inLibrary?: boolean;
}

export interface ICVModelSummary {
  reconstructionLoss?: number;
  labels: string[];
  confusionMatrix?: IConfusionMatrix;
  mAP?: number;
}

export type CVModelType = CVModelType.Custom | CVModelType.TL;

export namespace CVModelType {
  export enum Type {
    CUSTOM = 'CUSTOM',
    TL = 'TL',
  }

  export interface Base {
    type: Type;
    labelMode?: IAlbum.LabelMode;
  }

  export interface Custom extends Base {
    type: Type.CUSTOM;
    classReference: ClassReference;
  }

  export enum TLType {
    CLASSIFICATION = 'CLASSIFICATION',
    LOCALIZATION = 'LOCALIZATION',
    AUTOENCODER = 'AUTOENCODER',
  }

  export type TL = TLBase & TLConsumer;

  export interface TLBase extends Base {
    type: Type.TL;
    tlType: TLType;
    architecture: string;
  }

  export type TLConsumer = (TLClassifier | TLDetector | TLDecoder);

  export interface TLClassifier {
    tlType: TLType.CLASSIFICATION;
    classifierType: string;
  }

  export interface TLDetector {
    tlType: TLType.LOCALIZATION;
    detectorType: string;
  }

  export interface TLDecoder {
    tlType: TLType.AUTOENCODER;
    decoderType: string;
  }
}

export interface ICVModelUpdate {
  name?: string;
  description?: string;
}

export interface ICVModelImport {
  name?: string;
}

export namespace ICVModel {
  export enum Status {
    ACTIVE = 'ACTIVE',
    SAVING = 'SAVING',
    TRAINING = 'TRAINING',
    PREDICTING = 'PREDICTING',
    ERROR = 'ERROR',
    CANCELLED = 'CANCELLED',
  }
}

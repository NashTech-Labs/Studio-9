import {
  IAsset,
  IListRequest,
  TObjectId,
} from '../core/interfaces/common.interface';
import { IJobTimeSpentSummary } from '../core/interfaces/time-spent.interface';

export interface IAugmentationTimeSpentSummary extends IJobTimeSpentSummary {
  dataLoadingTime: number;
  augmentationTime: number;
}

export interface IAlbum extends IAsset {
  type: IAlbum.Type;
  status: IAlbum.Status;
  video?: IVideo;
  inLibrary?: boolean;
  labelMode: IAlbum.LabelMode;
  locked?: boolean;
  augmentationTimeSpentSummary?: IAugmentationTimeSpentSummary;
}

export interface IPicture {
  id: TObjectId;
  albumId: TObjectId;
  created?: string;
  updated?: string;
  filename: string;
  filepath: string;
  filesize: number;
  tags: IPictureTag[];
  predictedTags?: IPicturePredictedTag[]; // Filled on TRAIN/PREDICT
  caption?: string;
  predictedCaption?: string;
  predictionsRendered?: boolean;
  originalPictureId?: TObjectId;
  augmentationsApplied?: IPictureAugmentation[];
}

export interface IVideo {
  //albumId: TObjectId;
  created?: string;
  updated?: string;
  filename: string;
  filepath: string;
  filesize: number;
}

export type IAlbumTagsSummary = {
  label: string;
  count: number;
}[];

export interface IPictureTag {
  label: string;
  area?: {
    top: number;
    left: number;
    height: number;
    width: number;
  };
}

export interface IPicturePredictedTag extends IPictureTag {
  confidence: number;
}

export interface IAlbumCreate {
  name: string;
  labelMode: IAlbum.LabelMode;
  description?: string;
  copyPicturesFrom?: TObjectId[];
  copyOnlyLabelledPictures?: boolean;
}

export interface IAlbumUpdate {
  name?: string;
  labelMode?: IAlbum.LabelMode;
  description?: string;
}

export interface IAlbumClone {
  name: string;
  copyOnlyLabelledPictures?: boolean;
  pictureIds?: TObjectId[];
}

export interface IAlbumUploadParams {
  filename: string;
  file: File;
}

export interface IAlbumAugmentParams {
  outputName: string;
  includeOriginalPictures: boolean;
  augmentations: IAlbumAugmentParams.Augmentation[];
  bloatFactor: number;
}

interface IS3BucketReference {
  AWSS3BucketId?: string;
  AWSRegion?: string;
  AWSS3BucketName?: string;
  AWSAccessKey?: string;
  AWSSecretKey?: string;
  AWSSessionToken?: string;
}

export interface IAlbumImportLabelsFromS3 extends IS3BucketReference {
  S3CSVPath: string;
}

export interface IAlbumImportFromS3 extends IS3BucketReference {
  S3ImagesPath: string;
  labels?: File;
  S3CSVPath?: string;
  applyLogTransformation?: boolean;
}

export interface IAlbumImportVideoFromS3 extends IS3BucketReference {
  S3VideoPath: string;
  frameRateDivider: number;
}

export interface IPictureSave {
  tags: IPictureTag[];
  caption?: string;
}

export interface IPictureSearchParams extends IListRequest {
  labels?: string[];
  augmentations?: IAlbum.AugmentationType[];
}

export namespace IAlbum {
  export enum Status {
    ACTIVE = 'ACTIVE',
    SAVING = 'SAVING',
    ERROR = 'ERROR',
    UPLOADING = 'UPLOADING',
  }

  export enum LabelMode {
    LOCALIZATION = 'LOCALIZATION',
    CLASSIFICATION = 'CLASSIFICATION',
  }

  export enum Type {
    SOURCE = 'SOURCE',
    DERIVED = 'DERIVED',
    TRAINRESULTS = 'TRAINRESULTS',
  }

  export enum AugmentationType {
    ROTATION = 'ROTATION',
    SHEARING = 'SHEARING',
    NOISING = 'NOISING',
    ZOOM_IN = 'ZOOM_IN',
    ZOOM_OUT = 'ZOOM_OUT',
    OCCLUSION = 'OCCLUSION',
    TRANSLATION = 'TRANSLATION',
    SALT_PEPPER = 'SALT_PEPPER',
    MIRRORING = 'MIRRORING',
    CROPPING = 'CROPPING',
    BLURRING = 'BLURRING',
    PHOTO_DISTORT = 'PHOTO_DISTORT',
  }
}

export namespace IAlbumAugmentParams {
  export type Augmentation =
    RotationAugmentation | ShearingAugmentation | NoisingAugmentation | ZoomInAugmentation | ZoomOutAugmentation |
    OcclusionAugmentation | TranslationAugmentation | SaltPepperAugmentation | MirroringAugmentation |
    CroppingAugmentation | BlurringAugmentation | PhotoDistortAugmentation;

  export interface AbstractAugmentation {
    augmentationType: IAlbum.AugmentationType;
    bloatFactor: number;
  }

  export interface RotationAugmentation extends AbstractAugmentation {
    augmentationType: IAlbum.AugmentationType.ROTATION;
    angles: number[];
    resize: boolean;
  }

  export interface ShearingAugmentation extends AbstractAugmentation  {
    augmentationType: IAlbum.AugmentationType.SHEARING;
    angles: number[];
    resize: boolean;
  }

  export interface NoisingAugmentation extends AbstractAugmentation  {
    augmentationType: IAlbum.AugmentationType.NOISING;
    noiseSignalRatios: number[];
  }

  export interface ZoomInAugmentation extends AbstractAugmentation  {
    augmentationType: IAlbum.AugmentationType.ZOOM_IN;
    ratios: number[];
    resize: boolean;
  }

  export interface ZoomOutAugmentation extends AbstractAugmentation  {
    augmentationType: IAlbum.AugmentationType.ZOOM_OUT;
    ratios: number[];
    resize: boolean;
  }

  export interface OcclusionAugmentation extends AbstractAugmentation  {
    augmentationType: IAlbum.AugmentationType.OCCLUSION;
    occAreaFractions: number[];
    mode: 'BACKGROUND' | 'ZERO';
    isSARAlbum: boolean;
    targetWindowSize?: number; //px
  }

  export interface TranslationAugmentation extends AbstractAugmentation  {
    augmentationType: IAlbum.AugmentationType.TRANSLATION;
    translateFractions: number[];
    mode: 'REFLECT' | 'CONSTANT';
    resize: boolean;
  }

  export interface SaltPepperAugmentation extends AbstractAugmentation  {
    augmentationType: IAlbum.AugmentationType.SALT_PEPPER;
    knockoutFractions: number[];
    pepperProbability?: number;
  }

  export interface MirroringAugmentation extends AbstractAugmentation  {
    augmentationType: IAlbum.AugmentationType.MIRRORING;
    flipAxes: (0 | 1 | 2)[];
  }

  export interface CroppingAugmentation extends AbstractAugmentation  {
    augmentationType: IAlbum.AugmentationType.CROPPING;
    cropAreaFractions: number[];
    cropsPerImage: number;
    resize: boolean;
  }

  export interface BlurringAugmentation extends AbstractAugmentation  {
    augmentationType: IAlbum.AugmentationType.BLURRING;
    sigmas: number[];
  }

  export interface PhotoDistortAugmentation extends AbstractAugmentation  {
    augmentationType: IAlbum.AugmentationType.PHOTO_DISTORT;
    alphaMin: number;
    alphaMax: number;
    deltaMax: number;
  }
}

export type IPictureAugmentation =
  IPictureAugmentation.RotationAugmentation |
  IPictureAugmentation.ShearingAugmentation |
  IPictureAugmentation.NoisingAugmentation |
  IPictureAugmentation.ZoomInAugmentation |
  IPictureAugmentation.ZoomOutAugmentation |
  IPictureAugmentation.OcclusionAugmentation |
  IPictureAugmentation.TranslationAugmentation |
  IPictureAugmentation.SaltPepperAugmentation |
  IPictureAugmentation.MirroringAugmentation |
  IPictureAugmentation.CroppingAugmentation |
  IPictureAugmentation.BlurringAugmentation |
  IPictureAugmentation.PhotoDistortAugmentation;

export namespace IPictureAugmentation {
  export interface AbstractAugmentation {
    augmentationType: IAlbum.AugmentationType;
    extraParams: {[K: string]: number};
  }

  export interface RotationAugmentation extends AbstractAugmentation {
    augmentationType: IAlbum.AugmentationType.ROTATION;
    angle: number;
    resize: boolean;
  }

  export interface ShearingAugmentation extends AbstractAugmentation  {
    augmentationType: IAlbum.AugmentationType.SHEARING;
    angle: number;
    resize: boolean;
  }

  export interface NoisingAugmentation extends AbstractAugmentation  {
    augmentationType: IAlbum.AugmentationType.NOISING;
    noiseSignalRatio: number;
  }

  export interface ZoomInAugmentation extends AbstractAugmentation  {
    augmentationType: IAlbum.AugmentationType.ZOOM_IN;
    ratio: number;
    resize: boolean;
  }

  export interface ZoomOutAugmentation extends AbstractAugmentation  {
    augmentationType: IAlbum.AugmentationType.ZOOM_OUT;
    ratio: number;
    resize: boolean;
  }

  export interface OcclusionAugmentation extends AbstractAugmentation  {
    augmentationType: IAlbum.AugmentationType.OCCLUSION;
    occAreaFraction: number;
    mode: 'BACKGROUND' | 'ZERO';
    isSARAlbum: boolean;
    //targetWindowSize?: number; //px
  }

  export interface TranslationAugmentation extends AbstractAugmentation  {
    augmentationType: IAlbum.AugmentationType.TRANSLATION;
    translateFraction: number;
    mode: 'REFLECT' | 'CONSTANT';
    resize: boolean;
  }

  export interface SaltPepperAugmentation extends AbstractAugmentation  {
    augmentationType: IAlbum.AugmentationType.SALT_PEPPER;
    knockoutFractions: number;
    pepperProbability?: number;
  }

  export interface MirroringAugmentation extends AbstractAugmentation  {
    augmentationType: IAlbum.AugmentationType.MIRRORING;
    flipAxis: (0 | 1 | 2);
  }

  export interface CroppingAugmentation extends AbstractAugmentation  {
    augmentationType: IAlbum.AugmentationType.CROPPING;
    cropAreaFraction: number;
    resize: boolean;
  }

  export interface BlurringAugmentation extends AbstractAugmentation  {
    augmentationType: IAlbum.AugmentationType.BLURRING;
    sigma: number;
  }

  export interface PhotoDistortAugmentation extends AbstractAugmentation  {
    augmentationType: IAlbum.AugmentationType.PHOTO_DISTORT;
    alphaContrast: number;
    alphaSaturation: number;
    deltaHue: number;
  }
}

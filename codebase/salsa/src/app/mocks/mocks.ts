import { IAlbum } from '../albums/album.interface';
import { IAsset } from '../core/interfaces/common.interface';
import { IProcess } from '../core/interfaces/process.interface';
import { CVModelType, ICVModel } from '../train/cv-model.interface';

export function getMockAlbum(updates?: Partial<IAlbum>): IAlbum {
  return {
    id: null,
    ownerId: null,
    name: null,
    created: null,
    updated: null,
    type: IAlbum.Type.SOURCE,
    status: IAlbum.Status.ACTIVE,
    labelMode: IAlbum.LabelMode.CLASSIFICATION,
    ...updates,
  };
}


export function getMockCVModel(updates?: Partial<ICVModel>): ICVModel {
  return {
    id: null,
    ownerId: null,
    name: null,
    created: null,
    updated: null,
    status: ICVModel.Status.ACTIVE,
    modelType: {
      type: CVModelType.Type.TL,
      tlType: CVModelType.TLType.CLASSIFICATION,
      labelMode: IAlbum.LabelMode.CLASSIFICATION,
      classifierType: 'FCN_1LAYER',
      architecture: 'VGG_16',
    },
    ...updates,
  };
}


export function getMockProcess(updates?: Partial<IProcess>): IProcess {
  return {
    id: null,
    ownerId: null,
    target: IAsset.Type.CV_MODEL,
    targetId: null,
    status: IProcess.Status.QUEUED,
    progress: 0,
    created: null,
    started: null,
    jobType: IProcess.JobType.CV_MODEL_PREDICT,
    ...updates,
  };
}

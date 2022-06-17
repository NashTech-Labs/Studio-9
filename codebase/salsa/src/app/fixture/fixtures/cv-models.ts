import { IAlbum } from '../../albums/album.interface';
import { CVModelType, ICVModel } from '../../train/cv-model.interface';
import { IFixtureData } from '../fixture.interface';

export const cvModels: IFixtureData<ICVModel> = {
  data: [
    {
      id: 'cvModelId1',
      ownerId: 'ownerId1',
      name: 'CVModel1',
      created: '2016-01-01 01:01',
      updated: '2016-05-05 05:05',
      status: ICVModel.Status.ACTIVE,
      modelType: {
        type: CVModelType.Type.CUSTOM,
        labelMode: IAlbum.LabelMode.CLASSIFICATION,
        classReference: {
          packageId: 'some package',
          moduleName: 'some module',
          className: 'some class',
        },
      },
      classes: [
        'truck',
        'cat',
        'bird',
        'frog',
        'dog',
        'deer',
      ],
    },
    {
      id: 'cvModelId2',
      ownerId: 'ownerId1',
      name: 'CVModel2',
      created: '2016-01-01 01:01',
      updated: '2016-05-05 05:05',
      status: ICVModel.Status.ACTIVE,
      modelType: {
        type: CVModelType.Type.TL,
        tlType: CVModelType.TLType.CLASSIFICATION,
        architecture: 'VGG_16',
        labelMode: IAlbum.LabelMode.CLASSIFICATION,
        classifierType: 'FCN_3LAYER',
      },
      classes: [
        'bmp2_tank',
        'btr60_transport',
        'btr70_transport',
        't72_tank',
      ],
    },
    {
      id: 'cvModelId3',
      ownerId: 'ownerId1',
      name: 'CVModel3',
      created: '2016-01-01 01:01',
      updated: '2016-05-05 05:05',
      status: ICVModel.Status.ACTIVE,
      modelType: {
        type: CVModelType.Type.TL,
        tlType: CVModelType.TLType.LOCALIZATION,
        architecture: 'VGG_16_RFB',
        labelMode: IAlbum.LabelMode.LOCALIZATION,
        detectorType: 'RFBNET',
      },
      classes: [
        'bmp2_tank',
        'btr60_transport',
        'btr70_transport',
        't72_tank',
      ],
    },
    {
      id: 'm_1568189266408',
      experimentId: '1568189266392',
      ownerId: 'ownerId0',
      name: 'Classification Model (xView)',
      created: '2019-09-06 01:01',
      updated: '2019-09-06 05:05',
      inLibrary: true,
      status: ICVModel.Status.ACTIVE,
      modelType: {
        type: CVModelType.Type.CUSTOM,
        labelMode: IAlbum.LabelMode.CLASSIFICATION,
        classReference: {
          packageId: 's9-operators',
          moduleName: 'studio9.ml.cv.transfer_learning',
          className: 'ClassificationModel',
        },
      },
      classes: ['Aircraft', 'Boat', 'Vehicles', 'Others'],
    },
    {
      id: 'm_1568189481155',
      experimentId: '1568193021707',
      ownerId: 'ownerId0',
      name: 'Detection Model (xView)',
      created: '2018-09-06 01:01',
      updated: '2018-09-06 05:05',
      inLibrary: true,
      status: ICVModel.Status.ACTIVE,
      modelType: {
        type: CVModelType.Type.CUSTOM,
        labelMode: IAlbum.LabelMode.LOCALIZATION,
        classReference: {
          packageId: 's9-operators',
          moduleName: 'studio9.ml.cv.transfer_learning',
          className: 'DetectionModel',
        },
      },
      classes: ['object'],
    },
  ],
  options: {
    indices: ['id'],
  },
};

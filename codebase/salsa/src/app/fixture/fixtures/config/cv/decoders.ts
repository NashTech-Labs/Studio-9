import { ICVDecoder } from '../../../../train/cv-architecture.interfaces';
import { IFixtureData } from '../../../fixture.interface';

export const cvDecoders: IFixtureData<ICVDecoder>  = {
  data: [
    {
      id: 'STACKED',
      name: 'SCAE',
      isNeural: true,
      packageName: 'STACKED_PACKAGE',
      packageVersion: '1',
      module: 'STACKEDModule',
      className: 'SCAEClass',
    },
  ],
  options: {
    indices: ['id'],
  },
};

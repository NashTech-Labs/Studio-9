import { IS9Project } from '../../develop/s9-project.interfaces';
import { IFixtureData } from '../fixture.interface';

export const s9Projects: IFixtureData<IS9Project> = {
  data: [
    {
      id: 's9Project1',
      ownerId: 'ownerId1',
      name: 'DC ML',
      created: '2019-09-01 12:00',
      updated: '2019-09-01 12:00',
      status: IS9Project.Status.IDLE,
      description: 'DC Machine Learning',
      packageName: 'package1',
      packageVersion: '1.1.0',
    },
  ],
  options: {
    indices: ['id', 'ownerId'],
  },
};

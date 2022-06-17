import { IOnlineTriggeredJob } from '../../deploy/online-job.interface';
import { IFixtureData } from '../fixture.interface';

export const jobs: IFixtureData<IOnlineTriggeredJob> = {
  data: [],
  options: {
    indices: ['id', 'ownerId', 'name'],
  },
};

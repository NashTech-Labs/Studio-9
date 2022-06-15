import { IScriptDeployment } from '../../deploy/script-deployment.interface';
import { IFixtureData } from '../fixture.interface';

export const scriptDeployments: IFixtureData<IScriptDeployment> = {
  data: [],
  options: {
    indices: ['id', 'ownerId', 'name'],
  },
};

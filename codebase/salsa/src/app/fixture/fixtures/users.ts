import * as moment from 'moment';

import { UserRole, UserStatus } from '../../users/user.interfaces';
import { IFixtureData, IFixtureUser } from '../fixture.interface';

export const users: IFixtureData<IFixtureUser> = {
  data: [
    {
      id: 'ownerId0',
      username: 'ayush@knoldus.com',
      email: 'ayush@knoldus.com',
      firstName: 'Ayush',
      lastName: 'Mishra',
      token: 'tokenId0',
      password: 'test',
      role: UserRole.ADMIN,
      status: UserStatus.ACTIVE,
      created: moment().add(-10, 'days').toString(),
      updated: moment().add(-5, 'days').toString(),
    },
    {
      id: 'ownerId1',
      username: 'ayush.singhal@knoldus.com',
      email: 'ayush.singhal@knoldus.com',
      firstName: 'Ayush',
      lastName: 'Singhal',
      token: 'tokenId1',
      password: 'test',
      role: UserRole.ADMIN,
      status: UserStatus.ACTIVE,
      created: moment().add(-10, 'days').toString(),
      updated: moment().add(-5, 'days').toString(),
    },
    {
      id: 'ownerId4',
      username: 'prabhat',
      email: 'prabhat@knoldus.com',
      firstName: 'Prabhat',
      lastName: 'Kashyap',
      token: 'tokenId4',
      password: 'test',
      role: UserRole.ADMIN,
      status: UserStatus.DEACTIVATED,
      created: moment().add(-4, 'days').toString(),
      updated: moment().add(-3, 'days').toString(),
    },
    {
      id: 'ownerId5',
      username: 'miral',
      email: 'miral.gandhi@knoldus.com',
      firstName: 'Miral',
      lastName: 'Gandhi',
      token: 'tokenId5',
      password: 'test',
      role: UserRole.USER,
      status: UserStatus.INACTIVE,
      created: moment().add(-3, 'days').toString(),
      updated: moment().add(-2, 'days').toString(),
    },
  ],
  options: {
    indices: ['id', 'username', 'email', 'token'],
  },
};

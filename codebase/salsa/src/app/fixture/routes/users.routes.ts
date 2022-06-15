import { UserRole, UserStatus } from '../../users/user.interfaces';
import { IFixtureServiceRoute, IFixtureUser } from '../fixture.interface';

export const usersRoutes: IFixtureServiceRoute[] = [
  {
    url: 'users$',
    method: 'GET',
    handler: function (this, params) {
      const resultset = this.collections.users.chain();

      let searchField = 'search';
      if (params['firstName']) {
        searchField = 'firstName';
      }
      if (params['lastName']) {
        searchField = 'lastName';
      }
      return this.prepareListResponse(resultset, params, searchField, searchField);
    },
  },
  {
    url: 'users/([\\w\\-]+)$',
    method: 'GET',
    handler: function(this, params) {
      const id = params[1];
      const models = this.collections.users;
      const model = models.findOne({id: id});

      if (!model) {
        throw new Error('Model Not found');
      }

      return model;
    },
  },
  {
    url: 'users$',
    method: 'POST',
    handler: function (this, params) {
      const models = this.collections.users;

      const newModel: IFixtureUser = Object.assign(
        {
          id: Date.now().toString(),
          username: null,
          email: null,
          firstName: null,
          lastName: null,
          token: null,
          password: null,
          role: UserRole.USER,
          status: UserStatus.ACTIVE,
          created: Date.now().toString(),
          updated: Date.now().toString(),
        },
        params,
      );

      return models.insertOne(newModel);
    },
  },
  {
    url: 'users/([\\w\\-]+)$',
    method: 'PUT',
    handler: function (this, params) {
      const id = params[1];
      const models = this.collections.users;
      const model = models.findOne({id: id});
      if (!model) {
        throw new Error('Model Not found');
      }

      // update (specific properties only)
      [
        'username',
        'email',
        'password',
        'firstName',
        'lastName',
        'role',
      ].forEach(prop =>
        params[prop] !== undefined && (model[prop] = params[prop]),
      );

      models.update(model);

      return model;
    },
  },
  {
    url: 'users/([\\w\\-]+)$',
    method: 'DELETE',
    handler: function (this, params) {
      const id = params[1];
      const models = this.collections.users;
      const model = models.findOne({id: id});
      if (!model) {
        throw new Error('Model Not found');
      }

      models.remove(model);

      return model;
    },
  },
  {
    url: 'users/([\\w\\-]+)/(activate|deactivate)$',
    method: 'POST',
    handler: function (this, params) {
      const id = params[1];
      const action = params[2];
      const models = this.collections.users;
      const model = models.findOne({id: id});
      if (!model) {
        throw new Error('Model Not found');
      }

      model.status = action === 'activate' ? UserStatus.ACTIVE : UserStatus.DEACTIVATED;
      models.update(model);

      return model;
    },
  },
];

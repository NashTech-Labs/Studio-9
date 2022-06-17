import { IPackagePublish } from '../../develop/package.interfaces';
import { IFixtureServiceRoute } from '../fixture.interface';

export const packagesRoutes: IFixtureServiceRoute[] = [
  {
    url: 'packages$',
    method: 'GET',
    handler: function (this, params) {
      let resultset = this.collections.packages.chain();
      if (params['s9ProjectId']) {
        resultset = resultset.find({s9ProjectId: params['s9ProjectId']});
      }
      return this.prepareListResponse(resultset, params, 'name', 'packageName');
    },
  },
  {
    url: 'packages/([\\w\\-]+)$',
    method: 'GET',
    handler: function (this, params) {
      const id = params[1];
      const models = this.collections.packages;
      const model = models.findOne({id: id});

      if (!model) {
        throw new Error('Model Not found');
      }

      return model;
    },
  },
  {
    url: 'packages/([\\w\\-]+)$',
    method: 'DELETE',
    handler: function (this, params, user) {
      const id = params[1];
      const models = this.collections.packages;
      const model = models.findOne({id: id, ownerId: user.id});
      if (!model) {
        throw new Error('Model Not found');
      }

      models.remove(model);

      return model;
    },
  },
  {
    url: 'packages/([\\w\\-]+)/publish$',
    method: 'POST',
    handler: function (this, params: IPackagePublish & {1: string}, user) {
      const id = params[1];
      const packages = this.collections.packages;
      const thePackage = packages.findOne({ id: id, ownerId: user.id });

      if (!thePackage) {
        throw new Error('Model Not found');
      }

      thePackage.isPublished = true;

      params.pipelineOperators.forEach(update => {
        const operator = thePackage.pipelineOperators.find(_ => _.id === update.id);
        if (operator) {
          operator.category = update.categoryId;
        }
      });

      this.collections.packages.update(thePackage);

      return thePackage;
    },
  },
];

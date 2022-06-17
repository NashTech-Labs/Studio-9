import { TObjectId } from '../../core/interfaces/common.interface';
import { IFixtureServiceRoute } from '../fixture.interface';

export const sharesRoutes: IFixtureServiceRoute[] = [
  {
    url: 'shares$', //list
    method: 'GET',
    handler: function(this, params, user) {
      const shares = this.collections.shares;
      let resultset = shares.chain().where(_ => {
        return _.ownerId === user.id || _.recipientId === user.id;
      });

      if (params.asset_type) {
        resultset = resultset.find({assetType: params.asset_type});
      }
      if (params.asset_id) {
        resultset = resultset.find({assetId: params.asset_id});
      }

      return this.prepareListResponse(resultset, params);
    },
  },
  {
    url: 'shares/([\\w\\-]+)$',
    method: 'GET',
    handler: function(this, params, user) {
      const id = params[1];
      const shares = this.collections.shares;
      const share = shares.findOne({id: id, ownerId: user.id});
      if (share) {
        return share;
      } else {
        throw new Error('Not found');
      }
    },
  },
  {
    url: 'shares/([\\w\\-]+)/recipient$',
    method: 'GET',
    handler: function(this, params, user) {
      const id = params[1];
      const shares = this.collections.shares;
      const users = this.collections.users;
      const share = shares.findOne({id: id, ownerId: user.id});
      if (share) {
        return users.findOne({ id: share.recipientId });
      } else {
        throw new Error('Not found');
      }
    },
  },
  {
    url: 'shares/([\\w\\-]+)/owner',
    method: 'GET',
    handler: function(this, params, user) {
      const id = params[1];
      const shares = this.collections.shares;
      const users = this.collections.users;
      const share = shares.findOne({id: id, recipientId: user.id});
      if (share) {
        return users.findOne({ id: share.ownerId });
      } else {
        throw new Error('Not found');
      }
    },
  },
  {
    url: 'shares$', //create
    method: 'POST',
    handler: function(this, params, user) {
      const users = this.collections.users;
      const recipient = users.findOne({email: params.recipientEmail});

      const shares = this.collections.shares;

      const data = Object.assign({}, params, {
        id: Date.now().toString(),
        ownerId: user.id,
        recipientEmail: params.recipientEmail,
        recipientId: recipient ? recipient.id : undefined,
        updated: new Date().toISOString(),
        created: new Date().toISOString(),
      });

      return shares.insertOne(data);
    },
  },
  {
    url: 'shares/([\\w\\-]+)$',
    method: 'DELETE',
    handler: function (this, params): { id: TObjectId } {
      const id = params[1];
      const shares = this.collections.shares;
      shares.removeWhere({id: id});
      return {id: id};
    },
  },
  {
    url: 'me/shares$',
    method: 'GET',
    handler: function(this, params, user) {
      const shares = this.collections.shares;
      let resultset = shares.chain().find({recipientId: user.id});

      return this.prepareListResponse(resultset, params);
    },
  },
];

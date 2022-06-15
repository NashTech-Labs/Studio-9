import config from '../../config';

export const shares = {
  data: [
    {
      id: 'shareId1',
      name: 'Share1',
      ownerId: 'ownerId2',
      recipientId: 'ownerId1',
      assetType: config.asset.values.TABLE,
      assetId: 'tableId301',
    },
    {
      id: 'shareId2',
      name: 'Share2',
      ownerId: 'ownerId2',
      recipientId: 'ownerId1',
      assetType: config.asset.values.FLOW,
      assetId: 'flowId3',
    },
  ],
  options: {
    indices: ['id'],
  },
};

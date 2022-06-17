// for loki
export interface IProjectAsset {
  projectId: string;
  assetId: string;
  type: string;
  folderId?: string;
}

export const projectsAssets = {
  data: <IProjectAsset[]> [
    {
      projectId: 'project1',
      assetId: 'flowId1',
      type: 'flow',
    },
    {
      projectId: 'project1',
      type: 'flow',
      assetId: 'flowId1',
    },
    {
      projectId: 'project1',
      type: 'table',
      assetId: 'tableId111',
    },
    {
      projectId: 'project2',
      type: 'flow',
      assetId: 'flowId2',
    },
    {
      projectId: 'project2',
      type: 'table',
      assetId: 'tableId101',
    },
    {
      projectId: 'project3',
      type: 'table',
      assetId: 'tableId112',
    },
    {
      projectId: 'project4',
      type: 'table',
      assetId: 'tableId111',
    },
    {
      projectId: 'project4',
      type: 'table',
      assetId: 'tableId112',
    },
    {
      projectId: 'project4',
      type: 'cvmodel',
      assetId: 'cvModelId1',
    },
  ],
  options: {
    indices: ['id', 'ownerId'],
  },
};

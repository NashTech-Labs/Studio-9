import { IObjectId } from '../../core/interfaces/common.interface';
import { IFixtureServiceRoute } from '../fixture.interface';
import { IProjectAsset } from '../fixtures/project-assets';

export const projectsRoutes: IFixtureServiceRoute[] = [
  // PROJECTS

  {
    url: 'projects$',
    method: 'GET',
    handler: function(this, params, user) {
      const projects = this.collections.projects.findObjects({ ownerId: user.id });

      return {
        count: projects.length,
        data: projects,
      };
    },
  },
  {
    url: '/projects$',
    method: 'POST',
    handler: function(this, params, user) {
      const projects = this.collections.projects;

      const data = Object.assign({}, params, {
        id: Date.now().toString(),
        name: params['name'],
        ownerId: user.id,
        // TODO: import { DateFormatter } from '@angular/common/src/pipes/date_pipe'; how can we use intl stuff from angular
        // created: (new Date()).format('yyyy-mm-dd HH:MM'),
        created: new Date().toISOString(),
        updated: new Date().toISOString(),
        folders: [],
      });

      return projects.insertOne(data);
    },
  },
  {
    url: '/projects/([\\w\\-]+)$',
    method: 'GET',
    handler: function(this, params, user) {
      const id = params[1];
      const projects = this.collections.projects;
      const projectsAssets = this.collections.projectsAssets;

      const project = projects.findOne({ id: id });

      // Throw error
      if (!project) {
        throw new Error('Project Not found');
      }
      if (project.ownerId !== user.id) {
        throw new Error('Access denied');
      }

      // Add additional info
      project['tablesCount'] = projectsAssets.findObjects({
        projectId: id,
        type: 'flow',
      }).length;
      project['flowsCount'] = projectsAssets.findObjects({
        projectId: id,
        type: 'table',
      }).length;
      project['modelsCount'] = projectsAssets.findObjects({
        projectId: id,
        type: 'model',
      }).length;
      project['cvModelsCount'] = projectsAssets.findObjects({
        projectId: id,
        type: 'cvmodel',
      }).length;

      return project;
    },
  },
  {
    url: '/projects/([\\w\\-]+)$',
    method: 'PUT',
    handler: function(this, params, user) {
      const id = params[1];
      const projects = this.collections.projects;
      const projectsAssets = this.collections.projectsAssets;

      const project = projects.findOne({ id: id });

      // Throw error
      if (!project) {
        throw new Error('Not found');
      }
      if (project.ownerId !== user.id) {
        throw new Error('Access denied');
      }

      // Apply changes
      ['name'].forEach(prop => // update (specific properties only)
        params[prop] !== undefined && (project[prop] = params[prop]),
      );

      projects.update(project);

      // Add additional info
      project['tablesCount'] = projectsAssets.findObjects({
        projectId: id,
        type: 'flow',
      }).length;
      project['flowsCount'] = projectsAssets.findObjects({
        projectId: id,
        type: 'table',
      }).length;
      project['modelsCount'] = projectsAssets.findObjects({
        projectId: id,
        type: 'model',
      }).length;

      return project;
    },
  },
  {
    url: 'projects/([\\w\\-]+)$',
    method: 'DELETE',
    handler: function (this, params, user): IObjectId {
      const id = params[1];
      const projects = this.collections.projects;
      const projectsAssets = this.collections.projectsAssets;

      const project = projects.findOne({ id: id });

      // Throw error
      if (!project) {
        throw new Error('Project Not found');
      }
      if (project.ownerId !== user.id) {
        throw new Error('Access denied');
      }

      projects.remove(project);
      projectsAssets.removeWhere({ projectId: id });

      return { id: id };
    },
  },
  {
    url: 'projects/([\\w\\-]+)/folders$',
    method: 'POST',
    handler: function (this, params, user): IObjectId {
      const id = params[1];
      const projects = this.collections.projects;

      const project = projects.findOne({id: id});

      // Throw error
      if (!project) {
        throw new Error('Project Not found');
      }
      if (project.ownerId !== user.id) {
        throw new Error('Access denied');
      }

      const newFolder = {id: 'folder_' + Date.now().toString(), path: params['path']};
      project.folders = [...project.folders || [], newFolder];
      projects.update(project);

      return newFolder;
    },
  },
  {
    url: 'projects/([\\w\\-]+)/folders/([\\w\\-]+)$',
    method: 'DELETE',
    handler: function (this, params, user): IObjectId {
      const id = params[1];
      const folderId = params[2];
      const projects = this.collections.projects;

      const project = projects.findOne({id: id});

      // Throw error
      if (!project) {
        throw new Error('Project Not found');
      }
      if (project.ownerId !== user.id) {
        throw new Error('Access denied');
      }

      project.folders = project.folders.filter(_ => _.id !== folderId);
      projects.update(project);

      return folderId;
    },
  },
  {
    url: 'projects/([\\w\\-]+)/([\\w\\-]+)/([\\w\\-]+)$',
    method: 'PUT',
    handler: function(this, params, user) {
      const projectId = params[1];
      const assetType = params[2];
      const assetId = params[3];
      const folderId = params['folderId'];
      const projects = this.collections.projects;
      const projectsAssets = this.collections.projectsAssets;

      const project = projects.findOne({ id: projectId });

      // Throw error
      if (!project) {
        throw new Error('Project not found');
      }
      if (project.ownerId !== user.id) {
        throw new Error('Project access denied');
      }

      const projectAsset: IProjectAsset = projectsAssets.findOne({$and: [
          { projectId: projectId },
          { assetId: assetId },
        ]});

      if (projectAsset) {
        projectsAssets.update({
          ...projectAsset,
          folderId: folderId || null,
        });
      } else {
        projectsAssets.insertOne({
          projectId: projectId,
          assetId: assetId,
          type: assetType,
          folderId: folderId || null,
        });
      }

      return { id: assetId };
    },
  },
  {
    url: 'projects/([\\w\\-]+)/([\\w\\-]+)/([\\w\\-]+)$',
    method: 'DELETE',
    handler: function(this, params, user) {
      const projectId = params[1];
      const assetType = params[2];
      const assetId = params[3];
      const projects = this.collections.projects;
      const projectsAssets = this.collections.projectsAssets;

      const project = projects.findOne({ id: projectId });

      // Throw error
      if (!project) {
        throw new Error('Project not found');
      }
      if (project.ownerId !== user.id) {
        throw new Error('Project access denied');
      }

      const projectAsset = projectsAssets.findOne({$and: [
          { projectId: projectId },
          { assetId: assetId },
          { type: assetType },
        ]});

      if (!projectAsset) {
        throw new Error('Link not found');
      }

      projectsAssets.remove(projectAsset);

      return { id: assetId };
    },
  },
];

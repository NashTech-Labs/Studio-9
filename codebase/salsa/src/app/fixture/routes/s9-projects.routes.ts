import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { IAsset, TObjectId } from '../../core/interfaces/common.interface';
import { IProcess } from '../../core/interfaces/process.interface';
import { IS9Project, IS9ProjectFile, IS9ProjectSessionCreate } from '../../develop/s9-project.interfaces';
import { IPackage } from '../../develop/package.interfaces';
import { IFixtureServiceRoute } from '../fixture.interface';
import { doRealFirst } from '../fixture.utils';

export const s9ProjectsRoutes: IFixtureServiceRoute[] = doRealFirst([
  {
    url: 's9-projects$',
    method: 'GET',
    handler: function (this, params, user) {
      return this.serveAssetListRequest(this.collections.s9Projects, IAsset.Type.S9_PROJECT, params, user);
    },
  },
  {
    url: 's9-projects/([\\w\\-]+)$',
    method: 'GET',
    handler: function(this, params, user) {
      const id = params[1];
      const s9Projects = this.collections.s9Projects;
      const s9Project = s9Projects.findOne({id: id, ownerId: user.id});

      if (!s9Project) {
        throw new Error('Project Not found');
      }

      return s9Project;
    },
  },
  {
    url: 's9-projects/([\\w\\-]+)/files/(.+)$',
    method: 'GET',
    handler: function(this, params, user) {
      const projectId = params[1];
      const filePath = params[2];
      const s9Projects = this.collections.s9Projects;
      const s9Project = s9Projects.findOne({id: projectId, ownerId: user.id});

      if (!s9Project) {
        throw new Error('Project Not found');
      }

      return new HttpResponse<string>({
        body: `from ${filePath} import fn`,
        headers: new HttpHeaders({
          'Last-Modified': new Date().toUTCString(),
        }),
      });
    },
  },
  {
    url: 's9-projects/([\\w\\-]+)/files/(.+)$',
    method: 'PUT',
    handler: function(this, params, user) {
      const projectId = params[1];
      const filePath = params[2];
      const s9Projects = this.collections.s9Projects;
      const s9Project = s9Projects.findOne({id: projectId, ownerId: user.id});

      if (!s9Project) {
        throw new Error('Project Not found');
      }

      return {
        type: IS9ProjectFile.Type.FILE,
        name: filePath,
        modified: new Date().toISOString(),
      };
    },
  },
  {
    url: 's9-projects/([\\w\\-]+)/files/(.+)$',
    method: 'DELETE',
    handler: function(this, params, user) {
      const projectId = params[1];
      const s9Projects = this.collections.s9Projects;
      const s9Project = s9Projects.findOne({id: projectId, ownerId: user.id});

      if (!s9Project) {
        throw new Error('Project Not found');
      }

      return 'OK';
    },
  },
  {
    url: 's9-projects/([\\w\\-]+)/ls$',
    method: 'GET',
    handler: function(this, params, user) {
      const projectId = params[1];
      const path = params['path'];
      const s9Projects = this.collections.s9Projects;
      const s9Project = s9Projects.findOne({id: projectId, ownerId: user.id});

      if (!s9Project) {
        throw new Error('Project Not found');
      }

      const data: IS9ProjectFile[] = [
        {type: IS9ProjectFile.Type.DIR, name: 'foo', modified: '2018-01-01T23:00:00Z'},
        {type: IS9ProjectFile.Type.FILE, name: 'foo/bar.py', modified: '2018-01-01T23:00:00Z'},
        {type: IS9ProjectFile.Type.FILE, name: 'foo/bar.js', modified: '2018-01-01T23:00:00Z'},
        {type: IS9ProjectFile.Type.FILE, name: 'foo/bar.scala', modified: '2018-01-01T23:00:00Z'},
        {type: IS9ProjectFile.Type.FILE, name: 'foo/bar.java', modified: '2018-01-01T23:00:00Z'},
        {type: IS9ProjectFile.Type.FILE, name: 'foo/bar.sh', modified: '2018-01-01T23:00:00Z'},
        {type: IS9ProjectFile.Type.DIR, name: 'folder', modified: '2018-01-01T23:00:00Z'},
      ];

      return path
        ? data.filter(_ => _.name.startsWith(path))
        : data;
    },
  },
  {
    url: 's9-projects$',
    method: 'POST',
    handler: function (this, params, user) {
      const s9Projects = this.collections.s9Projects;

      const newS9Project = Object.assign(
        {
          id: Date.now().toString(),
          name: null,
          description: null,
          ownerId: user.id,
          status: IS9Project.Status.IDLE,
          created: new Date().toISOString(),
          updated: new Date().toISOString(),
        },
        params,
      );

      return s9Projects.insertOne(newS9Project);
    },
  },
  {
    url: 's9-projects/([\\w\\-]+)$',
    method: 'PUT',
    handler: function (this, params, user) {
      const id = params[1];
      const models = this.collections.s9Projects;
      const model = models.findOne({id: id, ownerId: user.id});
      if (!model) {
        throw new Error('Model Not found');
      }

      // update (specific properties only)
      [
        'name',
        'description',
      ].forEach(prop =>
        params[prop] !== undefined && (model[prop] = params[prop]),
      );

      models.update(model);

      return model;
    },
  },
  {
    url: 's9-projects/([\\w\\-]+)/session$',
    method: 'GET',
    handler: function (this, params: IS9ProjectSessionCreate & {1: TObjectId}, user) {
      const id = params[1];
      const s9Projects = this.collections.s9Projects;
      const s9Project = s9Projects.findOne({id: id, ownerId: user.id});

      if (!s9Project) {
        throw new Error('Project Not found');
      }

      throw new Error('Can\'t find a session');
    },
  },
  {
    url: 's9-projects/([\\w\\-]+)/session$',
    method: 'POST',
    handler: function (this, params: IS9ProjectSessionCreate & {1: TObjectId}, user) {
      const id = params[1];
      const s9Projects = this.collections.s9Projects;
      const s9Project = s9Projects.findOne({id: id, ownerId: user.id});

      if (!s9Project) {
        throw new Error('Project Not found');
      }

      throw new Error('Can\'t run a session');
    },
  },
  {
    url: 's9-projects/([\\w\\-]+)/session$',
    method: 'DELETE',
    handler: function (this, params: IS9ProjectSessionCreate & {1: TObjectId}, user) {
      const id = params[1];
      const s9Projects = this.collections.s9Projects;
      const s9Project = s9Projects.findOne({id: id, ownerId: user.id});

      if (!s9Project) {
        throw new Error('Project Not found');
      }

      throw new Error('Session not found');
    },
  },
  {
    url: 's9-projects/([\\w\\-]+)$',
    method: 'DELETE',
    handler: function (this, params, user) {
      const id = params[1];
      const models = this.collections.s9Projects;
      const model = models.findOne({id: id, ownerId: user.id});
      if (!model) {
        throw new Error('Model Not found');
      }

      models.remove(model);

      return model;
    },
  },
  {
    url: 's9-projects/([\\w\\-]+)/build$',
    method: 'POST',
    handler: function (this, params, user) {
      const id = params[1];
      const models = this.collections.s9Projects;
      const model = models.findOne({ id: id, ownerId: user.id });
      if (!model) {
        throw new Error('Model Not found');
      }

      const processes = this.collections.processes;
      const process = processes.findOne({ targetId: model.id, target: IAsset.Type.S9_PROJECT });

      if (process) {
        if (process.status === IProcess.Status.RUNNING) {
          throw new Error('A build process is already running for this project');
        }
        processes.remove(process);
      }

      const s9ProjectProcess: IProcess = {
        id: 'dc_' + Date.now().toString(),
        ownerId: user.id,
        target: IAsset.Type.S9_PROJECT,
        targetId: model.id,
        progress: 0,
        status: IProcess.Status.RUNNING,
        created: new Date().toISOString(),
        started: new Date().toISOString(),
        jobType: IProcess.JobType.PROJECT_BUILD,
      };

      processes.insertOne(s9ProjectProcess);

      // update packageName only when it's provided (locked)
      if (params['name']) {
        model.packageName = params['name'];
      }
      model.status = IS9Project.Status.BUILDING;
      models.update(model);
      const s9ProjectPackage: IPackage = {
        id: Date.now().toString(),
        name: model.packageName || params['name'],
        created: Date.now().toString(),
        ownerId: user.id,
        version: params['version'],
        location: '/some/location/',
        s9ProjectId: model.id,
        isPublished: false,
      };

      if (params['description']) {
        s9ProjectPackage.description = params['description'];
      }

      this.collections.packages.insertOne(s9ProjectPackage);

      return model;
    },
  },
]);

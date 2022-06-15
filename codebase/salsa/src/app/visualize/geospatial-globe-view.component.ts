import { Component, ElementRef, Input, OnChanges, OnInit, SimpleChanges, ViewChild } from '@angular/core';

@Component({
  selector: 'geospatial-globe-view',
  template: `
    <div class="row">
      <div class="col col-lg-2">
        <ul class="nav nav-pills submenu">
          <li *ngFor="let row of _data">
            <a (click)="view(row);" class="link">
              <i class="glyphicon glyphicon-map-marker"></i>
              {{row.src[1]}}, ({{row.src[6]}})
            </a>
          </li>
        </ul>
      </div>
      <div class="col col-lg-10">
        <div #viewer style="width: 100%; min-height: 500px;"></div>
      </div>
    </div>
  `,
})
export class GeospatialGlobeViewComponent implements OnInit, OnChanges {
  @ViewChild('viewer') viewerEl: ElementRef;
  @Input() data: any = [];
  private _data: {src: any, point: any, rect: any}[] = [];
  private viewer: any;

  public ngOnChanges(changes: SimpleChanges): void {
    if ('data' in changes && this.viewer) {
      this.processData(changes.data.currentValue);
    }
  }

  public ngOnInit(): void {
    Cesium.Ion.defaultAccessToken = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJqdGkiOiJmMzFmMDQ4YS0yN2RhLTRjMjItOTJiNC1iOWM5YjI2MmY1NTgiLCJpZCI6MTU2ODksInNjb3BlcyI6WyJhc3IiLCJnYyJdLCJpYXQiOjE1Njg2MDk4Mjl9._3uZn8-WPij0jbXzwsFmgWkqmUg96V_YK0Yq9lvlecs';
    const options = {
      animation: false,
      fullscreenButton: false,
      homeButton: false,
      infoBox: false,
      sceneModePicker: false,
      timeline: false,
      navigationHelpButton: false,
      navigationInstructionsInitiallyVisible: false,
      selectionIndicator: false,
      projectionPicker: false,
    };
    this.viewer = new Cesium.Viewer(this.viewerEl.nativeElement, options);
    this.processData(this.data);
  }

  public view(entity) {
    this.viewer.flyTo(entity.point);
  }

  private processData(data: any[]) {
    this.viewer.entities.removeAll();
    this._data = data.map(row => {
      const [, target, minLat, minLong, maxLat, maxLong, confidence] = row;
      const label = `${target} (${confidence})`;
      return {
        src: row,
        point: this.viewer.entities.add({
          position: Cesium.Cartesian3.fromDegrees((maxLong + minLong) / 2, (minLat + maxLat) / 2),
          label: {
            text: label,
            font: '14px sans-serif',
            style: Cesium.LabelStyle.FILL,
          },
        }),
        rect: this.viewer.entities.add({
          name: label,
          rectangle: {
            coordinates: Cesium.Rectangle.fromDegrees(minLong, maxLat, maxLong, minLat),
            material: Cesium.Color.GREEN.withAlpha(0.5),
            outline: true,
            outlineColor: Cesium.Color.BLACK,
          },
        }),
      };
    });
    if (this._data.length) {
      this.view(this._data[0]);
    }
  }
}

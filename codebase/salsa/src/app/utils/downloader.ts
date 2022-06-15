import { Observable } from 'rxjs/Observable';

const hiddenIFrameID = 'hiddenDownloader';

function createIframe(id = hiddenIFrameID, name = hiddenIFrameID): HTMLIFrameElement {
  const iframe = <HTMLIFrameElement> document.createElement('iframe');
  iframe.id = id;
  iframe.name = name;
  iframe.style.display = 'none';

  document.body.appendChild(iframe);

  return iframe;
}

export class Downloader {
  static get(url: string, filename?: string): Observable<boolean> {
    if (!('chrome' in window)) {
      let iframe = <HTMLIFrameElement> document.getElementById(hiddenIFrameID);

      if (!iframe) {
        iframe = createIframe();
      }

      iframe.src = url;
    } else {
      let link = document.createElement('a');
      link.download = filename;
      link.href = url;
      link.click();
    }
    return Observable.of(true).delay(1000);
  }

  static post(url: string, data?: { [key: string]: any }): Observable<boolean> {
    const iframe = createIframe();
    const doc = iframe.contentWindow.document || iframe.contentDocument;

    const form = doc.createElement('form');
    form.setAttribute('action', url);
    form.setAttribute('method', 'post');
    form.setAttribute('enctype', 'multipart/form-data');

    for (const key of Object.keys(data)) {
      const el = doc.createElement('input');
      el.setAttribute('type', 'hidden');
      el.setAttribute('name', key);
      el.value = Array.isArray(data[key]) ? JSON.stringify(data[key]) : data[key];
      form.appendChild(el);
    }

    doc.body.appendChild(form);

    form.submit();

    return Observable.of(true).delay(1000);
  }
}

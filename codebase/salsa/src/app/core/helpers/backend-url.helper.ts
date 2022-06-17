export function backendUrl(baseUrl: string | string[], parts: string[] = null): string {
  return (Array.isArray(baseUrl) ? baseUrl : baseUrl.split('/'))
    .concat(parts ? parts : [])
    .map(encodeURIComponent)
    .join('/');
}

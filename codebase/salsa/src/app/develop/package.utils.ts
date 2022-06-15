import { ParameterDefinition } from '../core/interfaces/params.interface';

export function getParamsTooltip(param: ParameterDefinition): string {
  const rows = Object.keys(param).reduce((memo: string, key: string): string => {
    if (Array.isArray(param[key])) {
      const values = param[key].join(', ');
      memo += `<tr><td>${key}</td><td>${values}</td></tr>`;
    }
    if (['string', 'number', 'boolean'].includes(typeof param[key])) {
      memo += `<tr><td>${key}</td><td>${param[key]}</td></tr>`;
    }
    return memo;
  }, '');
  return `<table class="table"><tr><th>Key</th><th>Value</th></tr>${rows}</table>`;
}


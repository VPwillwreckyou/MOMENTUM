import { afterEach, describe, expect, it } from 'vitest';
import { apiUrl, resolvedApiBase, setApiUrlOverride } from '../lib/api';

describe('api base resolution', () => {
  afterEach(() => {
    localStorage.removeItem('momentum.api-url');
  });

  it('defaults to same-origin when no override is configured', () => {
    expect(resolvedApiBase()).toBe('');
    expect(apiUrl('/api/health')).toBe('/api/health');
  });

  it('applies a runtime override (phone shell service address)', () => {
    setApiUrlOverride('http://100.64.0.5:8787');
    expect(resolvedApiBase()).toBe('http://100.64.0.5:8787');
    expect(apiUrl('/api/health')).toBe('http://100.64.0.5:8787/api/health');
  });

  it('trims trailing slashes and ignores non-http values', () => {
    setApiUrlOverride('http://localhost:8787///');
    expect(resolvedApiBase()).toBe('http://localhost:8787');
    setApiUrlOverride('not-a-url');
    expect(resolvedApiBase()).toBe('');
  });

  it('clears the override when given an empty value', () => {
    setApiUrlOverride('http://localhost:8787');
    setApiUrlOverride('');
    expect(localStorage.getItem('momentum.api-url')).toBeNull();
    expect(resolvedApiBase()).toBe('');
  });

  it('passes through absolute URLs unchanged', () => {
    expect(apiUrl('https://example.com/x')).toBe('https://example.com/x');
  });
});

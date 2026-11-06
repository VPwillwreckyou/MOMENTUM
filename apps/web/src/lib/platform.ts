const platform =
  typeof navigator !== 'undefined' ? navigator.platform || navigator.userAgent : '';

export const isMacPlatform = /mac|iphone|ipad|ipod/i.test(platform);
export const MODIFIER_LABEL = isMacPlatform ? '⌘' : 'Ctrl';

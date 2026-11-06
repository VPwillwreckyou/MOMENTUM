import { getVaultKey, putVaultKey } from './db';
import { asBufferSource, base64ToBytes, bytesToBase64 } from './binary';

const encoder = new TextEncoder();
const decoder = new TextDecoder();

async function loadDeviceKey(): Promise<CryptoKey> {
  if (!globalThis.crypto?.subtle) {
    throw new Error('Secure storage is unavailable in this browser context.');
  }
  const existing = await getVaultKey();
  if (existing) return existing;
  const key = await crypto.subtle.generateKey({ name: 'AES-GCM', length: 256 }, false, ['encrypt', 'decrypt']);
  await putVaultKey(key);
  return key;
}

/** Encrypt a user-owned credential with a non-extractable device key. */
export async function encryptSecret(plainText: string): Promise<string> {
  const key = await loadDeviceKey();
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const cipher = await crypto.subtle.encrypt(
    { name: 'AES-GCM', iv: asBufferSource(iv) },
    key,
    asBufferSource(encoder.encode(plainText)),
  );
  return `v1.${bytesToBase64(iv)}.${bytesToBase64(new Uint8Array(cipher))}`;
}

export async function decryptSecret(payload: string): Promise<string> {
  const [version, iv, cipher] = payload.split('.');
  if (version !== 'v1' || !iv || !cipher) throw new Error('Stored credential is not readable.');
  const key = await loadDeviceKey();
  const plain = await crypto.subtle.decrypt(
    { name: 'AES-GCM', iv: asBufferSource(base64ToBytes(iv)) },
    key,
    asBufferSource(base64ToBytes(cipher)),
  );
  return decoder.decode(plain);
}

export function maskSecret(secret: string): string {
  if (!secret) return '';
  if (secret.length <= 8) return '••••••••';
  return `${secret.slice(0, 4)}••••••••${secret.slice(-4)}`;
}

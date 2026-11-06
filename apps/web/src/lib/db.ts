import type { AttachmentMeta } from '../state/types';

const DB_NAME = 'momentum-local';
const DB_VERSION = 1;

interface StoredAttachment extends AttachmentMeta {
  blob: Blob;
}

interface VaultRecord {
  key: string;
  value: CryptoKey;
}

let dbPromise: Promise<IDBDatabase> | null = null;

function requestToPromise<T>(request: IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error ?? new Error('IndexedDB request failed'));
  });
}

function transactionDone(transaction: IDBTransaction): Promise<void> {
  return new Promise((resolve, reject) => {
    transaction.oncomplete = () => resolve();
    transaction.onerror = () => reject(transaction.error ?? new Error('IndexedDB transaction failed'));
    transaction.onabort = () => reject(transaction.error ?? new Error('IndexedDB transaction aborted'));
  });
}

export function openDatabase(): Promise<IDBDatabase> {
  if (dbPromise) return dbPromise;
  dbPromise = new Promise((resolve, reject) => {
    if (typeof indexedDB === 'undefined') {
      reject(new Error('Local storage is unavailable in this browser.'));
      return;
    }
    const request = indexedDB.open(DB_NAME, DB_VERSION);
    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains('documents')) {
        db.createObjectStore('documents', { keyPath: 'key' });
      }
      if (!db.objectStoreNames.contains('attachments')) {
        const store = db.createObjectStore('attachments', { keyPath: 'id' });
        store.createIndex('goalId', 'goalId', { unique: false });
      }
      if (!db.objectStoreNames.contains('vault')) {
        db.createObjectStore('vault', { keyPath: 'key' });
      }
    };
    request.onsuccess = () => {
      const db = request.result;
      db.onversionchange = () => db.close();
      resolve(db);
    };
    request.onerror = () => reject(request.error ?? new Error('Could not open local database.'));
    request.onblocked = () => reject(new Error('Close other MOMENTUM tabs and try again.'));
  });
  return dbPromise;
}

export async function getDocument<T>(key: string): Promise<T | null> {
  const db = await openDatabase();
  const transaction = db.transaction('documents', 'readonly');
  const result = await requestToPromise(transaction.objectStore('documents').get(key));
  await transactionDone(transaction);
  return result ? (result.value as T) : null;
}

export async function putDocument<T>(key: string, value: T): Promise<void> {
  const db = await openDatabase();
  const transaction = db.transaction('documents', 'readwrite');
  transaction.objectStore('documents').put({ key, value });
  await transactionDone(transaction);
}

export async function deleteDocument(key: string): Promise<void> {
  const db = await openDatabase();
  const transaction = db.transaction('documents', 'readwrite');
  transaction.objectStore('documents').delete(key);
  await transactionDone(transaction);
}

export async function hasDatabase(): Promise<boolean> {
  if (typeof indexedDB === 'undefined') return false;
  const databases = await indexedDB.databases();
  return databases.some((database) => database.name === DB_NAME);
}

export async function listAttachments(goalId?: string): Promise<AttachmentMeta[]> {
  const db = await openDatabase();
  const transaction = db.transaction('attachments', 'readonly');
  const store = transaction.objectStore('attachments');
  const request = goalId ? store.index('goalId').getAll(goalId) : store.getAll();
  const records = (await requestToPromise(request)) as StoredAttachment[];
  await transactionDone(transaction);
  return records
    .map(({ blob: _blob, ...meta }) => meta)
    .sort((a, b) => a.createdAt.localeCompare(b.createdAt));
}

export async function getAttachmentBlob(id: string): Promise<Blob | null> {
  const db = await openDatabase();
  const transaction = db.transaction('attachments', 'readonly');
  const record = (await requestToPromise(transaction.objectStore('attachments').get(id))) as
    | StoredAttachment
    | undefined;
  await transactionDone(transaction);
  return record?.blob ?? null;
}

export async function putAttachment(meta: AttachmentMeta, blob: Blob): Promise<void> {
  const db = await openDatabase();
  const transaction = db.transaction('attachments', 'readwrite');
  transaction.objectStore('attachments').put({ ...meta, blob });
  await transactionDone(transaction);
}

export async function removeAttachment(id: string): Promise<void> {
  const db = await openDatabase();
  const transaction = db.transaction('attachments', 'readwrite');
  transaction.objectStore('attachments').delete(id);
  await transactionDone(transaction);
}

export async function removeAttachmentsForGoal(goalId: string): Promise<void> {
  const db = await openDatabase();
  const transaction = db.transaction('attachments', 'readwrite');
  const store = transaction.objectStore('attachments');
  const keys = (await requestToPromise(store.index('goalId').getAllKeys(goalId))) as IDBValidKey[];
  for (const key of keys) store.delete(key);
  await transactionDone(transaction);
}

export async function clearAttachments(): Promise<void> {
  const db = await openDatabase();
  const transaction = db.transaction('attachments', 'readwrite');
  transaction.objectStore('attachments').clear();
  await transactionDone(transaction);
}

export async function getVaultKey(): Promise<CryptoKey | null> {
  const db = await openDatabase();
  const transaction = db.transaction('vault', 'readonly');
  const record = (await requestToPromise(transaction.objectStore('vault').get('device-key'))) as
    | VaultRecord
    | undefined;
  await transactionDone(transaction);
  return record?.value ?? null;
}

export async function putVaultKey(key: CryptoKey): Promise<void> {
  const db = await openDatabase();
  const transaction = db.transaction('vault', 'readwrite');
  transaction.objectStore('vault').put({ key: 'device-key', value: key });
  await transactionDone(transaction);
}

export async function clearDatabase(): Promise<void> {
  if (dbPromise) {
    const db = await dbPromise;
    db.close();
    dbPromise = null;
  }
  await new Promise<void>((resolve, reject) => {
    const request = indexedDB.deleteDatabase(DB_NAME);
    request.onsuccess = () => resolve();
    request.onerror = () => reject(request.error ?? new Error('Could not erase local data.'));
    request.onblocked = () => reject(new Error('Close other MOMENTUM tabs and try again.'));
  });
}

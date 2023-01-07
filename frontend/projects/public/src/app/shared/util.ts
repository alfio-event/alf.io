export const DELETE_ACCOUNT_CONFIRMATION = 'alfio.delete-account.confirmation';

export function writeToSessionStorage(key: string, value: string): void {
  try {
    window.sessionStorage.setItem(key, value);
  } catch (e) {
    // session storage might be disabled in some contexts
  }
}

export function getFromSessionStorage(key: string): string | null {
  try {
    return window.sessionStorage.getItem(key);
  } catch (e) {
    // session storage might be disabled in some contexts
    return null;
  }
}

export function removeFromSessionStorage(key: string): void {
  try {
    window.sessionStorage.removeItem(key);
  } catch (e) {
  }
}

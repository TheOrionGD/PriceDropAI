const disabledKeys = new Set<string>();

export function isZenRowsKeyDisabled(key?: string): boolean {
  if (!key) return false;
  return disabledKeys.has(key);
}

export function disableZenRowsKey(key?: string, reason?: string): void {
  if (key && !disabledKeys.has(key)) {
    disabledKeys.add(key);
    console.warn(`[ZenRows Notice] Key ${key.slice(0, 8)}... disabled: ${reason || 'Auth / Quota limit reached'}`);
  }
}

// Global check (legacy compatibility)
export function isZenRowsDisabled(): boolean {
  return false;
}

export function disableZenRows(reason?: string): void {
  if (reason) {
    console.warn(`[ZenRows Notice] Gateway warning: ${reason}`);
  }
}

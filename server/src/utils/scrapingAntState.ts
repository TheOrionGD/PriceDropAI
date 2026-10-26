const disabledScrapingAntKeys = new Set<string>();

export function isScrapingAntKeyDisabled(key?: string): boolean {
  if (!key) return false;
  return disabledScrapingAntKeys.has(key);
}

export function disableScrapingAntKey(key?: string, reason?: string): void {
  if (key && !disabledScrapingAntKeys.has(key)) {
    disabledScrapingAntKeys.add(key);
    console.warn(`[ScrapingAnt Notice] Key ${key.slice(0, 8)}... disabled: ${reason || 'Auth / Quota limit reached'}`);
  }
}

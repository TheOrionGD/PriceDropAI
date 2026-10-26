const disabledScraperApiKeys = new Set<string>();

export function isScraperApiKeyDisabled(key?: string): boolean {
  if (!key) return false;
  return disabledScraperApiKeys.has(key);
}

export function disableScraperApiKey(key?: string, reason?: string): void {
  if (key && !disabledScraperApiKeys.has(key)) {
    disabledScraperApiKeys.add(key);
    console.warn(`[ScraperAPI Notice] Key ${key.slice(0, 8)}... disabled: ${reason || 'Auth / Quota limit reached'}`);
  }
}

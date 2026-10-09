let zenrowsDisabled = false;

export function isZenRowsDisabled(): boolean {
  return zenrowsDisabled;
}

export function disableZenRows(reason?: string): void {
  if (!zenrowsDisabled) {
    zenrowsDisabled = true;
    if (reason) {
      console.warn(`[ZenRows Notice] Gateway disabled: ${reason}`);
    }
  }
}

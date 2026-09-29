/** Paged list envelope shared by every backend service: `{ items, page, size, total }`. */
export interface Page<T> {
  readonly items: readonly T[];
  readonly page: number;
  readonly size: number;
  readonly total: number;
}

/** Walks every page of a paged endpoint and returns all items, in order. */
export async function collectPages<T>(
  fetch: (page: number) => Promise<Page<T>>,
  size: number,
): Promise<T[]> {
  const all: T[] = [];
  for (let page = 0; ; page++) {
    const response = await fetch(page);
    all.push(...response.items);
    // Stop on a short page as well as on a satisfied total: a roster that shrinks
    // between requests would otherwise loop forever.
    if (response.items.length < size || all.length >= response.total) {
      return all;
    }
  }
}

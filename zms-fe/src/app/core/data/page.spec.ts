import { collectPages, Page } from './page';

describe('collectPages', () => {
  it('handles a single short page', async () => {
    const fetch = async (page: number): Promise<Page<number>> => {
      if (page === 0) {
        return { items: [1, 2, 3], page: 0, size: 100, total: 3 };
      }
      throw new Error('Should not fetch page ' + page);
    };

    const result = await collectPages(fetch, 100);
    expect(result).toEqual([1, 2, 3]);
  });

  it('collects multiple pages until a short page', async () => {
    const fetch = async (page: number): Promise<Page<number>> => {
      if (page === 0) {
        return { items: [1, 2, 3, 4, 5], page: 0, size: 5, total: 12 };
      }
      if (page === 1) {
        return { items: [6, 7, 8, 9, 10], page: 1, size: 5, total: 12 };
      }
      if (page === 2) {
        return { items: [11, 12], page: 2, size: 5, total: 12 };
      }
      throw new Error('Should not fetch page ' + page);
    };

    const result = await collectPages(fetch, 5);
    expect(result).toEqual([1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12]);
  });

  it('stops when total is reached even if the last page is full', async () => {
    const fetch = async (page: number): Promise<Page<number>> => {
      if (page === 0) {
        return { items: [1, 2, 3, 4, 5], page: 0, size: 5, total: 10 };
      }
      if (page === 1) {
        return { items: [6, 7, 8, 9, 10], page: 1, size: 5, total: 10 };
      }
      throw new Error('Should not fetch page ' + page);
    };

    const result = await collectPages(fetch, 5);
    expect(result).toEqual([1, 2, 3, 4, 5, 6, 7, 8, 9, 10]);
  });

  it('handles an empty first page', async () => {
    const fetch = async (page: number): Promise<Page<number>> => {
      if (page === 0) {
        return { items: [], page: 0, size: 100, total: 0 };
      }
      throw new Error('Should not fetch page ' + page);
    };

    const result = await collectPages(fetch, 100);
    expect(result).toEqual([]);
  });
});

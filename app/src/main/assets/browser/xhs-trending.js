(() => {
  const search = window.__INITIAL_STATE__?.search || {};
  const unwrap = value => value?._rawValue ?? value?.value ?? value;
  const lists = [window.__openscopeTrendingResponse?.data, search.queryTrendingInfo,
    search.queryTrending, search.trending, search.hotList]
    .map(unwrap)
    .flatMap(value => Array.isArray(value) ? [value] : [value?.items, value?.list, value?.trendings,
      value?.queries, value?.queryTrendingList, value?.query_trending_list,
      value?.data?.items, value?.data?.list,
      ...Object.values(value || {}).filter(Array.isArray)].filter(Array.isArray));
  const words = (lists.find(value => value?.length) || [])
    .map(item => item?.name || item?.word || item?.keyword || item?.query || item?.search_word || item?.title || '')
    .map(word => String(word).trim())
    .filter(Boolean)
    .slice(0, 20);
  if (!words.length && !window.__openscopeTrendingFocused) {
    const input = document.querySelector('#search-input, .search-input');
    if (input) {
      window.__openscopeTrendingFocused = true;
      input.focus();
      input.click();
    }
  }
  const body = document.body?.innerText || '';
  return JSON.stringify({words, challenge: /完成验证|拖动滑块|访问过于频繁/.test(body)});
})()

(() => {
  const visible = el => !!el && el.getBoundingClientRect().width > 0 && el.getBoundingClientRect().height > 0;
  const body = document.body?.innerText || '';
  const challenge = [...document.querySelectorAll('[id*=captcha],[class*=captcha],[id*=verify]')].some(visible) || /完成下列验证|拖动滑块|验证后继续|访问过于频繁/.test(body);
  const loginVisible = /登录后即可搜索|登录后搜索/.test(body) || [...document.querySelectorAll('input[type=password],input[placeholder*=手机号]')].some(visible);
  const items = [];
  if (!location.pathname.startsWith('/search/')) return JSON.stringify({items, challenge, loginVisible});
  const anchors = document.querySelectorAll('.search-result-card > a[href*="/video/"], [data-e2e=search-result] a[href*="/video/"], [data-e2e=search-video] a[href*="/video/"]');
  anchors.forEach(a => {
    if (!visible(a)) return;
    const card = a.closest('.search-result-card, [data-e2e=search-result], [data-e2e=search-video],li');
    if (!card) return;
    // The current desktop result card has a media column and a text column.
    // Keep the old data-e2e fallback for other deployed page versions.
    const title = (a.querySelector(':scope > div > div:nth-child(2) > div > div:first-child')?.textContent ||
      card.querySelector('[data-e2e=video-desc]')?.textContent || a.getAttribute('title') || '').trim();
    const url = new URL(a.href, location.href);
    if (!title || !/^\/video\/\d{8,30}\/?$/.test(url.pathname) || url.hostname !== 'www.douyin.com') return;
    const author = (a.querySelector(':scope > div > div:nth-child(2) > div > div:nth-child(2) > span:first-child > span:nth-child(2)')?.textContent ||
      card.querySelector('[data-e2e=video-author]')?.textContent || '').trim();
    const metric = (a.querySelector(':scope > div > div:first-child > div:first-child > div:nth-child(2) > div:nth-child(2) > div:nth-child(3) > span:nth-child(2)')?.textContent || '').trim();
    items.push({title, url:url.origin+url.pathname, author, thumbnail:card.querySelector('img')?.currentSrc||card.querySelector('img')?.src||'', metric});
  });
  return JSON.stringify({items, challenge, loginVisible, empty: /暂无搜索结果|没有找到相关/.test(body), hasMore: !/没有更多了|暂时没有更多/.test(body)});
})()

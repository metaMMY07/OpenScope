(() => {
  const visible = el => !!el && el.getBoundingClientRect().width > 0 && el.getBoundingClientRect().height > 0;
  const body = document.body?.innerText || '';
  const challenge = [...document.querySelectorAll('[id*=captcha],[class*=captcha],[id*=verify]')].some(visible) || /完成下列验证|拖动滑块|验证后继续|访问过于频繁/.test(body);
  const loginVisible = /登录后即可搜索|登录后搜索/.test(body) || [...document.querySelectorAll('input[type=password],input[placeholder*=手机号]')].some(visible);
  const items = [];
  if (!location.pathname.startsWith('/search/')) return JSON.stringify({items, challenge, loginVisible});
  document.querySelectorAll('[data-e2e=search-result] a[href*="/video/"], [data-e2e=search-video] a[href*="/video/"], [data-e2e=search-result-container] a[href*="/video/"]').forEach(a => {
    if (!visible(a)) return;
    const card = a.closest('[data-e2e=search-result], [data-e2e=search-video],li');
    if (!card) return;
    const title = (card.querySelector('[data-e2e=video-desc]')?.textContent || a.getAttribute('title') || a.textContent || '').trim();
    const url = new URL(a.href, location.href);
    if (!title || !/^\/video\/\d{8,30}\/?$/.test(url.pathname) || url.hostname !== 'www.douyin.com') return;
    items.push({title, url:url.origin+url.pathname, author:(card.querySelector('[data-e2e=video-author]')?.textContent||'').trim(), thumbnail:card.querySelector('img')?.src||'', metric:''});
  });
  return JSON.stringify({items, challenge, loginVisible, empty: /暂无搜索结果|没有找到相关/.test(body), hasMore: !/没有更多了|暂时没有更多/.test(body)});
})()

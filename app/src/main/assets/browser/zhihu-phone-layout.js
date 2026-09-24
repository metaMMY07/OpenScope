// Keep Zhihu's desktop features while reflowing its fixed-width feed on phones.
(() => {
  if (window !== window.top || window.__openScopeZhihuPhoneLayout) return;
  window.__openScopeZhihuPhoneLayout = true;
  const css = `
    html, body, #root {
      width: 100% !important;
      min-width: 0 !important;
      max-width: 100% !important;
      overflow-x: hidden !important;
    }
    .Topstory-container {
      display: flex !important;
      flex-direction: column !important;
      box-sizing: border-box !important;
      width: 100% !important;
      min-width: 0 !important;
      max-width: 100% !important;
      padding: 0 10px !important;
    }
    .Topstory-container > .Topstory-mainColumn,
    .Topstory-container > div:not(.Topstory-mainColumn),
    .Topstory-mainColumn .Card,
    .Topstory-mainColumn .ContentItem,
    .Topstory-mainColumn .ContentItem-title,
    .Topstory-mainColumn .RichContent,
    .Topstory-mainColumn .RichContent-inner {
      box-sizing: border-box !important;
      width: 100% !important;
      min-width: 0 !important;
      max-width: 100% !important;
      margin-left: 0 !important;
      margin-right: 0 !important;
    }
    .Topstory-mainColumn .ContentItem-actions {
      flex-wrap: wrap !important;
      height: auto !important;
      min-height: 0 !important;
    }
    .WriteArea section > div > div:nth-child(2) {
      flex-wrap: wrap !important;
      gap: 12px !important;
      height: auto !important;
    }
    .WriteArea section > div > div:nth-child(2) > div:nth-child(2) {
      flex-shrink: 0 !important;
      width: auto !important;
    }
    .WriteArea section > div > div:nth-child(2) > div:nth-child(2) > div {
      width: auto !important;
      white-space: nowrap !important;
    }
    .Topstory-mainColumn img { max-width: 100% !important; }
    .AppHeader, .AppHeader-inner {
      width: 100% !important;
      min-width: 0 !important;
      max-width: 100% !important;
    }
    .AppHeader-inner {
      overflow-x: auto !important;
      overscroll-behavior-x: contain !important;
    }
  `;
  const apply = () => {
    if (!document.head) return false;
    let style = document.getElementById('openscope-zhihu-phone-layout');
    if (!style) {
      style = document.createElement('style');
      style.id = 'openscope-zhihu-phone-layout';
      document.head.appendChild(style);
    }
    style.textContent = css;
    return true;
  };
  if (!apply()) {
    const waitForHead = new MutationObserver(() => {
      if (apply()) waitForHead.disconnect();
    });
    waitForHead.observe(document, { childList: true, subtree: true });
  }
})();

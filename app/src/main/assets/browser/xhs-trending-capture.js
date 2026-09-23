(() => {
  const matches = url => /\/api\/sns\/web\/v1\/search\/querytrending(?:\?|$)/.test(String(url));
  const capture = value => { window.__openscopeTrendingResponse = value; };
  const originalFetch = window.fetch;
  window.fetch = function (...args) {
    return originalFetch.apply(window, args).then(response => {
      if (response.ok && matches(response.url)) response.clone().json().then(capture).catch(() => {});
      return response;
    });
  };
  const originalOpen = XMLHttpRequest.prototype.open;
  XMLHttpRequest.prototype.open = function (method, url, ...rest) {
    if (matches(url)) this.addEventListener('load', function () {
      if (this.status === 200) {
        try { capture(this.responseType === 'json' ? this.response : JSON.parse(this.responseText)); } catch (_) {}
      }
    });
    return originalOpen.call(this, method, url, ...rest);
  };
})()

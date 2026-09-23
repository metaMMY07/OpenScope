// Desktop site in a phone-sized viewport. Never access form values, cookies, or native bridges.
(() => {
  if (window !== window.top || window.__openScopePhoneViewport) return;
  window.__openScopePhoneViewport = true;
  const content = 'width=device-width, initial-scale=1, user-scalable=yes';
  const apply = () => {
    const head = document.head;
    if (!head) return;
    let metas = head.querySelectorAll('meta[name="viewport"]');
    if (!metas.length) {
      const meta = document.createElement('meta');
      meta.name = 'viewport';
      meta.content = content;
      head.appendChild(meta);
      return;
    }
    metas.forEach(meta => { if (meta.content !== content) meta.content = content; });
  };
  const watchHead = () => {
    if (!document.head) return false;
    new MutationObserver(apply).observe(document.head, {
      childList: true, subtree: true, attributes: true, attributeFilter: ['content']
    });
    apply();
    return true;
  };
  if (!watchHead()) {
    const waitForHead = new MutationObserver(() => {
      if (watchHead()) waitForHead.disconnect();
    });
    waitForHead.observe(document, { childList: true, subtree: true });
  }
})();

// XHS's download button creates a JPEG Blob and clicks a detached <a download>.
// Android WebView does not persist that blob. Intercept only this image download;
// leave links, navigation, forms and all other object URLs to the official page.
(() => {
  if (window !== window.top || window.__openScopeImageDownload || !window.OpenScopeImageSaver) return;
  window.__openScopeImageDownload = true;
  const maxBytes = 20 * 1024 * 1024;
  const images = new Map();
  const createObjectURL = URL.createObjectURL;
  const revokeObjectURL = URL.revokeObjectURL;
  const click = HTMLAnchorElement.prototype.click;
  let transfer = Promise.resolve();

  URL.createObjectURL = function (blob) {
    const url = createObjectURL.call(this, blob);
    if (blob instanceof Blob && /^image\//i.test(blob.type) && blob.size <= maxBytes) {
      images.set(url, blob);
      if (images.size > 8) images.delete(images.keys().next().value);
    }
    return url;
  };
  URL.revokeObjectURL = function (url) {
    images.delete(url);
    return revokeObjectURL.call(this, url);
  };
  HTMLAnchorElement.prototype.click = function () {
    const blob = images.get(this.href);
    if (!blob || !/\.jpe?g$|\.png$|\.webp$|\.gif$|\.avif$/i.test(this.download || '')) {
      return click.apply(this, arguments);
    }
    images.delete(this.href);
    transfer = transfer.then(async () => {
      try {
        const bytes = await blob.arrayBuffer();
        OpenScopeImageSaver.postMessage(JSON.stringify({ type: 'start', size: bytes.byteLength }));
        OpenScopeImageSaver.postMessage(bytes);
      } catch (_) {
        OpenScopeImageSaver.postMessage(JSON.stringify({ type: 'error' }));
      }
    });
  };
})();

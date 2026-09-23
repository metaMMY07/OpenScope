// Adapt Bilibili's desktop video page to a phone viewport without reading page data.
(() => {
  if (window !== window.top || window.__openScopeBiliPhoneLayout) return;
  window.__openScopeBiliPhoneLayout = true;
  const css = `
    html, body, #app.app-v1 {
      width: 100% !important;
      max-width: 100% !important;
      min-width: 0 !important;
      overflow-x: hidden !important;
    }
    #mirror-vdcon.video-container-v1 {
      width: 100% !important;
      max-width: 100% !important;
      min-width: 0 !important;
      box-sizing: border-box !important;
      display: flex !important;
      flex-direction: column !important;
      padding: 0 12px !important;
    }
    #mirror-vdcon > .left-container,
    #mirror-vdcon > .right-container {
      width: 100% !important;
      max-width: 100% !important;
      min-width: 0 !important;
      margin: 0 !important;
      position: static !important;
      height: auto !important;
    }
    #mirror-vdcon > .fixed-sidenav-storage { display: none !important; }
    #mirror-vdcon .left-container > #bgm-entry { position: static !important; }
    #playerWrap, #bilibili-player, #bilibili-player .bpx-docker,
    #bilibili-player .bpx-player-container,
    #bilibili-player .bpx-player-video-area,
    #bilibili-player .bpx-player-video-wrap,
    #bilibili-player video {
      width: 100% !important;
      max-width: 100% !important;
    }
    #playerWrap, #bilibili-player, #bilibili-player .bpx-docker {
      height: auto !important;
      aspect-ratio: 16 / 10 !important;
      min-height: 0 !important;
    }
    #bilibili-player .bpx-player-container { height: 100% !important; }
    #viewbox_report, #viewbox_report .video-info-title,
    #viewbox_report .video-info-title-inner,
    #viewbox_report .video-info-meta,
    #viewbox_report .video-info-detail-list {
      height: auto !important;
      max-height: none !important;
      overflow: visible !important;
    }
    #viewbox_report .video-info-title-inner,
    #viewbox_report .video-info-detail-list { flex-wrap: wrap !important; }
    #viewbox_report .video-title {
      width: 100% !important;
      white-space: normal !important;
      overflow: visible !important;
      height: auto !important;
      line-height: 1.35 !important;
    }
    .bili-header, .bili-header .bili-header__bar {
      width: 100% !important;
      min-width: 0 !important;
      max-width: 100% !important;
    }
    .bili-header .bili-header__bar {
      overflow-x: auto !important;
      overscroll-behavior-x: contain !important;
    }
    #bilibili-player .bpx-player-container[data-screen="mini"] {
      position: relative !important;
      inset: auto !important;
      width: 100% !important;
      height: 100% !important;
      max-height: none !important;
      min-height: 0 !important;
      z-index: auto !important;
      box-shadow: none !important;
    }
    html.openscope-bili-fullscreen,
    html.openscope-bili-fullscreen body { overflow: hidden !important; }
    html.openscope-bili-fullscreen .bili-header { display: none !important; }
    html.openscope-bili-fullscreen #bilibili-player .bpx-player-sending-area { display: none !important; }
    html.openscope-bili-fullscreen #playerWrap {
      position: fixed !important;
      inset: 0 !important;
      z-index: 2147483647 !important;
      width: 100vw !important;
      height: 100vh !important;
      aspect-ratio: auto !important;
      max-width: none !important;
      background: black !important;
    }
    html.openscope-bili-fullscreen #bilibili-player,
    html.openscope-bili-fullscreen #bilibili-player .bpx-docker,
    html.openscope-bili-fullscreen #bilibili-player .bpx-player-container,
    html.openscope-bili-fullscreen #bilibili-player .bpx-player-primary-area,
    html.openscope-bili-fullscreen #bilibili-player .bpx-player-video-area,
    html.openscope-bili-fullscreen #bilibili-player .bpx-player-video-perch,
    html.openscope-bili-fullscreen #bilibili-player .bpx-player-video-wrap {
      position: relative !important;
      inset: auto !important;
      width: 100% !important;
      height: 100% !important;
      aspect-ratio: auto !important;
      max-width: none !important;
      max-height: none !important;
    }
    html.openscope-bili-fullscreen #bilibili-player video {
      width: 100% !important;
      height: 100% !important;
      object-fit: contain !important;
    }
  `;
  const apply = () => {
    if (!document.head) return false;
    let style = document.getElementById('openscope-bili-phone-layout');
    if (!style) {
      style = document.createElement('style');
      style.id = 'openscope-bili-phone-layout';
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

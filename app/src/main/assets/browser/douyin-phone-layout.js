// Reflow Douyin's desktop player so the whole frame and controls fit a phone.
(() => {
  if (window !== window.top || window.__openScopeDouyinPhoneLayout) return;
  window.__openScopeDouyinPhoneLayout = true;
  const css = `
    html, body, #root {
      width: 100% !important;
      min-width: 0 !important;
      max-width: 100% !important;
      overflow-x: hidden !important;
    }
    .jingxuanFeedList {
      box-sizing: border-box !important;
      flex: 1 1 auto !important;
      width: calc(100% - 24px) !important;
      min-width: 0 !important;
      max-width: 100% !important;
    }
    .jingxuan-scroll-element > div {
      box-sizing: border-box !important;
      grid-template-columns: minmax(0, 1fr) !important;
      width: 100% !important;
      min-width: 0 !important;
      max-width: 100% !important;
    }
    .jingxuan-scroll-element .discover-video-card-item {
      min-width: 0 !important;
      width: 100% !important;
    }
    .playerControlHeight {
      box-sizing: border-box !important;
      width: 100% !important;
      min-width: 0 !important;
      max-width: 100% !important;
      padding: 0 12px !important;
    }
    .playerControlHeight > .leftContainer,
    .leftContainer .video-detail-container,
    .leftContainer .basePlayerContainer,
    .leftContainer xg-video-container,
    .leftContainer video {
      box-sizing: border-box !important;
      width: 100% !important;
      min-width: 0 !important;
      max-width: 100% !important;
    }
    .leftContainer .video-detail-container {
      height: auto !important;
      aspect-ratio: 16 / 10 !important;
    }
    .leftContainer .basePlayerContainer { height: 100% !important; }
    .leftContainer xg-video-container,
    .leftContainer video {
      height: calc(100% - 48px) !important;
      object-fit: contain !important;
    }
    .modal-video-container,
    .modal-video-container .slidelist,
    .modal-video-container .sliderVideo,
    .modal-video-container .playerContainer,
    .modal-video-container .slider-video,
    .modal-video-container .basePlayerContainer,
    .modal-video-container xg-video-container,
    .modal-video-container video {
      box-sizing: border-box !important;
      width: 100vw !important;
      min-width: 0 !important;
      max-width: 100vw !important;
    }
    .modal-video-container video { object-fit: contain !important; }
    html.openscope-douyin-fullscreen,
    html.openscope-douyin-fullscreen body { overflow: hidden !important; }
    html.openscope-douyin-fullscreen .video-detail-container {
      position: fixed !important;
      inset: 0 !important;
      z-index: 2147483640 !important;
      width: 100vw !important;
      max-width: none !important;
      height: 100vh !important;
      min-height: 0 !important;
      margin: 0 !important;
      aspect-ratio: auto !important;
      border-radius: 0 !important;
      background: black !important;
    }
    html.openscope-douyin-fullscreen .video-detail-container .basePlayerContainer,
    html.openscope-douyin-fullscreen .video-detail-container xg-video-container,
    html.openscope-douyin-fullscreen .video-detail-container video {
      width: 100% !important;
      max-width: none !important;
    }
    html.openscope-douyin-fullscreen .video-detail-container .basePlayerContainer {
      height: 100% !important;
    }
    html.openscope-douyin-fullscreen .video-detail-container xg-video-container,
    html.openscope-douyin-fullscreen .video-detail-container video {
      height: calc(100% - 48px) !important;
      object-fit: contain !important;
    }
  `;
  const apply = () => {
    if (!document.head) return false;
    let style = document.getElementById('openscope-douyin-phone-layout');
    if (!style) {
      style = document.createElement('style');
      style.id = 'openscope-douyin-phone-layout';
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

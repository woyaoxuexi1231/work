(function() {
    const IP_CACHE_KEY = 'common_real_ip';
    const IP_CACHE_TIMESTAMP_KEY = 'common_real_ip_timestamp';
    const IP_CACHE_DURATION = 10 * 60 * 1000;

    function getCachedIP() {
        try {
            const cachedIP = localStorage.getItem(IP_CACHE_KEY);
            const cachedTime = localStorage.getItem(IP_CACHE_TIMESTAMP_KEY);
            if (cachedIP && cachedTime) {
                const elapsed = Date.now() - parseInt(cachedTime);
                if (elapsed < IP_CACHE_DURATION) {
                    return { ip: cachedIP, time: cachedTime };
                }
            }
        } catch (e) {}
        return null;
    }

    function saveIPToCache(ip) {
        try {
            localStorage.setItem(IP_CACHE_KEY, ip);
            localStorage.setItem(IP_CACHE_TIMESTAMP_KEY, Date.now().toString());
        } catch (e) {}
    }

    function isValidIP(ip) {
        if (!ip || typeof ip !== 'string') return false;
        ip = ip.trim();
        const ipv4Pattern = /^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$/;
        return ipv4Pattern.test(ip);
    }

    const IP_API_LIST = [
        { url: 'https://api.ipify.org?format=json', type: 'json', extract: 'ip', group: 1 },
        { url: 'https://api64.ipify.org?format=json', type: 'json', extract: 'ip', group: 1 },
        { url: 'https://ip.seeip.org/json', type: 'json', extract: 'ip', group: 2 },
        { url: 'https://api.my-ip.io/v2/ip', type: 'json', extract: 'ip', group: 2 },
        { url: 'https://icanhazip.com', type: 'text', group: 3 },
        { url: 'https://ifconfig.me/ip', type: 'text', group: 3 }
    ];

    let floatBtn, modal, overlay, closeBtn, refreshBtn, copyBtn;
    let ipValue, ipStatus, ipStatusText, ipCacheTime, ipBadge;
    let isDragging = false, hasMoved = false;
    let startX, startY, initialX, initialY;

    function initElements() {
        floatBtn = document.getElementById('ipFloatBtn');
        modal = document.getElementById('ipModal');
        overlay = document.getElementById('ipOverlay');
        closeBtn = document.getElementById('ipCloseBtn');
        refreshBtn = document.getElementById('ipRefreshBtn');
        copyBtn = document.getElementById('ipCopyBtn');
        ipValue = document.getElementById('ipValue');
        ipStatus = document.getElementById('ipStatus');
        ipStatusText = document.getElementById('ipStatusText');
        ipCacheTime = document.getElementById('ipCacheTime');
        ipBadge = document.getElementById('ipBadge');
    }

    async function fetchIP(showLoading = true) {
        if (showLoading) {
            ipValue.textContent = '获取中...';
            ipStatus.className = 'ip-status';
            ipStatusText.textContent = '正在获取';
            refreshBtn.disabled = true;
            refreshBtn.innerHTML = '<span class="ip-spinner"></span><span>获取中</span>';
        }

        const createRequest = (api) => {
            return new Promise((resolve) => {
                const controller = new AbortController();
                const timeout = setTimeout(() => {
                    controller.abort();
                    resolve({ success: false });
                }, 5000);

                fetch(api.url, { signal: controller.signal, cache: 'no-cache' })
                .then(async (response) => {
                    clearTimeout(timeout);
                    if (!response.ok) { resolve({ success: false }); return; }
                    let ip = '';
                    if (api.type === 'json') {
                        const data = await response.json();
                        ip = data[api.extract] || '';
                    } else {
                        ip = (await response.text()).trim();
                    }
                    resolve({ success: isValidIP(ip), ip: ip });
                })
                .catch(() => { clearTimeout(timeout); resolve({ success: false }); });
            });
        };

        for (const group of [1, 2, 3]) {
            const groupApis = IP_API_LIST.filter(api => api.group === group);
            const results = await Promise.allSettled(groupApis.map(createRequest));
            for (const result of results) {
                if (result.status === 'fulfilled' && result.value.success) {
                    saveIPToCache(result.value.ip);
                    updateIPDisplay(result.value.ip);
                    return result.value.ip;
                }
            }
        }

        updateIPDisplay(null);
        return null;
    }

    function updateIPDisplay(ip) {
        refreshBtn.disabled = false;
        refreshBtn.innerHTML = '<span>🔄</span><span>刷新IP</span>';

        if (ip) {
            ipValue.textContent = ip;
            ipStatus.className = 'ip-status';
            ipStatusText.textContent = '已获取';
            ipBadge.classList.remove('show');
        } else {
            ipValue.textContent = '获取失败';
            ipStatus.className = 'ip-status error';
            ipStatusText.textContent = '请点击刷新重试';
            ipBadge.classList.add('show');
        }

        const cached = getCachedIP();
        if (cached) {
            const date = new Date(parseInt(cached.time));
            const timeStr = date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
            ipCacheTime.textContent = `缓存时间: ${timeStr}`;
        } else {
            ipCacheTime.textContent = '';
        }
    }

    function showModal() {
        modal.classList.add('show');
        overlay.classList.add('show');
        const cached = getCachedIP();
        if (cached) {
            updateIPDisplay(cached.ip);
        } else {
            fetchIP(true);
        }
    }

    function hideModal() {
        modal.classList.remove('show');
        overlay.classList.remove('show');
    }

    function startDrag(e) {
        isDragging = true;
        hasMoved = false;
        
        const touch = e.touches ? e.touches[0] : e;
        startX = touch.clientX;
        startY = touch.clientY;
        initialX = floatBtn.offsetLeft;
        initialY = floatBtn.offsetTop;
        floatBtn.style.transition = 'none';
    }

    function doDrag(e) {
        if (!isDragging) return;
        e.preventDefault();
        const touch = e.touches ? e.touches[0] : e;
        const dx = touch.clientX - startX;
        const dy = touch.clientY - startY;

        // 判断是否移动超过阈值（移动端需要更大的阈值）
        const moveThreshold = e.touches ? 10 : 5;
        if (Math.abs(dx) > moveThreshold || Math.abs(dy) > moveThreshold) {
            hasMoved = true;
            isClicking = false;
        }

        if (hasMoved) {
            let newX = initialX + dx;
            let newY = initialY + dy;
            const maxX = window.innerWidth - floatBtn.offsetWidth;
            const maxY = window.innerHeight - floatBtn.offsetHeight;
            newX = Math.max(0, Math.min(newX, maxX));
            newY = Math.max(0, Math.min(newY, maxY));
            floatBtn.style.right = 'auto';
            floatBtn.style.bottom = 'auto';
            floatBtn.style.left = newX + 'px';
            floatBtn.style.top = newY + 'px';
        }
    }

    function endDrag(e) {
        if (!isDragging) return;
        isDragging = false;
        floatBtn.style.transition = 'transform 0.2s ease, box-shadow 0.2s ease';
    }

    async function copyIP() {
        const cached = getCachedIP();
        if (!cached) return;
        
        try {
            await navigator.clipboard.writeText(cached.ip);
            copyBtn.classList.add('copied');
            copyBtn.innerHTML = '<span>✅</span><span>已复制</span>';
            setTimeout(() => {
                copyBtn.classList.remove('copied');
                copyBtn.innerHTML = '<span>📋</span><span>复制</span>';
            }, 2000);
        } catch (e) {
            const textarea = document.createElement('textarea');
            textarea.value = cached.ip;
            document.body.appendChild(textarea);
            textarea.select();
            document.execCommand('copy');
            document.body.removeChild(textarea);
            copyBtn.classList.add('copied');
            copyBtn.innerHTML = '<span>✅</span><span>已复制</span>';
            setTimeout(() => {
                copyBtn.classList.remove('copied');
                copyBtn.innerHTML = '<span>📋</span><span>复制</span>';
            }, 2000);
        }
    }

    function injectHTML() {
        if (document.getElementById('ipFloatBtn')) return;
        
        const html = `
            <div class="ip-float-btn" id="ipFloatBtn">
                <span class="ip-float-btn-icon">📍</span>
                <div class="ip-float-btn-badge" id="ipBadge"></div>
            </div>
            <div class="ip-modal-overlay" id="ipOverlay"></div>
            <div class="ip-modal" id="ipModal">
                <div class="ip-modal-header">
                    <div class="ip-modal-title">📍 真实IP</div>
                    <div class="ip-modal-close" id="ipCloseBtn">✕</div>
                </div>
                <div class="ip-modal-body">
                    <div class="ip-display">
                        <div class="ip-label">你的IP地址</div>
                        <div class="ip-value" id="ipValue">获取中...</div>
                        <div class="ip-status" id="ipStatus">
                            <span class="ip-status-dot"></span>
                            <span id="ipStatusText">正在获取</span>
                        </div>
                    </div>
                    <div class="ip-actions">
                        <button class="ip-btn ip-btn-refresh" id="ipRefreshBtn">
                            <span>🔄</span><span>刷新IP</span>
                        </button>
                        <button class="ip-btn ip-btn-copy" id="ipCopyBtn">
                            <span>📋</span><span>复制</span>
                        </button>
                    </div>
                    <div class="ip-cache-time" id="ipCacheTime"></div>
                </div>
            </div>
        `;
        const div = document.createElement('div');
        div.innerHTML = html;
        document.body.appendChild(div.firstElementChild);
        document.body.appendChild(div.firstElementChild);
        document.body.appendChild(div.firstElementChild);
    }

    function init() {
        injectHTML();
        initElements();

        floatBtn.addEventListener('mousedown', startDrag);
        floatBtn.addEventListener('touchstart', startDrag, { passive: false });
        document.addEventListener('mousemove', doDrag);
        document.addEventListener('touchmove', doDrag, { passive: false });
        document.addEventListener('mouseup', endDrag);
        document.addEventListener('touchend', endDrag);
        closeBtn.addEventListener('click', () => { modal.classList.remove('show'); overlay.classList.remove('show'); });
        overlay.addEventListener('click', () => { modal.classList.remove('show'); overlay.classList.remove('show'); });
        refreshBtn.addEventListener('click', () => fetchIP(true));
        copyBtn.addEventListener('click', copyIP);

        floatBtn.addEventListener('click', function(e) {
            if (!hasMoved) {
                if (modal.classList.contains('show')) {
                    hideModal();
                } else {
                    showModal();
                }
            }
        });

        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape' && modal.classList.contains('show')) {
                modal.classList.remove('show');
                overlay.classList.remove('show');
            }
        });

        const cached = getCachedIP();
        if (!cached) {
            fetchIP(false);
        } else {
            updateIPDisplay(cached.ip);
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();

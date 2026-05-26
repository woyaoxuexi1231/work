import http.server
import os
import json
import urllib.parse
import sys

PORT = 8000
# 只允许访问此目录及其子目录
ROOT_PATH = r"D:\project\work"

HTML_CONTENT = """<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>工作目录浏览器</title>
    <script src="https://cdn.tailwindcss.com"></script>
    <script src="https://cdn.jsdelivr.net/npm/marked/marked.min.js"></script>
    <style>
        .tree-item {
            cursor: pointer;
            transition: background-color 0.15s;
            border-radius: 6px;
        }
        .tree-item:hover {
            background-color: #f3f4f6;
        }
        .tree-item.selected {
            background-color: #e5e7eb;
            font-weight: 500;
        }
        .arrow {
            transition: transform 0.2s;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            width: 16px;
            height: 16px;
            font-size: 10px;
            margin-right: 2px;
        }
        .arrow.expanded {
            transform: rotate(90deg);
        }
        .children {
            overflow: hidden;
            transition: max-height 0.3s ease;
        }
        .markdown-body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif;
            line-height: 1.6;
            color: #1f2937;
        }
        .markdown-body h1, .markdown-body h2, .markdown-body h3,
        .markdown-body h4, .markdown-body h5, .markdown-body h6 {
            margin-top: 24px;
            margin-bottom: 16px;
            font-weight: 600;
            line-height: 1.25;
        }
        .markdown-body h1 { font-size: 2em; border-bottom: 1px solid #e5e7eb; padding-bottom: 0.3em; }
        .markdown-body h2 { font-size: 1.5em; border-bottom: 1px solid #e5e7eb; padding-bottom: 0.3em; }
        .markdown-body h3 { font-size: 1.25em; }
        .markdown-body p { margin-bottom: 16px; }
        .markdown-body ul, .markdown-body ol { margin-bottom: 16px; padding-left: 2em; }
        .markdown-body li { margin-bottom: 4px; }
        .markdown-body code {
            padding: 0.2em 0.4em;
            background-color: rgba(27,31,35,0.05);
            border-radius: 4px;
            font-size: 85%;
        }
        .markdown-body pre {
            padding: 16px;
            background-color: #f6f8fa;
            border-radius: 8px;
            overflow-x: auto;
            margin-bottom: 16px;
        }
        .markdown-body pre code {
            background: none;
            padding: 0;
        }
        .markdown-body blockquote {
            padding: 0 1em;
            color: #6b7280;
            border-left: 0.25em solid #d1d5db;
            margin: 0 0 16px 0;
        }
        .markdown-body table {
            border-collapse: collapse;
            margin-bottom: 16px;
            width: 100%;
        }
        .markdown-body th, .markdown-body td {
            border: 1px solid #d1d5db;
            padding: 8px 12px;
        }
        .markdown-body th {
            background-color: #f9fafb;
            font-weight: 600;
        }
        .markdown-body img { max-width: 100%; }
    </style>
</head>
<body class="bg-gray-50 h-screen flex flex-col">
    <header class="bg-white shadow-sm px-6 py-3 flex items-center border-b">
        <span class="text-2xl mr-3">📁</span>
        <h1 class="text-lg font-semibold text-gray-800">工作目录浏览器</h1>
        <span class="ml-2 text-sm text-gray-400">点击左侧 .md 文件预览</span>
        <span id="root-path" class="ml-auto text-xs text-gray-400 font-mono"></span>
    </header>
    <main class="flex flex-1 overflow-hidden">
        <aside class="w-80 bg-white border-r overflow-y-auto p-3">
            <div id="file-tree" class="text-sm text-gray-700">
                <div class="flex items-center justify-center py-8 text-gray-400">正在加载目录...</div>
            </div>
        </aside>
        <section id="preview-section" class="flex-1 overflow-y-auto p-6 bg-gray-50">
            <div id="preview" class="max-w-3xl mx-auto">
                <div class="flex flex-col items-center justify-center h-full text-gray-400">
                    <svg class="w-16 h-16 mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M7 21h10a2 2 0 002-2V9.414a1 1 0 00-.293-.707l-5.414-5.414A1 1 0 0012.586 3H7a2 2 0 00-2 2v14a2 2 0 002 2z" />
                    </svg>
                    <p>点击左侧 Markdown 文件即可预览</p>
                </div>
            </div>
        </section>
    </main>
    <script>
        marked.setOptions({ breaks: true, gfm: true });
        const treeContainer = document.getElementById('file-tree');
        const previewContainer = document.getElementById('preview');
        const EXPAND_MAX_HEIGHT = '99999px';

        async function loadDir(path) {
            const res = await fetch(`/py-file/api/list?path=${encodeURIComponent(path)}`);
            if (!res.ok) throw new Error('目录加载失败');
            return await res.json();
        }

        async function loadFile(path) {
            const res = await fetch(`/py-file/api/file?path=${encodeURIComponent(path)}`);
            if (!res.ok) throw new Error('文件加载失败');
            return await res.text();
        }

        function createTreeItem(name, displayPath, type, level) {
            const div = document.createElement('div');
            div.className = 'tree-item flex items-center py-1.5 px-2 text-gray-700';
            div.style.paddingLeft = `${level * 16 + 8}px`;

            if (type === 'dir') {
                const arrow = document.createElement('span');
                arrow.className = 'arrow';
                arrow.textContent = '▶';
                div.appendChild(arrow);

                const icon = document.createElement('span');
                icon.textContent = '📁';
                icon.className = 'mr-1.5';
                div.appendChild(icon);

                const span = document.createElement('span');
                span.textContent = name;
                span.className = 'truncate';
                div.appendChild(span);

                let expanded = false;
                let childrenContainer = null;

                div.addEventListener('click', async (e) => {
                    e.stopPropagation();
                    document.querySelectorAll('.tree-item.selected').forEach(el => el.classList.remove('selected'));
                    div.classList.add('selected');

                    if (!expanded) {
                        arrow.classList.add('expanded');
                        if (!childrenContainer) {
                            childrenContainer = document.createElement('div');
                            childrenContainer.className = 'children';
                            childrenContainer.style.maxHeight = '0';
                            div.parentNode.insertBefore(childrenContainer, div.nextSibling);
                            try {
                                const data = await loadDir(displayPath);
                                const fragment = document.createDocumentFragment();
                                (data.dirs || []).forEach(d => fragment.appendChild(createTreeItem(d.name, d.path, 'dir', level + 1)));
                                (data.files || []).forEach(f => fragment.appendChild(createTreeItem(f.name, f.path, 'file', level + 1)));
                                if ((!data.dirs || data.dirs.length === 0) && (!data.files || data.files.length === 0)) {
                                    const empty = document.createElement('div');
                                    empty.className = 'text-gray-400 italic py-1';
                                    empty.style.paddingLeft = `${(level+1)*16+8}px`;
                                    empty.textContent = '空目录';
                                    fragment.appendChild(empty);
                                }
                                childrenContainer.appendChild(fragment);
                                requestAnimationFrame(() => {
                                    childrenContainer.style.maxHeight = EXPAND_MAX_HEIGHT;
                                });
                            } catch (err) {
                                childrenContainer.innerHTML = `<div class="text-red-400 italic py-1 pl-8">加载失败</div>`;
                                requestAnimationFrame(() => {
                                    childrenContainer.style.maxHeight = EXPAND_MAX_HEIGHT;
                                });
                            }
                        } else {
                            childrenContainer.style.maxHeight = EXPAND_MAX_HEIGHT;
                        }
                        expanded = true;
                    } else {
                        arrow.classList.remove('expanded');
                        if (childrenContainer) {
                            childrenContainer.style.maxHeight = '0';
                        }
                        expanded = false;
                    }
                });
            } else {
                const icon = document.createElement('span');
                icon.textContent = name.endsWith('.md') ? '📝' : '📄';
                icon.className = 'mr-1.5';
                div.appendChild(icon);

                const span = document.createElement('span');
                span.textContent = name;
                span.className = 'truncate';
                div.appendChild(span);

                div.addEventListener('click', async (e) => {
                    e.stopPropagation();
                    document.querySelectorAll('.tree-item.selected').forEach(el => el.classList.remove('selected'));
                    div.classList.add('selected');

                    if (!name.toLowerCase().endsWith('.md')) {
                        previewContainer.innerHTML = `<div class="flex flex-col items-center justify-center h-full text-gray-400"><p>⚠️ 仅支持预览 .md 文件</p></div>`;
                        return;
                    }

                    try {
                        const content = await loadFile(displayPath);
                        previewContainer.innerHTML = `<div class="markdown-body">${marked.parse(content)}</div>`;
                    } catch (err) {
                        previewContainer.innerHTML = `<div class="text-red-500 p-4">❌ 读取失败: ${err.message}</div>`;
                    }
                });
            }
            return div;
        }

        (async function init() {
            try {
                const data = await loadDir('');
                treeContainer.innerHTML = '';
                const fragment = document.createDocumentFragment();

                // 根目录节点
                const rootDiv = document.createElement('div');
                rootDiv.className = 'tree-item flex items-center py-1.5 px-2 text-gray-700 font-medium';
                rootDiv.style.paddingLeft = '8px';
                rootDiv.innerHTML = '<span class="mr-1.5">📂</span><span class="truncate">' + (data.rootName || 'work') + '</span>';

                let rootExpanded = true;
                let rootChildren = document.createElement('div');
                rootChildren.className = 'children';
                rootChildren.style.maxHeight = EXPAND_MAX_HEIGHT;

                (data.dirs || []).forEach(d => rootChildren.appendChild(createTreeItem(d.name, d.path, 'dir', 1)));
                (data.files || []).forEach(f => rootChildren.appendChild(createTreeItem(f.name, f.path, 'file', 1)));
                if ((!data.dirs || data.dirs.length === 0) && (!data.files || data.files.length === 0)) {
                    const empty = document.createElement('div');
                    empty.className = 'text-gray-400 italic py-1';
                    empty.style.paddingLeft = '24px';
                    empty.textContent = '空目录';
                    rootChildren.appendChild(empty);
                }

                fragment.appendChild(rootDiv);
                fragment.appendChild(rootChildren);
                treeContainer.appendChild(fragment);

                document.getElementById('root-path').textContent = data.rootPath || '';
            } catch (err) {
                treeContainer.innerHTML = '<div class="text-red-400 text-center py-8">无法加载目录: ' + err.message + '</div>';
            }
        })();
    </script>
</body>
</html>"""

def is_safe_path(requested_path):
    """检查请求路径是否在 ROOT_PATH 范围内，防止目录遍历"""
    try:
        real_requested = os.path.realpath(requested_path)
        real_root = os.path.realpath(ROOT_PATH)
        # 确保请求路径以 ROOT_PATH 开头
        return real_requested.startswith(real_root + os.sep) or real_requested == real_root
    except Exception:
        return False

class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path
        query = urllib.parse.parse_qs(parsed.query)

        try:
            if path == '/' or path == '/index.html':
                self.send_response(200)
                self.send_header('Content-type', 'text/html; charset=utf-8')
                self.end_headers()
                self.wfile.write(HTML_CONTENT.encode('utf-8'))
                return

            elif path == '/api/list':
                subpath = query.get('path', [''])[0]

                if subpath == '':
                    # 返回根目录内容
                    full_path = os.path.normpath(ROOT_PATH)
                else:
                    full_path = os.path.normpath(subpath)
                    # 安全检查
                    if not is_safe_path(full_path):
                        self.send_error(403, 'Access denied: out of allowed directory')
                        return

                if not os.path.isdir(full_path):
                    self.send_error(404, 'Directory not found')
                    return

                try:
                    items = os.listdir(full_path)
                except PermissionError:
                    self.send_error(403, 'Permission denied')
                    return

                dirs, files = [], []
                for item in items:
                    item_full = os.path.join(full_path, item)
                    if os.path.isdir(item_full):
                        dirs.append({'name': item, 'path': item_full, 'type': 'dir'})
                    else:
                        files.append({'name': item, 'path': item_full, 'type': 'file'})

                dirs.sort(key=lambda x: x['name'].lower())
                files.sort(key=lambda x: x['name'].lower())

                self.send_response(200)
                self.send_header('Content-type', 'application/json; charset=utf-8')
                self.end_headers()

                result = {
                    'dirs': dirs,
                    'files': files,
                    'rootName': os.path.basename(ROOT_PATH),
                    'rootPath': ROOT_PATH
                }
                self.wfile.write(json.dumps(result, ensure_ascii=False).encode('utf-8'))

            elif path == '/api/file':
                subpath = query.get('path', [''])[0]
                if not subpath:
                    self.send_error(400, 'Missing file path')
                    return

                full_path = os.path.normpath(subpath)

                # 安全检查
                if not is_safe_path(full_path):
                    self.send_error(403, 'Access denied: out of allowed directory')
                    return

                if not os.path.isfile(full_path):
                    self.send_error(404, 'File not found')
                    return
                if not full_path.lower().endswith('.md'):
                    self.send_error(400, 'Only .md files are supported for preview')
                    return

                try:
                    with open(full_path, 'r', encoding='utf-8', errors='ignore') as f:
                        content = f.read()
                except Exception:
                    self.send_error(500, 'Failed to read file')
                    return

                self.send_response(200)
                self.send_header('Content-type', 'text/plain; charset=utf-8')
                self.end_headers()
                self.wfile.write(content.encode('utf-8'))

            else:
                self.send_error(404, 'Not Found')
        except Exception as e:
            self.send_error(500, str(e))

if __name__ == '__main__':
    if not os.path.isdir(ROOT_PATH):
        print(f"❌ 目录不存在: {ROOT_PATH}")
        sys.exit(1)

    print(f"📁 根目录: {ROOT_PATH}")
    print("⚠️  仅限本地信任环境使用！")
    server = http.server.ThreadingHTTPServer(('0.0.0.0', PORT), Handler)
    print(f'✅ 服务已启动: http://localhost:{PORT}')
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print('\n🛑 服务已停止')
        server.server_close()
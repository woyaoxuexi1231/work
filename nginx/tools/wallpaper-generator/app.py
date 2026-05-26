from flask import Flask, render_template, request, jsonify
import random

app = Flask(__name__)

GRADIENTS = [
    ['#667eea', '#764ba2'],
    ['#f093fb', '#f5576c'],
    ['#4facfe', '#00f2fe'],
    ['#43e97b', '#38f9d7'],
    ['#fa709a', '#fee140'],
    ['#a8edea', '#fed6e3'],
    ['#ff9a9e', '#fecfef'],
    ['#ffecd2', '#fcb69f'],
    ['#667eea', '#764ba2'],
    ['#43e97b', '#38f9d7'],
    ['#fa709a', '#fee140'],
    ['#a18cd1', '#fbc2eb']
]

PATTERNS = ['none', 'dots', 'grid', 'waves', 'stars']

QUOTES = [
    '生活不止眼前的苟且，还有诗和远方',
    '明天会更好',
    '成功属于坚持不懈的人',
    '相信自己，你可以的',
    '每一天都是新的开始',
    '不忘初心，方得始终',
    '努力到无能为力，拼搏到感动自己',
    '梦想还是要有的，万一实现了呢',
    '路漫漫其修远兮，吾将上下而求索',
    '天生我材必有用'
]

@app.route('/')
def index():
    return render_template('index.html')

@app.route('/generate', methods=['POST'])
def generate_wallpaper():
    data = request.json
    gradient = data.get('gradient', random.choice(GRADIENTS))
    pattern = data.get('pattern', 'none')
    quote = data.get('quote', random.choice(QUOTES))
    show_quote = data.get('show_quote', True)
    
    return jsonify({
        'gradient': gradient,
        'pattern': pattern,
        'quote': quote,
        'show_quote': show_quote
    })

@app.route('/random-gradient', methods=['POST'])
def random_gradient():
    return jsonify({'gradient': random.choice(GRADIENTS)})

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5009, debug=True)
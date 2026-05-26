from flask import Flask, render_template, request, jsonify
import random

app = Flask(__name__)

WORD_LISTS = {
    'english': [
        {'word': 'apple', 'meaning': '苹果'},
        {'word': 'book', 'meaning': '书'},
        {'word': 'computer', 'meaning': '电脑'},
        {'word': 'dream', 'meaning': '梦想'},
        {'word': 'elephant', 'meaning': '大象'},
        {'word': 'flower', 'meaning': '花'},
        {'word': 'garden', 'meaning': '花园'},
        {'word': 'happy', 'meaning': '快乐的'},
        {'word': 'island', 'meaning': '岛屿'},
        {'word': 'jungle', 'meaning': '丛林'},
        {'word': 'king', 'meaning': '国王'},
        {'word': 'library', 'meaning': '图书馆'},
        {'word': 'mountain', 'meaning': '山'},
        {'word': 'night', 'meaning': '夜晚'},
        {'word': 'ocean', 'meaning': '海洋'},
        {'word': 'peace', 'meaning': '和平'},
        {'word': 'queen', 'meaning': '女王'},
        {'word': 'rainbow', 'meaning': '彩虹'},
        {'word': 'sunshine', 'meaning': '阳光'},
        {'word': 'treasure', 'meaning': '宝藏'}
    ],
    'chinese': [
        {'word': '美丽', 'meaning': 'beautiful'},
        {'word': '智慧', 'meaning': 'wisdom'},
        {'word': '勇敢', 'meaning': 'brave'},
        {'word': '幸福', 'meaning': 'happiness'},
        {'word': '梦想', 'meaning': 'dream'},
        {'word': '希望', 'meaning': 'hope'},
        {'word': '友谊', 'meaning': 'friendship'},
        {'word': '爱情', 'meaning': 'love'},
        {'word': '自由', 'meaning': 'freedom'},
        {'word': '成功', 'meaning': 'success'}
    ]
}

@app.route('/')
def index():
    return render_template('index.html')

@app.route('/get-words', methods=['POST'])
def get_words():
    data = request.json
    category = data.get('category', 'english')
    count = data.get('count', 5)
    
    words = WORD_LISTS.get(category, WORD_LISTS['english'])
    shuffled = random.sample(words, min(count, len(words)))
    
    return jsonify({'words': shuffled})

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5005, debug=True)
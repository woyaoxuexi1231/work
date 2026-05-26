from flask import Flask, render_template, request, jsonify
import random

app = Flask(__name__)

JOKES = [
    {
        'type': 'pun',
        'setup': '为什么程序员总是分不清圣诞节和万圣节？',
        'punchline': '因为 Dec 25 等于 Oct 31！(十进制25 = 八进制31)'
    },
    {
        'type': 'programming',
        'setup': '为什么 Java 程序员总是很冷静？',
        'punchline': '因为他们有很多 try-catch！'
    },
    {
        'type': 'life',
        'setup': '什么东西越洗越脏？',
        'punchline': '水！'
    },
    {
        'type': 'pun',
        'setup': '为什么数学书总是很忧郁？',
        'punchline': '因为它有太多的问题！'
    },
    {
        'type': 'programming',
        'setup': '为什么 Python 是最友好的编程语言？',
        'punchline': '因为它有很多蛇(友)！'
    },
    {
        'type': 'life',
        'setup': '一个人在沙漠里走了三天三夜，最想做什么？',
        'punchline': '撒尿！'
    },
    {
        'type': 'pun',
        'setup': '为什么闹钟总是很聪明？',
        'punchline': '因为它总是会"响"！'
    },
    {
        'type': 'programming',
        'setup': '为什么前端工程师总是很快乐？',
        'punchline': '因为他们每天都能看到新的"Vue"！'
    },
    {
        'type': 'life',
        'setup': '什么东西打破了才能用？',
        'punchline': '鸡蛋！'
    },
    {
        'type': 'pun',
        'setup': '为什么鱼不会说话？',
        'punchline': '因为它们怕"水"话！'
    },
    {
        'type': 'programming',
        'setup': '为什么 C++ 程序员不喜欢社交？',
        'punchline': '因为他们更喜欢"class"而不是"party"！'
    },
    {
        'type': 'life',
        'setup': '什么东西越热越往上爬？',
        'punchline': '温度计！'
    },
    {
        'type': 'pun',
        'setup': '为什么自行车不能自己站起来？',
        'punchline': '因为它只有两个"轮"！'
    },
    {
        'type': 'programming',
        'setup': '为什么数据库管理员总是很安静？',
        'punchline': '因为他们总是在"SQL"！'
    },
    {
        'type': 'life',
        'setup': '什么东西有头没有脚？',
        'punchline': '砖头！'
    }
]

@app.route('/')
def index():
    return render_template('index.html')

@app.route('/get-joke', methods=['POST'])
def get_joke():
    data = request.json
    joke_type = data.get('type', 'all')
    
    if joke_type == 'all':
        joke = random.choice(JOKES)
    else:
        filtered = [j for j in JOKES if j['type'] == joke_type]
        joke = random.choice(filtered) if filtered else random.choice(JOKES)
    
    return jsonify(joke)

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5006, debug=True)
from flask import Flask, render_template, request, jsonify
import random

app = Flask(__name__)

@app.route('/')
def index():
    return render_template('index.html')

@app.route('/start-game', methods=['POST'])
def start_game():
    data = request.json
    min_num = data.get('min', 1)
    max_num = data.get('max', 100)
    
    secret_number = random.randint(min_num, max_num)
    
    return jsonify({
        'secret_number': secret_number,
        'min': min_num,
        'max': max_num
    })

@app.route('/guess', methods=['POST'])
def guess():
    data = request.json
    secret_number = data.get('secret_number')
    guess = data.get('guess')
    
    try:
        guess = int(guess)
    except ValueError:
        return jsonify({'result': 'invalid', 'message': '请输入有效的数字'})
    
    if guess < secret_number:
        return jsonify({'result': 'low', 'message': '太小了！再试试更大的数字'})
    elif guess > secret_number:
        return jsonify({'result': 'high', 'message': '太大了！再试试更小的数字'})
    else:
        return jsonify({'result': 'correct', 'message': '🎉 恭喜！你猜对了！'})

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5010, debug=True)
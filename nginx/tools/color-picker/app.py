from flask import Flask, render_template, request, jsonify
import random

app = Flask(__name__)

PRESET_COLORS = [
    '#FF6B6B', '#4ECDC4', '#45B7D1', '#96CEB4', '#FFEAA7',
    '#DDA0DD', '#98D8C8', '#F7DC6F', '#BB8FCE', '#85C1E9',
    '#F8B500', '#00CED1', '#FF69B4', '#32CD32', '#FF4500'
]

@app.route('/')
def index():
    return render_template('index.html')

@app.route('/random', methods=['POST'])
def random_color():
    color = random.choice(PRESET_COLORS)
    return jsonify({'color': color})

@app.route('/palette', methods=['POST'])
def generate_palette():
    count = request.json.get('count', 5)
    palette = random.sample(PRESET_COLORS, min(count, len(PRESET_COLORS)))
    return jsonify({'palette': palette})

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5003, debug=True)
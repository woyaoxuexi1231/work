from flask import Flask, render_template, request, jsonify
import random
import string

app = Flask(__name__)

@app.route('/')
def index():
    return render_template('index.html')

@app.route('/generate', methods=['POST'])
def generate_password():
    data = request.json
    length = data.get('length', 12)
    include_uppercase = data.get('include_uppercase', True)
    include_lowercase = data.get('include_lowercase', True)
    include_numbers = data.get('include_numbers', True)
    include_symbols = data.get('include_symbols', False)
    
    charset = ''
    if include_uppercase:
        charset += string.ascii_uppercase
    if include_lowercase:
        charset += string.ascii_lowercase
    if include_numbers:
        charset += string.digits
    if include_symbols:
        charset += string.punctuation
    
    if not charset:
        return jsonify({'error': '至少选择一种字符类型'}), 400
    
    password = ''.join(random.choice(charset) for _ in range(length))
    
    return jsonify({'password': password})

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5001, debug=True)
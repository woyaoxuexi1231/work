from flask import Flask, render_template, request, jsonify
import qrcode
from io import BytesIO
import base64

app = Flask(__name__)

@app.route('/')
def index():
    return render_template('index.html')

@app.route('/generate', methods=['POST'])
def generate_qrcode():
    data = request.json
    content = data.get('content', '')
    size = data.get('size', 200)
    
    if not content.strip():
        return jsonify({'error': '请输入内容'}), 400
    
    qr = qrcode.QRCode(
        version=1,
        error_correction=qrcode.constants.ERROR_CORRECT_L,
        box_size=10,
        border=4,
    )
    qr.add_data(content)
    qr.make(fit=True)
    
    img = qr.make_image(fill_color='black', back_color='white')
    
    buffer = BytesIO()
    img.save(buffer, format='PNG')
    buffer.seek(0)
    
    img_base64 = base64.b64encode(buffer.read()).decode('utf-8')
    
    return jsonify({'image': f'data:image/png;base64,{img_base64}'})

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5002, debug=True)
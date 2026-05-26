from flask import Flask, render_template, request, jsonify

app = Flask(__name__)

@app.route('/')
def index():
    return render_template('index.html')

@app.route('/calculate', methods=['POST'])
def calculate_bmi():
    data = request.json
    weight = data.get('weight')
    height = data.get('height')
    
    if not weight or not height:
        return jsonify({'error': '请输入体重和身高'}), 400
    
    try:
        weight = float(weight)
        height = float(height) / 100  # 转换为米
        
        if weight <= 0 or height <= 0:
            return jsonify({'error': '体重和身高必须大于0'}), 400
        
        bmi = weight / (height * height)
        
        if bmi < 18.5:
            category = '偏瘦'
            color = 'blue'
            advice = '您的体重偏轻，建议适当增加营养摄入，保持均衡饮食。'
        elif bmi < 24:
            category = '正常'
            color = 'green'
            advice = '恭喜！您的体重在正常范围内，请继续保持健康的生活方式。'
        elif bmi < 28:
            category = '偏胖'
            color = 'orange'
            advice = '您的体重略微超标，建议适当控制饮食，增加运动量。'
        else:
            category = '肥胖'
            color = 'red'
            advice = '您的体重超标较多，建议咨询医生制定科学的减重计划。'
        
        return jsonify({
            'bmi': round(bmi, 1),
            'category': category,
            'color': color,
            'advice': advice
        })
    
    except ValueError:
        return jsonify({'error': '请输入有效的数字'}), 400

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5007, debug=True)
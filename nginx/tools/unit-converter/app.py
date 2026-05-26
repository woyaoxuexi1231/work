from flask import Flask, render_template, request, jsonify

app = Flask(__name__)

CONVERSION_TYPES = {
    'length': {
        'name': '长度',
        'units': ['米', '千米', '厘米', '毫米', '英寸', '英尺', '码', '英里'],
        'factors': [1, 0.001, 100, 1000, 39.3701, 3.28084, 1.09361, 0.000621371]
    },
    'weight': {
        'name': '重量',
        'units': ['千克', '克', '毫克', '吨', '磅', '盎司'],
        'factors': [1, 1000, 1000000, 0.001, 2.20462, 35.274]
    },
    'temperature': {
        'name': '温度',
        'units': ['摄氏度', '华氏度', '开尔文'],
        'factors': None  # 特殊处理
    },
    'volume': {
        'name': '体积',
        'units': ['立方米', '升', '毫升', '立方厘米', '立方英寸', '加仑'],
        'factors': [1, 1000, 1000000, 1000000, 61023.7, 264.172]
    }
}

@app.route('/')
def index():
    return render_template('index.html')

@app.route('/convert', methods=['POST'])
def convert():
    data = request.json
    from_unit = data.get('from_unit')
    to_unit = data.get('to_unit')
    value = data.get('value')
    conversion_type = data.get('type')
    
    try:
        value = float(value)
    except ValueError:
        return jsonify({'error': '请输入有效的数字'}), 400
    
    if conversion_type == 'temperature':
        return convert_temperature(value, from_unit, to_unit)
    
    types = CONVERSION_TYPES.get(conversion_type)
    if not types:
        return jsonify({'error': '不支持的转换类型'}), 400
    
    try:
        from_idx = types['units'].index(from_unit)
        to_idx = types['units'].index(to_unit)
    except ValueError:
        return jsonify({'error': '无效的单位'}), 400
    
    factors = types['factors']
    result = value * factors[to_idx] / factors[from_idx]
    
    return jsonify({'result': round(result, 6)})

def convert_temperature(value, from_unit, to_unit):
    # 先转换为摄氏度
    if from_unit == '华氏度':
        celsius = (value - 32) * 5/9
    elif from_unit == '开尔文':
        celsius = value - 273.15
    else:
        celsius = value
    
    # 再转换为目标单位
    if to_unit == '华氏度':
        result = celsius * 9/5 + 32
    elif to_unit == '开尔文':
        result = celsius + 273.15
    else:
        result = celsius
    
    return jsonify({'result': round(result, 4)})

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5008, debug=True)
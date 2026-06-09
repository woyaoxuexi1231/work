"""Eureka Demo - 注册到 Eureka 的极简 HTTP 服务"""
import http.server, json, socket, os, time, threading
import requests

EUREKA  = os.getenv("EUREKA_ADDR",  "localhost:8761")
SVC     = os.getenv("SERVICE_NAME", "PYTHON-DEMO")
PORT    = int(os.getenv("SERVER_PORT", "18082"))
HOST    = socket.gethostname()
IP      = os.getenv("MY_IP", socket.gethostbyname(HOST))
INST_ID = f"{HOST}:{SVC}:{PORT}"

URL = f"http://{EUREKA}/eureka/v2/apps/{SVC}"
BODY = {"instance":{"instanceId":INST_ID,"hostName":HOST,"app":SVC,
        "ipAddr":IP,"status":"UP","port":{"$":PORT,"@enabled":"true"},
        "dataCenterInfo":{"@class":"com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo","name":"MyOwn"}}}

def register():   r = requests.post(URL, json=BODY);            print(f"[reg] {r.status_code}")
def heartbeat():
    while True:
        try: r = requests.put(f"{URL}/{INST_ID}");             print(f"[beat] {r.status_code}")
        except: print("[beat] err")
        time.sleep(30)

class H(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        d = {"registry":"Eureka","mode":"AP","service":SVC,
             "hostname":HOST,"ip":IP,"port":PORT}
        self.send_response(200); self.send_header("Content-Type","application/json")
        self.end_headers(); self.wfile.write(json.dumps(d,indent=2).encode())
    def log_message(self, f, *a): pass

if __name__ == "__main__":
    print(f"\n  Eureka Demo [AP] -> {EUREKA}")
    print(f"  {HOST} ({IP}):{PORT}\n")
    register()
    threading.Thread(target=heartbeat, daemon=True).start()
    s = http.server.HTTPServer(("0.0.0.0", PORT), H)
    try: s.serve_forever()
    except KeyboardInterrupt:
        try: requests.delete(f"{URL}/{INST_ID}")
        except: pass
        print("\nbyebye")

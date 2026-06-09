"""Nacos Demo - 注册到 Nacos 的极简 HTTP 服务"""
import http.server, json, socket, os, time, threading
import requests

NACOS   = os.getenv("NACOS_ADDR",   "localhost:8848")
SVC     = os.getenv("SERVICE_NAME", "nacos-demo")
PORT    = int(os.getenv("SERVER_PORT", "18080"))
EPHEM   = os.getenv("EPHEMERAL", "true")   # "true"=AP零时实例  "false"=CP永久实例
HOST    = socket.gethostname()
IP      = os.getenv("MY_IP", socket.gethostbyname(HOST))

BASE = f"http://{NACOS}/nacos/v1/ns/instance"

def call(method, path, **params):
    p = {"serviceName": SVC, "ip": IP, "port": PORT, "ephemeral": EPHEM, **params}
    r = requests.request(method, f"{BASE}{path}", params=p)
    return r

def register():   call("POST", "")
def heartbeat():
    while True:
        try: call("PUT", "/beat"); print(f"[beat] ok")
        except: print("[beat] err")
        time.sleep(5)

class H(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        d = {"registry":"Nacos","mode":"AP" if EPHEM=="true" else "CP",
             "service":SVC,"hostname":HOST,"ip":IP,"port":PORT,"ephemeral":EPHEM}
        self.send_response(200); self.send_header("Content-Type","application/json")
        self.end_headers(); self.wfile.write(json.dumps(d,indent=2).encode())
    def log_message(self, f, *a): pass

if __name__ == "__main__":
    print(f"\n  Nacos Demo [{ 'AP' if EPHEM=='true' else 'CP' }] -> {NACOS}")
    print(f"  {HOST} ({IP}):{PORT}  ephemeral={EPHEM}\n")
    register()
    threading.Thread(target=heartbeat, daemon=True).start()
    s = http.server.HTTPServer(("0.0.0.0", PORT), H)
    try: s.serve_forever()
    except KeyboardInterrupt: call("DELETE", ""); print("\nbyebye")

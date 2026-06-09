"""Nacos Demo - 注册到 Nacos 的极简 HTTP 服务"""
import http.server, json, socket, os, time, threading
import requests

NACOS = os.getenv("NACOS_ADDR", "192.168.3.100:8848")
SVC   = os.getenv("SERVICE_NAME", "nacos-demo")
PORT  = int(os.getenv("SERVER_PORT", "18080"))
EPHEM = os.getenv("EPHEMERAL", "true")  # "true"=AP临时实例  "false"=CP持久实例
HOST  = socket.gethostname()
IP    = os.getenv("MY_IP", socket.gethostbyname(HOST))

# Nacos Open API: http://localhost:8848/nacos/v1/ns/instance
API   = f"http://{NACOS}/nacos/v1/ns"

def register():
    params = {
        "serviceName": SVC,
        "ip":          IP,
        "port":        PORT,
        "ephemeral":   EPHEM,
        "namespaceId": "public",
    }
    r = requests.post(f"{API}/instance", params=params)
    print(f"[register] {r.status_code} {r.text.strip()}")

def send_heartbeat():
    """Nacos 心跳：PUT /v1/ns/instance/beat"""
    beat = json.dumps({"ip": IP, "port": PORT, "state": "alive"})
    params = {
        "serviceName": SVC,
        "ip":          IP,
        "port":        PORT,
        "namespaceId": "public",
        "beat":        beat,
    }
    r = requests.put(f"{API}/instance/beat", params=params)
    return r

def heartbeat_loop():
    while True:
        try:
            r = send_heartbeat()
            print(f"[beat] {r.status_code}")
        except Exception as e:
            print(f"[beat] err: {e}")
        time.sleep(5)

def deregister():
    params = {"serviceName": SVC, "ip": IP, "port": PORT, "namespaceId": "public"}
    r = requests.delete(f"{API}/instance", params=params)
    print(f"[deregister] {r.status_code} {r.text.strip()}")

class H(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        d = {
            "registry":    "Nacos",
            "mode":        "AP" if EPHEM == "true" else "CP",
            "service":     SVC,
            "hostname":    HOST,
            "ip":          IP,
            "port":        PORT,
            "ephemeral":   EPHEM,
            "nacos_addr":  NACOS,
        }
        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.end_headers()
        self.wfile.write(json.dumps(d, indent=2, ensure_ascii=False).encode())
    def log_message(self, f, *a): pass

if __name__ == "__main__":
    print(f"\n  Nacos Demo [{ 'AP' if EPHEM == 'true' else 'CP' }]")
    print(f"  Nacos: {NACOS}")
    print(f"  Service: {SVC}  |  {HOST} ({IP}):{PORT}")
    print(f"  ephemeral={EPHEM}\n")
    register()
    threading.Thread(target=heartbeat_loop, daemon=True).start()
    s = http.server.HTTPServer(("0.0.0.0", PORT), H)
    try:
        s.serve_forever()
    except KeyboardInterrupt:
        deregister()
        print("\nbyebye")

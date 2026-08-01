from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from functools import partial
from pathlib import Path
import socket


PORT = 8787


class Handler(SimpleHTTPRequestHandler):
    def end_headers(self):
        self.send_header("Cache-Control", "no-store")
        super().end_headers()


if __name__ == "__main__":
    root = Path(__file__).resolve().parent
    server = ThreadingHTTPServer(("0.0.0.0", PORT), partial(Handler, directory=str(root)))
    print(f"Calibrador local: http://127.0.0.1:{PORT}")
    for address in socket.gethostbyname_ex(socket.gethostname())[2]:
        if not address.startswith("127."):
            print(f"Calibrador na rede: http://{address}:{PORT}")
    server.serve_forever()

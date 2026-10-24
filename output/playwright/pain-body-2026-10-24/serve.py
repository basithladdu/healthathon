from http.server import HTTPServer,SimpleHTTPRequestHandler
from pathlib import Path
from urllib.parse import urlparse,unquote
public=Path(r"F:/CODE/healthathon/prototype/public/pain-body-viewer").resolve()
qa=Path(r"F:/CODE/healthathon/output/playwright/pain-body-2026-10-24").resolve()
class Handler(SimpleHTTPRequestHandler):
 def translate_path(self,path):
  route=unquote(urlparse(path).path)
  if route in ["/","/harness.html"]:return str(qa/"harness.html")
  if not route.startswith("/viewer/"):return str(qa/"not-found")
  target=(public/route[len("/viewer/"):]).resolve()
  return str(target) if target.is_relative_to(public) else str(qa/"not-found")
HTTPServer(("127.0.0.1",8796),Handler).serve_forever()

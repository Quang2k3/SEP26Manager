package org.example.sep26management.presentation.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Scanner page using ZXing (QR only) — optimized for iOS Safari.
 * QR is far more reliable than 1D barcodes on mobile.
 *
 * Flow:
 * - Open /v1/scan?token=SCAN_TOKEN on iPhone
 * - Scan QR => POST /api/v1/scan-events (store in Redis by session)
 * - If QR fails => user manually types SKU in the input and taps Scan
 */
@RestController
public class ScannerPageController {

    @GetMapping(value = "/v1/scan/url", produces = MediaType.TEXT_PLAIN_VALUE)
    public String getScanUrl(@RequestParam("token") String token, HttpServletRequest request) {
        String base = request.getScheme() + "://" + request.getServerName()
                + (request.getServerPort() == 80 || request.getServerPort() == 443 ? ""
                        : ":" + request.getServerPort());
        // add a cache-buster
        return base + "/api/v1/scan?token=" + token + "&v=qr2";
    }

    @GetMapping(value = "/v1/scan", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> scannerPage(@RequestParam("token") String token) {
        String html = buildHtml(escapeForJs(token));
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
                .header("Pragma", "no-cache")
                .body(html);
    }

    private static String escapeForJs(String s) {
        if (s == null)
            return "";
        return s.replace("\\", "\\\\").replace("'", "\\'");
    }

    private String buildHtml(String token) {
        return "<!DOCTYPE html>" +
                "<html lang='vi'><head>" +
                "<meta charset='UTF-8'>" +
                "<meta name='viewport' content='width=device-width,initial-scale=1.0,user-scalable=no'>" +
                "<title>Warehouse Scanner (QR)</title>" +

                // ZXing QR (UMD)
                "<script src='https://cdn.jsdelivr.net/npm/@zxing/browser@0.1.4/umd/index.min.js'></script>" +

                "<style>" +
                "*{box-sizing:border-box;margin:0;padding:0}" +
                "body{font-family:-apple-system,BlinkMacSystemFont,sans-serif;background:#0f172a;color:#e2e8f0;min-height:100vh}"
                +
                "header{background:linear-gradient(135deg,#1e40af,#1d4ed8);padding:14px 16px;display:flex;align-items:center;gap:10px}"
                +
                "header h1{font-size:17px;font-weight:700;flex:1}" +
                ".badge{background:#dbeafe;color:#1e3a8a;border-radius:20px;padding:3px 12px;font-size:12px;font-weight:700}"
                +
                ".container{padding:12px;max-width:520px;margin:0 auto}" +
                "#cam-wrap{position:relative;border-radius:14px;overflow:hidden;border:2px solid #3b82f6;background:#111;margin-bottom:10px}"
                +
                "#qrVideo{width:100%;height:auto;display:block}" +
                "#scan-line{position:absolute;left:8%;right:8%;height:2px;background:linear-gradient(90deg,transparent,#34d399,transparent);top:50%;animation:scan 1.8s ease-in-out infinite;pointer-events:none;z-index:10}"
                +
                "@keyframes scan{0%,100%{top:20%}50%{top:80%}}" +
                "#cam-status{background:#1e293b;color:#94a3b8;font-size:11px;padding:6px 12px;text-align:center;border-radius:0 0 12px 12px}"
                +
                ".card{background:#1e293b;border-radius:12px;padding:14px;margin-top:10px}" +
                ".card-title{font-size:11px;color:#64748b;text-transform:uppercase;letter-spacing:.08em;margin-bottom:10px;font-weight:600}"
                +
                ".row{display:flex;gap:8px}" +
                "input{flex:1;padding:11px 14px;background:#0f172a;border:1.5px solid #334155;border-radius:8px;color:#e2e8f0;font-size:16px;-webkit-appearance:none}"
                +
                "input:focus{outline:none;border-color:#3b82f6}" +
                ".qty-in{max-width:80px;text-align:center;flex:none}" +
                ".btn{padding:11px 18px;border:none;border-radius:8px;font-size:15px;font-weight:700;cursor:pointer;background:#3b82f6;color:#fff}"
                +
                ".btn.danger{background:#ef4444;width:100%}" +
                ".hint{color:#94a3b8;font-size:12px;line-height:1.35}" +
                "table{width:100%;border-collapse:collapse;font-size:13px}" +
                "th{text-align:left;color:#475569;padding:5px 4px;font-size:11px;font-weight:600}" +
                "td{padding:9px 4px;border-bottom:1px solid #263347}" +
                ".qc{text-align:right;font-weight:700;color:#34d399;font-size:15px}" +
                ".sc{color:#94a3b8;font-size:11px}" +
                ".toast{position:fixed;bottom:28px;left:50%;transform:translateX(-50%);background:#10b981;color:#fff;padding:11px 24px;border-radius:28px;font-weight:700;font-size:14px;display:none;white-space:nowrap;box-shadow:0 4px 24px rgba(0,0,0,.5)}"
                +
                ".toast.err{background:#ef4444}" +
                "</style></head><body>" +

                "<header><span style='font-size:20px'>📦</span><h1>Warehouse Scanner</h1><span class='badge' id='cnt'>0 SKU</span></header>"
                +

                "<div class='container'>" +

                "<div id='cam-wrap'>" +
                "<video id='qrVideo' playsinline></video>" +
                "<div id='scan-line'></div>" +
                "</div>" +
                "<div id='cam-status'>Đang khởi động camera QR…</div>" +

                "<div class='card'>" +
                "<div class='card-title'>Quét QR</div>" +
                "<div class='hint'>Đưa mã QR vào khung. Nếu không quét được, kéo xuống nhập mã SKU bằng tay.</div>" +
                "<div class='hint' style='margin-top:8px'>Mã vừa quét: <b id='last'>-</b></div>" +
                "</div>" +

                "<div class='card'>" +
                "<div class='card-title'>Nhập thủ công (fallback)</div>" +
                "<div class='row'>" +
                "<input type='text' id='bc' placeholder='SKU Code (ví dụ: 0001-1012)' autocapitalize='characters' autocomplete='off'/>"
                +
                "<input type='number' id='qty' class='qty-in' value='1' min='0.01' step='0.01'/>" +
                "<button class='btn' id='manualBtn'>Scan</button>" +
                "</div>" +
                "</div>" +

                "<div class='card'>" +
                "<div class='card-title'>Đã scan</div>" +
                "<table><thead><tr><th>SKU</th><th>Tên sản phẩm</th><th style='text-align:right'>Qty</th></tr></thead>"
                +
                "<tbody id='lines'></tbody></table>" +
                "</div>" +

                "<div class='card'><button class='btn danger' id='closeBtn'>🛑 Kết thúc Scan</button></div>" +
                "</div>" +

                "<div class='toast' id='toast'></div>" +

                "<script>\n" +
                "var TOKEN='" + token + "';\n" +
                "var API=window.location.origin+'/api/v1/scan-events';\n" +
                "var lineData={};\n" +
                "var inflight=false;\n" +
                "var lastCode=null;\n" +
                "var lastAt=0;\n" +

                "function base64UrlDecode(str){\n" +
                "  str=(str||'').replace(/-/g,'+').replace(/_/g,'/');\n" +
                "  while(str.length%4) str+='=';\n" +
                "  return atob(str);\n" +
                "}\n" +
                "function getSessionId(){\n" +
                "  try{var p=TOKEN.split('.')[1];var d=JSON.parse(base64UrlDecode(p));return d.sessionId||null;}catch(e){return null;}\n"
                +
                "}\n" +
                "var SESSION_ID=getSessionId();\n" +

                "function toast(msg,err){\n" +
                "  var t=document.getElementById('toast');\n" +
                "  t.textContent=msg;t.className='toast'+(err?' err':'');\n" +
                "  t.style.display='block';\n" +
                "  clearTimeout(t._t);t._t=setTimeout(function(){t.style.display='none';},2600);\n" +
                "}\n" +
                "function setStatus(msg){document.getElementById('cam-status').textContent=msg;}\n" +

                "function updateTable(d){\n" +
                "  lineData[d.skuCode]={name:d.skuName||'',qty:d.newQty};\n" +
                "  var rows='';\n" +
                "  for(var k in lineData){\n" +
                "    rows+='<tr><td class=\"sc\">'+k+'</td><td>'+lineData[k].name+'</td><td class=\"qc\">'+lineData[k].qty+'</td></tr>';\n"
                +
                "  }\n" +
                "  document.getElementById('lines').innerHTML=rows;\n" +
                "  document.getElementById('cnt').textContent=Object.keys(lineData).length+' SKU';\n" +
                "}\n" +

                "function sendBarcode(barcode,qty){\n" +
                "  if(inflight) return;\n" +
                "  inflight=true;\n" +
                "  setStatus('Đang gửi: '+barcode);\n" +
                "  fetch(API,{\n" +
                "    method:'POST',\n" +
                "    headers:{'Content-Type':'application/json','Authorization':'Bearer '+TOKEN},\n" +
                "    body:JSON.stringify({barcode:barcode,qty:qty})\n" +
                "  }).then(function(r){\n" +
                "    return r.text().then(function(txt){\n" +
                "      try{return JSON.parse(txt);}catch(e){return {success:false,message:'HTTP '+r.status+': '+txt.substring(0,140)};}\n"
                +
                "    });\n" +
                "  }).then(function(d){\n" +
                "    if(d && d.success){\n" +
                "      toast('✓ '+d.data.skuCode+' — qty:'+d.data.newQty);\n" +
                "      updateTable(d.data);\n" +
                "      document.getElementById('bc').value='';\n" +
                "      if(navigator.vibrate) navigator.vibrate(60);\n" +
                "    }else{\n" +
                "      toast((d && d.message) ? d.message : 'Lỗi không xác định', true);\n" +
                "    }\n" +
                "    setStatus('Camera sẵn sàng (QR) — đưa QR vào khung');\n" +
                "  }).catch(function(e){\n" +
                "    toast('Mất kết nối',true);\n" +
                "    setStatus('Lỗi mạng: '+e);\n" +
                "  }).finally(function(){\n" +
                "    setTimeout(function(){inflight=false;},700);\n" +
                "  });\n" +
                "}\n" +

                "function submitManual(){\n" +
                "  var b=(document.getElementById('bc').value||'').trim().toUpperCase();\n" +
                "  var q=parseFloat(document.getElementById('qty').value)||1;\n" +
                "  if(!b){toast('Nhập mã SKU!',true);return;}\n" +
                "  document.getElementById('last').textContent=b;\n" +
                "  sendBarcode(b,q);\n" +
                "}\n" +

                "function closeScan(){\n" +
                "  if(!SESSION_ID){toast('Không tìm thấy session',true);return;}\n" +
                "  if(!confirm('Kết thúc phiên scan?')) return;\n" +
                "  fetch(window.location.origin+'/api/v1/receiving-sessions/'+SESSION_ID,{\n" +
                "    method:'DELETE',\n" +
                "    headers:{'Authorization':'Bearer '+TOKEN}\n" +
                "  }).then(function(r){return r.text().then(function(t){try{return JSON.parse(t);}catch(e){return {success:false,message:t};}});})\n"
                +
                "  .then(function(d){\n" +
                "    if(d && d.success){\n" +
                "      toast('Phiên scan đã đóng');\n" +
                "      try{stopQr();}catch(e){}\n" +
                "      var btn=document.getElementById('closeBtn');\n" +
                "      btn.disabled=true;btn.textContent='Đã đóng';\n" +
                "      setStatus('Phiên scan đã kết thúc');\n" +
                "    }else{\n" +
                "      toast((d && d.message) ? d.message : 'Lỗi đóng session', true);\n" +
                "    }\n" +
                "  }).catch(function(e){toast('Lỗi kết nối: '+e,true);});\n" +
                "}\n" +

                // --- QR scanner (ZXing) ---
                "var qrReader=null;\n" +
                "var qrActive=false;\n" +
                "async function startQr(){\n" +
                "  try{\n" +
                "    var videoEl=document.getElementById('qrVideo');\n" +
                "    qrReader=new ZXingBrowser.BrowserQRCodeReader();\n" +
                "    qrActive=true;\n" +
                "    setStatus('Camera sẵn sàng (QR) — đưa QR vào khung');\n" +
                "    await qrReader.decodeFromVideoDevice(null, videoEl, function(result, err){\n" +
                "      if(!qrActive) return;\n" +
                "      if(result){\n" +
                "        var code=result.getText().trim().toUpperCase();\n" +
                "        if(code.length<2) return;\n" +
                "        var now=Date.now();\n" +
                "        if(code===lastCode && (now-lastAt)<1600) return;\n" +
                "        lastCode=code; lastAt=now;\n" +
                "        document.getElementById('last').textContent=code;\n" +
                "        sendBarcode(code,1);\n" +
                "      }\n" +
                "    });\n" +
                "  }catch(e){\n" +
                "    toast('Không mở được camera QR', true);\n" +
                "    setStatus('Camera lỗi: '+e);\n" +
                "  }\n" +
                "}\n" +
                "function stopQr(){\n" +
                "  qrActive=false;\n" +
                "  try{ if(qrReader) qrReader.reset(); }catch(e){}\n" +
                "}\n" +

                "document.addEventListener('DOMContentLoaded',function(){\n" +
                "  document.getElementById('manualBtn').addEventListener('click', submitManual);\n" +
                "  document.getElementById('closeBtn').addEventListener('click', closeScan);\n" +
                "  document.getElementById('bc').addEventListener('keydown', function(e){ if(e.key==='Enter') submitManual(); });\n"
                +
                "});\n" +
                "window.addEventListener('load', startQr);\n" +

                "</script></body></html>";
    }
}
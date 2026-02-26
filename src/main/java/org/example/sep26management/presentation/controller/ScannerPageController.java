package org.example.sep26management.presentation.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ScannerPageController {

    @GetMapping(value = "/v1/scan/url", produces = MediaType.TEXT_PLAIN_VALUE)
    public String getScanUrl(@RequestParam("token") String token, HttpServletRequest request) {
        String base = request.getScheme() + "://" + request.getServerName()
                + (request.getServerPort() == 80 || request.getServerPort() == 443 ? ""
                        : ":" + request.getServerPort());
        return base + "/api/v1/scan?token=" + token + "&v=qr3";
    }

    @GetMapping(value = "/v1/scan", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> scannerPage(@RequestParam("token") String token) {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
                .header("Pragma", "no-cache")
                .body(buildHtml(escapeForJs(token)));
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

                // html5-qrcode (QR cực ổn định trên iOS Safari)
                "<script src='https://unpkg.com/html5-qrcode@2.3.10/html5-qrcode.min.js'></script>" +

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

                "#cam-wrap{position:relative;border-radius:14px;overflow:hidden;border:2px solid #3b82f6;background:#111;margin-bottom:8px}"
                +
                "#reader{width:100%}" + /* html5-qrcode render vào div này */
                "#scan-line{position:absolute;left:8%;right:8%;height:2px;background:linear-gradient(90deg,transparent,#34d399,transparent);top:50%;animation:scan 1.8s ease-in-out infinite;pointer-events:none;z-index:10}"
                +
                "@keyframes scan{0%,100%{top:20%}50%{top:80%}}" +
                "#cam-status{background:#1e293b;color:#94a3b8;font-size:11px;padding:6px 12px;text-align:center;border-radius:0 0 12px 12px}"
                +

                ".card{background:#1e293b;border-radius:12px;padding:14px;margin-top:10px}" +
                ".card-title{font-size:11px;color:#64748b;text-transform:uppercase;letter-spacing:.08em;margin-bottom:10px;font-weight:600}"
                +
                ".hint{color:#94a3b8;font-size:12px;line-height:1.35}" +

                ".row{display:flex;gap:8px}" +
                "input{flex:1;padding:11px 14px;background:#0f172a;border:1.5px solid #334155;border-radius:8px;color:#e2e8f0;font-size:16px;-webkit-appearance:none}"
                +
                "input:focus{outline:none;border-color:#3b82f6}" +
                ".qty-in{max-width:90px;text-align:center;flex:none}" +

                ".btn{padding:12px 18px;border:none;border-radius:10px;font-size:16px;font-weight:800;cursor:pointer;background:#3b82f6;color:#fff}"
                +
                ".btn.full{width:100%}" +
                ".btn.danger{background:#ef4444;width:100%}" +

                "table{width:100%;border-collapse:collapse;font-size:13px}" +
                "th{text-align:left;color:#475569;padding:5px 4px;font-size:11px;font-weight:600}" +
                "td{padding:9px 4px;border-bottom:1px solid #263347}" +
                ".qc{text-align:right;font-weight:800;color:#34d399;font-size:15px}" +
                ".sc{color:#94a3b8;font-size:11px}" +

                ".toast{position:fixed;bottom:28px;left:50%;transform:translateX(-50%);background:#10b981;color:#fff;padding:11px 24px;border-radius:28px;font-weight:800;font-size:14px;display:none;white-space:nowrap;box-shadow:0 4px 24px rgba(0,0,0,.5)}"
                +
                ".toast.err{background:#ef4444}" +
                "</style></head><body>" +

                "<header><span style='font-size:20px'>📦</span><h1>Warehouse Scanner</h1><span class='badge' id='cnt'>0 SKU</span></header>"
                +

                "<div class='container'>" +
                "<div id='cam-wrap'><div id='reader'></div><div id='scan-line'></div></div>" +
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
                "</div>" +
                // nút Scan xuống dưới (full width)
                "<button class='btn full' id='manualBtn' style='margin-top:10px'>Scan</button>" +
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

                "function base64UrlDecode(str){str=(str||'').replace(/-/g,'+').replace(/_/g,'/');while(str.length%4)str+='=';return atob(str);} \n"
                +
                "function getSessionId(){try{var p=TOKEN.split('.')[1];var d=JSON.parse(base64UrlDecode(p));return d.sessionId||null;}catch(e){return null;}} \n"
                +
                "var SESSION_ID=getSessionId();\n" +

                "function toast(msg,err){var t=document.getElementById('toast');t.textContent=msg;t.className='toast'+(err?' err':'');t.style.display='block';clearTimeout(t._t);t._t=setTimeout(function(){t.style.display='none';},2600);} \n"
                +
                "function setStatus(msg){document.getElementById('cam-status').textContent=msg;} \n" +

                "function updateTable(d){\n" +
                "  lineData[d.skuCode]={name:d.skuName||'',qty:d.newQty};\n" +
                "  var rows='';\n" +
                "  for(var k in lineData){rows+='<tr><td class=\"sc\">'+k+'</td><td>'+lineData[k].name+'</td><td class=\"qc\">'+lineData[k].qty+'</td></tr>';}\n"
                +
                "  document.getElementById('lines').innerHTML=rows;\n" +
                "  document.getElementById('cnt').textContent=Object.keys(lineData).length+' SKU';\n" +
                "} \n" +

                "function sendBarcode(barcode,qty){\n" +
                "  if(inflight) return;\n" +
                "  inflight=true;\n" +
                "  setStatus('Đang gửi: '+barcode);\n" +
                "  fetch(API,{method:'POST',headers:{'Content-Type':'application/json','Authorization':'Bearer '+TOKEN},body:JSON.stringify({barcode:barcode,qty:qty})})\n"
                +
                "  .then(function(r){return r.text().then(function(txt){try{return JSON.parse(txt);}catch(e){return {success:false,message:'HTTP '+r.status+': '+txt.substring(0,140)};}});})\n"
                +
                "  .then(function(d){\n" +
                "    if(d && d.success){toast('✓ '+d.data.skuCode+' — qty:'+d.data.newQty);updateTable(d.data);document.getElementById('bc').value='';if(navigator.vibrate)navigator.vibrate(60);} \n"
                +
                "    else{toast((d&&d.message)?d.message:'Lỗi không xác định',true);} \n" +
                "    setStatus('Camera sẵn sàng (QR) — đưa QR vào khung');\n" +
                "  })\n" +
                "  .catch(function(e){toast('Mất kết nối',true);setStatus('Lỗi mạng: '+e);})\n" +
                "  .finally(function(){setTimeout(function(){inflight=false;},600);});\n" +
                "} \n" +

                "function submitManual(){\n" +
                "  var b=(document.getElementById('bc').value||'').trim().toUpperCase();\n" +
                "  var q=parseFloat(document.getElementById('qty').value)||1;\n" +
                "  if(!b){toast('Nhập mã SKU!',true);return;}\n" +
                "  document.getElementById('last').textContent=b;\n" +
                "  sendBarcode(b,q);\n" +
                "} \n" +

                "function closeScan(){\n" +
                "  if(!SESSION_ID){toast('Không tìm thấy session',true);return;}\n" +
                "  if(!confirm('Kết thúc phiên scan?')) return;\n" +
                "  fetch(window.location.origin+'/api/v1/receiving-sessions/'+SESSION_ID,{method:'DELETE',headers:{'Authorization':'Bearer '+TOKEN}})\n"
                +
                "   .then(function(r){return r.text().then(function(t){try{return JSON.parse(t);}catch(e){return {success:false,message:t};}});})\n"
                +
                "   .then(function(d){\n" +
                "     if(d && d.success){toast('Phiên scan đã đóng');stopQr();var btn=document.getElementById('closeBtn');btn.disabled=true;btn.textContent='Đã đóng';setStatus('Phiên scan đã kết thúc');}\n"
                +
                "     else{toast((d&&d.message)?d.message:'Lỗi đóng session',true);} \n" +
                "   }).catch(function(e){toast('Lỗi kết nối: '+e,true);});\n" +
                "} \n" +

                // QR start/stop (html5-qrcode)
                "var qr=null;\n" +
                "var qrRunning=false;\n" +
                "function stopQr(){\n" +
                "  try{ if(qr && qrRunning){ qr.stop().then(function(){qrRunning=false;}).catch(function(){});} }catch(e){}\n"
                +
                "} \n" +
                "function startQr(){\n" +
                "  // Check HTTPS - camera requires secure context\n" +
                "  if (window.location.protocol !== 'https:' && window.location.hostname !== 'localhost' && window.location.hostname !== '127.0.0.1') {\n" +
                "    toast('Cần HTTPS để quét QR!', true);\n" +
                "    setStatus('Lỗi: Cần HTTPS để truy cập camera');\n" +
                "    return;\n" +
                "  }\n" +
                "  try{\n" +
                "    // Use Html5QrcodeScanner for better permission handling, or fallback to Html5Qrcode\n" +
                "    Html5Qrcode.getCameras().then(function(cameras){\n" +
                "      if(cameras && cameras.length){\n" +
                "        var cameraId = cameras.find(function(c){return c.label.toLowerCase().includes('back') || c.label.toLowerCase().includes('environment');}) || cameras[0].id;\n" +
                "        qr = new Html5Qrcode('reader');\n" +
                "        var config = { fps: 10, qrbox: { width: 250, height: 250 }, videoConstraints: { facingMode: 'environment', width: { ideal: 1280 }, height: { ideal: 720 } } };\n" +
                "        qr.start(cameraId, config, function(decodedText){\n" +
                "          var code=(decodedText||'').trim().toUpperCase();\n" +
                "          if(code.length<2) return;\n" +
                "          var now=Date.now();\n" +
                "          if(code===lastCode && (now-lastAt)<1500) return;\n" +
                "          lastCode=code; lastAt=now;\n" +
                "          document.getElementById('last').textContent=code;\n" +
                "          sendBarcode(code,1);\n" +
                "        }, function(err){}).then(function(){\n" +
                "          qrRunning=true;\n" +
                "          setStatus('Camera sẵn sàng (QR) — đưa QR vào khung');\n" +
                "        }).catch(function(e){\n" +
                "          toast('Không mở được camera: ' + e, true);\n" +
                "          setStatus('Camera lỗi: '+e);\n" +
                "        });\n" +
                "      }else{\n" +
                "        toast('Không tìm thấy camera!', true);\n" +
                "        setStatus('Lỗi: Không tìm thấy camera trên thiết bị');\n" +
                "      }\n" +
                "    }).catch(function(e){\n" +
                "      toast('Không truy cập được camera: Quyền bị từ chối hoặc không có camera', true);\n" +
                "      setStatus('Lỗi quyền camera: ' + e + '. Vui lòng cho phép truy cập camera trong cài đặt trình duyệt.');\n" +
                "    });\n" +
                "  }catch(e){toast('Không khởi tạo được QR: '+e,true);setStatus('Lỗi: '+e);} \n" +
                "} \n" +

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
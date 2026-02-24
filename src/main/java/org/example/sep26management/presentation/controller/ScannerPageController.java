package org.example.sep26management.presentation.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ScannerPageController {

    @GetMapping(value = "/v1/scan/url", produces = MediaType.TEXT_PLAIN_VALUE)
    public String getScanUrl(@RequestParam("token") String token,
            jakarta.servlet.http.HttpServletRequest request) {
        String base = request.getScheme() + "://" + request.getServerName()
                + (request.getServerPort() == 80 || request.getServerPort() == 443 ? ""
                        : ":" + request.getServerPort());
        return base + "/api/v1/scan?token=" + token;
    }

    @GetMapping(value = "/v1/scan", produces = MediaType.TEXT_HTML_VALUE)
    public String scannerPage(@RequestParam("token") String token) {
        return buildHtml(token.replace("'", "\\'"));
    }

    private String buildHtml(String token) {
        return "<!DOCTYPE html>" +
                "<html lang='vi'><head>" +
                "<meta charset='UTF-8'>" +
                "<meta name='viewport' content='width=device-width,initial-scale=1.0,user-scalable=no'>" +
                "<title>Scanner</title>" +
                "<script src='https://unpkg.com/@zxing/library@0.21.3/umd/index.min.js'></script>" +
                "<style>" +
                "*{box-sizing:border-box;margin:0;padding:0}" +
                "body{font-family:-apple-system,BlinkMacSystemFont,sans-serif;background:#0f172a;color:#e2e8f0;min-height:100vh}"
                +
                "header{background:linear-gradient(135deg,#1e40af,#1d4ed8);padding:14px 16px;display:flex;align-items:center;gap:10px}"
                +
                "header h1{font-size:17px;font-weight:700;flex:1}" +
                ".badge{background:#dbeafe;color:#1e3a8a;border-radius:20px;padding:3px 12px;font-size:12px;font-weight:700}"
                +
                ".container{padding:12px;max-width:500px;margin:0 auto}" +
                "#cam-wrap{position:relative;border-radius:14px;overflow:hidden;border:2px solid #3b82f6;background:#000;margin-bottom:12px}"
                +
                "#video{width:100%;display:block;max-height:52vw;object-fit:cover}" +
                "#scan-line{position:absolute;left:8%;right:8%;height:2px;background:linear-gradient(90deg,transparent,#34d399,transparent);top:50%;animation:scan 1.8s ease-in-out infinite}"
                +
                "@keyframes scan{0%,100%{top:20%}50%{top:80%}}" +
                "#cam-status{position:absolute;bottom:0;left:0;right:0;background:rgba(0,0,0,.65);color:#94a3b8;font-size:11px;padding:5px 10px;text-align:center}"
                +
                ".card{background:#1e293b;border-radius:12px;padding:14px;margin-bottom:10px}" +
                ".card-title{font-size:11px;color:#64748b;text-transform:uppercase;letter-spacing:.08em;margin-bottom:10px;font-weight:600}"
                +
                ".row{display:flex;gap:8px}" +
                "input{flex:1;padding:11px 14px;background:#0f172a;border:1.5px solid #334155;border-radius:8px;color:#e2e8f0;font-size:16px;-webkit-appearance:none}"
                +
                "input:focus{outline:none;border-color:#3b82f6}" +
                ".qty-in{max-width:72px;text-align:center;flex:none}" +
                ".btn{padding:11px 18px;border:none;border-radius:8px;font-size:15px;font-weight:700;cursor:pointer;background:#3b82f6;color:#fff}"
                +
                "table{width:100%;border-collapse:collapse;font-size:13px}" +
                "th{text-align:left;color:#475569;padding:5px 4px;font-size:11px;font-weight:600}" +
                "td{padding:9px 4px;border-bottom:1px solid #1e293b}" +
                ".qc{text-align:right;font-weight:700;color:#34d399;font-size:15px}" +
                ".sc{color:#94a3b8;font-size:11px}" +
                ".toast{position:fixed;bottom:28px;left:50%;transform:translateX(-50%);background:#10b981;color:#fff;padding:11px 24px;border-radius:28px;font-weight:700;font-size:14px;display:none;white-space:nowrap;box-shadow:0 4px 24px rgba(0,0,0,.5)}"
                +
                ".toast.err{background:#ef4444}" +
                "</style></head><body>" +
                "<header>" +
                "<span style='font-size:20px'>📦</span>" +
                "<h1>Warehouse Scanner</h1>" +
                "<span class='badge' id='cnt'>0 SKU</span>" +
                "</header>" +
                "<div class='container'>" +
                "<div id='cam-wrap'>" +
                "<video id='video' autoplay muted playsinline></video>" +
                "<div id='scan-line'></div>" +
                "<div id='cam-status'>Đang khởi động camera…</div>" +
                "</div>" +
                "<div class='card'>" +
                "<div class='card-title'>Nhập thủ công (hoặc máy scan Bluetooth)</div>" +
                "<div class='row'>" +
                "<input type='text' id='bc' placeholder='Barcode / SKU Code' autocapitalize='characters' autocomplete='off'/>"
                +
                "<input type='number' id='qty' class='qty-in' value='1' min='0.01' step='0.01'/>" +
                "<button class='btn' onclick='submitManual()'>Scan</button>" +
                "</div>" +
                "</div>" +
                "<div class='card'>" +
                "<div class='card-title'>Đã scan</div>" +
                "<table><thead><tr><th>SKU</th><th>Tên sản phẩm</th><th style='text-align:right'>Qty</th></tr></thead>"
                +
                "<tbody id='lines'></tbody></table>" +
                "</div></div>" +
                "<div class='toast' id='toast'></div>" +
                "<script>\n" +
                "var TOKEN='" + token + "';\n" +
                "var API=window.location.origin+'/api/v1/scan-events';\n" +
                "var scanning=false;\n" +
                "var lineData={};\n" +

                "function toast(msg,err){\n" +
                "  var t=document.getElementById('toast');\n" +
                "  t.textContent=msg;t.className='toast'+(err?' err':'');\n" +
                "  t.style.display='block';\n" +
                "  clearTimeout(t._t);t._t=setTimeout(function(){t.style.display='none';},2800);\n" +
                "}\n" +
                "function setStatus(msg){document.getElementById('cam-status').textContent=msg;}\n" +
                "function updateTable(d){\n" +
                "  lineData[d.skuCode]={name:d.skuName||'',qty:d.newQty};\n" +
                "  var rows='';\n" +
                "  for(var k in lineData)rows+='<tr><td class=\"sc\">'+k+'</td><td>'+lineData[k].name+'</td><td class=\"qc\">'+lineData[k].qty+'</td></tr>';\n"
                +
                "  document.getElementById('lines').innerHTML=rows;\n" +
                "  document.getElementById('cnt').textContent=Object.keys(lineData).length+' SKU';\n" +
                "}\n" +

                // Safe fetch — handles non-JSON responses gracefully
                "function sendBarcode(barcode,qty){\n" +
                "  if(scanning)return;scanning=true;\n" +
                "  setStatus('Đang gửi: '+barcode);\n" +
                "  fetch(API,{\n" +
                "    method:'POST',\n" +
                "    headers:{'Content-Type':'application/json','Authorization':'Bearer '+TOKEN},\n" +
                "    body:JSON.stringify({barcode:barcode,qty:qty})\n" +
                "  }).then(function(r){\n" +
                "    return r.text().then(function(txt){\n" +
                "      try{return JSON.parse(txt);}\n" +
                "      catch(e){return {success:false,message:'HTTP '+r.status+' — '+txt.substring(0,120)};}\n" +
                "    });\n" +
                "  }).then(function(d){\n" +
                "    if(d.success){\n" +
                "      toast('✓ '+d.data.skuCode+' — qty:'+d.data.newQty);\n" +
                "      updateTable(d.data);\n" +
                "      document.getElementById('bc').value='';\n" +
                "      if(navigator.vibrate)navigator.vibrate(60);\n" +
                "    }else{\n" +
                "      toast(d.message||'Lỗi không xác định',true);\n" +
                "    }\n" +
                "    setStatus('Camera sẵn sàng — hướng vào barcode');\n" +
                "  }).catch(function(e){\n" +
                "    toast('Mất kết nối: '+e,true);\n" +
                "    setStatus('Lỗi mạng');\n" +
                "  }).finally(function(){\n" +
                "    setTimeout(function(){scanning=false;},1200);\n" +
                "  });\n" +
                "}\n" +

                "function submitManual(){\n" +
                "  var b=document.getElementById('bc').value.trim().toUpperCase();\n" +
                "  var q=parseFloat(document.getElementById('qty').value)||1;\n" +
                "  if(!b){toast('Nhập barcode!',true);return;}\n" +
                "  sendBarcode(b,q);\n" +
                "}\n" +
                "document.addEventListener('DOMContentLoaded',function(){\n" +
                "  document.getElementById('bc').addEventListener('keydown',function(e){if(e.key==='Enter')submitManual();});\n"
                +
                "});\n" +

                // Camera: ZXing-only, use decodeFromStream to avoid conflict with existing
                // stream
                "window.addEventListener('load',function(){\n" +
                "  navigator.mediaDevices.getUserMedia({video:{facingMode:'environment',width:{ideal:1280},height:{ideal:720}}})\n"
                +
                "  .then(function(stream){\n" +
                "    var vid=document.getElementById('video');\n" +
                "    vid.srcObject=stream;\n" +
                "    return vid.play().then(function(){return stream;});\n" +
                "  }).then(function(stream){\n" +
                "    startZxing(stream);\n" +
                "  }).catch(function(e){\n" +
                "    setStatus('Camera không khả dụng ('+e.message+') — dùng nhập tay');\n" +
                "  });\n" +
                "});\n" +

                // ZXing: use decodeFromStream (passes existing stream, no new getUserMedia)
                "function startZxing(stream){\n" +
                "  if(typeof ZXing==='undefined'){setStatus('ZXing không tải được — dùng nhập tay');return;}\n" +
                "  var hints=new Map();\n" +
                "  hints.set(ZXing.DecodeHintType.POSSIBLE_FORMATS,[ZXing.BarcodeFormat.CODE_39,ZXing.BarcodeFormat.CODE_128]);\n"
                +
                "  hints.set(ZXing.DecodeHintType.TRY_HARDER,true);\n" +
                "  var reader=new ZXing.BrowserMultiFormatReader(hints,600);\n" +
                "  var vid=document.getElementById('video');\n" +
                "  // decodeFromStream reuses existing stream — no camera conflict\n" +
                "  reader.decodeFromStream(stream,vid,function(result,err){\n" +
                "    if(result&&!scanning){\n" +
                "      var raw=result.getText().trim().toUpperCase();\n" +
                "      if(raw.length>=2)sendBarcode(raw,1);\n" +
                "    }\n" +
                "    if(err&&typeof ZXing!=='undefined'&&!(err instanceof ZXing.NotFoundException)){\n" +
                "      setStatus('Lỗi ZXing: '+err.message);\n" +
                "    }\n" +
                "  });\n" +
                "  setStatus('Camera sẵn sàng (Code 39/128) — hướng vào barcode');\n" +
                "}\n" +
                "</script></body></html>";
    }
}

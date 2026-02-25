package org.example.sep26management.presentation.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Scanner page using Quagga2 — specialized for linear barcodes (Code 39, Code
 * 128).
 * Much better than ZXing for 1D barcodes on iOS Safari.
 */
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
                // Quagga2 — specialized for linear/1D barcodes, much better than ZXing for Code
                // 39/128 on mobile
                "<script src='https://cdn.jsdelivr.net/npm/@ericblade/quagga2@1.2.6/dist/quagga.min.js'></script>" +
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
                "#cam-wrap{position:relative;border-radius:14px;overflow:hidden;border:2px solid #3b82f6;background:#111;margin-bottom:12px;min-height:180px}"
                +
                // Quagga renders into a viewport div
                "#interactive{width:100%;min-height:180px;position:relative}" +
                "#interactive video,#interactive canvas{width:100%;height:auto;display:block;border-radius:12px}" +
                "#interactive canvas.drawingBuffer{position:absolute;top:0;left:0}" +
                "#scan-line{position:absolute;left:8%;right:8%;height:2px;background:linear-gradient(90deg,transparent,#34d399,transparent);top:50%;animation:scan 1.8s ease-in-out infinite;pointer-events:none;z-index:10}"
                +
                "@keyframes scan{0%,100%{top:20%}50%{top:80%}}" +
                "#cam-status{background:#1e293b;color:#94a3b8;font-size:11px;padding:6px 12px;text-align:center;border-radius:0 0 12px 12px}"
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
                "td{padding:9px 4px;border-bottom:1px solid #263347}" +
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
                "<div id='interactive'></div>" +
                "<div id='scan-line'></div>" +
                "</div>" +
                "<div id='cam-status'>Đang khởi động camera…</div>" +
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
                "var lastCode=null;\n" +
                "var debounceTimer=null;\n" +

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
                "      catch(e){return {success:false,message:'HTTP '+r.status+': '+txt.substring(0,120)};}\n" +
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
                "    toast('Mất kết nối',true);\n" +
                "    setStatus('Lỗi mạng: '+e);\n" +
                "  }).finally(function(){\n" +
                "    setTimeout(function(){scanning=false;},1500);\n" +
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

                // Quagga2 initialization — specialized for Code 39 / Code 128
                "window.addEventListener('load',function(){\n" +
                "  Quagga.init({\n" +
                "    inputStream:{\n" +
                "      name:'Live',\n" +
                "      type:'LiveStream',\n" +
                "      target:document.getElementById('interactive'),\n" +
                "      constraints:{\n" +
                "        facingMode:'environment',\n" +
                "        width:{ideal:1280},\n" +
                "        height:{ideal:720}\n" +
                "      }\n" +
                "    },\n" +
                "    locator:{patchSize:'medium',halfSample:true},\n" +
                "    numOfWorkers:0,\n" + // 0 = run in main thread (required for iOS Safari)
                "    frequency:10,\n" + // scan 10 times per second
                "    decoder:{\n" +
                "      readers:['code_39_reader','code_128_reader']\n" +
                "    },\n" +
                "    locate:true\n" +
                "  },function(err){\n" +
                "    if(err){\n" +
                "      setStatus('Camera lỗi: '+err.message+' — dùng nhập tay');\n" +
                "      toast('Không mở được camera',true);\n" +
                "      return;\n" +
                "    }\n" +
                "    Quagga.start();\n" +
                "    setStatus('Camera sẵn sàng (Quagga2 · Code 39/128) — hướng vào barcode');\n" +
                "  });\n" +

                // Quagga detected event — fired when barcode is successfully decoded
                "  Quagga.onDetected(function(data){\n" +
                "    var code=data.codeResult.code.trim().toUpperCase();\n" +
                "    if(code.length<2)return;\n" +
                // Debounce: same code within 2s → ignore
                "    if(code===lastCode)return;\n" +
                "    lastCode=code;\n" +
                "    clearTimeout(debounceTimer);\n" +
                "    debounceTimer=setTimeout(function(){lastCode=null;},2000);\n" +
                "    sendBarcode(code,1);\n" +
                "  });\n" +
                "});\n" +
                "</script></body></html>";
    }
}

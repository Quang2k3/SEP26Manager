package org.example.sep26management.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Scanner Page", description = "Trang quét barcode cho iPhone. Trả về URL hoặc trang HTML có camera QR để quét sản phẩm.")
public class ScannerPageController {

    @GetMapping(value = "/v1/scan/url", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Lấy URL trang scan", description = "Trả về URL đầy đủ để mở trang quét barcode trên iPhone. Dùng cho QR code.")
    public String getScanUrl(@RequestParam("token") String token, HttpServletRequest request) {
        String base = request.getScheme() + "://" + request.getServerName()
                + (request.getServerPort() == 80 || request.getServerPort() == 443 ? ""
                        : ":" + request.getServerPort());
        return base + "/v1/scan?token=" + token + "&v=qr3";
    }

    @GetMapping(value = "/v1/scan", produces = MediaType.TEXT_HTML_VALUE)
    @Operation(summary = "Trang quét barcode (HTML)", description = "Trả về trang HTML với camera QR scanner. iPhone mở trang này để quét barcode và gửi scan event.")
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

                // html5-qrcode - load local (must include context-path /api)
                "<script src='/js/html5-qrcode.min.js'></script>" +

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
                "<div id='cam-wrap'>" +
                "<div id='reader'></div><div id='scan-line'></div>" +
                "<div id='cam-start-overlay' style='position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;background:rgba(15,23,42,0.95);z-index:20;border-radius:14px'>"
                +
                "<div style='font-size:52px;margin-bottom:14px'>📷</div>" +
                "<button id='startCamBtn' style='background:linear-gradient(135deg,#2563eb,#3b82f6);color:#fff;border:none;border-radius:14px;padding:16px 42px;font-size:19px;font-weight:800;cursor:pointer;box-shadow:0 4px 20px rgba(59,130,246,.5)'>▶ Bắt đầu quét</button>"
                +
                "<div style='color:#64748b;font-size:12px;margin-top:14px;text-align:center'>Nhấn vào đây để bật Camera &amp; iOS sẽ hỏi xin quyền</div>"
                +
                "</div>" +
                "</div>" +
                "<div id='cam-status'>Chưa bật camera — Nhấn nút bên trên</div>" +

                "<div class='card'>" +
                "<div class='card-title'>Quét QR</div>" +
                "<div class='hint'>Đưa mã QR vào khung. Nếu không quét được, kéo xuống nhập mã SKU bằng tay.</div>" +
                "<div class='hint' style='margin-top:8px'>Mã vừa quét: <b id='last'>-</b></div>" +
                "</div>" +

                "<div class='card'>" +
                "<div class='card-title'>Phân loại & Nhập thủ công</div>" +
                "<div class='row' style='margin-bottom:8px'>" +
                "<select id='cond' style='flex:1;padding:11px;background:#0f172a;border:1.5px solid #334155;border-radius:8px;color:#e2e8f0;font-size:15px' onchange='document.getElementById(\"reasonWrap\").style.display=(this.value===\"FAIL\"?\"flex\":\"none\")'>"
                +
                "<option value='PASS'>✅ HÀNG TỐT (PASS)</option>" +
                "<option value='FAIL'>❌ HÀNG LỖI (FAIL)</option>" +
                "</select>" +
                "</div>" +
                "<div class='row' id='reasonWrap' style='margin-bottom:8px;display:none'>" +
                "<select id='reason' style='flex:1;padding:11px;background:#0f172a;border:1.5px dotted #ef4444;border-radius:8px;color:#ef4444;font-size:14px'>"
                +
                "<option value=''>-- Chọn lỗi cơ bản --</option>" +
                "<option value='DENTED'>Móp méo vỏ (DENTED)</option>" +
                "<option value='TEAR'>Rách bao bì (TEAR)</option>" +
                "<option value='LEAK'>Chảy/Rỉ nước (LEAK)</option>" +
                "<option value='DIRTY'>Bẩn/Bụi (DIRTY)</option>" +
                "<option value='OTHER'>Khác (OTHER)</option>" +
                "</select>" +
                "</div>" +
                "<div class='row'>" +
                "<input type='text' id='bc' placeholder='SKU Code (ví dụ: 0001-1012)' autocapitalize='characters' autocomplete='off'/>"
                +
                "<input type='number' id='qty' class='qty-in' value='1' min='0.01' step='0.01'/>" +
                "</div>" +
                // nút Scan xuống dưới (full width)
                "<button class='btn full' id='manualBtn' style='margin-top:10px'>Scan thủ công</button>" +
                "</div>" +

                "<div class='card'>" +
                "<div class='card-title'>Đã scan</div>" +
                "<table><thead><tr><th>SKU</th><th>Tên sản phẩm</th><th style='text-align:right'>Qty</th><th style='width:35px'></th></tr></thead>"
                +
                "<tbody id='lines'></tbody></table>" +
                "</div>" +

                "<div class='card'>" +
                "<div class='card-title'>Chốt & Tạo Phiếu Kho</div>" +
                "<div class='row' style='margin-bottom:8px'>" +
                "<select id='sourceType' style='flex:1;padding:11px;background:#0f172a;border:1.5px solid #334155;border-radius:8px;color:#e2e8f0;font-size:15px'>"
                +
                "<option value='SUPPLIER'>Nhập từ NCC (SUPPLIER)</option>" +
                "<option value='TRANSFER'>Chuyển Kho (TRANSFER)</option>" +
                "<option value='RETURN'>Khách Hàng Trả (RETURN)</option>" +
                "</select>" +
                "</div>" +
                "<div class='row' style='margin-bottom:8px'>" +
                "<input type='text' id='supplierCode' placeholder='Mã NCC (vd: SUP-001)' autocapitalize='characters' autocomplete='off'/>"
                +
                "</div>" +
                "<div class='row' style='margin-bottom:8px'>" +
                "<input type='text' id='sourceRef' placeholder='Mã chứng từ / PO' autocapitalize='characters' autocomplete='off'/>"
                +
                "</div>" +
                "<div class='row' style='margin-bottom:8px'>" +
                "<input type='text' id='note' placeholder='Ghi chú' autocomplete='off'/>" +
                "</div>" +
                "<button class='btn full' id='createGrnBtn' style='background:#10b981;margin-top:5px'>✅ Tạo Phiếu GRN</button>"
                +
                "</div>" +

                "<div class='card'><button class='btn danger' id='closeBtn'>⏸ Tạm dừng / Đóng Camera</button></div>" +
                "</div>" +

                "<div class='toast' id='toast'></div>" +

                "<script>\n" +
                "var TOKEN='" + token + "';\n" +
                "var API=window.location.origin+'/v1/scan-events';\n" +
                "var lineData={};\n" +
                "var inflight=false;\n" +
                "var lastCode=null;\n" +
                "var lastAt=0;\n" +
                "var html5QrcodeLoaded = typeof Html5Qrcode !== 'undefined';\n" +

                "function waitForHtml5Qrcode(callback, retries) {\n" +
                "  if (typeof Html5Qrcode !== 'undefined') {\n" +
                "    html5QrcodeLoaded = true;\n" +
                "    callback();\n" +
                "    return;\n" +
                "  }\n" +
                "  if (retries <= 0) {\n" +
                "    document.getElementById('cam-status').textContent = 'Lỗi: Không tải được thư viện QR';\n" +
                "    return;\n" +
                "  }\n" +
                "  setTimeout(function() { waitForHtml5Qrcode(callback, retries - 1); }, 200);\n" +
                "}\n" +

                "function base64UrlDecode(str){str=(str||'').replace(/-/g,'+').replace(/_/g,'/');while(str.length%4)str+='=';return atob(str);} \n"
                +
                "function getSessionId(){try{var p=TOKEN.split('.')[1];var d=JSON.parse(base64UrlDecode(p));return d.sessionId||null;}catch(e){return null;}} \n"
                +
                "var SESSION_ID=getSessionId();\n" +

                "function toast(msg,err){var t=document.getElementById('toast');t.textContent=msg;t.className='toast'+(err?' err':'');t.style.display='block';clearTimeout(t._t);t._t=setTimeout(function(){t.style.display='none';},2600);} \n"
                +
                "function setStatus(msg){document.getElementById('cam-status').textContent=msg;} \n" +

                "function updateTable(lines){\n" +
                "  lineData = lines;\n" +
                "  var rows='';\n" +
                "  var count=0;\n" +
                "  for(var i=0; i<lines.length; i++){ \n" +
                "     var l = lines[i]; count++; \n" +
                "     var isFail = l.condition === 'FAIL'; \n" +
                "     var condHtml = isFail ? '<span style=\"color:#ef4444;font-size:10px;display:block\">Lỗi: '+(l.reasonCode||'N/A')+'</span>' : '';\n"
                +
                "     rows+='<tr><td class=\"sc\">'+l.skuCode+condHtml+'</td><td>'+(l.skuName||'')+'</td><td class=\"qc\" style=\"'+(isFail?'color:#ef4444':'')+'\">'+l.qty+'</td><td><button onclick=\"removeL(\\''+l.skuId+'\\',\\''+l.condition+'\\')\" style=\"background:transparent;border:1px solid #ef4444;border-radius:4px;color:#ef4444;font-size:12px;padding:3px 6px;font-weight:bold\">-1</button></td></tr>';\n"
                +
                "  }\n" +
                "  document.getElementById('lines').innerHTML=rows;\n" +
                "  document.getElementById('cnt').textContent=count+' dòng';\n" +
                "} \n" +
                "function renderSession(s){\n" +
                "  if(s && s.lines) updateTable(s.lines);\n" +
                "}\n" +
                "}\n" +
                "function removeL(skuId, cond){\n" +
                "  fetch(API+'?sessionId='+SESSION_ID+'&skuId='+skuId+'&condition='+cond+'&qty=1',{method:'DELETE',headers:{'Authorization':'Bearer '+TOKEN}})\n"
                +
                "  .then(function(r){return r.json();})\n" +
                "  .then(function(d){\n" +
                "    if(d&&d.success){ fetchSession(); }\n" +
                "    else{ toast(d.message||'Lỗi giảm số lượng',true); }\n" +
                "  }).catch(function(e){ toast('Lỗi mạng: '+e,true); });\n" +
                "}\n" +
                "function fetchSession(){\n" +
                "  if(!SESSION_ID) return;\n" +
                "  fetch(window.location.origin+'/v1/receiving-sessions/'+SESSION_ID,{headers:{'Authorization':'Bearer '+TOKEN}})\n"
                +
                "  .then(function(r){return r.json();})\n" +
                "  .then(function(d){ if(d&&d.success) renderSession(d.data); });\n" +
                "}\n" +

                "function sendBarcode(barcode,qty){\n" +
                "  if(inflight) return;\n" +
                "  inflight=true;\n" +
                "  setStatus('Đang gửi: '+barcode);\n" +
                "  var cond = document.getElementById('cond').value;\n" +
                "  var reason = cond==='FAIL'? document.getElementById('reason').value : null;\n" +
                "  if(cond==='FAIL' && !reason){ toast('Vui lòng chọn Lý do lỗi',true); inflight=false; setStatus('Chưa nhập lý do lỗi'); return; }\n"
                +
                "  fetch(API,{method:'POST',headers:{'Content-Type':'application/json','Authorization':'Bearer '+TOKEN},body:JSON.stringify({barcode:barcode,qty:qty,condition:cond,reasonCode:reason})})\n"
                +
                "  .then(function(r){return r.text().then(function(txt){try{return JSON.parse(txt);}catch(e){return {success:false,message:'HTTP '+r.status+': '+txt.substring(0,140)};}});})\n"
                +
                "  .then(function(d){\n" +
                "    if(d && d.success){toast('✓ '+(cond==='FAIL'?'LỖI ':'')+d.data.skuCode+' — qty:'+d.data.newQty); fetchSession(); document.getElementById('bc').value='';if(navigator.vibrate)navigator.vibrate(60);} \n"
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

                "function toggleCamera(){\n" +
                "  var btn=document.getElementById('closeBtn');\n" +
                "  if(qrRunning) {\n" +
                "    stopQr();\n" +
                "    btn.textContent='▶ Tiếp tục quét';\n" +
                "    btn.style.background='#3b82f6';\n" +
                "    btn.style.borderColor='#3b82f6';\n" +
                "    setStatus('Camera đã tạm dừng');\n" +
                "  } else {\n" +
                "    startQr();\n" +
                "    btn.textContent='⏸ Tạm dừng / Đóng Camera';\n" +
                "    btn.style.background='';\n" +
                "    btn.style.borderColor='';\n" +
                "  }\n" +
                "} \n" +
                "function createGrn(){\n" +
                "  if(!SESSION_ID){toast('Không tìm thấy session',true);return;}\n" +
                "  if(Object.keys(lineData||{}).length===0){toast('Chưa quét sản phẩm nào!',true);return;}\n" +
                "  if(!confirm('Chốt số lượng và tạo Phiếu Nhập Kho?')) return;\n" +
                "  var body = {\n" +
                "    sourceType: document.getElementById('sourceType').value,\n" +
                "    supplierCode: document.getElementById('supplierCode').value.trim() || null,\n" +
                "    sourceReferenceCode: document.getElementById('sourceRef').value.trim() || null,\n" +
                "    note: document.getElementById('note').value.trim() || null \n" +
                "  };\n" +
                "  var btn = document.getElementById('createGrnBtn');\n" +
                "  btn.disabled=true; btn.textContent='Đang tạo...';\n" +
                "  fetch(window.location.origin+'/v1/receiving-sessions/'+SESSION_ID+'/create-grn',{method:'POST',headers:{'Content-Type':'application/json','Authorization':'Bearer '+TOKEN},body:JSON.stringify(body)})\n"
                +
                "  .then(function(r){return r.json();})\n" +
                "  .then(function(d){\n" +
                "    if(d&&d.success){\n" +
                "      toast('✅ Đã tạo GRN: '+ (d.data.receivingId||'Thành công'), false);\n" +
                "      stopQr();\n" +
                "      document.getElementById('cam-status').textContent='Đã hoàn tất phiên quét.';\n" +
                "      btn.textContent='✅ Đã tạo Phiếu';\n" +
                "      document.getElementById('manualBtn').disabled=true;\n" +
                "      document.getElementById('closeBtn').disabled=true;\n" +
                "    } else {\n" +
                "      toast(d.message||'Lỗi tạo phiếu',true);\n" +
                "      btn.disabled=false; btn.textContent='✅ Tạo Phiếu GRN';\n" +
                "    }\n" +
                "  }).catch(function(e){\n" +
                "    toast('Lỗi mạng: '+e,true);\n" +
                "    btn.disabled=false; btn.textContent='✅ Tạo Phiếu GRN';\n" +
                "  });\n" +
                "}\n" +

                // QR start/stop (html5-qrcode)
                "var qr=null;\n" +
                "var qrRunning=false;\n" +
                "function stopQr(){\n" +
                "  try{ if(qr && qrRunning){ qr.stop().then(function(){qrRunning=false;}).catch(function(){});} }catch(e){}\n"
                +
                "} \n" +
                "function startQr(){\n" +
                "  setStatus('Đang khởi động camera...');\n" +
                "  \n" +
                "  // Check HTTPS - camera requires secure context\n" +
                "  if (window.location.protocol !== 'https:' && window.location.hostname !== 'localhost' && window.location.hostname !== '127.0.0.1') {\n"
                +
                "    toast('Cần HTTPS để quét QR!', true);\n" +
                "    setStatus('Lỗi: Cần HTTPS để truy cập camera');\n" +
                "    return;\n" +
                "  }\n" +
                "  \n" +
                "  // Debug: log library availability\n" +
                "  console.log('Html5Qrcode available:', typeof Html5Qrcode !== 'undefined');\n" +
                "  \n" +
                "  if (typeof Html5Qrcode === 'undefined') {\n" +
                "    toast('Thư viện QR chưa tải! Thử refresh trang.', true);\n" +
                "    setStatus('Lỗi: Thư viện chưa tải');\n" +
                "    return;\n" +
                "  }\n" +
                "  \n" +
                "  try{\n" +
                "    Html5Qrcode.getCameras().then(function(cameras){\n" +
                "      console.log('Cameras found:', cameras);\n" +
                "      \n" +
                "      if(cameras && cameras.length){\n" +
                "        var cameraId = null;\n" +
                "        \n" +
                "        // First: try to find back camera (prefer environment/back/rear)\n" +
                "        for(var i=0; i<cameras.length; i++){\n" +
                "          var label = cameras[i].label.toLowerCase();\n" +
                "          if(label.includes('back') || label.includes('rear') || label.includes('environment')){\n" +
                "            cameraId = cameras[i].id;\n" +
                "            break;\n" +
                "          }\n" +
                "        }\n" +
                "        \n" +
                "        // If no back camera found, use last camera (usually front on iOS)\n" +
                "        if(!cameraId && cameras.length > 0){\n" +
                "          cameraId = cameras[cameras.length - 1].id;\n" +
                "        }\n" +
                "        \n" +
                "        console.log('Using camera:', cameraId);\n" +
                "        \n" +
                "        qr = new Html5Qrcode('reader');\n" +
                "        \n" +
                "        // Use facingMode environment to prefer back camera\n" +
                "        var config = { fps: 10, qrbox: { width: 250, height: 250 }, videoConstraints: { facingMode: 'environment' } };\n"
                +
                "        \n" +
                "        qr.start(cameraId, config, function(decodedText){\n" +
                "          var code=(decodedText||'').trim().toUpperCase();\n" +
                "          if(code.length<2) return;\n" +
                "          var now=Date.now();\n" +
                "          if(code===lastCode && (now-lastAt)<1500) return;\n" +
                "          lastCode=code; lastAt=now;\n" +
                "          document.getElementById('last').textContent=code;\n" +
                "          sendBarcode(code,1);\n" +
                "        }, function(errorMessage){ \n" +
                "          // Ignore scan errors (no QR found)\n" +
                "        }).then(function(){\n" +
                "          qrRunning=true;\n" +
                "          setStatus('Camera sẵn sàng — đưa QR vào khung');\n" +
                "        }).catch(function(e){\n" +
                "          console.log('Camera error:', e);\n" +
                "          toast('Không mở được camera: ' + e, true);\n" +
                "          setStatus('Camera lỗi: '+e);\n" +
                "        });\n" +
                "      }else{\n" +
                "        toast('Không tìm thấy camera!', true);\n" +
                "        setStatus('Lỗi: Không tìm thấy camera');\n" +
                "      }\n" +
                "    }).catch(function(e){\n" +
                "      console.log('getCameras error:', e);\n" +
                "      toast('Lỗi truy cập camera: ' + e, true);\n" +
                "      setStatus('Lỗi: ' + e);\n" +
                "    });\n" +
                "  }catch(e){\n" +
                "    console.log('Start QR error:', e);\n" +
                "    toast('Không khởi tạo được QR: '+e, true);\n" +
                "    setStatus('Lỗi: '+e); \n" +
                "  } \n" +
                "} \n" +

                "document.addEventListener('DOMContentLoaded',function(){\n" +
                "  document.getElementById('manualBtn').addEventListener('click', submitManual);\n" +
                "  document.getElementById('closeBtn').addEventListener('click', toggleCamera);\n" +
                "  document.getElementById('createGrnBtn').addEventListener('click', createGrn);\n" +
                "  document.getElementById('bc').addEventListener('keydown', function(e){ if(e.key==='Enter') submitManual(); });\n"
                +
                "  document.getElementById('startCamBtn').addEventListener('click', function(){\n" +
                "    document.getElementById('cam-start-overlay').style.display='none';\n" +
                "    waitForHtml5Qrcode(startQr, 25);\n" +
                "  });\n" +
                "});\n" +
                "window.addEventListener('load', function(){\n" +
                "  fetchSession();\n" +
                "});\n" +

                "</script></body></html>";
    }
}
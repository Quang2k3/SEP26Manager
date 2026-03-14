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
@Tag(name = "Scanner Page", description = "Trang quét barcode")
public class ScannerPageController {

    @GetMapping(value = "/v1/scan/url", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Lấy URL trang scan")
    public String getScanUrl(@RequestParam("token") String token,
                             @RequestParam(value = "receivingId", required = false) Long receivingId,
                             HttpServletRequest request) {
        // Ưu tiên header X-Forwarded-Host để handle reverse proxy (Cloudflare)
        String host = request.getHeader("X-Forwarded-Host");
        String scheme = request.getHeader("X-Forwarded-Proto");
        if (host == null || host.isBlank()) {
            host = request.getServerName();
            int port = request.getServerPort();
            if (port != 80 && port != 443) host += ":" + port;
        }
        if (scheme == null || scheme.isBlank()) scheme = request.getScheme();
        String base = scheme + "://" + host;
        String url = base + "/v1/scan?token=" + token;
        if (receivingId != null) url += "&receivingId=" + receivingId;
        return url;
    }

    @GetMapping(value = "/v1/scan", produces = MediaType.TEXT_HTML_VALUE)
    @Operation(summary = "Trang quét barcode (HTML)")
    public ResponseEntity<String> scannerPage(@RequestParam("token") String token,
                                              @RequestParam(value = "receivingId", required = false) Long receivingId) {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
                .header("Pragma", "no-cache")
                .body(buildHtml(escapeForJs(token), receivingId));
    }

    private static String escapeForJs(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("'", "\\'");
    }

    private String buildHtml(String token, Long receivingId) {
        String receivingIdJs = receivingId != null ? String.valueOf(receivingId) : "null";
        return "<!DOCTYPE html>" +
                "<html lang='vi'><head>" +
                "<meta charset='UTF-8'>" +
                "<meta name='viewport' content='width=device-width,initial-scale=1.0,user-scalable=no'>" +
                "<title>Warehouse Scanner</title>" +
                "<script src='/js/html5-qrcode.min.js'></script>" +
                "<style>" +
                "*{box-sizing:border-box;margin:0;padding:0}" +
                "body{font-family:-apple-system,BlinkMacSystemFont,sans-serif;background:#0f172a;color:#e2e8f0;min-height:100vh}" +
                "header{background:linear-gradient(135deg,#1e40af,#1d4ed8);padding:14px 16px;display:flex;align-items:center;gap:10px}" +
                "header h1{font-size:17px;font-weight:700;flex:1}" +
                ".badge{border-radius:20px;padding:3px 12px;font-size:12px;font-weight:700}" +
                ".badge-role{background:#22c55e;color:#fff;text-transform:uppercase}" +
                ".badge-role.role-qc{background:#f59e0b;color:#000}" +
                ".badge-cnt{background:#dbeafe;color:#1e3a8a}" +
                ".container{padding:12px;max-width:520px;margin:0 auto}" +
                "#cam-wrap{position:relative;border-radius:14px;overflow:hidden;border:2px solid #3b82f6;background:#111;margin-bottom:8px}" +
                "#reader{width:100%}" +
                "#scan-line{position:absolute;left:8%;right:8%;height:2px;background:linear-gradient(90deg,transparent,#34d399,transparent);top:50%;animation:scan 1.8s ease-in-out infinite;pointer-events:none;z-index:10}" +
                "@keyframes scan{0%,100%{top:20%}50%{top:80%}}" +
                "#cam-status{background:#1e293b;color:#94a3b8;font-size:11px;padding:6px 12px;text-align:center;border-radius:0 0 12px 12px}" +
                ".card{background:#1e293b;border-radius:12px;padding:14px;margin-top:10px}" +
                ".card-title{font-size:11px;color:#64748b;text-transform:uppercase;letter-spacing:.08em;margin-bottom:10px;font-weight:600}" +
                ".hint{color:#94a3b8;font-size:12px;line-height:1.35}" +
                ".row{display:flex;gap:8px}" +
                "input{flex:1;padding:11px 14px;background:#0f172a;border:1.5px solid #334155;border-radius:8px;color:#e2e8f0;font-size:16px;-webkit-appearance:none}" +
                "input:focus{outline:none;border-color:#3b82f6}" +
                ".qty-in{max-width:90px;text-align:center;flex:none}" +
                ".btn{padding:12px 18px;border:none;border-radius:10px;font-size:16px;font-weight:800;cursor:pointer;background:#3b82f6;color:#fff}" +
                ".btn.full{width:100%}" +
                ".btn.danger{background:#ef4444;width:100%}" +
                ".btn.success{background:linear-gradient(135deg,#22c55e,#16a34a);width:100%;font-size:17px;padding:14px 18px;margin-top:10px}" +
                ".btn.success:disabled{opacity:.5;cursor:not-allowed}" +
                ".btn.warning{background:linear-gradient(135deg,#f59e0b,#d97706);width:100%;font-size:17px;padding:14px 18px;margin-top:10px;display:none}" +
                "table{width:100%;border-collapse:collapse;font-size:13px}" +
                "th{text-align:left;color:#475569;padding:5px 4px;font-size:11px;font-weight:600}" +
                "td{padding:9px 4px;border-bottom:1px solid #263347}" +
                ".qc{text-align:right;font-weight:800;color:#34d399;font-size:15px}" +
                ".sc{color:#94a3b8;font-size:11px}" +
                ".status-match{color:#22c55e;font-weight:800}" +
                ".status-mismatch{color:#ef4444;font-weight:800}" +
                ".status-pending{color:#64748b}" +
                ".expected-col,.received-col{text-align:center}" +
                ".received-col{font-weight:800;font-size:14px}" +
                ".toast{position:fixed;bottom:28px;left:50%;transform:translateX(-50%);background:#10b981;color:#fff;padding:11px 24px;border-radius:28px;font-weight:800;font-size:14px;display:none;white-space:nowrap;box-shadow:0 4px 24px rgba(0,0,0,.5)}" +
                ".toast.err{background:#ef4444}" +
                ".qc-toggle-group{display:flex;gap:8px;margin-bottom:12px}" +
                ".qc-btn{flex:1;padding:10px;border-radius:8px;border:2px solid #334155;background:transparent;color:#94a3b8;font-weight:700;font-size:13px;cursor:pointer;opacity:.6}" +
                ".qc-btn.active.pass{border-color:#10b981;background:rgba(16,185,129,.1);color:#10b981;opacity:1}" +
                ".qc-btn.active.fail{border-color:#ef4444;background:rgba(239,68,68,.1);color:#ef4444;opacity:1}" +
                "</style></head><body>" +

                "<header><span style='font-size:20px'>📦</span><h1>Warehouse Scanner</h1>" +
                "<span class='badge badge-role' id='role-badge'></span>" +
                "<span class='badge badge-cnt' id='cnt'>0 dòng</span></header>" +

                "<div class='container'>" +
                "<div id='cam-wrap'><div id='reader'></div><div id='scan-line'></div></div>" +
                "<div id='cam-status'>Đang khởi động camera…</div>" +

                // Card: Phiếu nhận hàng
                "<div class='card'>" +
                "<div class='card-title'>Phiếu Nhận Hàng</div>" +
                "<div id='recv-display' style='display:none;padding:10px 14px;background:#0f172a;border-radius:8px;border:1.5px solid #22c55e'>" +
                "  <span style='color:#64748b;font-size:12px'>ID Phiếu Nhận</span>" +
                "  <div style='font-size:22px;font-weight:800;color:#22c55e;margin-top:2px' id='recv-id-label'></div>" +
                "</div>" +
                "<div id='recv-manual' style='display:none'>" +
                "  <input type='number' id='recv-input' placeholder='Nhập ID Phiếu Nhận ...' style='width:100%'/>" +
                "  <div style='color:#f59e0b;font-size:11px;margin-top:6px'>⚠️ Không có ID phiếu trong URL. Vui lòng nhập tay.</div>" +
                "</div>" +
                "</div>" +

                // Card: Thông tin đơn hàng
                "<div class='card' id='order-info-card' style='display:none'>" +
                "<div class='card-title'>📋 Thông tin đơn hàng</div>" +
                "<div id='order-loading' style='text-align:center;padding:16px;color:#64748b'>Đang tải...</div>" +
                "<div id='order-info-content' style='display:none'>" +
                "  <div style='display:grid;grid-template-columns:1fr 1fr;gap:8px'>" +
                "    <div style='background:#0f172a;border-radius:8px;padding:10px'><div style='font-size:10px;color:#64748b'>MÃ PHIẾU</div><div style='font-size:14px;font-weight:700' id='oi-code'>—</div></div>" +
                "    <div style='background:#0f172a;border-radius:8px;padding:10px'><div style='font-size:10px;color:#64748b'>TRẠNG THÁI</div><div style='font-size:14px;font-weight:700' id='oi-status'>—</div></div>" +
                "    <div style='background:#0f172a;border-radius:8px;padding:10px'><div style='font-size:10px;color:#64748b'>NHÀ CUNG CẤP</div><div style='font-size:14px;font-weight:700' id='oi-supplier'>—</div></div>" +
                "    <div style='background:#0f172a;border-radius:8px;padding:10px'><div style='font-size:10px;color:#64748b'>DỰ KIẾN</div><div style='font-size:14px;font-weight:700' id='oi-expected'>—</div></div>" +
                "  </div>" +
                "  <div style='color:#64748b;font-size:11px;margin-top:6px' id='oi-note'></div>" +
                "</div>" +
                "</div>" +

                // Card: Bảng kiểm đếm dự kiến vs thực nhận
                "<div class='card' id='comparison-card' style='display:none'>" +
                "<div class='card-title'>📊 KIỂM ĐẾM (Dự kiến vs Thực nhận)</div>" +
                "<table><thead><tr>" +
                "<th>SKU</th><th>Tên SP</th>" +
                "<th style='text-align:center'>Dự kiến</th>" +
                "<th style='text-align:center'>Thực nhận</th>" +
                "<th style='text-align:center'>KQ</th>" +
                "</tr></thead><tbody id='comparison-lines'></tbody></table>" +
                "<div style='margin-top:10px;display:flex;justify-content:space-between;font-size:12px'>" +
                "  <span style='color:#64748b'>Tổng: <b id='comp-total' style='color:#e2e8f0'>0</b></span>" +
                "  <span>✓ <b id='comp-match' style='color:#22c55e'>0</b> &nbsp; ✗ <b id='comp-mismatch' style='color:#ef4444'>0</b></span>" +
                "</div>" +
                "</div>" +

                // Card: Quét QR / QC toggle
                "<div class='card'>" +
                "<div class='card-title'>Quét QR <span id='qc-indicator' style='display:none;color:#f59e0b;font-weight:700'>(CHẾ ĐỘ QC)</span></div>" +
                "<div id='qc-toggle' class='qc-toggle-group' style='display:none'>" +
                "  <button id='btn-pass' class='qc-btn active pass' onclick='setCondition(\'PASS\')'>✓ PASS</button>" +
                "  <button id='btn-fail' class='qc-btn fail' onclick='setCondition(\'FAIL\')'>✗ FAIL</button>" +
                "</div>" +
                "<div class='hint'>Đưa barcode vào khung camera. Hoặc nhập SKU Code bên dưới.</div>" +
                "<div class='hint' style='margin-top:6px'>Vừa quét: <b id='last'>—</b></div>" +
                "</div>" +

                // Card: Nhập thủ công
                "<div class='card'>" +
                "<div class='card-title'>Nhập thủ công</div>" +
                "<div class='row'>" +
                "<input type='text' id='bc' placeholder='SKU Code' autocapitalize='characters' autocomplete='off'/>" +
                "<input type='number' id='qty' class='qty-in' value='1' min='0.01' step='0.01'/>" +
                "</div>" +
                "<button class='btn full' id='manualBtn' style='margin-top:10px'>Scan</button>" +
                "</div>" +

                // Card: Bảng đã scan phiên hiện tại
                "<div class='card'>" +
                "<div class='card-title'>Đã scan (phiên hiện tại)</div>" +
                "<table><thead><tr>" +
                "<th>SKU</th><th>Tên sản phẩm</th><th style='text-align:right'>Tổng Qty</th>" +
                "</tr></thead>" +
                "<tbody id='lines'></tbody></table>" +
                "</div>" +

                // Card: Nút xác nhận (chỉ hiện khi có receivingId)
                "<div class='card' id='confirm-card' style='display:none'>" +
                "<button class='btn success' id='confirmBtn'>✅ Xác nhận kiểm đếm — Gửi QC</button>" +
                "<button class='btn warning' id='qcSubmitBtn'>✅ QC HOÀN TẤT — Gửi kết quả</button>" +
                "</div>" +

                "<div class='card'><button class='btn danger' id='closeBtn'>🛑 Kết thúc Scan</button></div>" +
                "</div>" +
                "<div class='toast' id='toast'></div>" +

                "<script>\n" +
                "var TOKEN='" + token + "';\n" +
                "var RECEIVING_ID=" + receivingIdJs + ";\n" +
                "var API=window.location.origin+'/v1/scan-events';\n" +
                "var ORDER_API=window.location.origin+'/v1/receiving-orders';\n" +
                "var SESSION_API=window.location.origin+'/v1/receiving-sessions';\n" +
                // lineData: key = skuCode, value = {name, passQty, failQty}
                "var lineData={};\n" +
                "var orderItems=[];\n" +
                "var inflight=false;\n" +
                "var lastCode=null;\n" +
                "var lastAt=0;\n" +
                "var currentCondition='PASS';\n" +
                "var SESSION_ID=(function(){try{var p=TOKEN.split('.')[1];var s=p.replace(/-/g,'+').replace(/_/g,'/');while(s.length%4)s+='=';return JSON.parse(atob(s)).sessionId||null;}catch(e){return null;}})();\n" +
                "var USER_ROLE=(function(){try{var p=TOKEN.split('.')[1];var s=p.replace(/-/g,'+').replace(/_/g,'/');while(s.length%4)s+='=';var d=JSON.parse(atob(s));return (d.roles||d.role||'').split(',')[0].trim().toUpperCase()||'KEEPER';}catch(e){return 'KEEPER';}})();\n" +

                "function toast(msg,err){var t=document.getElementById('toast');t.textContent=msg;t.className='toast'+(err?' err':'');t.style.display='block';clearTimeout(t._t);t._t=setTimeout(function(){t.style.display='none';},2800);}\n" +
                "function setStatus(msg){document.getElementById('cam-status').textContent=msg;}\n" +

                // renderLines: render bảng đã scan từ lineData
                // lineData[skuCode] = {name, passQty, failQty}
                "function renderLines(){\n" +
                "  var rows='';\n" +
                "  var total=0;\n" +
                "  for(var k in lineData){\n" +
                "    var d=lineData[k];\n" +
                "    var tq=(d.passQty||0)+(d.failQty||0);\n" +
                "    total+=tq;\n" +
                "    rows+='<tr><td class=\"sc\">'+k+'</td><td>'+d.name+'</td><td class=\"qc\">'+tq+(d.failQty>0?' <span style=\"color:#ef4444;font-size:10px\">('+d.failQty+' FAIL)</span>':'')+ '</td></tr>';\n" +
                "  }\n" +
                "  document.getElementById('lines').innerHTML=rows;\n" +
                "  document.getElementById('cnt').textContent=Object.keys(lineData).length+' dòng';\n" +
                "  updateComparisonTable();\n" +
                "}\n" +

                // addToLineData: cộng dồn qty đúng theo condition
                "function addToLineData(skuCode, skuName, qty, condition){\n" +
                "  if(!lineData[skuCode]) lineData[skuCode]={name:skuName||skuCode,passQty:0,failQty:0};\n" +
                "  var q=parseFloat(qty)||0;\n" +
                "  if(condition==='FAIL') lineData[skuCode].failQty+=q;\n" +
                "  else lineData[skuCode].passQty+=q;\n" +
                "  if(skuName) lineData[skuCode].name=skuName;\n" +
                "}\n" +

                "function updateComparisonTable(){\n" +
                "  if(!orderItems.length) return;\n" +
                "  var rows='',match=0,mis=0;\n" +
                "  for(var i=0;i<orderItems.length;i++){\n" +
                "    var item=orderItems[i];\n" +
                "    var sku=item.skuCode||'';\n" +
                "    var expected=parseFloat(item.expectedQty)||0;\n" +
                "    var ld=lineData[sku];\n" +
                "    var scanned=ld?(ld.passQty||0)+(ld.failQty||0):0;\n" +
                "    var cls='status-pending',txt='⏳';\n" +
                "    if(scanned>0){if(scanned>=expected){cls='status-match';txt='✓';match++;}else{cls='status-mismatch';txt=scanned+'/'+expected;mis++;}}\n" +
                "    rows+='<tr><td class=\"sc\">'+sku+'</td><td>'+( item.skuName||'')+ '</td><td class=\"expected-col\">'+expected+'</td><td class=\"received-col\">'+scanned+'</td><td class=\"'+ cls+'\">'+ txt+'</td></tr>';\n" +
                "  }\n" +
                "  document.getElementById('comparison-lines').innerHTML=rows;\n" +
                "  document.getElementById('comp-total').textContent=orderItems.length;\n" +
                "  document.getElementById('comp-match').textContent=match;\n" +
                "  document.getElementById('comp-mismatch').textContent=(orderItems.length-match);\n" +
                "}\n" +

                "function loadOrderDetails(){\n" +
                "  if(!RECEIVING_ID) return;\n" +
                "  document.getElementById('order-info-card').style.display='block';\n" +
                "  document.getElementById('comparison-card').style.display='block';\n" +
                "  document.getElementById('confirm-card').style.display='block';\n" +
                "  fetch(ORDER_API+'/'+RECEIVING_ID,{headers:{'Authorization':'Bearer '+TOKEN}})\n" +
                "  .then(function(r){return r.json();})\n" +
                "  .then(function(resp){\n" +
                "    if(resp&&resp.success&&resp.data){\n" +
                "      orderItems=resp.data.items||[];\n" +
                "      document.getElementById('order-loading').style.display='none';\n" +
                "      document.getElementById('order-info-content').style.display='block';\n" +
                "      document.getElementById('oi-code').textContent=resp.data.receivingCode||'N/A';\n" +
                "      document.getElementById('oi-status').textContent=resp.data.status||'N/A';\n" +
                "      document.getElementById('oi-supplier').textContent=resp.data.supplierName||'N/A';\n" +
                "      document.getElementById('oi-expected').textContent=(resp.data.totalExpectedQty||0)+' sp';\n" +
                "      if(resp.data.note) document.getElementById('oi-note').textContent='📝 '+resp.data.note;\n" +
                "      updateComparisonTable();\n" +
                "    } else {\n" +
                "      document.getElementById('order-loading').textContent='Không tải được đơn: '+(resp&&resp.message?resp.message:'Lỗi');\n" +
                "    }\n" +
                "  }).catch(function(e){document.getElementById('order-loading').textContent='Lỗi kết nối: '+e;});\n" +
                "}\n" +

                // loadSessionData: load lines từ Redis session về lineData
                "function loadSessionData(){\n" +
                "  if(!SESSION_ID) return;\n" +
                "  fetch(SESSION_API+'/'+SESSION_ID,{headers:{'Authorization':'Bearer '+TOKEN}})\n" +
                "  .then(function(r){return r.json();})\n" +
                "  .then(function(resp){\n" +
                "    if(!resp||!resp.success||!resp.data||!resp.data.lines) return;\n" +
                "    var ls=resp.data.lines;\n" +
                "    lineData={};\n" +    // reset trước khi load
                "    for(var i=0;i<ls.length;i++){\n" +
                "      var l=ls[i];\n" +
                "      if(l.skuCode) addToLineData(l.skuCode, l.skuName||l.skuCode, l.qty||0, l.condition||'PASS');\n" +
                "    }\n" +
                "    renderLines();\n" +
                "  }).catch(function(e){console.warn('loadSessionData error:',e);});\n" +
                "}\n" +

                "function setCondition(cond){\n" +
                "  currentCondition=cond;\n" +
                "  document.getElementById('btn-pass').className='qc-btn'+(cond==='PASS'?' active pass':'');\n" +
                "  document.getElementById('btn-fail').className='qc-btn'+(cond==='FAIL'?' active fail':'');\n" +
                "}\n" +

                "function sendBarcode(barcode,qty){\n" +
                "  if(inflight) return;\n" +
                "  inflight=true;\n" +
                "  setStatus('Đang gửi: '+barcode);\n" +
                "  var rid=RECEIVING_ID;\n" +
                "  if(!rid){var v=document.getElementById('recv-input');if(v&&v.value)rid=parseInt(v.value)||null;}\n" +
                "  fetch(API,{method:'POST'," +
                "    headers:{'Content-Type':'application/json','Authorization':'Bearer '+TOKEN}," +
                "    body:JSON.stringify({barcode:barcode,qty:qty,condition:currentCondition,receivingId:rid})})\n" +
                "  .then(function(r){return r.text().then(function(txt){try{return JSON.parse(txt);}catch(e){return {success:false,message:'HTTP '+r.status+': '+txt.substring(0,120)};}});})\n" +
                "  .then(function(d){\n" +
                "    if(d&&d.success){\n" +
                "      var data=d.data;\n" +
                "      toast('✓ '+data.skuCode+' qty:'+data.newQty+(currentCondition==='FAIL'?' [FAIL]':''));\n" +
                "      addToLineData(data.skuCode, data.skuName||data.skuCode, data.newQty, data.condition||currentCondition);\n" +
                // Sau khi addToLineData, reload toàn bộ session để đồng bộ chính xác
                "      loadSessionData();\n" +
                "      document.getElementById('bc').value='';\n" +
                "      if(navigator.vibrate) navigator.vibrate(60);\n" +
                "    } else {\n" +
                "      toast((d&&d.message)?d.message:'Lỗi không xác định',true);\n" +
                "    }\n" +
                "    setStatus('Camera sẵn sàng — đưa barcode vào khung');\n" +
                "  })\n" +
                "  .catch(function(e){toast('Mất kết nối',true);setStatus('Lỗi mạng: '+e);})\n" +
                "  .finally(function(){setTimeout(function(){inflight=false;},800);});\n" +
                "}\n" +

                "function submitManual(){\n" +
                "  var b=(document.getElementById('bc').value||'').trim().toUpperCase();\n" +
                "  var q=parseFloat(document.getElementById('qty').value)||1;\n" +
                "  if(!b){toast('Nhập mã SKU!',true);return;}\n" +
                "  document.getElementById('last').textContent=b;\n" +
                "  sendBarcode(b,q);\n" +
                "}\n" +

                // confirmAndSubmit: DRAFT → SUBMITTED (gộp submit + finalize-count)
                "function confirmAndSubmit(){\n" +
                "  var rid=RECEIVING_ID;\n" +
                "  if(!rid){var v=document.getElementById('recv-input');if(v&&v.value)rid=parseInt(v.value)||null;}\n" +
                "  if(!rid){toast('Không có ID phiếu!',true);return;}\n" +
                "  if(!confirm('Xác nhận kiểm đếm xong?\nPhiếu sẽ gửi cho QC kiểm tra.')) return;\n" +
                "  var btn=document.getElementById('confirmBtn');\n" +
                "  btn.disabled=true;btn.textContent='Đang gửi...';\n" +
                "  fetch(ORDER_API+'/'+rid+'/submit',{method:'POST',headers:{'Authorization':'Bearer '+TOKEN}})\n" +
                "  .then(function(r){return r.json();})\n" +
                "  .then(function(d1){\n" +
                "    var ok=d1&&d1.success;\n" +
                "    var alreadyPending=!ok&&d1&&d1.message&&d1.message.indexOf('PENDING_COUNT')!==-1;\n" +
                "    if(!ok&&!alreadyPending){toast((d1&&d1.message)?d1.message:'Lỗi submit',true);btn.disabled=false;btn.textContent='✅ Xác nhận kiểm đếm — Gửi QC';return;}\n" +
                "    btn.textContent='Đang chốt...';\n" +
                "    return fetch(ORDER_API+'/'+rid+'/finalize-count',{method:'POST',headers:{'Authorization':'Bearer '+TOKEN}})\n" +
                "      .then(function(r2){return r2.json();})\n" +
                "      .then(function(d2){\n" +
                "        if(d2&&d2.success){\n" +
                "          toast('✅ Đã gửi QC! Phiếu chuyển SUBMITTED.');\n" +
                "          btn.textContent='✅ Đã gửi QC';btn.style.background='#475569';\n" +
                "          document.getElementById('oi-status').textContent='SUBMITTED';\n" +
                "        } else {\n" +
                "          toast((d2&&d2.message)?d2.message:'Lỗi finalize',true);\n" +
                "          btn.disabled=false;btn.textContent='✅ Xác nhận kiểm đếm — Gửi QC';\n" +
                "        }\n" +
                "      });\n" +
                "  })\n" +
                "  .catch(function(e){toast('Lỗi kết nối: '+e,true);btn.disabled=false;btn.textContent='✅ Xác nhận kiểm đếm — Gửi QC';});\n" +
                "}\n" +

                "function qcSubmit(){\n" +
                "  var rid=RECEIVING_ID;\n" +
                "  if(!rid){toast('Không có ID phiếu!',true);return;}\n" +
                "  if(!SESSION_ID){toast('Không có session ID!',true);return;}\n" +
                "  if(!confirm('Hoàn tất QC?\nHệ thống sẽ đối chiếu và tạo Incident nếu có hàng FAIL.')) return;\n" +
                "  var btn=document.getElementById('qcSubmitBtn');\n" +
                "  btn.disabled=true;btn.textContent='Đang xử lý...';\n" +
                "  fetch(ORDER_API+'/'+rid+'/qc-submit-session?sessionId='+SESSION_ID,{method:'POST',headers:{'Authorization':'Bearer '+TOKEN}})\n" +
                "  .then(function(r){return r.json();})\n" +
                "  .then(function(d){\n" +
                "    if(d&&d.success){\n" +
                "      var msg=d.data&&d.data.hasFailItems?'⚠️ QC Hoàn Tất: Có hàng FAIL. Đã tạo Incident.':'✅ QC Hoàn Tất: 100% PASS!';\n" +
                "      toast(msg);\n" +
                "      btn.textContent='Đã hoàn tất';btn.style.background='#475569';\n" +
                "      if(d.data&&d.data.status) document.getElementById('oi-status').textContent=d.data.status;\n" +
                "      setTimeout(function(){window.location.reload();},2000);\n" +
                "    } else {\n" +
                "      toast((d&&d.message)?d.message:'Lỗi QC Submit',true);\n" +
                "      btn.disabled=false;btn.textContent='✅ QC HOÀN TẤT — Gửi kết quả';\n" +
                "    }\n" +
                "  }).catch(function(e){toast('Lỗi kết nối: '+e,true);btn.disabled=false;btn.textContent='✅ QC HOÀN TẤT — Gửi kết quả';});\n" +
                "}\n" +

                "function closeScan(){\n" +
                "  if(!SESSION_ID){toast('Không tìm thấy session',true);return;}\n" +
                "  if(!confirm('Kết thúc phiên scan?')) return;\n" +
                "  fetch(SESSION_API+'/'+SESSION_ID,{method:'DELETE',headers:{'Authorization':'Bearer '+TOKEN}})\n" +
                "  .then(function(r){return r.json();})\n" +
                "  .then(function(d){\n" +
                "    if(d&&d.success){toast('Phiên scan đã đóng');stopQr();var btn=document.getElementById('closeBtn');btn.disabled=true;btn.textContent='Đã đóng';}\n" +
                "    else{toast((d&&d.message)?d.message:'Lỗi đóng session',true);}\n" +
                "  }).catch(function(e){toast('Lỗi kết nối: '+e,true);});\n" +
                "}\n" +

                "var qr=null,qrRunning=false;\n" +
                "function stopQr(){try{if(qr&&qrRunning){qr.stop().then(function(){qrRunning=false;}).catch(function(){});}}catch(e){}}\n" +

                "function startQr(){\n" +
                "  setStatus('Đang khởi động camera...');\n" +
                "  if(window.location.protocol!=='https:'&&window.location.hostname!=='localhost'&&window.location.hostname!=='127.0.0.1'){\n" +
                "    setStatus('Lỗi: Cần HTTPS để dùng camera');toast('Cần HTTPS!',true);return;\n" +
                "  }\n" +
                "  if(typeof Html5Qrcode==='undefined'){setStatus('Lỗi: Thư viện QR chưa tải');toast('Reload trang!',true);return;}\n" +
                "  var onDecode=function(txt){\n" +
                "    var code=(txt||'').trim().toUpperCase();\n" +
                "    if(code.length<2) return;\n" +
                "    var now=Date.now();\n" +
                "    if(code===lastCode&&(now-lastAt)<1500) return;\n" +
                "    lastCode=code;lastAt=now;\n" +
                "    document.getElementById('last').textContent=code;\n" +
                "    sendBarcode(code,1);\n" +
                "  };\n" +
                "  function tryStart(mode){\n" +
                "    qr=new Html5Qrcode('reader');\n" +
                "    qr.start({facingMode:mode},{fps:10,qrbox:{width:250,height:250}},onDecode,function(){})\n" +
                "      .then(function(){qrRunning=true;setStatus('Camera sẵn sàng — đưa barcode vào khung');})\n" +
                "      .catch(function(e){\n" +
                "        if(mode==='environment'){tryStart('user');}\n" +
                "        else{toast('Không mở được camera: '+e,true);setStatus('Camera lỗi: '+e);}\n" +
                "      });\n" +
                "  }\n" +
                "  try{tryStart('environment');}catch(e){toast('Lỗi khởi tạo QR: '+e,true);setStatus('Lỗi: '+e);}\n" +
                "}\n" +

                "function waitLib(cb,n){if(typeof Html5Qrcode!=='undefined'){cb();}else if(n>0){setTimeout(function(){waitLib(cb,n-1);},200);}else{setStatus('Lỗi: Không tải được thư viện QR');}}\n" +

                "document.addEventListener('DOMContentLoaded',function(){\n" +
                "  var rb=document.getElementById('role-badge');\n" +
                "  if(rb&&USER_ROLE){rb.textContent=USER_ROLE;rb.className='badge badge-role role-'+USER_ROLE.toLowerCase();}\n" +
                "  if(USER_ROLE==='QC'){\n" +
                "    document.getElementById('qc-toggle').style.display='flex';\n" +
                "    document.getElementById('qc-indicator').style.display='inline';\n" +
                "    document.getElementById('confirmBtn').style.display='none';\n" +
                "    document.getElementById('qcSubmitBtn').style.display='block';\n" +
                "  }\n" +
                "  if(RECEIVING_ID!==null&&RECEIVING_ID!==undefined){\n" +
                "    document.getElementById('recv-id-label').textContent='#'+RECEIVING_ID;\n" +
                "    document.getElementById('recv-display').style.display='block';\n" +
                "    document.getElementById('recv-manual').style.display='none';\n" +
                "    loadOrderDetails();\n" +
                "    loadSessionData();\n" +
                "    document.getElementById('confirm-card').style.display='block';\n" +
                "  } else {\n" +
                "    document.getElementById('recv-display').style.display='none';\n" +
                "    document.getElementById('recv-manual').style.display='block';\n" +
                "    loadSessionData();\n" +
                "  }\n" +
                "  document.getElementById('manualBtn').onclick=submitManual;\n" +
                "  document.getElementById('closeBtn').onclick=closeScan;\n" +
                "  document.getElementById('confirmBtn').onclick=confirmAndSubmit;\n" +
                "  document.getElementById('qcSubmitBtn').onclick=qcSubmit;\n" +
                "  document.getElementById('bc').onkeydown=function(e){if(e.key==='Enter')submitManual();};\n" +
                "});\n" +
                "window.addEventListener('load',function(){waitLib(startQr,25);});\n" +
                "</script></body></html>";
    }
}
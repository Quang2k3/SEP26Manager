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
@Tag(name = "Scanner Page", description = "Trang quét barcode cho iPhone.")
public class ScannerPageController {

    @GetMapping(value = "/v1/scan/url", produces = MediaType.TEXT_PLAIN_VALUE)
    public String getScanUrl(@RequestParam("token") String token,
                             @RequestParam(value = "receivingId", required = false) Long receivingId,
                             HttpServletRequest request) {
        String base = request.getScheme() + "://" + request.getServerName()
                + (request.getServerPort() == 80 || request.getServerPort() == 443 ? ""
                : ":" + request.getServerPort());
        String url = base + "/v1/scan?token=" + token + "&v=qr4";
        if (receivingId != null) url += "&receivingId=" + receivingId;
        return url;
    }

    @GetMapping(value = "/v1/scan", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> scannerPage(
            @RequestParam("token") String token,
            @RequestParam(value = "receivingId", required = false) Long receivingId,
            @RequestParam(value = "taskId",      required = false) Long taskId,
            @RequestParam(value = "mode",        required = false) String mode) {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
                .header("Pragma", "no-cache")
                .body(buildHtml(escapeJs(token), receivingId, taskId, mode));
    }

    private static String escapeJs(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("'", "\\'");
    }

    private String buildHtml(String token, Long receivingId, Long taskId, String mode) {
        String receivingIdJs = receivingId != null ? String.valueOf(receivingId) : "null";
        String taskIdJs      = taskId     != null ? String.valueOf(taskId)      : "null";
        String modeJs        = mode       != null ? mode : "inbound";
        return "<!DOCTYPE html>" +
                "<html lang='vi'><head>" +
                "<meta charset='UTF-8'>" +
                "<meta name='viewport' content='width=device-width,initial-scale=1.0,user-scalable=no'>" +
                "<title>Warehouse Scanner</title>" +
                "<script src='/js/html5-qrcode.min.js'></script>" +
                "<style>" +
                "*{box-sizing:border-box;margin:0;padding:0}\n" +
                "body{font-family:-apple-system,BlinkMacSystemFont,sans-serif;background:#0f172a;color:#e2e8f0;min-height:100vh}\n" +
                "header{background:linear-gradient(135deg,#1e40af,#1d4ed8);padding:14px 16px;display:flex;align-items:center;gap:10px}\n" +
                "header h1{font-size:17px;font-weight:700;flex:1}\n" +
                ".badge{border-radius:20px;padding:3px 12px;font-size:12px;font-weight:700}\n" +
                ".badge-role{background:#22c55e;color:#fff;text-transform:uppercase;letter-spacing:.05em}\n" +
                ".badge-role.role-keeper{background:#22c55e}\n" +
                ".badge-role.role-qc{background:#f59e0b;color:#000}\n" +
                ".badge-role.role-manager{background:#a855f7}\n" +
                ".badge-cnt{background:#dbeafe;color:#1e3a8a}\n" +
                ".container{padding:12px;max-width:520px;margin:0 auto}\n" +
                "#cam-wrap{position:relative;border-radius:14px;overflow:hidden;border:2px solid #3b82f6;background:#111;margin-bottom:8px}\n" +
                "#reader{width:100%}\n" +
                "#scan-line{position:absolute;left:8%;right:8%;height:2px;background:linear-gradient(90deg,transparent,#34d399,transparent);top:50%;animation:scan 1.8s ease-in-out infinite;pointer-events:none;z-index:10}\n" +
                "@keyframes scan{0%,100%{top:20%}50%{top:80%}}\n" +
                "#cam-status{background:#1e293b;color:#94a3b8;font-size:11px;padding:6px 12px;text-align:center;border-radius:0 0 12px 12px}\n" +
                ".card{background:#1e293b;border-radius:12px;padding:14px;margin-top:10px}\n" +
                ".card-title{font-size:11px;color:#64748b;text-transform:uppercase;letter-spacing:.08em;margin-bottom:10px;font-weight:600}\n" +
                ".hint{color:#94a3b8;font-size:12px;line-height:1.35}\n" +
                ".row{display:flex;gap:8px}\n" +
                "input{flex:1;padding:11px 14px;background:#0f172a;border:1.5px solid #334155;border-radius:8px;color:#e2e8f0;font-size:16px;-webkit-appearance:none}\n" +
                "input:focus{outline:none;border-color:#3b82f6}\n" +
                ".qty-in{max-width:90px;text-align:center;flex:none}\n" +
                ".btn{padding:12px 18px;border:none;border-radius:10px;font-size:16px;font-weight:800;cursor:pointer;background:#3b82f6;color:#fff;width:100%}\n" +
                ".btn.success{background:linear-gradient(135deg,#22c55e,#16a34a);font-size:17px;padding:14px 18px;margin-top:6px}\n" +
                ".btn.success:disabled{opacity:.5;cursor:not-allowed}\n" +
                ".btn.danger{background:#ef4444;margin-top:6px}\n" +
                ".btn.warning{background:linear-gradient(135deg,#f59e0b,#d97706);color:#fff;font-size:17px;padding:14px 18px;margin-top:6px}\n" +
                ".btn.warning:disabled{opacity:.5;cursor:not-allowed}\n" +
                ".btn-minus{background:#ef4444;color:#fff;border:none;border-radius:6px;padding:4px 10px;font-size:13px;font-weight:800;cursor:pointer;min-width:32px}\n" +
                ".cond-badge{display:inline-block;border-radius:4px;padding:1px 6px;font-size:10px;font-weight:800;letter-spacing:.03em}\n" +
                ".cond-pass{background:rgba(34,197,94,.15);color:#22c55e}\n" +
                ".cond-fail{background:rgba(239,68,68,.15);color:#ef4444}\n" +
                "table{width:100%;border-collapse:collapse;font-size:13px}\n" +
                "th{text-align:left;color:#475569;padding:5px 4px;font-size:11px;font-weight:600}\n" +
                "td{padding:9px 4px;border-bottom:1px solid #263347}\n" +
                ".qc{text-align:right;font-weight:800;color:#34d399;font-size:15px}\n" +
                ".sc{color:#94a3b8;font-size:11px}\n" +
                ".order-info{display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-bottom:8px}\n" +
                ".order-info-item{background:#0f172a;border-radius:8px;padding:10px}\n" +
                ".order-info-label{font-size:10px;color:#64748b;text-transform:uppercase;letter-spacing:.05em}\n" +
                ".order-info-value{font-size:14px;font-weight:700;color:#e2e8f0;margin-top:2px}\n" +
                ".order-info-item.full{grid-column:1/-1}\n" +
                ".status-match{color:#22c55e;font-weight:800}\n" +
                ".status-mismatch{color:#ef4444;font-weight:800}\n" +
                ".status-over{color:#f59e0b;font-weight:800}\n" +
                ".status-pending{color:#64748b}\n" +
                ".expected-col{text-align:center;color:#94a3b8;font-size:12px}\n" +
                ".received-col{text-align:center;font-weight:800;font-size:14px}\n" +
                ".loading-spinner{text-align:center;padding:20px;color:#64748b}\n" +
                ".toast{position:fixed;bottom:28px;left:50%;transform:translateX(-50%);background:#10b981;color:#fff;padding:11px 24px;border-radius:28px;font-weight:800;font-size:14px;display:none;white-space:nowrap;box-shadow:0 4px 24px rgba(0,0,0,.5)}\n" +
                ".toast.err{background:#ef4444}\n" +
                ".toast.locked{background:#475569}\n" +
                ".qc-toggle-group{display:flex;gap:8px;margin-bottom:12px}\n" +
                ".qc-toggle-btn{flex:1;padding:8px 12px;border-radius:6px;border:2px solid #334155;background:transparent;color:#94a3b8;font-weight:700;font-size:12px;cursor:pointer;transition:all 0.2s;opacity:0.6}\n" +
                ".qc-toggle-btn.active.pass{border-color:#10b981;background:rgba(16,185,129,0.1);color:#10b981;opacity:1}\n" +
                ".qc-toggle-btn.active.fail{border-color:#ef4444;background:rgba(239,68,68,0.1);color:#ef4444;opacity:1}\n" +
                ".locked-overlay{text-align:center;padding:16px;background:rgba(71,85,105,0.3);border-radius:10px;margin-top:8px;border:1px solid #334155}\n" +
                "</style></head><body>" +
                "<header><span style='font-size:20px'>📦</span><h1>Warehouse Scanner</h1><span class='badge badge-role' id='role-badge'></span><span class='badge badge-cnt' id='cnt'>0 dòng</span></header>" +
                "<div class='container'>" +
                "<div id='cam-wrap'><div id='reader'></div><div id='scan-line'></div></div>" +
                "<div id='cam-status'>Đang khởi động camera QR…</div>" +
                "<div class='card' id='recv-card' style='display:none'>" +
                "<div class='card-title'>Phiếu Nhận Hàng</div>" +
                "<div id='recv-display' style='display:none;padding:10px 14px;background:#0f172a;border-radius:8px;border:1.5px solid #22c55e'>" +
                "  <span style='color:#64748b;font-size:12px'>ID Phiếu Nhận</span>" +
                "  <div style='font-size:22px;font-weight:800;color:#22c55e;margin-top:2px' id='recv-id-label'></div>" +
                "</div>" +
                "<div id='recv-manual' style='display:none'>" +
                "  <input type='number' id='recv-input' placeholder='Nhập ID Phiếu Nhận ...' style='width:100%'/>" +
                "  <div style='color:#f59e0b;font-size:11px;margin-top:6px'>⚠️ Không tìm thấy ID phiếu trong URL. Vui lòng nhập tay.</div>" +
                "</div></div>" +
                "<div class='card' id='order-info-card' style='display:none'><div class='card-title'>📋 Thông tin đơn hàng</div>" +
                "<div id='order-loading' class='loading-spinner'>Đang tải thông tin đơn hàng...</div>" +
                "<div id='order-info-content' style='display:none'><div class='order-info'>" +
                "<div class='order-info-item'><div class='order-info-label'>Mã phiếu</div><div class='order-info-value' id='oi-code'>—</div></div>" +
                "<div class='order-info-item'><div class='order-info-label'>Trạng thái</div><div class='order-info-value' id='oi-status'>—</div></div>" +
                "<div class='order-info-item'><div class='order-info-label'>Nhà cung cấp</div><div class='order-info-value' id='oi-supplier'>—</div></div>" +
                "<div class='order-info-item'><div class='order-info-label'>Dự kiến</div><div class='order-info-value' id='oi-expected'>—</div></div>" +
                "</div><div style='color:#64748b;font-size:11px;margin-top:4px' id='oi-note'></div></div></div>" +
                "<div class='card' id='comparison-card' style='display:none'><div class='card-title'>📊 Kiểm đếm (Dự kiến vs Thực nhận)</div>" +
                "<table><thead><tr><th>SKU</th><th>Tên SP</th><th style='text-align:center'>Dự kiến</th><th style='text-align:center'>Thực nhận</th><th style='text-align:center'>KQ</th></tr></thead><tbody id='comparison-lines'></tbody></table>" +
                "<div style='margin-top:10px;display:flex;gap:8px;justify-content:space-between'><div style='font-size:12px;color:#64748b'>Tổng dòng: <b id='comp-total' style='color:#e2e8f0'>0</b></div>" +
                "<div style='font-size:12px'>✓ <span id='comp-match' style='color:#22c55e;font-weight:700'>0</span> &nbsp; ✗ <span id='comp-mismatch' style='color:#ef4444;font-weight:700'>0</span></div></div></div>" +
                "<div class='card'><div class='card-title'>Quét QR <span id='qc-mode-indicator' style='display:none;font-weight:700;color:#f59e0b;'></span></div>" +
                "<div id='qc-toggle-panel' class='qc-toggle-group' style='display:none'>" +
                "  <button id='btn-qc-pass' class='qc-toggle-btn active pass' onclick='setQcCondition(\"PASS\")'>✓ Hàng Tốt (PASS)</button>" +
                "  <button id='btn-qc-fail' class='qc-toggle-btn fail' onclick='setQcCondition(\"FAIL\")'>✗ Hàng Lỗi (FAIL)</button>" +
                "</div>" +
                "<div class='hint'>Đưa mã QR vào khung. Nếu không quét được, kéo xuống nhập mã SKU bằng tay.</div>" +
                "<div class='hint' style='margin-top:8px'>Mã vừa quét: <b id='last'>-</b></div></div>" +
                "<div class='card'><div class='card-title'>Nhập thủ công (fallback)</div>" +
                "<div class='row'><input type='text' id='bc' placeholder='SKU Code (ví dụ: 0001-1012)' autocapitalize='characters' autocomplete='off'/>" +
                "<input type='number' id='qty' class='qty-in' value='1' min='0.01' step='0.01'/></div>" +
                "<button class='btn' id='manualBtn' style='margin-top:10px'>Scan</button></div>" +
                "<div class='card'><div class='card-title'>Đã scan (phiên hiện tại)</div>" +
                "<table><thead><tr><th>SKU</th><th>Tên sản phẩm</th><th style='text-align:center'>TT</th><th style='text-align:right'>Qty</th><th style='text-align:center;width:40px'></th></tr></thead><tbody id='lines'></tbody></table></div>" +
                "<div class='card' id='picking-card' style='display:none'>" +
                "<div class='card-title' style='color:#60a5fa'>📋 Picking — Xác nhận lấy hàng</div>" +
                "<div id='picking-items-list'></div>" +
                "<div id='picking-status' style='font-size:12px;color:#94a3b8;margin:8px 0'>Đang tải danh sách...</div>" +
                "</div>" +
                "<div class='card' id='action-card' style='display:none'>" +
                "<div id='action-locked' class='locked-overlay' style='display:none'>" +
                "  <div style='font-size:28px;margin-bottom:6px'>🔒</div>" +
                "  <p style='color:#94a3b8;font-size:13px;font-weight:700'>Đã hoàn tất — QR bị khoá</p>" +
                "  <p id='action-locked-msg' style='color:#64748b;font-size:11px;margin-top:4px'></p>" +
                "</div>" +
                "<button class='btn success' id='confirmBtn' style='display:none'> Xác nhận kiểm đếm — Gửi QC</button>" +
                "<button class='btn warning' id='qcSubmitBtn' style='display:none'> QC HOÀN TẤT CHECK — Gửi kết quả</button>" +
                "<div id='outbound-qc-summary' style='display:none;font-size:13px;color:#94a3b8;margin-bottom:8px'>Đang scan...</div>" +
                "<button class='btn success' id='qcPassAllBtn' style='display:none;background:#22c55e' onclick='submitOutboundQcResult(true)'> Xác nhận — Tất cả PASS (Cho xuất kho)</button>" +
                "<button class='btn danger' id='qcRejectBtn' style='display:none;margin-top:6px' onclick='submitOutboundQcResult(false)'>  Báo có hàng FAIL / HOLD</button>" +
                "<button class='btn success' id='confirmPickedBtn' style='display:none' disabled onclick='confirmPicked()'> Gửi sang QC — Đã lấy đủ hàng</button>" +
                "</div>" +
                "<div class='card'><button class='btn danger' id='closeBtn'>🛑 Kết thúc Scan</button></div>" +
                "</div>" +
                "<div class='toast' id='toast'></div>" +
                "<script>\n" +
                "var TOKEN='" + token + "';\n" +
                "var TASK_ID=" + taskIdJs + ";\n" +
                "var SCAN_MODE='" + modeJs + "';\n" +
                "var RECEIVING_ID=" + receivingIdJs + ";\n" +
                "var API=window.location.origin+'/v1/scan-events';\n" +
                "var ORDER_API=window.location.origin+'/v1/receiving-orders';\n" +
                "var lineData={};\n" +
                "var orderItems=[];\n" +
                "var orderData=null;\n" +
                "var inflight=false;\n" +
                "var lastCode=null;\n" +
                "var lastAt=0;\n" +
                "var currentCondition='PASS';\n" +
                "var pickItems=[];\n" +
                "var scannedPickSkus={};\n" +
                "\n" +
                "// ── Helpers ──────────────────────────────────────────────────────\n" +
                "function toast(msg,err){\n" +
                "  var t=document.getElementById('toast');\n" +
                "  t.textContent=msg;\n" +
                "  t.className='toast'+(err?' err':'');\n" +
                "  t.style.display='block';\n" +
                "  clearTimeout(t._t);\n" +
                "  t._t=setTimeout(function(){t.style.display='none';},3000);\n" +
                "}\n" +
                "function setStatus(msg){document.getElementById('cam-status').textContent=msg;}\n" +
                "function lockUI(msg){\n" +
                "  // Ẩn tất cả nút submit, hiện locked overlay\n" +
                "  document.getElementById('confirmBtn').style.display='none';\n" +
                "  document.getElementById('qcSubmitBtn').style.display='none';\n" +
                "  document.getElementById('qcPassAllBtn').style.display='none';\n" +
                "  document.getElementById('qcRejectBtn').style.display='none';\n" +
                "  document.getElementById('confirmPickedBtn').style.display='none';\n" +
                "  document.getElementById('action-locked-msg').textContent=msg||'';\n" +
                "  document.getElementById('action-locked').style.display='block';\n" +
                "  stopQr();\n" +
                "}\n" +
                "\n" +
                "// ── Role / session from token ─────────────────────────────────────\n" +
                "function base64UrlDecode(s){s=(s||'').replace(/-/g,'+').replace(/_/g,'/');while(s.length%4)s+='=';return atob(s);}\n" +
                "function getSessionId(){try{var p=TOKEN.split('.')[1];return JSON.parse(base64UrlDecode(p)).sessionId||null;}catch(e){return null;}}\n" +
                "function getRoleFromToken(){try{var p=TOKEN.split('.')[1];return (JSON.parse(base64UrlDecode(p)).roles||'').split(',')[0].trim().toUpperCase();}catch(e){return 'UNKNOWN';}}\n" +
                "var SESSION_ID=getSessionId();\n" +
                "var USER_ROLE=getRoleFromToken();\n" +
                "\n" +
                "// ── Table rendering ───────────────────────────────────────────────\n" +
                "function updateTable(d){\n" +
                "  var cond=d.condition||'PASS';\n" +
                "  var key=d.skuCode+'_'+cond;\n" +
                "  lineData[key]={name:d.skuName||'',qty:d.newQty,skuId:d.skuId,skuCode:d.skuCode,condition:cond};\n" +
                "  renderScanTable();\n" +
                "}\n" +
                "function renderScanTable(){\n" +
                "  var rows='';\n" +
                "  for(var k in lineData){\n" +
                "    var it=lineData[k];\n" +
                "    if(it.qty<=0){delete lineData[k];continue;}\n" +
                "    var cb=it.condition==='FAIL'?'<span class=\"cond-badge cond-fail\">✗ FAIL</span>':'<span class=\"cond-badge cond-pass\">✓ PASS</span>';\n" +
                "    rows+='<tr><td class=\"sc\">'+it.skuCode+'</td><td>'+it.name+'</td><td style=\"text-align:center\">'+cb+'</td><td class=\"qc\">'+it.qty+'</td>';\n" +
                "    rows+='<td style=\"text-align:center\"><button class=\"btn-minus\" onclick=\"decrementItem(\\''+k+'\\')\" title=\"Giảm 1\">−1</button></td></tr>';\n" +
                "  }\n" +
                "  document.getElementById('lines').innerHTML=rows;\n" +
                "  document.getElementById('cnt').textContent=Object.keys(lineData).length+' dòng';\n" +
                "  updateComparisonTable();\n" +
                "  if(SCAN_MODE==='outbound_qc') updateOutboundQcSummary();\n" +
                "}\n" +
                "\n" +
                "// ── Inbound: load order details ───────────────────────────────────\n" +
                "function loadOrderDetails(){\n" +
                "  if(!RECEIVING_ID) return;\n" +
                "  document.getElementById('order-info-card').style.display='block';\n" +
                "  document.getElementById('comparison-card').style.display='block';\n" +
                "  fetch(ORDER_API+'/'+RECEIVING_ID,{headers:{'Authorization':'Bearer '+TOKEN}})\n" +
                "  .then(function(r){return r.json();})\n" +
                "  .then(function(resp){\n" +
                "    if(resp&&resp.success&&resp.data){\n" +
                "      orderData=resp.data;\n" +
                "      orderItems=resp.data.items||[];\n" +
                "      document.getElementById('order-loading').style.display='none';\n" +
                "      document.getElementById('order-info-content').style.display='block';\n" +
                "      document.getElementById('oi-code').textContent=orderData.receivingCode||'N/A';\n" +
                "      document.getElementById('oi-status').textContent=orderData.status||'N/A';\n" +
                "      document.getElementById('oi-supplier').textContent=orderData.supplierName||'N/A';\n" +
                "      document.getElementById('oi-expected').textContent=(orderData.totalExpectedQty||0)+' sp / '+(orderData.totalLines||0)+' dòng';\n" +
                "      if(orderData.note) document.getElementById('oi-note').textContent='📝 '+orderData.note;\n" +
                "      updateComparisonTable();\n" +
                "    } else {\n" +
                "      document.getElementById('order-loading').textContent='Không tải được: '+(resp&&resp.message?resp.message:'Lỗi');\n" +
                "    }\n" +
                "  })\n" +
                "  .catch(function(e){document.getElementById('order-loading').textContent='Lỗi kết nối: '+e;});\n" +
                "}\n" +
                "function updateComparisonTable(){\n" +
                "  if(!orderItems.length) return;\n" +
                "  var rows='';var matchCount=0;\n" +
                "  for(var i=0;i<orderItems.length;i++){\n" +
                "    var item=orderItems[i];\n" +
                "    var sku=item.skuCode||'';\n" +
                "    var expected=parseFloat(item.expectedQty)||0;\n" +
                "    var scanned=0;\n" +
                "    for(var lk in lineData){if(lineData[lk].skuCode===sku)scanned+=parseFloat(lineData[lk].qty)||0;}\n" +
                "    var sc='status-pending',st='⏳';\n" +
                "    if(scanned>0){\n" +
                "      if(scanned===expected){sc='status-match';st='✓';matchCount++;}\n" +
                "      else if(scanned>expected){sc='status-over';st='✗ THỪA';}\n" +
                "      else{sc='status-mismatch';st='✗ '+scanned+'/'+expected;}\n" +
                "    }\n" +
                "    rows+='<tr><td class=\"sc\">'+sku+'</td><td>'+(item.skuName||'')+'</td><td class=\"expected-col\">'+expected+'</td><td class=\"received-col\">'+scanned+'</td><td class=\"'+sc+'\" style=\"text-align:center\">'+st+'</td></tr>';\n" +
                "  }\n" +
                "  document.getElementById('comparison-lines').innerHTML=rows;\n" +
                "  document.getElementById('comp-total').textContent=orderItems.length;\n" +
                "  document.getElementById('comp-match').textContent=matchCount;\n" +
                "  document.getElementById('comp-mismatch').textContent=(orderItems.length-matchCount);\n" +
                "}\n" +
                "\n" +
                "// ── ACTION FUNCTIONS (per mode) ───────────────────────────────────\n" +
                "\n" +
                "// INBOUND KEEPER: Xác nhận kiểm đếm → Gửi QC\n" +
                "function confirmAndSubmit(){\n" +
                "  if(!RECEIVING_ID){toast('Không có ID phiếu!',true);return;}\n" +
                "  if(!confirm('Xác nhận kiểm đếm xong?\\nPhiếu sẽ được gửi cho QC kiểm tra.')) return;\n" +
                "  var btn=document.getElementById('confirmBtn');\n" +
                "  btn.disabled=true;btn.textContent='Đang gửi...';\n" +
                "  fetch(ORDER_API+'/'+RECEIVING_ID+'/finalize-count',{method:'POST',headers:{'Authorization':'Bearer '+TOKEN}})\n" +
                "  .then(function(r){return r.json();})\n" +
                "  .then(function(d){\n" +
                "    if(d&&d.success){\n" +
                "      toast('✅ Đã gửi QC kiểm tra! QR bị khoá.');\n" +
                "      lockUI('Keeper đã chốt kiểm đếm — chờ QC xử lý');\n" +
                "      if(document.getElementById('oi-status')) document.getElementById('oi-status').textContent='SUBMITTED';\n" +
                "    } else {\n" +
                "      toast((d&&d.message)?d.message:'Lỗi submit',true);\n" +
                "      btn.disabled=false;btn.textContent=' Xác nhận kiểm đếm — Gửi QC';\n" +
                "    }\n" +
                "  })\n" +
                "  .catch(function(e){toast('Lỗi kết nối: '+e,true);btn.disabled=false;btn.textContent=' Xác nhận kiểm đếm — Gửi QC';});\n" +
                "}\n" +
                "\n" +
                "// INBOUND QC: Hoàn tất kiểm đếm QC → gửi kết quả\n" +
                "function qcSubmit(){\n" +
                "  if(!RECEIVING_ID){toast('Không có ID phiếu!',true);return;}\n" +
                "  if(!confirm('Hoàn tất quét duyệt QC?\\n(Sẽ đối chiếu kết quả với Keeper)')) return;\n" +
                "  var btn=document.getElementById('qcSubmitBtn');\n" +
                "  btn.disabled=true;btn.textContent='Đang xử lý...';\n" +
                "  var url=ORDER_API+'/'+RECEIVING_ID+'/qc-submit-session?sessionId='+SESSION_ID;\n" +
                "  fetch(url,{method:'POST',headers:{'Authorization':'Bearer '+TOKEN}})\n" +
                "  .then(function(r){return r.json();})\n" +
                "  .then(function(d){\n" +
                "    if(d&&d.success){\n" +
                "      var msg=d.data.hasFailItems?'⚠️ QC Hoàn Tất: Đã phát hiện hàng Lỗi — đã tạo Incident.':'✅ QC Hoàn Tất: 100% Pass!';\n" +
                "      toast(msg);\n" +
                "      lockUI(msg);\n" +
                "      if(document.getElementById('oi-status')) document.getElementById('oi-status').textContent=d.data.status;\n" +
                "    } else {\n" +
                "      toast((d&&d.message)?d.message:'Lỗi QC Submit',true);\n" +
                "      btn.disabled=false;btn.textContent=' QC HOÀN TẤT CHECK — Gửi kết quả';\n" +
                "    }\n" +
                "  })\n" +
                "  .catch(function(e){toast('Lỗi kết nối: '+e,true);btn.disabled=false;btn.textContent=' QC HOÀN TẤT CHECK — Gửi kết quả';});\n" +
                "}\n" +
                "\n" +
                "// OUTBOUND QC: Pass all / Reject\n" +
                "function updateOutboundQcSummary(){\n" +
                "  var lines=Object.values(lineData);\n" +
                "  var pass=lines.filter(function(l){return l.condition==='PASS';}).length;\n" +
                "  var fail=lines.filter(function(l){return l.condition==='FAIL';}).length;\n" +
                "  var html='<span style=\"color:#22c55e;font-weight:700\">✓ '+pass+' PASS</span>';\n" +
                "  if(fail>0) html+=' &nbsp;<span style=\"color:#ef4444;font-weight:700\">✗ '+fail+' FAIL</span>';\n" +
                "  html+=' / '+lines.length+' đã scan';\n" +
                "  document.getElementById('outbound-qc-summary').innerHTML=html;\n" +
                "  var pb=document.getElementById('qcPassAllBtn');\n" +
                "  if(pb) pb.textContent=fail>0?'⚠️ Vẫn xác nhận PASS tất cả?':' Xác nhận — Tất cả PASS (Cho xuất kho)';\n" +
                "}\n" +
                "function submitOutboundQcResult(allPass){\n" +
                "  if(!TASK_ID){toast('Không tìm thấy Task ID!',true);return;}\n" +
                "  var msg=allPass?'Xác nhận tất cả hàng ĐẠT CHUẨN — cho xuất kho?':'Xác nhận có hàng KHÔNG ĐẠT — tạm dừng xuất kho?';\n" +
                "  if(!confirm(msg)) return;\n" +
                "  var btn=allPass?document.getElementById('qcPassAllBtn'):document.getElementById('qcRejectBtn');\n" +
                "  btn.disabled=true;btn.textContent='Đang gửi...';\n" +
                "  var closeUrl=window.location.origin+'/v1/receiving-sessions/'+SESSION_ID;\n" +
                "  fetch(closeUrl,{method:'DELETE',headers:{'Authorization':'Bearer '+TOKEN}})\n" +
                "  .then(function(r){return r.json();})\n" +
                "  .then(function(d){\n" +
                "    var resultMsg=allPass?'✅ QC xác nhận PASS — Keeper có thể xuất kho!':'⚠️ Đã báo cáo hàng lỗi';\n" +
                "    toast(resultMsg);\n" +
                "    lockUI(resultMsg);\n" +
                "  })\n" +
                "  .catch(function(e){toast('Lỗi: '+e,true);btn.disabled=false;});\n" +
                "}\n" +
                "\n" +
                "// OUTBOUND PICKING: scan confirm + gửi QC\n" +
                "function loadPickItems(){\n" +
                "  fetch(window.location.origin+'/v1/outbound/pick-list/'+TASK_ID,{headers:{'Authorization':'Bearer '+TOKEN}})\n" +
                "  .then(function(r){return r.json();})\n" +
                "  .then(function(d){\n" +
                "    if(d&&d.success&&d.data){\n" +
                "      pickItems=d.data.items||[];\n" +
                "      renderPickItems();\n" +
                "      document.getElementById('picking-status').textContent=pickItems.length+' mặt hàng cần lấy. Scan barcode để xác nhận.';\n" +
                "    } else {\n" +
                "      document.getElementById('picking-status').textContent='Lỗi tải Pick List: '+(d&&d.message?d.message:'unknown');\n" +
                "    }\n" +
                "  })\n" +
                "  .catch(function(e){document.getElementById('picking-status').textContent='Lỗi kết nối: '+e;});\n" +
                "}\n" +
                "function renderPickItems(){\n" +
                "  var html='';\n" +
                "  var allDone=pickItems.length>0;\n" +
                "  var sBase='border-radius:8px;padding:10px 12px;margin-bottom:6px;display:flex;align-items:center;justify-content:space-between';\n" +
                "  for(var i=0;i<pickItems.length;i++){\n" +
                "    var it=pickItems[i];\n" +
                "    var done=!!scannedPickSkus[(it.skuCode||'').toUpperCase()];\n" +
                "    if(!done) allDone=false;\n" +
                "    var bg=done?'background:rgba(16,185,129,.08)':'background:#1e293b';\n" +
                "    var bl=done?'border-left:3px solid #10b981':'border-left:3px solid #334155';\n" +
                "    html+='<div style=\"'+bg+';'+bl+';'+sBase+'\">';\n" +
                "    html+='<div><div style=\"font-size:13px;font-weight:700;color:#e2e8f0\">'+it.skuCode+'</div>';\n" +
                "    var lot=it.lotNumber?' · LOT '+it.lotNumber:'';\n" +
                "    var bc=it.barcode?' · '+it.barcode:'';\n" +
                "    html+='<div style=\"font-size:11px;color:#64748b\">'+it.skuName+' · '+it.locationCode+lot+bc+'</div></div>';\n" +
                "    html+='<div style=\"text-align:right\"><div style=\"font-size:15px;font-weight:800;color:#60a5fa\">&times;'+it.requiredQty+'</div>';\n" +
                "    html+=done?'<div style=\"font-size:10px;color:#10b981;font-weight:700\">✓ ĐÃ LẤY</div>':'<div style=\"font-size:10px;color:#64748b\">Chờ scan</div>';\n" +
                "    html+='</div></div>';\n" +
                "  }\n" +
                "  document.getElementById('picking-items-list').innerHTML=html;\n" +
                "  var btn=document.getElementById('confirmPickedBtn');\n" +
                "  if(btn){btn.disabled=!allDone;btn.style.opacity=allDone?'1':'0.4';}\n" +
                "  var dc=Object.keys(scannedPickSkus).length;\n" +
                "  document.getElementById('picking-status').textContent=dc+'/'+pickItems.length+' SKU đã scan.'+(allDone?' Sẵn sàng gửi QC!':'');\n" +
                "}\n" +
                "function handlePickingScan(input){\n" +
                "  var code=(input||'').trim().toUpperCase();\n" +
                "  var matched=pickItems.filter(function(it){\n" +
                "    return (it.skuCode||'').toUpperCase()===code||(it.barcode||'').toUpperCase()===code;\n" +
                "  });\n" +
                "  if(matched.length===0){toast('Mã '+input+' không có trong Pick List!',true);return;}\n" +
                "  var key=matched[0].skuCode.toUpperCase();\n" +
                "  if(scannedPickSkus[key]){toast('✓ '+matched[0].skuCode+' đã được scan rồi');return;}\n" +
                "  scannedPickSkus[key]=true;\n" +
                "  toast('✓ Xác nhận: '+matched[0].skuCode+(matched[0].barcode?' ['+matched[0].barcode+']':''));\n" +
                "  if(navigator.vibrate) navigator.vibrate(80);\n" +
                "  renderPickItems();\n" +
                "}\n" +
                "function confirmPicked(){\n" +
                "  if(!TASK_ID){toast('Không có Task ID!',true);return;}\n" +
                "  if(!confirm('Xác nhận đã lấy đủ toàn bộ hàng?\\nĐơn sẽ chuyển sang bước QC.')) return;\n" +
                "  var btn=document.getElementById('confirmPickedBtn');\n" +
                "  btn.disabled=true;btn.textContent='Đang gửi...';\n" +
                "  fetch(window.location.origin+'/v1/outbound/pick-list/'+TASK_ID+'/confirm-picked',{\n" +
                "    method:'PATCH',headers:{'Authorization':'Bearer '+TOKEN,'Content-Type':'application/json'}\n" +
                "  })\n" +
                "  .then(function(r){return r.json();})\n" +
                "  .then(function(d){\n" +
                "    if(d&&d.success){\n" +
                "      toast('✅ Đã gửi QC! Chờ QC kiểm tra hàng.');\n" +
                "      lockUI('Keeper đã xác nhận lấy hàng — chờ QC kiểm tra');\n" +
                "    } else {\n" +
                "      toast((d&&d.message)?d.message:'Lỗi confirm picked',true);\n" +
                "      btn.disabled=false;btn.textContent=' Gửi sang QC — Đã lấy đủ hàng';\n" +
                "    }\n" +
                "  })\n" +
                "  .catch(function(e){toast('Lỗi kết nối: '+e,true);btn.disabled=false;btn.textContent=' Gửi sang QC — Đã lấy đủ hàng';});\n" +
                "}\n" +
                "\n" +
                "// ── QC condition toggle ───────────────────────────────────────────\n" +
                "function setQcCondition(cond){\n" +
                "  currentCondition=cond;\n" +
                "  document.getElementById('btn-qc-pass').classList.remove('active');\n" +
                "  document.getElementById('btn-qc-fail').classList.remove('active');\n" +
                "  document.getElementById(cond==='PASS'?'btn-qc-pass':'btn-qc-fail').classList.add('active');\n" +
                "}\n" +
                "\n" +
                "// ── sendBarcode: route to correct handler ─────────────────────────\n" +
                "function sendBarcode(barcode,qty){\n" +
                "  if(SCAN_MODE==='outbound_picking'){handlePickingScan(barcode);return;}\n" +
                "  // Filter URL/long codes (camera picks up QR on screen/poster)\n" +
                "  var lc=(barcode||'').toLowerCase();\n" +
                "  if(lc.indexOf('http')===0||lc.indexOf('//')!==-1||barcode.length>80){\n" +
                "    setStatus('⚠️ Phát hiện QR/URL — đưa barcode sản phẩm vào khung');\n" +
                "    return;\n" +
                "  }\n" +
                "  if(inflight) return;\n" +
                "  inflight=true;\n" +
                "  setStatus('Đang gửi: '+barcode);\n" +
                "  fetch(API,{method:'POST',headers:{'Content-Type':'application/json','Authorization':'Bearer '+TOKEN},body:JSON.stringify({barcode:barcode,qty:qty,condition:currentCondition})})\n" +
                "  .then(function(r){return r.text().then(function(txt){try{return JSON.parse(txt);}catch(e){return {success:false,message:'HTTP '+r.status+': '+txt.substring(0,140)};}});})\n" +
                "  .then(function(d){\n" +
                "    if(d&&d.success){toast('✓ '+d.data.skuCode+' — qty:'+d.data.newQty);updateTable(d.data);document.getElementById('bc').value='';if(navigator.vibrate)navigator.vibrate(60);}\n" +
                "    else{toast((d&&d.message)?d.message:'Lỗi không xác định',true);}\n" +
                "    setStatus('Camera sẵn sàng (QR) — đưa QR vào khung');\n" +
                "  })\n" +
                "  .catch(function(e){toast('Mất kết nối',true);setStatus('Lỗi mạng: '+e);})\n" +
                "  .finally(function(){setTimeout(function(){inflight=false;},600);});\n" +
                "}\n" +
                "\n" +
                "// ── decrementItem ─────────────────────────────────────────────────\n" +
                "function decrementItem(lineKey){\n" +
                "  if(inflight) return;\n" +
                "  var item=lineData[lineKey];\n" +
                "  if(!item||!item.skuId){toast('Không tìm thấy: '+lineKey,true);return;}\n" +
                "  inflight=true;\n" +
                "  var delUrl=window.location.origin+'/v1/scan-events?sessionId='+SESSION_ID+'&skuId='+item.skuId+'&condition='+(item.condition||'PASS')+'&qty=1';\n" +
                "  if(RECEIVING_ID) delUrl+='&receivingId='+RECEIVING_ID;\n" +
                "  fetch(delUrl,{method:'DELETE',headers:{'Authorization':'Bearer '+TOKEN}})\n" +
                "  .then(function(r){return r.json();})\n" +
                "  .then(function(d){\n" +
                "    if(d&&d.success){\n" +
                "      item.qty=parseFloat(item.qty)-1;\n" +
                "      if(item.qty<=0) delete lineData[lineKey];\n" +
                "      renderScanTable();\n" +
                "      toast('−1 '+item.skuCode+' (còn '+(item.qty>0?item.qty:0)+')');\n" +
                "    } else {toast((d&&d.message)?d.message:'Lỗi xóa',true);}\n" +
                "  })\n" +
                "  .catch(function(e){toast('Lỗi kết nối: '+e,true);})\n" +
                "  .finally(function(){setTimeout(function(){inflight=false;},400);});\n" +
                "}\n" +
                "\n" +
                "// ── Manual input ──────────────────────────────────────────────────\n" +
                "function submitManual(){\n" +
                "  var b=(document.getElementById('bc').value||'').trim().toUpperCase();\n" +
                "  var q=parseFloat(document.getElementById('qty').value)||1;\n" +
                "  if(!b){toast('Nhập mã SKU!',true);return;}\n" +
                "  document.getElementById('last').textContent=b;\n" +
                "  sendBarcode(b,q);\n" +
                "}\n" +
                "\n" +
                "// ── Close scan ────────────────────────────────────────────────────\n" +
                "function closeScan(){\n" +
                "  if(!SESSION_ID){toast('Không tìm thấy session',true);return;}\n" +
                "  if(!confirm('Kết thúc phiên scan?')) return;\n" +
                "  fetch(window.location.origin+'/v1/receiving-sessions/'+SESSION_ID,{method:'DELETE',headers:{'Authorization':'Bearer '+TOKEN}})\n" +
                "  .then(function(r){return r.text().then(function(t){try{return JSON.parse(t);}catch(e){return {success:false,message:t};}});})\n" +
                "  .then(function(d){\n" +
                "    if(d&&d.success){toast('Phiên scan đã đóng');stopQr();var btn=document.getElementById('closeBtn');btn.disabled=true;btn.textContent='Đã đóng';setStatus('Phiên scan đã kết thúc');}\n" +
                "    else{toast((d&&d.message)?d.message:'Lỗi đóng session',true);}\n" +
                "  }).catch(function(e){toast('Lỗi kết nối: '+e,true);});\n" +
                "}\n" +
                "\n" +
                "// ── QR camera ────────────────────────────────────────────────────\n" +
                "var qr=null;var qrRunning=false;\n" +
                "function stopQr(){\n" +
                "  try{if(qr&&qrRunning){qr.stop().then(function(){qrRunning=false;}).catch(function(){});}}catch(e){}\n" +
                "}\n" +
                "function waitForHtml5Qrcode(callback,retries){\n" +
                "  if(typeof Html5Qrcode!=='undefined'){callback();return;}\n" +
                "  if(retries<=0){setStatus('Lỗi: Không tải được thư viện QR');return;}\n" +
                "  setTimeout(function(){waitForHtml5Qrcode(callback,retries-1);},200);\n" +
                "}\n" +
                "function startQr(){\n" +
                "  setStatus('Đang khởi động camera...');\n" +
                "  if(window.location.protocol!=='https:'&&window.location.hostname!=='localhost'&&window.location.hostname!=='127.0.0.1'){\n" +
                "    setStatus('Lỗi: Cần HTTPS để truy cập camera');return;\n" +
                "  }\n" +
                "  if(typeof Html5Qrcode==='undefined'){setStatus('Lỗi: Thư viện chưa tải');return;}\n" +
                "  try{\n" +
                "    Html5Qrcode.getCameras().then(function(cameras){\n" +
                "      if(!cameras||!cameras.length){setStatus('Lỗi: Không tìm thấy camera');return;}\n" +
                "      var cameraId=null;\n" +
                "      for(var i=0;i<cameras.length;i++){\n" +
                "        var lbl=cameras[i].label.toLowerCase();\n" +
                "        if(lbl.includes('back')||lbl.includes('rear')||lbl.includes('environment')){cameraId=cameras[i].id;break;}\n" +
                "      }\n" +
                "      if(!cameraId) cameraId=cameras[cameras.length-1].id;\n" +
                "      qr=new Html5Qrcode('reader');\n" +
                "      var cfg={fps:10,qrbox:{width:250,height:250},videoConstraints:{facingMode:'environment'}};\n" +
                "      qr.start(cameraId,cfg,function(decodedText){\n" +
                "        var raw=(decodedText||'').trim();\n" +
                "        // Filter URL-like codes (camera picks up QR from screen/posters)\n" +
                "        var lc=raw.toLowerCase();\n" +
                "        if(lc.indexOf('http')===0||lc.indexOf('//')!==-1||raw.length>80){\n" +
                "          if(SCAN_MODE==='outbound_qc'||SCAN_MODE==='outbound_picking')\n" +
                "            setStatus('⚠️ Phát hiện QR/URL — đưa barcode sản phẩm vào khung');\n" +
                "          return;\n" +
                "        }\n" +
                "        var code=raw.toUpperCase();\n" +
                "        if(code.length<2) return;\n" +
                "        var now=Date.now();\n" +
                "        if(code===lastCode&&(now-lastAt)<1500) return;\n" +
                "        lastCode=code;lastAt=now;\n" +
                "        document.getElementById('last').textContent=code;\n" +
                "        sendBarcode(code,1);\n" +
                "      },function(){}).then(function(){\n" +
                "        qrRunning=true;\n" +
                "        setStatus('Camera sẵn sàng (QR) — đưa QR vào khung');\n" +
                "      }).catch(function(e){\n" +
                "        toast('Không mở được camera: '+e,true);\n" +
                "        setStatus('Camera lỗi: '+e);\n" +
                "      });\n" +
                "    }).catch(function(e){setStatus('Lỗi: '+e);});\n" +
                "  }catch(e){setStatus('Lỗi: '+e);}\n" +
                "}\n" +
                "\n" +
                "// ── DOMContentLoaded: init UI per mode ───────────────────────────\n" +
                "document.addEventListener('DOMContentLoaded',function(){\n" +
                "  // Role badge\n" +
                "  var rb=document.getElementById('role-badge');\n" +
                "  if(rb&&USER_ROLE){rb.textContent=USER_ROLE;rb.classList.add('role-'+USER_ROLE.toLowerCase());}\n" +
                "\n" +
                "  // Show action-card always; configure buttons per mode\n" +
                "  document.getElementById('action-card').style.display='block';\n" +
                "\n" +
                "  if(SCAN_MODE==='outbound_picking'){\n" +
                "    // ── OUTBOUND PICKING ─────────────────────────────────────────\n" +
                "    document.getElementById('picking-card').style.display='block';\n" +
                "    document.getElementById('confirmPickedBtn').style.display='block';\n" +
                "    document.getElementById('qc-mode-indicator').style.display='inline';\n" +
                "    document.getElementById('qc-mode-indicator').textContent='(CHẾ ĐỘ PICKING)';\n" +
                "    if(TASK_ID) loadPickItems();\n" +
                "\n" +
                "  } else if(SCAN_MODE==='outbound_qc'){\n" +
                "    // ── OUTBOUND QC ──────────────────────────────────────────────\n" +
                "    document.getElementById('qc-toggle-panel').style.display='flex';\n" +
                "    document.getElementById('qc-mode-indicator').style.display='inline';\n" +
                "    document.getElementById('qc-mode-indicator').textContent='(CHẾ ĐỘ QC XUẤT KHO)';\n" +
                "    document.getElementById('outbound-qc-summary').style.display='block';\n" +
                "    document.getElementById('qcPassAllBtn').style.display='block';\n" +
                "    document.getElementById('qcRejectBtn').style.display='block';\n" +
                "    updateOutboundQcSummary();\n" +
                "\n" +
                "  } else {\n" +
                "    // ── INBOUND (default) ────────────────────────────────────────\n" +
                "    document.getElementById('recv-card').style.display='block';\n" +
                "    if(RECEIVING_ID!==null&&RECEIVING_ID!==undefined){\n" +
                "      document.getElementById('recv-id-label').textContent='#'+RECEIVING_ID;\n" +
                "      document.getElementById('recv-display').style.display='block';\n" +
                "      document.getElementById('recv-manual').style.display='none';\n" +
                "      loadOrderDetails();\n" +
                "    } else {\n" +
                "      document.getElementById('recv-display').style.display='none';\n" +
                "      document.getElementById('recv-manual').style.display='block';\n" +
                "    }\n" +
                "    if(USER_ROLE==='QC'){\n" +
                "      // QC inbound: PASS/FAIL toggle + QC HOÀN TẤT CHECK\n" +
                "      document.getElementById('qc-toggle-panel').style.display='flex';\n" +
                "      document.getElementById('qc-mode-indicator').style.display='inline';\n" +
                "      document.getElementById('qc-mode-indicator').textContent='(CHẾ ĐỘ QC - RESCAN)';\n" +
                "      document.getElementById('qcSubmitBtn').style.display='block';\n" +
                "    } else {\n" +
                "      // KEEPER inbound: Xác nhận kiểm đếm\n" +
                "      document.getElementById('confirmBtn').style.display='block';\n" +
                "    }\n" +
                "  }\n" +
                "\n" +
                "  // Event listeners\n" +
                "  document.getElementById('manualBtn').addEventListener('click',submitManual);\n" +
                "  document.getElementById('closeBtn').addEventListener('click',closeScan);\n" +
                "  document.getElementById('confirmBtn').addEventListener('click',confirmAndSubmit);\n" +
                "  document.getElementById('qcSubmitBtn').addEventListener('click',qcSubmit);\n" +
                "  document.getElementById('bc').addEventListener('keydown',function(e){if(e.key==='Enter')submitManual();});\n" +
                "});\n" +
                "window.addEventListener('load',function(){\n" +
                "  waitForHtml5Qrcode(startQr,25);\n" +
                "});\n" +
                "</script></body></html>";
    }
}
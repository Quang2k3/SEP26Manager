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
    @Operation(summary = "Lấy URL trang scan", description = "Trả về URL đầy đủ để mở trang quét barcode trên iPhone. Dùng cho QR code.\n\n"
            + "**Data yêu cầu:**\n"
            + "- `token`: JWT scan token, lấy từ `POST /v1/receiving-sessions/{sessionId}/scan-token`.\n"
            + "- `receivingId` *(Tùy chọn)*: ID Phiếu Nhận Hàng (GRN), lấy từ response của `POST /v1/receiving-sessions/{sessionId}/create-grn` (field `receivingId`). "
            + "Nếu truyền vào, trang scan sẽ **tự động hiển thị** ID phiếu nhận mà không cần nhập tay.")
    public String getScanUrl(@RequestParam("token") String token,
                             @RequestParam(value = "receivingId", required = false) Long receivingId,
                             HttpServletRequest request) {
        String base = request.getScheme() + "://" + request.getServerName()
                + (request.getServerPort() == 80 || request.getServerPort() == 443 ? ""
                : ":" + request.getServerPort());
        String url = base + "/v1/scan?token=" + token + "&v=qr3";
        if (receivingId != null) {
            url += "&receivingId=" + receivingId;
        }
        return url;
    }

    @GetMapping(value = "/v1/scan", produces = MediaType.TEXT_HTML_VALUE)
    @Operation(summary = "Trang quét barcode (HTML)", description = "Trả về trang HTML với camera QR scanner. iPhone mở trang này để quét barcode và gửi scan event.\n\n"
            + "**Data yêu cầu:**\n"
            + "- `token`: JWT scan token.\n"
            + "- `receivingId` *(Tùy chọn)*: Nếu được truyền qua URL (do FE nhúng từ bước `create-grn`), "
            + "trường **PHIẾU NHẬN HÀNG** trên trang sẽ **tự động điền sẵn** — người dùng không cần nhập tay nữa.")
    public ResponseEntity<String> scannerPage(@RequestParam("token") String token,
                                              @RequestParam(value = "receivingId", required = false) Long receivingId) {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
                .header("Pragma", "no-cache")
                .body(buildHtml(escapeForJs(token), receivingId));
    }

    private static String escapeForJs(String s) {
        if (s == null)
            return "";
        return s.replace("\\", "\\\\").replace("'", "\\'");
    }

    private String buildHtml(String token, Long receivingId) {
        String receivingIdJs = receivingId != null ? String.valueOf(receivingId) : "null";
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
                ".badge{border-radius:20px;padding:3px 12px;font-size:12px;font-weight:700}" +
                ".badge-role{background:#22c55e;color:#fff;text-transform:uppercase;letter-spacing:.05em}" +
                ".badge-role.role-keeper{background:#22c55e}" +
                ".badge-role.role-qc{background:#f59e0b;color:#000}" +
                ".badge-role.role-manager{background:#a855f7}" +
                ".badge-role.role-admin{background:#ef4444}" +
                ".badge-role.role-staff{background:#3b82f6}" +
                ".badge-cnt{background:#dbeafe;color:#1e3a8a}"
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
                ".btn.success{background:linear-gradient(135deg,#22c55e,#16a34a);width:100%;font-size:17px;padding:14px 18px;margin-top:10px}"
                +
                ".btn.success:disabled{opacity:.5;cursor:not-allowed}" +

                "table{width:100%;border-collapse:collapse;font-size:13px}" +
                "th{text-align:left;color:#475569;padding:5px 4px;font-size:11px;font-weight:600}" +
                "td{padding:9px 4px;border-bottom:1px solid #263347}" +
                ".qc{text-align:right;font-weight:800;color:#34d399;font-size:15px}" +
                ".sc{color:#94a3b8;font-size:11px}" +

                // Order info styles
                ".order-info{display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-bottom:8px}" +
                ".order-info-item{background:#0f172a;border-radius:8px;padding:10px}" +
                ".order-info-label{font-size:10px;color:#64748b;text-transform:uppercase;letter-spacing:.05em}" +
                ".order-info-value{font-size:14px;font-weight:700;color:#e2e8f0;margin-top:2px}" +
                ".order-info-item.full{grid-column:1/-1}" +

                // Comparison table styles
                ".status-match{color:#22c55e;font-weight:800}" +
                ".status-mismatch{color:#ef4444;font-weight:800}" +
                ".status-pending{color:#64748b}" +
                ".expected-col{text-align:center;color:#94a3b8;font-size:12px}" +
                ".received-col{text-align:center;font-weight:800;font-size:14px}" +
                ".loading-spinner{text-align:center;padding:20px;color:#64748b}" +

                ".toast{position:fixed;bottom:28px;left:50%;transform:translateX(-50%);background:#10b981;color:#fff;padding:11px 24px;border-radius:28px;font-weight:800;font-size:14px;display:none;white-space:nowrap;box-shadow:0 4px 24px rgba(0,0,0,.5)}"
                +
                ".toast.err{background:#ef4444}" +
                ".qc-toggle-group { display:flex; gap:8px; margin-bottom:12px; }\n" +
                ".qc-toggle-btn { flex:1; padding:8px 12px; border-radius:6px; border:2px solid #334155; background:transparent; color:#94a3b8; font-weight:700; font-size:12px; cursor:pointer; transition:all 0.2s auto; opacity:0.6; }\n"
                +
                ".qc-toggle-btn.active.pass { border-color:#10b981; background:rgba(16,185,129,0.1); color:#10b981; opacity:1; }\n"
                +
                ".qc-toggle-btn.active.fail { border-color:#ef4444; background:rgba(239,68,68,0.1); color:#ef4444; opacity:1; }\n"
                +
                "</style></head><body>" +

                "<header><span style='font-size:20px'>📦</span><h1>Warehouse Scanner</h1><span class='badge badge-role' id='role-badge'></span><span class='badge badge-cnt' id='cnt'>0 dòng</span></header>"
                +

                "<div class='container'>" +
                "<div id='cam-wrap'><div id='reader'></div><div id='scan-line'></div></div>" +
                "<div id='cam-status'>Đang khởi động camera QR…</div>" +

                // ── PHIẾU NHẬN HÀNG card ──
                "<div class='card'>" +
                "<div class='card-title'>Phiếu Nhận Hàng</div>" +
                "<div id='recv-display' style='display:none;padding:10px 14px;background:#0f172a;border-radius:8px;border:1.5px solid #22c55e'>"
                +
                "  <span style='color:#64748b;font-size:12px'>ID Phiếu Nhận</span>" +
                "  <div style='font-size:22px;font-weight:800;color:#22c55e;margin-top:2px' id='recv-id-label'></div>" +
                "</div>" +
                "<div id='recv-manual' style='display:none'>" +
                "  <input type='number' id='recv-input' placeholder='Nhập ID Phiếu Nhận ...' style='width:100%'/>" +
                "  <div style='color:#f59e0b;font-size:11px;margin-top:6px'>⚠️ Không tìm thấy ID phiếu trong URL. Vui lòng nhập tay.</div>"
                +
                "</div>" +
                "</div>" +

                // ── THÔNG TIN ĐƠN HÀNG card ── (chỉ hiện khi có receivingId)
                "<div class='card' id='order-info-card' style='display:none'>" +
                "<div class='card-title'>📋 Thông tin đơn hàng</div>" +
                "<div id='order-loading' class='loading-spinner'>Đang tải thông tin đơn hàng...</div>" +
                "<div id='order-info-content' style='display:none'>" +
                "  <div class='order-info'>" +
                "    <div class='order-info-item'><div class='order-info-label'>Mã phiếu</div><div class='order-info-value' id='oi-code'>—</div></div>"
                +
                "    <div class='order-info-item'><div class='order-info-label'>Trạng thái</div><div class='order-info-value' id='oi-status'>—</div></div>"
                +
                "    <div class='order-info-item'><div class='order-info-label'>Nhà cung cấp</div><div class='order-info-value' id='oi-supplier'>—</div></div>"
                +
                "    <div class='order-info-item'><div class='order-info-label'>Dự kiến</div><div class='order-info-value' id='oi-expected'>—</div></div>"
                +
                "  </div>" +
                "  <div style='color:#64748b;font-size:11px;margin-top:4px' id='oi-note'></div>" +
                "</div>" +
                "</div>" +

                // ── BẢNG KIỂM ĐẾM (Dự kiến vs Thực nhận) ──
                "<div class='card' id='comparison-card' style='display:none'>" +
                "<div class='card-title'>📊 Kiểm đếm (Dự kiến vs Thực nhận)</div>" +
                "<table>" +
                "<thead><tr><th>SKU</th><th>Tên SP</th><th style='text-align:center'>Dự kiến</th><th style='text-align:center'>Thực nhận</th><th style='text-align:center'>KQ</th></tr></thead>"
                +
                "<tbody id='comparison-lines'></tbody>" +
                "</table>" +
                "<div style='margin-top:10px;display:flex;gap:8px;justify-content:space-between'>" +
                "  <div style='font-size:12px;color:#64748b'>Tổng dòng: <b id='comp-total' style='color:#e2e8f0'>0</b></div>"
                +
                "  <div style='font-size:12px'>✓ <span id='comp-match' style='color:#22c55e;font-weight:700'>0</span> &nbsp; ✗ <span id='comp-mismatch' style='color:#ef4444;font-weight:700'>0</span></div>"
                +
                "</div>" +
                "</div>" +

                "<div class='card'>" +
                "<div class='card-title'>Quét QR <span id='qc-mode-indicator' style='display:none;font-weight:700;color:#f59e0b;'>(CHẾ ĐỘ QC - RESCAN)</span></div>"
                +
                "<div id='qc-toggle-panel' class='qc-toggle-group' style='display:none'>" +
                "  <button id='btn-qc-pass' class='qc-toggle-btn active pass' onclick='setQcCondition(\"PASS\")'>✓ Hàng Tốt (PASS)</button>"
                +
                "  <button id='btn-qc-fail' class='qc-toggle-btn fail' onclick='setQcCondition(\"FAIL\")'>✗ Hàng Lỗi (FAIL)</button>"
                +
                "</div>" +
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
                "<div class='card-title'>Đã scan (phiên hiện tại)</div>" +
                "<table><thead><tr><th>SKU</th><th>Tên sản phẩm</th><th style='text-align:right'>Qty</th></tr></thead>"
                +
                "<tbody id='lines'></tbody></table>" +
                "</div>" +

                // ── NÚT XÁC NHẬN KIỂM ĐẾM (chỉ hiện khi có receivingId) ──
                "<div class='card' id='confirm-card' style='display:none'>" +
                "<button class='btn success' id='confirmBtn'>✅ Xác nhận kiểm đếm — Gửi QC</button>" +
                "<button class='btn warning' id='qcSubmitBtn' style='display:none; color:#fff;'>✅ QC HOÀN TẤT CHECK</button>"
                +
                "</div>" +

                "<div class='card'><button class='btn danger' id='closeBtn'>🛑 Kết thúc Scan</button></div>" +
                "</div>" +

                "<div class='toast' id='toast'></div>" +

                "<script>\n" +
                "var TOKEN='" + token + "';\n" +
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
                "function getRoleFromToken(){try{var p=TOKEN.split('.')[1];var d=JSON.parse(base64UrlDecode(p));return (d.roles||'').split(',')[0].trim().toUpperCase()||'UNKNOWN';}catch(e){return 'UNKNOWN';}} \n"
                +
                "var SESSION_ID=getSessionId();\n" +
                "var USER_ROLE=getRoleFromToken();\n" +

                "function toast(msg,err){var t=document.getElementById('toast');t.textContent=msg;t.className='toast'+(err?' err':'');t.style.display='block';clearTimeout(t._t);t._t=setTimeout(function(){t.style.display='none';},2600);} \n"
                +
                "function setStatus(msg){document.getElementById('cam-status').textContent=msg;} \n" +

                "function updateTable(d){\n" +
                "  lineData[d.skuCode]={name:d.skuName||'',qty:d.newQty};\n" +
                "  var rows='';\n" +
                "  for(var k in lineData){rows+='<tr><td class=\"sc\">'+k+'</td><td>'+lineData[k].name+'</td><td class=\"qc\">'+lineData[k].qty+'</td></tr>';}\n"
                +
                "  document.getElementById('lines').innerHTML=rows;\n" +
                "  document.getElementById('cnt').textContent=Object.keys(lineData).length+' dòng';\n" +
                "  updateComparisonTable();\n" +
                "} \n" +

                // ── Load order details from API ──
                "function loadOrderDetails(){\n" +
                "  if(!RECEIVING_ID) return;\n" +
                "  document.getElementById('order-info-card').style.display='block';\n" +
                "  document.getElementById('comparison-card').style.display='block';\n" +
                "  document.getElementById('confirm-card').style.display='block';\n" +
                "  fetch(ORDER_API+'/'+RECEIVING_ID,{headers:{'Authorization':'Bearer '+TOKEN}})\n" +
                "  .then(function(r){return r.json();})\n" +
                "  .then(function(resp){\n" +
                "    if(resp && resp.success && resp.data){\n" +
                "      orderData=resp.data;\n" +
                "      orderItems=resp.data.items||[];\n" +
                "      document.getElementById('order-loading').style.display='none';\n" +
                "      document.getElementById('order-info-content').style.display='block';\n" +
                "      document.getElementById('oi-code').textContent=orderData.receivingCode||'N/A';\n" +
                "      document.getElementById('oi-status').textContent=orderData.status||'N/A';\n" +
                "      document.getElementById('oi-supplier').textContent=orderData.supplierName||'N/A';\n" +
                "      document.getElementById('oi-expected').textContent=(orderData.totalExpectedQty||0)+' sp / '+(orderData.totalLines||0)+' dòng';\n"
                +
                "      if(orderData.note){document.getElementById('oi-note').textContent='📝 '+orderData.note;}\n" +
                "      updateComparisonTable();\n" +
                "    } else {\n" +
                "      document.getElementById('order-loading').textContent='Không tải được thông tin đơn: '+(resp&&resp.message?resp.message:'Lỗi');\n"
                +
                "    }\n" +
                "  })\n" +
                "  .catch(function(e){document.getElementById('order-loading').textContent='Lỗi kết nối: '+e;});\n" +
                "} \n" +

                // ── Update comparison table (expected vs scanned) ──
                "function updateComparisonTable(){\n" +
                "  if(!orderItems.length) return;\n" +
                "  var rows='';\n" +
                "  var matchCount=0, mismatchCount=0;\n" +
                "  for(var i=0;i<orderItems.length;i++){\n" +
                "    var item=orderItems[i];\n" +
                "    var sku=item.skuCode||'';\n" +
                "    var name=item.skuName||'';\n" +
                "    var expected=parseFloat(item.expectedQty)||0;\n" +
                "    var scanned=(lineData[sku])?parseFloat(lineData[sku].qty)||0:0;\n" +
                "    var statusCls='status-pending', statusTxt='⏳';\n" +
                "    if(scanned>0){\n" +
                "      if(scanned>=expected){statusCls='status-match';statusTxt='✓';matchCount++;}\n" +
                "      else{statusCls='status-mismatch';statusTxt='✗ '+scanned+'/'+expected;mismatchCount++;}\n" +
                "    }\n" +
                "    rows+='<tr><td class=\"sc\">'+sku+'</td><td>'+name+'</td><td class=\"expected-col\">'+expected+'</td><td class=\"received-col\">'+scanned+'</td><td class=\"'+statusCls+'\" style=\"text-align:center\">'+statusTxt+'</td></tr>';\n"
                +
                "  }\n" +
                "  document.getElementById('comparison-lines').innerHTML=rows;\n" +
                "  document.getElementById('comp-total').textContent=orderItems.length;\n" +
                "  document.getElementById('comp-match').textContent=matchCount;\n" +
                "  document.getElementById('comp-mismatch').textContent=(orderItems.length-matchCount);\n" +
                "} \n" +

                // ── Confirm and submit to QC ──
                "function confirmAndSubmit(){\n" +
                "  if(!RECEIVING_ID){toast('Không có ID phiếu!',true);return;}\n" +
                "  if(!confirm('Xác nhận kiểm đếm xong?\\nPhiếu sẽ được gửi cho QC kiểm tra chất lượng.')) return;\n" +
                "  var btn=document.getElementById('confirmBtn');\n" +
                "  btn.disabled=true;btn.textContent='Đang gửi...';\n" +
                "  fetch(ORDER_API+'/'+RECEIVING_ID+'/submit',{method:'POST',headers:{'Authorization':'Bearer '+TOKEN}})\n"
                +
                "  .then(function(r){return r.json();})\n" +
                "  .then(function(d){\n" +
                "    if(d && d.success){\n" +
                "      toast('✅ Đã gửi QC kiểm tra!');\n" +
                "      btn.textContent='✅ Đã gửi QC';\n" +
                "      btn.style.background='#475569';\n" +
                "      if(document.getElementById('oi-status')){document.getElementById('oi-status').textContent='SUBMITTED';}\n"
                +
                "    } else {\n" +
                "      toast((d&&d.message)?d.message:'Lỗi submit',true);\n" +
                "      btn.disabled=false;btn.textContent='✅ Xác nhận kiểm đếm — Gửi QC';\n" +
                "    }\n" +
                "  })\n" +
                "  .catch(function(e){toast('Lỗi kết nối: '+e,true);btn.disabled=false;btn.textContent='✅ Xác nhận kiểm đếm — Gửi QC';});\n"
                +
                "} \n" +

                // ── QC Submit Session ──
                "function qcSubmit(){\n" +
                "  if(!RECEIVING_ID){toast('Không có ID phiếu!',true);return;}\n" +
                "  if(!confirm('Hoàn tất quét duyệt QC?\\n(Sẽ đối chiếu với kết quả của Keeper)')) return;\n" +
                "  var btn=document.getElementById('qcSubmitBtn');\n" +
                "  btn.disabled=true;btn.textContent='Đang xử lý...';\n" +
                "  var url = ORDER_API+'/'+RECEIVING_ID+'/qc-submit-session?sessionId='+SESSION_ID;\n" +
                "  fetch(url,{method:'POST',headers:{'Authorization':'Bearer '+TOKEN}})\n" +
                "  .then(function(r){return r.json();})\n" +
                "  .then(function(d){\n" +
                "    if(d && d.success){\n" +
                "      var msg = d.data.hasFailItems ? '⚠️ QC Hoàn Tấn: Đã phát hiện hàng Lỗi. Đã chuyển tạo Incident.' : '✅ QC Hoàn Tất: 100% Pass!';\n"
                +
                "      toast(msg);\n" +
                "      btn.textContent='Đã Đóng Session';\n" +
                "      btn.style.background='#475569';\n" +
                "      if(document.getElementById('oi-status')){document.getElementById('oi-status').textContent=d.data.status;}\n"
                +
                "      setTimeout(function(){window.location.reload();}, 2000);\n" +
                "    } else {\n" +
                "      toast((d&&d.message)?d.message:'Lỗi QC Submit',true);\n" +
                "      btn.disabled=false;btn.textContent='✅ QC HOÀN TẤT CHECK';\n" +
                "    }\n" +
                "  })\n" +
                "  .catch(function(e){toast('Lỗi kết nối: '+e,true);btn.disabled=false;btn.textContent='✅ QC HOÀN TẤT CHECK';});\n"
                +
                "} \n" +

                "function setQcCondition(cond){\n" +
                "  currentCondition = cond;\n" +
                "  document.getElementById('btn-qc-pass').classList.remove('active');\n" +
                "  document.getElementById('btn-qc-fail').classList.remove('active');\n" +
                "  if(cond==='PASS'){ document.getElementById('btn-qc-pass').classList.add('active'); }\n" +
                "  else { document.getElementById('btn-qc-fail').classList.add('active'); }\n" +
                "}\n" +

                "function sendBarcode(barcode,qty){\n" +
                "  if(inflight) return;\n" +
                "  inflight=true;\n" +
                "  setStatus('Đang gửi: '+barcode);\n" +
                "  fetch(API,{method:'POST',headers:{'Content-Type':'application/json','Authorization':'Bearer '+TOKEN},body:JSON.stringify({barcode:barcode,qty:qty,condition:currentCondition,receivingId:RECEIVING_ID})})\n"
                +
                "  .then(function(r){return r.text().then(function(txt){try{return JSON.parse(txt);}catch(e){return {success:false,message:'HTTP '+r.status+': '+txt.substring(0,140)};}});})\n"
                +
                "  .then(function(d){\n" +
                "    console.log(\'Scan resp:\', JSON.stringify(d));\\n" +
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
                "  // Display role badge\n" +
                "  var roleBadge=document.getElementById('role-badge');\n" +
                "  if(roleBadge && USER_ROLE){\n" +
                "      roleBadge.textContent=USER_ROLE;roleBadge.classList.add('role-'+USER_ROLE.toLowerCase());\n" +
                "      if(USER_ROLE.toUpperCase() === 'QC') {\n" +
                "          document.getElementById('qc-toggle-panel').style.display = 'flex';\n" +
                "          document.getElementById('qc-mode-indicator').style.display = 'inline';\n" +
                "          document.getElementById('confirmBtn').style.display = 'none';\n" +
                "          document.getElementById('qcSubmitBtn').style.display = 'block';\n" +
                "      }\n" +
                "  }\n" +
                "  // Auto-populate receiving ID từ URL param (truyền qua khi FE tạo scan URL)\n" +
                "  if(RECEIVING_ID !== null && RECEIVING_ID !== undefined){\n" +
                "    document.getElementById('recv-id-label').textContent='#'+RECEIVING_ID;\n" +
                "    document.getElementById('recv-display').style.display='block';\n" +
                "    document.getElementById('recv-manual').style.display='none';\n" +
                "    loadOrderDetails();\n" +
                "    document.getElementById('confirm-card').style.display='block';\n" +
                "  } else {\n" +
                "    document.getElementById('recv-display').style.display='none';\n" +
                "    document.getElementById('recv-manual').style.display='block';\n" +
                "  }\n" +
                "  document.getElementById('manualBtn').addEventListener('click', submitManual);\n" +
                "  document.getElementById('closeBtn').addEventListener('click', closeScan);\n" +
                "  document.getElementById('confirmBtn').addEventListener('click', confirmAndSubmit);\n" +
                "  document.getElementById('qcSubmitBtn').addEventListener('click', qcSubmit);\n" +
                "  document.getElementById('bc').addEventListener('keydown', function(e){ if(e.key==='Enter') submitManual(); });\n"
                +
                "});\n" +
                "window.addEventListener('load', function(){\n" +
                "  waitForHtml5Qrcode(startQr, 25);\n" +
                "});\n" +

                "</script></body></html>";
    }
}
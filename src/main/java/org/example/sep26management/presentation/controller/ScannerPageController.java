package org.example.sep26management.presentation.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the barcode scanner HTML page for the iPhone / any mobile browser.
 * URL: GET /v1/scan?token={SCAN_TOKEN}
 * The page uses html5-qrcode to activate the camera and POST to
 * /v1/scan-events.
 */
@RestController
public class ScannerPageController {

    @GetMapping(value = "/v1/scan", produces = MediaType.TEXT_HTML_VALUE)
    public String scannerPage(@RequestParam("token") String token) {
        // Use plain string concat to avoid text-block / backslash issues
        return "<!DOCTYPE html>\n" +
                "<html lang='vi'>\n" +
                "<head>\n" +
                "  <meta charset='UTF-8'>\n" +
                "  <meta name='viewport' content='width=device-width, initial-scale=1.0, user-scalable=no'>\n" +
                "  <title>Warehouse Scanner</title>\n" +
                "  <script src='https://unpkg.com/html5-qrcode@2.3.8/html5-qrcode.min.js'></script>\n" +
                "  <style>\n" +
                "    * { box-sizing:border-box; margin:0; padding:0; }\n" +
                "    body { font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif; background:#0f172a; color:#e2e8f0; min-height:100vh; }\n"
                +
                "    header { background:#1e40af; padding:16px; display:flex; align-items:center; gap:12px; }\n" +
                "    header h1 { font-size:18px; font-weight:700; }\n" +
                "    .badge { background:#dbeafe; color:#1e3a8a; border-radius:20px; padding:4px 12px; font-size:12px; font-weight:600; margin-left:auto; }\n"
                +
                "    .container { padding:16px; max-width:480px; margin:0 auto; }\n" +
                "    #reader { border-radius:12px; overflow:hidden; border:2px solid #3b82f6; margin-bottom:16px; }\n" +
                "    .card { background:#1e293b; border-radius:12px; padding:16px; margin-bottom:12px; }\n" +
                "    .card h2 { font-size:13px; color:#94a3b8; margin-bottom:12px; text-transform:uppercase; letter-spacing:0.05em; }\n"
                +
                "    .row { display:flex; gap:8px; margin-bottom:8px; }\n" +
                "    input { flex:1; padding:10px 14px; background:#0f172a; border:1px solid #334155; border-radius:8px; color:#e2e8f0; font-size:16px; }\n"
                +
                "    input:focus { outline:none; border-color:#3b82f6; }\n" +
                "    .qty { max-width:80px; }\n" +
                "    button { padding:10px 18px; border:none; border-radius:8px; font-size:15px; font-weight:600; cursor:pointer; }\n"
                +
                "    .btn-blue { background:#3b82f6; color:#fff; }\n" +
                "    .toast { position:fixed; bottom:24px; left:50%; transform:translateX(-50%); background:#10b981; color:#fff; padding:12px 24px; border-radius:24px; font-weight:700; font-size:15px; display:none; white-space:nowrap; box-shadow:0 4px 20px rgba(0,0,0,.4); }\n"
                +
                "    .toast.err { background:#ef4444; }\n" +
                "    table { width:100%; border-collapse:collapse; font-size:14px; }\n" +
                "    th { text-align:left; color:#64748b; padding:6px 4px; font-size:12px; }\n" +
                "    td { padding:8px 4px; border-bottom:1px solid #334155; }\n" +
                "    td:last-child { text-align:right; font-weight:700; color:#34d399; }\n" +
                "    #status { font-size:12px; color:#64748b; text-align:center; margin-top:8px; }\n" +
                "  </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "<header>\n" +
                "  <span>📦</span>\n" +
                "  <h1>Warehouse Scanner</h1>\n" +
                "  <div class='badge' id='count'>0 items</div>\n" +
                "</header>\n" +
                "<div class='container'>\n" +
                "  <div id='reader'></div>\n" +
                "  <div class='card'>\n" +
                "    <h2>Nhập thủ công (không cần camera)</h2>\n" +
                "    <div class='row'>\n" +
                "      <input type='text' id='barcodeInput' placeholder='Barcode' />\n" +
                "      <input type='number' id='qtyInput' class='qty' value='1' min='0.01' step='0.01' />\n" +
                "      <button class='btn-blue' onclick='submitScan()'>Scan</button>\n" +
                "    </div>\n" +
                "    <p id='status'>Camera chưa khởi động</p>\n" +
                "  </div>\n" +
                "  <div class='card'>\n" +
                "    <h2>Danh sách đã scan</h2>\n" +
                "    <table><thead><tr><th>SKU</th><th>Tên</th><th>Qty</th></tr></thead><tbody id='lineTable'></tbody></table>\n"
                +
                "  </div>\n" +
                "</div>\n" +
                "<div class='toast' id='toast'></div>\n" +
                "<script>\n" +
                "  const SCAN_TOKEN = '" + token + "';\n" +
                "  const SCAN_API = window.location.origin + '/api/v1/scan-events';\n" +
                "  let scanning = false;\n" +
                "  let html5QrCode;\n" +
                "\n" +
                "  window.addEventListener('load', () => {\n" +
                "    html5QrCode = new Html5Qrcode('reader');\n" +
                "    Html5Qrcode.getCameras().then(cameras => {\n" +
                "      if (!cameras.length) { setStatus('Không tìm thấy camera — dùng nhập tay'); return; }\n" +
                "      const cam = cameras.find(c => /back|rear|environment/i.test(c.label)) || cameras[cameras.length-1];\n"
                +
                "      html5QrCode.start(cam.id, { fps:10, qrbox:{width:280,height:200} },\n" +
                "        decodedText => {\n" +
                "          if (!scanning) { scanning=true; submitScanWithBarcode(decodedText,1); setTimeout(()=>{scanning=false;},1500); }\n"
                +
                "        }, () => {}\n" +
                "      ).then(()=>setStatus('✅ Camera sẵn sàng — hướng vào barcode'))\n" +
                "       .catch(err=>setStatus('⚠️ Camera lỗi: '+err+' — dùng nhập tay'));\n" +
                "    }).catch(err=>setStatus('Camera không khả dụng: '+err));\n" +
                "  });\n" +
                "\n" +
                "  document.addEventListener('DOMContentLoaded', ()=>{\n" +
                "    document.getElementById('barcodeInput').addEventListener('keydown', e=>{ if(e.key==='Enter') submitScan(); });\n"
                +
                "  });\n" +
                "\n" +
                "  function submitScan() {\n" +
                "    const b=document.getElementById('barcodeInput').value.trim();\n" +
                "    const q=parseFloat(document.getElementById('qtyInput').value)||1;\n" +
                "    if(!b){showToast('Nhập barcode!',true);return;}\n" +
                "    submitScanWithBarcode(b,q);\n" +
                "  }\n" +
                "\n" +
                "  function submitScanWithBarcode(barcode,qty) {\n" +
                "    setStatus('Đang gửi: '+barcode);\n" +
                "    fetch(SCAN_API,{\n" +
                "      method:'POST',\n" +
                "      headers:{'Content-Type':'application/json','Authorization':'Bearer '+SCAN_TOKEN},\n" +
                "      body:JSON.stringify({barcode,qty})\n" +
                "    }).then(r=>r.json()).then(data=>{\n" +
                "      if(data.success){\n" +
                "        showToast('✅ '+data.data.skuCode+' — qty: '+data.data.newQty);\n" +
                "        updateTable(data.data);\n" +
                "        document.getElementById('barcodeInput').value='';\n" +
                "        setStatus('✅ Camera sẵn sàng');\n" +
                "      } else {\n" +
                "        showToast('❌ '+(data.message||'Lỗi'),true);\n" +
                "        setStatus('❌ '+data.message);\n" +
                "      }\n" +
                "    }).catch(err=>{ showToast('❌ Mất kết nối',true); setStatus('Lỗi: '+err); });\n" +
                "  }\n" +
                "\n" +
                "  const lineData={};\n" +
                "  function updateTable(item){\n" +
                "    lineData[item.skuCode]={name:item.skuName||'',qty:item.newQty};\n" +
                "    const tbody=document.getElementById('lineTable');\n" +
                "    tbody.innerHTML=Object.entries(lineData).map(([c,d])=>`<tr><td>${c}</td><td>${d.name}</td><td>${d.qty}</td></tr>`).join('');\n"
                +
                "    document.getElementById('count').textContent=Object.keys(lineData).length+' items';\n" +
                "  }\n" +
                "\n" +
                "  function showToast(msg,isError=false){\n" +
                "    const t=document.getElementById('toast');\n" +
                "    t.textContent=msg; t.className='toast'+(isError?' err':''); t.style.display='block';\n" +
                "    setTimeout(()=>{t.style.display='none';},2500);\n" +
                "  }\n" +
                "\n" +
                "  function setStatus(msg){document.getElementById('status').textContent=msg;}\n" +
                "</script>\n" +
                "</body></html>";
    }
}

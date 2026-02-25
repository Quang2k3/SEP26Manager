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
        String safeToken = token.replace("'", "\\'");
        return buildHtml(safeToken);
    }

    private String buildHtml(String token) {
        StringBuilder sb = new StringBuilder();

        sb.append("<!DOCTYPE html>");
        sb.append("<html lang='vi'>");
        sb.append("<head>");
        sb.append("<meta charset='UTF-8'>");
        sb.append("<meta name='viewport' content='width=device-width, initial-scale=1.0, user-scalable=no'>");
        sb.append("<title>Warehouse Scanner</title>");
        sb.append("<script src='https://unpkg.com/@zxing/library@0.21.3/umd/index.min.js'></script>");

        sb.append("<style>");
        sb.append("* { box-sizing:border-box; margin:0; padding:0; }");
        sb.append(
                "body { font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif; background:#0f172a; color:#e2e8f0; min-height:100vh; }");
        sb.append("header { background:#1e40af; padding:16px; display:flex; align-items:center; gap:12px; }");
        sb.append("header h1 { font-size:18px; font-weight:700; }");
        sb.append(
                ".badge { background:#dbeafe; color:#1e3a8a; border-radius:20px; padding:4px 12px; font-size:12px; font-weight:600; margin-left:auto; }");
        sb.append(".container { padding:16px; max-width:480px; margin:0 auto; }");
        sb.append(
                "#reader { position:relative; border-radius:12px; overflow:hidden; border:2px solid #3b82f6; margin-bottom:16px; background:#000; }");
        sb.append("#video { width:100%; display:block; background:#000; }");
        sb.append(".scan-guide { position:absolute; inset:0; pointer-events:none; }");
        sb.append(
                ".scan-box { position:absolute; left:8%; right:8%; top:38%; height:24%; border:2px solid rgba(59,130,246,.9); border-radius:8px; }");
        sb.append(
                ".scan-line { position:absolute; left:10%; right:10%; top:50%; height:2px; background:rgba(16,185,129,.9); }");
        sb.append(".card { background:#1e293b; border-radius:12px; padding:16px; margin-bottom:12px; }");
        sb.append(
                ".card h2 { font-size:13px; color:#94a3b8; margin-bottom:12px; text-transform:uppercase; letter-spacing:0.05em; }");
        sb.append(".row { display:flex; gap:8px; margin-bottom:8px; }");
        sb.append(
                "input { flex:1; padding:10px 14px; background:#0f172a; border:1px solid #334155; border-radius:8px; color:#e2e8f0; font-size:16px; }");
        sb.append("input:focus { outline:none; border-color:#3b82f6; }");
        sb.append(".qty { max-width:80px; }");
        sb.append(
                "button { padding:10px 18px; border:none; border-radius:8px; font-size:15px; font-weight:600; cursor:pointer; }");
        sb.append(".btn-blue { background:#3b82f6; color:#fff; }");
        sb.append(".btn-gray { background:#334155; color:#fff; }");
        sb.append(
                ".toast { position:fixed; bottom:24px; left:50%; transform:translateX(-50%); background:#10b981; color:#fff; padding:12px 24px; border-radius:24px; font-weight:700; font-size:15px; display:none; white-space:nowrap; box-shadow:0 4px 20px rgba(0,0,0,.4); max-width:90vw; overflow:hidden; text-overflow:ellipsis; }");
        sb.append(".toast.err { background:#ef4444; }");
        sb.append("table { width:100%; border-collapse:collapse; font-size:14px; }");
        sb.append("th { text-align:left; color:#64748b; padding:6px 4px; font-size:12px; }");
        sb.append("td { padding:8px 4px; border-bottom:1px solid #334155; }");
        sb.append("td:last-child { text-align:right; font-weight:700; color:#34d399; }");
        sb.append("#status { font-size:12px; color:#94a3b8; text-align:center; margin-top:8px; min-height:18px; }");
        sb.append(".hint { font-size:12px; color:#64748b; margin-top:8px; line-height:1.4; }");
        sb.append("</style>");

        sb.append("</head>");
        sb.append("<body>");

        sb.append("<header>");
        sb.append("<span>&#128230;</span>");
        sb.append("<h1>Warehouse Scanner</h1>");
        sb.append("<div class='badge' id='count'>0 items</div>");
        sb.append("</header>");

        sb.append("<div class='container'>");

        sb.append("<div id='reader'>");
        sb.append("<video id='video' autoplay muted playsinline webkit-playsinline></video>");
        sb.append("<div class='scan-guide'><div class='scan-box'></div><div class='scan-line'></div></div>");
        sb.append("</div>");

        sb.append("<div class='card'>");
        sb.append("<h2>Nhap thu cong (khong can camera)</h2>");
        sb.append("<div class='row'>");
        sb.append("<input type='text' id='barcodeInput' placeholder='Barcode Code39' autocomplete='off' />");
        sb.append("<input type='number' id='qtyInput' class='qty' value='1' min='0.01' step='0.01' />");
        sb.append("<button class='btn-blue' onclick='submitScan()'>Scan</button>");
        sb.append("</div>");
        sb.append("<div class='row'>");
        sb.append(
                "<button class='btn-gray' style='width:100%;' onclick='restartCamera()'>Khoi dong lai camera</button>");
        sb.append("</div>");
        sb.append("<p id='status'>Dang khoi dong camera...</p>");
        sb.append(
                "<p class='hint'>Code39 hop le: A-Z, 0-9, dau -, ., $, /, +, %, khoang trang. Nen de barcode trong khung giua va co vien trang 2 ben.</p>");
        sb.append("</div>");

        sb.append("<div class='card'>");
        sb.append("<h2>Danh sach da scan</h2>");
        sb.append(
                "<table><thead><tr><th>SKU</th><th>Ten</th><th>Qty</th></tr></thead><tbody id='lineTable'></tbody></table>");
        sb.append("</div>");

        sb.append("</div>");
        sb.append("<div class='toast' id='toast'></div>");

        sb.append("<script>");
        sb.append("var SCAN_TOKEN='").append(token).append("';");
        sb.append("var SCAN_API=window.location.origin+'/api/v1/scan-events';");
        sb.append("var scanning=false;");
        sb.append("var scanCooldownMs=1800;");
        sb.append("var codeReader=null;");
        sb.append("var lineData={};");
        sb.append("var currentDeviceId=null;");
        sb.append("var streamStarted=false;");

        sb.append("function setStatus(msg){ document.getElementById('status').textContent = msg; }");

        sb.append("function showToast(msg,isError){");
        sb.append("  var t=document.getElementById('toast');");
        sb.append("  t.textContent=msg;");
        sb.append("  t.className='toast'+(isError?' err':'');");
        sb.append("  t.style.display='block';");
        sb.append("  setTimeout(function(){ t.style.display='none'; }, 2600);");
        sb.append("}");

        // Validate Code 39 text
        sb.append("function isValidCode39(s){");
        sb.append("  return /^[0-9A-Z\\-\\.\\$\\/\\+\\% ]+$/.test(s);");
        sb.append("}");

        sb.append("function normalizeBarcode(raw){");
        sb.append("  if(!raw) return '';");
        sb.append("  var v = String(raw).trim().toUpperCase();");
        // bỏ * nếu scanner/decoder trả cả start-stop char
        sb.append("  if(v.length >= 2 && v.charAt(0) === '*' && v.charAt(v.length-1) === '*'){");
        sb.append("    v = v.substring(1, v.length-1);");
        sb.append("  }");
        sb.append("  return v;");
        sb.append("}");

        sb.append("function updateTable(item){");
        sb.append("  lineData[item.skuCode] = { name: item.skuName || '', qty: item.newQty };");
        sb.append("  var rows='';");
        sb.append("  for(var k in lineData){");
        sb.append("    if(!Object.prototype.hasOwnProperty.call(lineData,k)) continue;");
        sb.append(
                "    rows += '<tr><td>'+escapeHtml(k)+'</td><td>'+escapeHtml(lineData[k].name)+'</td><td>'+lineData[k].qty+'</td></tr>';");
        sb.append("  }");
        sb.append("  document.getElementById('lineTable').innerHTML = rows;");
        sb.append("  document.getElementById('count').textContent = Object.keys(lineData).length + ' items';");
        sb.append("}");

        sb.append("function escapeHtml(s){");
        sb.append(
                "  return String(s || '').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/\"/g,'&quot;').replace(/'/g,'&#39;');");
        sb.append("}");

        sb.append("function submitScan(){");
        sb.append("  var b = normalizeBarcode(document.getElementById('barcodeInput').value);");
        sb.append("  var q = parseFloat(document.getElementById('qtyInput').value);");
        sb.append("  if(!q || q <= 0) q = 1;");
        sb.append("  if(!b){ showToast('Nhap barcode!', true); return; }");
        sb.append("  if(!isValidCode39(b)){");
        sb.append("    showToast('Barcode khong hop le cho Code39', true);");
        sb.append("    setStatus('Sai dinh dang Code39');");
        sb.append("    return;");
        sb.append("  }");
        sb.append("  submitScanWithBarcode(b, q);");
        sb.append("}");

        sb.append("function submitScanWithBarcode(barcode, qty){");
        sb.append("  setStatus('Dang gui: ' + barcode);");
        sb.append("  fetch(SCAN_API, {");
        sb.append("    method: 'POST',");
        sb.append("    headers: { 'Content-Type':'application/json', 'Authorization':'Bearer '+SCAN_TOKEN },");
        sb.append("    body: JSON.stringify({ barcode: barcode, qty: qty })");
        sb.append("  })");
        sb.append("  .then(function(r){");
        sb.append("    return r.text().then(function(text){");
        sb.append("      var body = null;");
        sb.append("      try { body = JSON.parse(text); }");
        sb.append(
                "      catch(e){ body = { success:false, message:'Server loi ('+r.status+'): ' + text.substring(0,120) }; }");
        sb.append("      if(!r.ok && (!body || body.success !== false)){");
        sb.append("        body = { success:false, message:'HTTP ' + r.status };");
        sb.append("      }");
        sb.append("      return body;");
        sb.append("    });");
        sb.append("  })");
        sb.append("  .then(function(data){");
        sb.append("    if(data && data.success){");
        sb.append("      showToast('OK: ' + (data.data.skuCode || barcode) + ' qty:' + data.data.newQty, false);");
        sb.append("      updateTable(data.data);");
        sb.append("      document.getElementById('barcodeInput').value='';");
        sb.append("      setStatus('Camera san sang (Code39)');");
        sb.append("    } else {");
        sb.append("      var msg = (data && data.message) ? data.message : 'Loi';");
        sb.append("      showToast(msg, true);");
        sb.append("      setStatus('Loi: ' + msg);");
        sb.append("    }");
        sb.append("  })");
        sb.append("  .catch(function(err){");
        sb.append("    showToast('Mat ket noi', true);");
        sb.append("    setStatus('Loi mang: ' + err);");
        sb.append("  });");
        sb.append("}");

        sb.append("function onDecodeResult(result){");
        sb.append("  if(!result || scanning) return;");
        sb.append("  var raw = result.getText ? result.getText() : '';");
        sb.append("  var barcode = normalizeBarcode(raw);");
        sb.append("  if(!barcode) return;");
        sb.append("  if(!isValidCode39(barcode)){");
        sb.append("    setStatus('Bo qua ma khong hop le: ' + barcode);");
        sb.append("    return;");
        sb.append("  }");
        sb.append("  scanning = true;");
        sb.append("  submitScanWithBarcode(barcode, 1);");
        sb.append("  setTimeout(function(){ scanning = false; }, scanCooldownMs);");
        sb.append("}");

        sb.append("function chooseBestCamera(devices){");
        sb.append("  if(!devices || !devices.length) return null;");
        sb.append("  var preferred = null;");
        sb.append("  for(var i=0;i<devices.length;i++){");
        sb.append("    var lbl = (devices[i].label || '').toLowerCase();");
        sb.append(
                "    if(lbl.includes('back') || lbl.includes('rear') || lbl.includes('environment')) { preferred = devices[i]; break; }");
        sb.append("  }");
        sb.append("  return preferred || devices[devices.length-1];");
        sb.append("}");

        sb.append("function stopCamera(){");
        sb.append("  try { if(codeReader) codeReader.reset(); } catch(e) { console.log('reset err', e); }");
        sb.append("  streamStarted = false;");
        sb.append("}");

        sb.append("function restartCamera(){");
        sb.append("  stopCamera();");
        sb.append("  setTimeout(function(){ initCamera(); }, 250);");
        sb.append("}");

        sb.append("function initCamera(){");
        sb.append("  setStatus('Dang khoi dong camera...');");
        sb.append("  try {");
        sb.append("    var hints = new Map();");
        sb.append("    hints.set(ZXing.DecodeHintType.POSSIBLE_FORMATS, [ZXing.BarcodeFormat.CODE_39]);");
        sb.append("    hints.set(ZXing.DecodeHintType.TRY_HARDER, true);");
        sb.append("    codeReader = new ZXing.BrowserMultiFormatReader(hints, 250);");
        sb.append("  } catch (e) {");
        sb.append("    setStatus('Khong tao duoc ZXing: ' + e);");
        sb.append("    return;");
        sb.append("  }");

        // B1: xin quyền + liệt kê camera
        sb.append("  codeReader.listVideoInputDevices().then(function(devices){");
        sb.append("    if(!devices || !devices.length){");
        sb.append("      setStatus('Khong tim thay camera - dung nhap tay');");
        sb.append("      return;");
        sb.append("    }");
        sb.append("    var cam = chooseBestCamera(devices);");
        sb.append("    currentDeviceId = cam ? cam.deviceId : null;");

        // B2: dùng constraints để ưu tiên camera sau + HD
        sb.append("    var constraints = {");
        sb.append("      video: {");
        sb.append("        deviceId: currentDeviceId ? { exact: currentDeviceId } : undefined,");
        sb.append("        facingMode: { ideal: 'environment' },");
        sb.append("        width: { ideal: 1920 },");
        sb.append("        height: { ideal: 1080 }");
        sb.append("      },");
        sb.append("      audio: false");
        sb.append("    };");

        sb.append("    setStatus('Dang mo camera sau...');");

        sb.append("    codeReader.decodeFromConstraints(constraints, 'video', function(result, err){");
        sb.append("      if(result){");
        sb.append("        if(!streamStarted){ streamStarted = true; setStatus('Camera san sang (Code39)'); }");
        sb.append("        onDecodeResult(result);");
        sb.append("        return;");
        sb.append("      }");

        sb.append("      if(err){");
        sb.append("        // NotFoundException xay ra lien tuc khi chua thay ma -> bo qua");
        sb.append("        var name = (err && err.name) ? err.name : '';");
        sb.append("        if(name && name.indexOf('NotFoundException') >= 0) {");
        sb.append("          if(!streamStarted){ setStatus('Camera dang mo... huong vao barcode Code39'); }");
        sb.append("          return;");
        sb.append("        }");
        sb.append("        console.log('Decode error:', err);");
        sb.append("      }");
        sb.append("    });");

        sb.append("    setStatus('Camera san sang (Code39) - dua ma vao khung giua');");
        sb.append("  }).catch(function(err){");
        sb.append("    console.log('Camera init error:', err);");
        sb.append("    setStatus('Camera khong kha dung: ' + (err && err.message ? err.message : err));");
        sb.append("  });");
        sb.append("}");

        sb.append("document.addEventListener('DOMContentLoaded', function(){");
        sb.append("  document.getElementById('barcodeInput').addEventListener('keydown', function(e){");
        sb.append("    if(e.key === 'Enter') submitScan();");
        sb.append("  });");
        sb.append("  initCamera();");
        sb.append("});");

        sb.append("window.addEventListener('beforeunload', function(){ stopCamera(); });");

        sb.append("</script>");
        sb.append("</body></html>");

        return sb.toString();
    }
}
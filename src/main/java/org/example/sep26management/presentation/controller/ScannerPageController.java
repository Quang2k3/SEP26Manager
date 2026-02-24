package org.example.sep26management.presentation.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the barcode scanner HTML page for the iPhone / any mobile browser.
 * URL: GET /v1/scan?token={SCAN_TOKEN}
 * Uses ZXing JS library — configured for CODE_39 ONLY to avoid cross-format
 * misreads.
 *
 * GET /v1/scan/url?token={SCAN_TOKEN} — returns the full scan URL as plain text
 * so the frontend can generate a QR code from it.
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
        // ZXing JS — pure JS decoder, works on Safari iOS without native
        // BarcodeDetector
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
                "#reader { border-radius:12px; overflow:hidden; border:2px solid #3b82f6; margin-bottom:16px; background:#000; }");
        sb.append("#video { width:100%; display:block; border-radius:10px; }");
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
        sb.append(
                ".toast { position:fixed; bottom:24px; left:50%; transform:translateX(-50%); background:#10b981; color:#fff; padding:12px 24px; border-radius:24px; font-weight:700; font-size:15px; display:none; white-space:nowrap; box-shadow:0 4px 20px rgba(0,0,0,.4); }");
        sb.append(".toast.err { background:#ef4444; }");
        sb.append("table { width:100%; border-collapse:collapse; font-size:14px; }");
        sb.append("th { text-align:left; color:#64748b; padding:6px 4px; font-size:12px; }");
        sb.append("td { padding:8px 4px; border-bottom:1px solid #334155; }");
        sb.append("td:last-child { text-align:right; font-weight:700; color:#34d399; }");
        sb.append("#status { font-size:12px; color:#64748b; text-align:center; margin-top:8px; }");
        sb.append("</style>");
        sb.append("</head>");
        sb.append("<body>");
        sb.append("<header>");
        sb.append("<span>&#128230;</span>");
        sb.append("<h1>Warehouse Scanner</h1>");
        sb.append("<div class='badge' id='count'>0 items</div>");
        sb.append("</header>");
        sb.append("<div class='container'>");
        sb.append("<div id='reader'><video id='video' autoplay muted playsinline></video></div>");
        sb.append("<div class='card'>");
        sb.append("<h2>Nhap thu cong (khong can camera)</h2>");
        sb.append("<div class='row'>");
        sb.append("<input type='text' id='barcodeInput' placeholder='Barcode' autocomplete='off' />");
        sb.append("<input type='number' id='qtyInput' class='qty' value='1' min='0.01' step='0.01' />");
        sb.append("<button class='btn-blue' onclick='submitScan()'>Scan</button>");
        sb.append("</div>");
        sb.append("<p id='status'>Camera chua khoi dong</p>");
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
        sb.append("var codeReader;");
        sb.append("var lineData={};");

        // Init ZXing with CODE_39 ONLY — prevents cross-format misreads (EAN-13,
        // CODE_128, etc.)
        sb.append("window.addEventListener('load',function(){");
        sb.append("  var hints=new Map();");
        sb.append("  hints.set(ZXing.DecodeHintType.POSSIBLE_FORMATS,[");
        sb.append("    ZXing.BarcodeFormat.CODE_39"); // CODE_39 ONLY
        sb.append("  ]);");
        sb.append("  hints.set(ZXing.DecodeHintType.TRY_HARDER,true);");
        sb.append("  codeReader=new ZXing.BrowserMultiFormatReader(hints,300);");
        sb.append("  codeReader.listVideoInputDevices().then(function(devices){");
        sb.append("    if(!devices.length){setStatus('Khong tim thay camera - dung nhap tay');return;}");
        sb.append("    var cam=null;");
        sb.append("    for(var i=0;i<devices.length;i++){");
        sb.append("      if(/back|rear|environment/i.test(devices[i].label)){cam=devices[i];break;}");
        sb.append("    }");
        sb.append("    if(!cam)cam=devices[devices.length-1];");
        sb.append("    codeReader.decodeFromVideoDevice(cam.deviceId,'video',function(result,err){");
        sb.append("      if(result&&!scanning){");
        sb.append("        scanning=true;");
        sb.append("        submitScanWithBarcode(result.getText().toUpperCase(),1);");
        sb.append("        setTimeout(function(){scanning=false;},1500);");
        sb.append("      }");
        sb.append("    });");
        sb.append("    setStatus('Camera san sang (Code 39) - huong vao barcode');");
        sb.append("  }).catch(function(err){setStatus('Camera khong kha dung: '+err);});");
        sb.append("});");

        sb.append("document.addEventListener('DOMContentLoaded',function(){");
        sb.append(
                "  document.getElementById('barcodeInput').addEventListener('keydown',function(e){if(e.key==='Enter')submitScan();});");
        sb.append("});");

        sb.append("function submitScan(){");
        sb.append("  var b=document.getElementById('barcodeInput').value.trim().toUpperCase();");
        sb.append("  var q=parseFloat(document.getElementById('qtyInput').value)||1;");
        sb.append("  if(!b){showToast('Nhap barcode!',true);return;}");
        sb.append("  submitScanWithBarcode(b,q);");
        sb.append("}");

        sb.append("function submitScanWithBarcode(barcode,qty){");
        sb.append("  setStatus('Dang gui: '+barcode);");
        sb.append("  fetch(SCAN_API,{");
        sb.append("    method:'POST',");
        sb.append("    headers:{'Content-Type':'application/json','Authorization':'Bearer '+SCAN_TOKEN},");
        sb.append("    body:JSON.stringify({barcode:barcode,qty:qty})");
        sb.append("  }).then(function(r){");
        sb.append("    return r.text().then(function(text){");
        sb.append("      try{return JSON.parse(text);}");
        sb.append(
                "      catch(e){return {success:false,message:'Server loi ('+r.status+'): '+text.substring(0,120)};}");
        sb.append("    });");
        sb.append("  }).then(function(data){");
        sb.append("    if(data.success){");
        sb.append("      showToast('OK: '+data.data.skuCode+' qty:'+data.data.newQty);");
        sb.append("      updateTable(data.data);");
        sb.append("      document.getElementById('barcodeInput').value='';");
        sb.append("      setStatus('Camera san sang (Code 39)');");
        sb.append("    }else{");
        sb.append("      showToast(data.message||'Loi',true);");
        sb.append("      setStatus('Loi: '+(data.message||'khong xac dinh'));");
        sb.append("    }");
        sb.append("  }).catch(function(err){showToast('Mat ket noi',true);setStatus('Loi mang: '+err);});");
        sb.append("}");

        sb.append("function updateTable(item){");
        sb.append("  lineData[item.skuCode]={name:item.skuName||'',qty:item.newQty};");
        sb.append("  var rows='';");
        sb.append(
                "  for(var k in lineData){rows+='<tr><td>'+k+'</td><td>'+lineData[k].name+'</td><td>'+lineData[k].qty+'</td></tr>';}");
        sb.append("  document.getElementById('lineTable').innerHTML=rows;");
        sb.append("  document.getElementById('count').textContent=Object.keys(lineData).length+' items';");
        sb.append("}");

        sb.append("function showToast(msg,isError){");
        sb.append("  var t=document.getElementById('toast');");
        sb.append("  t.textContent=msg;t.className='toast'+(isError?' err':'');t.style.display='block';");
        sb.append("  setTimeout(function(){t.style.display='none';},2500);");
        sb.append("}");

        sb.append("function setStatus(msg){document.getElementById('status').textContent=msg;}");
        sb.append("</script>");
        sb.append("</body></html>");
        return sb.toString();
    }
}

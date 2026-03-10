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

        return base + request.getContextPath() + "/v1/scan?token=" + token + "&v=qr3";
    }

    @GetMapping(value = "/v1/scan", produces = MediaType.TEXT_HTML_VALUE)
    @Operation(summary = "Trang quét barcode (HTML)", description = "Trả về trang HTML với camera QR scanner. iPhone mở trang này để quét barcode và gửi scan event.")
    public ResponseEntity<String> scannerPage(@RequestParam("token") String token, HttpServletRequest request) {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
                .header("Pragma", "no-cache")
                .body(buildHtml(escapeForJs(token), request.getContextPath()));
    }

    private static String escapeForJs(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\"", "\\\"");
    }

    private String buildHtml(String token, String contextPath) {
        String scriptPath = (contextPath == null || contextPath.isBlank() ? "" : contextPath)
                + "/js/html5-qrcode.min.js";
        String safeToken = escapeForJs(token);
        return """
                <!DOCTYPE html>
                <html lang='vi'>
                <head>
                    <meta charset='UTF-8'>
                    <meta name='viewport' content='width=device-width,initial-scale=1.0,user-scalable=no,viewport-fit=cover'>
                    <meta name='apple-mobile-web-app-capable' content='yes'>
                    <meta name='mobile-web-app-capable' content='yes'>
                    <title>Warehouse Scanner (QR)</title>

                    <script src='"""
                + scriptPath
                + """
                        '></script>

                                            <style>
                                                *{box-sizing:border-box;margin:0;padding:0}
                                                body{
                                                    font-family:-apple-system,BlinkMacSystemFont,sans-serif;
                                                    background:#0f172a;
                                                    color:#e2e8f0;
                                                    min-height:100vh
                                                }
                                                header{
                                                    background:linear-gradient(135deg,#1e40af,#1d4ed8);
                                                    padding:14px 16px;
                                                    display:flex;
                                                    align-items:center;
                                                    gap:10px
                                                }
                                                header h1{
                                                    font-size:17px;
                                                    font-weight:700;
                                                    flex:1
                                                }
                                                .badge{
                                                    background:#dbeafe;
                                                    color:#1e3a8a;
                                                    border-radius:20px;
                                                    padding:3px 12px;
                                                    font-size:12px;
                                                    font-weight:700
                                                }
                                                .container{
                                                    padding:12px;
                                                    max-width:520px;
                                                    margin:0 auto
                                                }
                                                #cam-wrap{
                                                    position:relative;
                                                    border-radius:14px;
                                                    overflow:hidden;
                                                    border:2px solid #3b82f6;
                                                    background:#111;
                                                    margin-bottom:8px;
                                                    min-height:260px
                                                }
                                                #reader{
                                                    width:100%;
                                                    min-height:260px
                                                }
                                                #scan-line{
                                                    position:absolute;
                                                    left:8%;
                                                    right:8%;
                                                    height:2px;
                                                    background:linear-gradient(90deg,transparent,#34d399,transparent);
                                                    top:50%;
                                                    animation:scan 1.8s ease-in-out infinite;
                                                    pointer-events:none;
                                                    z-index:10
                                                }
                                                @keyframes scan{
                                                    0%,100%{top:20%}
                                                    50%{top:80%}
                                                }
                                                #cam-status{
                                                    background:#1e293b;
                                                    color:#94a3b8;
                                                    font-size:11px;
                                                    padding:6px 12px;
                                                    text-align:center;
                                                    border-radius:0 0 12px 12px
                                                }
                                                .card{
                                                    background:#1e293b;
                                                    border-radius:12px;
                                                    padding:14px;
                                                    margin-top:10px
                                                }
                                                .card-title{
                                                    font-size:11px;
                                                    color:#64748b;
                                                    text-transform:uppercase;
                                                    letter-spacing:.08em;
                                                    margin-bottom:10px;
                                                    font-weight:600
                                                }
                                                .hint{
                                                    color:#94a3b8;
                                                    font-size:12px;
                                                    line-height:1.35
                                                }
                                                .row{
                                                    display:flex;
                                                    gap:8px
                                                }
                                                input,select{
                                                    flex:1;
                                                    padding:11px 14px;
                                                    background:#0f172a;
                                                    border:1.5px solid #334155;
                                                    border-radius:8px;
                                                    color:#e2e8f0;
                                                    font-size:16px;
                                                    -webkit-appearance:none;
                                                    appearance:none
                                                }
                                                input:focus,select:focus{
                                                    outline:none;
                                                    border-color:#3b82f6
                                                }
                                                .qty-in{
                                                    max-width:90px;
                                                    text-align:center;
                                                    flex:none
                                                }
                                                .btn{
                                                    padding:12px 18px;
                                                    border:none;
                                                    border-radius:10px;
                                                    font-size:16px;
                                                    font-weight:800;
                                                    cursor:pointer;
                                                    background:#3b82f6;
                                                    color:#fff
                                                }
                                                .btn.full{width:100%}
                                                .btn.danger{
                                                    background:#ef4444;
                                                    width:100%
                                                }
                                                table{
                                                    width:100%;
                                                    border-collapse:collapse;
                                                    font-size:13px
                                                }
                                                th{
                                                    text-align:left;
                                                    color:#475569;
                                                    padding:5px 4px;
                                                    font-size:11px;
                                                    font-weight:600
                                                }
                                                td{
                                                    padding:9px 4px;
                                                    border-bottom:1px solid #263347
                                                }
                                                .qc{
                                                    text-align:right;
                                                    font-weight:800;
                                                    color:#34d399;
                                                    font-size:15px
                                                }
                                                .sc{
                                                    color:#94a3b8;
                                                    font-size:11px
                                                }
                                                .toast{
                                                    position:fixed;
                                                    bottom:28px;
                                                    left:50%;
                                                    transform:translateX(-50%);
                                                    background:#10b981;
                                                    color:#fff;
                                                    padding:11px 24px;
                                                    border-radius:28px;
                                                    font-weight:800;
                                                    font-size:14px;
                                                    display:none;
                                                    white-space:nowrap;
                                                    box-shadow:0 4px 24px rgba(0,0,0,.5);
                                                    z-index:9999
                                                }
                                                .toast.err{background:#ef4444}
                                            </style>
                                        </head>
                                        <body>
                                            <header>
                                                <span style='font-size:20px'>📦</span>
                                                <h1>Warehouse Scanner</h1>
                                                <span class='badge' id='roleBadge' style='background:#fbbf24;color:#92400e;margin-right:4px'>KEEPER</span>
                                                <span class='badge' id='cnt'>0 dòng</span>
                                            </header>

                                            <div class='container'>
                                                <div id='cam-wrap'>
                                                    <div id='reader'></div>
                                                    <div id='scan-line'></div>

                                                    <div id='cam-start-overlay'
                                                         style='position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;background:rgba(15,23,42,0.95);z-index:20;border-radius:14px'>
                                                        <div style='font-size:52px;margin-bottom:14px'>📷</div>
                                                        <button id='startCamBtn'
                                                                style='background:linear-gradient(135deg,#2563eb,#3b82f6);color:#fff;border:none;border-radius:14px;padding:16px 42px;font-size:19px;font-weight:800;cursor:pointer;box-shadow:0 4px 20px rgba(59,130,246,.5)'>
                                                            ▶ Bắt đầu quét
                                                        </button>
                                                        <div style='color:#64748b;font-size:12px;margin-top:14px;text-align:center'>
                                                            Nhấn vào đây để bật Camera &amp; iOS sẽ hỏi xin quyền
                                                        </div>
                                                    </div>
                                                </div>

                                                <div id='cam-status'>Chưa bật camera — Nhấn nút bên trên</div>

                                                <div class='card'>
                                                    <div class='card-title'>Quét QR / Barcode</div>
                                                    <div class='hint'>Đưa mã vào khung. Nếu không quét được, kéo xuống nhập mã SKU bằng tay.</div>
                                                    <div class='hint' style='margin-top:8px'>Mã vừa quét: <b id='last'>-</b></div>
                                                </div>

                                                <div class='card' id='classifyCard'>
                                                    <div class='card-title'>Phân loại & Nhập thủ công</div>

                                                    <div class='row' style='margin-bottom:8px'>
                                                        <select id='cond'>
                                                            <option value='PASS'>✅ HÀNG TỐT (PASS)</option>
                                                            <option value='FAIL'>❌ HÀNG LỖI (FAIL)</option>
                                                        </select>
                                                    </div>

                                                    <div class='row' id='reasonWrap' style='margin-bottom:8px;display:none'>
                                                        <select id='reason' style='border:1.5px dotted #ef4444;color:#ef4444'>
                                                            <option value=''>-- Chọn lỗi cơ bản --</option>
                                                            <option value='DENTED'>Móp méo vỏ (DENTED)</option>
                                                            <option value='TEAR'>Rách bao bì (TEAR)</option>
                                                            <option value='LEAK'>Chảy/Rỉ nước (LEAK)</option>
                                                            <option value='DIRTY'>Bẩn/Bụi (DIRTY)</option>
                                                            <option value='OTHER'>Khác (OTHER)</option>
                                                        </select>
                                                    </div>

                                                    <div class='row'>
                                                        <input type='text' id='bc' placeholder='SKU Code (ví dụ: 0001-1012)' autocapitalize='characters' autocomplete='off'/>
                                                        <input type='number' id='qty' class='qty-in' value='1' min='0.01' step='0.01'/>
                                                    </div>

                                                    <button class='btn full' id='manualBtn' style='margin-top:10px'>Scan thủ công</button>
                                                </div>

                                                <div class='card'>
                                                    <div class='card-title'>Đã scan</div>
                                                    <table>
                                                        <thead>
                                                            <tr>
                                                                <th>SKU</th>
                                                                <th>Tên sản phẩm</th>
                                                                <th style='text-align:right'>Qty</th>
                                                                <th style='width:35px'></th>
                                                            </tr>
                                                        </thead>
                                                        <tbody id='lines'></tbody>
                                                    </table>
                                                </div>

                                                <div class='card'>
                                                    <div class='card-title'>Chốt & Tạo Phiếu Kho</div>

                                                    <div class='row' style='margin-bottom:8px'>
                                                        <select id='sourceType'>
                                                            <option value='SUPPLIER'>Nhập từ NCC (SUPPLIER)</option>
                                                            <option value='TRANSFER'>Chuyển Kho (TRANSFER)</option>
                                                            <option value='RETURN'>Khách Hàng Trả (RETURN)</option>
                                                        </select>
                                                    </div>

                                                    <div class='row' style='margin-bottom:8px'>
                                                        <input type='text' id='supplierCode' placeholder='Mã NCC (vd: SUP-001)' autocapitalize='characters' autocomplete='off'/>
                                                    </div>

                                                    <div class='row' style='margin-bottom:8px'>
                                                        <input type='text' id='sourceRef' placeholder='Mã chứng từ / PO' autocapitalize='characters' autocomplete='off'/>
                                                    </div>

                                                    <div class='row' style='margin-bottom:8px'>
                                                        <input type='text' id='note' placeholder='Ghi chú' autocomplete='off'/>
                                                    </div>

                                                    <button class='btn full' id='createGrnBtn' style='background:#10b981;margin-top:5px'>✅ Tạo Phiếu GRN</button>
                                                </div>

                                                <div class='card'>
                                                    <button class='btn danger' id='closeBtn'>⏸ Tạm dừng / Đóng Camera</button>
                                                </div>
                                            </div>

                                            <div class='toast' id='toast'></div>

                                            <script>
                                                var TOKEN = '"""
                + safeToken
                + """
                        ';
                                                var API_BASE = window.location.origin + '"""
                + (contextPath == null ? "" : contextPath)
                + """
                        ';
                                                var API = API_BASE + '/v1/scan-events';

                                                var lineData = [];
                                                var inflight = false;
                                                var lastCode = null;
                                                var lastAt = 0;
                                                var qr = null;
                                                var qrRunning = false;

                                                function getUserRole() {
                                                    try {
                                                        var p = TOKEN.split('.')[1];
                                                        var d = JSON.parse(base64UrlDecode(p));
                                                        return (d.roles || 'KEEPER').toUpperCase();
                                                    } catch (e) {
                                                        return 'KEEPER';
                                                    }
                                                }
                                                var USER_ROLE = getUserRole();
                                                var IS_QC = (USER_ROLE === 'QC');

                                                function toast(msg, err) {
                                                    var t = document.getElementById('toast');
                                                    t.textContent = msg;
                                                    t.className = 'toast' + (err ? ' err' : '');
                                                    t.style.display = 'block';
                                                    clearTimeout(t._t);
                                                    t._t = setTimeout(function () {
                                                        t.style.display = 'none';
                                                    }, 2600);
                                                }

                                                function setStatus(msg) {
                                                    document.getElementById('cam-status').textContent = msg;
                                                }

                                                function base64UrlDecode(str) {
                                                    str = (str || '').replace(/-/g, '+').replace(/_/g, '/');
                                                    while (str.length % 4) str += '=';
                                                    return atob(str);
                                                }

                                                function getSessionId() {
                                                    try {
                                                        var p = TOKEN.split('.')[1];
                                                        var d = JSON.parse(base64UrlDecode(p));
                                                        return d.sessionId || null;
                                                    } catch (e) {
                                                        return null;
                                                    }
                                                }

                                                var SESSION_ID = getSessionId();

                                                function waitForHtml5Qrcode(callback, retries) {
                                                    if (typeof Html5Qrcode !== 'undefined') {
                                                        callback();
                                                        return;
                                                    }
                                                    if (retries <= 0) {
                                                        setStatus('Lỗi: Không tải được thư viện QR');
                                                        toast('Không tải được html5-qrcode.min.js', true);
                                                        return;
                                                    }
                                                    setTimeout(function () {
                                                        waitForHtml5Qrcode(callback, retries - 1);
                                                    }, 200);
                                                }

                                                function updateTable(lines) {
                                                    lineData = Array.isArray(lines) ? lines : [];

                                                    var rows = '';
                                                    var count = 0;

                                                    for (var i = 0; i < lineData.length; i++) {
                                                        var l = lineData[i];
                                                        count++;

                                                        var isFail = l.condition === 'FAIL';
                                                        var condHtml = isFail
                                                            ? '<span style="color:#ef4444;font-size:10px;display:block">Lỗi: ' + (l.reasonCode || 'N/A') + '</span>'
                                                            : '';

                                                        rows += '<tr>'
                                                            + '<td class="sc">' + (l.skuCode || '') + condHtml + '</td>'
                                                            + '<td>' + (l.skuName || '') + '</td>'
                                                            + '<td class="qc" style="' + (isFail ? 'color:#ef4444' : '') + '">' + (l.qty || 0) + '</td>'
                                                            + '<td><button onclick="removeL(\\'' + l.skuId + '\\',\\'' + l.condition + '\\')" '
                                                            + 'style="background:transparent;border:1px solid #ef4444;border-radius:4px;color:#ef4444;font-size:12px;padding:3px 6px;font-weight:bold">-1</button></td>'
                                                            + '</tr>';
                                                    }

                                                    document.getElementById('lines').innerHTML = rows;
                                                    document.getElementById('cnt').textContent = count + ' dòng';
                                                }

                                                function renderSession(s) {
                                                    if (s && s.lines) {
                                                        updateTable(s.lines);
                                                    } else {
                                                        updateTable([]);
                                                    }
                                                }

                                                function fetchSession() {
                                                    if (!SESSION_ID) return;

                                                    fetch(API_BASE + '/v1/receiving-sessions/' + SESSION_ID, {
                                                        headers: { 'Authorization': 'Bearer ' + TOKEN }
                                                    })
                                                    .then(function (r) { return r.json(); })
                                                    .then(function (d) {
                                                        if (d && d.success) {
                                                            renderSession(d.data);
                                                        }
                                                    })
                                                    .catch(function (e) {
                                                        console.error('fetchSession error:', e);
                                                    });
                                                }

                                                function removeL(skuId, cond) {
                                                    fetch(API + '?sessionId=' + encodeURIComponent(SESSION_ID)
                                                            + '&skuId=' + encodeURIComponent(skuId)
                                                            + '&condition=' + encodeURIComponent(cond)
                                                            + '&qty=1', {
                                                        method: 'DELETE',
                                                        headers: { 'Authorization': 'Bearer ' + TOKEN }
                                                    })
                                                    .then(function (r) { return r.json(); })
                                                    .then(function (d) {
                                                        if (d && d.success) {
                                                            fetchSession();
                                                        } else {
                                                            toast((d && d.message) || 'Lỗi giảm số lượng', true);
                                                        }
                                                    })
                                                    .catch(function (e) {
                                                        toast('Lỗi mạng: ' + e, true);
                                                    });
                                                }

                                                function sendBarcode(barcode, qty) {
                                                    if (inflight) return;

                                                    inflight = true;
                                                    setStatus('Đang gửi: ' + barcode);

                                                    var cond = IS_QC ? document.getElementById('cond').value : 'PASS';
                                                    var reason = (IS_QC && cond === 'FAIL') ? document.getElementById('reason').value : null;

                                                    if (IS_QC && cond === 'FAIL' && !reason) {
                                                        toast('Vui lòng chọn Lý do lỗi', true);
                                                        inflight = false;
                                                        setStatus('Chưa nhập lý do lỗi');
                                                        return;
                                                    }

                                                    fetch(API, {
                                                        method: 'POST',
                                                        headers: {
                                                            'Content-Type': 'application/json',
                                                            'Authorization': 'Bearer ' + TOKEN
                                                        },
                                                        body: JSON.stringify({
                                                            barcode: barcode,
                                                            qty: qty,
                                                            condition: cond,
                                                            reasonCode: reason
                                                        })
                                                    })
                                                    .then(function (r) {
                                                        return r.text().then(function (txt) {
                                                            try {
                                                                return JSON.parse(txt);
                                                            } catch (e) {
                                                                return {
                                                                    success: false,
                                                                    message: 'HTTP ' + r.status + ': ' + txt.substring(0, 140)
                                                                };
                                                            }
                                                        });
                                                    })
                                                    .then(function (d) {
                                                        if (d && d.success) {
                                                            toast('✓ ' + (cond === 'FAIL' ? 'LỖI ' : '') + d.data.skuCode + ' — qty:' + d.data.newQty, false);
                                                            fetchSession();
                                                            document.getElementById('bc').value = '';
                                                            if (navigator.vibrate) navigator.vibrate(60);
                                                        } else {
                                                            toast((d && d.message) ? d.message : 'Lỗi không xác định', true);
                                                        }

                                                        setStatus('Camera sẵn sàng — đưa QR/Barcode vào khung');
                                                    })
                                                    .catch(function (e) {
                                                        toast('Mất kết nối', true);
                                                        setStatus('Lỗi mạng: ' + e);
                                                    })
                                                    .finally(function () {
                                                        setTimeout(function () {
                                                            inflight = false;
                                                        }, 600);
                                                    });
                                                }

                                                function submitManual() {
                                                    var b = (document.getElementById('bc').value || '').trim().toUpperCase();
                                                    var q = parseFloat(document.getElementById('qty').value) || 1;

                                                    if (!b) {
                                                        toast('Nhập mã SKU!', true);
                                                        return;
                                                    }

                                                    document.getElementById('last').textContent = b;
                                                    sendBarcode(b, q);
                                                }

                                                function stopQr() {
                                                    try {
                                                        if (qr && qrRunning) {
                                                            qr.stop()
                                                                .then(function () {
                                                                    qrRunning = false;
                                                                    setStatus('Camera đã tạm dừng');
                                                                })
                                                                .catch(function (e) {
                                                                    console.error('stopQr error:', e);
                                                                    qrRunning = false;
                                                                });
                                                        }
                                                    } catch (e) {
                                                        console.error('stopQr exception:', e);
                                                        qrRunning = false;
                                                    }
                                                }

                                                function startQr() {
                                                    setStatus('Đang khởi động camera...');

                                                    if (typeof Html5Qrcode === 'undefined') {
                                                        toast('Thư viện QR chưa tải! Thử refresh trang.', true);
                                                        setStatus('Lỗi: Thư viện chưa tải');
                                                        return;
                                                    }

                                                    try {
                                                        if (qr && qrRunning) {
                                                            qr.stop().catch(function () {});
                                                        }

                                                        qr = new Html5Qrcode('reader', {
                                                            formatsToSupport: undefined,
                                                            verbose: false
                                                        });

                                                        var config = {
                                                            fps: 10,
                                                            qrbox: { width: 250, height: 250 },
                                                            aspectRatio: 1.0,
                                                            rememberLastUsedCamera: true,
                                                            supportedScanTypes: [Html5QrcodeScanType.SCAN_TYPE_CAMERA]
                                                        };

                                                        qr.start(
                                                            { facingMode: { exact: 'environment' } },
                                                            config,
                                                            function (decodedText) {
                                                                var code = (decodedText || '').trim().toUpperCase();
                                                                if (code.length < 2) return;

                                                                var now = Date.now();
                                                                if (code === lastCode && (now - lastAt) < 1500) return;

                                                                lastCode = code;
                                                                lastAt = now;

                                                                document.getElementById('last').textContent = code;
                                                                sendBarcode(code, 1);
                                                            },
                                                            function () {
                                                                // bỏ qua lỗi đọc từng frame
                                                            }
                                                        ).then(function () {
                                                            qrRunning = true;
                                                            setStatus('Camera sẵn sàng — đưa QR/Barcode vào khung');

                                                            var video = document.querySelector('#reader video');
                                                            if (video) {
                                                                video.setAttribute('playsinline', 'true');
                                                                video.setAttribute('muted', 'true');
                                                                video.setAttribute('autoplay', 'true');
                                                            }
                                                        }).catch(function (e1) {
                                                            console.warn('Back camera exact failed, fallback to environment:', e1);

                                                            qr.start(
                                                                { facingMode: 'environment' },
                                                                config,
                                                                function (decodedText) {
                                                                    var code = (decodedText || '').trim().toUpperCase();
                                                                    if (code.length < 2) return;

                                                                    var now = Date.now();
                                                                    if (code === lastCode && (now - lastAt) < 1500) return;

                                                                    lastCode = code;
                                                                    lastAt = now;

                                                                    document.getElementById('last').textContent = code;
                                                                    sendBarcode(code, 1);
                                                                },
                                                                function () {}
                                                            ).then(function () {
                                                                qrRunning = true;
                                                                setStatus('Camera sẵn sàng — đưa QR/Barcode vào khung');

                                                                var video = document.querySelector('#reader video');
                                                                if (video) {
                                                                    video.setAttribute('playsinline', 'true');
                                                                    video.setAttribute('muted', 'true');
                                                                    video.setAttribute('autoplay', 'true');
                                                                }
                                                            }).catch(function (e2) {
                                                                console.error('Camera start error:', e2);
                                                                toast('Không mở được camera. Kiểm tra quyền Camera trên iPhone/Safari.', true);
                                                                setStatus('Lỗi camera: ' + e2);
                                                                document.getElementById('cam-start-overlay').style.display = 'flex';
                                                            });
                                                        });
                                                    } catch (e) {
                                                        console.error('startQr exception:', e);
                                                        toast('Không khởi tạo được QR: ' + e, true);
                                                        setStatus('Lỗi: ' + e);
                                                        document.getElementById('cam-start-overlay').style.display = 'flex';
                                                    }
                                                }

                                                function toggleCamera() {
                                                    var btn = document.getElementById('closeBtn');

                                                    if (qrRunning) {
                                                        stopQr();
                                                        btn.textContent = '▶ Tiếp tục quét';
                                                        btn.style.background = '#3b82f6';
                                                    } else {
                                                        document.getElementById('cam-start-overlay').style.display = 'none';
                                                        startQr();
                                                        btn.textContent = '⏸ Tạm dừng / Đóng Camera';
                                                        btn.style.background = '#ef4444';
                                                    }
                                                }

                                                function createGrn() {
                                                    if (!SESSION_ID) {
                                                        toast('Không tìm thấy session', true);
                                                        return;
                                                    }

                                                    if (!lineData || lineData.length === 0) {
                                                        toast('Chưa quét sản phẩm nào!', true);
                                                        return;
                                                    }

                                                    if (!confirm('Chốt số lượng và tạo Phiếu Nhập Kho?')) {
                                                        return;
                                                    }

                                                    var body = {
                                                        sourceType: document.getElementById('sourceType').value,
                                                        supplierCode: document.getElementById('supplierCode').value.trim() || null,
                                                        sourceReferenceCode: document.getElementById('sourceRef').value.trim() || null,
                                                        note: document.getElementById('note').value.trim() || null
                                                    };

                                                    var btn = document.getElementById('createGrnBtn');
                                                    btn.disabled = true;
                                                    btn.textContent = 'Đang tạo...';

                                                    fetch(API_BASE + '/v1/receiving-sessions/' + SESSION_ID + '/create-grn', {
                                                        method: 'POST',
                                                        headers: {
                                                            'Content-Type': 'application/json',
                                                            'Authorization': 'Bearer ' + TOKEN
                                                        },
                                                        body: JSON.stringify(body)
                                                    })
                                                    .then(function (r) { return r.json(); })
                                                    .then(function (d) {
                                                        if (d && d.success) {
                                                            toast('✅ Đã tạo GRN: ' + (d.data.receivingId || 'Thành công'), false);
                                                            stopQr();
                                                            document.getElementById('cam-status').textContent = 'Đã hoàn tất phiên quét.';
                                                            btn.textContent = '✅ Đã tạo Phiếu';
                                                            document.getElementById('manualBtn').disabled = true;
                                                            document.getElementById('closeBtn').disabled = true;
                                                        } else {
                                                            toast((d && d.message) || 'Lỗi tạo phiếu', true);
                                                            btn.disabled = false;
                                                            btn.textContent = '✅ Tạo Phiếu GRN';
                                                        }
                                                    })
                                                    .catch(function (e) {
                                                        toast('Lỗi mạng: ' + e, true);
                                                        btn.disabled = false;
                                                        btn.textContent = '✅ Tạo Phiếu GRN';
                                                    });
                                                }

                                                document.addEventListener('DOMContentLoaded', function () {
                                                    document.getElementById('cond').addEventListener('change', function () {
                                                        document.getElementById('reasonWrap').style.display =
                                                            this.value === 'FAIL' ? 'flex' : 'none';
                                                    });

                                                    // ── Role-based UI ──
                                                    var roleBadge = document.getElementById('roleBadge');
                                                    roleBadge.textContent = USER_ROLE;
                                                    if (IS_QC) {
                                                        roleBadge.style.background = '#a78bfa';
                                                        roleBadge.style.color = '#4c1d95';
                                                    } else {
                                                        roleBadge.style.background = '#34d399';
                                                        roleBadge.style.color = '#064e3b';
                                                    }

                                                    // Hide classification section for KEEPER
                                                    if (!IS_QC) {
                                                        var condRow = document.getElementById('cond').parentElement;
                                                        if (condRow) condRow.style.display = 'none';
                                                        document.getElementById('reasonWrap').style.display = 'none';
                                                        // Update card title for Keeper
                                                        var classifyCard = document.getElementById('classifyCard');
                                                        if (classifyCard) {
                                                            classifyCard.querySelector('.card-title').textContent = 'Nhập thủ công';
                                                        }
                                                    }

                                                    document.getElementById('manualBtn').addEventListener('click', submitManual);
                                                    document.getElementById('closeBtn').addEventListener('click', toggleCamera);
                                                    document.getElementById('createGrnBtn').addEventListener('click', createGrn);

                                                    document.getElementById('bc').addEventListener('keydown', function (e) {
                                                        if (e.key === 'Enter') {
                                                            submitManual();
                                                        }
                                                    });

                                                    document.getElementById('startCamBtn').addEventListener('click', function () {
                                                        waitForHtml5Qrcode(function () {
                                                            document.getElementById('cam-start-overlay').style.display = 'none';
                                                            startQr();
                                                        }, 25);
                                                    });
                                                });

                                                window.addEventListener('load', function () {
                                                    fetchSession();
                                                });
                                            </script>
                                        </body>
                                        </html>
                                        """;
    }
}
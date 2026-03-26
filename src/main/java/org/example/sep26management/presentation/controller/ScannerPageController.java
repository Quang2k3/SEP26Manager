package org.example.sep26management.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Scanner Page", description = "Trang quét barcode cho iPhone.")
public class ScannerPageController {

    @GetMapping(value = "/v1/scan/url", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Lấy URL trang scan",
            description = "Trả về URL trang scan (hiện tại FE Next.js tự build URL, endpoint này dùng cho legacy).")
    public String getScanUrl(@RequestParam("token") String token,
            @RequestParam(value = "receivingId", required = false) Long receivingId,
            HttpServletRequest request) {
        String base = request.getScheme() + "://" + request.getServerName()
                + (request.getServerPort() == 80 || request.getServerPort() == 443 ? ""
                        : ":" + request.getServerPort());
        String url = base + "/v1/scan?token=" + token + "&v=qr4";
        if (receivingId != null)
            url += "&receivingId=" + receivingId;
        return url;
    }
}
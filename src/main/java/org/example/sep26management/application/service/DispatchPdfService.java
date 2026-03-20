package org.example.sep26management.application.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import com.itextpdf.text.pdf.draw.LineSeparator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.dto.response.DispatchNoteResponse;
import org.example.sep26management.infrastructure.persistence.entity.*;
import org.example.sep26management.infrastructure.persistence.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

/**
 * DispatchPdfService — Tạo Phiếu Xuất Kho PDF và upload lên Cloudinary.
 *
 * Flow:
 *   confirmDispatch() → generateAndUploadPdf(soId) → lưu URL vào sales_orders.dispatch_pdf_url
 *
 * Endpoint tải về:
 *   GET /v1/outbound/sales-orders/{soId}/dispatch-pdf → redirect URL hoặc trả presigned URL
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DispatchPdfService {

    private final SalesOrderJpaRepository salesOrderRepository;
    private final SalesOrderItemJpaRepository salesOrderItemRepository;
    private final CustomerJpaRepository customerRepository;
    private final SkuJpaRepository skuRepository;
    private final WarehouseJpaRepository warehouseRepository;
    private final UserJpaRepository userRepository;
    private final PickingTaskItemJpaRepository pickingTaskItemRepository;
    private final InventoryLotJpaRepository inventoryLotRepository;
    private final Cloudinary cloudinary;

    // ─── Màu sắc ───────────────────────────────────────────────
    private static final BaseColor HEADER_BG   = new BaseColor(0xD6, 0xEA, 0xF8); // #D6EAF8
    private static final BaseColor CATEGORY_BG = new BaseColor(0xF2, 0xF3, 0xF4); // #F2F3F4
    private static final BaseColor TOTAL_BG    = new BaseColor(0xEB, 0xF5, 0xFB); // #EBF5FB
    private static final BaseColor BORDER_CLR  = new BaseColor(0xAE, 0xD6, 0xF1); // #AED6F1
    private static final BaseColor TITLE_CLR   = new BaseColor(0x1A, 0x52, 0x76); // #1A5276

    // ─── Fonts ─────────────────────────────────────────────────
    // Dùng BaseFont để hỗ trợ tiếng Việt (Unicode)
    private Font getFontBold(float size, BaseColor color) {
        try {
            BaseFont bf = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            return new Font(bf, size, Font.BOLD, color);
        } catch (Exception e) {
            return new Font(Font.FontFamily.HELVETICA, size, Font.BOLD, color);
        }
    }

    private Font getFont(float size, BaseColor color) {
        try {
            BaseFont bf = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            return new Font(bf, size, Font.NORMAL, color);
        } catch (Exception e) {
            return new Font(Font.FontFamily.HELVETICA, size, Font.NORMAL, color);
        }
    }

    // ─── Generate + Upload ─────────────────────────────────────

    /**
     * Tạo PDF phiếu xuất kho và upload lên Cloudinary.
     * Lưu URL vào sales_orders.dispatch_pdf_url.
     *
     * @return URL của PDF đã upload (Cloudinary URL)
     */
    // [FIX] REQUIRES_NEW: tách khỏi transaction của confirmDispatch
    // → nếu PDF/Cloudinary fail chỉ rollback phần PDF, không rollback dispatch
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String generateAndUploadPdf(Long soId) {
        try {
            byte[] pdfBytes = buildPdfBytes(soId);

            // Upload lên Cloudinary dưới dạng raw file
            Map<String, Object> uploadResult = cloudinary.uploader().upload(
                    pdfBytes,
                    ObjectUtils.asMap(
                            "resource_type", "raw",
                            "folder",        "dispatch_notes",
                            "public_id",     "dispatch_note_SO_" + soId + "_" + System.currentTimeMillis(),
                            "format",        "pdf"
                    )
            );

            String pdfUrl = (String) uploadResult.get("secure_url");
            log.info("Dispatch PDF uploaded for soId={}: {}", soId, pdfUrl);

            // Lưu URL vào entity
            salesOrderRepository.findById(soId).ifPresent(so -> {
                so.setDispatchPdfUrl(pdfUrl);
                salesOrderRepository.save(so);
            });

            return pdfUrl;

        } catch (Exception e) {
            // [FIX] KHÔNG throw exception — tránh đánh dấu transaction rollback-only
            // Dispatch đã thành công, PDF chỉ là phụ — lỗi PDF không được cancel dispatch
            log.error("Failed to generate/upload dispatch PDF for soId={}: {}", soId, e.getMessage());
            return null;
        }
    }

    /**
     * Lấy URL PDF đã lưu, hoặc tạo mới nếu chưa có.
     */
    public String getOrCreatePdfUrl(Long soId) {
        return salesOrderRepository.findById(soId)
                .map(so -> {
                    if (so.getDispatchPdfUrl() != null && !so.getDispatchPdfUrl().isBlank()) {
                        return so.getDispatchPdfUrl();
                    }
                    return generateAndUploadPdf(soId);
                })
                .orElseThrow(() -> new org.example.sep26management.infrastructure.exception.BusinessException("Sales Order not found: " + soId));
    }

    // ─── Build PDF bytes ───────────────────────────────────────

    private byte[] buildPdfBytes(Long soId) throws Exception {
        SalesOrderEntity so = salesOrderRepository.findById(soId)
                .orElseThrow(() -> new RuntimeException("SO not found: " + soId));

        CustomerEntity customer = customerRepository.findById(so.getCustomerId()).orElse(null);
        WarehouseEntity warehouse = warehouseRepository.findById(so.getWarehouseId()).orElse(null);
        String createdByName = so.getCreatedBy() != null
                ? userRepository.findById(so.getCreatedBy()).map(UserEntity::getFullName).orElse("")
                : "";

        // Lấy danh sách item PASS từ picking task
        List<PickingTaskItemEntity> passItems = pickingTaskItemRepository.findPassedItemsBySoId(soId);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 15f, 15f, 15f, 15f); // Landscape A4
        PdfWriter writer = PdfWriter.getInstance(doc, baos);
        doc.open();

        // ── Header ───────────────────────────────────────────
        addHeader(doc, so, customer, warehouse);

        // ── Items table ──────────────────────────────────────
        addItemsTable(doc, passItems);

        // ── Signature section ────────────────────────────────
        addSignatureSection(doc);

        doc.close();
        return baos.toByteArray();
    }

    // ── Header ──────────────────────────────────────────────────

    private void addHeader(Document doc, SalesOrderEntity so, CustomerEntity customer, WarehouseEntity warehouse) throws Exception {
        Font logoFont   = getFontBold(16, TITLE_CLR);
        Font titleFont  = getFontBold(16, BaseColor.BLACK);
        Font labelFont  = getFontBold(9,  BaseColor.BLACK);
        Font valueFont  = getFont(9, BaseColor.BLACK);

        // Logo + Title row
        PdfPTable headerTable = new PdfPTable(2);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{30f, 70f});

        // Logo cell
        PdfPCell logoCell = new PdfPCell(new Phrase("SHEENYCOS", logoFont));
        logoCell.setBorder(Rectangle.NO_BORDER);
        logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        logoCell.setPadding(4);
        headerTable.addCell(logoCell);

        // Title cell
        PdfPCell titleCell = new PdfPCell(new Phrase("PHIẾU XUẤT KHO", titleFont));
        titleCell.setBorder(Rectangle.NO_BORDER);
        titleCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        titleCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        titleCell.setPadding(4);
        headerTable.addCell(titleCell);

        doc.add(headerTable);

        // Divider line
        LineSeparator line = new LineSeparator();
        line.setLineColor(TITLE_CLR);
        line.setLineWidth(1f);
        doc.add(new Chunk(line));
        doc.add(Chunk.NEWLINE);

        // Info rows
        String dispatchDateStr = so.getUpdatedAt() != null
                ? so.getUpdatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                : LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        String customerCode = customer != null ? customer.getCustomerCode() : "";
        String customerName = customer != null ? customer.getCustomerName() : "";
        String customerAddr = customer != null ? customer.getAddress() : "";

        PdfPTable infoTable = new PdfPTable(2);
        infoTable.setWidthPercentage(100);
        infoTable.setWidths(new float[]{45f, 55f});
        infoTable.setSpacingAfter(6);

        addInfoRow(infoTable, "Ngày ĐH:", dispatchDateStr,   "Mã KH:",   customerCode, labelFont, valueFont);
        addInfoRow(infoTable, "Số phiếu:", so.getSoCode(),   "Tên KH:",  customerName, labelFont, valueFont);
        addInfoRow(infoTable, "Ghi chú:", so.getNote() != null ? so.getNote() : "", "Địa chỉ:", customerAddr, labelFont, valueFont);

        doc.add(infoTable);
    }

    private void addInfoRow(PdfPTable table, String lbl1, String val1, String lbl2, String val2,
                            Font labelFont, Font valueFont) {
        Paragraph p1 = new Paragraph();
        p1.add(new Chunk(lbl1 + " ", labelFont));
        p1.add(new Chunk(val1, valueFont));

        Paragraph p2 = new Paragraph();
        p2.add(new Chunk(lbl2 + " ", labelFont));
        p2.add(new Chunk(val2 != null ? val2 : "", valueFont));

        PdfPCell c1 = new PdfPCell(p1);
        c1.setBorder(Rectangle.NO_BORDER);
        c1.setPaddingBottom(2);

        PdfPCell c2 = new PdfPCell(p2);
        c2.setBorder(Rectangle.NO_BORDER);
        c2.setPaddingBottom(2);

        table.addCell(c1);
        table.addCell(c2);
    }

    // ── Items Table ──────────────────────────────────────────────

    private void addItemsTable(Document doc, List<PickingTaskItemEntity> passItems) throws Exception {
        Font thFont    = getFontBold(7.5f, BaseColor.BLACK);
        Font cellFont  = getFont(7.5f, BaseColor.BLACK);
        Font catFont   = getFontBold(8f, BaseColor.BLACK);
        Font totalFont = getFontBold(8f, BaseColor.BLACK);

        String[] headers = {"Stt", "Mã hàng", "Tên hàng", "Quy\ncách", "Dvt", "Thùng", "Chai lẻ", "Ghi chú", "Mã đóng gói", "NSX", "NHH"};
        float[]  widths  = {4f,    9f,         30f,         5f,          5f,    6f,      5f,         7f,        11f,           10f,   10f};

        PdfPTable table = new PdfPTable(headers.length);
        table.setWidthPercentage(100);
        table.setWidths(widths);
        table.setHeaderRows(1);
        table.setSpacingAfter(6);

        // Header row
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, thFont));
            cell.setBackgroundColor(HEADER_BG);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setPadding(3);
            cell.setBorderColor(BORDER_CLR);
            table.addCell(cell);
        }

        // Group by category (derive from lot/sku — fallback to single group)
        // Vì PickingTaskItem không có category, tạm gom theo skuId grouping
        // Trong thực tế category có thể được lấy từ SKU category table
        int stt = 1;
        int totalThung = 0;

        Map<String, List<PickingTaskItemEntity>> grouped = new LinkedHashMap<>();
        for (PickingTaskItemEntity item : passItems) {
            // Resolve category from sku (fallback "Hàng xuất kho")
            String cat = resolveCategory(item);
            grouped.computeIfAbsent(cat, k -> new ArrayList<>()).add(item);
        }

        for (Map.Entry<String, List<PickingTaskItemEntity>> entry : grouped.entrySet()) {
            String cat = entry.getKey();
            List<PickingTaskItemEntity> catItems = entry.getValue();

            // Category row (span all cols)
            PdfPCell catCell = new PdfPCell(new Phrase(cat, catFont));
            catCell.setColspan(headers.length);
            catCell.setBackgroundColor(CATEGORY_BG);
            catCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            catCell.setPadding(3);
            catCell.setBorderColor(BORDER_CLR);
            table.addCell(catCell);

            for (PickingTaskItemEntity item : catItems) {
                SkuEntity sku = skuRepository.findById(item.getSkuId()).orElse(null);
                InventoryLotEntity lot = item.getLotId() != null
                        ? inventoryLotRepository.findById(item.getLotId()).orElse(null) : null;

                int qty = item.getPickedQty() != null && item.getPickedQty().intValue() > 0
                        ? item.getPickedQty().intValue() : item.getRequiredQty().intValue();
                totalThung += qty;

                String skuCode  = sku != null ? sku.getSkuCode() : "";
                String skuName  = sku != null ? sku.getSkuName() : "";
                String packCode = sku != null ? sku.getSkuCode() : "";
                String mfgDate  = lot != null && lot.getManufactureDate() != null
                        ? lot.getManufactureDate().format(DateTimeFormatter.ofPattern("dd/MM/yy")) : "";
                String expDate  = lot != null && lot.getExpiryDate() != null
                        ? lot.getExpiryDate().format(DateTimeFormatter.ofPattern("dd/MM/yy")) : "";

                String[] rowData = {
                        String.valueOf(stt++),
                        skuCode,
                        skuName,
                        "",           // Quy cách — lấy từ SKU nếu có
                        "Thùng",
                        String.valueOf(qty),
                        "",           // Chai lẻ
                        "",           // Ghi chú
                        packCode,
                        mfgDate,
                        expDate
                };

                for (int i = 0; i < rowData.length; i++) {
                    PdfPCell cell = new PdfPCell(new Phrase(rowData[i], cellFont));
                    cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                    cell.setHorizontalAlignment(i == 2 ? Element.ALIGN_LEFT : Element.ALIGN_CENTER);
                    cell.setPadding(2);
                    cell.setBorderColor(BORDER_CLR);
                    table.addCell(cell);
                }
            }
        }

        // Total row
        PdfPCell blankCell = new PdfPCell(new Phrase("", totalFont));
        blankCell.setColspan(2);
        blankCell.setBackgroundColor(TOTAL_BG);
        blankCell.setBorderColor(BORDER_CLR);
        blankCell.setPadding(3);
        table.addCell(blankCell);

        PdfPCell totalLabelCell = new PdfPCell(new Phrase("Tổng cộng", totalFont));
        totalLabelCell.setBackgroundColor(TOTAL_BG);
        totalLabelCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        totalLabelCell.setBorderColor(BORDER_CLR);
        totalLabelCell.setPadding(3);
        table.addCell(totalLabelCell);

        PdfPCell blankCell2 = new PdfPCell(new Phrase("", totalFont));
        blankCell2.setColspan(2);
        blankCell2.setBackgroundColor(TOTAL_BG);
        blankCell2.setBorderColor(BORDER_CLR);
        blankCell2.setPadding(3);
        table.addCell(blankCell2);

        PdfPCell totalValCell = new PdfPCell(new Phrase(String.valueOf(totalThung), totalFont));
        totalValCell.setBackgroundColor(TOTAL_BG);
        totalValCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        totalValCell.setBorderColor(BORDER_CLR);
        totalValCell.setPadding(3);
        table.addCell(totalValCell);

        PdfPCell blankCell3 = new PdfPCell(new Phrase("", totalFont));
        blankCell3.setColspan(5);
        blankCell3.setBackgroundColor(TOTAL_BG);
        blankCell3.setBorderColor(BORDER_CLR);
        blankCell3.setPadding(3);
        table.addCell(blankCell3);

        doc.add(table);
    }

    // ── Signature Section ────────────────────────────────────────

    private void addSignatureSection(Document doc) throws Exception {
        Font thFont   = getFontBold(8f, BaseColor.BLACK);
        Font cellFont = getFont(8f, BaseColor.BLACK);

        PdfPTable sigTable = new PdfPTable(3);
        sigTable.setWidthPercentage(100);
        sigTable.setWidths(new float[]{30f, 50f, 20f});

        String[] sigHeaders = {"Nhân sự", "Ký tên xác nhận", "Họ và tên"};
        for (String h : sigHeaders) {
            PdfPCell cell = new PdfPCell(new Phrase(h, thFont));
            cell.setBackgroundColor(HEADER_BG);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setPadding(4);
            cell.setBorderColor(BORDER_CLR);
            sigTable.addCell(cell);
        }

        String[] roles = {"Người lập phiếu", "Kế toán xác nhận", "Người giao hàng", "Người nhận hàng"};
        for (String role : roles) {
            PdfPCell roleCell = new PdfPCell(new Phrase(role, cellFont));
            roleCell.setPadding(8);
            roleCell.setBorderColor(BORDER_CLR);
            sigTable.addCell(roleCell);

            PdfPCell sigCell = new PdfPCell(new Phrase("", cellFont));
            sigCell.setPadding(8);
            sigCell.setBorderColor(BORDER_CLR);
            sigTable.addCell(sigCell);

            PdfPCell nameCell = new PdfPCell(new Phrase("", cellFont));
            nameCell.setPadding(8);
            nameCell.setBorderColor(BORDER_CLR);
            sigTable.addCell(nameCell);
        }

        doc.add(sigTable);
    }

    // ── Helper ───────────────────────────────────────────────────

    private String resolveCategory(PickingTaskItemEntity item) {
        // Đây có thể được mở rộng để lấy category thực từ DB
        // Hiện tại dùng tên SKU prefix hoặc fallback
        try {
            SkuEntity sku = skuRepository.findById(item.getSkuId()).orElse(null);
            if (sku != null && sku.getSkuName() != null) {
                String name = sku.getSkuName().toLowerCase();
                if (name.contains("khuyến mãi") || name.contains("kèm")) return "Hàng kèm khuyến mãi";
                if (name.contains("tặng"))  return "Hàng khuyến mãi";
            }
        } catch (Exception ignored) {}
        return "Hàng xuất kho";
    }
}
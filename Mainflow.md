



# MAIN FLOW 0 — Onboarding & Access Control (đăng nhập, xác thực, phân quyền)

Mục tiêu: đảm bảo user vào đúng dashboard và chỉ thấy đúng chức năng.

## Luồng

User mở Login → nhập email/password

## System kiểm tra

user tồn tại, status ACTIVE

password đúng

nếu account chưa verify email → yêu cầu OTP verify

System gửi OTP email (nếu cần) → user nhập OTP → verified

System tạo session/token, ghi last_login_at

## Điều hướng

Keeper → Inbound/Putaway/Pick/Packing/Dispatch

Accountant → Receipt Posting, Outbound Create

Manager → Approval Queue, Master Data, Reports

Kết quả: user có phiên đăng nhập hợp lệ, menu theo role.



# MAIN FLOW 1 — Master Data Setup (chuẩn bị trước khi vận hành kho)

Mục tiêu: tạo nền dữ liệu để nhập/xuất chạy được (SKU, Category, Zone/Bin).

## Luồng thiết lập tối thiểu (Manager)

Tạo Category tree

Cấp 1: Home Care / Personal Care

Cấp 2 (tùy chọn): Nước giặt, Nước rửa chén, Dầu gội…

Mapping Category → Zone

Home Care → Z-HC

Personal Care → Z-PC

Defect/Hold → Z-HOLD

Tạo Zone/Location

Z-INB (Inbound staging)

Z-HC, Z-PC (storage)

Z-OUT (Outbound staging)

(optional) Z-HOLD

Aisle/Rack/Bin theo mã location

Tạo SKU

mỗi “mặt hàng + kiểu đóng gói” = 1 SKU (thùng)

bật Lot/HSD = TRUE

gán Category

Kết quả: có đủ dữ liệu để system gợi ý putaway/pick.



# MAIN FLOW 2 — Inbound: Nhập kho + Cất lên kệ (Putaway)

Mục tiêu: hàng về kho → ghi nhận đúng lot/HSD → tạo tồn → cất lên bin.

## Luồng thực tế theo vai trò

Keeper nhận hàng thực tế, để ở Z-INB

## Keeper tạo GRN (phiếu nhập thực tế)

SKU, Qty (thùng), Lot, HSD

Save Draft → Submit

Manager vào Approval Queue → Approve GRN

Accountant vào Receipt Posting → Post/Confirm

System tạo tồn tại Z-INB: Available tăng theo SKU/Lot/HSD

System tạo Putaway Task

## System gợi ý bin putaway

đọc Category SKU → ra Zone (Z-HC/Z-PC)

chọn bin trống theo rule (thứ tự mã / còn capacity)

## Keeper mở Putaway Task

thấy “From: Z-INB → To: WH01-ZHC-A01-R01-B01”

cất hàng lên kệ → Confirm Putaway

## System cập nhật tồn

Z-INB giảm

bin storage tăng (theo SKU/Lot/HSD)

GRN Closed

Kết quả: tồn đã nằm đúng bin cụ thể, sẵn sàng FEFO pick.



# MAIN FLOW 3 — Outbound Sales: Tạo đơn xuất bán → Duyệt → Reserve/Allocate

Mục tiêu: tạo đơn xuất bán theo SKU và “giữ hàng” trước khi thủ kho đi pick.

## Luồng

## Accountant tạo Outbound Sales Order (Draft)

customer/ship-to

lines: SKU + qty

Submit

Manager duyệt Outbound

## System chạy Allocate/Reserve

query tồn theo SKU (nhiều bin/lô)

lọc status hợp lệ (Available, không Hold/Expired)

sắp theo FEFO (HSD tăng dần)

reserve đủ qty hoặc báo thiếu

System tạo Pick Task/Pick List cho Keeper

Kết quả: tồn chuyển từ Available → Reserved (giữ cho đơn).



# MAIN FLOW 4 — Picking: Pick task detail theo line + Confirm Pick

Mục tiêu: thủ kho đi theo hướng dẫn, lấy đúng bin/lot/HSD, xác nhận từng line.

## Luồng

Keeper vào Pick Task List → chọn task → Start

## Màn Pick Task Detail hiển thị nhiều pick line

Location bin

SKU

Lot + HSD

Qty required

input Qty picked

nút Confirm line

## Keeper đi theo thứ tự line

đến đúng bin

lấy đúng Lot/HSD

nhập qty picked → Confirm line

Khi tất cả line confirmed → Complete Pick

## System cập nhật

Reserved giảm

Picked tăng

Order status: Picking → Picked

Kết quả: hàng đã pick xong (logic), chờ đóng gói.



# MAIN FLOW 5 — Packing: Confirm Pack → chuyển Outbound Staging

Mục tiêu: gom hàng đã pick thành kiện xuất, chuyển sang khu chờ xuất.

## Luồng

Keeper mở Packing Screen (từ đơn Picked)

Hệ thống liệt kê items đã picked theo SKU/Lot/HSD

Keeper thao tác đóng gói → Complete Pack

## System

Picked giảm

Packed/Staged tăng

chuyển location logic sang Z-OUT

Order status: Picked → Packed/Staged

Kết quả: đơn sẵn sàng xuất kho.



# MAIN FLOW 6 — Dispatch: Confirm Dispatch → trừ tồn kho (On-hand)

Mục tiêu: xác nhận hàng rời kho (scope dự án không cần “delivered”).

## Luồng

Keeper vào Dispatch Confirm

Chọn đơn Packed/Staged → bấm Confirm Dispatch

## System

Packed/Staged giảm

On-hand giảm đúng qty

ghi transaction history

Order status: Dispatched/Closed

Kết quả: hoàn tất xuất kho, tồn giảm chính thức.



# MAIN FLOW 7 — Exception Flow: Thiếu hàng khi pick (Short-pick)

Mục tiêu: xử lý khi bin thiếu/hư/không tìm thấy.

## Luồng

## Trong Pick Task Detail, Keeper gặp thiếu

nhập qty picked < required

bấm Report Issue (popup)

chọn lý do: thiếu hàng/không tìm thấy/hư hỏng…

System tạo issue & notify Manager (+ Accountant)

## Manager xử lý

Approve short pick (chấp nhận thiếu, giảm qty đơn)

hoặc Re-allocate (tìm bin/lô khác, tạo pick line bổ sung)

(optional) tạo kiểm kê/điều chỉnh nếu nghi thất thoát

## Keeper nhận kết quả

nếu re-allocate → pick tiếp line mới

nếu approve short → complete pick/pack/dispatch theo qty mới

Kết quả: đơn được xử lý hợp lệ, không làm sai tồn.



# MAIN FLOW 8 — Inventory Control: Kiểm kê → Điều chỉnh

Mục tiêu: đối soát tồn thực tế và hệ thống.

## Luồng

Keeper tạo Stocktake theo zone/bin hoặc theo SKU

Keeper đi đếm và nhập Counted qty

System tính chênh lệch (diff)

Manager Approve Adjustment

System cập nhật tồn + ghi log

Kết quả: tồn chuẩn lại, có lịch sử/audit.



# MAIN FLOW 9 — Returns: Hàng trả về kho

Mục tiêu: nhận hàng trả, phân loại OK/Hold/Defect/Expired, cập nhật tồn.

## Luồng

Keeper tạo Return Request (tham chiếu đơn nếu có)

Manager duyệt

## Keeper xử lý hàng trả

OK → nhập lại kho (về Z-INB rồi putaway, hoặc putaway trực tiếp)

Defect/Hold → đưa vào Z-HOLD

Expired → đánh Expired (không cho pick)

System cập nhật tồn, log movement

Kết quả: hàng trả được quản lý đúng trạng thái.



# MAIN FLOW 10 — Reporting & Audit

Mục tiêu: quản lý xem báo cáo và truy vết thao tác.

## Luồng

## Manager mở Reports

tồn kho, nhập xuất, hao hụt/điều chỉnh

Filter theo thời gian/warehouse/category/SKU

Export excel/pdf (tuỳ scope)

Audit log / transaction history cho phép truy vết: ai làm, lúc nào, từ đâu đến đâu, qty bao nhiêu




---
description: Quy trình Git và GitHub cho dự án
---

# Quy Trình Git và GitHub - Hướng Dẫn Chi Tiết

## 🎯 1. Thiết Lập Ban Đầu

### Cài đặt Git
```bash
# Kiểm tra Git đã cài chưa
git --version

# Cấu hình thông tin cá nhân
git config --global user.name "Tên của bạn"
git config --global user.email "email@example.com"

# Xem cấu hình hiện tại
git config --list
```

### Kết nối với GitHub
```bash
# Tạo SSH key (khuyến nghị)
ssh-keygen -t ed25519 -C "email@example.com"

# Copy public key và thêm vào GitHub Settings > SSH Keys
cat ~/.ssh/id_ed25519.pub
```

---

## 📦 2. Khởi Tạo Repository

### Tạo repo mới từ local
```bash
# Di chuyển vào thư mục dự án
cd C:\Users\nguye\Documents\SEP26Manager

# Khởi tạo Git repository
git init

# Thêm remote repository (GitHub)
git remote add origin https://github.com/Quang2k3/SEP26Manager.git

# Hoặc dùng SSH (khuyến nghị)
git remote add origin git@github.com:Quang2k3/SEP26Manager.git
```

### Clone repo có sẵn
```bash
# Clone qua HTTPS
git clone https://github.com/Quang2k3/SEP26Manager.git

# Clone qua SSH
git clone git@github.com:Quang2k3/SEP26Manager.git
```

---

## 🔄 3. Quy Trình Làm Việc Hàng Ngày

### Bước 1: Cập nhật code mới nhất
```bash
# Lấy thay đổi mới nhất từ remote
git pull origin main

# Hoặc nếu dùng branch khác
git pull origin develop
```

### Bước 2: Tạo branch mới cho feature/fix
```bash
# Tạo và chuyển sang branch mới
git checkout -b feature/ten-tinh-nang

# Ví dụ cụ thể
git checkout -b feature/user-authentication
git checkout -b fix/login-bug
git checkout -b refactor/database-optimization
```

### Bước 3: Làm việc và commit thay đổi
```bash
# Xem trạng thái file
git status

# Thêm file vào staging area
git add .                    # Thêm tất cả
git add file1.txt file2.txt  # Thêm file cụ thể
git add src/                 # Thêm thư mục

# Commit với message rõ ràng
git commit -m "feat: thêm chức năng đăng nhập"

# Commit dài hơn với mô tả
git commit -m "feat: thêm chức năng đăng nhập" -m "- Tạo form đăng nhập
- Xác thực JWT token
- Lưu session người dùng"
```

### Bước 4: Đẩy code lên GitHub
```bash
# Push lần đầu (tạo branch trên remote)
git push -u origin feature/ten-tinh-nang

# Push các lần sau
git push
```

---

## 📝 4. Convention Commit Messages (Quan Trọng!)

Sử dụng format chuẩn để dễ quản lý:

```
<type>: <description>

[optional body]
[optional footer]
```

### Các loại commit phổ biến:
- **feat**: Tính năng mới
  - `feat: thêm API đăng ký người dùng`
- **fix**: Sửa bug
  - `fix: sửa lỗi validation form login`
- **docs**: Cập nhật tài liệu
  - `docs: cập nhật README với hướng dẫn cài đặt`
- **style**: Format code (không ảnh hưởng logic)
  - `style: format code theo chuẩn ESLint`
- **refactor**: Tái cấu trúc code
  - `refactor: tách UserService thành các module nhỏ`
- **test**: Thêm/sửa tests
  - `test: thêm unit tests cho AuthController`
- **chore**: Công việc maintenance
  - `chore: cập nhật dependencies`

---

## 🌿 5. Quản Lý Branches

### Xem branches
```bash
# Xem branch local
git branch

# Xem tất cả branches (bao gồm remote)
git branch -a

# Xem branch với thông tin chi tiết
git branch -v
```

### Chuyển đổi branches
```bash
# Chuyển sang branch khác
git checkout main
git checkout develop

# Tạo và chuyển sang branch mới
git checkout -b feature/new-feature
```

### Xóa branches
```bash
# Xóa branch local (đã merge)
git branch -d feature/completed-feature

# Xóa branch local (force)
git branch -D feature/abandoned-feature

# Xóa branch trên remote
git push origin --delete feature/old-feature
```

---

## 🔀 6. Quy Trình Merge An Toàn (TRÁNH MẤT CODE!)

> [!CAUTION]
> Merge sai có thể khiến bạn mất code của mình hoặc đồng nghiệp! Luôn luôn follow quy trình này.

---

### 🛡️ Pre-Merge Checklist (QUAN TRỌNG!)

**Trước khi merge, hãy check:**

```bash
# ✅ 1. Đảm bảo code của bạn đã commit hết
git status
# Output phải là: "nothing to commit, working tree clean"

# ✅ 2. Đảm bảo đang ở đúng branch
git branch
# Dấu * phải ở branch bạn muốn làm việc

# ✅ 3. Pull code mới nhất từ remote
git pull origin feature/your-branch

# ✅ 4. Xem history để hiểu context
git log --oneline -10
```

> [!IMPORTANT]
> **KHÔNG BAO GIỜ** merge khi `git status` còn uncommitted changes! Bạn sẽ mất code!

---

### 💾 Backup Trước Khi Merge (BẮT BUỘC!)

#### **Method 1: Tạo backup branch (KHUYẾN NGHỊ!)**

```bash
# Đang ở feature branch của bạn
git checkout feature/payment-integration

# Tạo backup branch
git branch backup/payment-integration-2026-01-27
# Hoặc với timestamp
git branch backup/payment-integration-$(date +%Y%m%d-%H%M)

# Verify backup đã tạo
git branch | grep backup
```

#### **Method 2: Tạo tag backup**

```bash
# Tạo tag tại commit hiện tại
git tag backup-before-merge-main

# Xem tất cả tags
git tag | grep backup
```

#### **Method 3: Stash + Branch (cho uncommitted changes)**

```bash
# Nếu bạn có changes chưa commit
git stash save "backup before merge main - 2026-01-27"

# Xem stash list
git stash list
```

---

### 🔄 Quy Trình Merge Chuẩn - Không Mất Code

#### **Scenario 1: Merge main vào feature branch của BẠN**

> Đây là cách **AN TOÀN NHẤT** khi làm việc nhóm!

```bash
# ===== BƯỚC 1: CẬP NHẬT MAIN ===== 
git checkout main
git pull origin main
# Đảm bảo main là code mới nhất từ team

# ===== BƯỚC 2: TẠO BACKUP ===== 
git checkout feature/your-feature
git branch backup/your-feature-$(date +%Y%m%d)
# Backup branch hiện tại

# ===== BƯỚC 3: MERGE MAIN VÀO FEATURE ===== 
git merge main
# Git sẽ:
# - Nếu KHÔNG có conflict → Auto merge thành công ✅
# - Nếu CÓ conflict → Dừng lại, yêu cầu bạn resolve ⚠️

# ===== BƯỚC 4A: Nếu KHÔNG có conflict ===== 
git log --oneline -5  # Xem merge commit
git push origin feature/your-feature  # Push lên remote

# ===== BƯỚC 4B: Nếu CÓ conflict ===== 
# Xem file conflict
git status

# Mở từng file, sửa conflict
# (Xem section 12 - Merge Conflicts để biết cách sửa)

# Sau khi sửa xong TẤT CẢ conflicts:
git add .
git status  # Verify: "All conflicts fixed but you are still merging"
git commit -m "fix: merge main into feature/your-feature

Resolved conflicts in:
- src/controllers/UserController.js
- src/services/PaymentService.js"

git push origin feature/your-feature
```

#### **Scenario 2: Merge feature branch vào main (sau khi PR approved)**

```bash
# ===== TRƯỚC KHI MERGE ===== 
# 1. Đảm bảo feature branch đã update với main mới nhất
git checkout feature/completed-feature
git pull origin main
# Nếu có conflicts → Resolve trên feature branch TRƯỚC

# 2. Run tests
npm test           # Hoặc
pytest             # Hoặc
mvn test           # Tùy tech stack

# 3. Verify build thành công
npm run build      # Hoặc build command của bạn

# ===== MERGE VÀO MAIN ===== 
git checkout main
git pull origin main  # Update main

# TẠO BACKUP MAIN
git branch backup/main-before-merge-$(date +%Y%m%d)

# Merge (có 3 options)
# Option A: Merge thường (giữ history)
git merge feature/completed-feature

# Option B: Merge với --no-ff (luôn tạo merge commit)
git merge --no-ff feature/completed-feature

# Option C: Squash merge (gộp thành 1 commit)
git merge --squash feature/completed-feature
git commit -m "feat: add payment integration (#123)"

# Push lên GitHub
git push origin main
```

---

### 🧪 Testing Sau Khi Merge (BẮT BUỘC!)

```bash
# ===== BƯỚC 1: Build ===== 
npm run build            # Node.js
# hoặc
mvn clean install        # Java
# hoặc
dotnet build             # .NET

# ===== BƯỚC 2: Run Tests ===== 
npm test                 # Unit tests
npm run test:integration # Integration tests
npm run test:e2e         # E2E tests

# ===== BƯỚC 3: Local Testing ===== 
npm run dev              # Start dev server
# Manually test các features:
# - Features cũ vẫn hoạt động?
# - Features mới hoạt động?
# - Không có bugs mới?

# ===== BƯỚC 4: Smoke Test Checklist ===== 
```

**Checklist sau merge:**
- [ ] Application starts without errors
- [ ] Login functionality works
- [ ] Main features still functional
- [ ] New feature works as expected
- [ ] No console errors
- [ ] Database migrations ran successfully (nếu có)

---

### ⚡ Rebase - Alternative cho Merge (Sạch hơn nhưng phức tạp hơn)

> [!WARNING]
> Rebase **viết lại history**. KHÔNG dùng rebase trên branch public đã có người khác dùng!

#### **Khi nào dùng Rebase?**
- ✅ Feature branch cá nhân, chưa ai dùng
- ✅ Muốn history sạch, tuyến tính
- ❌ KHÔNG dùng trên main/develop
- ❌ KHÔNG dùng trên branch nhiều người cùng làm

#### **Quy trình Rebase an toàn:**

```bash
# ===== BƯỚC 1: BACKUP ===== 
git checkout feature/your-feature
git branch backup/your-feature-before-rebase

# ===== BƯỚC 2: UPDATE MAIN ===== 
git checkout main
git pull origin main

# ===== BƯỚC 3: REBASE ===== 
git checkout feature/your-feature
git rebase main

# Nếu có conflicts:
# 1. Sửa conflict trong file
# 2. git add <file>
# 3. git rebase --continue
# 4. Lặp lại cho đến hết conflicts

# ===== BƯỚC 4: FORCE PUSH ===== 
# Dùng --force-with-lease (an toàn hơn --force)
git push --force-with-lease origin feature/your-feature
```

#### **So sánh Merge vs Rebase:**

```diff
MERGE:
main:     A---B---C---D
               \       \
feature:        E---F---M (merge commit)

REBASE:
main:     A---B---C---D
                       \
feature:                E'---F' (commits được replay)
```

**Chọn lựa:**
- **Merge**: An toàn, giữ nguyên history, dễ hiểu
- **Rebase**: History sạch, tuyến tính, nhưng phức tạp hơn

---

### 🚨 Recovery: Recover Code Nếu Merge Sai

#### **Case 1: Vừa merge xong, chưa push**

```bash
# Undo merge commit cuối
git reset --hard HEAD~1

# Hoặc quay về commit cụ thể
git log --oneline -10  # Tìm commit hash
git reset --hard abc123
```

#### **Case 2: Đã merge VÀ push, nhưng phát hiện sai**

```bash
# Option A: Revert merge commit
git log --oneline -5  # Tìm merge commit
git revert -m 1 <merge-commit-hash>
git push origin main

# Option B: Reset về backup branch
git checkout main
git reset --hard backup/main-before-merge-2026-01-27
git push --force-with-lease origin main  # NGUY HIỂM! Cần permission
```

#### **Case 3: Mất code sau khi resolve conflict**

```bash
# Hủy merge đang làm dở
git merge --abort

# Restore từ backup branch
git checkout backup/your-feature-2026-01-27
git checkout -b feature/your-feature-recovered

# Compare với branch bị mất
git diff feature/your-feature-old feature/your-feature-recovered
```

#### **Case 4: Tìm lại code đã bị xóa (Git Reflog - Vũ khí tối thượng)**

```bash
# Xem tất cả thao tác Git gần đây
git reflog

# Output:
# abc123 HEAD@{0}: merge main: Merge made by the 'recursive' strategy
# def456 HEAD@{1}: commit: feat: add payment
# ghi789 HEAD@{2}: checkout: moving from main to feature/payment

# Quay về bất kỳ thời điểm nào
git reset --hard HEAD@{2}

# Hoặc tạo branch mới từ reflog
git checkout -b recovery-branch HEAD@{2}
```

---

### ✅ Best Practices - Merge Workflow

#### **1. LUÔN LUÔN tạo backup trước khi merge**
```bash
# Habit tốt:
git branch backup/$(git branch --show-current)-$(date +%Y%m%d)
git merge main
```

#### **2. Merge thường xuyên, nhỏ gọn**
```
❌ KHÔNG TốT:
- Code 2 tuần không merge → 500 files conflict

✅ TỐT:
- Mỗi ngày merge main vào feature branch
- Conflicts nhỏ, dễ handle
```

#### **3. Merge main vào feature, KHÔNG ngược lại**
```bash
✅ ĐÚNG:
git checkout feature/my-feature
git merge main  # Merge main VÀO feature

❌ SAI:
git checkout main
git merge feature/unfinished  # KHÔNG merge feature chưa xong vào main!
```

#### **4. Dùng Pull Request thay vì merge trực tiếp**
```
Main branch workflow:
1. Push feature branch lên GitHub
2. Tạo Pull Request
3. Code review
4. Merge qua GitHub UI (có backup tự động)
```

#### **5. Protected Branches trên GitHub**
```
Settings → Branches → Branch protection rules:
- ✅ Require pull request before merging
- ✅ Require approvals (at least 1)
- ✅ Require status checks to pass
- ✅ Do not allow bypassing the above settings
```

---

### 📊 Merge Strategy Summary

| Tình huống | Nên dùng | Lệnh |
|------------|----------|------|
| Update feature with latest main | **Merge** | `git merge main` |
| Feature hoàn thành → main | **PR + Merge** | GitHub PR |
| Clean up messy commits | **Squash** | `git merge --squash` |
| Personal branch, want clean history | **Rebase** | `git rebase main` |
| Emergency hotfix | **Cherry-pick** | `git cherry-pick <commit>` |

---

## 🚀 7. Pull Request Workflow (GitHub)

### Quy trình chuẩn:

1. **Tạo branch mới từ main/develop**
   ```bash
   git checkout main
   git pull origin main
   git checkout -b feature/new-feature
   ```

2. **Code và commit**
   ```bash
   # Làm việc...
   git add .
   git commit -m "feat: implement new feature"
   ```

3. **Push lên GitHub**
   ```bash
   git push -u origin feature/new-feature
   ```

4. **Tạo Pull Request trên GitHub**
   - Vào repository trên GitHub
   - Click "Compare & pull request"
   - Điền tiêu đề và mô tả rõ ràng
   - Assign reviewers
   - Thêm labels (bug, enhancement, etc.)

5. **Code Review**
   - Reviewer comment và request changes
   - Bạn push thêm commits để fix
   ```bash
   git add .
   git commit -m "fix: address review comments"
   git push
   ```

6. **Merge PR**
   - Sau khi approved, merge vào main/develop
   - Xóa branch sau khi merge

---

## 🔧 8. Các Lệnh Hữu Ích Khác

### Xem lịch sử commits
```bash
# Xem log đơn giản
git log

# Xem log một dòng
git log --oneline

# Xem log với graph
git log --oneline --graph --all

# Xem log của một file
git log -- path/to/file
```

### Hoàn tác thay đổi
```bash
# Bỏ file khỏi staging area
git restore --staged file.txt

# Hoàn tác thay đổi file (chưa commit)
git restore file.txt

# Hoàn tác commit cuối (giữ changes)
git reset --soft HEAD~1

# Hoàn tác commit cuối (xóa changes)
git reset --hard HEAD~1

# Tạo commit mới để revert commit cũ
git revert <commit-hash>
```

### Lưu công việc tạm thời
```bash
# Lưu changes vào stash
git stash

# Xem danh sách stash
git stash list

# Apply stash gần nhất
git stash pop

# Apply stash cụ thể
git stash apply stash@{0}
```

### So sánh thay đổi
```bash
# Xem changes chưa stage
git diff

# Xem changes đã stage
git diff --staged

# So sánh giữa branches
git diff main..feature/branch-name
```

---

## 🏗️ 9. Branching Strategy Phổ Biến

### Git Flow
```
main (production)
  └── develop (development)
       ├── feature/feature-1
       ├── feature/feature-2
       ├── hotfix/urgent-fix
       └── release/v1.0.0
```

### Quy trình:
- **main**: Code production, luôn stable
- **develop**: Code đang phát triển
- **feature/***: Tính năng mới (branch từ develop)
- **hotfix/***: Sửa bug khẩn cấp (branch từ main)
- **release/***: Chuẩn bị release (branch từ develop)

---

## ⚠️ 10. Best Practices

### ✅ Nên làm:
- Commit thường xuyên với messages rõ ràng
- Pull trước khi bắt đầu làm việc
- Tạo branch cho mỗi feature/fix
- Review code trước khi merge
- Viết commit messages có ý nghĩa
- Sử dụng `.gitignore` để loại trừ file không cần thiết

### ❌ Không nên:
- Commit trực tiếp vào main/develop
- Push code chưa test
- Commit file cấu hình cá nhân
- Force push lên main
- Tạo commit với message "fix", "update", "test"
- Push secrets/passwords lên GitHub

---

## 📋 11. File .gitignore Mẫu

Tạo file `.gitignore` trong root project:

```gitignore
# Dependencies
node_modules/
vendor/

# Build outputs
dist/
build/
*.exe
*.dll

# IDE
.vscode/
.idea/
*.swp
*.swo

# OS
.DS_Store
Thumbs.db

# Environment variables
.env
.env.local

# Logs
*.log
logs/

# Database
*.db
*.sqlite

# Temp files
tmp/
temp/
*.tmp
```

---

## 🆘 12. Xử Lý Merge Conflicts (Xung Đột Code)

### 🔴 Khi nào xảy ra conflicts?

**Tình huống phổ biến**: 2 người cùng sửa cùng 1 file, cùng 1 vùng code

**Ví dụ thực tế:**
- Bạn A: Sửa hàm `login()` ở dòng 15-20
- Bạn B: Cũng sửa hàm `login()` ở dòng 15-20
- Git không biết nên giữ code của ai → **CONFLICT!**

---

### 📖 Hiểu Conflict Markers

Khi có conflict, Git sẽ đánh dấu trong file như sau:

```javascript
<<<<<<< HEAD (Your current changes)
function login(username, password) {
    return authenticateUser(username, password);
}
=======
function login(email, pwd) {
    return validateAndLogin(email, pwd);
}
>>>>>>> feature/new-login (Incoming changes)
```

**Giải thích:**
- `<<<<<<< HEAD`: Code hiện tại của BẠN
- `=======`: Phân cách giữa 2 versions
- `>>>>>>> feature/new-login`: Code từ branch khác (đồng nghiệp)

---

### 🛠️ Quy Trình Giải Quyết Conflict (Chi Tiết)

#### **Bước 1: Phát hiện conflict**

```bash
# Pull code từ main
git pull origin main

# Nếu có conflict, Git sẽ báo:
# CONFLICT (content): Merge conflict in src/auth.js
# Automatic merge failed; fix conflicts and then commit the result.
```

#### **Bước 2: Xem file nào bị conflict**

```bash
git status

# Output:
# Unmerged paths:
#   (use "git add <file>..." to mark resolution)
#         both modified:   src/auth.js
#         both modified:   src/user.js
```

#### **Bước 3: Mở file và phân tích**

**File `src/auth.js` trước khi sửa:**
```javascript
import bcrypt from 'bcrypt';

<<<<<<< HEAD
// Code của BẠN
function authenticateUser(username, password) {
    const user = findUserByUsername(username);
    if (!user) return null;
    
    return bcrypt.compare(password, user.passwordHash);
}
=======
// Code của ĐỒNG NGHIỆP
function authenticateUser(email, password) {
    const user = database.getUserByEmail(email);
    if (!user) throw new Error('User not found');
    
    const isValid = bcrypt.compareSync(password, user.hash);
    return isValid ? user : null;
}
>>>>>>> feature/email-login
```

#### **Bước 4: Quyết định cách giải quyết**

Bạn có 3 lựa chọn:

**Option 1: Giữ code của BẠN**
```javascript
// Xóa hết conflict markers và code của người kia
function authenticateUser(username, password) {
    const user = findUserByUsername(username);
    if (!user) return null;
    
    return bcrypt.compare(password, user.passwordHash);
}
```

**Option 2: Giữ code của ĐỒNG NGHIỆP**
```javascript
// Xóa code của bạn và markers
function authenticateUser(email, password) {
    const user = database.getUserByEmail(email);
    if (!user) throw new Error('User not found');
    
    const isValid = bcrypt.compareSync(password, user.hash);
    return isValid ? user : null;
}
```

**Option 3: KẾT HỢP cả 2 (KHUYẾN NGHỊ!)**
```javascript
// Lấy ý tưởng hay từ cả 2 bên
function authenticateUser(email, password) {
    // Dùng email thay vì username (từ đồng nghiệp)
    const user = database.getUserByEmail(email);
    
    // Giữ error handling tốt hơn
    if (!user) throw new Error('User not found');
    
    // Dùng async từ code cũ (tốt hơn sync)
    const isValid = await bcrypt.compare(password, user.passwordHash);
    return isValid ? user : null;
}
```

#### **Bước 5: Mark as resolved**

```bash
# Sau khi sửa xong, add file
git add src/auth.js
git add src/user.js

# Kiểm tra lại
git status
# Output: All conflicts fixed but you are still merging.
```

#### **Bước 6: Commit merge**

```bash
# Commit với message rõ ràng
git commit -m "fix: resolve merge conflicts in auth module

- Combined username and email login approaches
- Kept async bcrypt.compare from main
- Added error handling from feature/email-login"

# Push lên remote
git push origin feature/your-branch
```

---

### 🎯 Ví Dụ Thực Tế: 2 Người Sửa Cùng File

**Scenario:**
- **Bạn**: Đang làm việc trên `feature/add-validation`
- **Đồng nghiệp**: Push code lên `main` trước bạn
- Cả 2 đều sửa file `UserController.java`

**Quy trình:**

```bash
# 1. Bạn đang trên feature branch
git checkout feature/add-validation

# 2. Pull main về để merge
git pull origin main
# → CONFLICT!

# 3. Xem conflicts
git status
# both modified:   src/controllers/UserController.java

# 4. Mở file UserController.java
```

**File conflict:**
```java
<<<<<<< HEAD
public User createUser(UserDTO dto) {
    // Validation của BẠN
    if (dto == null || dto.getEmail() == null) {
        throw new ValidationException("Email is required");
    }
    
    validator.validate(dto);
    return userService.create(dto);
}
=======
public User createUser(UserDTO userDto) {
    // Code của ĐỒNG NGHIỆP
    if (userDto == null) {
        return null;  // Silent fail
    }
    
    User user = userMapper.toEntity(userDto);
    return userRepository.save(user);
}
>>>>>>> main
```

**Giải quyết thông minh:**
```java
// KẾT HỢP cả 2: validation + mapping
public User createUser(UserDTO userDto) {
    // Validation từ code của bạn (TỐT HƠN return null)
    if (userDto == null || userDto.getEmail() == null) {
        throw new ValidationException("User data and email are required");
    }
    
    // Additional validation
    validator.validate(userDto);
    
    // Mapping approach từ đồng nghiệp (CLEAN HƠN)
    User user = userMapper.toEntity(userDto);
    return userRepository.save(user);
}
```

```bash
# 5. Mark resolved
git add src/controllers/UserController.java

# 6. Commit
git commit -m "fix: merge main into add-validation branch

- Combined validation logic with mapping approach
- Kept ValidationException (better than silent fail)
- Used userMapper pattern from main branch"

# 7. Push
git push origin feature/add-validation
```

---

### 🧰 Tools Hỗ Trợ Giải Quyết Conflicts

#### **1. VS Code** (Built-in)
- Tự động detect conflicts
- Hiện buttons: `Accept Current Change | Accept Incoming Change | Accept Both`
- Rất trực quan và dễ dùng!

#### **2. Git Commands**
```bash
# Chọn giữ code của mình (ours)
git checkout --ours path/to/file

# Chọn giữ code của người khác (theirs)
git checkout --theirs path/to/file

# Xem diff 3-way
git diff --ours
git diff --theirs
```

#### **3. Merge Tools**
```bash
# Sử dụng merge tool (KDiff3, Meld, etc.)
git mergetool

# Cấu hình VS Code làm merge tool
git config --global merge.tool vscode
git config --global mergetool.vscode.cmd 'code --wait $MERGED'
```

---

### ✅ Best Practices: TRÁNH Conflicts

#### **1. Pull thường xuyên**
```bash
# ĐẦU MỖI NGÀY làm việc
git checkout main
git pull origin main
git checkout feature/your-branch
git merge main

# HOẶC rebase
git rebase main
```

#### **2. Chia nhỏ công việc**
- 1 feature = 1 branch
- Commit nhỏ, thường xuyên
- Merge PR sớm, đừng để branch sống lâu

#### **3. Giao tiếp với team**
```
❌ KHÔNG NÊN:
- Bạn và đồng nghiệp im lặng code cùng file 1 tuần

✅ NÊN:
- "Hey, mình đang sửa UserController nhé, bạn tránh file này"
- Dùng Jira/Trello để assign tasks rõ ràng
```

#### **4. Code organization**
```javascript
❌ Dễ conflict:
// Tất cả logic trong 1 file lớn
src/
  └── app.js (2000 lines)

✅ Khó conflict:
// Tách thành modules nhỏ
src/
  ├── auth/
  │   ├── login.js
  │   └── register.js
  ├── users/
  │   ├── profile.js
  │   └── settings.js
```

#### **5. Branch protection rules** (GitHub)
- Bắt buộc Pull Request
- Require review trước khi merge
- Auto-run tests
- Chặn force push lên main

---

### 🚨 Các Lỗi Thường Gặp Khác

#### **Đã commit nhầm file**
```bash
# Bỏ file khỏi commit cuối (giữ changes)
git reset HEAD~1 path/to/file
git commit --amend

# Hoặc tạo commit mới
git rm --cached sensitive-file.txt
git commit -m "chore: remove sensitive file"
```

#### **Push bị từ chối**
```bash
# Lỗi: ! [rejected] main -> main (fetch first)
# Nghĩa là: Có commits mới trên remote chưa có ở local

# Giải pháp 1: Pull và merge
git pull origin main

# Giải pháp 2: Pull và rebase (sạch hơn)
git pull --rebase origin main
```

#### **Muốn hủy merge đang làm dở**
```bash
# Nếu conflict quá phức tạp, muốn bắt đầu lại
git merge --abort

# Hoặc với rebase
git rebase --abort
```

---

### 💡 Tips Chuyên Nghiệp

**1. Xem ai sửa dòng nào**
```bash
# Git blame để biết ai viết code này
git blame src/auth.js

# Xem chi tiết với author
git blame -L 10,20 src/auth.js
```

**2. Chat với người conflict**
```bash
# Xem author của conflict
git log --oneline --graph feature/their-branch

# Liên hệ họ:
"Hey Nam, mình thấy code của bạn thay đổi hàm login,
mình cũng đang sửa chỗ đó. Mình họp nhanh để sync nhé!"
```

**3. Practice conflict resolution**
```bash
# Tạo branch test để thử nghiệm
git checkout -b test-merge
git merge some-branch

# Nếu sai, bỏ đi và thử lại
git merge --abort
```

---

## 📚 Tài Nguyên Học Thêm

- [Git Documentation](https://git-scm.com/doc)
- [GitHub Guides](https://guides.github.com/)
- [Conventional Commits](https://www.conventionalcommits.org/)
- [Git Flow](https://nvie.com/posts/a-successful-git-branching-model/)

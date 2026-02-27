package org.example.sep26management.application.constants;

/**
 * Centralized message constants for the application.
 * All user-facing messages should be defined here for consistency and
 * maintainability.
 */
public final class MessageConstants {

    private MessageConstants() {
        // Prevent instantiation
    }

    // ==================== Auth Messages ====================
    public static final String LOGIN_SUCCESS = "Login successful";
    public static final String LOGOUT_SUCCESS = "Logged out successfully";
    public static final String NOT_AUTHENTICATED = "Not authenticated";
    public static final String INVALID_CREDENTIALS = "Invalid account or password.";
    public static final String ACCOUNT_DISABLED = "Account is disabled.";
    public static final String VERIFICATION_REQUIRED = "Verification required. OTP has been sent to your email.";
    public static final String CURRENT_USER_SUCCESS = "Current user retrieved successfully";
    public static final String USER_NOT_FOUND = "User not found";
    public static final String USER_ID_NOT_FOUND = "User ID not found in authentication";

    // ==================== OTP Messages ====================
    public static final String OTP_SENT = "OTP has been sent to your email. Please check your inbox.";
    public static final String OTP_RESENT = "A new OTP has been sent to your email. Please check your inbox.";
    public static final String OTP_INVALID = "Invalid or expired OTP code. Please try again.";
    public static final String OTP_EXPIRED = "OTP expired or not found. Please request a new OTP";
    public static final String OTP_VERIFIED = "Email verified successfully. You are now logged in.";
    public static final String OTP_COOLDOWN = "Please wait %d seconds before requesting a new OTP";
    public static final String OTP_LOCKED = "Too many failed attempts. Please try again in %d minutes";

    // ==================== Profile Messages ====================
    public static final String PROFILE_SUCCESS = "Profile retrieved successfully";
    public static final String PROFILE_UPDATED = "Profile updated successfully.";
    public static final String PROFILE_LOAD_FAILED = "Unable to load profile information.";
    public static final String PASSWORD_CHANGED = "Password changed successfully";
    public static final String PASSWORD_INCORRECT = "Current password is incorrect.";
    public static final String PASSWORD_MISMATCH = "New password and confirmation do not match.";
    public static final String PASSWORD_SAME = "New password must be different from current password.";

    // ==================== File/Avatar Messages ====================
    public static final String FILE_EMPTY = "The uploaded file is empty.";
    public static final String FILE_TOO_LARGE = "File size must be less than 5MB";
    public static final String FILE_INVALID_NAME = "Invalid file name";
    public static final String FILE_NOT_IMAGE = "Please select an image file";
    public static final String AVATAR_SAVE_FAILED = "Failed to save avatar image";

    // ==================== Pending Token Messages ====================
    public static final String INVALID_PENDING_TOKEN = "Invalid or expired verification token. Please login again.";

    // ==================== User Management Messages ====================
    public static final String USER_CREATED_SUCCESS = "User created successfully";
    public static final String USER_EMAIL_EXISTS = "Email already exists";
    public static final String ROLE_NOT_FOUND = "Role not found: %s";
    public static final String EXPIRE_DATE_REQUIRED = "Expire date is required for temporary accounts";
    public static final String INVALID_EXPIRE_DATE = "Expire date must be in the future";

    // Role assignment messages
    public static final String ROLE_ASSIGNED_SUCCESS = "Role assigned successfully";
    public static final String USER_NOT_FOUND_FOR_ROLE_ASSIGNMENT = "Target user not found";
    public static final String ROLE_ASSIGNMENT_FAILED = "Failed to assign role";
    public static final String SAME_ROLE_ASSIGNMENT = "User already has this role";

    // Status management messages
    public static final String STATUS_CHANGED_SUCCESS = "User status changed successfully";
    public static final String USER_NOT_FOUND_FOR_STATUS_CHANGE = "Target user not found";
    public static final String STATUS_CHANGE_FAILED = "Failed to change user status";
    public static final String SAME_STATUS_ASSIGNMENT = "User already has this status";
    public static final String INVALID_STATUS_TRANSITION = "Invalid status transition";
    public static final String ACCOUNT_DEACTIVATED = "Your account has been deactivated. Please contact your administrator.";

    // ==================== Error Messages ====================
    public static final String AUTH_FAILED = "Authentication failed";
    public static final String ACCESS_DENIED = "You do not have permission to perform this action!";
    public static final String UNEXPECTED_ERROR = "An unexpected error occurred. Please try again later.";

    // ==================== Category Messages ====================
    public static final String CATEGORY_CREATED_SUCCESS = "Category created successfully";
    public static final String CATEGORY_UPDATED_SUCCESS = "Category updated successfully";
    public static final String CATEGORY_RETRIEVED_SUCCESS = "Category retrieved successfully";
    public static final String CATEGORY_LIST_SUCCESS = "Categories retrieved successfully";
    public static final String CATEGORY_NOT_FOUND = "Category not found with ID: %s";
    public static final String CATEGORY_CODE_EXISTS = "Category code '%s' already exists";
    public static final String CATEGORY_PARENT_NOT_FOUND = "Parent category not found with ID: %s";
    public static final String CATEGORY_PARENT_INACTIVE = "Cannot assign to an inactive parent category";
    public static final String CATEGORY_SELF_PARENT = "A category cannot be its own parent";
    public static final String CATEGORY_CIRCULAR_REFERENCE = "Circular parent-child reference detected";

    // ==================== Category Deactivate & Tree ====================
    public static final String CATEGORY_DEACTIVATED_SUCCESS = "Category deactivated successfully";
    public static final String CATEGORY_ALREADY_INACTIVE = "Category is already inactive";
    public static final String CATEGORY_HAS_ACTIVE_CHILDREN = "Cannot deactivate: category has %d active sub-categories. Deactivate them first.";
    public static final String CATEGORY_TREE_SUCCESS = "Category tree retrieved successfully";

    // ==================== Map Category to Zone (Convention) ====================
    public static final String CZM_MAPPED_SUCCESS = "Category successfully mapped to zone";
    public static final String CZM_CATEGORY_INACTIVE = "Cannot map: category is inactive (BR-CAT-08)";
    public static final String CZM_ZONE_NOT_FOUND_CONVENTION = "Zone '%s' not found in warehouse %s. Please create zone with code '%1$s' first.";
    public static final String CZM_ZONE_INACTIVE = "Cannot map: zone '%s' is inactive (BR-CAT-08)";

    // ==================== SKU Messages ====================
    public static final String SKU_NOT_FOUND = "SKU not found with ID: %s";
    public static final String SKU_CATEGORY_ASSIGNED_SUCCESS = "Category assigned to SKU successfully";
    public static final String SKU_CATEGORY_INACTIVE = "Cannot assign an inactive category to SKU (BR-CAT-08)";
    public static final String SKU_SAME_CATEGORY = "SKU already belongs to this category";
    public static final String SKU_DETAIL_SUCCESS = "SKU detail retrieved successfully";

    // ==================== SKU Search Messages (UC-B06) ====================
    public static final String SKU_SEARCH_SUCCESS = "SKU search completed successfully";
    public static final String SKU_SEARCH_NO_RESULT = "No SKUs found matching your criteria.";

    // ==================== SKU Threshold Messages (UC-B07) ====================
    public static final String THRESHOLD_UPDATED_SUCCESS = "Thresholds updated successfully";
    public static final String THRESHOLD_NOT_FOUND = "Threshold configuration not found";
    public static final String THRESHOLD_MUST_BE_POSITIVE = "Please enter valid positive integers.";
    public static final String THRESHOLD_MIN_MUST_BE_LESS_THAN_MAX = "Min threshold must be lower than Max threshold.";
    public static final String SKU_INACTIVE = "SKU is not active";

    // ==================== Import SKU Messages (UC-B08) ====================
    public static final String IMPORT_SKU_SUCCESS = "SKU import completed";
    public static final String IMPORT_INVALID_FILE_FORMAT = "Invalid file format. Please use Excel or CSV.";
    public static final String IMPORT_FILE_TOO_LARGE = "File size exceeds 5MB limit.";
    public static final String IMPORT_TOO_MANY_ROWS = "File exceeds maximum 1,000 rows per import batch.";

    // ==================== Zone Messages (UC-LOC-01) ====================
    public static final String ZONE_CREATED_SUCCESS = "Zone created successfully";
    public static final String ZONE_CODE_DUPLICATE = "Zone Code '%s' already exists in this warehouse.";

    // ==================== Warehouse Messages ====================
    public static final String WAREHOUSE_NOT_FOUND = "Warehouse not found with ID: %s";

    // ==================== THÊM VÀO MessageConstants.java ====================

    // ==================== Location Messages ====================
    public static final String LOCATION_CREATED_SUCCESS        = "Location created successfully";
    public static final String LOCATION_UPDATED_SUCCESS        = "Location updated successfully";
    public static final String LOCATION_DEACTIVATED_SUCCESS    = "Location deactivated successfully";
    public static final String LOCATION_LIST_SUCCESS           = "Locations retrieved successfully";
    public static final String LOCATION_NOT_FOUND              = "Location not found with ID: %s";
    public static final String LOCATION_CODE_DUPLICATE         = "Location Code '%s' already exists in this zone.";
    public static final String LOCATION_ZONE_INACTIVE          = "Cannot create location under an inactive zone.";
    public static final String LOCATION_ALREADY_INACTIVE       = "Location is already inactive.";
    public static final String LOCATION_HAS_INVENTORY          = "Cannot deactivate location: it still contains inventory.";
    public static final String LOCATION_HAS_ACTIVE_CHILDREN    = "Cannot deactivate location: it has active child locations. Deactivate children first.";
    public static final String LOCATION_CAPACITY_BELOW_CURRENT = "New capacity cannot be less than current occupied quantity.";
    public static final String LOCATION_INVALID_HIERARCHY      = "AISLE must not have a parent location.";
    public static final String LOCATION_PARENT_REQUIRED        = "%s requires a parent of type %s.";
    public static final String LOCATION_INVALID_PARENT_TYPE    = "%s requires a parent of type %s, but got %s.";
    public static final String LOCATION_PARENT_DIFFERENT_ZONE  = "Parent location must belong to the same zone.";
    public static final String LOCATION_PARENT_INACTIVE        = "Cannot create location under an inactive parent.";

    // ==================== Zone Messages ====================
    public static final String ZONE_NOT_FOUND = "Zone not found with ID: %s";
}

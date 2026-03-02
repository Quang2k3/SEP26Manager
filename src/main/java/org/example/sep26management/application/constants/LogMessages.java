package org.example.sep26management.application.constants;

/**
 * Centralized log message constants for the application.
 * All log messages are defined here to improve maintainability and consistency.
 * 
 * Message naming convention: {MODULE}_{ACTION}_{DETAIL}
 * - MODULE: AUTH, OTP, EMAIL, PROFILE, JWT, etc.
 * - ACTION: verb describing what's happening (ATTEMPT, SENT, FAILED, etc.)
 * - DETAIL: additional context if needed
 * 
 * Placeholders use SLF4J syntax: {} for parameters
 */
public final class LogMessages {

    private LogMessages() {
        // Prevent instantiation
    }

    // ============================================================
    // APPLICATION STARTUP
    // ============================================================
    public static final String APP_SEPARATOR = "=================================================================";
    public static final String APP_STARTED_SUCCESS = " WAREHOUSE MANAGEMENT SYSTEM STARTED SUCCESSFULLY!";
    public static final String APP_SWAGGER_UI = " Swagger UI: http://localhost:8080/api/swagger-ui/index.html";
    public static final String APP_DEFAULT_LOGIN = " Default Login:";
    public static final String APP_DEFAULT_EMAIL = "   Email: admin@warehouse.com";
    public static final String APP_DEFAULT_PASSWORD = "   Password: Admin@123";

    // ============================================================
    // AUTHENTICATION
    // ============================================================
    public static final String AUTH_LOGIN_ATTEMPT = "Login attempt for email: {}";
    public static final String AUTH_LOGIN_REQUEST_FROM_IP = "Login request from IP: {} for email: {}";
    public static final String AUTH_FIRST_LOGIN_OTP_REQUIRED = "First login detected, OTP verification required for user: {}";
    public static final String AUTH_COMPLETING_OTP_VERIFICATION = "Completing OTP verification for first login: {}";
    public static final String AUTH_FIRST_LOGIN_COMPLETED = "First login OTP verification completed for user: {}";
    public static final String AUTH_LOGOUT = "Logout for user ID: {}";
    public static final String AUTH_LOGOUT_REQUEST = "Logout request from user ID: {}";
    public static final String AUTH_FETCHING_CURRENT_USER = "Fetching current user for ID: {}";

    // Auth warnings
    public static final String AUTH_LOGIN_FAILED_USER_NOT_FOUND = "Login failed: User not found for email: {}";
    public static final String AUTH_LOGIN_FAILED_INVALID_PASSWORD = "Login failed: Invalid password for email: {}, failed attempts: {}";

    // ============================================================
    // OTP (One-Time Password)
    // ============================================================
    public static final String OTP_GENERATING = "Generating OTP for email: {}";
    public static final String OTP_GENERATED = "Generated OTP: {} for email: {}";
    public static final String OTP_STORED_REDIS = "OTP stored in Redis with TTL {} minutes";
    public static final String OTP_SENT_SUCCESS = "OTP sent successfully to email: {}";
    public static final String OTP_VERIFYING = "Verifying OTP for email: {}";
    public static final String OTP_VERIFIED_SUCCESS = "OTP verified successfully for email: {}";

    // OTP warnings
    public static final String OTP_NOT_FOUND_OR_EXPIRED = "OTP not found or expired for email: {}";
    public static final String OTP_INVALID_ATTEMPT = "Invalid OTP attempt for email: {}";
    public static final String OTP_EMAIL_LOCKED_OUT = "Email {} locked out for {} minutes due to {} failed attempts";

    // OTP debug
    public static final String OTP_COOLDOWN_SET = "Cooldown set for {} seconds";
    public static final String OTP_FAILED_ATTEMPTS = "Failed OTP attempts for {}: {}/{}";
    public static final String OTP_RESET_FAILED_ATTEMPTS = "Reset failed attempts for email: {}";

    // ============================================================
    // EMAIL SERVICE
    // ============================================================
    public static final String EMAIL_OTP_SENT_SUCCESS = "OTP email sent successfully to: {}";
    public static final String EMAIL_WELCOME_SENT_SUCCESS = "Welcome email sent successfully to: {}";
    public static final String EMAIL_STATUS_CHANGE_SENT_SUCCESS = "Status change email sent successfully to: {}";

    // Email errors
    public static final String EMAIL_FROM_EMPTY = "spring.mail.username is empty. Check mail config/env. fromEmail='{}'";
    public static final String EMAIL_TO_EMPTY = "toEmail is empty";
    public static final String EMAIL_OTP_SEND_FAILED = "Failed to send OTP email to: {}";
    public static final String EMAIL_WELCOME_SEND_FAILED = "Failed to send welcome email to: {} - Error: {}";
    public static final String EMAIL_STATUS_CHANGE_SEND_FAILED = "Failed to send status change email to: {} - Error: {}";

    // Email warnings (for testing)
    public static final String EMAIL_OTP_CODE_FOR_TESTING = "OTP code for testing: {}";
    public static final String EMAIL_TEMP_PASSWORD_FOR_TESTING = "Temp password for testing: {}";

    // ============================================================
    // PROFILE SERVICE
    // ============================================================
    public static final String PROFILE_FETCHING = "Fetching profile for user ID: {}";
    public static final String PROFILE_UPDATING = "Updating profile for user ID: {}";
    public static final String PROFILE_CHANGING_PASSWORD = "Changing password for user ID: {}";

    // Profile errors
    public static final String PROFILE_ERROR_GETTING = "Error getting profile: {}";
    public static final String PROFILE_ERROR_UPDATING = "Error updating profile: {}";
    public static final String PROFILE_ERROR_CHANGING_PASSWORD = "Error changing password: {}";
    public static final String PROFILE_ERROR_SAVING_AVATAR = "Error saving avatar file";

    // ============================================================
    // AUDIT LOG SERVICE
    // ============================================================
    public static final String AUDIT_SAVED = "Audit log saved: {} - {}";
    public static final String AUDIT_WITH_VALUES_SAVED = "Audit log with values saved: {} - {}";
    public static final String AUDIT_FAILED_LOGIN_LOGGED = "Failed login logged: {}";

    // Audit errors
    public static final String AUDIT_SAVE_FAILED = "Failed to save audit log";
    public static final String AUDIT_SAVE_WITH_VALUES_FAILED = "Failed to save audit log with values";
    public static final String AUDIT_FAILED_LOGIN_LOG_ERROR = "Failed to log failed login";

    // ============================================================
    // OTP CONTROLLER
    // ============================================================
    public static final String OTP_CONTROLLER_VERIFICATION_REQUEST = "OTP verification request for email: {}";
    public static final String OTP_CONTROLLER_VERIFICATION_SUCCESS = "Email verification successful for: {}";
    public static final String OTP_CONTROLLER_RESEND_REQUEST = "OTP resend request for email: {}";
    public static final String OTP_CONTROLLER_RESEND_SUCCESS = "OTP resent successfully to: {}";

    // OTP Controller warnings
    public static final String OTP_CONTROLLER_INVALID_PENDING_TOKEN = "Invalid pending token for OTP verification: {}";
    public static final String OTP_CONTROLLER_INVALID_OTP = "Invalid OTP provided for email: {}";
    public static final String OTP_CONTROLLER_INVALID_PENDING_TOKEN_RESEND = "Invalid pending token for OTP resend: {}";

    // ============================================================
    // REDIS CONFIGURATION
    // ============================================================
    public static final String REDIS_CONFIGURING = "Configuring RedisTemplate for OTP storage";
    public static final String REDIS_CONFIGURED_SUCCESS = "RedisTemplate configured successfully";

    // ============================================================
    // JWT (JSON Web Token)
    // ============================================================
    public static final String JWT_INVALID_TOKEN = "Invalid JWT token";
    public static final String JWT_EXPIRED_TOKEN = "Expired JWT token";
    public static final String JWT_UNSUPPORTED_TOKEN = "Unsupported JWT token";
    public static final String JWT_CLAIMS_EMPTY = "JWT claims string is empty";
    public static final String JWT_AUTH_SET_FAILED = "Could not set user authentication in security context";

    // ============================================================
    // USER MANAGEMENT SERVICE
    // ============================================================
    public static final String USER_CREATING = "Creating user with email: {}";
    public static final String USER_CREATED = "User created successfully with ID: {}";
    public static final String USER_CREATION_FAILED = "Failed to create user: {}";
    public static final String USER_EMAIL_DUPLICATE = "Attempted to create user with duplicate email: {}";

    // User role assignment
    public static final String USER_ROLE_ASSIGNING = "Assigning role {} to user ID: {} by manager ID: {}";
    public static final String USER_ROLE_ASSIGNED = "Role assigned successfully to user ID: {} - Old: {}, New: {}";
    public static final String USER_ROLE_ASSIGNMENT_FAILED = "Failed to assign role to user ID: {} - Error: {}";
    public static final String EMAIL_ROLE_CHANGE_SENT_SUCCESS = "Role change email sent successfully to: {}";
    public static final String EMAIL_ROLE_CHANGE_SEND_FAILED = "Failed to send role change email to: {} - Error: {}";

    // ============================================================
    // USER MANAGEMENT CONTROLLER
    // ============================================================
    public static final String USER_LIST_REQUEST = "Get user list request - keyword: {}, status: {}, page: {}, size: {}";
    public static final String USER_LIST_FETCH_FAILED = "Failed to get user list: {}";
    public static final String USER_ROLE_ASSIGNMENT_REQUEST = "Manager ID: {} is assigning role {} to user ID: {}";
    public static final String USER_ROLE_ASSIGNMENT_CONTROLLER_FAILED = "Failed to assign role to user {}: {}";
    public static final String USER_LIST_FETCHING = "Fetching user list - keyword: {}, status: {}, page: {}, size: {}";
    public static final String USER_ROLE_ALREADY_ASSIGNED = "User {} already has role {}";

    // ============================================================
    // USER STATUS MANAGEMENT
    // ============================================================
    public static final String USER_STATUS_CHANGING = "Changing status for user ID: {} from {} to {} by manager ID: {}";
    public static final String USER_STATUS_CHANGED = "User ID: {} status changed from {} to {} successfully";
    public static final String USER_STATUS_CHANGE_FAILED = "Failed to change status for user ID: {} - Error: {}";
    public static final String USER_ALREADY_HAS_STATUS = "User {} already has status {}";

    // ============================================================
    // EXCEPTION HANDLER
    // ============================================================
    public static final String EXCEPTION_VALIDATION_ERROR = "Validation error: {}";
    public static final String EXCEPTION_BUSINESS = "Business exception: {}";
    public static final String EXCEPTION_UNAUTHORIZED = "Unauthorized access: {}";
    public static final String EXCEPTION_AUTHENTICATION_FAILED = "Authentication failed: {}";
    public static final String EXCEPTION_RESOURCE_NOT_FOUND = "Resource not found: {}";
    public static final String EXCEPTION_ACCESS_DENIED = "Access denied: {}";
    public static final String EXCEPTION_FORBIDDEN = "Forbidden: {}";
    public static final String EXCEPTION_FILE_SIZE_EXCEEDED = "File size exceeded: {}";
    public static final String EXCEPTION_UNEXPECTED = "Unexpected error occurred";

    // ============================================================
    // CATEGORY MANAGEMENT SERVICE
    // ============================================================
    public static final String CATEGORY_CREATING = "Creating category with code: {}";
    public static final String CATEGORY_CREATED = "Category created successfully with ID: {}";
    public static final String CATEGORY_CODE_DUPLICATE = "Attempted to create category with duplicate code: {}";
    public static final String CATEGORY_UPDATING = "Updating category with ID: {}";
    public static final String CATEGORY_UPDATED = "Category updated successfully with ID: {}";
    public static final String CATEGORY_FETCHING = "Fetching category with ID: {}";
    public static final String CATEGORY_LIST_FETCHING = "Fetching all categories";

    // ============================================================
    // CATEGORY CONTROLLER
    // ============================================================
    public static final String CATEGORY_CONTROLLER_CREATE_REQUEST = "Create category request: {} by userId: {}";
    public static final String CATEGORY_CONTROLLER_CREATE_FAILED = "Failed to create category: {}";
    public static final String CATEGORY_CONTROLLER_UPDATE_REQUEST = "Update category ID: {} by userId: {}";
    public static final String CATEGORY_CONTROLLER_UPDATE_FAILED = "Failed to update category ID {}: {}";

    // ============================================================
    // CATEGORY DEACTIVATE & TREE
    // ============================================================
    public static final String CATEGORY_DEACTIVATING = "Deactivating category ID: {}";
    public static final String CATEGORY_DEACTIVATED = "Category deactivated successfully ID: {}";
    public static final String CATEGORY_TREE_FETCHING = "Fetching category tree";

    // ============================================================
    // MAP CATEGORY TO ZONE (Convention-based)
    // ============================================================
    public static final String CZM_MAPPING = "Mapping category ID: {} to zone in warehouse ID: {}";
    public static final String CZM_MAPPED = "Category {} mapped to zone {} by convention";

    // ============================================================
    // SKU
    // ============================================================
    public static final String SKU_ASSIGNING_CATEGORY = "Assigning category to SKU: skuId={}, categoryId={}";
    public static final String SKU_CATEGORY_ASSIGNED = "Category assigned to SKU: skuId={}, categoryId={}";
    public static final String SKU_FETCHING_DETAIL = "Fetching SKU detail for ID: {}";
}

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

    // ==================== Error Messages ====================
    public static final String AUTH_FAILED = "Authentication failed";
    public static final String ACCESS_DENIED = "You do not have permission to perform this action!";
    public static final String UNEXPECTED_ERROR = "An unexpected error occurred. Please try again later.";
}

package org.example.sep26management.application.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * UserManagementService - TEMPORARILY DISABLED
 * 
 * This service needs to be refactored to use dynamic RBAC (Set<RoleEntity>)
 * instead of UserRole enum.
 * 
 * TODO: Refactor to:
 * 1. Update CreateUserRequest to use Set<String> roleCodes
 * 2. Update AssignRoleRequest to use Set<String> roleCodes
 * 3. Inject UserEntityMapper and RoleEntityMapper
 * 4. Fetch RoleEntity objects from database
 * 5. Update all methods to work with Set<RoleEntity>
 * 
 * For now, this is commented out to allow compilation.
 */
@Service
@Slf4j
public class UserManagementService {

        // ALL METHODS TEMPORARILY COMMENTED OUT
        // WILL BE REFACTORED TO USE DYNAMIC RBAC

}
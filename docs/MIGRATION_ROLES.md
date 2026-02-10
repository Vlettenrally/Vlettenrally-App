# Migration: Multiple Roles Support for RefreshToken

## Overview
This document describes the changes made to support storing multiple roles in the `RefreshTokenEntity` object, aligned with the existing database migration V22.

## Database Changes
The database migration `V22__change_role_to_roles_array.sql` was already in place and converted the `role` column to `roles` (TEXT array) in the `refresh_tokens` table.

## Code Changes

### 1. Created StringSetConverter
**File**: `api/src/main/java/nl/vlettenrally/data/converter/StringSetConverter.java`

A JPA AttributeConverter that converts between Java `Set<String>` and PostgreSQL `TEXT[]` array.

```java
@Converter
public class StringSetConverter implements AttributeConverter<Set<String>, String[]>
```

This converter handles:
- Converting Java Set to PostgreSQL array for database storage
- Converting PostgreSQL array back to Java Set when reading from database
- Null and empty set handling

### 2. Updated RefreshTokenEntity
**File**: `api/src/main/java/nl/vlettenrally/data/entity/jpa/RefreshTokenEntity.java`

Changed from:
```java
@Column(name = "role", nullable = false, length = 50)
private String role;
```

To:
```java
@Convert(converter = StringSetConverter.class)
@Column(name = "roles", columnDefinition = "text[]")
private Set<String> roles = new java.util.HashSet<>();
```

### 3. Updated RefreshToken Domain Object
**File**: `api/src/main/java/nl/vlettenrally/data/domain/RefreshToken.java`

Already had `Set<String> roles` field with proper support.

### 4. Updated RefreshTokenMapper
**File**: `api/src/main/java/nl/vlettenrally/data/mapper/RefreshTokenMapper.java`

Updated all mapper methods to work with `roles` (Set) instead of `role` (String):
- `toDomain()` - Maps entity.getRoles() to domain.setRoles()
- `toEntity()` - Maps domain.getRoles() to entity.setRoles()
- `updateEntity()` - Updates entity roles from domain

### 5. Updated TokenResult DTO
**File**: `api/src/main/java/nl/vlettenrally/data/dto/auth/TokenResult.java`

Enhanced to support both single role (backward compatibility) and multiple roles:
```java
public record TokenResult(
    String accessToken, 
    String refreshToken, 
    long expiresIn, 
    String subject, 
    String name, 
    String role,           // Single role as comma-separated string
    Set<String> roles      // Multiple roles as Set
)
```

Provides convenience constructors:
- Single role constructor: Converts to Set internally
- Multiple roles constructor: Joins roles with comma for single role field

### 6. Updated RefreshTokenData DTO
**File**: `api/src/main/java/nl/vlettenrally/data/dto/auth/RefreshTokenData.java`

Enhanced to support both single role and multiple roles:
```java
public record RefreshTokenData(
    String subject,
    String name,
    String role,           // Single role for backward compatibility
    Set<String> roles,     // Multiple roles
    long expiresAt,
    boolean revoked
)
```

## Backward Compatibility
The changes maintain backward compatibility by:
1. Providing both `role` (String) and `roles` (Set<String>) fields in DTOs
2. The single `role` field contains comma-separated roles when multiple roles exist
3. Convenience constructors that accept single role and convert to Set internally
4. The `AuthServiceImpl` already uses the new structure and joins roles with commas

## Testing
- Project compiles successfully with `./mvnw clean compile`
- No breaking changes to existing API contracts
- Database migration V22 ensures existing data is converted properly

## Future Considerations
- Frontend may need updates to handle multiple roles per user
- Consider deprecating single `role` field in DTOs in favor of `roles` Set
- Add validation to ensure at least one role is present


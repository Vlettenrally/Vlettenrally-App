package nl.vlettenrally.data.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import nl.vlettenrally.data.domain.Admin;

/**
 * Repository interface for Admin entities
 */
public interface AdminRepository {
    
    Optional<Admin> findById(UUID id);
    
    Optional<Admin> findByUsername(String username);
    
    List<Admin> findAll();
    
    Admin save(Admin admin);
    
    void delete(UUID id);
}

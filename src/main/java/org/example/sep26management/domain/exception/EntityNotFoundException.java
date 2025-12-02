package org.example.sep26management.domain.exception;

public class EntityNotFoundException extends DomainException {
    
    public EntityNotFoundException(String entityName, Long id) {
        super(String.format("%s with id %d not found", entityName, id));
    }
    
    public EntityNotFoundException(String message) {
        super(message);
    }
}


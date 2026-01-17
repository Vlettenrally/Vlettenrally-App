package nl.vlettenrally.provider;

import org.wildfly.security.password.Password;

import io.quarkus.security.jpa.PasswordProvider;

public class AdminPasswordProvider implements PasswordProvider {
    @Override
    public Password getPassword(String passwordFromDatabase) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getPassword'");
    }
    
}

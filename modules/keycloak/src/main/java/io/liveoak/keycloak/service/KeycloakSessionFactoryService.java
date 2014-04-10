package io.liveoak.keycloak.service;

import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.mongo.keycloak.MongoModelProvider;

/**
 * @author Bob McWhirter
 */
public class KeycloakSessionFactoryService implements Service<KeycloakSessionFactory> {

    @Override
    public void start(StartContext context) throws StartException {
        this.sessionFactory = new MongoModelProvider().createFactory();
    }

    @Override
    public void stop(StopContext context) {
        this.sessionFactory = null;
    }

    @Override
    public KeycloakSessionFactory getValue() throws IllegalStateException, IllegalArgumentException {
        return this.sessionFactory;
    }

    private KeycloakSessionFactory sessionFactory;

}

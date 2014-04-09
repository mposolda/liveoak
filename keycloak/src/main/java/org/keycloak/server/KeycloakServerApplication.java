package org.keycloak.server;

import org.keycloak.models.ApplicationModel;
import org.keycloak.models.ClaimMask;
import org.keycloak.models.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.services.managers.ApplicationManager;
import org.keycloak.services.managers.RealmManager;
import org.keycloak.services.resources.KeycloakApplication;

import javax.servlet.ServletContext;
import javax.ws.rs.core.Context;
import java.io.FileNotFoundException;

public class KeycloakServerApplication extends KeycloakApplication {

    static {
        Config.setAdminRealm("liveoak-admin");
        Config.setModelProvider("mongo");
    }

    public KeycloakServerApplication(@Context ServletContext servletContext) throws FileNotFoundException {
        super(servletContext);

        configureLiveOakConsole("http://localhost:8080");
    }

    protected void configureLiveOakConsole(String baseUrl) {
        KeycloakSession session = factory.createSession();
        session.getTransaction().begin();

        try {
            RealmManager manager = new RealmManager(session);
            RealmModel adminRealm = manager.getRealm(Config.getAdminRealm());

            if (adminRealm.getApplicationByName("console") == null) {
                ApplicationModel consoleApp = new ApplicationManager(manager).createApplication(adminRealm, "console");
                consoleApp.setPublicClient(true);

                consoleApp.addDefaultRole("user");
                consoleApp.addRole("admin");

                consoleApp.setAllowedClaimsMask(ClaimMask.USERNAME);

                consoleApp.addRedirectUri(baseUrl + "/admin");
                consoleApp.addRedirectUri(baseUrl + "/admin/");
                consoleApp.addWebOrigin(baseUrl);
                consoleApp.setBaseUrl(baseUrl + "/admin/");
            }

            session.getTransaction().commit();
        } finally {
            session.close();
        }
    }

}

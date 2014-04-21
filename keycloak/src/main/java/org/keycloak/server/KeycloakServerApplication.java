package org.keycloak.server;

import org.keycloak.models.ApplicationModel;
import org.keycloak.models.ClaimMask;
import org.keycloak.models.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.representations.idm.CredentialRepresentation;
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

        KeycloakSession session = factory.createSession();
        session.getTransaction().begin();
        try {
            configureLiveOakConsole(session, "http://localhost:8080");
            configureLiveOakAppsRealm(session);

            session.getTransaction().commit();
        } finally {
            session.close();
        }
    }

    protected void configureLiveOakConsole(KeycloakSession session, String baseUrl) {
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
    }

    protected void configureLiveOakAppsRealm(KeycloakSession session) {
        RealmManager manager = new RealmManager(session);
        RealmModel appsRealm = manager.getRealm("liveoak-apps");

        if (appsRealm == null) {
            RealmManager realmManager = new RealmManager(session);
            RealmModel realm = realmManager.createRealm("liveoak-apps");
            realm.setEnabled(true);
            realm.setRegistrationAllowed(true);
            realm.setSslNotRequired(true);
            realm.addRequiredCredential(CredentialRepresentation.PASSWORD);

            realm.setCentralLoginLifespan(3000);
            realm.setAccessTokenLifespan(60);
            realm.setRefreshTokenLifespan(3600);
            realm.setAccessCodeLifespan(60);
            realm.setAccessCodeLifespanUserAction(300);

            manager.generateRealmKeys(realm);
        }
    }

}

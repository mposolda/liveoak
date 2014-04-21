package io.liveoak.keycloak;

import org.keycloak.jose.jws.JWSBuilder;
import org.keycloak.models.RealmModel;
import org.keycloak.representations.AccessToken;
import org.keycloak.util.JsonSerialization;

import java.security.PrivateKey;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:sthorger@redhat.com">Stian Thorgersen</a>
 */
public class TokenUtil {

    private String realm;
    private PrivateKey privateKey;

    public TokenUtil(RealmModel realmModel) {
        privateKey = realmModel.getPrivateKey();
        realm = realmModel.getName();
    }

    public AccessToken createToken() {
        AccessToken token = new AccessToken();
        token.id("token-id");
        token.subject("user-id");
        token.audience(realm);
        token.expiration(System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(300));
        token.issuedFor("app-id");
        token.issuedNow();

        token.setRealmAccess(new AccessToken.Access().roles(Collections.singleton("realm-role")));
        token.addAccess("app-id").roles(Collections.singleton("app-role"));
        token.addAccess("app2-id").roles(Collections.singleton("app-role"));

        return token;
    }

    public String toString(AccessToken token) throws Exception {
        byte[] tokenBytes = JsonSerialization.writeValueAsBytes(token);
        return new JWSBuilder().content(tokenBytes).rsa256(privateKey);
    }

    public String realm() {
        return this.realm;
    }


}

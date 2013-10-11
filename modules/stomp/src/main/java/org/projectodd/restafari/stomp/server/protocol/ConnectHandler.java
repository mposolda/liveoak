package org.projectodd.restafari.stomp.server.protocol;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;
import org.projectodd.restafari.stomp.Headers;
import org.projectodd.restafari.stomp.Stomp;
import org.projectodd.restafari.stomp.common.AbstractControlFrameHandler;
import org.projectodd.restafari.stomp.common.StompControlFrame;
import org.projectodd.restafari.stomp.common.StompFrame;
import org.projectodd.restafari.stomp.server.Heartbeat;
import org.projectodd.restafari.stomp.server.ServerContext;
import org.projectodd.restafari.stomp.server.StompConnection;
import org.projectodd.restafari.stomp.server.StompServerException;

import java.util.regex.Pattern;

/**
 * @author Bob McWhirter
 */
public class ConnectHandler extends AbstractControlFrameHandler {

    static final AttributeKey<StompConnection> CONNECTION = new AttributeKey<>("stomp-connection");

    public ConnectHandler(ServerContext serverContext) {
        super(Stomp.Command.CONNECT);
        this.serverContext = serverContext;
    }


    @Override
    public void handleControlFrame(ChannelHandlerContext ctx, StompControlFrame frame) throws StompServerException {
        Stomp.Version version = checkVersion(frame);
        Heartbeat hb = checkHeartbeat(frame, version);
        Headers headers = frame.headers();
        String hostHeader = headers.get(Headers.HOST);

        checkHost(frame, headers, version);
        StompConnection stompConnection = new StompConnection( ctx.channel() );
        StompFrame connected = StompFrame.newConnectedFrame(stompConnection.getConnectionId(), version);
        if (hb != null) {
            connected.headers().put(Headers.HEARTBEAT, hb.getServerSend() + "," + hb.getServerReceive());
        }
        this.serverContext.handleConnect(stompConnection);
        ctx.channel().attr(CONNECTION).set(stompConnection);
        ctx.writeAndFlush(connected);
    }

    private Heartbeat checkHeartbeat(StompFrame frame, Stomp.Version version) throws StompServerException {
        Heartbeat hb = null;
        String heartBeat = frame.headers().get(Headers.HEARTBEAT);
        if (!version.isBefore(Stomp.Version.VERSION_1_1) && heartBeat != null && !heartBeat.equals("")) {
            if (!HEART_BEAT_PATTERN.matcher(heartBeat).matches()) {
                throw exception(frame, "Heartbeat must be specified in msec as two comma-separated values.");
            }
            String[] components = heartBeat.split(",");
            try {
                hb = new Heartbeat();
                hb.setClientReceive(Integer.parseInt(components[0]));
                hb.setClientSend(Integer.parseInt(components[1]));
            } catch (Exception ex) {
                throw exception(frame, "Heartbeat values must be integers.");
            }
        }
        return hb;
    }

    private String checkHost(StompFrame frame, Headers headers, Stomp.Version version) throws StompServerException {
        String host = headers.get(Headers.HOST);
        if (version.isAfter(Stomp.Version.VERSION_1_0) && (host == null || "".equals(host))) {
            throw exception(frame, "Must specify host in STOMP protocol 1.1 and above.");
        }
        return host;
    }

    private Stomp.Version checkVersion(StompFrame frame) throws StompServerException {
        String acceptVersion = frame.headers().get(Headers.ACCEPT_VERSION);
        if (acceptVersion == null) {
            return Stomp.Version.VERSION_1_0;
        } else if (!VERSION_PATTERN.matcher(acceptVersion).matches()) {
            throw exception(frame, "Accept-version header value must be an incrementing comma-separated list.");
        }
        String[] versions = acceptVersion.split(",");
        Stomp.Version selectedVersion = null;
        for (int i = versions.length - 1; i >= 0; i--) {
            if ((selectedVersion = Stomp.Version.forVersionString(versions[i])) != null)
                break;
        }
        if (selectedVersion == null) {
            // no matching version found - handleSend error frame
            throw exception(frame, "Supported protocol versions are " + Stomp.Version.supportedVersions());
        }
        return selectedVersion;

    }

    private StompServerException exception(StompFrame frame, String message) {
        return new StompServerException(message).withReceiptId(frame.headers().get(Headers.RECEIPT));
    }


    private static final Pattern HEART_BEAT_PATTERN = Pattern.compile("^\\d+,\\d+$");
    private static final Pattern VERSION_PATTERN = Pattern.compile("^([^\\s]+,)*[^\\s]*$");

    private ServerContext serverContext;

}
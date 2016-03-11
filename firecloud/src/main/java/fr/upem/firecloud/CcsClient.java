package fr.upem.firecloud;

import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.ConnectionConfiguration.SecurityMode;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.PacketInterceptor;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.PacketTypeFilter;
import org.jivesoftware.smack.packet.DefaultPacketExtension;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.provider.PacketExtensionProvider;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smack.util.StringUtils;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;
import org.xmlpull.v1.XmlPullParser;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.net.ssl.SSLSocketFactory;

/**
 * Sample Smack implementation of a client for GCM Cloud Connection Server. This
 * code can be run as a standalone CCS client.
 *
 * For illustration purposes only.
 */
public class CcsClient {

    private static final String GCM_SERVER = "gcm.googleapis.com";
    private static final int GCM_PORT = 5235;

    private static final String GCM_ELEMENT_NAME = "gcm";
    private static final String GCM_NAMESPACE = "google:mobile:data";

    private XMPPConnection connection;

    private final String apiKey;
    private final String projectId;
    private final boolean debuggable;
    private final DataBaseCommunicator dataBaseCommunicator = new DataBaseCommunicator();

    /**
     * Indicates whether the connection is in draining state, which means that it
     * will not accept any new downstream messages.
     */
    protected volatile boolean connectionDraining = false;

    public CcsClient(String projectId, String apiKey, boolean debuggable) {
        // Add GcmPacketExtension
        ProviderManager.getInstance().addExtensionProvider(GCM_ELEMENT_NAME,
                GCM_NAMESPACE, new PacketExtensionProvider() {

                    @Override
                    public PacketExtension parseExtension(XmlPullParser parser)
                            throws Exception {
                        String json = parser.nextText();
                        return new GcmPacketExtension(json);
                    }
                });
        this.apiKey = apiKey;
        this.projectId = projectId;
        this.debuggable = debuggable;
    }



    /**
     * XMPP Packet Extension for GCM Cloud Connection Server.
     */
    private class GcmPacketExtension extends DefaultPacketExtension {

        String json;

        private GcmPacketExtension(String json) {
            super(GCM_ELEMENT_NAME, GCM_NAMESPACE);
            this.json = json;
        }

        private String getJson() {
            return json;
        }

        @Override
        public String toXML() {
            return String.format("<%s xmlns=\"%s\">%s</%s>",
                    GCM_ELEMENT_NAME, GCM_NAMESPACE,
                    StringUtils.escapeForXML(json), GCM_ELEMENT_NAME);
        }

        private Packet toPacket() {
            org.jivesoftware.smack.packet.Message message = new org.jivesoftware.smack.packet.Message();
            message.addExtension(this);
            return message;
        }
    }


    /**
     * Returns a random message id to uniquely identify a message.
     *
     * Note: This is generated by a pseudo random number generator for
     * illustration purpose, and is not guaranteed to be unique.
     */
    private String getRandomMessageId() {
        return "m-" + UUID.randomUUID().toString();
    }

    /**
     * Sends a packet with contents provided.
     */
    private void send(String jsonRequest) {
        Packet request = new GcmPacketExtension(jsonRequest).toPacket();
        connection.sendPacket(request);
    }

    /**
     * Sends a downstream message to GCM.
     *
     */
    private void sendDownstreamMessage(String jsonRequest) {
        System.out.println("Trying to send downstream message...");
        while(connectionDraining);
        send(jsonRequest);
        System.out.println("Message sent");
    }


    /**
     * Connects to GCM Cloud Connection Server using the supplied credentials.
     * @throws XMPPException
     */
    public void connect() throws XMPPException {
        ConnectionConfiguration config = new ConnectionConfiguration(GCM_SERVER, GCM_PORT);
        config.setSecurityMode(SecurityMode.enabled);
        config.setReconnectionAllowed(true);
        config.setRosterLoadedAtLogin(false);
        config.setSendPresence(false);
        config.setSocketFactory(SSLSocketFactory.getDefault());

        // NOTE: Set to true to launch a window with information about packets sent and received
        config.setDebuggerEnabled(debuggable);

        // -Dsmack.debugEnabled=true
        XMPPConnection.DEBUG_ENABLED = true;

        connection = new XMPPConnection(config);
        connection.connect();

        connection.addConnectionListener(new ConnectionListener() {

            @Override
            public void reconnectionSuccessful() {
                System.out.println("The reconnection is successful.");
            }

            @Override
            public void reconnectionFailed(Exception e) {
                System.out.println("The reconnection failed with this exception :\n" + e);
            }

            @Override
            public void reconnectingIn(int seconds) {
                System.out.println("The reconnection will try again in " + seconds + " seconds.");
            }

            @Override
            public void connectionClosedOnError(Exception e) {
                System.out.println("The connection closed with an exception :\n" + e);
            }

            @Override
            public void connectionClosed() {
                System.out.println("The connection has been closed.");
            }
        });

        // Handle incoming packets
        connection.addPacketListener(new PacketListener() {

            @Override
            public void processPacket(Packet packet) {
                System.out.println("The following packet has been received :\n" + packet.toXML());
                org.jivesoftware.smack.packet.Message incomingMessage = (org.jivesoftware.smack.packet.Message) packet;
                GcmPacketExtension gcmPacket
                        = (GcmPacketExtension) incomingMessage.getExtension(GCM_NAMESPACE);
                String json = gcmPacket.getJson();
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> jsonMap
                            = (Map<String, Object>) JSONValue.parseWithException(json);
                    handleMessage(jsonMap);
                } catch (ParseException e) {
                    System.err.println("An error occurred while parsing the following json :\n" + json + "\n" + e);
                }
            }
        }, new PacketTypeFilter(org.jivesoftware.smack.packet.Message.class));

        // Log all outgoing packets
        connection.addPacketInterceptor(new PacketInterceptor() {
            @Override
            public void interceptPacket(Packet packet) {
                System.out.println("Trying to send the following packet :\n" + packet.toXML());
            }
        }, new PacketTypeFilter(org.jivesoftware.smack.packet.Message.class));

        connection.login(projectId + "@gcm.googleapis.com", apiKey);
        System.out.println("The project " + projectId + " has been correctly recognized by Google with the right ApiKey.");
    }

    private void handleMessage(Map<String, Object> jsonMap) {
        // present for "ack"/"nack", null otherwise
        Object messageType = jsonMap.get("message_type");

        if (messageType == null) {
            Message msg = getMessage(jsonMap);
            // Normal upstream data message
            handleIncomingDataMessage(msg);
            // Send ACK to CCS
            String ack = createJsonAck(msg.getFrom(), msg.getMessageId());
            send(ack);
        } else if ("ack".equals(messageType.toString())) {
            // Process Ack
            handleAckReceipt(jsonMap);
        } else if ("nack".equals(messageType.toString())) {
            // Process Nack
            handleNackReceipt(jsonMap);
        } else {
            System.err.println("The message_type received (" + messageType + ") is not one from Google (ack or nack)");
        }
    }

    /**
     * Creates a Message from the json of the message.
     */
    private Message getMessage(Map<String, Object> jsonObject) {
        String from = jsonObject.get("from").toString();

        // unique id of this message
        String messageId = jsonObject.get("message_id").toString();

        @SuppressWarnings("unchecked")
        Map<String, String> payload = (Map<String, String>) jsonObject.get("data");

        return new Message(from, messageId, payload);
    }

    /**
     * Handles an ACK.
     *
     * By default, it only logs a INFO message, but subclasses could override it
     * to properly handle ACKS.
     */
    private void handleAckReceipt(Map<String, Object> jsonObject) {
        String messageId = jsonObject.get("message_id").toString();
        String from = jsonObject.get("from").toString();
        System.out.println("Ack received : " + from);
        System.out.println("Message id : " + messageId);
    }

    /**
     * Handles a NACK.
     *
     * By default, it only logs a INFO message, but subclasses could override it
     * to properly handle NACKS.
     */
    private void handleNackReceipt(Map<String, Object> jsonObject) {
        String messageId = jsonObject.get("message_id").toString();
        String from = jsonObject.get("from").toString();
        System.out.println("Nack received from : " + from);
        System.out.println("Something might be wrong.");
        System.out.println("Message id : " + messageId);
    }

    /**
     * Creates a JSON encoded ACK message for an upstream message received
     * from an application.
     *
     * @param to RegistrationId of the device who sent the upstream message.
     * @param messageId messageId of the upstream message to be acknowledged to CCS.
     * @return JSON encoded ack.
     */
    private static String createJsonAck(String to, String messageId) {
        Map<String, Object> message = new HashMap<>();
        message.put("message_type", "ack");
        message.put("to", to);
        message.put("message_id", messageId);
        return JSONValue.toJSONString(message);
    }


    /**
     * Creates a JSON encoded GCM message.
     *
     * @param to RegistrationId of the target device (Required).
     * @param messageId Unique messageId for which CCS will send an
     *         "ack/nack" (Required).
     * @param payload Message content intended for the application. (Optional).
     * @param collapseKey GCM collapse_key parameter (Optional).
     * @param timeToLive GCM time_to_live parameter (Optional).
     * @param delayWhileIdle GCM delay_while_idle parameter (Optional).
     * @return JSON encoded GCM message.
     */
    private static String createJsonMessage(String to, String messageId,
                                           Map<String, Object> payload, String collapseKey, Long timeToLive,
                                           Boolean delayWhileIdle) {
        Map<String, Object> message = new HashMap<>();
        message.put("to", to);
        if (collapseKey != null) {
            message.put("collapse_key", collapseKey);
        }
        if (timeToLive != null) {
            message.put("time_to_live", timeToLive);
        }
        if (delayWhileIdle != null && delayWhileIdle) {
            message.put("delay_while_idle", true);
        }
        message.put("message_id", messageId);
        message.put("data", payload);
        return JSONValue.toJSONString(message);
    }

    /**
     * Handles an upstream data message from a device application.
     */
    private void handleIncomingDataMessage(Message message) {
        if (message.getPayload().get("action") != null) {
            Map<String, Object> payload;
            switch(message.getPayload().remove("action")){
                case "createEvent" :
                    payload = new HashMap<>();
                    payload.putAll(message.getPayload());
                    //_id generated automatically
                    Map<String, Object> map = dataBaseCommunicator.createEvent(payload);
                    if(map == null){
                        break;
                    }
                    map.put("action", "receivedEventId");
                    sendDownstreamMessage(createJsonMessage(
                                    message.getFrom(), getRandomMessageId(), map, null, null, true)
                    );
                    break;
                case "createUser" :
                    payload = new HashMap<>();
                    payload.putAll(message.getPayload());
                    payload.put("device", message.getFrom());
                    dataBaseCommunicator.createUser(payload);
                    break;
                case "updatePosition" :
                    //Receive userId, lat, lng, eventId
                    payload = new HashMap<>();
                    payload.putAll(message.getPayload());
                    String eventId = (String) payload.remove("eventId");
                    dataBaseCommunicator.updatePosition(new HashMap<>(payload));
                    List<String> devices = dataBaseCommunicator.getOwnerDevices(eventId);
                    Map<String, Object> newPayload = dataBaseCommunicator.getUser((String) payload.get("userId"));
                    newPayload.put("action", "receivedUserPosition");
                    newPayload.remove("device");
                    newPayload.put("lat", payload.get("lat"));
                    newPayload.put("lng", payload.get("lng"));
                    for(String device : devices){
                        sendDownstreamMessage(createJsonMessage(
                                device, getRandomMessageId(), newPayload, null, null, true
                        ));
                    }
                    break;
                case "addUserToEvent" :
                    payload = new HashMap<>();
                    payload.putAll(message.getPayload());
                    String userId = (String) payload.get("userId");
                    payload.put("_id", userId);
                    dataBaseCommunicator.addUserToEvent(payload);
                    break;
                case "removeUserToEvent" :
                    payload = new HashMap<>();
                    payload.putAll(message.getPayload());
                    String userId2 = (String) payload.get("userId");
                    payload.put("_id", userId2);
                    dataBaseCommunicator.removeUserToEvent(payload);
                    break;
                case "updateEvent" :
                    payload = new HashMap<>();
                    payload.putAll(message.getPayload());
                    dataBaseCommunicator.updateEvent(payload);
                    break;
                case "updateUser" :
                    payload = new HashMap<>();
                    payload.putAll(message.getPayload());
                    payload.put("device", message.getFrom());
                    dataBaseCommunicator.updateUser(payload);
                    break;
                case "getAllEventsOwned" :
                    //action = "receivedEventsOwned"
                    //receive : userId
                    payload = new HashMap<>();
                    payload.putAll(message.getPayload());
                    Map<String, Object> eventsOwned = dataBaseCommunicator.getAllEventsOwned((String) payload.get("userId"));
                    eventsOwned.put("action", "receivedEventsOwned");
                    sendDownstreamMessage(createJsonMessage(
                                    message.getFrom(), getRandomMessageId(), eventsOwned, null, null, true)
                    );
                    break;
                case "getAllEventsGuested" :
                    //action = "receivedEventsGuested"
                    payload = new HashMap<>();
                    payload.putAll(message.getPayload());
                    Map<String, Object> eventsGuested = dataBaseCommunicator.getAllEventsGuested((String) payload.get("userId"));
                    eventsGuested.put("action", "receivedEventsGuested");
                    sendDownstreamMessage(createJsonMessage(
                                    message.getFrom(), getRandomMessageId(), eventsGuested, null, null, true)
                    );
                    break;
                case "getAllUsers" :
                    Map<String, Object> users = dataBaseCommunicator.getUsers();
                    users.put("action", "receivedUsers");
                    sendDownstreamMessage(createJsonMessage(
                            message.getFrom(), getRandomMessageId(), users, null, null, true
                    ));
                    //action = "receivedUsers"
                    break;
                default :
                    System.err.println("Unknown action");
                    break;
            }
        }
        else{
            System.err.println("No action in this message with a payload. Can't proceed.");
        }
    }

}

